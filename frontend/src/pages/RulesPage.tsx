import React, { useEffect, useState } from 'react'
import { rulesApi, Rule, FieldMetadata, CreateRulePayload } from '../api/rules'
import { RuleModal } from '../components/rules/RuleModal'
import { useAuth } from '../hooks/useAuth'

export default function RulesPage() {
  const { user } = useAuth()
  const [rules, setRules] = useState<Rule[]>([])
  const [catalog, setCatalog] = useState<FieldMetadata[]>([])
  const [filterStatus, setFilterStatus] = useState<string>('ALL')
  const [loading, setLoading] = useState<boolean>(true)
  const [error, setError] = useState<string | null>(null)
  const [isModalOpen, setIsModalOpen] = useState<boolean>(false)
  const [ruleToEdit, setRuleToEdit] = useState<Rule | null>(null)

  const loadData = async () => {
    setLoading(true)
    setError(null)
    try {
      const [rulesData, catalogData] = await Promise.all([
        rulesApi.getRules(filterStatus === 'ALL' ? undefined : filterStatus),
        rulesApi.getCatalog(),
      ])
      setRules(rulesData)
      setCatalog(catalogData)
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

  const draftCount = rules.filter((r) => r.status === 'DRAFT').length
  const activeCount = rules.filter((r) => r.active).length

  return (
    <main style={{ padding: '2rem', maxWidth: '1200px', margin: '0 auto', fontFamily: 'inherit' }}>
      {/* Header */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '1.5rem' }}>
        <div>
          <h1 style={{ margin: '0 0 0.5rem 0', fontSize: '1.8rem', color: '#0f172a' }}>
            Reglas de Riesgo Antifraude
          </h1>
          <p style={{ margin: 0, color: '#64748b', fontSize: '0.95rem' }}>
            HU-07 · Crea y edita reglas con lenguaje acotado. Las nuevas reglas se guardan en <strong>Borrador (DRAFT)</strong> y no afectan decisiones en vivo hasta su publicación formal (HU-08).
          </p>
        </div>
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

      {/* KPI Cards */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: '1rem', marginBottom: '1.5rem' }}>
        <div style={kpiCardStyle}>
          <div style={{ color: '#64748b', fontSize: '0.85rem' }}>Total de Reglas</div>
          <div style={{ fontSize: '1.5rem', fontWeight: 700, color: '#0f172a', marginTop: '0.25rem' }}>{rules.length}</div>
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
          <div style={{ fontSize: '1.1rem', fontWeight: 600, color: '#2563eb', marginTop: '0.4rem' }}>
            {user?.role || 'RISK_ANALYST'}
          </div>
        </div>
      </div>

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
      {error && (
        <div style={{ backgroundColor: '#fee2e2', border: '1px solid #f87171', color: '#991b1b', padding: '1rem', borderRadius: '8px', marginBottom: '1rem' }}>
          ⚠️ {error}
        </div>
      )}

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
                      {rule.active ? '● Sí' : '○ No'}
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

      {/* Modal Guiado */}
      <RuleModal
        isOpen={isModalOpen}
        onClose={() => setIsModalOpen(false)}
        onSave={handleSaveRule}
        catalog={catalog}
        ruleToEdit={ruleToEdit}
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
