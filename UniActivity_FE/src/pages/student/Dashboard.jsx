import { useState, useEffect, useRef, useCallback } from 'react'
import { useOutletContext, NavLink } from 'react-router-dom'
import { Html5Qrcode } from 'html5-qrcode'

/* ==========================================
   CONSTANTS
   ========================================== */
const QUICK_ACTIONS = [
    { name: 'Hoạt động', icon: 'event', path: '/student/activities', iconBg: 'bg-emerald-500/10 text-emerald-600' },
    { name: 'Đăng ký', icon: 'app_registration', path: '/student/my-registrations', iconBg: 'bg-blue-500/10 text-blue-500' },
    { name: 'Lịch sử điểm', icon: 'trending_up', path: '/student/my-scores', iconBg: 'bg-purple-500/10 text-purple-500' },
    { name: 'Lớp của tôi', icon: 'school', path: '/student/my-class', iconBg: 'bg-orange-500/10 text-orange-500' },
]

/* ==========================================
   COMPONENT CHÍNH
   ========================================== */
export default function StudentDashboard() {
    const { currentUser } = useOutletContext()
    const [data, setData] = useState(null)
    const [loading, setLoading] = useState(true)
    const [error, setError] = useState(null)

    // Fetch dashboard data từ API backend
    const fetchDashboardData = useCallback(() => {
        fetch('/student/api/dashboard', { credentials: 'include' })
            .then((res) => {
                if (!res.ok) throw new Error('Không thể tải dữ liệu')
                return res.json()
            })
            .then((json) => {
                setData(json)
                setLoading(false)
            })
            .catch((err) => {
                setError(err.message)
                setLoading(false)
            })
    }, [])

    useEffect(() => {
        if (!currentUser) return
        const initialFetch = setTimeout(fetchDashboardData, 0)
        return () => clearTimeout(initialFetch)
    }, [currentUser, fetchDashboardData])

    // Tự động kiểm tra trạng thái duyệt lớp nếu đang chờ duyệt
    useEffect(() => {
        if (!data || data.hasClass || !data.hasPendingRequest) return

        const interval = setInterval(() => {
            fetch('/student/api/dashboard', { credentials: 'include' })
                .then((res) => res.ok ? res.json() : null)
                .then((json) => {
                    if (json && (json.hasClass !== data.hasClass || json.hasPendingRequest !== data.hasPendingRequest)) {
                        setData(json)
                    }
                })
                .catch(() => {})
        }, 5000) // 5 giây kiểm tra ngầm 1 lần

        return () => clearInterval(interval)
    }, [data])

    // Lắng nghe sự kiện SSE thông báo mới để cập nhật số liệu Dashboard ngầm lập tức
    useEffect(() => {
        const handleNotification = () => {
            console.log('[StudentDashboard] 🔔 Nhận thông báo mới, cập nhật Dashboard ngầm...')
            fetch('/student/api/dashboard', { credentials: 'include' })
                .then((res) => res.ok ? res.json() : null)
                .then((json) => {
                    if (json) setData(json)
                })
                .catch(() => {})
        }
        window.addEventListener('new-notification', handleNotification)
        return () => window.removeEventListener('new-notification', handleNotification)
    }, [])

    if (loading) {
        return (
            <div className="flex items-center justify-center h-[60vh]">
                <div className="text-center">
                    <div className="w-10 h-10 border-3 border-emerald-500/30 border-t-emerald-500 rounded-full animate-spin mx-auto mb-4" />
                    <p className="text-sm text-slate-400">Đang tải dữ liệu...</p>
                </div>
            </div>
        )
    }

    if (error) {
        return (
            <div className="flex items-center justify-center h-[60vh]">
                <div className="text-center">
                    <span className="material-symbols-outlined text-5xl text-red-400 mb-4 block">error</span>
                    <p className="text-slate-500">{error}</p>
                    <button onClick={() => window.location.reload()} className="mt-4 px-4 py-2 bg-emerald-500 text-white rounded-lg text-sm font-medium hover:bg-emerald-600">
                        Thử lại
                    </button>
                </div>
            </div>
        )
    }

    if (!data) return null

    const userName = data.user?.fullName || currentUser?.fullName || 'Sinh viên'
    const hasClass = data.hasClass

    return (
        <div className="space-y-8">
            {/* Welcome Header */}
            <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
                <div>
                    <h2 className="text-2xl font-bold text-slate-900 dark:text-white tracking-tight">
                        Chào mừng, <span className="text-emerald-500">{userName}</span>
                    </h2>
                    {hasClass && data.studentClass && (
                        <p className="text-sm text-slate-500 dark:text-gray-400 mt-1">
                            {data.studentClass.facultyName} – {data.studentClass.name}
                        </p>
                    )}
                </div>
                {hasClass && (
                    <div className="flex items-center gap-2">
                        <span className="px-3 py-1 text-xs font-bold bg-emerald-100 dark:bg-emerald-900/30 text-emerald-600 dark:text-emerald-400 rounded-full">
                            {data.trainingPoints?.classification || 'Chưa xếp loại'}
                        </span>
                        <span className="px-3 py-1 text-xs font-bold bg-emerald-500 text-white rounded-full">
                            ACTIVE
                        </span>
                    </div>
                )}
            </div>

            {/* ===== TRẠNG THÁI CHƯA CÓ LỚP ===== */}
            {!hasClass && <NoClassSection hasPendingRequest={data.hasPendingRequest} pendingClassName={data.pendingClassName} />}

            {/* ===== TRẠNG THÁI ĐÃ CÓ LỚP ===== */}
            {hasClass && (
                <>
                    {/* Stats Grid */}
                    <StatsGrid stats={data.stats} />

                    {/* Class Info + Quick Actions + Next Event */}
                    <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
                        <ClassInfoCard classData={data.studentClass} />
                        <QuickActionsGrid />
                        <NextEventCard activity={data.upcomingActivities?.[0]} />
                    </div>

                    {/* Training Points */}
                    {data.trainingPoints && <TrainingPointsSection data={data.trainingPoints} />}

                    {/* HOT Activities */}
                    {data.upcomingActivities?.length > 0 && (
                        <HotActivitiesSection activities={data.upcomingActivities} registeredIds={data.registeredActivityIds || []} />
                    )}
                </>
            )}
        </div>
    )
}

