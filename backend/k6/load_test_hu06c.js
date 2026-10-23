/**
 * Prueba de Carga k6 — HU-06c: Endpoint de Evaluación de Transacciones
 *
 * Objetivo: Validar 100 TPS durante 60 segundos con p95 < 100 ms en local.
 *
 * Ejecución:
 *   1. Arrancar el servidor: ./mvnw spring-boot:run (con PostgreSQL y Redis activos)
 *   2. Exportar variables de entorno:
 *        $env:K6_API_KEY = "pk_live_XXXXX"   (clave M2M activa del comercio de prueba)
 *   3. Correr: k6 run backend/k6/load_test_hu06c.js
 *
 * Criterio de Aceptación (RF-06c):
 *   - p95 latencia HTTP < 100 ms
 *   - p99 latencia HTTP < 300 ms
 *   - Tasa de errores (HTTP != 200) < 1%
 *   - Throughput sostenido ≥ 100 req/s
 */
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';

// Métricas personalizadas
const errorRate    = new Rate('error_rate');
const latencyTrend = new Trend('hot_path_latency_ms', true);
const approveCount = new Counter('decisions_approve');
const reviewCount  = new Counter('decisions_review');
const rejectCount  = new Counter('decisions_reject');
const degradedCount = new Counter('decisions_degraded');

// Configuración de la prueba de carga
export const options = {
    scenarios: {
        constant_load: {
            executor: 'constant-arrival-rate',
            rate: 100,               // 100 peticiones por segundo
            timeUnit: '1s',
            duration: '60s',         // durante 60 segundos
            preAllocatedVUs: 20,     // VUs pre-asignados
            maxVUs: 50,              // máximo de VUs concurrentes
        },
    },
    thresholds: {
        // Criterios de aceptación obligatorios
        'http_req_duration{scenario:constant_load}': [
            'p(95)<100',   // p95 < 100 ms  ← Criterio principal de la card
            'p(99)<300',   // p99 < 300 ms  ← SLA de resiliencia
        ],
        'error_rate':  ['rate<0.01'],   // < 1% de errores
        'http_reqs':   ['rate>=90'],    // mínimo 90 req/s efectivos (margen de arranque)
    },
};

// Configuración del endpoint
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const API_KEY  = __ENV.K6_API_KEY;

if (!API_KEY) {
    throw new Error('Debes exportar K6_API_KEY con una clave activa del comercio de prueba.');
}

const HEADERS = {
    'Content-Type': 'application/json',
    'X-API-Key': API_KEY,
};

// Contador de VU para generar transactionIds únicos
let txCounter = 0;

export default function () {
    // Payload con transactionId único por iteración (evita que idempotencia devuelva siempre la misma respuesta)
    const txId = `k6_tx_${__VU}_${__ITER}_${Date.now()}`;

    const payload = JSON.stringify({
        transactionId:   txId,
        amount:          (Math.random() * 1000 + 10).toFixed(2),
        currency:        'PEN',
        paymentMethod:   'CREDIT_CARD',
        cardBin:         '411111',
        cardLastFour:    '1111',
        cardFingerprint: `fp_k6_vu${__VU}`,   // por VU para simular clientes distintos
        customerId:      `cust_k6_${__VU}`,
        ipAddress:       `10.${__VU % 255}.${__ITER % 255}.1`,
        deviceId:        `dev_k6_${__VU}`,
    });

    const res = http.post(`${BASE_URL}/v1/transactions/evaluate`, payload, { headers: HEADERS });

    // Registrar latencia del hot path reportada por el sistema (campo latencyMs en la respuesta)
    const isOk = res.status === 200;
    errorRate.add(!isOk);

    const ok = check(res, {
        'HTTP 200 OK':               (r) => r.status === 200,
        'Content-Type JSON':         (r) => r.headers['Content-Type'] && r.headers['Content-Type'].includes('application/json'),
        'decision presente':         (r) => { try { return JSON.parse(r.body).decision != null; } catch { return false; } },
        'riskScore presente':        (r) => { try { return JSON.parse(r.body).riskScore != null; } catch { return false; } },
        'degraded presente':         (r) => { try { return JSON.parse(r.body).hasOwnProperty('degraded'); } catch { return false; } },
        'latencyMs presente':        (r) => { try { return JSON.parse(r.body).latencyMs != null; } catch { return false; } },
        'ruleSetVersion presente':   (r) => { try { return JSON.parse(r.body).ruleSetVersion != null; } catch { return false; } },
    });

    if (isOk) {
        try {
            const body = JSON.parse(res.body);
            if (body.latencyMs != null) {
                latencyTrend.add(body.latencyMs);
            }
            switch (body.decision) {
                case 'APPROVE': approveCount.add(1); break;
                case 'REVIEW':  reviewCount.add(1); break;
                case 'REJECT':  rejectCount.add(1); break;
            }
            if (body.degraded === true) {
                degradedCount.add(1);
            }
        } catch (_) { /* ignore parse errors */ }
    }
}

