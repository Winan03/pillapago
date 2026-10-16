import React, { createContext, useContext, useState, ReactNode } from 'react'
import { authApi, LoginRequest, UserDto, UserRole } from '../api/auth'
import { tokenStore } from '../api/tokenStore'

interface AuthContextType {
  user: UserDto | null
  isAuthenticated: boolean
  loading: boolean
  error: string | null
  login: (credentials: LoginRequest) => Promise<string>
  logout: () => Promise<void>
  clearError: () => void
}

const AuthContext = createContext<AuthContextType | undefined>(undefined)

/**
 * Mapeo de rol a ruta de panel de control según especificación de Pillapago.
 */
export function getRoleDashboardPath(role: UserRole): string {
  switch (role) {
    case 'MERCHANT_ADMIN':
      return '/dashboard'
    case 'RISK_ANALYST':
      return '/cases'
    case 'AUDITOR':
      return '/audit'
    default:
      return '/dashboard'
  }
}

export const AuthProvider: React.FC<{ children: ReactNode }> = ({ children }) => {
  const [user, setUser] = useState<UserDto | null>(null)
  const [currentRefreshToken, setCurrentRefreshToken] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const login = async (credentials: LoginRequest): Promise<string> => {
    setLoading(true)
    setError(null)
    try {
      const response = await authApi.login(credentials)
      const data = response.data

      // Guardar token ESTRICTAMENTE en memoria
      tokenStore.setToken(data.accessToken)
      setCurrentRefreshToken(data.refreshToken)
      setUser(data.user)

      return getRoleDashboardPath(data.user.role)
    } catch (err: any) {
      const detail = err.response?.data?.detail || 'Error al iniciar sesión'
      setError(detail)
      throw new Error(detail)
    } finally {
      setLoading(false)
    }
  }

  const logout = async (): Promise<void> => {
    setLoading(true)
    try {
      if (currentRefreshToken) {
        await authApi.logout({ refreshToken: currentRefreshToken })
      }
    } catch (err) {
      console.error('Error al cerrar sesión en el servidor', err)
    } finally {
      // Limpiar memoria
      tokenStore.clearToken()
      setUser(null)
      setCurrentRefreshToken(null)
      setError(null)
      setLoading(false)
    }
  }

  const clearError = () => setError(null)

  return (
    <AuthContext.Provider
      value={{
        user,
        isAuthenticated: !!user && !!tokenStore.getToken(),
        loading,
        error,
        login,
        logout,
        clearError,
      }}
    >
      {children}
    </AuthContext.Provider>
  )
}

export function useAuthContext(): AuthContextType {
  const context = useContext(AuthContext)
  if (!context) {
    throw new Error('useAuthContext debe ser utilizado dentro de un AuthProvider')
  }
  return context
}
