import React, { useState, useEffect } from 'react'
import { FieldMetadata, CreateRulePayload, Rule, Condition } from '../../api/rules'

interface RuleModalProps {
  isOpen: boolean
  onClose: () => void
  onSave: (payload: CreateRulePayload) => Promise<void>
  catalog: FieldMetadata[]
  ruleToEdit?: Rule | null
}

const DEFAULT_CONDITION: Condition = {
  field: 'device_accounts_24h',
  operator: '>',
  value: '3',
}

export const RuleModal: React.FC<RuleModalProps> = ({
  isOpen,
  onClose,
  onSave,
  catalog,
  ruleToEdit,
}) => {
  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const [points, setPoints] = useState<number>(40)
  const [active, setActive] = useState<boolean>(true)
  const [conditions, setConditions] = useState<Condition[]>([{ ...DEFAULT_CONDITION }])
  const [loading, setLoading] = useState(false)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)

  useEffect(() => {
    if (ruleToEdit) {
      setName(ruleToEdit.name)
      setDescription(ruleToEdit.description || '')
      setPoints(ruleToEdit.points)
      setActive(ruleToEdit.active)
      setConditions(
        ruleToEdit.conditions.length > 0
          ? ruleToEdit.conditions.map((c) => ({ ...c }))
          : [{ ...DEFAULT_CONDITION }]
      )
    } else {
      setName('')
      setDescription('')
      setPoints(40)
      setActive(true)
      setConditions([{ ...DEFAULT_CONDITION }])
    }
    setErrorMessage(null)
  }, [ruleToEdit, isOpen])

  if (!isOpen) return null

  const getFieldMeta = (fieldName: string) => {
    return catalog.find((c) => c.field.toLowerCase() === fieldName.toLowerCase())
  }

  const handleFieldChange = (index: number, newField: string) => {
    const meta = getFieldMeta(newField)
    const allowedOps = meta?.allowedOperators || ['==']
    const updated = [...conditions]
    updated[index] = {
      field: newField,
      operator: allowedOps.includes(updated[index].operator) ? updated[index].operator : allowedOps[0],
      value: '',
    }
    setConditions(updated)
  }

  const handleOperatorChange = (index: number, newOperator: string) => {
    const updated = [...conditions]
    updated[index].operator = newOperator
    setConditions(updated)
  }

  const handleValueChange = (index: number, newValue: string) => {
    const updated = [...conditions]
    updated[index].value = newValue
    setConditions(updated)
  }

  const addCondition = () => {
    if (conditions.length < 3) {
      setConditions([...conditions, { field: 'amount', operator: '>', value: '1000' }])
    }
  }

  const removeCondition = (index: number) => {
    if (conditions.length > 1) {
      setConditions(conditions.filter((_, i) => i !== index))
    }
  }

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setErrorMessage(null)

    if (!name.trim()) {
      setErrorMessage('El nombre de la regla es obligatorio')
      return
    }

    if (points < 0 || points > 100) {
      setErrorMessage('Los puntos de riesgo deben estar entre 0 y 100')
      return
    }

    for (let i = 0; i < conditions.length; i++) {
      const c = conditions[i]
      if (!c.field || !c.operator || !c.value.trim()) {
        setErrorMessage(`La condición #${i + 1} tiene campos incompletos`)
        return
      }
    }

    setLoading(true)
    try {
      await onSave({
        name: name.trim(),
        description: description.trim(),
        points,
        active,
        conditions,
      })
      onClose()
    } catch (err: any) {
      const detail =
        err?.response?.data?.userMessage ||
        err?.response?.data?.detail ||
        err?.message ||
        'Error al guardar la regla'
      setErrorMessage(detail)
    } finally {
      setLoading(false)
    }
  }

  // Previsualización en lenguaje natural
  const conditionsPreview = conditions
    .map((c) => `${c.field} ${c.operator} ${c.value || '?'}`)
    .join(' Y ')

  return (
    <div style={modalBackdropStyle}>
      <div style={modalContentStyle}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1.5rem' }}>
          <h2 style={{ margin: 0, fontSize: '1.4rem', color: '#0f172a' }}>
            {ruleToEdit ? 'Editar Regla (Borrador)' : 'Crear Nueva Regla Antifraude'}
          </h2>
          <button
            onClick={onClose}
            style={{ background: 'transparent', border: 'none', fontSize: '1.5rem', cursor: 'pointer', color: '#64748b' }}
          >
            &times;
          </button>
        </div>

        {errorMessage && (
          <div style={{ backgroundColor: '#fee2e2', border: '1px solid #f87171', color: '#991b1b', padding: '0.75rem', borderRadius: '6px', marginBottom: '1rem', fontSize: '0.9rem' }}>
            ⚠️ {errorMessage}
          </div>
        )}

        <form onSubmit={handleSubmit}>
          {/* Nombre y Descripción */}
          <div style={{ marginBottom: '1rem' }}>
            <label style={labelStyle}>Nombre de la Regla *</label>
            <input
              type="text"
              placeholder="Ej: Dispositivo sospechoso en múltiples cuentas"
              value={name}
              onChange={(e) => setName(e.target.value)}
              style={inputStyle}
              required
            />
          </div>

          <div style={{ marginBottom: '1rem' }}>
            <label style={labelStyle}>Descripción / Motivo</label>
            <textarea
              placeholder="Explicación del riesgo detectado por la regla"
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              style={{ ...inputStyle, minHeight: '60px', resize: 'vertical' }}
            />
          </div>

          {/* Puntos de Riesgo y Estado */}
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1rem', marginBottom: '1.5rem' }}>
            <div>
              <label style={labelStyle}>
                Puntos de Riesgo (0 a 100): <strong style={{ color: points >= 70 ? '#dc2626' : points >= 30 ? '#d97706' : '#16a34a' }}>+{points} pts</strong>
              </label>
              <input
                type="range"
                min="0"
                max="100"
                value={points}
                onChange={(e) => setPoints(Number(e.target.value))}
                style={{ width: '100%', cursor: 'pointer' }}
              />
            </div>
            <div>
              <label style={labelStyle}>Estado Inicial</label>
              <label style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginTop: '0.5rem', cursor: 'pointer' }}>
                <input
                  type="checkbox"
                  checked={active}
                  onChange={(e) => setActive(e.target.checked)}
                />
                <span>{active ? 'Activa' : 'Inactiva'} (en Borrador)</span>
              </label>
            </div>
          </div>

          {/* Constructor Guiado de Condiciones (Máx 3) */}
          <div style={{ marginBottom: '1.5rem', border: '1px solid #e2e8f0', borderRadius: '8px', padding: '1rem', backgroundColor: '#f8fafc' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.75rem' }}>
              <h4 style={{ margin: 0, fontSize: '1rem', color: '#1e293b' }}>
                Condiciones de Activación (Lógica AND) — {conditions.length}/3
              </h4>
              {conditions.length < 3 && (
                <button
                  type="button"
                  onClick={addCondition}
                  style={secondaryButtonStyle}
                >
                  + Agregar condición (AND)
                </button>
              )}
            </div>

            {conditions.map((cond, idx) => {
              const meta = getFieldMeta(cond.field)
              const allowedOps = meta?.allowedOperators || ['>', '<', '>=', '<=', '==', '!=', 'in']

              return (
                <div key={idx} style={{ display: 'flex', gap: '0.5rem', alignItems: 'center', marginBottom: '0.75rem' }}>
                  {idx > 0 && <span style={{ fontWeight: 'bold', color: '#64748b', fontSize: '0.85rem' }}>Y</span>}

                  {/* Dropdown de Campo */}
                  <select
                    value={cond.field}
                    onChange={(e) => handleFieldChange(idx, e.target.value)}
                    style={{ ...selectStyle, flex: 2 }}
                  >
                    {catalog.map((f) => (
                      <option key={f.field} value={f.field}>
                        [{f.category}] {f.label} ({f.field})
                      </option>
                    ))}
                  </select>

                  {/* Dropdown de Operador */}
                  <select
                    value={cond.operator}
                    onChange={(e) => handleOperatorChange(idx, e.target.value)}
                    style={{ ...selectStyle, flex: 1 }}
                  >
                    {allowedOps.map((op) => (
                      <option key={op} value={op}>
                        {op}
                      </option>
                    ))}
                  </select>

                  {/* Input de Valor */}
                  <input
                    type="text"
                    value={cond.value}
                    placeholder={meta?.placeholder || 'Valor'}
                    onChange={(e) => handleValueChange(idx, e.target.value)}
                    style={{ ...inputStyle, flex: 2 }}
                    required
                  />

                  {/* Botón Eliminar condición */}
                  {conditions.length > 1 && (
                    <button
                      type="button"
                      onClick={() => removeCondition(idx)}
                      title="Eliminar condición"
                      style={{ background: 'transparent', border: 'none', color: '#ef4444', cursor: 'pointer', fontSize: '1.2rem', padding: '0 0.5rem' }}
                    >
                      🗑️
                    </button>
                  )}
                </div>
              )
            })}
          </div>

          {/* Previsualización en Lenguaje Natural */}
          <div style={{ backgroundColor: '#eff6ff', border: '1px solid #bfdbfe', borderRadius: '6px', padding: '0.75rem', marginBottom: '1.5rem', fontSize: '0.9rem', color: '#1e40af' }}>
            💡 <strong>Vista previa:</strong> Si <code>{conditionsPreview}</code> &rarr; suma <strong>+{points} puntos</strong> de riesgo.
          </div>

          {/* Botones de Acción */}
          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '1rem' }}>
            <button
              type="button"
              onClick={onClose}
              disabled={loading}
              style={{ ...secondaryButtonStyle, padding: '0.6rem 1.2rem' }}
            >
              Cancelar
            </button>
            <button
              type="submit"
              disabled={loading}
              style={{ ...primaryButtonStyle, padding: '0.6rem 1.4rem' }}
            >
              {loading ? 'Guardando...' : ruleToEdit ? 'Actualizar Regla' : 'Guardar Regla (Borrador)'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}

