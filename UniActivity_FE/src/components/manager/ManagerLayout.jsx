import { useState, useEffect } from 'react'
import { Outlet, useNavigate } from 'react-router-dom'
import ManagerSidebar from './ManagerSidebar'
import ManagerHeader from './ManagerHeader'

export default function ManagerLayout() {
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
                console.error('[ManagerLayout] Auth error:', err.message)
                navigate('/login?error=session&message=' + encodeURIComponent(err.message), { replace: true })
            })
    }, [navigate])

    return (
        <div className="min-h-screen bg-gray-50 dark:bg-gray-950 transition-colors">
            <ManagerSidebar collapsed={sidebarCollapsed} setCollapsed={setSidebarCollapsed} currentUser={currentUser} />
            <div className={`flex flex-col min-h-screen transition-all duration-300 ${sidebarCollapsed ? 'lg:ml-[72px]' : 'lg:ml-72'
                }`}>
                <ManagerHeader />
                <main className="flex-1 overflow-y-auto overflow-x-hidden p-6 lg:p-10">
                    <div className="max-w-7xl mx-auto">
                        <Outlet context={{ currentUser }} />
                    </div>
                </main>
                <div className="py-4 text-center text-xs text-gray-400 border-t border-gray-200 dark:border-gray-700">
                    <p>© 2026 UniActivity Manager Portal. All rights reserved.</p>
                </div>
            </div>
        </div>
    )
}
