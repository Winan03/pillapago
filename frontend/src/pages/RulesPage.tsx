import React, { useEffect, useState } from 'react'
import {
  rulesApi,
  Rule,
  FieldMetadata,
  CreateRulePayload,
  DraftDiffResponse,
  RuleSetVersionSummary,
} from '../api/rules'
import { RuleModal } from '../components/rules/RuleModal'
import { PublishVersionModal } from '../components/rules/PublishVersionModal'
import { useAuth } from '../hooks/useAuth'

export default function RulesPage() {
  const { user } = useAuth()
  const [rules, setRules] = useState<Rule[]>([])
  const [catalog, setCatalog] = useState<FieldMetadata[]>([])
  const [filterStatus, setFilterStatus] = useState<string>('ALL')
  const [loading, setLoading] = useState<boolean>(true)
  const [error, setError] = useState<string | null>(null)
  const [successMessage, setSuccessMessage] = useState<string | null>(null)

  // Modales
  const [isModalOpen, setIsModalOpen] = useState<boolean>(false)
  const [ruleToEdit, setRuleToEdit] = useState<Rule | null>(null)

  // Versionado y Rollback (HU-08)
  const [isPublishModalOpen, setIsPublishModalOpen] = useState<boolean>(false)
  const [draftDiff, setDraftDiff] = useState<DraftDiffResponse | null>(null)
  const [diffLoading, setDiffLoading] = useState<boolean>(false)
  const [versionsHistory, setVersionsHistory] = useState<RuleSetVersionSummary[]>([])
  const [showHistory, setShowHistory] = useState<boolean>(false)
  const [rollbackLoading, setRollbackLoading] = useState<boolean>(false)

  const loadData = async () => {
    setLoading(true)
    setError(null)
    try {
      const [rulesData, catalogData, historyData] = await Promise.all([
        rulesApi.getRules(filterStatus === 'ALL' ? undefined : filterStatus),
        rulesApi.getCatalog(),
        rulesApi.getHistory().catch(() => []),
      ])
      setRules(rulesData)
      setCatalog(catalogData)
      setVersionsHistory(historyData)
    } catch (err: any) {
      setError(err?.response?.data?.detail || err?.message || 'Error al cargar las reglas')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    loadData()
  }, [filterStatus])

  const handleOpenCreateModal = () => {
    setRuleToEdit(null)
    setIsModalOpen(true)
  }

  const handleOpenEditModal = (rule: Rule) => {
    setRuleToEdit(rule)
    setIsModalOpen(true)
  }

  const handleSaveRule = async (payload: CreateRulePayload) => {
    if (ruleToEdit) {
      await rulesApi.updateRule(ruleToEdit.id, payload)
    } else {
      await rulesApi.createRule(payload)
    }
    await loadData()
  }

  const handleOpenPublishModal = async () => {
    setDiffLoading(true)
    setError(null)
    try {
      const diff = await rulesApi.getDraftDiff()
      setDraftDiff(diff)
      setIsPublishModalOpen(true)
    } catch (err: any) {
      setError(err?.response?.data?.detail || err?.message || 'Error al calcular diferencias para publicación')
    } finally {
      setDiffLoading(false)
    }
  }

  const handleConfirmPublish = async (description: string) => {
    try {
      const published = await rulesApi.publishVersion(description)
      setSuccessMessage(`Versión ${published.versionName} publicada exitosamente con ${published.rulesCount} reglas activas.`)
      await loadData()
      setTimeout(() => setSuccessMessage(null), 5000)
    } catch (err: any) {
      throw err
    }
  }

  const handleRollback = async (targetVersion: string) => {
    if (!window.confirm(`¿Estás seguro de reactivar la versión ${targetVersion}? El motor de evaluación cambiará inmediatamente a este snapshot histórico.`)) {
      return
    }
    setRollbackLoading(true)
    setError(null)
    try {
      const restored = await rulesApi.rollbackVersion(targetVersion)
      setSuccessMessage(`Rollback exitoso: Versión ${restored.versionName} ha sido reactivada.`)
      await loadData()
      setTimeout(() => setSuccessMessage(null), 5000)
    } catch (err: any) {
      setError(err?.response?.data?.detail || err?.message || 'Error al ejecutar rollback')
    } finally {
      setRollbackLoading(false)
    }
  }

  const draftCount = rules.filter((r) => r.status === 'DRAFT').length
  const activeCount = rules.filter((r) => r.active).length
  const activeVersion = versionsHistory.find((v) => v.status === 'ACTIVE')

  return (
    <main style={{ padding: '2rem', maxWidth: '1200px', margin: '0 auto', fontFamily: 'inherit' }}>
      {/* Header */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '1.5rem', flexWrap: 'wrap', gap: '1rem' }}>
        <div>
          <h1 style={{ margin: '0 0 0.5rem 0', fontSize: '1.8rem', color: '#0f172a' }}>
            Reglas de Riesgo Antifraude
          </h1>
          <p style={{ margin: 0, color: '#64748b', fontSize: '0.95rem' }}>
            Crea, edita y versiona reglas de decisión. La publicación genera un conjunto inmutable con recarga atómica en memoria y soporte de rollback sin downtime.
          </p>
        </div>
        <div style={{ display: 'flex', gap: '0.75rem' }}>
          <button
            onClick={() => setShowHistory(!showHistory)}
            style={{
              backgroundColor: '#ffffff',
              color: '#334155',
              border: '1px solid #cbd5e1',
              borderRadius: '8px',
              padding: '0.75rem 1.25rem',
              fontSize: '0.95rem',
              fontWeight: 600,
              cursor: 'pointer',
            }}
          >
            {showHistory ? 'Ocultar Historial' : 'Historial de Versiones'}
          </button>

          <button
            onClick={handleOpenPublishModal}
            disabled={diffLoading || draftCount === 0 && !activeVersion}
            style={{
              backgroundColor: '#0f172a',
              color: '#ffffff',
              border: 'none',
              borderRadius: '8px',
              padding: '0.75rem 1.4rem',
              fontSize: '0.95rem',
              fontWeight: 600,
              cursor: diffLoading ? 'not-allowed' : 'pointer',
              opacity: diffLoading ? 0.7 : 1,
            }}
          >
            {diffLoading ? 'Calculando...' : 'Publicar Versión'}
          </button>

          <button
            onClick={handleOpenCreateModal}
            style={{
              backgroundColor: '#2563eb',
              color: '#ffffff',
              border: 'none',
              borderRadius: '8px',
              padding: '0.75rem 1.4rem',
              fontSize: '0.95rem',
              fontWeight: 600,
              cursor: 'pointer',
              boxShadow: '0 4px 6px -1px rgba(37, 99, 235, 0.2)',
            }}
          >
            + Nueva Regla
          </button>
        </div>
      </div>

      {/* Alertas */}
      {successMessage && (
        <div style={{ backgroundColor: '#f0fdf4', border: '1px solid #bbf7d0', color: '#166534', padding: '1rem', borderRadius: '8px', marginBottom: '1.5rem', fontWeight: 500 }}>
          {successMessage}
        </div>
      )}

      {error && (
        <div style={{ backgroundColor: '#fee2e2', border: '1px solid #f87171', color: '#991b1b', padding: '1rem', borderRadius: '8px', marginBottom: '1.5rem' }}>
          {error}
        </div>
      )}

      {/* KPI Cards */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: '1rem', marginBottom: '1.5rem' }}>
        <div style={kpiCardStyle}>
          <div style={{ color: '#64748b', fontSize: '0.85rem' }}>Versión Activa en Memoria</div>
          <div style={{ fontSize: '1.5rem', fontWeight: 700, color: '#2563eb', marginTop: '0.25rem' }}>
            {activeVersion ? activeVersion.versionName : 'Sin publicar (v0)'}
          </div>
        </div>
        <div style={kpiCardStyle}>
          <div style={{ color: '#64748b', fontSize: '0.85rem' }}>Borradores Pendientes</div>
          <div style={{ fontSize: '1.5rem', fontWeight: 700, color: '#d97706', marginTop: '0.25rem' }}>{draftCount}</div>
        </div>
        <div style={kpiCardStyle}>
          <div style={{ color: '#64748b', fontSize: '0.85rem' }}>Reglas Habilitadas</div>
          <div style={{ fontSize: '1.5rem', fontWeight: 700, color: '#16a34a', marginTop: '0.25rem' }}>{activeCount}</div>
        </div>
        <div style={kpiCardStyle}>
          <div style={{ color: '#64748b', fontSize: '0.85rem' }}>Rol Autenticado</div>
          <div style={{ fontSize: '1.1rem', fontWeight: 600, color: '#0f172a', marginTop: '0.4rem' }}>
            {user?.role || 'RISK_ANALYST'}
          </div>
        </div>
      </div>

      {/* Panel de Historial de Versiones */}
      {showHistory && (
        <div style={{ backgroundColor: '#ffffff', border: '1px solid #cbd5e1', borderRadius: '8px', padding: '1.5rem', marginBottom: '1.5rem' }}>
          <h3 style={{ margin: '0 0 1rem 0', fontSize: '1.1rem', color: '#0f172a' }}>
            Historial de Versiones Publicadas (Rollback Controlado)
          </h3>
          {versionsHistory.length === 0 ? (
            <p style={{ margin: 0, color: '#64748b', fontSize: '0.9rem' }}>Aún no se han publicado versiones para este comercio.</p>
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
              {versionsHistory.map((ver) => (
                <div
                  key={ver.id}
                  style={{
                    display: 'flex',
                    justifyContent: 'space-between',
                    alignItems: 'center',
                    padding: '0.75rem 1rem',
                    backgroundColor: ver.status === 'ACTIVE' ? '#eff6ff' : '#f8fafc',
                    border: ver.status === 'ACTIVE' ? '1px solid #bfdbfe' : '1px solid #e2e8f0',
                    borderRadius: '6px',
                  }}
                >
                  <div>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                      <strong style={{ color: '#0f172a', fontSize: '1rem' }}>{ver.versionName}</strong>
                      <span
                        style={{
                          fontSize: '0.75rem',
                          padding: '0.15rem 0.5rem',
                          borderRadius: '4px',
                          fontWeight: 600,
                          backgroundColor: ver.status === 'ACTIVE' ? '#dbeafe' : '#e2e8f0',
                          color: ver.status === 'ACTIVE' ? '#1e40af' : '#64748b',
                        }}
                      >
                        {ver.status === 'ACTIVE' ? 'ACTIVA EN VIVO' : 'ARCHIVADA'}
                      </span>
                    </div>
                    <div style={{ fontSize: '0.8rem', color: '#64748b', marginTop: '0.25rem' }}>
                      {ver.description} &bull; {ver.rulesCount} reglas &bull; Creado por: {ver.createdByUsername} &bull; {new Date(ver.createdAt).toLocaleString()}
                    </div>
                  </div>

                  {ver.status === 'ARCHIVED' && (
                    <button
                      onClick={() => handleRollback(ver.versionName)}
                      disabled={rollbackLoading}
                      style={{
                        padding: '0.4rem 0.8rem',
                        backgroundColor: '#ffffff',
                        border: '1px solid #cbd5e1',
                        borderRadius: '6px',
                        fontSize: '0.8rem',
                        fontWeight: 600,
                        color: '#0f172a',
                        cursor: rollbackLoading ? 'not-allowed' : 'pointer',
                      }}
                    >
                      Revertir a esta versión
                    </button>
                  )}
                </div>
              ))}
            </div>
          )}
        </div>
      )}

      {/* Filtros */}
      <div style={{ display: 'flex', gap: '0.5rem', marginBottom: '1rem', borderBottom: '1px solid #e2e8f0', paddingBottom: '0.5rem' }}>
        {['ALL', 'DRAFT', 'PUBLISHED'].map((tab) => (
          <button
            key={tab}
            onClick={() => setFilterStatus(tab)}
            style={{
              background: 'transparent',
              border: 'none',
              padding: '0.5rem 1rem',
              cursor: 'pointer',
              fontWeight: filterStatus === tab ? 700 : 500,
              color: filterStatus === tab ? '#2563eb' : '#64748b',
              borderBottom: filterStatus === tab ? '2px solid #2563eb' : '2px solid transparent',
            }}
          >
            {tab === 'ALL' ? 'Todas' : tab === 'DRAFT' ? 'Borradores (DRAFT)' : 'Publicadas (PUBLISHED)'}
          </button>
        ))}
      </div>

      {/* Contenido / Tabla */}
      {loading ? (
        <div style={{ textAlign: 'center', padding: '3rem', color: '#64748b' }}>Cargando catálogo y reglas...</div>
      ) : rules.length === 0 ? (
        <div style={{ textAlign: 'center', padding: '3rem', backgroundColor: '#f8fafc', borderRadius: '8px', border: '1px dashed #cbd5e1' }}>
          <p style={{ color: '#64748b', fontSize: '1.1rem', margin: 0 }}>No se encontraron reglas en este estado.</p>
          <button
            onClick={handleOpenCreateModal}
            style={{ marginTop: '1rem', background: 'transparent', color: '#2563eb', border: '1px solid #2563eb', borderRadius: '6px', padding: '0.5rem 1rem', cursor: 'pointer', fontWeight: 600 }}
          >
            Crear la primera regla
          </button>
        </div>
      ) : (
        <div style={{ backgroundColor: '#ffffff', border: '1px solid #e2e8f0', borderRadius: '8px', overflow: 'hidden', boxShadow: '0 1px 3px rgba(0,0,0,0.05)' }}>
          <table style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left', fontSize: '0.9rem' }}>
            <thead>
              <tr style={{ backgroundColor: '#f8fafc', borderBottom: '1px solid #e2e8f0', color: '#475569', fontWeight: 600 }}>
                <th style={{ padding: '0.75rem 1rem' }}>Regla</th>
                <th style={{ padding: '0.75rem 1rem' }}>Condiciones (Lógica AND)</th>
                <th style={{ padding: '0.75rem 1rem' }}>Puntos</th>
                <th style={{ padding: '0.75rem 1rem' }}>Estado</th>
                <th style={{ padding: '0.75rem 1rem' }}>Activa</th>
                <th style={{ padding: '0.75rem 1rem', textAlign: 'right' }}>Acciones</th>
              </tr>
            </thead>
            <tbody>
              {rules.map((rule) => (
                <tr key={rule.id} style={{ borderBottom: '1px solid #f1f5f9' }}>
                  <td style={{ padding: '1rem' }}>
                    <div style={{ fontWeight: 600, color: '#0f172a' }}>{rule.name}</div>
                    {rule.description && (
                      <div style={{ color: '#64748b', fontSize: '0.8rem', marginTop: '0.2rem' }}>
                        {rule.description}
                      </div>
                    )}
                  </td>
                  <td style={{ padding: '1rem' }}>
                    <div style={{ display: 'flex', flexWrap: 'wrap', gap: '0.35rem' }}>
                      {rule.conditions.map((c, i) => (
                        <span
                          key={i}
                          style={{
                            backgroundColor: '#e0f2fe',
                            color: '#0369a1',
                            padding: '0.2rem 0.5rem',
                            borderRadius: '4px',
                            fontFamily: 'monospace',
                            fontSize: '0.85rem',
                          }}
                        >
                          {i > 0 && <strong style={{ color: '#0284c7' }}>AND </strong>}
                          {c.field} {c.operator} {c.value}
                        </span>
                      ))}
                    </div>
                  </td>
                  <td style={{ padding: '1rem' }}>
                    <span
                      style={{
                        fontWeight: 700,
                        color: rule.points >= 70 ? '#dc2626' : rule.points >= 30 ? '#d97706' : '#16a34a',
                      }}
                    >
                      +{rule.points} pts
                    </span>
                  </td>
                  <td style={{ padding: '1rem' }}>
                    <span
                      style={{
                        padding: '0.25rem 0.6rem',
                        borderRadius: '9999px',
                        fontSize: '0.75rem',
                        fontWeight: 700,
                        backgroundColor: rule.status === 'DRAFT' ? '#fef3c7' : '#dcfce7',
                        color: rule.status === 'DRAFT' ? '#92400e' : '#166534',
                      }}
                    >
                      {rule.status === 'DRAFT' ? 'BORRADOR' : 'PUBLICADA'}
                    </span>
                  </td>
                  <td style={{ padding: '1rem' }}>
                    <span style={{ color: rule.active ? '#16a34a' : '#94a3b8', fontWeight: 600 }}>
                      {rule.active ? 'Habilitada' : 'Deshabilitada'}
                    </span>
                  </td>
                  <td style={{ padding: '1rem', textAlign: 'right' }}>
                    {rule.status === 'DRAFT' ? (
                      <button
                        onClick={() => handleOpenEditModal(rule)}
                        style={{
                          backgroundColor: '#f1f5f9',
                          border: '1px solid #cbd5e1',
                          color: '#0f172a',
                          padding: '0.35rem 0.75rem',
                          borderRadius: '4px',
                          cursor: 'pointer',
                          fontWeight: 500,
                          fontSize: '0.85rem',
                        }}
                      >
                        Editar
                      </button>
                    ) : (
                      <span style={{ color: '#94a3b8', fontSize: '0.85rem' }}>Inmutable</span>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* Modal de Edición de Regla */}
      <RuleModal
        isOpen={isModalOpen}
        onClose={() => setIsModalOpen(false)}
        onSave={handleSaveRule}
        catalog={catalog}
        ruleToEdit={ruleToEdit}
      />

      {/* Modal de Publicación de Versión (HU-08) */}
      <PublishVersionModal
        isOpen={isPublishModalOpen}
        onClose={() => setIsPublishModalOpen(false)}
        onConfirm={handleConfirmPublish}
        diff={draftDiff}
        loading={diffLoading}
      />
    </main>
  )
}

const kpiCardStyle: React.CSSProperties = {
  backgroundColor: '#ffffff',
  border: '1px solid #e2e8f0',
  borderRadius: '8px',
  padding: '1rem',
  boxShadow: '0 1px 2px rgba(0,0,0,0.03)',
}
