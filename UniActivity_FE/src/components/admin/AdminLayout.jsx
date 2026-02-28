import { useState, useEffect } from 'react'
import { Outlet, useNavigate } from 'react-router-dom'
import Sidebar from './Sidebar'
import Header from './Header'

export default function AdminLayout() {
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false)
  const [currentUser, setCurrentUser] = useState(null)
  const navigate = useNavigate()

  useEffect(() => {
    fetch('/api/auth/me', {
      credentials: 'include',
      headers: { 'Accept': 'application/json' },
    })
      .then(async (res) => {
        if (res.ok) return res.json()
        const text = await res.text().catch(() => '')
        throw new Error(`Auth check failed: ${res.status} - ${text}`)
      })
      .then(setCurrentUser)
      .catch((err) => {
        console.error('[AdminLayout] Auth error:', err.message)
        navigate('/login?error=session&message=' + encodeURIComponent(err.message), { replace: true })
      })
  }, [navigate])

  return (
    <div className="min-h-screen bg-gray-50 dark:bg-gray-950 transition-colors">
      {/* Sidebar */}
      <Sidebar collapsed={sidebarCollapsed} setCollapsed={setSidebarCollapsed} currentUser={currentUser} />

      {/* Main content area — shifts based on sidebar width */}
      <div
        className={`flex flex-col min-h-screen transition-all duration-300 ${
          sidebarCollapsed ? 'ml-[72px]' : 'ml-64'
        }`}
      >
        <Header />

        {/* Page content */}
        <main className="flex-1 p-6">
          <Outlet context={{ currentUser }} />
        </main>
      </div>
    </div>
  )
}
