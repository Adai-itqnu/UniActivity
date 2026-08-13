import { useState, useEffect, useCallback } from 'react'
import { Link, useOutletContext } from 'react-router-dom'

/* ── Quick actions config ── */
const QUICK_ACTIONS = [
    { name: 'Thành viên', icon: 'groups', path: '/manager/members', iconBg: 'bg-blue-500/10 text-blue-600' },
    { name: 'YC Tham gia', icon: 'person_add', path: '/manager/join-requests', iconBg: 'bg-amber-500/10 text-amber-500' },
    { name: 'YC Điểm', icon: 'grade', path: '/manager/point-requests', iconBg: 'bg-purple-500/10 text-purple-500' },
    { name: 'Hoạt động', icon: 'event', path: '/manager/activities', iconBg: 'bg-emerald-500/10 text-emerald-600' },
]

export default function ManagerDashboard() {
    const { currentUser } = useOutletContext()
    const [data, setData] = useState(null)
    const [loading, setLoading] = useState(true)
    const [error, setError] = useState(null)

    const fetchDashboardData = useCallback((silent = false) => {
        if (!silent) setLoading(true)
        fetch('/manager/api/dashboard', { credentials: 'include' })
            .then(r => { if (!r.ok) throw new Error('Không thể tải dữ liệu'); return r.json() })
            .then(setData)
            .catch(err => { if (!silent) setError(err.message) })
            .finally(() => { if (!silent) setLoading(false) })
    }, [])

    useEffect(() => {
        const initialFetch = setTimeout(() => fetchDashboardData(false), 0)
        return () => clearTimeout(initialFetch)
    }, [fetchDashboardData])

    // Lắng nghe sự kiện SSE để cập nhật số liệu Dashboard ngầm lập tức
    useEffect(() => {
        const handleDashboardUpdate = () => {
            console.log('[ManagerDashboard] 📊 Nhận tín hiệu SSE, cập nhật số liệu ngầm...');
            fetchDashboardData(true)
        }
        window.addEventListener('dashboard-update', handleDashboardUpdate)
        return () => window.removeEventListener('dashboard-update', handleDashboardUpdate)
    }, [fetchDashboardData])

    if (loading) return <Loading />

    if (error) {
        return (
            <div className="flex items-center justify-center h-[60vh]">
                <div className="text-center">
                    <span className="material-symbols-outlined text-5xl text-red-400 mb-4 block">error</span>
                    <p className="text-slate-500">{error}</p>
                    <button onClick={() => window.location.reload()} className="mt-4 px-4 py-2 bg-blue-600 text-white rounded-lg text-sm font-medium hover:bg-blue-700 transition-colors">Thử lại</button>
                </div>
            </div>
        )
    }

    if (!data || !data.hasClass) {
        return (
            <div className="flex flex-col items-center justify-center h-[60vh] text-center">
                <div className="size-20 rounded-2xl bg-amber-100 dark:bg-amber-900/30 flex items-center justify-center mb-4">
                    <span className="material-symbols-outlined text-4xl text-amber-500">warning</span>
                </div>
                <h2 className="text-xl font-bold text-gray-900 dark:text-white mb-2">Chưa được gán lớp</h2>
                <p className="text-sm text-gray-500 dark:text-gray-400 max-w-md">Bạn chưa được gán quản lý lớp nào. Vui lòng liên hệ Quản trị viên để được phân công.</p>
            </div>
        )
    }

    const userName = currentUser?.fullName || 'Quản lý'

    return (
        <div className="space-y-8">
            {/* ── Welcome Header ── */}
            <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
                <div>
                    <h2 className="text-2xl font-bold text-slate-900 dark:text-white tracking-tight">
                        Chào mừng, <span className="text-blue-600 dark:text-blue-400">{userName}</span>
                    </h2>
                    <p className="text-sm text-slate-500 dark:text-gray-400 mt-1">
                        {data.studentClass?.facultyName} – {data.studentClass?.name}
                    </p>
                </div>
                <div className="flex items-center gap-2">
                    <span className="px-3 py-1 text-xs font-bold bg-blue-100 dark:bg-blue-900/30 text-blue-600 dark:text-blue-400 rounded-full">Quản lý lớp</span>
                    <span className="px-3 py-1 text-xs font-bold bg-blue-600 text-white rounded-full">ACTIVE</span>
                </div>
            </div>

            {/* ── Stats Grid ── */}
            <StatsGrid data={data} />

            {/* ── Class Info + Member Count + Quick Actions ── */}
            <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
                <ClassInfoCard classData={data.studentClass} />
                <MemberCountCard count={data.memberCount} />
                <QuickActionsGrid />
            </div>

            {/* ── Overview Summary ── */}
            <OverviewSection data={data} />

            {/* ── Active Activities + Recent Members ── */}
            <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
                <ActiveActivitiesSection activities={data.activeActivities} />
                <RecentMembersSection members={data.recentMembers} />
            </div>

            {/* ── Event Banner ── */}
            <EventBanner activity={data.activeActivities?.[0]} />
        </div>
    )
}

