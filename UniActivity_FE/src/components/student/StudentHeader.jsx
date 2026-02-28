import { useState, useRef, useEffect, useCallback } from 'react'
import { useDarkMode } from '../../contexts/DarkModeContext'
import { useNavigate } from 'react-router-dom'

function timeAgo(dateStr) {
    if (!dateStr) return ''
    const now = new Date()
    const d = new Date(dateStr)
    const diffMs = now - d
    const mins = Math.floor(diffMs / 60000)
    if (mins < 1) return 'Vừa xong'
    if (mins < 60) return `${mins} phút trước`
    const hours = Math.floor(mins / 60)
    if (hours < 24) return `${hours} giờ trước`
    const days = Math.floor(hours / 24)
    if (days < 30) return `${days} ngày trước`
    return `${Math.floor(days / 30)} tháng trước`
}

const notifTypeIcons = {
    JOIN_REQUEST_APPROVED: { icon: 'how_to_reg', iconColor: 'text-green-500 bg-green-100 dark:bg-green-900/40' },
    JOIN_REQUEST_REJECTED: { icon: 'person_off', iconColor: 'text-red-500 bg-red-100 dark:bg-red-900/40' },
    NEW_ACTIVITY: { icon: 'event', iconColor: 'text-emerald-500 bg-emerald-100 dark:bg-emerald-900/40' },
    EVIDENCE_APPROVED: { icon: 'check_circle', iconColor: 'text-green-500 bg-green-100 dark:bg-green-900/40' },
    EVIDENCE_REJECTED: { icon: 'cancel', iconColor: 'text-red-500 bg-red-100 dark:bg-red-900/40' },
    STUDENT_CHECKED_IN: { icon: 'fact_check', iconColor: 'text-blue-500 bg-blue-100 dark:bg-blue-900/40' },
    DASHBOARD_UPDATED: { icon: 'dashboard', iconColor: 'text-purple-500 bg-purple-100 dark:bg-purple-900/40' },
}
const defaultNotifIcon = { icon: 'notifications', iconColor: 'text-gray-500 bg-gray-100 dark:bg-gray-800' }

