import React, { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../hooks/useAuth'

export default function LoginPage() {
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const { login, loading, error, clearError } = useAuth()
  const navigate = useNavigate()

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!email || !password) return

    try {
      const redirectPath = await login({ email, password })
      navigate(redirectPath, { replace: true })
    } catch {
      // El error ya queda registrado en el AuthContext
    }
  }

  return (
    <main style={containerStyle}>
      <div style={cardStyle}>
        <div style={{ textAlign: 'center', marginBottom: '1.5rem' }}>
          <h1 style={{ color: '#1e293b', margin: '0 0 0.5rem 0' }}>Pillapago</h1>
          <p style={{ color: '#64748b', margin: 0, fontSize: '0.9rem' }}>
            Sistema Antifraude en Tiempo Real — Iniciar Sesión
          </p>
        </div>

        {error && (
          <div
            role="alert"
            style={errorAlertStyle}
          >
            <span>{error}</span>
            <button
              onClick={clearError}
              style={closeAlertBtnStyle}
              aria-label="Cerrar alerta"
            >
              &times;
            </button>
          </div>
        )}

        <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
          <div>
            <label htmlFor="email" style={labelStyle}>
              Correo Electrónico
            </label>
            <input
              id="email"
              type="email"
              required
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="usuario@comercio.com"
              disabled={loading}
              style={inputStyle}
            />
          </div>

          <div>
            <label htmlFor="password" style={labelStyle}>
              Contraseña
            </label>
            <input
              id="password"
              type="password"
              required
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="••••••••••••"
              disabled={loading}
              style={inputStyle}
            />
          </div>

          <button
            type="submit"
            disabled={loading}
            style={loading ? { ...buttonStyle, opacity: 0.7, cursor: 'not-allowed' } : buttonStyle}
          >
            {loading ? 'Iniciando sesión...' : 'Ingresar'}
          </button>
        </form>
      </div>
    </main>
  )
}

const containerStyle: React.CSSProperties = {
  display: 'flex',
  justifyContent: 'center',
  alignItems: 'center',
  minHeight: '100vh',
  backgroundColor: '#f8fafc',
  fontFamily: 'system-ui, -apple-system, sans-serif',
  padding: '1rem',
}

const cardStyle: React.CSSProperties = {
  backgroundColor: '#ffffff',
  borderRadius: '8px',
  boxShadow: '0 4px 6px -1px rgb(0 0 0 / 0.1), 0 2px 4px -2px rgb(0 0 0 / 0.1)',
  padding: '2rem',
  width: '100%',
  maxWidth: '400px',
  border: '1px solid #e2e8f0',
}

const labelStyle: React.CSSProperties = {
  display: 'block',
  marginBottom: '0.4rem',
  fontSize: '0.875rem',
  fontWeight: 500,
  color: '#334155',
}

const inputStyle: React.CSSProperties = {
  width: '100%',
  boxSizing: 'border-box',
  padding: '0.6rem 0.8rem',
  borderRadius: '6px',
  border: '1px solid #cbd5e1',
  fontSize: '0.95rem',
  outline: 'none',
}

const buttonStyle: React.CSSProperties = {
  backgroundColor: '#0284c7',
  color: '#ffffff',
  border: 'none',
  padding: '0.75rem',
  borderRadius: '6px',
  fontSize: '1rem',
  fontWeight: 600,
  cursor: 'pointer',
  transition: 'background-color 0.2s',
  marginTop: '0.5rem',
}

const errorAlertStyle: React.CSSProperties = {
  backgroundColor: '#fef2f2',
  color: '#991b1b',
  border: '1px solid #fecaca',
  padding: '0.75rem 1rem',
  borderRadius: '6px',
  fontSize: '0.875rem',
  marginBottom: '1rem',
  display: 'flex',
  justifyContent: 'space-between',
  alignItems: 'center',
}

const closeAlertBtnStyle: React.CSSProperties = {
  background: 'none',
  border: 'none',
  color: '#991b1b',
  fontSize: '1.2rem',
  cursor: 'pointer',
  lineHeight: 1,
}