/* ═══════════════════════════════════════════ */
/*  SUB-COMPONENTS                            */
/* ═══════════════════════════════════════════ */

function StatsGrid({ data }) {
    const items = [
        { label: 'Thành viên', value: data.memberCount, icon: 'groups', gradient: 'from-blue-500 to-indigo-600', link: '/manager/members' },
        { label: 'YC tham gia', value: data.pendingJoinRequests, icon: 'person_add', gradient: 'from-amber-400 to-orange-500', link: '/manager/join-requests' },
        { label: 'YC điểm', value: data.pendingPointRequests, icon: 'grade', gradient: 'from-purple-500 to-pink-500', link: '/manager/point-requests' },
        { label: 'Điểm TB', value: data.avgTrainingPoints, icon: 'trending_up', gradient: 'from-emerald-500 to-teal-500', link: '/manager/reports' },
    ]
    return (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-5">
            {items.map((s, i) => (
                <Link key={i} to={s.link} className={`bg-gradient-to-br ${s.gradient} rounded-2xl p-5 text-white relative overflow-hidden hover:-translate-y-1 hover:shadow-xl transition-all duration-300 group`}>
                    <div className="absolute -right-4 -top-4 size-20 rounded-full bg-white/10 blur-xl" />
                    <div className="relative z-10">
                        <div className="flex items-center justify-between mb-3">
                            <div className="size-11 rounded-xl bg-white/20 flex items-center justify-center"><span className="material-symbols-outlined text-xl">{s.icon}</span></div>
                            <span className="material-symbols-outlined text-white/50 group-hover:text-white/80 transition-colors">arrow_forward</span>
                        </div>
                        <h3 className="text-2xl font-bold">{s.value}</h3>
                        <p className="text-sm text-white/80">{s.label}</p>
                    </div>
                </Link>
            ))}
        </div>
    )
}

function ClassInfoCard({ classData }) {
    if (!classData) return null
    return (
        <div className="bg-white dark:bg-gray-900 rounded-2xl border border-gray-100 dark:border-gray-800 overflow-hidden">
            <div className="bg-gradient-to-r from-blue-500 to-cyan-500 px-6 py-4 flex items-center gap-3">
                <span className="material-symbols-outlined text-white text-xl">school</span>
                <h3 className="text-white font-bold">Thông tin lớp</h3>
            </div>
            <div className="p-6 space-y-3">
                <InfoRow label="Tên lớp" value={classData.name} />
                <InfoRow label="Mã lớp" value={classData.code} />
                <InfoRow label="Khoa" value={classData.facultyName || 'N/A'} />
                <div className="flex items-center justify-between pt-3 border-t border-gray-100 dark:border-gray-800">
                    <span className="text-sm text-gray-500">Mã tham gia</span>
                    <span className="font-mono text-sm font-bold text-blue-600 dark:text-blue-400 bg-blue-50 dark:bg-blue-900/30 px-3 py-1.5 rounded-lg tracking-wider">{classData.joinCode || '------'}</span>
                </div>
            </div>
        </div>
    )
}

