/**
 * API de autenticación — HU-01, HU-02, HU-03
 * Se implementa en la Fase 6.
 */
import apiClient from './client'

export interface LoginRequest {
  email: string
  password: string
}

export interface TokenResponse {
  accessToken: string
  expiresIn: number
}

export const authApi = {
  login: (data: LoginRequest) =>
    apiClient.post<TokenResponse>('/auth/login', data),

  logout: () =>
    apiClient.post('/auth/logout'),

  refresh: () =>
    apiClient.post<TokenResponse>('/auth/refresh'),
}