/* ==========================================
   SUB-COMPONENTS
   ========================================== */

/* ---- Stats Grid ---- */
function StatsGrid({ stats }) {
    if (!stats) return null
    const items = [
        { label: 'Hoạt động đã tham gia', value: stats.eventsAttended || 0, icon: 'event_available', iconBg: 'bg-emerald-500/10 text-emerald-600' },
        { label: 'Điểm rèn luyện', value: stats.totalScore || 0, icon: 'star', iconBg: 'bg-blue-500/10 text-blue-500' },
        { label: 'Xếp hạng', value: stats.classification || 'Chưa XL', icon: 'workspace_premium', iconBg: 'bg-purple-500/10 text-purple-500' },
        { label: 'Đang chờ duyệt', value: stats.pendingRegistrations || 0, icon: 'pending_actions', iconBg: 'bg-orange-500/10 text-orange-500' },
    ]
    return (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
            {items.map((stat, i) => (
                <div key={i} className="bg-white dark:bg-gray-900 p-6 rounded-2xl border border-slate-200 dark:border-gray-700 shadow-sm hover:shadow-md hover:-translate-y-0.5 transition-all duration-200">
                    <div className="flex items-center justify-between mb-4">
                        <div className={`size-12 rounded-xl ${stat.iconBg} flex items-center justify-center`}>
                            <span className="material-symbols-outlined">{stat.icon}</span>
                        </div>
                    </div>
                    <h3 className="text-2xl font-bold text-slate-900 dark:text-white">{stat.value}</h3>
                    <p className="text-sm text-slate-500 dark:text-gray-400">{stat.label}</p>
                </div>
            ))}
        </div>
    )
}

