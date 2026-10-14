/**
 * Hook de autenticación — gestiona el estado de sesión en el cliente.
 * Se implementa en la Fase 6.
 */
import { useState } from 'react'
import { authApi, type LoginRequest } from '../api/auth'

export function useAuth() {
  const [isAuthenticated, setIsAuthenticated] = useState(
    () => !!localStorage.getItem('access_token')
  )

  const login = async (credentials: LoginRequest) => {
    const { data } = await authApi.login(credentials)
    localStorage.setItem('access_token', data.accessToken)
    setIsAuthenticated(true)
  }

  const logout = async () => {
    await authApi.logout()
    localStorage.removeItem('access_token')
    setIsAuthenticated(false)
  }

  return { isAuthenticated, login, logout }
}
