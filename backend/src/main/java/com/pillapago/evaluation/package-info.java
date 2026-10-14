/**
 * Módulo evaluation — Endpoint de evaluación de transacciones (hot path).
 * HU-06a, HU-06b · RF-07, RF-08
 *
 * Sub-paquetes:
 *   controller/  → EvaluationController (POST /v1/transactions/evaluate)
 *   service/     → EvaluationService (orquesta autenticación, idempotencia, motor)
 *   engine/      → Núcleo Java puro sin Spring: Rule, Condition, RuleEvaluator, ScoreCalculator
 */
package com.pillapago.evaluation;
