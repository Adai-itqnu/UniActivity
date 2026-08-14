import { useState, useEffect } from 'react'
import { Outlet, useNavigate } from 'react-router-dom'
import ManagerSidebar from './ManagerSidebar'
import ManagerHeader from './ManagerHeader'
import useSseConnection from '../../utils/useSse'

export default function ManagerLayout() {
    const [sidebarCollapsed, setSidebarCollapsed] = useState(false)
    const [mobileOpen, setMobileOpen] = useState(false)
    const [currentUser, setCurrentUser] = useState(null)
    const [pendingCounts, setPendingCounts] = useState({})
    const navigate = useNavigate()

    // Kết nối SSE nhận thông báo & cập nhật thống kê thời gian thực
    useSseConnection('MANAGER')

    // Fetch pending counts cho sidebar badges
    const fetchPendingCounts = () => {
        fetch('/manager/api/dashboard', { credentials: 'include' })
            .then(r => r.ok ? r.json() : {})
            .then(data => setPendingCounts({
                joinRequests: data.pendingJoinRequests || 0,
                pointRequests: data.pendingPointRequests || 0,
                evidences: data.pendingEvidences || 0,
            }))
            .catch(() => {})
    }

    useEffect(() => {
        fetchPendingCounts()
        const interval = setInterval(fetchPendingCounts, 30000)
        // Refresh counts khi nhận SSE notification
        const onSse = () => fetchPendingCounts()
        window.addEventListener('sse-notification', onSse)
        window.addEventListener('sse-registration-update', onSse)
        return () => {
            clearInterval(interval)
            window.removeEventListener('sse-notification', onSse)
            window.removeEventListener('sse-registration-update', onSse)
        }
    }, [])

    useEffect(() => {
        // Interceptor tự động gắn JWT token
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
                if (data.role === 'ADMIN') {
                    window.location.href = '/admin/dashboard'
                    return
                }
                setCurrentUser(data)
            })
            .catch((err) => {
                console.error('[ManagerLayout] Auth error:', err.message)
                sessionStorage.removeItem('accessToken')
                sessionStorage.removeItem('refreshToken')
                sessionStorage.removeItem('user')
                navigate('/login?error=session&message=' + encodeURIComponent(err.message), { replace: true })
            })
    }, [navigate])

    return (
        <div className="layout-shell bg-gray-100 dark:bg-gray-950 transition-colors">
                <ManagerSidebar collapsed={sidebarCollapsed} setCollapsed={setSidebarCollapsed} currentUser={currentUser} mobileOpen={mobileOpen} setMobileOpen={setMobileOpen} pendingCounts={pendingCounts} />
            <div className={`flex flex-col transition-all duration-300 ${
                sidebarCollapsed ? 'lg:ml-[80px]' : 'lg:ml-[18rem]'
            }`}>
                <div className="layout-card flex flex-col flex-1 bg-white dark:bg-gray-900 shadow-sm border border-gray-200/80 dark:border-gray-800 overflow-hidden">
                    <ManagerHeader onMenuToggle={() => setMobileOpen(!mobileOpen)} />
                    <main className="layout-content flex-1 overflow-y-auto overflow-x-hidden">
                        <div className="max-w-7xl mx-auto">
                            <Outlet context={{ currentUser }} />
                        </div>
                    </main>
                    <div className="py-3 text-center text-xs text-gray-400 border-t border-gray-100 dark:border-gray-800">
                        <p>© 2026 UniActivity Manager Portal. All rights reserved.</p>
                    </div>
                </div>
            </div>
        </div>
    )
}
