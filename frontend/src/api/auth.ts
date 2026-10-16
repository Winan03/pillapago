/**
 * API de autenticación — HU-02 (Iniciar y cerrar sesión).
 */
import apiClient from './client'

export type UserRole = 'MERCHANT_ADMIN' | 'RISK_ANALYST' | 'AUDITOR'

export interface UserDto {
  id: string
  merchantId: string
  email: string
  username: string
  role: UserRole
}

export interface LoginRequest {
  email: string
  password: string
}

export interface LoginResponse {
  accessToken: string
  tokenType: string
  expiresIn: number
  refreshToken: string
  user: UserDto
}

export interface LogoutRequest {
  refreshToken: string
}

export interface MessageResponse {
  message: string
}

export const authApi = {
  login: (data: LoginRequest) =>
    apiClient.post<LoginResponse>('/auth/login', data),

  logout: (data: LogoutRequest) =>
    apiClient.post<MessageResponse>('/auth/logout', data),
}