/* ---- Trạng thái chưa có lớp ---- */
function NoClassSection({ hasPendingRequest, pendingClassName }) {
    const [joinCode, setJoinCode] = useState('')
    const [submitting, setSubmitting] = useState(false)
    const [message, setMessage] = useState(null)
    const [showScanner, setShowScanner] = useState(false)

    const handleJoin = async (e) => {
        if (e) e.preventDefault()
        const code = typeof e === 'string' ? e : joinCode.trim()
        if (!code) return
        setSubmitting(true)
        setMessage(null)
        try {
            const res = await fetch('/student/api/join-class', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                credentials: 'include',
                body: JSON.stringify({ joinCode: code }),
            })
            const data = await res.json()
            if (!res.ok) throw new Error(data.message || 'Không thể gửi yêu cầu')
            setMessage({ type: 'success', text: data.message })
            setTimeout(() => window.location.reload(), 1500)
        } catch (err) {
            setMessage({ type: 'error', text: err.message })
        } finally {
            setSubmitting(false)
        }
    }

    const handleScanned = async (code) => {
        setShowScanner(false)
        setJoinCode(code)
        setSubmitting(true)
        setMessage(null)
        try {
            const res = await fetch('/student/api/join-class', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                credentials: 'include',
                body: JSON.stringify({ joinCode: code }),
            })
            const data = await res.json()
            if (!res.ok) throw new Error(data.message || 'Không thể gửi yêu cầu')
            setMessage({ type: 'success', text: data.message })
            setTimeout(() => window.location.reload(), 1500)
        } catch (err) {
            setMessage({ type: 'error', text: err.message })
        } finally {
            setSubmitting(false)
        }
    }

    return (
        <div className="space-y-6">
            {hasPendingRequest ? (
                <div className="bg-gradient-to-br from-blue-500/10 to-cyan-500/10 border border-blue-200 dark:border-blue-800 rounded-2xl p-8 text-center">
                    <div className="size-16 rounded-2xl bg-blue-500/10 flex items-center justify-center mx-auto mb-4">
                        <span className="material-symbols-outlined text-4xl text-blue-500">hourglass_top</span>
                    </div>
                    <h3 className="text-xl font-bold text-slate-900 dark:text-white mb-2">Yêu cầu đang chờ duyệt</h3>
                    <p className="text-slate-500 dark:text-gray-400 max-w-md mx-auto">
                        Bạn đã gửi yêu cầu tham gia lớp <strong className="text-emerald-500">{pendingClassName}</strong>. Vui lòng chờ Manager duyệt.
                    </p>
                </div>
            ) : (
                <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
                    <div className="lg:col-span-2 bg-gradient-to-br from-amber-500/10 via-orange-500/10 to-red-500/10 border border-amber-200 dark:border-amber-900/50 rounded-2xl p-8 relative overflow-hidden">
                        <div className="absolute -right-10 -top-10 size-40 rounded-full bg-amber-500/5 blur-2xl" />
                        <div className="relative z-10">
                            <div className="size-14 rounded-2xl bg-amber-500/20 flex items-center justify-center mb-4">
                                <span className="material-symbols-outlined text-3xl text-amber-600">warning</span>
                            </div>
                            <h3 className="text-2xl font-bold text-slate-900 dark:text-white mb-2">Bạn chưa tham gia lớp nào</h3>
                            <p className="text-slate-500 dark:text-gray-400 mb-6 max-w-lg">
                                Để xem thông tin lớp, hoạt động và đăng ký sự kiện, bạn cần tham gia một lớp học trước.
                            </p>
                            {message && (
                                <div className={`mb-4 px-4 py-2 rounded-lg text-sm ${message.type === 'success' ? 'bg-emerald-100 text-emerald-700 dark:bg-emerald-900/30 dark:text-emerald-400' : 'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-400'}`}>
                                    {message.text}
                                </div>
                            )}
                            <form onSubmit={handleJoin} className="flex gap-3 max-w-md">
                                <div className="relative flex-1">
                                    <span className="material-symbols-outlined absolute left-3 top-1/2 -translate-y-1/2 text-slate-400">key</span>
                                    <input
                                        type="text"
                                        value={joinCode}
                                        onChange={(e) => setJoinCode(e.target.value)}
                                        placeholder="Nhập mã lớp (VD: ABC123)"
                                        className="w-full pl-10 pr-4 py-3 rounded-xl border border-slate-300 dark:border-gray-700 bg-white dark:bg-gray-800 text-slate-900 dark:text-white placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-emerald-500/50 focus:border-emerald-500"
                                        disabled={submitting}
                                    />
                                </div>
                                <button
                                    type="submit"
                                    disabled={submitting}
                                    className="px-6 py-3 bg-emerald-500 text-white font-bold rounded-xl hover:bg-emerald-600 transition-colors flex items-center gap-2 shrink-0 disabled:opacity-50"
                                >
                                    {submitting ? 'Đang gửi...' : 'Tham gia'}
                                    {!submitting && <span className="material-symbols-outlined text-lg">arrow_forward</span>}
                                </button>
                            </form>
                        </div>
                    </div>
                    <div className="bg-white dark:bg-gray-900 border border-slate-200 dark:border-gray-700 rounded-2xl p-6 flex flex-col items-center justify-center text-center">
                        <div className="size-16 rounded-2xl bg-emerald-500/10 flex items-center justify-center mb-4">
                            <span className="material-symbols-outlined text-4xl text-emerald-500">qr_code_scanner</span>
                        </div>
                        <h4 className="font-bold text-slate-900 dark:text-white mb-1">Quét mã QR</h4>
                        <p className="text-xs text-slate-400 mb-4">Tham gia nhanh bằng camera</p>
                        <button
                            onClick={() => setShowScanner(true)}
                            className="w-full py-2.5 border-2 border-emerald-500 text-emerald-600 dark:text-emerald-400 font-bold text-sm rounded-xl hover:bg-emerald-500 hover:text-white transition-all flex items-center justify-center gap-2"
                        >
                            <span className="material-symbols-outlined text-lg">photo_camera</span>
                            Mở Scanner
                        </button>
                    </div>
                </div>
            )}

            {/* QR Scanner Modal */}
            {showScanner && (
                <QrScannerModal
                    onScanned={handleScanned}
                    onClose={() => setShowScanner(false)}
                    onError={(msg) => { setShowScanner(false); setMessage({ type: 'error', text: msg }) }}
                />
            )}
        </div>
    )
}

