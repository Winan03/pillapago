import { useEffect, useState, useMemo } from 'react'
import { casesApi, CaseSummary, CaseDetail, CaseFilterParams, ResolveCasePayload } from '../api/cases'
import { CaseDetailModal } from '../components/cases/CaseDetailModal'
import { useAuth } from '../hooks/useAuth'
import '../styles/cases.css'

/**
 * Pantalla Principal: Cola de Casos en RevisiÃ³n Manual (HU-09 / RF-14 / RF-15).
 * Permite a los analistas de riesgo (RISK_ANALYST):
 * 1. Filtrar casos por antigÃ¼edad (dÃ­as) y rangos de monto de la transacciÃ³n.
 * 2. Visualizar casos abiertos, asignados y resueltos de su comercio.
 * 3. Reclamar casos en un clic.
 * 4. Resolver casos en 3 clics o menos con bloqueo optimista.
 */
export default function CasesPage() {
  const { user } = useAuth()
  const [cases, setCases] = useState<CaseSummary[]>([])
  const [loading, setLoading] = useState<boolean>(true)
  const [error, setError] = useState<string | null>(null)
  const [successMessage, setSuccessMessage] = useState<string | null>(null)

  // Filtros
  const [ageDays, setAgeDays] = useState<number | undefined>(undefined)
  const [minAmount, setMinAmount] = useState<string>('')
  const [maxAmount, setMaxAmount] = useState<string>('')
  const [statusFilter, setStatusFilter] = useState<string>('ALL')

  // Modal de Detalle / ResoluciÃ³n
  const [isModalOpen, setIsModalOpen] = useState<boolean>(false)
  const [selectedCaseDetail, setSelectedCaseDetail] = useState<CaseDetail | null>(null)
  const [detailLoading, setDetailLoading] = useState<boolean>(false)
  const [actionLoading, setActionLoading] = useState<boolean>(false)
  const [modalError, setModalError] = useState<string | null>(null)

  const fetchCases = async () => {
    setLoading(true)
    setError(null)
    try {
      const filters: CaseFilterParams = {}
      if (ageDays && ageDays > 0) filters.ageDays = ageDays
      if (minAmount.trim() !== '') filters.minAmount = parseFloat(minAmount)
      if (maxAmount.trim() !== '') filters.maxAmount = parseFloat(maxAmount)
      if (statusFilter !== 'ALL') filters.status = statusFilter as any

      const data = await casesApi.getCases(filters)
      setCases(data)
    } catch (err: any) {
      setError(err?.response?.data?.userMessage || err?.response?.data?.detail || 'Error al cargar la cola de casos.')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    fetchCases()
  }, [ageDays, minAmount, maxAmount, statusFilter])

  // MÃ©tricas para KPI
  const kpis = useMemo(() => {
    const total = cases.length
    const pending = cases.filter((c) => c.status === 'PENDING_REVIEW').length
    const assigned = cases.filter((c) => c.status === 'ASSIGNED').length
    const resolved = cases.filter((c) => c.status === 'RESOLVED').length
    return { total, pending, assigned, resolved }
  }, [cases])

  // Abrir detalle
  const handleOpenDetail = async (caseId: string) => {
    setIsModalOpen(true)
    setDetailLoading(true)
    setModalError(null)
    try {
      const detail = await casesApi.getCaseDetail(caseId)
      setSelectedCaseDetail(detail)
    } catch (err: any) {
      setModalError(err?.response?.data?.userMessage || 'No se pudo obtener el detalle del caso.')
    } finally {
      setDetailLoading(false)
    }
  }

  // Reclamar caso
  const handleClaimCase = async (caseId: string) => {
    setActionLoading(true)
    setError(null)
    try {
      await casesApi.claimCase(caseId)
      setSuccessMessage('Caso asignado correctamente.')
      await fetchCases()
    } catch (err: any) {
      const msg = err?.response?.data?.userMessage || err?.response?.data?.detail || 'Error al asignar el caso.'
      setError(msg)
    } finally {
      setActionLoading(false)
    }
  }

  // Resolver caso
  const handleResolveCase = async (caseId: string, payload: ResolveCasePayload) => {
    setActionLoading(true)
    setModalError(null)
    try {
      await casesApi.resolveCase(caseId, payload)
      setIsModalOpen(false)
      setSuccessMessage(`Caso resuelto exitosamente como ${payload.resolution === 'APPROVED' ? 'APROBADO' : 'RECHAZADO'}.`)
      await fetchCases()
    } catch (err: any) {
      // Bloqueo optimista o caso ya resuelto
      const msg = err?.response?.data?.userMessage || err?.response?.data?.detail || 'Error al resolver el caso.'
      setModalError(msg)
      // Refrescar lista de casos por si hubo cambio concurrente
      fetchCases()
    } finally {
      setActionLoading(false)
    }
  }

  const handleResetFilters = () => {
    setAgeDays(undefined)
    setMinAmount('')
    setMaxAmount('')
    setStatusFilter('ALL')
  }

  return (
    <main className="cases-page-container">
      {/* Encabezado */}
      <div className="cases-header">
        <div className="cases-title-group">
          <h1>Cola de Casos en RevisiÃ³n</h1>
          <p>
            SupervisiÃ³n y resoluciÃ³n de transacciones sospechosas con soporte de bloqueo optimista â€” Comercio:{' '}
            <strong>{user?.merchantId || 'Activo'}</strong>
          </p>
        </div>
      </div>

      {/* Alertas */}
      {successMessage && (
        <div
          style={{
            backgroundColor: '#dcfce7',
            border: '1px solid #bbf7d0',
            color: '#166534',
            padding: '0.75rem 1rem',
            borderRadius: '6px',
            marginBottom: '1rem',
            fontSize: '0.875rem',
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
          }}
        >
          <span>{successMessage}</span>
          <button
            onClick={() => setSuccessMessage(null)}
            style={{ background: 'none', border: 'none', cursor: 'pointer', color: '#166534', fontWeight: 'bold' }}
          >
            &times;
          </button>
        </div>
      )}

      {error && (
        <div
          style={{
            backgroundColor: '#fee2e2',
            border: '1px solid #fecaca',
            color: '#991b1b',
            padding: '0.75rem 1rem',
            borderRadius: '6px',
            marginBottom: '1rem',
            fontSize: '0.875rem',
          }}
        >
          {error}
        </div>
      )}

      {/* KPIs de la Cola */}
      <div className="cases-kpi-grid">
        <div className="cases-kpi-card">
          <div className="cases-kpi-label">Casos Totales</div>
          <div className="cases-kpi-value">{kpis.total}</div>
        </div>
        <div className="cases-kpi-card">
          <div className="cases-kpi-label" style={{ color: '#d97706' }}>
            Pendientes de RevisiÃ³n
          </div>
          <div className="cases-kpi-value" style={{ color: '#d97706' }}>
            {kpis.pending}
          </div>
        </div>
        <div className="cases-kpi-card">
          <div className="cases-kpi-label" style={{ color: '#0284c7' }}>
            En GestiÃ³n (Asignados)
          </div>
          <div className="cases-kpi-value" style={{ color: '#0284c7' }}>
            {kpis.assigned}
          </div>
        </div>
        <div className="cases-kpi-card">
          <div className="cases-kpi-label" style={{ color: '#16a34a' }}>
            Resueltos
          </div>
          <div className="cases-kpi-value" style={{ color: '#16a34a' }}>
            {kpis.resolved}
          </div>
        </div>
      </div>

      {/* Barra de Filtros */}
      <div className="cases-filter-bar">
        <div className="cases-filter-group">
          <label>Estado del Caso</label>
          <select
            className="cases-filter-select"
            value={statusFilter}
            onChange={(e) => setStatusFilter(e.target.value)}
          >
            <option value="ALL">Todos los estados</option>
            <option value="PENDING_REVIEW">Pendientes</option>
            <option value="ASSIGNED">Asignados</option>
            <option value="RESOLVED">Resueltos</option>
          </select>
        </div>

        <div className="cases-filter-group">
          <label>AntigÃ¼edad mÃ¡xima (dÃ­as)</label>
          <input
            type="number"
            min="1"
            className="cases-filter-input"
            placeholder="Ej. 7"
            value={ageDays !== undefined ? ageDays : ''}
            onChange={(e) => setAgeDays(e.target.value ? parseInt(e.target.value) : undefined)}
          />
        </div>

        <div className="cases-filter-group">
          <label>Monto MÃ­nimo</label>
          <input
            type="number"
            min="0"
            step="0.01"
            className="cases-filter-input"
            placeholder="0.00"
            value={minAmount}
            onChange={(e) => setMinAmount(e.target.value)}
          />
        </div>

        <div className="cases-filter-group">
          <label>Monto MÃ¡ximo</label>
          <input
            type="number"
            min="0"
            step="0.01"
            className="cases-filter-input"
            placeholder="10000.00"
            value={maxAmount}
            onChange={(e) => setMaxAmount(e.target.value)}
          />
        </div>

        <button type="button" className="cases-btn-reset" onClick={handleResetFilters}>
          Limpiar Filtros
        </button>
      </div>

      {/* Tabla de Casos */}
      {loading ? (
        <div style={{ textAlign: 'center', padding: '3rem', color: '#64748b' }}>
          Cargando cola de casos...
        </div>
      ) : cases.length === 0 ? (
        <div
          style={{
            backgroundColor: '#ffffff',
            border: '1px solid #e2e8f0',
            borderRadius: '8px',
            padding: '3rem',
            textAlign: 'center',
            color: '#64748b',
          }}
        >
          No hay casos que coincidan con los filtros seleccionados.
        </div>
      ) : (
        <div className="cases-table-wrapper">
          <table className="cases-table">
            <thead>
              <tr>
                <th>Caso</th>
                <th>TransacciÃ³n ID</th>
                <th>Monto</th>
                <th>Score</th>
                <th>VersiÃ³n Reglas</th>
                <th>Estado</th>
                <th>Asignado a</th>
                <th>Dictamen</th>
                <th style={{ textAlign: 'right' }}>AcciÃ³n</th>
              </tr>
            </thead>
            <tbody>
              {cases.map((c) => {
                const isClaimedByMe = c.assignedToName && c.assignedToName === user?.username
                return (
                  <tr key={c.id}>
                    <td>
                      <span style={{ fontFamily: 'monospace', fontWeight: 600, color: '#0f172a' }}>
                        {c.id.substring(0, 8)}...
                      </span>
                    </td>
                    <td>
                      <span style={{ color: '#475569' }}>{c.externalTransactionId}</span>
                    </td>
                    <td>
                      <strong>
                        {c.currency} {c.amount ? c.amount.toFixed(2) : '-'}
                      </strong>
                    </td>
                    <td>
                      <span
                        style={{
                          fontWeight: 700,
                          color: c.riskScore >= 75 ? '#dc2626' : c.riskScore >= 50 ? '#d97706' : '#16a34a',
                        }}
                      >
                        {c.riskScore}
                      </span>
                    </td>
                    <td>
                      <span style={{ fontFamily: 'monospace', color: '#64748b' }}>
                        {c.ruleSetVersion || 'v1'}
                      </span>
                    </td>
                    <td>
                      <span className={`badge-status badge-status-${c.status.toLowerCase()}`}>
                        {c.status}
                      </span>
                    </td>
                    <td>
                      <span style={{ fontSize: '0.85rem', color: '#334155' }}>
                        {c.assignedToName ? (isClaimedByMe ? 'TÃº' : c.assignedToName) : '-'}
                      </span>
                    </td>
                    <td>
                      {c.resolution ? (
                        <span
                          className={`badge-status ${
                            c.resolution === 'APPROVED' ? 'badge-resolution-approved' : 'badge-resolution-rejected'
                          }`}
                        >
                          {c.resolution === 'APPROVED' ? 'APROBADO' : 'RECHAZADO'}
                        </span>
                      ) : (
                        <span style={{ color: '#94a3b8', fontSize: '0.8rem' }}>Pendiente</span>
                      )}
                    </td>
                    <td style={{ textAlign: 'right' }}>
                      <div style={{ display: 'inline-flex', gap: '0.5rem' }}>
                        {c.status === 'PENDING_REVIEW' && (
                          <button
                            className="cases-btn-outline"
                            onClick={() => handleClaimCase(c.id)}
                            disabled={actionLoading}
                          >
                            Reclamar
                          </button>
                        )}
                        <button
                          className="cases-btn-action"
                          onClick={() => handleOpenDetail(c.id)}
                          disabled={actionLoading}
                        >
                          {c.status === 'RESOLVED' ? 'Ver Detalle' : 'Revisar'}
                        </button>
                      </div>
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      )}

      {/* Modal de Detalle y ResoluciÃ³n */}
      <CaseDetailModal
        isOpen={isModalOpen}
        onClose={() => {
          setIsModalOpen(false)
          setSelectedCaseDetail(null)
          setModalError(null)
        }}
        caseDetail={selectedCaseDetail}
        loading={detailLoading}
        onResolve={handleResolveCase}
        actionLoading={actionLoading}
        errorMessage={modalError}
      />
    </main>
  )
}
