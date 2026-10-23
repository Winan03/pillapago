import React, { useState } from 'react'
import { DraftDiffResponse } from '../../api/rules'

interface PublishVersionModalProps {
  isOpen: boolean
  onClose: () => void
  onConfirm: (description: string) => Promise<void>
  diff: DraftDiffResponse | null
  loading: boolean
}

export const PublishVersionModal: React.FC<PublishVersionModalProps> = ({
  isOpen,
  onClose,
  onConfirm,
  diff,
  loading,
}) => {
  const [description, setDescription] = useState<string>('')
  const [isSubmitting, setIsSubmitting] = useState<boolean>(false)
  const [error, setError] = useState<string | null>(null)

  if (!isOpen || !diff) return null

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setIsSubmitting(true)
    setError(null)
    try {
      await onConfirm(description)
      onClose()
    } catch (err: any) {
      setError(err?.response?.data?.detail || err?.message || 'Error al publicar la versión')
    } finally {
      setIsSubmitting(false)
    }
  }

  const hasChanges =
    diff.addedRules.length > 0 ||
    diff.modifiedRules.length > 0 ||
    diff.deactivatedRules.length > 0

  return (
    <div
      style={{
        position: 'fixed',
        inset: 0,
        backgroundColor: 'rgba(15, 23, 42, 0.6)',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        zIndex: 50,
        padding: '1rem',
      }}
    >
      <div
        style={{
          backgroundColor: '#ffffff',
          borderRadius: '8px',
          width: '100%',
          maxWidth: '680px',
          maxHeight: '90vh',
          display: 'flex',
          flexDirection: 'column',
          boxShadow: '0 20px 25px -5px rgba(0, 0, 0, 0.1)',
          border: '1px solid #e2e8f0',
        }}
      >
        {/* Encabezado */}
        <div
          style={{
            padding: '1.25rem 1.5rem',
            borderBottom: '1px solid #e2e8f0',
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
          }}
        >
          <div>
            <h2 style={{ margin: 0, fontSize: '1.25rem', fontWeight: 600, color: '#0f172a' }}>
              Publicación de Conjunto de Reglas
            </h2>
            <p style={{ margin: '0.25rem 0 0 0', fontSize: '0.875rem', color: '#64748b' }}>
              Transición de versión activa: <strong>{diff.currentActiveVersion}</strong> &rarr;{' '}
              <strong style={{ color: '#2563eb' }}>{diff.nextProposedVersion}</strong>
            </p>
          </div>
          <button
            onClick={onClose}
            disabled={isSubmitting}
            style={{
              background: 'none',
              border: 'none',
              fontSize: '1.25rem',
              color: '#94a3b8',
              cursor: 'pointer',
              padding: '0.25rem',
            }}
          >
            &times;
          </button>
        </div>

        {/* Cuerpo / Resumen de Cambios */}
        <div style={{ padding: '1.5rem', overflowY: 'auto', flex: 1 }}>
          {error && (
            <div
              style={{
                backgroundColor: '#fef2f2',
                color: '#991b1b',
                padding: '0.75rem',
                borderRadius: '6px',
                fontSize: '0.875rem',
                marginBottom: '1rem',
                border: '1px solid #fecaca',
              }}
            >
              {error}
            </div>
          )}

          {/* Tarjetas métricas */}
          <div
            style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(3, 1fr)',
              gap: '1rem',
              marginBottom: '1.5rem',
            }}
          >
            <div
              style={{
                padding: '0.75rem 1rem',
                backgroundColor: '#f8fafc',
                borderRadius: '6px',
                border: '1px solid #e2e8f0',
              }}
            >
              <div style={{ fontSize: '0.75rem', color: '#64748b', textTransform: 'uppercase' }}>
                Reglas Activas
              </div>
              <div style={{ fontSize: '1.25rem', fontWeight: 700, color: '#0f172a' }}>
                {diff.totalRulesInDraft}
              </div>
            </div>
            <div
              style={{
                padding: '0.75rem 1rem',
                backgroundColor: '#f8fafc',
                borderRadius: '6px',
                border: '1px solid #e2e8f0',
              }}
            >
              <div style={{ fontSize: '0.75rem', color: '#64748b', textTransform: 'uppercase' }}>
                Puntos Totales
              </div>
              <div style={{ fontSize: '1.25rem', fontWeight: 700, color: '#0f172a' }}>
                {diff.totalPointsInDraft} pts
              </div>
            </div>
            <div
              style={{
                padding: '0.75rem 1rem',
                backgroundColor: '#f8fafc',
                borderRadius: '6px',
                border: '1px solid #e2e8f0',
              }}
            >
              <div style={{ fontSize: '0.75rem', color: '#64748b', textTransform: 'uppercase' }}>
                Modificaciones
              </div>
              <div style={{ fontSize: '1.25rem', fontWeight: 700, color: '#2563eb' }}>
                {diff.addedRules.length + diff.modifiedRules.length + diff.deactivatedRules.length}
              </div>
            </div>
          </div>

          {!hasChanges ? (
            <div
              style={{
                padding: '1.25rem',
                backgroundColor: '#f8fafc',
                borderRadius: '6px',
                border: '1px solid #e2e8f0',
                color: '#475569',
                fontSize: '0.875rem',
                textAlign: 'center',
              }}
            >
              No se detectaron diferencias entre el borrador y la versión activa actual. Publicar creará una nueva versión snapshot.
            </div>
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
              {/* Reglas Nuevas */}
              {diff.addedRules.length > 0 && (
                <div>
                  <h4 style={{ margin: '0 0 0.5rem 0', fontSize: '0.875rem', color: '#0f172a', fontWeight: 600 }}>
                    Reglas Nuevas ({diff.addedRules.length})
                  </h4>
                  <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
                    {diff.addedRules.map((rule) => (
                      <div
                        key={rule.ruleId}
                        style={{
                          padding: '0.75rem',
                          backgroundColor: '#f0fdf4',
                          border: '1px solid #bbf7d0',
                          borderRadius: '6px',
                          display: 'flex',
                          justifyContent: 'space-between',
                          alignItems: 'center',
                        }}
                      >
                        <div>
                          <div style={{ fontWeight: 600, fontSize: '0.875rem', color: '#166534' }}>
                            {rule.name}
                          </div>
                          <div style={{ fontSize: '0.75rem', color: '#15803d' }}>
                            {rule.changeSummary}
                          </div>
                        </div>
                        <span
                          style={{
                            fontSize: '0.75rem',
                            fontWeight: 600,
                            padding: '0.25rem 0.5rem',
                            backgroundColor: '#dcfce7',
                            color: '#166534',
                            borderRadius: '4px',
                          }}
                        >
                          +{rule.points} pts
                        </span>
                      </div>
                    ))}
                  </div>
                </div>
              )}

              {/* Reglas Modificadas */}
              {diff.modifiedRules.length > 0 && (
                <div>
                  <h4 style={{ margin: '0 0 0.5rem 0', fontSize: '0.875rem', color: '#0f172a', fontWeight: 600 }}>
                    Reglas Modificadas ({diff.modifiedRules.length})
                  </h4>
                  <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
                    {diff.modifiedRules.map((rule) => (
                      <div
                        key={rule.ruleId}
                        style={{
                          padding: '0.75rem',
                          backgroundColor: '#eff6ff',
                          border: '1px solid #bfdbfe',
                          borderRadius: '6px',
                          display: 'flex',
                          justifyContent: 'space-between',
                          alignItems: 'center',
                        }}
                      >
                        <div>
                          <div style={{ fontWeight: 600, fontSize: '0.875rem', color: '#1e40af' }}>
                            {rule.name}
                          </div>
                          <div style={{ fontSize: '0.75rem', color: '#1d4ed8' }}>
                            {rule.changeSummary}
                          </div>
                        </div>
                        <span
                          style={{
                            fontSize: '0.75rem',
                            fontWeight: 600,
                            padding: '0.25rem 0.5rem',
                            backgroundColor: '#dbeafe',
                            color: '#1e40af',
                            borderRadius: '4px',
                          }}
                        >
                          {rule.previousPoints} &rarr; {rule.points} pts
                        </span>
                      </div>
                    ))}
                  </div>
                </div>
              )}

              {/* Reglas Desactivadas */}
              {diff.deactivatedRules.length > 0 && (
                <div>
                  <h4 style={{ margin: '0 0 0.5rem 0', fontSize: '0.875rem', color: '#0f172a', fontWeight: 600 }}>
                    Reglas Desactivadas ({diff.deactivatedRules.length})
                  </h4>
                  <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
                    {diff.deactivatedRules.map((rule) => (
                      <div
                        key={rule.ruleId}
                        style={{
                          padding: '0.75rem',
                          backgroundColor: '#f8fafc',
                          border: '1px solid #cbd5e1',
                          borderRadius: '6px',
                          display: 'flex',
                          justifyContent: 'space-between',
                          alignItems: 'center',
                        }}
                      >
                        <div>
                          <div style={{ fontWeight: 600, fontSize: '0.875rem', color: '#475569' }}>
                            {rule.name}
                          </div>
                          <div style={{ fontSize: '0.75rem', color: '#64748b' }}>
                            {rule.changeSummary}
                          </div>
                        </div>
                        <span
                          style={{
                            fontSize: '0.75rem',
                            fontWeight: 600,
                            padding: '0.25rem 0.5rem',
                            backgroundColor: '#e2e8f0',
                            color: '#475569',
                            borderRadius: '4px',
                          }}
                        >
                          Inactiva
                        </span>
                      </div>
                    ))}
                  </div>
                </div>
              )}
            </div>
          )}

          {/* Campo de descripción */}
          <div style={{ marginTop: '1.5rem' }}>
            <label
              htmlFor="version-desc"
              style={{ display: 'block', fontSize: '0.875rem', fontWeight: 600, color: '#334155', marginBottom: '0.5rem' }}
            >
              Descripción o Motivo de la Versión (opcional)
            </label>
            <textarea
              id="version-desc"
              rows={3}
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              placeholder="Ej. Ajuste de pesos por campaña Black Friday o actualización de umbrales..."
              style={{
                width: '100%',
                padding: '0.5rem 0.75rem',
                borderRadius: '6px',
                border: '1px solid #cbd5e1',
                fontSize: '0.875rem',
                fontFamily: 'inherit',
                resize: 'vertical',
                boxSizing: 'border-box',
              }}
            />
          </div>
        </div>

        {/* Pie de modal */}
        <div
          style={{
            padding: '1rem 1.5rem',
            borderTop: '1px solid #e2e8f0',
            display: 'flex',
            justifyContent: 'flex-end',
            gap: '0.75rem',
            backgroundColor: '#f8fafc',
            borderBottomLeftRadius: '8px',
            borderBottomRightRadius: '8px',
          }}
        >
          <button
            type="button"
            onClick={onClose}
            disabled={isSubmitting}
            style={{
              padding: '0.5rem 1rem',
              backgroundColor: '#ffffff',
              border: '1px solid #cbd5e1',
              borderRadius: '6px',
              fontSize: '0.875rem',
              fontWeight: 500,
              color: '#334155',
              cursor: 'pointer',
            }}
          >
            Cancelar
          </button>
          <button
            type="button"
            onClick={handleSubmit}
            disabled={isSubmitting || loading}
            style={{
              padding: '0.5rem 1.25rem',
              backgroundColor: '#2563eb',
              border: 'none',
              borderRadius: '6px',
              fontSize: '0.875rem',
              fontWeight: 600,
              color: '#ffffff',
              cursor: isSubmitting || loading ? 'not-allowed' : 'pointer',
              opacity: isSubmitting || loading ? 0.7 : 1,
            }}
          >
            {isSubmitting ? 'Publicando...' : `Confirmar y Publicar ${diff.nextProposedVersion}`}
          </button>
        </div>
      </div>
    </div>
  )
}