/* ---- Class Info Card ---- */
function ClassInfoCard({ classData }) {
    if (!classData) return null
    return (
        <div className="bg-gradient-to-br from-emerald-500/5 to-teal-500/5 dark:from-emerald-900/20 dark:to-teal-900/20 border border-emerald-200 dark:border-emerald-800/50 rounded-2xl p-6 relative overflow-hidden">
            <div className="absolute -right-6 -bottom-6 size-32 rounded-full bg-emerald-500/5 blur-2xl" />
            <div className="relative z-10">
                <div className="flex items-center gap-2 mb-3">
                    <span className="material-symbols-outlined text-emerald-500 text-lg">verified</span>
                    <span className="text-xs font-bold text-emerald-600 dark:text-emerald-400 uppercase tracking-wider">Lớp hiện tại</span>
                </div>
                <h3 className="text-xl font-bold text-slate-900 dark:text-white mb-1">{classData.name}</h3>
                <p className="text-sm text-slate-500 dark:text-gray-400 mb-4">
                    {classData.facultyName || 'Khoa'}
                </p>
                <div className="flex items-center gap-3 mb-4">
                    <span className="px-2.5 py-1 text-xs font-bold bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 rounded-lg">{classData.code}</span>
                    <span className="text-xs text-slate-400">• {classData.memberCount || 0} thành viên</span>
                </div>
                <NavLink to="/student/my-class" className="inline-flex items-center gap-2 px-4 py-2 bg-emerald-500 text-white text-sm font-bold rounded-xl hover:bg-emerald-600 transition-colors">
                    Xem thành viên
                    <span className="material-symbols-outlined text-lg">arrow_forward</span>
                </NavLink>
            </div>
        </div>
    )
}

/* ---- Quick Actions Grid ---- */
function QuickActionsGrid() {
    return (
        <div className="grid grid-cols-2 gap-3">
            {QUICK_ACTIONS.map((action, i) => (
                <NavLink
                    key={i}
                    to={action.path}
                    className="bg-white dark:bg-gray-900 border border-slate-200 dark:border-gray-700 rounded-2xl p-4 flex flex-col items-center justify-center gap-2 hover:-translate-y-1 hover:shadow-md hover:border-emerald-300 dark:hover:border-emerald-700 transition-all duration-200 group"
                >
                    <div className={`size-12 rounded-xl ${action.iconBg} flex items-center justify-center group-hover:scale-110 transition-transform`}>
                        <span className="material-symbols-outlined">{action.icon}</span>
                    </div>
                    <span className="text-xs font-semibold text-slate-700 dark:text-gray-300">{action.name}</span>
                </NavLink>
            ))}
        </div>
    )
}

