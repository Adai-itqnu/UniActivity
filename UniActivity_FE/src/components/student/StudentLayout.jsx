import { useState, useEffect } from 'react'
import { Outlet } from 'react-router-dom'
import StudentSidebar from './StudentSidebar'
import StudentHeader from './StudentHeader'

export default function StudentLayout() {
    const [sidebarCollapsed, setSidebarCollapsed] = useState(false)
    const [currentUser, setCurrentUser] = useState(null)

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
                console.error('[StudentLayout] Auth error:', err.message)
                window.location.href = '/login?error=session&message=' + encodeURIComponent(err.message)
            })
    }, [])

    return (
        <div className="min-h-screen bg-gray-50 dark:bg-gray-950 transition-colors">
            {/* Sidebar */}
            <StudentSidebar collapsed={sidebarCollapsed} setCollapsed={setSidebarCollapsed} currentUser={currentUser} />

            {/* Main content area — shifts based on sidebar width */}
            <div
                className={`flex flex-col min-h-screen transition-all duration-300 ${sidebarCollapsed ? 'lg:ml-[72px]' : 'lg:ml-72'
                    }`}
            >
                <StudentHeader />

                {/* Page content */}
                <main className="flex-1 overflow-y-auto overflow-x-hidden p-6 lg:p-10">
                    <div className="max-w-7xl mx-auto">
                        <Outlet context={{ currentUser }} />
                    </div>
                </main>

                {/* Footer */}
                <div className="py-4 text-center text-xs text-gray-400 border-t border-gray-200 dark:border-gray-700">
                    <p>© 2026 UniActivity Student Portal. All rights reserved.</p>
                </div>
            </div>
        </div>
    )
}
