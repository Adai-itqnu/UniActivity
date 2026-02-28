import { useState, useEffect, useRef, useCallback } from 'react'

function timeAgo(dateStr) {
    if (!dateStr) return ''
    const now = new Date(), d = new Date(dateStr)
    const mins = Math.floor((now - d) / 60000)
    if (mins < 1) return 'Vừa xong'
    if (mins < 60) return `${mins} phút trước`
    const hours = Math.floor(mins / 60)
    if (hours < 24) return `${hours} giờ trước`
    const days = Math.floor(hours / 24)
    if (days < 30) return `${days} ngày trước`
    return `${Math.floor(days / 30)} tháng trước`
}

const TYPE_CONFIG = {
    JOIN_REQUEST_APPROVED: { icon: 'how_to_reg', gradient: 'from-emerald-400 to-green-500', border: 'border-l-emerald-500' },
    JOIN_REQUEST_REJECTED: { icon: 'person_off', gradient: 'from-red-400 to-rose-500', border: 'border-l-red-500' },
    NEW_ACTIVITY: { icon: 'event', gradient: 'from-blue-400 to-indigo-500', border: 'border-l-blue-500' },
    EVIDENCE_APPROVED: { icon: 'verified', gradient: 'from-emerald-400 to-teal-500', border: 'border-l-emerald-500' },
    EVIDENCE_REJECTED: { icon: 'cancel', gradient: 'from-red-400 to-pink-500', border: 'border-l-red-500' },
    POINT_REQUEST_APPROVED: { icon: 'grade', gradient: 'from-amber-400 to-orange-500', border: 'border-l-amber-500' },
    POINT_REQUEST_REJECTED: { icon: 'grade', gradient: 'from-red-400 to-rose-500', border: 'border-l-red-500' },
    REMOVED_FROM_CLASS: { icon: 'group_remove', gradient: 'from-gray-400 to-gray-600', border: 'border-l-gray-500' },
    DASHBOARD_UPDATED: { icon: 'dashboard', gradient: 'from-indigo-400 to-blue-600', border: 'border-l-indigo-500' },
}
const DEFAULT_TYPE = { icon: 'notifications', gradient: 'from-gray-400 to-gray-500', border: 'border-l-gray-400' }