const modalBackdropStyle: React.CSSProperties = {
  position: 'fixed',
  top: 0,
  left: 0,
  right: 0,
  bottom: 0,
  backgroundColor: 'rgba(15, 23, 42, 0.65)',
  display: 'flex',
  alignItems: 'center',
  justifyContent: 'center',
  zIndex: 1000,
  padding: '1rem',
}

const modalContentStyle: React.CSSProperties = {
  backgroundColor: '#ffffff',
  borderRadius: '12px',
  width: '100%',
  maxWidth: '680px',
  padding: '2rem',
  boxShadow: '0 20px 25px -5px rgba(0, 0, 0, 0.1), 0 10px 10px -5px rgba(0, 0, 0, 0.04)',
  maxHeight: '90vh',
  overflowY: 'auto',
}

const labelStyle: React.CSSProperties = {
  display: 'block',
  fontSize: '0.875rem',
  fontWeight: 600,
  color: '#334155',
  marginBottom: '0.35rem',
}

const inputStyle: React.CSSProperties = {
  width: '100%',
  padding: '0.6rem 0.8rem',
  border: '1px solid #cbd5e1',
  borderRadius: '6px',
  fontSize: '0.925rem',
  color: '#0f172a',
  backgroundColor: '#ffffff',
  outline: 'none',
  boxSizing: 'border-box',
}

const selectStyle: React.CSSProperties = {
  padding: '0.6rem 0.8rem',
  border: '1px solid #cbd5e1',
  borderRadius: '6px',
  fontSize: '0.925rem',
  color: '#0f172a',
  backgroundColor: '#ffffff',
  outline: 'none',
  boxSizing: 'border-box',
}

const primaryButtonStyle: React.CSSProperties = {
  backgroundColor: '#2563eb',
  color: '#ffffff',
  border: 'none',
  borderRadius: '6px',
  fontWeight: 600,
  cursor: 'pointer',
}

const secondaryButtonStyle: React.CSSProperties = {
  backgroundColor: '#f1f5f9',
  color: '#334155',
  border: '1px solid #cbd5e1',
  borderRadius: '6px',
  fontWeight: 500,
  cursor: 'pointer',
  padding: '0.4rem 0.8rem',
  fontSize: '0.85rem',
}