/* ---- Next Event Highlight ---- */
function NextEventCard({ activity }) {
    if (!activity) {
        return (
            <div className="bg-gradient-to-br from-violet-500 to-purple-700 rounded-2xl p-6 text-white relative overflow-hidden flex flex-col justify-center items-center text-center">
                <span className="material-symbols-outlined text-5xl text-white/30 mb-2">event_busy</span>
                <p className="text-sm text-white/70">Chưa có sự kiện sắp tới</p>
            </div>
        )
    }
    return (
        <div className="bg-gradient-to-br from-violet-500 to-purple-700 rounded-2xl p-6 text-white relative overflow-hidden flex flex-col justify-between">
            <div className="absolute -right-6 -top-6 size-28 rounded-full bg-white/10 blur-xl" />
            <div className="relative z-10">
                <span className="text-[10px] font-bold uppercase tracking-wider text-white/70">Sắp diễn ra</span>
                <h3 className="text-lg font-bold mt-2 mb-1 leading-snug line-clamp-2">{activity.name}</h3>
                <p className="text-xs text-white/70 mb-4 line-clamp-2">{activity.description || activity.location}</p>
            </div>
            <div className="relative z-10 flex items-end justify-between">
                <span className="text-xs text-white/80">
                    <span className="material-symbols-outlined text-[14px] align-text-bottom mr-1">schedule</span>
                    {activity.startTime}
                </span>
                {activity.totalPoints && (
                    <span className="px-2 py-0.5 bg-white/20 backdrop-blur text-xs font-bold rounded-full">
                        +{activity.totalPoints} pts
                    </span>
                )}
            </div>
        </div>
    )
}

/* ---- Training Points (SVG doughnut) ---- */
function TrainingPointsSection({ data }) {
    const total = data.categories?.reduce((s, c) => s + c.value, 0) || 1

    return (
        <div className="bg-white dark:bg-gray-900 rounded-2xl border border-slate-200 dark:border-gray-700 shadow-sm p-6">
            <div className="flex items-center justify-between mb-6">
                <div>
                    <h3 className="text-lg font-bold text-slate-900 dark:text-white">Phân phối điểm rèn luyện</h3>
                    <p className="text-sm text-slate-500 dark:text-gray-400">Tổng điểm tích lũy học kỳ này</p>
                </div>
                <NavLink to="/student/my-scores" className="text-sm font-medium text-emerald-500 hover:text-emerald-600">Chi tiết →</NavLink>
            </div>
            <div className="grid grid-cols-1 md:grid-cols-3 gap-8 items-center">
                {/* Doughnut Chart */}
                <div className="flex flex-col items-center">
                    <div className="relative size-44">
                        <svg viewBox="0 0 100 100" className="w-full h-full -rotate-90">
                            {data.categories?.map((cat, i) => {
                                const pct = total > 0 ? (cat.value / total) * 100 : 0
                                const circumference = 2 * Math.PI * 40
                                const offset = data.categories.slice(0, i).reduce((s, c) => s + (c.value / total) * circumference, 0)
                                return (
                                    <circle key={i} cx="50" cy="50" r="40" fill="none" stroke={cat.color} strokeWidth="10"
                                        strokeDasharray={`${(pct / 100) * circumference} ${circumference}`}
                                        strokeDashoffset={-offset} strokeLinecap="round" className="transition-all duration-500" />
                                )
                            })}
                        </svg>
                        <div className="absolute inset-0 flex flex-col items-center justify-center">
                            <span className="text-3xl font-bold text-slate-900 dark:text-white">{data.classification || '?'}</span>
                            <span className="text-xs text-slate-400 uppercase">Xếp loại</span>
                        </div>
                    </div>
                </div>

                {/* Legend */}
                <div className="space-y-3">
                    {data.categories?.map((cat, i) => (
                        <div key={i} className="flex items-center justify-between">
                            <span className="flex items-center gap-2 text-sm text-slate-600 dark:text-gray-300">
                                <span className="size-2.5 rounded-full shrink-0" style={{ backgroundColor: cat.color }} />
                                {cat.name}
                            </span>
                            <span className="text-sm font-bold text-slate-900 dark:text-white">{cat.value}</span>
                        </div>
                    ))}
                </div>

                {/* Total + Progress */}
                <div className="flex flex-col items-center lg:items-start gap-3">
                    <div>
                        <span className="text-4xl font-bold text-slate-900 dark:text-white">{data.totalScore}</span>
                        <span className="text-lg text-slate-400 ml-1">pts</span>
                    </div>
                    <div className="w-full bg-slate-200 dark:bg-gray-700 h-2 rounded-full overflow-hidden">
                        <div className="h-full bg-gradient-to-r from-emerald-400 to-teal-500 rounded-full transition-all" style={{ width: `${Math.min((data.totalScore / 100) * 100, 100)}%` }} />
                    </div>
                    <p className="text-xs text-slate-400">{data.classification || 'Chưa xếp loại'}</p>
                </div>
            </div>
        </div>
    )
}

