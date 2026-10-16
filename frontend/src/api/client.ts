/**
 * Cliente HTTP centralizado — Axios configurado con baseURL e interceptores de seguridad.
 * El token JWT se extrae estrictamente del almacén en memoria (tokenStore).
 */
import axios from 'axios'
import { tokenStore } from './tokenStore'

const apiClient = axios.create({
  baseURL: '/v1',
  headers: {
    'Content-Type': 'application/json',
  },
})

// Interceptor: añade el token JWT en memoria a cada petición si existe
apiClient.interceptors.request.use((config) => {
  const token = tokenStore.getToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

export default apiClient