function MemberCountCard({ count }) {
    return (
        <div className="bg-gradient-to-br from-blue-500 to-cyan-500 rounded-2xl p-6 flex flex-col items-center justify-center text-white relative overflow-hidden">
            <div className="absolute -right-6 -top-6 size-32 rounded-full bg-white/10 blur-xl" />
            <div className="absolute -left-8 -bottom-8 size-24 rounded-full bg-white/10 blur-xl" />
            <div className="relative z-10 text-center">
                <span className="text-6xl font-bold">{count}</span>
                <p className="mt-2 text-white/80 font-medium">Thành viên trong lớp</p>
            </div>
        </div>
    )
}

function QuickActionsGrid() {
    return (
        <div className="grid grid-cols-2 gap-3">
            {QUICK_ACTIONS.map((action, i) => (
                <Link key={i} to={action.path} className="bg-white dark:bg-gray-900 border border-gray-100 dark:border-gray-800 rounded-2xl p-4 flex flex-col items-center justify-center gap-2 hover:-translate-y-1 hover:shadow-md hover:border-blue-300 dark:hover:border-blue-700 transition-all duration-200 group">
                    <div className={`size-12 rounded-xl ${action.iconBg} flex items-center justify-center group-hover:scale-110 transition-transform`}>
                        <span className="material-symbols-outlined">{action.icon}</span>
                    </div>
                    <span className="text-xs font-semibold text-slate-700 dark:text-gray-300">{action.name}</span>
                </Link>
            ))}
        </div>
    )
}

function OverviewSection({ data }) {
    const items = [
        { label: 'Thành viên', value: data.memberCount, icon: 'groups', color: 'from-blue-400 to-blue-600' },
        { label: 'YC Tham gia', value: data.pendingJoinRequests, icon: 'person_add', color: 'from-amber-400 to-amber-600' },
        { label: 'YC Điểm', value: data.pendingPointRequests, icon: 'grade', color: 'from-purple-400 to-purple-600' },
        { label: 'Minh chứng', value: data.pendingEvidences, icon: 'description', color: 'from-pink-400 to-pink-600' },
        { label: 'HĐ đang mở', value: data.activeActivitiesCount, icon: 'event', color: 'from-emerald-400 to-emerald-600' },
    ]
    return (
        <div className="bg-white dark:bg-gray-900 rounded-2xl border border-gray-100 dark:border-gray-800 p-6">
            <div className="flex items-center gap-2 mb-5">
                <span className="material-symbols-outlined text-blue-500">bar_chart</span>
                <h3 className="font-bold text-gray-900 dark:text-white">Tổng quan nhanh</h3>
            </div>
            <div className="grid grid-cols-2 sm:grid-cols-5 gap-4">
                {items.map((item, i) => (
                    <div key={i} className="text-center p-4 rounded-xl bg-gray-50 dark:bg-gray-800/50 hover:shadow-md hover:-translate-y-0.5 transition-all duration-200">
                        <div className={`size-11 rounded-xl bg-gradient-to-br ${item.color} mx-auto mb-3 flex items-center justify-center shadow-sm`}>
                            <span className="material-symbols-outlined text-white text-lg">{item.icon}</span>
                        </div>
                        <p className="text-2xl font-bold text-gray-900 dark:text-white">{item.value}</p>
                        <p className="text-[10px] text-gray-400 mt-1 font-medium uppercase tracking-wide">{item.label}</p>
                    </div>
                ))}
            </div>
        </div>
    )
}

