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
    JOIN_REQUEST_SUBMITTED: { icon: 'person_add', gradient: 'from-blue-400 to-blue-600', border: 'border-l-blue-500' },
    POINT_REQUEST_SUBMITTED: { icon: 'grade', gradient: 'from-amber-400 to-orange-500', border: 'border-l-amber-500' },
    EVIDENCE_SUBMITTED: { icon: 'upload_file', gradient: 'from-purple-400 to-pink-500', border: 'border-l-purple-500' },
    STUDENT_CHECKED_IN: { icon: 'fact_check', gradient: 'from-emerald-400 to-teal-500', border: 'border-l-emerald-500' },
    ACTIVITY_CREATED: { icon: 'event', gradient: 'from-indigo-400 to-blue-600', border: 'border-l-indigo-500' },
    REGISTRATION_APPROVED: { icon: 'how_to_reg', gradient: 'from-green-400 to-emerald-500', border: 'border-l-green-500' },
    REGISTRATION_REJECTED: { icon: 'person_off', gradient: 'from-red-400 to-rose-500', border: 'border-l-red-500' },
    JOIN_REQUEST_APPROVED: { icon: 'check_circle', gradient: 'from-green-400 to-emerald-500', border: 'border-l-green-500' },
    JOIN_REQUEST_REJECTED: { icon: 'cancel', gradient: 'from-red-400 to-rose-500', border: 'border-l-red-500' },
    POINT_REQUEST_APPROVED: { icon: 'verified', gradient: 'from-green-400 to-teal-500', border: 'border-l-green-500' },
    POINT_REQUEST_REJECTED: { icon: 'block', gradient: 'from-red-400 to-rose-500', border: 'border-l-red-500' },
    EVIDENCE_APPROVED: { icon: 'task_alt', gradient: 'from-green-400 to-emerald-500', border: 'border-l-green-500' },
    EVIDENCE_REJECTED: { icon: 'unpublished', gradient: 'from-red-400 to-rose-500', border: 'border-l-red-500' },
    NEW_ACTIVITY: { icon: 'celebration', gradient: 'from-pink-400 to-rose-500', border: 'border-l-pink-500' },
    DASHBOARD_UPDATED: { icon: 'dashboard', gradient: 'from-indigo-400 to-blue-600', border: 'border-l-indigo-500' },
    ACTIVITY_SLOT_FULL: { icon: 'group', gradient: 'from-orange-400 to-red-500', border: 'border-l-orange-500' },
    ACTIVITY_DEADLINE_PASSED: { icon: 'timer_off', gradient: 'from-red-400 to-rose-600', border: 'border-l-red-600' },
    ADMIN_BROADCAST: { icon: 'campaign', gradient: 'from-violet-400 to-purple-600', border: 'border-l-violet-500' },
    ACTIVITY_REGISTRATION: { icon: 'how_to_reg', gradient: 'from-cyan-400 to-blue-500', border: 'border-l-cyan-500' },
}
const DEFAULT_TYPE = { icon: 'notifications', gradient: 'from-gray-400 to-gray-500', border: 'border-l-gray-400' }

