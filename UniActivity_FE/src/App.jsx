import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router-dom'
import { DarkModeProvider } from './contexts/DarkModeContext'
import AuthPage from './pages/AuthPage'
import TermsPage from './pages/TermsPage'

function App() {
  return (
    <DarkModeProvider>
      <Router>
        <Routes>
          {/* Auth routes */}
          <Route path="/login" element={<AuthPage />} />
          <Route path="/register" element={<AuthPage defaultTab="register" />} />

          {/* Trang điều khoản */}
          <Route path="/terms" element={<TermsPage />} />

          {/* Redirect mặc định sang login */}
          <Route path="*" element={<Navigate to="/login" replace />} />
        </Routes>
      </Router>
    </DarkModeProvider>
  )
}

export default App