/* ---- HOT Activities ---- */
function HotActivitiesSection({ activities, registeredIds = [] }) {
    const registeredSet = new Set(registeredIds)
    return (
        <div>
            <div className="flex items-center justify-between mb-4">
                <h3 className="text-lg font-bold text-slate-900 dark:text-white flex items-center gap-2">
                    <span className="material-symbols-outlined text-orange-500">local_fire_department</span>
                    Hoạt động sắp tới
                </h3>
                <NavLink to="/student/activities" className="text-sm font-medium text-emerald-500 hover:text-emerald-600">Xem tất cả →</NavLink>
            </div>
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
                {activities.map((a) => (
                    <div key={a.id} className="bg-white dark:bg-gray-900 rounded-2xl border border-slate-200 dark:border-gray-700 overflow-hidden hover:-translate-y-1 hover:shadow-lg transition-all duration-200">
                        <div className="h-32 bg-gradient-to-br from-slate-700 to-slate-900 relative flex items-end p-4">
                            {a.bannerUrl && <img src={a.bannerUrl} alt="" className="absolute inset-0 w-full h-full object-cover" />}
                            <div className="absolute inset-0 bg-gradient-to-t from-black/60 via-transparent to-transparent" />
                            {registeredSet.has(a.id) && (
                                <span className="absolute top-3 left-3 px-2 py-0.5 bg-emerald-500 text-white text-[10px] font-bold rounded-full">Đã ĐK</span>
                            )}
                            {a.maxSlots > 0 && a.registeredCount >= a.maxSlots && (
                                <span className="absolute top-3 right-3 px-2 py-0.5 bg-red-500 text-white text-[10px] font-bold rounded-full">ĐẦY</span>
                            )}
                            <h4 className="relative z-10 text-white font-bold text-sm leading-tight line-clamp-2">{a.name}</h4>
                        </div>
                        <div className="p-4 space-y-2">
                            <div className="flex items-center gap-1 text-xs text-slate-500 dark:text-gray-400">
                                <span className="material-symbols-outlined text-[14px]">schedule</span>
                                {a.startTime}
                            </div>
                            {a.location && (
                                <div className="flex items-center gap-1 text-xs text-slate-500 dark:text-gray-400">
                                    <span className="material-symbols-outlined text-[14px]">location_on</span>
                                    {a.location}
                                </div>
                            )}
                            <div className="flex items-center justify-between pt-2 border-t border-slate-100 dark:border-gray-700">
                                <div className="flex items-center gap-3 text-xs text-slate-400">
                                    {a.totalPoints && <span className="font-bold text-emerald-500">+{a.totalPoints} pts</span>}
                                    <span>{a.registeredCount}/{a.maxSlots}</span>
                                </div>
                                <NavLink
                                    to="/student/activities"
                                    className="px-3 py-1.5 text-xs font-bold border border-slate-300 dark:border-gray-600 text-slate-700 dark:text-gray-300 rounded-lg hover:bg-emerald-500 hover:text-white hover:border-emerald-500 transition-all"
                                >
                                    Chi tiết
                                </NavLink>
                            </div>
                        </div>
                    </div>
                ))}
            </div>
        </div>
    )
}