export default function StudentHeader() {
    const { isDark, toggleDarkMode } = useDarkMode()
    const navigate = useNavigate()
    const [showNotifications, setShowNotifications] = useState(false)
    const [searchQuery, setSearchQuery] = useState('')
    const notifRef = useRef(null)

    const [notifications, setNotifications] = useState([])
    const [unreadCount, setUnreadCount] = useState(0)
    const [notifLoading, setNotifLoading] = useState(false)

    useEffect(() => {
        const handleClick = (e) => {
            if (notifRef.current && !notifRef.current.contains(e.target)) {
                setShowNotifications(false)
            }
        }
        document.addEventListener('mousedown', handleClick)
        return () => document.removeEventListener('mousedown', handleClick)
    }, [])

    // Fetch notifications từ backend
    const fetchNotifications = useCallback(() => {
        setNotifLoading(true)
        Promise.all([
            fetch('/student/api/notifications', { credentials: 'include' }).then(r => r.ok ? r.json() : { notifications: [] }),
            fetch('/student/api/notifications/unread-count', { credentials: 'include' }).then(r => r.ok ? r.json() : { count: 0 }),
        ])
            .then(([notifData, countData]) => {
                setNotifications(notifData.notifications || [])
                setUnreadCount(countData.count || 0)
                setNotifLoading(false)
            })
            .catch(() => {
                setNotifications([])
                setUnreadCount(0)
                setNotifLoading(false)
            })
    }, [])

    useEffect(() => {
        fetchNotifications()
        const interval = setInterval(fetchNotifications, 30000)
        return () => clearInterval(interval)
    }, [fetchNotifications])

    const handleMarkAllRead = () => {
        fetch('/student/api/notifications/read-all', {
            method: 'POST',
            credentials: 'include',
        })
            .then(() => {
                setNotifications((prev) => prev.map((n) => ({ ...n, isRead: true })))
                setUnreadCount(0)
            })
            .catch(() => { })
    }

    return (
        <header className="h-20 border-b border-gray-200 dark:border-gray-700 bg-white/80 dark:bg-gray-900/80 backdrop-blur-md sticky top-0 z-30 px-6 flex items-center justify-between gap-4 shrink-0">
            {/* Left: Mobile Menu + Title */}
            <div className="flex items-center gap-4">
                <button className="lg:hidden p-2 text-gray-600 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-gray-800 rounded-lg">
                    <span className="material-symbols-outlined">menu</span>
                </button>
                <div className="flex items-center gap-2 lg:hidden">
                    <div className="flex items-center justify-center w-9 h-9 rounded-lg bg-emerald-500 text-white shadow-sm">
                        <span className="material-symbols-outlined text-lg">school</span>
                    </div>
                    <span className="font-bold text-gray-800 dark:text-white text-sm">UniActivity</span>
                </div>
            </div>

            {/* Center: Search */}
            <div className="hidden md:flex flex-1 max-w-lg mx-4">
                <div className="relative w-full group">
                    <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
                        <span className="material-symbols-outlined text-gray-400 group-focus-within:text-emerald-500 transition-colors">search</span>
                    </div>
                    <input
                        value={searchQuery}
                        onChange={(e) => setSearchQuery(e.target.value)}
                        className="block w-full pl-10 pr-3 py-2.5 border-none rounded-xl bg-gray-100 dark:bg-gray-800 text-gray-900 dark:text-white placeholder-gray-400 focus:outline-none focus:ring-2 focus:ring-emerald-500/50 transition-all text-sm"
                        placeholder="Tìm hoạt động, lớp, sự kiện..."
                        type="text"
                    />
                </div>
            </div>

            {/* Right: Actions */}
            <div className="flex items-center gap-3">
                {/* Notifications */}
                <div className="relative" ref={notifRef}>
                    <button
                        onClick={() => {
                            setShowNotifications(!showNotifications)
                            if (!showNotifications) fetchNotifications()
                        }}
                        className="relative size-10 flex items-center justify-center rounded-xl bg-gray-100 dark:bg-gray-800 text-gray-600 dark:text-gray-300 hover:text-emerald-500 dark:hover:text-emerald-400 transition-colors"
                    >
                        <span className="material-symbols-outlined text-xl">notifications</span>
                        {unreadCount > 0 && (
                            <span className="absolute top-1.5 right-1.5 size-2.5 bg-red-500 rounded-full border-2 border-white dark:border-gray-900" />
                        )}
                    </button>

                    {showNotifications && (
                        <div className="absolute right-0 mt-2 w-80 bg-white dark:bg-gray-800 rounded-2xl shadow-xl border border-gray-200 dark:border-gray-700 overflow-hidden animate-in z-50">
                            <div className="flex items-center justify-between px-4 py-3 border-b border-gray-100 dark:border-gray-700">
                                <h3 className="font-semibold text-gray-800 dark:text-white text-sm">
                                    Thông báo {unreadCount > 0 && `(${unreadCount})`}
                                </h3>
                                {unreadCount > 0 && (
                                    <button
                                        onClick={handleMarkAllRead}
                                        className="text-xs text-emerald-500 font-medium cursor-pointer hover:underline"
                                    >
                                        Đánh dấu đã đọc
                                    </button>
                                )}
                            </div>
                            <div className="max-h-80 overflow-y-auto">
                                {notifLoading ? (
                                    <div className="flex items-center justify-center py-6">
                                        <div className="w-5 h-5 border-2 border-emerald-500/30 border-t-emerald-500 rounded-full animate-spin" />
                                    </div>
                                ) : notifications.length > 0 ? (
                                    notifications.map((notif) => {
                                        const typeInfo = notifTypeIcons[notif.type] || defaultNotifIcon
                                        return (
                                            <div
                                                key={notif.id}
                                                className={`flex items-start gap-3 px-4 py-3 hover:bg-gray-50 dark:hover:bg-gray-700/50 cursor-pointer transition-colors ${!notif.isRead ? 'bg-emerald-500/5' : ''
                                                    }`}
                                            >
                                                <div className={`flex items-center justify-center w-9 h-9 rounded-lg shrink-0 ${typeInfo.iconColor}`}>
                                                    <span className="material-symbols-outlined text-lg">{typeInfo.icon}</span>
                                                </div>
                                                <div className="min-w-0">
                                                    <p className="text-sm text-gray-700 dark:text-gray-200 leading-snug">
                                                        {notif.title}
                                                    </p>
                                                    {notif.message && (
                                                        <p className="text-xs text-gray-400 mt-0.5 line-clamp-2">{notif.message}</p>
                                                    )}
                                                    <p className="text-xs text-emerald-500 mt-0.5 flex items-center gap-1">
                                                        <span className="w-1.5 h-1.5 rounded-full bg-emerald-500 inline-block" />
                                                        {timeAgo(notif.createdAt)}
                                                    </p>
                                                </div>
                                            </div>
                                        )
                                    })
                                ) : (
                                    <div className="px-4 py-6 text-center">
                                        <span className="material-symbols-outlined text-3xl text-gray-300 dark:text-gray-600 block mb-1">notifications_off</span>
                                        <p className="text-sm text-gray-400">Không có thông báo mới</p>
                                    </div>
                                )}
                            </div>
                            <div className="border-t border-gray-100 dark:border-gray-700 px-4 py-2.5">
                                <button
                                    onClick={() => {
                                        setShowNotifications(false)
                                        navigate('/student/notifications')
                                    }}
                                    className="w-full text-center text-sm text-emerald-500 font-medium hover:underline"
                                >
                                    Xem tất cả thông báo →
                                </button>
                            </div>
                        </div>
                    )}
                </div>

                {/* Dark Mode */}
                <button
                    onClick={toggleDarkMode}
                    className="size-10 flex items-center justify-center rounded-xl bg-gray-100 dark:bg-gray-800 text-gray-600 dark:text-gray-300 hover:text-emerald-500 dark:hover:text-emerald-400 transition-colors"
                    title={isDark ? 'Chế độ sáng' : 'Chế độ tối'}
                >
                    <span className="material-symbols-outlined text-xl">
                        {isDark ? 'light_mode' : 'dark_mode'}
                    </span>
                </button>
            </div>
        </header>
    )
}
