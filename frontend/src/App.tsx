import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { AuthProvider } from './context/AuthContext'
import { ProtectedRoute } from './components/ProtectedRoute'
import LoginPage from './pages/LoginPage'
import DashboardPage from './pages/DashboardPage'
import CasesPage from './pages/CasesPage'
import RulesPage from './pages/RulesPage'
import AuditPage from './pages/AuditPage'

/**
 * Enrutamiento principal de la aplicación Pillapago.
 * Las rutas protegidas se resguardan mediante <ProtectedRoute> y el estado en memoria de <AuthProvider>.
 */
export default function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        <Routes>
          {/* Ruta pública */}
          <Route path="/login" element={<LoginPage />} />

          {/* Rutas protegidas con RBAC */}
          <Route
            path="/dashboard"
            element={
              <ProtectedRoute allowedRoles={['MERCHANT_ADMIN', 'RISK_ANALYST', 'AUDITOR']}>
                <DashboardPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/cases"
            element={
              <ProtectedRoute allowedRoles={['MERCHANT_ADMIN', 'RISK_ANALYST']}>
                <CasesPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/rules"
            element={
              <ProtectedRoute allowedRoles={['MERCHANT_ADMIN']}>
                <RulesPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/audit"
            element={
              <ProtectedRoute allowedRoles={['MERCHANT_ADMIN', 'AUDITOR']}>
                <AuditPage />
              </ProtectedRoute>
            }
          />

          {/* Redirección por defecto */}
          <Route path="/" element={<Navigate to="/dashboard" replace />} />
          <Route path="*" element={<Navigate to="/dashboard" replace />} />
        </Routes>
      </BrowserRouter>
    </AuthProvider>
  )
}
