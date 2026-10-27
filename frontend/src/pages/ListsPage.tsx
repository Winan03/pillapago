import { useEffect, useState, useMemo } from 'react'
import { listsApi, ListEntry, ListType, ListEntryType, CreateListEntryPayload } from '../api/lists'
import '../styles/lists.css'

/**
 * Pantalla Principal: Gestión de Listas de Bloqueo y Confianza (HU-13 / RF-09).
 * Exclusiva para analistas de riesgo (rol RISK_ANALYST).
 * Permite:
 * 1. Visualizar y alternar entre Lista de Bloqueo (BLOCK) y Lista de Confianza (ALLOW).
 * 2. Filtrar por tipo de identificador (IP, Dispositivo, Correo, Tarjeta).
 * 3. Agregar nuevos identificadores con justificación obligatoria y vencimiento opcional.
 * 4. Dar de baja entradas con actualización inmediata de la memoria.
 */
export default function ListsPage() {
  const [entries, setEntries] = useState<ListEntry[]>([])
  const [loading, setLoading] = useState<boolean>(true)
  const [error, setError] = useState<string | null>(null)
  const [successMessage, setSuccessMessage] = useState<string | null>(null)

  // Filtros
  const [activeTab, setActiveTab] = useState<ListType>('BLOCK')
  const [selectedEntryType, setSelectedEntryType] = useState<string>('ALL')
  const [searchQuery, setSearchQuery] = useState<string>('')

  // Modal de Alta
  const [isModalOpen, setIsModalOpen] = useState<boolean>(false)
  const [modalLoading, setModalLoading] = useState<boolean>(false)
  const [modalError, setModalError] = useState<string | null>(null)
  const [formData, setFormData] = useState<{
    listType: ListType
    entryType: ListEntryType
    value: string
    reason: string
    expiresAt: string
  }>({
    listType: 'BLOCK',
    entryType: 'IP',
    value: '',
    reason: '',
    expiresAt: '',
  })

  const fetchEntries = async () => {
    setLoading(true)
    setError(null)
    try {
      const data = await listsApi.getEntries()
      setEntries(data)
    } catch (err: any) {
      setError(err?.response?.data?.userMessage || err?.response?.data?.detail || 'Error al cargar las listas de control.')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    fetchEntries()
  }, [])

  // Métricas / KPIs
  const kpiMetrics = useMemo(() => {
    const totalBlock = entries.filter((e) => e.listType === 'BLOCK').length
    const totalAllow = entries.filter((e) => e.listType === 'ALLOW').length
    const activeEntries = entries.filter((e) => e.active).length
    return { totalBlock, totalAllow, activeEntries }
  }, [entries])

  // Entradas filtradas para la vista
  const filteredEntries = useMemo(() => {
    return entries.filter((item) => {
      if (item.listType !== activeTab) return false
      if (selectedEntryType !== 'ALL' && item.entryType !== selectedEntryType) return false
      if (searchQuery.trim() !== '') {
        const query = searchQuery.toLowerCase()
        const matchesMasked = item.valueMasked?.toLowerCase().includes(query)
        const matchesReason = item.reason?.toLowerCase().includes(query)
        if (!matchesMasked && !matchesReason) return false
      }
      return true
    })
  }, [entries, activeTab, selectedEntryType, searchQuery])

  const handleOpenModal = () => {
    setFormData({
      listType: activeTab,
      entryType: 'IP',
      value: '',
      reason: '',
      expiresAt: '',
    })
    setModalError(null)
    setIsModalOpen(true)
  }

  const handleCloseModal = () => {
    setIsModalOpen(false)
    setModalError(null)
  }

  const handleFormSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setModalError(null)

    if (formData.value.trim() === '') {
      setModalError('El valor del identificador es obligatorio.')
      return
    }
    if (formData.reason.trim().length < 5) {
      setModalError('El motivo debe contener al menos 5 caracteres.')
      return
    }

    setModalLoading(true)
    try {
      const payload: CreateListEntryPayload = {
        listType: formData.listType,
        entryType: formData.entryType,
        value: formData.value.trim(),
        reason: formData.reason.trim(),
      }
      if (formData.expiresAt.trim() !== '') {
        payload.expiresAt = new Date(formData.expiresAt).toISOString()
      }

      await listsApi.createEntry(payload)
      setSuccessMessage('Elemento incorporado exitosamente a la lista. Aplicará en la siguiente evaluación.')
      setIsModalOpen(false)
      await fetchEntries()
    } catch (err: any) {
      setModalError(err?.response?.data?.userMessage || err?.response?.data?.detail || 'Error al agregar elemento a la lista.')
    } finally {
      setModalLoading(false)
    }
  }

  const handleDelete = async (entry: ListEntry) => {
    const confirmDelete = window.confirm(
      `¿Deseas eliminar la entrada ${entry.valueMasked} de la lista ${entry.listType}? Dejará de aplicar de inmediato.`
    )
    if (!confirmDelete) return

    try {
      await listsApi.deleteEntry(entry.id)
      setSuccessMessage(`Entrada ${entry.valueMasked} removida exitosamente.`)
      await fetchEntries()
    } catch (err: any) {
      setError(err?.response?.data?.userMessage || err?.response?.data?.detail || 'Error al eliminar la entrada.')
    }
  }

  const getEntryTypeLabel = (type: ListEntryType) => {
    switch (type) {
      case 'IP':
        return 'Dirección IP'
      case 'DEVICE':
        return 'Dispositivo'
      case 'EMAIL':
        return 'Correo Electrónico'
      case 'CARD_FINGERPRINT':
        return 'Huella de Tarjeta'
      default:
        return type
    }
  }

  return (
    <div className="lists-page-container">
      {/* Encabezado */}
      <div className="lists-header">
        <div>
          <h1 className="lists-title">Listas de Bloqueo y Confianza</h1>
          <p className="lists-subtitle">
            Administración de listas negras (REJECT inmediato) y listas blancas (APPROVE) consultadas en memoria RAM.
          </p>
        </div>
        <div>
          <button className="btn-primary-action" onClick={handleOpenModal}>
            + Nueva Entrada
          </button>
        </div>
      </div>

      {/* Alertas */}
      {successMessage && (
        <div className="alert-feedback alert-success">
          {successMessage}
          <button
            style={{ float: 'right', background: 'none', border: 'none', cursor: 'pointer', fontWeight: 'bold' }}
            onClick={() => setSuccessMessage(null)}
          >
            ✕
          </button>
        </div>
      )}

      {error && (
        <div className="alert-feedback alert-error">
          {error}
          <button
            style={{ float: 'right', background: 'none', border: 'none', cursor: 'pointer', fontWeight: 'bold' }}
            onClick={() => setError(null)}
          >
            ✕
          </button>
        </div>
      )}

      {/* KPIs */}
      <div className="lists-kpi-grid">
        <div className="lists-kpi-card kpi-block">
          <div className="lists-kpi-label">Lista de Bloqueo (Blocklist)</div>
          <div className="lists-kpi-value">{kpiMetrics.totalBlock}</div>
        </div>
        <div className="lists-kpi-card kpi-allow">
          <div className="lists-kpi-label">Lista de Confianza (Allowlist)</div>
          <div className="lists-kpi-value">{kpiMetrics.totalAllow}</div>
        </div>
        <div className="lists-kpi-card">
          <div className="lists-kpi-label">Entradas Activas en Caché</div>
          <div className="lists-kpi-value">{kpiMetrics.activeEntries}</div>
        </div>
      </div>

      {/* Pestañas */}
      <div className="lists-tabs-wrapper">
        <button
          className={`lists-tab-button ${activeTab === 'BLOCK' ? 'active-tab-block' : ''}`}
          onClick={() => setActiveTab('BLOCK')}
        >
          Lista de Bloqueo (REJECT)
        </button>
        <button
          className={`lists-tab-button ${activeTab === 'ALLOW' ? 'active-tab-allow' : ''}`}
          onClick={() => setActiveTab('ALLOW')}
        >
          Lista de Confianza (ALLOW)
        </button>
      </div>

      {/* Barra de filtros */}
      <div className="lists-filters-bar">
        <input
          type="text"
          className="lists-search-input"
          placeholder="Buscar por valor enmascarado o motivo..."
          value={searchQuery}
          onChange={(e) => setSearchQuery(e.target.value)}
        />
        <select
          className="lists-select-input"
          value={selectedEntryType}
          onChange={(e) => setSelectedEntryType(e.target.value)}
        >
          <option value="ALL">Todos los tipos de identificador</option>
          <option value="IP">Dirección IP</option>
          <option value="DEVICE">Dispositivo</option>
          <option value="EMAIL">Correo Electrónico</option>
          <option value="CARD_FINGERPRINT">Huella de Tarjeta</option>
        </select>
      </div>

      {/* Tabla */}
      <div className="lists-table-card">
        {loading ? (
          <div className="empty-state-container">
            <p className="empty-state-title">Cargando entradas de lista...</p>
          </div>
        ) : filteredEntries.length === 0 ? (
          <div className="empty-state-container">
            <p className="empty-state-title">No se encontraron entradas en esta lista</p>
            <p className="empty-state-description">
              {searchQuery || selectedEntryType !== 'ALL'
                ? 'Prueba modificando los criterios de búsqueda o filtro.'
                : `Aún no has registrado elementos en la ${activeTab === 'BLOCK' ? 'Lista de Bloqueo' : 'Lista de Confianza'}.`}
            </p>
          </div>
        ) : (
          <table className="lists-table">
            <thead>
              <tr>
                <th>Tipo de Lista</th>
                <th>Tipo de Identificador</th>
                <th>Valor Protegido</th>
                <th>Motivo de Inclusión</th>
                <th>Estado</th>
                <th>Vencimiento</th>
                <th>Registrado Por</th>
                <th>Acciones</th>
              </tr>
            </thead>
            <tbody>
              {filteredEntries.map((entry) => (
                <tr key={entry.id}>
                  <td>
                    <span className={`badge-list-type ${entry.listType === 'BLOCK' ? 'badge-list-block' : 'badge-list-allow'}`}>
                      {entry.listType}
                    </span>
                  </td>
                  <td>
                    <span className="badge-entry-type">{getEntryTypeLabel(entry.entryType)}</span>
                  </td>
                  <td>
                    <code style={{ fontFamily: 'ui-monospace, monospace', fontWeight: 600 }}>{entry.valueMasked}</code>
                  </td>
                  <td>{entry.reason}</td>
                  <td>
                    {entry.active ? (
                      <span className="badge-status-active">Activo</span>
                    ) : (
                      <span className="badge-status-expired">Vencido</span>
                    )}
                  </td>
                  <td>
                    {entry.expiresAt ? (
                      new Date(entry.expiresAt).toLocaleDateString() + ' ' + new Date(entry.expiresAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
                    ) : (
                      <span style={{ color: '#64748b' }}>Permanente</span>
                    )}
                  </td>
                  <td>{entry.createdByName || 'Sistema'}</td>
                  <td>
                    <button className="btn-delete-entry" onClick={() => handleDelete(entry)}>
                      Eliminar
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      {/* Modal de Creación */}
      {isModalOpen && (
        <div className="list-modal-backdrop" onClick={handleCloseModal}>
          <div className="list-modal-dialog" onClick={(e) => e.stopPropagation()}>
            <div className="list-modal-header">
              <h3 className="list-modal-title">
                Agregar a {formData.listType === 'BLOCK' ? 'Lista de Bloqueo (REJECT)' : 'Lista de Confianza (ALLOW)'}
              </h3>
              <button className="list-modal-close-btn" onClick={handleCloseModal}>
                ✕
              </button>
            </div>

            <form onSubmit={handleFormSubmit}>
              <div className="list-modal-body">
                {modalError && <div className="alert-feedback alert-error">{modalError}</div>}

                <div className="list-form-group">
                  <label className="list-form-label">Lista Destino</label>
                  <select
                    className="list-form-control"
                    value={formData.listType}
                    onChange={(e) => setFormData({ ...formData, listType: e.target.value as ListType })}
                  >
                    <option value="BLOCK">Lista de Bloqueo (BLOCK — REJECT Inmediato)</option>
                    <option value="ALLOW">Lista de Confianza (ALLOW — APPROVE Inmediato)</option>
                  </select>
                </div>

                <div className="list-form-group">
                  <label className="list-form-label">Tipo de Identificador</label>
                  <select
                    className="list-form-control"
                    value={formData.entryType}
                    onChange={(e) => setFormData({ ...formData, entryType: e.target.value as ListEntryType })}
                  >
                    <option value="IP">Dirección IP (ej. 192.168.1.100)</option>
                    <option value="DEVICE">Identificador de Dispositivo (deviceId)</option>
                    <option value="EMAIL">Correo Electrónico</option>
                    <option value="CARD_FINGERPRINT">Huella / Hash de Tarjeta</option>
                  </select>
                </div>

                <div className="list-form-group">
                  <label className="list-form-label">Valor del Identificador</label>
                  <input
                    type="text"
                    className="list-form-control"
                    placeholder="Introduce el valor exacto..."
                    value={formData.value}
                    onChange={(e) => setFormData({ ...formData, value: e.target.value })}
                    required
                  />
                  <div className="list-form-help">
                    El valor se guardará cifrado con Google Tink y se calculará su índice ciego para el Hot Path en memoria.
                  </div>
                </div>

                <div className="list-form-group">
                  <label className="list-form-label">Motivo de Inclusión (Obligatorio)</label>
                  <textarea
                    className="list-form-control"
                    rows={3}
                    placeholder="Describe la razón técnica o de riesgo (mínimo 5 caracteres)..."
                    value={formData.reason}
                    onChange={(e) => setFormData({ ...formData, reason: e.target.value })}
                    required
                  />
                </div>

                <div className="list-form-group">
                  <label className="list-form-label">Fecha y Hora de Expiración (Opcional)</label>
                  <input
                    type="datetime-local"
                    className="list-form-control"
                    value={formData.expiresAt}
                    onChange={(e) => setFormData({ ...formData, expiresAt: e.target.value })}
                  />
                  <div className="list-form-help">Si se deja vacío, la regla aplicará de forma permanente hasta su eliminación manual.</div>
                </div>
              </div>

              <div className="list-modal-footer">
                <button type="button" className="btn-secondary" onClick={handleCloseModal} disabled={modalLoading}>
                  Cancelar
                </button>
                <button type="submit" className="btn-primary-action" disabled={modalLoading}>
                  {modalLoading ? 'Guardando...' : 'Guardar Entrada'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  )
}
