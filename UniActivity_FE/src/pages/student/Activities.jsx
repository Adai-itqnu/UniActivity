import { useState, useEffect, useMemo } from 'react'
import { NavLink, useSearchParams } from 'react-router-dom'

export default function Activities() {
    const [searchParams] = useSearchParams()
    const [data, setData] = useState(null)
    const [loading, setLoading] = useState(true)
    const [search, setSearch] = useState(searchParams.get('search') || '')
    const [statusFilter, setStatusFilter] = useState('all')
    const [regFilter, setRegFilter] = useState('all')
    const [actionLoading, setActionLoading] = useState(null)
    const [toast, setToast] = useState(null)

    const fetchData = () => {
        setLoading(true)
        fetch('/student/api/activities', { credentials: 'include' })
            .then(r => { if (!r.ok) throw new Error('Lỗi'); return r.json() })
            .then(json => { setData(json); setLoading(false) })
            .catch(() => setLoading(false))
    }

    useEffect(() => { fetchData() }, [])

    const showToast = (type, text) => {
        setToast({ type, text })
        setTimeout(() => setToast(null), 3000)
    }

    const handleRegister = async (activityId) => {
        setActionLoading(activityId)
        try {
            const res = await fetch(`/student/api/activities/${activityId}/register`, {
                method: 'POST', credentials: 'include',
            })
            const json = await res.json()
            if (!res.ok) throw new Error(json.message)
            showToast('success', json.message)
            fetchData()
        } catch (e) {
            showToast('error', e.message)
        } finally {
            setActionLoading(null)
        }
    }

    const handleCancel = async (activityId) => {
        if (!confirm('Bạn có chắc muốn hủy đăng ký?')) return
        setActionLoading(activityId)
        try {
            const res = await fetch(`/student/api/activities/${activityId}/register`, {
                method: 'DELETE', credentials: 'include',
            })
            const json = await res.json()
            if (!res.ok) throw new Error(json.message)
            showToast('success', json.message)
            fetchData()
        } catch (e) {
            showToast('error', e.message)
        } finally {
            setActionLoading(null)
        }
    }

    // Filtered activities
    const filteredActivities = useMemo(() => {
        if (!data?.activities) return []
        return data.activities.filter(a => {
            // Search
            if (search) {
                const q = search.toLowerCase()
                if (!a.name?.toLowerCase().includes(q) && !a.location?.toLowerCase().includes(q)) return false
            }
            // Status filter
            if (statusFilter === 'open' && a.isDeadlinePassed) return false
            if (statusFilter === 'closed' && !a.isDeadlinePassed) return false
            // Registration filter
            if (regFilter === 'registered' && !a.isRegistered) return false
            if (regFilter === 'not-registered' && a.isRegistered) return false
            return true
        })
    }, [data, search, statusFilter, regFilter])

    if (loading) return <Loading />

    if (!data?.hasClass) {
        return (
            <div className="space-y-6">
                <PageHeader />
                <div className="bg-amber-50 dark:bg-amber-900/10 border border-amber-200 dark:border-amber-800/50 rounded-2xl p-8 text-center">
                    <span className="material-symbols-outlined text-5xl text-amber-500 mb-4 block">warning</span>
                    <h3 className="text-xl font-bold text-gray-900 dark:text-white mb-2">Bạn chưa tham gia lớp nào</h3>
                    <p className="text-gray-500 dark:text-gray-400">
                        Vui lòng vào <NavLink to="/student/home" className="text-emerald-500 font-bold hover:underline">trang chủ</NavLink> để tham gia lớp trước khi xem hoạt động.
                    </p>
                </div>
            </div>
        )
    }

    const allActivities = data.activities || []
    const registeredCount = allActivities.filter(a => a.isRegistered).length
    const openCount = allActivities.filter(a => !a.isDeadlinePassed).length

    return (
        <div className="space-y-6">
            <PageHeader />

            {/* Toast */}
            {toast && (
                <div className={`px-4 py-3 rounded-xl text-sm font-medium flex items-center gap-2 ${toast.type === 'success' ? 'bg-emerald-100 dark:bg-emerald-900/30 text-emerald-700 dark:text-emerald-400' : 'bg-red-100 dark:bg-red-900/30 text-red-700 dark:text-red-400'}`}>
                    <span className="material-symbols-outlined text-lg">{toast.type === 'success' ? 'check_circle' : 'error'}</span>
                    {toast.text}
                </div>
            )}

            {/* Stats */}
            <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                <StatCard icon="calendar_month" label="Hoạt động khả dụng" value={allActivities.length} gradient="from-blue-500 to-indigo-500" />
                <StatCard icon="how_to_reg" label="Đã đăng ký" value={registeredCount} gradient="from-emerald-500 to-teal-500" />
                <StatCard icon="door_open" label="Đang mở đăng ký" value={openCount} gradient="from-cyan-500 to-blue-500" />
            </div>

            {/* Search & Filters */}
            <div className="bg-white dark:bg-gray-900 rounded-2xl border border-gray-200 dark:border-gray-700 shadow-sm p-5">
                <div className="grid grid-cols-1 md:grid-cols-6 gap-3">
                    <div className="md:col-span-3 relative">
                        <span className="material-symbols-outlined absolute left-3 top-1/2 -translate-y-1/2 text-gray-400">search</span>
                        <input
                            type="text" value={search} onChange={e => setSearch(e.target.value)}
                            placeholder="Tìm kiếm hoạt động..."
                            className="w-full pl-10 pr-4 py-2.5 rounded-xl border border-gray-300 dark:border-gray-600 bg-gray-50 dark:bg-gray-800 text-gray-900 dark:text-white placeholder-gray-400 focus:outline-none focus:ring-2 focus:ring-emerald-500/50 text-sm"
                        />
                    </div>
                    <select value={statusFilter} onChange={e => setStatusFilter(e.target.value)} className="md:col-span-1.5 px-3 py-2.5 rounded-xl border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-800 text-gray-900 dark:text-white text-sm focus:outline-none focus:ring-2 focus:ring-emerald-500/50">
                        <option value="all">Tất cả trạng thái</option>
                        <option value="open">Đang mở</option>
                        <option value="closed">Đã hết hạn</option>
                    </select>
                    <select value={regFilter} onChange={e => setRegFilter(e.target.value)} className="md:col-span-1.5 px-3 py-2.5 rounded-xl border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-800 text-gray-900 dark:text-white text-sm focus:outline-none focus:ring-2 focus:ring-emerald-500/50">
                        <option value="all">Tất cả</option>
                        <option value="registered">Đã đăng ký</option>
                        <option value="not-registered">Chưa đăng ký</option>
                    </select>
                </div>
            </div>

            {/* Activities Grid */}
            {filteredActivities.length === 0 ? (
                <div className="bg-white dark:bg-gray-900 rounded-2xl border border-gray-200 dark:border-gray-700 shadow-sm p-12 text-center">
                    <span className="material-symbols-outlined text-6xl text-gray-300 dark:text-gray-600 block mb-3">
                        {allActivities.length === 0 ? 'event_busy' : 'search_off'}
                    </span>
                    <h3 className="text-lg font-bold text-gray-700 dark:text-gray-300 mb-1">
                        {allActivities.length === 0 ? 'Chưa có hoạt động nào' : 'Không tìm thấy kết quả'}
                    </h3>
                    <p className="text-sm text-gray-400">
                        {allActivities.length === 0 ? 'Hiện không có hoạt động nào dành cho bạn.' : 'Thử thay đổi từ khóa tìm kiếm hoặc bộ lọc.'}
                    </p>
                </div>
            ) : (
                <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5">
                    {filteredActivities.map(a => (
                        <ActivityCard
                            key={a.id}
                            activity={a}
                            onRegister={handleRegister}
                            onCancel={handleCancel}
                            actionLoading={actionLoading}
                        />
                    ))}
                </div>
            )}
        </div>
    )
}

