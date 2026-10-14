/**
 * Cliente HTTP centralizado — todas las llamadas a la API pasan por aquí.
 * Configurado con baseURL, interceptores de auth (JWT) y manejo de errores.
 *
 * Se implementa en la Fase 6.
 */
import axios from 'axios'

const apiClient = axios.create({
  baseURL: '/v1',
  headers: {
    'Content-Type': 'application/json',
  },
})

// Interceptor: añade el token JWT a cada petición
apiClient.interceptors.request.use((config) => {
  const token = localStorage.getItem('access_token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

export default apiClient
