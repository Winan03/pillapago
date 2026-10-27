import React, { useState } from 'react'
import { CaseDetail, CaseResolution, ResolveCasePayload } from '../../api/cases'

interface CaseDetailModalProps {
  isOpen: boolean
  onClose: () => void
  caseDetail: CaseDetail | null
  loading: boolean
  onResolve: (caseId: string, payload: ResolveCasePayload) => Promise<void>
  actionLoading: boolean
  errorMessage: string | null
}

/**
 * Modal de Detalle y Resolución de Casos (HU-09).
 * Permite resolver el caso en 3 clics o menos:
 * Clic 1: Abrir modal desde la cola
 * Clic 2: Seleccionar Aprobar o Rechazar
 * Clic 3: Confirmar resolución con justificación
 *
 * Muestra:
 * - Score de riesgo y versión de reglas evaluada
 * - Reglas activadas con puntajes
 * - Bloqueo optimista y manejo de inmutabilidad
 */
export const CaseDetailModal: React.FC<CaseDetailModalProps> = ({
  isOpen,
  onClose,
  caseDetail,
  loading,
  onResolve,
  actionLoading,
  errorMessage,
}) => {
  const [resolution, setResolution] = useState<CaseResolution>('APPROVED')
  const [comment, setComment] = useState<string>('')
  const [validationError, setValidationError] = useState<string | null>(null)

  if (!isOpen) return null

  const handleConfirmResolve = async () => {
    if (!comment.trim() || comment.trim().length < 5) {
      setValidationError('La justificación o comentario es obligatorio (mínimo 5 caracteres).')
      return
    }

    setValidationError(null)
    if (caseDetail) {
      await onResolve(caseDetail.id, {
        resolution,
        comment: comment.trim(),
      })
    }
  }

  const isResolved = caseDetail?.status === 'RESOLVED'

  return (
    <div className="case-modal-overlay">
      <div className="case-modal-dialog">
        <div className="case-modal-header">
          <div>
            <h2>
              Caso {caseDetail?.id ? caseDetail.id.substring(0, 8) : '...'} - Transacción #{caseDetail?.externalTransactionId}
            </h2>
            <div style={{ marginTop: '0.25rem', display: 'flex', gap: '0.5rem', alignItems: 'center' }}>
              <span className={`badge-status badge-status-${caseDetail?.status.toLowerCase()}`}>
                {caseDetail?.status}
              </span>
              <span style={{ fontSize: '0.8rem', color: '#64748b' }}>
                Versión de bloqueo: {caseDetail?.version}
              </span>
            </div>
          </div>
          <button className="case-modal-close-btn" onClick={onClose} disabled={actionLoading}>
            &times;
          </button>
        </div>

        <div className="case-modal-body">
          {loading ? (
            <div style={{ textAlign: 'center', padding: '2rem', color: '#64748b' }}>
              Cargando detalle del caso...
            </div>
          ) : caseDetail ? (
            <>
              {errorMessage && (
                <div
                  style={{
                    backgroundColor: '#fee2e2',
                    border: '1px solid #fecaca',
                    color: '#991b1b',
                    padding: '0.75rem 1rem',
                    borderRadius: '6px',
                    fontSize: '0.85rem',
                  }}
                >
                  {errorMessage}
                </div>
              )}

              {/* Sección: Resumen de la Transacción */}
              <div className="case-detail-section">
                <div className="case-detail-section-title">Datos de la Transacción</div>
                <div className="case-meta-grid">
                  <div className="case-meta-item">
                    <label>Monto</label>
                    <span>{caseDetail.currency} {caseDetail.amount.toFixed(2)}</span>
                  </div>
                  <div className="case-meta-item">
                    <label>Score de Riesgo</label>
                    <span style={{ color: caseDetail.riskScore >= 75 ? '#dc2626' : '#d97706' }}>
                      {caseDetail.riskScore} / 100
                    </span>
                  </div>
                  <div className="case-meta-item">
                    <label>Versión de Reglas</label>
                    <span style={{ fontFamily: 'monospace' }}>{caseDetail.ruleSetVersion || 'v1'}</span>
                  </div>
                  <div className="case-meta-item">
                    <label>Método de Pago</label>
                    <span>{caseDetail.paymentMethod || 'Tarjeta'}</span>
                  </div>
                  <div className="case-meta-item">
                    <label>Tarjeta</label>
                    <span>{caseDetail.cardBin || '******'} **** {caseDetail.cardLastFour || '****'}</span>
                  </div>
                  <div className="case-meta-item">
                    <label>Fecha Creación</label>
                    <span>{new Date(caseDetail.createdAt).toLocaleString()}</span>
                  </div>
                </div>
              </div>

              {/* Sección: Reglas Activadas (Motivos del Review) */}
              <div className="case-detail-section">
                <div className="case-detail-section-title">
                  Reglas Activadas ({caseDetail.triggeredRules?.length || 0})
                </div>
                {caseDetail.triggeredRules && caseDetail.triggeredRules.length > 0 ? (
                  caseDetail.triggeredRules.map((rule, idx) => (
                    <div key={idx} className="case-rule-item">
                      <div>
                        <div className="case-rule-item-name">{rule.ruleName}</div>
                        <div className="case-rule-item-desc">{rule.description || 'Disparo por sospecha estadística'}</div>
                      </div>
                      <div className="case-rule-item-points">+{rule.points} pts</div>
                    </div>
                  ))
                ) : (
                  <div style={{ fontSize: '0.85rem', color: '#64748b' }}>
                    Sin reglas específicas individuales disparadas.
                  </div>
                )}
              </div>

              {/* Sección de Resolución o Auditoría */}
              {isResolved ? (
                <div className="case-detail-section" style={{ backgroundColor: '#f1f5f9' }}>
                  <div className="case-detail-section-title">Dictamen del Caso (Inmutable)</div>
                  <div style={{ marginBottom: '0.5rem' }}>
                    <span
                      className={`badge-status ${
                        caseDetail.resolution === 'APPROVED' ? 'badge-resolution-approved' : 'badge-resolution-rejected'
                      }`}
                    >
                      {caseDetail.resolution === 'APPROVED' ? 'APROBADO' : 'RECHAZADO'}
                    </span>
                  </div>
                  <div style={{ fontSize: '0.875rem', color: '#334155', marginBottom: '0.5rem' }}>
                    <strong>Comentario de resolución:</strong> {caseDetail.resolutionNotes}
                  </div>
                  <div style={{ fontSize: '0.8rem', color: '#64748b' }}>
                    Resuelto por: {caseDetail.resolvedByName || 'Analista'} el{' '}
                    {caseDetail.resolvedAt ? new Date(caseDetail.resolvedAt).toLocaleString() : '-'}
                  </div>
                </div>
              ) : (
                <div className="case-resolution-box">
                  <div className="case-detail-section-title">Dictamen de Resolución</div>
                  <div className="case-resolution-choice">
                    <button
                      type="button"
                      className={`case-choice-btn case-choice-approve ${resolution === 'APPROVED' ? 'selected' : ''}`}
                      onClick={() => setResolution('APPROVED')}
                    >
                      Aprobar Transacción
                    </button>
                    <button
                      type="button"
                      className={`case-choice-btn case-choice-reject ${resolution === 'REJECTED' ? 'selected' : ''}`}
                      onClick={() => setResolution('REJECTED')}
                    >
                      Rechazar como Fraude
                    </button>
                  </div>

                  <div style={{ marginBottom: '0.5rem' }}>
                    <label style={{ display: 'block', fontSize: '0.8rem', fontWeight: 600, color: '#475569', marginBottom: '0.25rem' }}>
                      Justificación / Comentario Obligatorio *
                    </label>
                    <textarea
                      className="case-comment-textarea"
                      placeholder="Indique la justificación técnica y análisis de riesgo efectuado..."
                      value={comment}
                      onChange={(e) => setComment(e.target.value)}
                      disabled={actionLoading}
                    />
                    {validationError && (
                      <div style={{ color: '#dc2626', fontSize: '0.8rem', marginTop: '0.25rem' }}>
                        {validationError}
                      </div>
                    )}
                  </div>
                </div>
              )}
            </>
          ) : null}
        </div>

        <div className="case-modal-footer">
          <button className="cases-btn-outline" onClick={onClose} disabled={actionLoading}>
            Cerrar
          </button>
          {!isResolved && caseDetail && (
            <button
              className="cases-btn-action"
              onClick={handleConfirmResolve}
              disabled={actionLoading || loading}
            >
              {actionLoading ? 'Guardando dictamen...' : 'Confirmar Resolución'}
            </button>
          )}
        </div>
      </div>
    </div>
  )
}