/* ---- QR Scanner Modal ---- */
function QrScannerModal({ onScanned, onClose, onError }) {
    const containerRef = useRef(null)
    const scannerRef = useRef(null)
    const mountedRef = useRef(true)
    const [status, setStatus] = useState('Đang khởi tạo camera...')

    useEffect(() => {
        mountedRef.current = true
        let html5Qr = null

        const startScanner = async () => {
            const el = containerRef.current
            if (!el || !mountedRef.current) return

            // Clean up any leftover DOM (StrictMode double-mount)
            el.replaceChildren()

            try {
                // Step 1: Enumerate cameras FIRST (also triggers permission)
                setStatus('Đang yêu cầu quyền camera...')
                console.log('[QR] Enumerating cameras...')
                const cameras = await Html5Qrcode.getCameras()
                console.log('[QR] Found cameras:', cameras.map(c => `${c.label} (${c.id})`))

                if (!cameras.length) {
                    onError('Không tìm thấy camera nào trên thiết bị.')
                    return
                }
                if (!mountedRef.current) return

                // Step 2: Pick camera - prefer back camera on mobile, first available on desktop
                let cameraId = cameras[0].id
                const backCam = cameras.find(c => /back|rear|environment/i.test(c.label))
                if (backCam) cameraId = backCam.id
                console.log('[QR] Using camera:', cameraId)

                // Step 3: Create scanner and start with camera ID directly
                setStatus('Đang mở camera...')
                html5Qr = new Html5Qrcode(el.id, { verbose: false })
                scannerRef.current = html5Qr

                await html5Qr.start(
                    cameraId,
                    { fps: 10, qrbox: 250, disableFlip: false },
                    (decodedText) => {
                        if (!mountedRef.current) return
                        mountedRef.current = false
                        const code = decodedText.trim()
                        console.log('[QR] ✅ Decoded:', code)
                        if (html5Qr && html5Qr.isScanning) {
                            html5Qr.stop().then(() => onScanned(code)).catch(() => onScanned(code))
                        } else {
                            onScanned(code)
                        }
                    },
                    () => {} // per-frame error (ignore - normal when no QR visible)
                )
                console.log('[QR] ✅ Scanner running, waiting for QR code...')
                setStatus('')
            } catch (err) {
                console.error('[QR] ❌ Error:', err)
                if (mountedRef.current) {
                    onError('Không thể mở camera: ' + (err?.message || err))
                }
            }
        }

        const timer = setTimeout(startScanner, 300)

        return () => {
            clearTimeout(timer)
            mountedRef.current = false
            const s = scannerRef.current
            scannerRef.current = null
            if (s) {
                try { if (s.isScanning) s.stop().catch(() => {}) } catch { /* Scanner already stopped. */ }
            }
        }
    }, []) // eslint-disable-line react-hooks/exhaustive-deps

    return (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm" onClick={onClose}>
            <div
                className="bg-white dark:bg-gray-900 rounded-2xl shadow-2xl w-full max-w-md mx-4 overflow-hidden"
                onClick={(e) => e.stopPropagation()}
            >
                {/* Header */}
                <div className="flex items-center justify-between px-5 py-4 border-b border-slate-200 dark:border-gray-700">
                    <div className="flex items-center gap-2">
                        <span className="material-symbols-outlined text-emerald-500">qr_code_scanner</span>
                        <h3 className="font-bold text-slate-900 dark:text-white">Quét mã QR</h3>
                    </div>
                    <button onClick={onClose} className="size-8 rounded-lg bg-slate-100 dark:bg-gray-800 flex items-center justify-center hover:bg-red-100 hover:text-red-500 transition-colors">
                        <span className="material-symbols-outlined text-lg">close</span>
                    </button>
                </div>

                {/* Camera preview */}
                <div className="p-4">
                    <div
                        ref={containerRef}
                        id="qr-reader-container"
                        className="w-full rounded-xl overflow-hidden bg-black"
                        style={{ minHeight: 300 }}
                    />
                    {status ? (
                        <p className="text-xs text-amber-500 text-center mt-3 flex items-center justify-center gap-1">
                            <span className="material-symbols-outlined text-sm animate-spin">progress_activity</span>
                            {status}
                        </p>
                    ) : (
                        <p className="text-xs text-slate-400 text-center mt-3">
                            Hướng camera vào mã QR của lớp để quét tự động
                        </p>
                    )}
                </div>
            </div>
        </div>
    )
}