/* ---- Sub-components ---- */

function PageHeader() {
    return (
        <div className="flex items-center gap-3">
            <div className="size-10 rounded-xl bg-blue-500/10 flex items-center justify-center">
                <span className="material-symbols-outlined text-blue-500">event</span>
            </div>
            <div>
                <h2 className="text-2xl font-bold text-gray-900 dark:text-white">Hoạt động</h2>
                <p className="text-sm text-gray-500 dark:text-gray-400">Khám phá và đăng ký các hoạt động dành cho bạn</p>
            </div>
        </div>
    )
}

function StatCard({ icon, label, value, gradient }) {
    return (
        <div className={`bg-gradient-to-br ${gradient} rounded-2xl p-5 text-white relative overflow-hidden`}>
            <div className="absolute -right-4 -top-4 size-20 rounded-full bg-white/10 blur-xl" />
            <div className="relative z-10 flex items-center gap-4">
                <div className="size-12 rounded-xl bg-white/20 flex items-center justify-center">
                    <span className="material-symbols-outlined text-xl">{icon}</span>
                </div>
                <div>
                    <h3 className="text-2xl font-bold">{value}</h3>
                    <p className="text-sm text-white/80">{label}</p>
                </div>
            </div>
        </div>
    )
}

function ActivityCard({ activity: a, onRegister, onCancel, actionLoading }) {
    const isFull = a.maxSlots > 0 && a.registeredCount >= a.maxSlots
    const progress = a.maxSlots > 0 ? Math.min((a.registeredCount / a.maxSlots) * 100, 100) : 0

    return (
        <div className="bg-white dark:bg-gray-900 rounded-2xl border border-gray-200 dark:border-gray-700 shadow-sm overflow-hidden hover:-translate-y-1 hover:shadow-lg transition-all duration-200 flex flex-col">
            {/* Banner */}
            <div className="h-40 relative">
                {a.bannerUrl ? (
                    <img src={a.bannerUrl} alt="" className="w-full h-full object-cover" />
                ) : (
                    <div className="w-full h-full bg-gradient-to-br from-slate-700 to-slate-900 flex items-center justify-center">
                        <span className="material-symbols-outlined text-5xl text-white/20">event</span>
                    </div>
                )}
                <div className="absolute inset-0 bg-gradient-to-t from-black/40 via-transparent to-transparent" />

                {/* Status badge */}
                <div className="absolute top-3 right-3">
                    {!a.isDeadlinePassed ? (
                        <span className="px-2 py-0.5 text-[10px] font-bold bg-emerald-500 text-white rounded-full shadow-sm flex items-center gap-1">
                            <span className="material-symbols-outlined text-xs">check_circle</span>
                            Đang mở
                        </span>
                    ) : (
                        <span className="px-2 py-0.5 text-[10px] font-bold bg-gray-500 text-white rounded-full shadow-sm flex items-center gap-1">
                            <span className="material-symbols-outlined text-xs">cancel</span>
                            Hết hạn
                        </span>
                    )}
                </div>

                {/* Registered badge */}
                {a.isRegistered && (
                    <div className="absolute top-3 left-3">
                        <span className="px-2 py-0.5 text-[10px] font-bold bg-blue-500 text-white rounded-full shadow-sm flex items-center gap-1">
                            <span className="material-symbols-outlined text-xs">bookmark</span>
                            Đã ĐK
                        </span>
                    </div>
                )}
            </div>

            {/* Content */}
            <div className="p-4 flex-1 flex flex-col">
                <h3 className="font-bold text-gray-900 dark:text-white line-clamp-2 mb-2 text-sm leading-tight">{a.name}</h3>
                <p className="text-xs text-gray-400 line-clamp-2 mb-3 flex-1">{a.description || 'Không có mô tả'}</p>

                {/* Progress */}
                <div className="mb-3">
                    <div className="flex items-center justify-between mb-1">
                        <span className="text-xs text-gray-400 flex items-center gap-1">
                            <span className="material-symbols-outlined text-sm">group</span>
                            Số lượng
                        </span>
                        <span className="text-xs font-bold text-gray-600 dark:text-gray-300">{a.registeredCount}/{a.maxSlots}</span>
                    </div>
                    <div className="h-1.5 bg-gray-200 dark:bg-gray-700 rounded-full overflow-hidden">
                        <div className={`h-full rounded-full transition-all ${isFull ? 'bg-red-500' : 'bg-emerald-500'}`} style={{ width: `${progress}%` }} />
                    </div>
                </div>

                {/* Info */}
                <div className="border-t border-gray-100 dark:border-gray-800 pt-3 space-y-1.5 mb-3">
                    {a.startTime && (
                        <div className="flex items-center gap-1.5 text-xs text-gray-500 dark:text-gray-400">
                            <span className="material-symbols-outlined text-sm text-blue-500">schedule</span>
                            {a.startTime}
                        </div>
                    )}
                    {a.location && (
                        <div className="flex items-center gap-1.5 text-xs text-gray-500 dark:text-gray-400">
                            <span className="material-symbols-outlined text-sm text-red-500">location_on</span>
                            {a.location}
                        </div>
                    )}
                </div>

                {/* Action */}
                {a.isRegistered ? (
                    <div className="flex gap-2">
                        <span className="flex-1 py-2 text-center text-xs font-bold bg-emerald-100 dark:bg-emerald-900/30 text-emerald-600 dark:text-emerald-400 rounded-xl flex items-center justify-center gap-1">
                            <span className="material-symbols-outlined text-sm">check</span>
                            Đã đăng ký
                        </span>
                        {!a.isDeadlinePassed && (
                            <button
                                onClick={() => onCancel(a.id)}
                                disabled={actionLoading === a.id}
                                className="px-3 py-2 text-xs font-bold border border-red-300 dark:border-red-800 text-red-500 rounded-xl hover:bg-red-50 dark:hover:bg-red-900/20 transition-colors disabled:opacity-50"
                                title="Hủy đăng ký"
                            >
                                <span className="material-symbols-outlined text-sm">close</span>
                            </button>
                        )}
                    </div>
                ) : (
                    <button
                        onClick={() => onRegister(a.id)}
                        disabled={a.isDeadlinePassed || isFull || actionLoading === a.id}
                        className={`w-full py-2.5 text-xs font-bold rounded-xl flex items-center justify-center gap-1 transition-colors disabled:opacity-50 ${isFull ? 'bg-gray-100 dark:bg-gray-800 text-gray-500' : 'bg-emerald-500 text-white hover:bg-emerald-600'}`}
                    >
                        {actionLoading === a.id ? 'Đang xử lý...' : isFull ? (
                            <>
                                <span className="material-symbols-outlined text-sm">block</span>
                                Đã đầy
                            </>
                        ) : (
                            <>
                                <span className="material-symbols-outlined text-sm">person_add</span>
                                Đăng ký
                            </>
                        )}
                    </button>
                )}
            </div>
        </div>
    )
}

function Loading() {
    return (
        <div className="flex items-center justify-center h-[60vh]">
            <div className="text-center">
                <div className="w-10 h-10 border-3 border-emerald-500/30 border-t-emerald-500 rounded-full animate-spin mx-auto mb-4" />
                <p className="text-sm text-gray-400">Đang tải dữ liệu...</p>
            </div>
        </div>
    )
}
