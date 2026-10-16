/**
 * Hook de autenticación — expone el estado de sesión en memoria y funciones de login/logout.
 */
import { useAuthContext } from '../context/AuthContext'

export function useAuth() {
  return useAuthContext()
}