function ActiveActivitiesSection({ activities = [] }) {
    return (
        <div className="bg-white dark:bg-gray-900 rounded-2xl border border-gray-100 dark:border-gray-800 overflow-hidden">
            <div className="flex items-center justify-between px-6 py-4 border-b border-gray-100 dark:border-gray-800">
                <div className="flex items-center gap-2">
                    <span className="material-symbols-outlined text-emerald-500">event</span>
                    <h3 className="font-bold text-gray-900 dark:text-white">Hoạt động đang mở</h3>
                </div>
                <Link to="/manager/activities" className="text-xs font-medium text-blue-600 hover:text-blue-700 dark:text-blue-400">Xem tất cả →</Link>
            </div>
            <div className="divide-y divide-gray-50 dark:divide-gray-800">
                {activities?.length > 0 ? activities.map(act => (
                    <Link key={act.id} to={`/manager/activities/${act.id}`} className="flex items-center gap-3 px-6 py-3.5 hover:bg-blue-50/50 dark:hover:bg-gray-800/50 transition-colors group">
                        <div className="size-10 rounded-xl bg-emerald-500/10 flex items-center justify-center shrink-0 group-hover:scale-105 transition-transform">
                            <span className="material-symbols-outlined text-emerald-500 text-lg">event</span>
                        </div>
                        <div className="min-w-0 flex-1">
                            <p className="text-sm font-medium text-gray-900 dark:text-white truncate group-hover:text-blue-600 dark:group-hover:text-blue-400 transition-colors">{act.name}</p>
                            <p className="text-xs text-gray-400 flex items-center gap-1 mt-0.5"><span className="material-symbols-outlined text-xs">location_on</span>{act.location || 'Chưa có địa điểm'}</p>
                        </div>
                        <div className="text-right shrink-0">
                            <p className="text-sm font-bold text-blue-600 dark:text-blue-400">{act.registeredCount || 0}/{act.maxSlots || '∞'}</p>
                            <p className="text-[10px] text-gray-400">Đã ĐK</p>
                        </div>
                    </Link>
                )) : (
                    <div className="px-6 py-10 text-center">
                        <span className="material-symbols-outlined text-4xl text-gray-300 dark:text-gray-600 block mb-2">event_busy</span>
                        <p className="text-sm text-gray-400">Không có hoạt động đang mở</p>
                    </div>
                )}
            </div>
        </div>
    )
}

function RecentMembersSection({ members = [] }) {
    return (
        <div className="bg-white dark:bg-gray-900 rounded-2xl border border-gray-100 dark:border-gray-800 overflow-hidden">
            <div className="flex items-center justify-between px-6 py-4 border-b border-gray-100 dark:border-gray-800">
                <div className="flex items-center gap-2">
                    <span className="material-symbols-outlined text-blue-500">groups</span>
                    <h3 className="font-bold text-gray-900 dark:text-white">Thành viên gần đây</h3>
                </div>
                <Link to="/manager/members" className="text-xs font-medium text-blue-600 hover:text-blue-700 dark:text-blue-400">Xem tất cả →</Link>
            </div>
            <div className="divide-y divide-gray-50 dark:divide-gray-800">
                {members?.length > 0 ? members.map(m => (
                    <div key={m.id} className="flex items-center gap-3 px-6 py-3.5 hover:bg-blue-50/50 dark:hover:bg-gray-800/50 transition-colors">
                        {m.avatarUrl ? (
                            <img src={m.avatarUrl} alt={m.fullName} className="w-10 h-10 rounded-xl object-cover" />
                        ) : (
                            <div className="w-10 h-10 rounded-xl bg-gradient-to-br from-blue-400 to-indigo-600 flex items-center justify-center">
                                <span className="text-xs font-bold text-white">{(m.fullName || '?').split(' ').map(w => w[0]).join('').toUpperCase().slice(0, 2)}</span>
                            </div>
                        )}
                        <div className="min-w-0 flex-1">
                            <p className="text-sm font-medium text-gray-900 dark:text-white truncate">{m.fullName}</p>
                            <p className="text-xs text-gray-400">{m.username}</p>
                        </div>
                        <span className={`px-2.5 py-1 text-[10px] font-bold rounded-full ${m.role === 'MANAGER' ? 'bg-blue-100 dark:bg-blue-900/30 text-blue-600 dark:text-blue-400' : 'bg-gray-100 dark:bg-gray-800 text-gray-500'}`}>
                            {m.role === 'MANAGER' ? 'Quản lý' : 'SV'}
                        </span>
                    </div>
                )) : (
                    <div className="px-6 py-10 text-center">
                        <span className="material-symbols-outlined text-4xl text-gray-300 dark:text-gray-600 block mb-2">group_off</span>
                        <p className="text-sm text-gray-400">Chưa có thành viên</p>
                    </div>
                )}
            </div>
        </div>
    )
}

