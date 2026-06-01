import { useState, useRef, useEffect } from 'react'
import { NavLink } from 'react-router-dom'

// Menu cấu trúc cho Student Portal — theme xanh lá
const menuGroups = [
    {
        label: 'Menu',
        items: [
            { name: 'Dashboard', icon: 'dashboard', path: '/student/home' },
            { name: 'Lớp của tôi', icon: 'school', path: '/student/my-class' },
            { name: 'Hoạt động', icon: 'event', path: '/student/activities', badge: 'New' },
            { name: 'Check-in', icon: 'qr_code_scanner', path: '/student/checkin' },
            { name: 'Lịch sử đăng ký', icon: 'content_paste', path: '/student/my-registrations' },
            { name: 'Điểm rèn luyện', icon: 'star', path: '/student/my-scores' },
        ],
    },
    {
        label: 'Hỗ trợ',
        items: [
            { name: 'Thông báo', icon: 'notifications', path: '/student/notifications' },
            { name: 'Cài đặt', icon: 'settings', path: '/student/settings' },
            { name: 'Trợ giúp', icon: 'help', path: '/student/help' },
        ],
    },
]

export default function StudentSidebar({ collapsed, setCollapsed, currentUser }) {
    const [showUserMenu, setShowUserMenu] = useState(false)
    const userMenuRef = useRef(null)

    // Đóng user menu khi click bên ngoài
    useEffect(() => {
        const handleClick = (e) => {
            if (userMenuRef.current && !userMenuRef.current.contains(e.target)) {
                setShowUserMenu(false)
            }
        }
        document.addEventListener('mousedown', handleClick)
        return () => document.removeEventListener('mousedown', handleClick)
    }, [])

    const handleLogout = async () => {
        try {
            await fetch('/logout', { method: 'POST', credentials: 'include' })
        } catch (_) { /* ignore */ }
        // Clear JWT tokens
        sessionStorage.removeItem('accessToken')
        sessionStorage.removeItem('refreshToken')
        sessionStorage.removeItem('user')
        window.location.href = '/login'
    }

    const userName = currentUser?.fullName || 'Sinh viên'
    const userRole = 'Student'
    const avatarUrl = currentUser?.avatarUrl
    const initials = userName
        .split(' ')
        .map((w) => w[0])
        .join('')
        .toUpperCase()
        .slice(0, 2)

    return (
        <aside
            className={`fixed top-0 left-0 z-40 h-screen flex flex-col bg-white dark:bg-gray-900 border-r border-gray-200 dark:border-gray-700 transition-all duration-300 ${collapsed ? 'w-[72px]' : 'w-72'
                } hidden lg:flex`}
        >
            {/* Logo + Toggle */}
            <div className="flex items-center gap-3 px-4 h-16 border-b border-gray-200 dark:border-gray-700 shrink-0">
                <button
                    onClick={() => setCollapsed(!collapsed)}
                    className="flex items-center justify-center w-10 h-10 rounded-xl bg-emerald-500 text-white hover:bg-emerald-600 transition-colors shrink-0 shadow-lg shadow-emerald-500/30"
                >
                    <span className="material-symbols-outlined text-xl">
                        {collapsed ? 'menu' : 'menu_open'}
                    </span>
                </button>
                {!collapsed && (
                    <div className="overflow-hidden">
                        <h1 className="text-lg font-bold text-slate-900 dark:text-white leading-tight whitespace-nowrap">
                            UniActivity
                        </h1>
                        <p className="text-[10px] font-medium text-emerald-500 leading-tight whitespace-nowrap">
                            Student Portal
                        </p>
                    </div>
                )}
            </div>

            {/* Navigation */}
            <nav className="flex-1 overflow-y-auto py-4 px-3 space-y-1 scrollbar-thin hide-scrollbar">
                {menuGroups.map((group, gi) => (
                    <div key={gi} className={gi > 0 ? 'mt-6' : ''}>
                        {group.label && !collapsed && (
                            <p className="px-4 mb-2 text-[10px] font-semibold uppercase tracking-wider text-gray-400 dark:text-gray-500">
                                {group.label}
                            </p>
                        )}
                        {group.label && collapsed && gi > 0 && (
                            <div className="mx-3 mb-2 border-t border-gray-200 dark:border-gray-700" />
                        )}
                        {group.items.map((item) => (
                            <NavLink
                                key={item.path}
                                to={item.path}
                                className={({ isActive }) =>
                                    `flex items-center gap-3 px-4 py-3 rounded-xl text-sm font-medium transition-all duration-200 group ${isActive
                                        ? 'bg-emerald-500/10 text-emerald-600 dark:text-emerald-400'
                                        : 'text-gray-600 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-gray-800 hover:text-emerald-600 dark:hover:text-emerald-400'
                                    } ${collapsed ? 'justify-center' : ''}`
                                }
                                title={collapsed ? item.name : undefined}
                            >
                                <span className="material-symbols-outlined text-xl shrink-0">
                                    {item.icon}
                                </span>
                                {!collapsed && <span className="whitespace-nowrap">{item.name}</span>}
                                {!collapsed && item.badge && (
                                    <span className="ml-auto rounded-full bg-red-500 px-2 py-0.5 text-[10px] font-bold text-white">
                                        {item.badge}
                                    </span>
                                )}
                            </NavLink>
                        ))}
                    </div>
                ))}
            </nav>

            {/* CTA Card + Logout */}
            <div className="p-3 border-t border-gray-200 dark:border-gray-700 shrink-0">


                {/* User Profile */}
                <div className="relative" ref={userMenuRef}>
                    {showUserMenu && (
                        <div
                            className={`absolute bottom-full mb-2 bg-white dark:bg-gray-800 rounded-xl shadow-xl border border-gray-200 dark:border-gray-700 overflow-hidden animate-in ${collapsed ? 'left-1/2 -translate-x-1/2 w-48' : 'left-0 right-0'
                                }`}
                        >
                            <div className="px-4 py-3 border-b border-gray-100 dark:border-gray-700">
                                <p className="text-sm font-semibold text-gray-800 dark:text-white truncate">
                                    {userName}
                                </p>
                                <p className="text-xs text-gray-400 dark:text-gray-500 truncate">
                                    {currentUser?.email || ''}
                                </p>
                            </div>
                            <div className="py-1">
                                <NavLink
                                    to="/student/settings"
                                    onClick={() => setShowUserMenu(false)}
                                    className="flex items-center gap-2.5 w-full px-4 py-2.5 text-sm text-gray-700 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-700/50 transition-colors"
                                >
                                    <span className="material-symbols-outlined text-lg">person</span>
                                    Hồ sơ cá nhân
                                </NavLink>
                            </div>
                            <div className="border-t border-gray-100 dark:border-gray-700 py-1">
                                <button
                                    onClick={handleLogout}
                                    className="flex items-center gap-2.5 w-full px-4 py-2.5 text-sm text-red-500 hover:bg-red-50 dark:hover:bg-red-900/20 transition-colors"
                                >
                                    <span className="material-symbols-outlined text-lg">logout</span>
                                    Đăng xuất
                                </button>
                            </div>
                        </div>
                    )}

                    <button
                        onClick={() => setShowUserMenu(!showUserMenu)}
                        className={`flex items-center gap-3 w-full rounded-xl border border-gray-200 dark:border-gray-700 hover:bg-gray-50 dark:hover:bg-gray-800 transition-colors ${collapsed ? 'justify-center p-2' : 'px-3 py-2.5'
                            }`}
                        title={collapsed ? userName : undefined}
                    >
                        {avatarUrl ? (
                            <img
                                src={avatarUrl}
                                alt={userName}
                                className="w-9 h-9 rounded-full object-cover shrink-0 border-2 border-gray-200 dark:border-gray-600"
                            />
                        ) : (
                            <div className="flex items-center justify-center w-9 h-9 rounded-full bg-emerald-500/10 text-emerald-600 font-semibold text-sm shrink-0 border-2 border-emerald-500/20">
                                {initials}
                            </div>
                        )}
                        {!collapsed && (
                            <>
                                <div className="flex-1 text-left min-w-0">
                                    <p className="text-sm font-semibold text-gray-800 dark:text-white truncate">
                                        {userName}
                                    </p>
                                    <p className="text-[10px] text-gray-400 dark:text-gray-500 truncate">
                                        {userRole}
                                    </p>
                                </div>
                                <span className="material-symbols-outlined text-gray-400 dark:text-gray-500 text-lg shrink-0">
                                    {showUserMenu ? 'expand_less' : 'expand_more'}
                                </span>
                            </>
                        )}
                    </button>
                </div>
            </div>
        </aside>
    )
}
