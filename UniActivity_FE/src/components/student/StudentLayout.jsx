import { useState, useEffect, useCallback } from 'react'
import { Outlet } from 'react-router-dom'
import StudentSidebar from './StudentSidebar'
import StudentHeader from './StudentHeader'
import useSseConnection from '../../utils/useSse'

export default function StudentLayout() {
    const [sidebarCollapsed, setSidebarCollapsed] = useState(false)
    const [mobileOpen, setMobileOpen] = useState(false)
    const [currentUser, setCurrentUser] = useState(null)

    // Kết nối SSE nhận thông báo thời gian thực
    useSseConnection('STUDENT')

    const fetchCurrentUser = useCallback(() => {
        // Interceptor tự động gắn JWT token
        fetch('/api/auth/me')
            .then(async (res) => {
                if (res.ok) return res.json()
                const text = await res.text().catch(() => '')
                throw new Error(`Auth check failed: ${res.status} - ${text}`)
            })
            .then((data) => {
                if (data.role === 'MANAGER') {
                    const currentPath = window.location.pathname
                    if (currentPath.startsWith('/student/checkin')) {
                        const targetPath = currentPath.replace('/student/checkin', '/manager/checkin')
                        window.location.href = targetPath + window.location.search
                    } else if (currentPath.startsWith('/student/activities')) {
                        window.location.href = '/manager/my-activities'
                    } else if (currentPath.startsWith('/student/my-registrations')) {
                        window.location.href = '/manager/my-registrations'
                    } else if (currentPath.startsWith('/student/my-scores')) {
                        window.location.href = '/manager/my-scores'
                    } else {
                        window.location.href = '/manager/dashboard'
                    }
                    return
                }
                if (data.role === 'ADMIN') {
                    window.location.href = '/admin/dashboard'
                    return
                }
                setCurrentUser(data)
            })
            .catch((err) => {
                console.error('[StudentLayout] Auth error:', err.message)
                sessionStorage.removeItem('accessToken')
                sessionStorage.removeItem('refreshToken')
                sessionStorage.removeItem('user')
                window.location.href = '/login?error=session&message=' + encodeURIComponent(err.message)
            })
    }, [])

    useEffect(() => {
        fetchCurrentUser()
    }, [fetchCurrentUser])

    // Lắng nghe sự kiện SSE để tự động cập nhật thông tin user ngầm
    useEffect(() => {
        const handleNotification = () => {
            console.log('[StudentLayout] 🔔 Nhận thông báo mới, cập nhật user profile ngầm...')
            fetchCurrentUser()
        }
        window.addEventListener('new-notification', handleNotification)
        return () => window.removeEventListener('new-notification', handleNotification)
    }, [fetchCurrentUser])

    return (
        <div className="layout-shell bg-gray-100 dark:bg-gray-950 transition-colors">
            <StudentSidebar collapsed={sidebarCollapsed} setCollapsed={setSidebarCollapsed} currentUser={currentUser} mobileOpen={mobileOpen} setMobileOpen={setMobileOpen} />

            <div
                className={`flex flex-col transition-all duration-300 ${
                    sidebarCollapsed ? 'lg:ml-[80px]' : 'lg:ml-[18rem]'
                }`}
            >
                <div className="layout-card flex flex-col flex-1 bg-white dark:bg-gray-900 shadow-sm border border-gray-200/80 dark:border-gray-800 overflow-hidden">
                    <StudentHeader onMenuToggle={() => setMobileOpen(!mobileOpen)} />
                    <main className="layout-content flex-1 overflow-y-auto overflow-x-hidden">
                        <div className="max-w-7xl mx-auto">
                            <Outlet context={{ currentUser }} />
                        </div>
                    </main>
                    <div className="py-3 text-center text-xs text-gray-400 border-t border-gray-100 dark:border-gray-800">
                        <p>© 2026 UniActivity Student Portal. All rights reserved.</p>
                    </div>
                </div>
            </div>
        </div>
    )
}