export function handleSummary(data) {
    const p50  = data.metrics.http_req_duration?.values?.['p(50)']?.toFixed(2)  ?? 'N/A';
    const p95  = data.metrics.http_req_duration?.values?.['p(95)']?.toFixed(2)  ?? 'N/A';
    const p99  = data.metrics.http_req_duration?.values?.['p(99)']?.toFixed(2)  ?? 'N/A';
    const rps  = data.metrics.http_reqs?.values?.rate?.toFixed(1)               ?? 'N/A';
    const errs = ((data.metrics.error_rate?.values?.rate ?? 0) * 100).toFixed(2);
    const p95ok = parseFloat(p95) < 100;
    const p99ok = parseFloat(p99) < 300;
    const errOk = parseFloat(errs) < 1.0;

    const report = [
        '===============================================================================',
        'REPORTE DE PRUEBA DE CARGA — HU-06c (POST /v1/transactions/evaluate)',
        `Fecha: ${new Date().toISOString()}`,
        '===============================================================================',
        '',
        '--- RESULTADOS DE LATENCIA HTTP ---',
        `  p50  latencia HTTP : ${p50} ms`,
        `  p95  latencia HTTP : ${p95} ms   ${p95ok ? '✓ CUMPLE < 100 ms' : '✗ SUPERA 100 ms'}`,
        `  p99  latencia HTTP : ${p99} ms   ${p99ok ? '✓ CUMPLE < 300 ms' : '✗ SUPERA 300 ms'}`,
        '',
        '--- THROUGHPUT ---',
        `  Peticiones/segundo : ${rps} req/s`,
        `  Tasa de errores    : ${errs}%    ${errOk ? '✓ CUMPLE < 1%' : '✗ SUPERA 1%'}`,
        '',
        '--- DISTRIBUCIÓN DE DECISIONES ---',
        `  APPROVE  : ${data.metrics.decisions_approve?.values?.count  ?? 0}`,
        `  REVIEW   : ${data.metrics.decisions_review?.values?.count   ?? 0}`,
        `  REJECT   : ${data.metrics.decisions_reject?.values?.count   ?? 0}`,
        `  DEGRADED : ${data.metrics.decisions_degraded?.values?.count ?? 0}`,
        '',
        '--- VEREDICTO ---',
        (p95ok && p99ok && errOk)
            ? '✓ PRUEBA DE CARGA APROBADA: SLA de latencia y disponibilidad cumplido al 100%.'
            : '✗ PRUEBA DE CARGA FALLIDA: Revisar thresholds y configuración del sistema.',
        '===============================================================================',
    ].join('\n');

    console.log('\n' + report);

    return {
        'target/reporte_hu06c_k6_carga.txt': report,
        stdout: report,
    };
}
