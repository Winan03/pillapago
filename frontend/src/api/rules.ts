/**
 * API client para la gestión de reglas antifraude (HU-07 / RF-10)
 */
import apiClient from './client'

export interface Condition {
  field: string
  operator: string
  value: string
}

export interface Rule {
  id: string
  name: string
  description?: string
  points: number
  active: boolean
  status: 'DRAFT' | 'PUBLISHED' | 'ARCHIVED'
  conditions: Condition[]
  createdAt: string
  updatedAt: string
}

export interface CreateRulePayload {
  name: string
  description?: string
  points: number
  active?: boolean
  conditions: Condition[]
}

export interface FieldMetadata {
  field: string
  label: string
  type: 'NUMERIC' | 'STRING' | 'BOOLEAN'
  category: string
  allowedOperators: string[]
  placeholder: string
}

export const rulesApi = {
  async getRules(status?: string): Promise<Rule[]> {
    const params = status ? { status } : {}
    const response = await apiClient.get<Rule[]>('/rules', { params })
    return response.data
  },

  async getCatalog(): Promise<FieldMetadata[]> {
    const response = await apiClient.get<FieldMetadata[]>('/rules/catalog')
    return response.data
  },

  async getRuleById(id: string): Promise<Rule> {
    const response = await apiClient.get<Rule>(`/rules/${id}`)
    return response.data
  },

  async createRule(payload: CreateRulePayload): Promise<Rule> {
    const response = await apiClient.post<Rule>('/rules', payload)
    return response.data
  },

  async updateRule(id: string, payload: CreateRulePayload): Promise<Rule> {
    const response = await apiClient.put<Rule>(`/rules/${id}`, payload)
    return response.data
  },
}