export default function StudentNotifications() {
    const [notifications, setNotifications] = useState([])
    const [loading, setLoading] = useState(true)
    const [hasMore, setHasMore] = useState(true)
    const [page, setPage] = useState(0)
    const [unreadCount, setUnreadCount] = useState(0)
    const loaderRef = useRef(null)

    const fetchPage = useCallback((p = 0, append = false) => {
        if (p === 0) setLoading(true)
        fetch(`/student/api/notifications?page=${p}&size=20`, { credentials: 'include' })
            .then(r => r.ok ? r.json() : { notifications: [], hasMore: false })
            .then(data => {
                setNotifications(prev => append ? [...prev, ...data.notifications] : data.notifications)
                setHasMore(data.hasMore)
                setLoading(false)
            })
            .catch(() => setLoading(false))
    }, [])

    const fetchUnread = useCallback(() => {
        fetch('/student/api/notifications/unread-count', { credentials: 'include' })
            .then(r => r.ok ? r.json() : { count: 0 })
            .then(d => setUnreadCount(d.count || 0))
            .catch(() => { })
    }, [])

    useEffect(() => { fetchPage(0); fetchUnread() }, [fetchPage, fetchUnread])

    useEffect(() => {
        if (!loaderRef.current) return
        const obs = new IntersectionObserver(entries => {
            if (entries[0].isIntersecting && hasMore && !loading) {
                const nextPage = page + 1
                setPage(nextPage)
                fetchPage(nextPage, true)
            }
        }, { threshold: 0.1 })
        obs.observe(loaderRef.current)
        return () => obs.disconnect()
    }, [hasMore, loading, page, fetchPage])

    const handleMarkRead = async (id) => {
        await fetch(`/student/api/notifications/${id}/read`, { method: 'POST', credentials: 'include' })
        setNotifications(prev => prev.map(n => n.id === id ? { ...n, isRead: true } : n))
        fetchUnread()
    }

    const handleMarkAllRead = async () => {
        await fetch('/student/api/notifications/read-all', { method: 'POST', credentials: 'include' })
        setNotifications(prev => prev.map(n => ({ ...n, isRead: true })))
        setUnreadCount(0)
    }

    return (
        <div className="space-y-6">
            {/* ── Header ── */}
            <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
                <div className="flex items-center gap-3">
                    <div className="size-11 rounded-xl bg-gradient-to-br from-emerald-500 to-teal-500 flex items-center justify-center shadow-sm">
                        <span className="material-symbols-outlined text-white text-xl">notifications</span>
                    </div>
                    <div>
                        <h2 className="text-2xl font-bold text-gray-900 dark:text-white tracking-tight">Thông báo</h2>
                        <p className="text-sm text-gray-500 dark:text-gray-400">
                            {unreadCount > 0 ? `${unreadCount} thông báo chưa đọc` : 'Tất cả thông báo'}
                        </p>
                    </div>
                </div>
                {unreadCount > 0 && (
                    <button onClick={handleMarkAllRead}
                        className="px-4 py-2.5 text-sm font-semibold text-emerald-600 dark:text-emerald-400 border border-emerald-200 dark:border-emerald-800/50 rounded-xl hover:bg-emerald-50 dark:hover:bg-emerald-900/20 transition-colors flex items-center gap-2 self-start shadow-sm">
                        <span className="material-symbols-outlined text-lg">done_all</span>
                        Đánh dấu tất cả đã đọc
                    </button>
                )}
            </div>

            {/* ── Stats Mini ── */}
            <div className="grid grid-cols-2 gap-4">
                <div className="bg-gradient-to-br from-emerald-500 to-teal-500 rounded-2xl p-5 text-white relative overflow-hidden">
                    <div className="absolute -right-4 -top-4 size-16 rounded-full bg-white/10 blur-xl" />
                    <div className="relative z-10 flex items-center gap-4">
                        <div className="size-11 rounded-xl bg-white/20 flex items-center justify-center"><span className="material-symbols-outlined text-xl">mark_email_unread</span></div>
                        <div><h3 className="text-2xl font-bold">{unreadCount}</h3><p className="text-sm text-white/80">Chưa đọc</p></div>
                    </div>
                </div>
                <div className="bg-gradient-to-br from-blue-500 to-indigo-500 rounded-2xl p-5 text-white relative overflow-hidden">
                    <div className="absolute -right-4 -top-4 size-16 rounded-full bg-white/10 blur-xl" />
                    <div className="relative z-10 flex items-center gap-4">
                        <div className="size-11 rounded-xl bg-white/20 flex items-center justify-center"><span className="material-symbols-outlined text-xl">notifications_active</span></div>
                        <div><h3 className="text-2xl font-bold">{notifications.length}</h3><p className="text-sm text-white/80">Tổng cộng</p></div>
                    </div>
                </div>
            </div>

            {/* ── Notification List ── */}
            <div className="bg-white dark:bg-gray-900 rounded-2xl border border-gray-100 dark:border-gray-800 overflow-hidden">
                {loading && page === 0 ? (
                    <div className="flex items-center justify-center py-16">
                        <div className="text-center">
                            <div className="w-8 h-8 border-3 border-emerald-500/30 border-t-emerald-500 rounded-full animate-spin mx-auto mb-3" />
                            <p className="text-sm text-gray-400">Đang tải thông báo...</p>
                        </div>
                    </div>
                ) : notifications.length > 0 ? (
                    <div className="divide-y divide-gray-50 dark:divide-gray-800">
                        {notifications.map(notif => {
                            const cfg = TYPE_CONFIG[notif.type] || DEFAULT_TYPE
                            return (
                                <div
                                    key={notif.id}
                                    onClick={() => !notif.isRead && handleMarkRead(notif.id)}
                                    className={`flex items-start gap-4 px-6 py-4 cursor-pointer hover:bg-emerald-50/40 dark:hover:bg-gray-800/50 transition-colors border-l-3 ${!notif.isRead ? `${cfg.border} bg-emerald-50/30 dark:bg-emerald-900/5` : 'border-l-transparent'}`}
                                >
                                    <div className={`flex items-center justify-center w-10 h-10 rounded-xl bg-gradient-to-br ${cfg.gradient} shrink-0 shadow-sm`}>
                                        <span className="material-symbols-outlined text-white text-lg">{cfg.icon}</span>
                                    </div>
                                    <div className="min-w-0 flex-1">
                                        <div className="flex items-start justify-between gap-4">
                                            <div className="min-w-0">
                                                <p className={`text-sm leading-snug ${!notif.isRead ? 'font-bold text-gray-900 dark:text-white' : 'text-gray-700 dark:text-gray-300'}`}>
                                                    {notif.title}
                                                </p>
                                                {notif.message && (
                                                    <p className="text-xs text-gray-400 mt-1 line-clamp-2">{notif.message}</p>
                                                )}
                                            </div>
                                            {!notif.isRead && (
                                                <div className="size-2.5 rounded-full bg-emerald-500 shrink-0 mt-1.5 animate-pulse" />
                                            )}
                                        </div>
                                        <p className="text-xs text-gray-400 mt-1.5 flex items-center gap-1">
                                            <span className="material-symbols-outlined text-xs">schedule</span>
                                            {timeAgo(notif.createdAt)}
                                        </p>
                                    </div>
                                </div>
                            )
                        })}
                        {/* Infinite scroll loader */}
                        <div ref={loaderRef} className="px-6 py-4 text-center">
                            {hasMore ? (
                                <div className="w-5 h-5 border-2 border-emerald-500/30 border-t-emerald-500 rounded-full animate-spin mx-auto" />
                            ) : (
                                <p className="text-xs text-gray-400">Đã hiển thị tất cả thông báo</p>
                            )}
                        </div>
                    </div>
                ) : (
                    <div className="flex flex-col items-center justify-center py-16">
                        <div className="size-16 rounded-2xl bg-gray-100 dark:bg-gray-800 flex items-center justify-center mb-4">
                            <span className="material-symbols-outlined text-4xl text-gray-300 dark:text-gray-600">notifications_off</span>
                        </div>
                        <p className="text-gray-400 font-medium">Bạn chưa có thông báo nào</p>
                        <p className="text-xs text-gray-300 dark:text-gray-600 mt-1">Thông báo sẽ hiển thị khi có cập nhật mới</p>
                    </div>
                )}
            </div>
        </div>
    )
}
