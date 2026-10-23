/**
 * API client para la gestión de reglas antifraude y versionado (HU-07, HU-08)
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

export interface RuleDiffItem {
  ruleId: string
  name: string
  description?: string
  points: number
  previousPoints: number
  changeSummary: string
}

export interface DraftDiffResponse {
  currentActiveVersion: string
  nextProposedVersion: string
  totalRulesInDraft: number
  totalPointsInDraft: number
  addedRules: RuleDiffItem[]
  modifiedRules: RuleDiffItem[]
  deactivatedRules: RuleDiffItem[]
}

export interface RuleSetVersionSummary {
  id: string
  merchantId: string
  versionName: string
  versionNumber: number
  description?: string
  status: 'ACTIVE' | 'ARCHIVED'
  rulesCount: number
  createdByUsername: string
  createdAt: string
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

  // Versionado y Rollback (HU-08)
  async getDraftDiff(): Promise<DraftDiffResponse> {
    const response = await apiClient.get<DraftDiffResponse>('/rule-sets/draft-diff')
    return response.data
  },

  async publishVersion(description?: string): Promise<RuleSetVersionSummary> {
    const response = await apiClient.post<RuleSetVersionSummary>('/rule-sets/publish', { description })
    return response.data
  },

  async rollbackVersion(targetVersion: string): Promise<RuleSetVersionSummary> {
    const response = await apiClient.post<RuleSetVersionSummary>('/rule-sets/rollback', { targetVersion })
    return response.data
  },

  async getHistory(): Promise<RuleSetVersionSummary[]> {
    const response = await apiClient.get<RuleSetVersionSummary[]>('/rule-sets/history')
    return response.data
  },
}
