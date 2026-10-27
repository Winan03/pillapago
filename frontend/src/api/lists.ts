import apiClient from './client'

export type ListType = 'BLOCK' | 'ALLOW'
export type ListEntryType = 'DEVICE' | 'IP' | 'EMAIL' | 'CARD_FINGERPRINT'

export interface ListEntry {
  id: string
  listType: ListType
  entryType: ListEntryType
  valueMasked: string
  reason: string
  expiresAt: string | null
  active: boolean
  createdById: string | null
  createdByName: string | null
  createdAt: string
  updatedAt: string
}

export interface CreateListEntryPayload {
  listType: ListType
  entryType: ListEntryType
  value: string
  reason: string
  expiresAt?: string
}

export interface ListFilterParams {
  listType?: ListType
  entryType?: ListEntryType
}

export const listsApi = {
  getEntries: async (params?: ListFilterParams): Promise<ListEntry[]> => {
    const response = await apiClient.get<ListEntry[]>('/lists', { params })
    return response.data
  },

  createEntry: async (payload: CreateListEntryPayload): Promise<ListEntry> => {
    const response = await apiClient.post<ListEntry>('/lists', payload)
    return response.data
  },

  deleteEntry: async (id: string): Promise<void> => {
    await apiClient.delete(`/lists/${id}`)
  },
}