function EventBanner({ activity }) {
    if (!activity) {
        return (
            <div className="relative rounded-2xl overflow-hidden bg-gradient-to-r from-gray-600 to-gray-700 p-6 sm:p-8">
                <div className="absolute -top-10 -right-10 w-40 h-40 bg-white/10 rounded-full" />
                <div className="absolute -bottom-6 -left-6 w-24 h-24 bg-white/10 rounded-full" />
                <div className="relative flex items-center justify-between gap-4">
                    <div className="text-white">
                        <h3 className="text-xl font-bold">Không có sự kiện sắp tới</h3>
                        <p className="mt-1 text-sm text-gray-300 max-w-lg">Hiện tại chưa có hoạt động nào đang mở. Hãy theo dõi trang hoạt động để cập nhật!</p>
                    </div>
                    <Link to="/manager/activities" className="inline-flex items-center gap-2 px-5 py-2.5 rounded-xl bg-white text-gray-700 font-semibold text-sm hover:bg-gray-100 transition-colors shrink-0 shadow-lg">Xem hoạt động</Link>
                </div>
            </div>
        )
    }
    return (
        <div className="relative rounded-2xl overflow-hidden bg-gradient-to-r from-blue-600 to-cyan-500 p-6 sm:p-8">
            <div className="absolute -top-10 -right-10 w-40 h-40 bg-white/10 rounded-full" />
            <div className="absolute -bottom-6 -left-6 w-24 h-24 bg-white/10 rounded-full" />
            <div className="relative flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
                <div className="text-white">
                    <span className="text-[10px] font-bold uppercase tracking-wider text-white/70">Hoạt động nổi bật</span>
                    <h3 className="text-xl font-bold mt-1">{activity.name}</h3>
                    <div className="mt-2 flex items-center gap-4 text-xs text-blue-100">
                        {activity.location && (<span className="flex items-center gap-1"><span className="material-symbols-outlined text-sm">location_on</span>{activity.location}</span>)}
                        <span className="flex items-center gap-1"><span className="material-symbols-outlined text-sm">group</span>{activity.registeredCount || 0}/{activity.maxSlots || '∞'} đã đăng ký</span>
                    </div>
                </div>
                <Link to={`/manager/activities/${activity.id}`} className="inline-flex items-center gap-2 px-5 py-2.5 rounded-xl bg-white text-blue-600 font-semibold text-sm hover:bg-gray-100 transition-colors shrink-0 shadow-lg">Xem chi tiết</Link>
            </div>
        </div>
    )
}

function Loading() {
    return (
        <div className="flex items-center justify-center h-[60vh]">
            <div className="text-center">
                <div className="w-10 h-10 border-3 border-blue-500/30 border-t-blue-500 rounded-full animate-spin mx-auto mb-4" />
                <p className="text-sm text-slate-400">Đang tải dữ liệu...</p>
            </div>
        </div>
    )
}

function InfoRow({ label, value }) {
    return (
        <div className="flex items-center justify-between py-2 border-b border-gray-100 dark:border-gray-800 last:border-0">
            <span className="text-sm text-gray-500 dark:text-gray-400">{label}</span>
            <span className="text-sm font-medium text-gray-900 dark:text-white">{value}</span>
        </div>
    )
}
