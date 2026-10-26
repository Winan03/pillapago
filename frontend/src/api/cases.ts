import apiClient from './client'

export type CaseStatus = 'PENDING_REVIEW' | 'ASSIGNED' | 'RESOLVED'
export type CaseResolution = 'APPROVED' | 'REJECTED'

export interface CaseSummary {
  id: string
  version: number
  status: CaseStatus
  priority: string
  transactionId: string
  externalTransactionId: string
  amount: number
  currency: string
  riskScore: number
  ruleSetVersion: string
  paymentMethod?: string
  cardBin?: string
  cardLastFour?: string
  assignedToId?: string
  assignedToName?: string
  resolution?: CaseResolution
  resolvedById?: string
  resolvedByName?: string
  resolvedAt?: string
  createdAt: string
  updatedAt: string
}

export interface RuleReason {
  ruleName: string
  points: number
  description?: string
}

export interface CaseDetail {
  id: string
  version: number
  status: CaseStatus
  priority: string
  notes?: string
  transactionId: string
  externalTransactionId: string
  amount: number
  currency: string
  riskScore: number
  ruleSetVersion: string
  degraded?: boolean
  latencyMs?: number
  paymentMethod?: string
  cardBin?: string
  cardLastFour?: string
  metadata?: string
  transactionCreatedAt?: string
  triggeredRules: RuleReason[]
  assignedToId?: string
  assignedToName?: string
  assignedToEmail?: string
  resolution?: CaseResolution
  resolutionNotes?: string
  resolvedById?: string
  resolvedByName?: string
  resolvedByEmail?: string
  resolvedAt?: string
  createdAt: string
  updatedAt: string
}

export interface CaseFilterParams {
  ageDays?: number
  minAmount?: number
  maxAmount?: number
  status?: CaseStatus
}

export interface ResolveCasePayload {
  resolution: CaseResolution
  comment: string
}

export const casesApi = {
  /**
   * Obtiene la cola de casos con filtros por antigüedad, monto y estado.
   */
  async getCases(filters?: CaseFilterParams): Promise<CaseSummary[]> {
    const params: Record<string, any> = {}
    if (filters?.ageDays) params.ageDays = filters.ageDays
    if (filters?.minAmount !== undefined && filters?.minAmount !== null) params.minAmount = filters.minAmount
    if (filters?.maxAmount !== undefined && filters?.maxAmount !== null) params.maxAmount = filters.maxAmount
    if (filters?.status) params.status = filters.status

    const response = await apiClient.get<CaseSummary[]>('/cases', { params })
    return response.data
  },

  /**
   * Obtiene el detalle de un caso específico.
   */
  async getCaseDetail(caseId: string): Promise<CaseDetail> {
    const response = await apiClient.get<CaseDetail>(`/cases/${caseId}`)
    return response.data
  },

  /**
   * Reclama y auto-asigna el caso al analista autenticado (estado ASSIGNED).
   */
  async claimCase(caseId: string): Promise<CaseSummary> {
    const response = await apiClient.post<CaseSummary>(`/cases/${caseId}/claim`)
    return response.data
  },

  /**
   * Resuelve el caso aprobándolo o rechazándolo con justificación.
   */
  async resolveCase(caseId: string, payload: ResolveCasePayload): Promise<CaseSummary> {
    const response = await apiClient.post<CaseSummary>(`/cases/${caseId}/resolve`, payload)
    return response.data
  },
}
