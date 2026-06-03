import { useState, useEffect } from 'react'
import { Outlet, useNavigate } from 'react-router-dom'
import Sidebar from './Sidebar'
import Header from './Header'
import useSseConnection from '../../utils/useSse'

export default function AdminLayout() {
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false)
  const [mobileOpen, setMobileOpen] = useState(false)
  const [currentUser, setCurrentUser] = useState(null)
  const navigate = useNavigate()

  // Kết nối SSE nhận thông báo thời gian thực
  useSseConnection('ADMIN')

  useEffect(() => {
    // Interceptor tự động gắn JWT token, không cần set thủ công
    fetch('/api/auth/me')
      .then(async (res) => {
        if (res.ok) return res.json()
        const text = await res.text().catch(() => '')
        throw new Error(`Auth check failed: ${res.status} - ${text}`)
      })
      .then((data) => {
        if (data.role === 'STUDENT') {
          window.location.href = '/student/home'
          return
        }
        if (data.role === 'MANAGER') {
          window.location.href = '/manager/dashboard'
          return
        }
        setCurrentUser(data)
      })
      .catch((err) => {
        console.error('[AdminLayout] Auth error:', err.message)
        sessionStorage.removeItem('accessToken')
        sessionStorage.removeItem('refreshToken')
        sessionStorage.removeItem('user')
        navigate('/login?error=session&message=' + encodeURIComponent(err.message), { replace: true })
      })
  }, [navigate])

  return (
    <div className="layout-shell bg-gray-100 dark:bg-gray-950 transition-colors">
      <Sidebar collapsed={sidebarCollapsed} setCollapsed={setSidebarCollapsed} currentUser={currentUser} mobileOpen={mobileOpen} setMobileOpen={setMobileOpen} />

      <div
        className={`flex flex-col transition-all duration-300 ${
          sidebarCollapsed ? 'lg:ml-[80px]' : 'lg:ml-[17rem]'
        }`}
      >
        <div className="layout-card flex flex-col flex-1 bg-white dark:bg-gray-900 shadow-sm border border-gray-200/80 dark:border-gray-800 overflow-hidden">
          <Header onMenuToggle={() => setMobileOpen(!mobileOpen)} />
          <main className="layout-content flex-1 overflow-y-auto overflow-x-hidden">
            <div className="max-w-7xl mx-auto">
              <Outlet context={{ currentUser }} />
            </div>
          </main>
        </div>
      </div>
    </div>
  )
}