export default function AdminNotifications() {
    const [notifications, setNotifications] = useState([])
    const [loading, setLoading] = useState(true)
    const [hasMore, setHasMore] = useState(true)
    const [page, setPage] = useState(0)
    const [unreadCount, setUnreadCount] = useState(0)
    const loaderRef = useRef(null)

    // Broadcast form state
    const [showBroadcast, setShowBroadcast] = useState(false)
    const [bcTitle, setBcTitle] = useState('')
    const [bcMessage, setBcMessage] = useState('')
    const [bcTarget, setBcTarget] = useState('ALL')
    const [bcSending, setBcSending] = useState(false)
    const [bcToast, setBcToast] = useState(null)

    const fetchPage = useCallback((p = 0, append = false) => {
        if (p === 0) setLoading(true)
        fetch(`/admin/api/notifications/all?page=${p}&size=20`, { credentials: 'include' })
            .then(r => r.ok ? r.json() : { notifications: [], hasMore: false, unreadCount: 0 })
            .then(data => {
                setNotifications(prev => append ? [...prev, ...data.notifications] : data.notifications)
                setHasMore(data.hasMore)
                setUnreadCount(data.unreadCount || 0)
                setLoading(false)
            })
            .catch(() => setLoading(false))
    }, [])

    useEffect(() => { fetchPage(0) }, [fetchPage])

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
        await fetch(`/admin/api/notifications/${id}/read`, { method: 'POST', credentials: 'include' })
        setNotifications(prev => prev.map(n => n.id === id ? { ...n, isRead: true } : n))
        setUnreadCount(c => Math.max(0, c - 1))
    }

    const handleMarkAllRead = async () => {
        await fetch('/admin/api/notifications/mark-all-read', { method: 'POST', credentials: 'include' })
        setNotifications(prev => prev.map(n => ({ ...n, isRead: true })))
        setUnreadCount(0)
    }

    const handleBroadcast = async () => {
        if (!bcTitle.trim() || !bcMessage.trim()) {
            setBcToast({ type: 'error', text: 'Vui lòng nhập đủ tiêu đề và nội dung' })
            setTimeout(() => setBcToast(null), 3000)
            return
        }
        setBcSending(true)
        try {
            const res = await fetch('/admin/api/notifications/broadcast', {
                method: 'POST',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ title: bcTitle.trim(), message: bcMessage.trim(), target: bcTarget }),
            })
            const data = await res.json()
            if (!res.ok) throw new Error(data.message)
            setBcToast({ type: 'success', text: data.message })
            setBcTitle('')
            setBcMessage('')
            setBcTarget('ALL')
            setShowBroadcast(false)
            // Re-fetch notifications to show the new broadcast
            setTimeout(() => { setPage(0); fetchPage(0) }, 500)
        } catch (e) {
            setBcToast({ type: 'error', text: e.message })
        } finally {
            setBcSending(false)
            setTimeout(() => setBcToast(null), 4000)
        }
    }

    return (
        <div className="space-y-6">
            {/* Header */}
            <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
                <div className="flex items-center gap-3">
                    <div className="size-11 rounded-xl bg-gradient-to-br from-blue-500 to-cyan-500 flex items-center justify-center shadow-sm">
                        <span className="material-symbols-outlined text-white text-xl">notifications</span>
                    </div>
                    <div>
                        <h2 className="text-2xl font-bold text-gray-900 dark:text-white tracking-tight">Thông báo</h2>
                        <p className="text-sm text-gray-500 dark:text-gray-400">
                            {unreadCount > 0 ? `${unreadCount} thông báo chưa đọc` : 'Tất cả thông báo'}
                        </p>
                    </div>
                </div>
                <div className="flex items-center gap-2 self-start">
                    <button onClick={() => setShowBroadcast(!showBroadcast)}
                        className="px-4 py-2.5 text-sm font-semibold text-white bg-gradient-to-r from-violet-500 to-purple-600 rounded-xl hover:shadow-lg hover:shadow-purple-500/25 transition-all flex items-center gap-2 shadow-sm">
                        <span className="material-symbols-outlined text-lg">campaign</span>
                        Gửi thông báo
                    </button>
                    {unreadCount > 0 && (
                        <button onClick={handleMarkAllRead}
                            className="px-4 py-2.5 text-sm font-semibold text-blue-600 dark:text-blue-400 border border-blue-200 dark:border-blue-800/50 rounded-xl hover:bg-blue-50 dark:hover:bg-blue-900/20 transition-colors flex items-center gap-2 shadow-sm">
                            <span className="material-symbols-outlined text-lg">done_all</span>
                            Đánh dấu tất cả đã đọc
                        </button>
                    )}
                </div>
            </div>

            {/* Toast */}
            {bcToast && (
                <div className={`px-4 py-3 rounded-xl text-sm font-medium flex items-center gap-2 shadow-sm ${bcToast.type === 'success' ? 'bg-emerald-100 dark:bg-emerald-900/30 text-emerald-700 dark:text-emerald-400' : 'bg-red-100 dark:bg-red-900/30 text-red-700 dark:text-red-400'}`}>
                    <span className="material-symbols-outlined text-lg">{bcToast.type === 'success' ? 'check_circle' : 'error'}</span>
                    {bcToast.text}
                </div>
            )}

            {/* Broadcast Form */}
            {showBroadcast && (
                <div className="bg-white dark:bg-gray-900 rounded-2xl border border-purple-200 dark:border-purple-800/50 p-6 shadow-sm">
                    <div className="flex items-center gap-3 mb-5">
                        <div className="size-10 rounded-xl bg-gradient-to-br from-violet-500 to-purple-600 flex items-center justify-center">
                            <span className="material-symbols-outlined text-white">campaign</span>
                        </div>
                        <div>
                            <h3 className="font-bold text-gray-900 dark:text-white">Gửi thông báo đến người dùng</h3>
                            <p className="text-xs text-gray-400">Thông báo sẽ được gửi đến tất cả người dùng theo phạm vi chọn</p>
                        </div>
                    </div>

                    <div className="space-y-4">
                        {/* Target Selection */}
                        <div>
                            <label className="text-xs font-bold text-gray-500 dark:text-gray-400 uppercase tracking-wide mb-2 block">Phạm vi gửi</label>
                            <div className="flex gap-2">
                                {[
                                    { value: 'ALL', label: 'Toàn hệ thống', icon: 'public', color: 'from-blue-500 to-cyan-500' },
                                    { value: 'MANAGER', label: 'Quản lý lớp', icon: 'manage_accounts', color: 'from-amber-500 to-orange-500' },
                                    { value: 'STUDENT', label: 'Sinh viên', icon: 'school', color: 'from-emerald-500 to-teal-500' },
                                ].map(opt => (
                                    <button
                                        key={opt.value}
                                        onClick={() => setBcTarget(opt.value)}
                                        className={`flex-1 py-2.5 px-3 rounded-xl text-xs font-bold flex items-center justify-center gap-1.5 transition-all ${
                                            bcTarget === opt.value
                                                ? `bg-gradient-to-r ${opt.color} text-white shadow-sm`
                                                : 'bg-gray-100 dark:bg-gray-800 text-gray-500 dark:text-gray-400 hover:bg-gray-200 dark:hover:bg-gray-700'
                                        }`}
                                    >
                                        <span className="material-symbols-outlined text-sm">{opt.icon}</span>
                                        {opt.label}
                                    </button>
                                ))}
                            </div>
                        </div>

                        {/* Title */}
                        <div>
                            <label className="text-xs font-bold text-gray-500 dark:text-gray-400 uppercase tracking-wide mb-2 block">Tiêu đề</label>
                            <input
                                type="text"
                                value={bcTitle}
                                onChange={e => setBcTitle(e.target.value)}
                                placeholder="Nhập tiêu đề thông báo..."
                                className="w-full px-4 py-2.5 rounded-xl border border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-800 text-gray-900 dark:text-white text-sm focus:outline-none focus:ring-2 focus:ring-purple-500/50 focus:border-transparent transition"
                                maxLength={100}
                            />
                        </div>

                        {/* Message */}
                        <div>
                            <label className="text-xs font-bold text-gray-500 dark:text-gray-400 uppercase tracking-wide mb-2 block">Nội dung</label>
                            <textarea
                                value={bcMessage}
                                onChange={e => setBcMessage(e.target.value)}
                                placeholder="Nhập nội dung thông báo..."
                                rows={3}
                                className="w-full px-4 py-2.5 rounded-xl border border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-800 text-gray-900 dark:text-white text-sm focus:outline-none focus:ring-2 focus:ring-purple-500/50 focus:border-transparent resize-none transition"
                                maxLength={500}
                            />
                        </div>

                        {/* Actions */}
                        <div className="flex justify-end gap-2 pt-1">
                            <button onClick={() => { setShowBroadcast(false); setBcTitle(''); setBcMessage(''); setBcTarget('ALL') }}
                                className="px-4 py-2.5 border border-gray-200 dark:border-gray-700 text-gray-600 dark:text-gray-300 text-sm font-medium rounded-xl hover:bg-gray-50 dark:hover:bg-gray-800 transition-colors">
                                Hủy
                            </button>
                            <button onClick={handleBroadcast}
                                disabled={bcSending}
                                className="px-5 py-2.5 bg-gradient-to-r from-violet-500 to-purple-600 text-white text-sm font-semibold rounded-xl hover:shadow-lg hover:shadow-purple-500/25 transition-all disabled:opacity-50 flex items-center gap-2">
                                {bcSending ? (
                                    <>
                                        <div className="w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin" />
                                        Đang gửi...
                                    </>
                                ) : (
                                    <>
                                        <span className="material-symbols-outlined text-lg">send</span>
                                        Gửi thông báo
                                    </>
                                )}
                            </button>
                        </div>
                    </div>
                </div>
            )}

            {/* Stats */}
            <div className="grid grid-cols-2 gap-4">
                <div className="bg-gradient-to-br from-blue-500 to-indigo-600 rounded-2xl p-5 text-white relative overflow-hidden">
                    <div className="absolute -right-4 -top-4 size-16 rounded-full bg-white/10 blur-xl" />
                    <div className="relative z-10 flex items-center gap-4">
                        <div className="size-11 rounded-xl bg-white/20 flex items-center justify-center"><span className="material-symbols-outlined text-xl">mark_email_unread</span></div>
                        <div><h3 className="text-2xl font-bold">{unreadCount}</h3><p className="text-sm text-white/80">Chưa đọc</p></div>
                    </div>
                </div>
                <div className="bg-gradient-to-br from-emerald-500 to-teal-500 rounded-2xl p-5 text-white relative overflow-hidden">
                    <div className="absolute -right-4 -top-4 size-16 rounded-full bg-white/10 blur-xl" />
                    <div className="relative z-10 flex items-center gap-4">
                        <div className="size-11 rounded-xl bg-white/20 flex items-center justify-center"><span className="material-symbols-outlined text-xl">notifications_active</span></div>
                        <div><h3 className="text-2xl font-bold">{notifications.length}</h3><p className="text-sm text-white/80">Tổng cộng</p></div>
                    </div>
                </div>
            </div>

            {/* Notification List */}
            <div className="bg-white dark:bg-gray-900 rounded-2xl border border-gray-100 dark:border-gray-800 overflow-hidden">
                {loading && page === 0 ? (
                    <div className="flex items-center justify-center py-16">
                        <div className="text-center">
                            <div className="w-8 h-8 border-3 border-blue-500/30 border-t-blue-500 rounded-full animate-spin mx-auto mb-3" />
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
                                    className={`flex items-start gap-4 px-6 py-4 cursor-pointer hover:bg-blue-50/40 dark:hover:bg-gray-800/50 transition-colors border-l-3 ${!notif.isRead ? `${cfg.border} bg-blue-50/30 dark:bg-blue-900/5` : 'border-l-transparent'}`}
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
                                                <div className="size-2.5 rounded-full bg-blue-500 shrink-0 mt-1.5 animate-pulse" />
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
                                <div className="w-5 h-5 border-2 border-blue-500/30 border-t-blue-500 rounded-full animate-spin mx-auto" />
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
                        <p className="text-gray-400 font-medium">Chưa có thông báo nào</p>
                        <p className="text-xs text-gray-300 dark:text-gray-600 mt-1">Thông báo sẽ hiển thị khi có hoạt động mới trong hệ thống</p>
                    </div>
                )}
            </div>
        </div>
    )
}
