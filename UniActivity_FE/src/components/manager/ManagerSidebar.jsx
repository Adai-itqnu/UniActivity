import { useState, useRef, useEffect } from 'react'
import { NavLink } from 'react-router-dom'

const menuGroups = [
    {
        label: 'Tổng quan',
        items: [
            { name: 'Dashboard', icon: 'dashboard', path: '/manager/dashboard' },
        ],
    },
    {
        label: 'Quản lý Lớp',
        items: [
            { name: 'Thành viên lớp', icon: 'groups', path: '/manager/members' },
            { name: 'Yêu cầu tham gia', icon: 'person_add', path: '/manager/join-requests' },
        ],
    },
    {
        label: 'Hoạt động',
        items: [
            { name: 'Hoạt động', icon: 'event', path: '/manager/activities' },
        ],
    },
    {
        label: 'Điểm rèn luyện',
        items: [
            { name: 'Yêu cầu điểm', icon: 'grade', path: '/manager/point-requests' },
            { name: 'Xuất báo cáo', icon: 'description', path: '/manager/reports' },
        ],
    },
    {
        label: 'Hệ thống',
        items: [
            { name: 'Thông báo', icon: 'notifications', path: '/manager/notifications' },
            { name: 'Hồ sơ cá nhân', icon: 'person', path: '/manager/profile' },
        ],
    },
]

export default function ManagerSidebar({ collapsed, setCollapsed, currentUser }) {
    const [showUserMenu, setShowUserMenu] = useState(false)
    const userMenuRef = useRef(null)

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

    const userName = currentUser?.fullName || 'Quản lý'
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
                    className="flex items-center justify-center w-10 h-10 rounded-xl bg-blue-600 text-white hover:bg-blue-700 transition-colors shrink-0 shadow-lg shadow-blue-600/30"
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
                        <p className="text-[10px] font-medium text-blue-600 leading-tight whitespace-nowrap">
                            Manager Portal
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
                                        ? 'bg-blue-500/10 text-blue-600 dark:text-blue-400'
                                        : 'text-gray-600 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-gray-800 hover:text-blue-600 dark:hover:text-blue-400'
                                    } ${collapsed ? 'justify-center' : ''}`
                                }
                                title={collapsed ? item.name : undefined}
                            >
                                <span className="material-symbols-outlined text-xl shrink-0">
                                    {item.icon}
                                </span>
                                {!collapsed && <span className="whitespace-nowrap">{item.name}</span>}
                            </NavLink>
                        ))}
                    </div>
                ))}
            </nav>

            {/* User Profile */}
            <div className="p-3 border-t border-gray-200 dark:border-gray-700 shrink-0">
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
                                    to="/manager/profile"
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
                        className={`flex items-center gap-3 w-full p-2 rounded-xl hover:bg-gray-100 dark:hover:bg-gray-800 transition-colors ${collapsed ? 'justify-center' : ''}`}
                    >
                        {avatarUrl ? (
                            <img src={avatarUrl} alt={userName} className="w-9 h-9 rounded-lg object-cover shrink-0" />
                        ) : (
                            <div className="w-9 h-9 rounded-lg bg-gradient-to-br from-blue-400 to-blue-600 flex items-center justify-center shrink-0">
                                <span className="text-sm font-bold text-white">{initials}</span>
                            </div>
                        )}
                        {!collapsed && (
                            <div className="flex-1 text-left min-w-0">
                                <p className="text-sm font-semibold text-gray-800 dark:text-white truncate">{userName}</p>
                                <p className="text-[10px] text-blue-500 font-medium">Manager</p>
                            </div>
                        )}
                        {!collapsed && (
                            <span className="material-symbols-outlined text-gray-400 text-lg shrink-0">
                                unfold_more
                            </span>
                        )}
                    </button>
                </div>
            </div>
        </aside>
    )
}
