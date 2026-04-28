import { useState, useEffect } from 'react'
import { Outlet } from 'react-router-dom'
import StudentSidebar from './StudentSidebar'
import StudentHeader from './StudentHeader'

export default function StudentLayout() {
    const [sidebarCollapsed, setSidebarCollapsed] = useState(false)
    const [currentUser, setCurrentUser] = useState(null)

    useEffect(() => {
        const headers = { 'Accept': 'application/json' }
        const accessToken = localStorage.getItem('accessToken')
        if (accessToken) {
            headers['Authorization'] = `Bearer ${accessToken}`
        }
        fetch('/api/auth/me', {
            credentials: 'include',
            headers,
        })
            .then(async (res) => {
                if (res.ok) return res.json()
                const text = await res.text().catch(() => '')
                throw new Error(`Auth check failed: ${res.status} - ${text}`)
            })
            .then(setCurrentUser)
            .catch((err) => {
                console.error('[StudentLayout] Auth error:', err.message)
                localStorage.removeItem('accessToken')
                localStorage.removeItem('refreshToken')
                localStorage.removeItem('user')
                window.location.href = '/login?error=session&message=' + encodeURIComponent(err.message)
            })
    }, [])

    return (
        <div className="layout-shell bg-gray-100 dark:bg-gray-950 transition-colors">
            <StudentSidebar collapsed={sidebarCollapsed} setCollapsed={setSidebarCollapsed} currentUser={currentUser} />

            <div
                className={`flex flex-col transition-all duration-300 ${
                    sidebarCollapsed ? 'lg:ml-[80px]' : 'lg:ml-[18rem]'
                }`}
            >
                <div className="layout-card flex flex-col flex-1 bg-white dark:bg-gray-900 shadow-sm border border-gray-200/80 dark:border-gray-800 overflow-hidden">
                    <StudentHeader />
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
