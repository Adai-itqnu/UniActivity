import { useState, useEffect, useRef } from 'react'
import { Link } from 'react-router-dom'

function fmtDate(d) {
    if (!d) return ''
    const dt = new Date(d)
    const p = n => String(n).padStart(2, '0')
    return `${p(dt.getDate())}/${p(dt.getMonth() + 1)}/${dt.getFullYear()} ${p(dt.getHours())}:${p(dt.getMinutes())}`
}

const STATUS_COLORS = {
    OPEN: 'bg-emerald-100 dark:bg-emerald-900/30 text-emerald-600 dark:text-emerald-400',
    DRAFT: 'bg-gray-100 dark:bg-gray-800 text-gray-500',
    FINISHED: 'bg-blue-100 dark:bg-blue-900/30 text-blue-600 dark:text-blue-400',
    CANCELLED: 'bg-red-100 dark:bg-red-900/30 text-red-500',
}
const STATUS_LABELS = { OPEN: 'Đang mở', DRAFT: 'Bản nháp', FINISHED: 'Đã kết thúc', CANCELLED: 'Đã hủy' }
const STATUS_GRADIENTS = {
    OPEN: 'from-emerald-500 to-teal-500',
    DRAFT: 'from-gray-400 to-gray-500',
    FINISHED: 'from-blue-500 to-indigo-500',
    CANCELLED: 'from-red-400 to-rose-500',
}

export default function ManagerActivities() {
    const [activities, setActivities] = useState([])
    const [loading, setLoading] = useState(true)
    const [search, setSearch] = useState('')
    const [statusFilter, setStatusFilter] = useState('ALL')
    const [viewMode, setViewMode] = useState('table')
    const [qrModal, setQrModal] = useState(null)

    useEffect(() => {
        fetch('/manager/api/activities', { credentials: 'include' })
            .then(r => r.ok ? r.json() : [])
            .then(setActivities)
            .catch(() => setActivities([]))
            .finally(() => setLoading(false))
    }, [])

    const filtered = activities.filter(a => {
        if (a.status === 'DRAFT') return false // Ẩn hoạt động bản nháp
        const matchSearch = !search || a.name?.toLowerCase().includes(search.toLowerCase()) || a.location?.toLowerCase().includes(search.toLowerCase())
        const matchStatus = statusFilter === 'ALL' || a.status === statusFilter
        return matchSearch && matchStatus
    })

    /* ── Stat counts ── */
    const countOpen = activities.filter(a => a.status === 'OPEN').length
    const countFinished = activities.filter(a => a.status === 'FINISHED').length
    const totalRegistered = activities.reduce((s, a) => s + (a.registeredCount || 0), 0)

    const openQR = (activityId, activityName) => {
        setQrModal({ activityId, activityName })
    }

    if (loading) {
        return (
            <div className="flex items-center justify-center h-[60vh]">
                <div className="text-center">
                    <div className="w-10 h-10 border-3 border-blue-500/30 border-t-blue-500 rounded-full animate-spin mx-auto mb-4" />
                    <p className="text-sm text-slate-400">Đang tải hoạt động...</p>
                </div>
            </div>
        )
    }

    return (
        <div className="space-y-6">
            {/* ── Header ── */}
            <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
                <div className="flex items-center gap-3">
                    <div className="size-11 rounded-xl bg-gradient-to-br from-blue-500 to-cyan-500 flex items-center justify-center shadow-sm">
                        <span className="material-symbols-outlined text-white text-xl">event</span>
                    </div>
                    <div>
                        <h2 className="text-2xl font-bold text-gray-900 dark:text-white tracking-tight">Hoạt động</h2>
                        <p className="text-sm text-gray-500 dark:text-gray-400">Quản lý hoạt động liên quan đến lớp</p>
                    </div>
                </div>
                <span className="text-xs text-gray-400 dark:text-gray-500">Tổng {filtered.length} hoạt động</span>
            </div>

            {/* ── Stats ── */}
            <div className="grid grid-cols-1 sm:grid-cols-3 gap-5">
                {[
                    { label: 'Đang mở', value: countOpen, icon: 'event_available', gradient: 'from-emerald-500 to-teal-500' },
                    { label: 'Đã kết thúc', value: countFinished, icon: 'event_busy', gradient: 'from-blue-500 to-indigo-600' },
                    { label: 'Tổng đăng ký', value: totalRegistered, icon: 'how_to_reg', gradient: 'from-amber-400 to-orange-500' },
                ].map((s, i) => (
                    <div key={i} className={`bg-gradient-to-br ${s.gradient} rounded-2xl p-5 text-white relative overflow-hidden`}>
                        <div className="absolute -right-4 -top-4 size-20 rounded-full bg-white/10 blur-xl" />
                        <div className="relative z-10 flex items-center gap-4">
                            <div className="size-11 rounded-xl bg-white/20 flex items-center justify-center"><span className="material-symbols-outlined text-xl">{s.icon}</span></div>
                            <div>
                                <h3 className="text-2xl font-bold">{s.value}</h3>
                                <p className="text-sm text-white/80">{s.label}</p>
                            </div>
                        </div>
                    </div>
                ))}
            </div>

            {/* ── Filters + View Toggle ── */}
            <div className="bg-white dark:bg-gray-900 rounded-2xl border border-gray-100 dark:border-gray-800 p-4">
                <div className="flex flex-col sm:flex-row items-stretch sm:items-center gap-3">
                    <div className="relative flex-1">
                        <span className="material-symbols-outlined absolute left-3 top-1/2 -translate-y-1/2 text-gray-400 text-lg">search</span>
                        <input type="text" value={search} onChange={e => setSearch(e.target.value)} placeholder="Tìm hoạt động..."
                            className="w-full pl-10 pr-4 py-2.5 rounded-xl border border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-800 text-gray-900 dark:text-white text-sm focus:outline-none focus:ring-2 focus:ring-blue-500/50 focus:border-transparent transition" />
                    </div>
                    <select value={statusFilter} onChange={e => setStatusFilter(e.target.value)}
                        className="px-4 py-2.5 rounded-xl border border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-800 text-gray-900 dark:text-white text-sm focus:outline-none focus:ring-2 focus:ring-blue-500/50">
                        <option value="ALL">Tất cả trạng thái</option>
                        <option value="OPEN">Đang mở</option>
                        <option value="FINISHED">Đã kết thúc</option>
                        <option value="CANCELLED">Đã hủy</option>
                    </select>
                    {/* View toggle */}
                    <div className="inline-flex rounded-xl border border-gray-200 dark:border-gray-700 overflow-hidden self-start">
                        <button onClick={() => setViewMode('grid')}
                            className={`px-3 py-2 text-sm flex items-center gap-1 transition-colors ${viewMode === 'grid' ? 'bg-blue-600 text-white' : 'bg-white dark:bg-gray-800 text-gray-500 hover:text-blue-600'}`}>
                            <span className="material-symbols-outlined text-lg">grid_view</span>
                        </button>
                        <button onClick={() => setViewMode('table')}
                            className={`px-3 py-2 text-sm flex items-center gap-1 transition-colors ${viewMode === 'table' ? 'bg-blue-600 text-white' : 'bg-white dark:bg-gray-800 text-gray-500 hover:text-blue-600'}`}>
                            <span className="material-symbols-outlined text-lg">view_list</span>
                        </button>
                    </div>
                </div>
            </div>

            {/* ── Content ── */}
            {filtered.length === 0 ? (
                <div className="bg-white dark:bg-gray-900 rounded-2xl border border-gray-100 dark:border-gray-800 py-16 text-center">
                    <span className="material-symbols-outlined text-5xl text-gray-300 dark:text-gray-600 block mb-3">event_busy</span>
                    <p className="text-gray-400 font-medium">Không tìm thấy hoạt động nào</p>
                </div>
            ) : viewMode === 'grid' ? (
                <GridView items={filtered} openQR={openQR} />
            ) : (
                <TableView items={filtered} openQR={openQR} />
            )}

            {/* ── QR Modal ── */}
            {qrModal && <QRModal data={qrModal} onClose={() => setQrModal(null)} />}
        </div>
    )
}

/* ═══════════════════════════════════════════ */
/*  GRID VIEW                                 */
/* ═══════════════════════════════════════════ */

function GridView({ items, openQR }) {
    return (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-5">
            {items.map(a => (
                <div key={a.id} className="bg-white dark:bg-gray-900 rounded-2xl border border-gray-100 dark:border-gray-800 overflow-hidden hover:shadow-lg hover:-translate-y-0.5 transition-all duration-300 group">
                    {/* Banner */}
                    <div className={`h-32 bg-gradient-to-br ${STATUS_GRADIENTS[a.status] || STATUS_GRADIENTS.DRAFT} relative overflow-hidden`}>
                        <div className="absolute -right-6 -top-6 w-24 h-24 rounded-full bg-white/10" />
                        <div className="absolute -left-4 -bottom-4 w-16 h-16 rounded-full bg-white/10" />
                        <div className="absolute inset-0 bg-black/10 opacity-0 group-hover:opacity-100 transition-opacity" />
                        {a.imageUrl && <img src={a.imageUrl} alt="" className="absolute inset-0 w-full h-full object-cover mix-blend-overlay opacity-40" />}
                        <div className="absolute top-3 left-3">
                            <span className={`px-2.5 py-1 text-[10px] font-bold rounded-full bg-white/20 backdrop-blur-sm text-white`}>
                                {STATUS_LABELS[a.status] || a.status}
                            </span>
                        </div>
                        <div className="absolute bottom-3 right-3 flex gap-1">
                            {a.status === 'OPEN' && (
                                <button onClick={() => openQR(a.id, a.name)} className="size-8 rounded-lg bg-white/20 backdrop-blur-sm flex items-center justify-center text-white hover:bg-white/40 transition-colors" title="QR Check-in">
                                    <span className="material-symbols-outlined text-lg">qr_code_2</span>
                                </button>
                            )}
                        </div>
                    </div>
                    {/* Body */}
                    <div className="p-5">
                        <Link to={`/manager/activities/${a.id}`} className="text-sm font-bold text-gray-900 dark:text-white hover:text-blue-600 dark:hover:text-blue-400 transition-colors line-clamp-2">
                            {a.name}
                        </Link>
                        <div className="mt-3 space-y-1.5">
                            {a.startTime && (
                                <div className="flex items-center gap-1.5 text-xs text-gray-400">
                                    <span className="material-symbols-outlined text-sm">schedule</span>{fmtDate(a.startTime)}
                                </div>
                            )}
                            {a.location && (
                                <div className="flex items-center gap-1.5 text-xs text-gray-400">
                                    <span className="material-symbols-outlined text-sm">location_on</span>{a.location}
                                </div>
                            )}
                        </div>
                        <div className="mt-4 flex items-center justify-between">
                            <div className="flex items-center gap-1.5 text-xs">
                                <span className="material-symbols-outlined text-blue-500 text-sm">group</span>
                                <span className="font-bold text-gray-900 dark:text-white">{a.registeredCount || 0}</span>
                                <span className="text-gray-400">/ {a.maxSlots || '∞'}</span>
                            </div>
                            <Link to={`/manager/activities/${a.id}`} className="text-xs font-medium text-blue-600 dark:text-blue-400 hover:underline">Chi tiết →</Link>
                        </div>
                    </div>
                </div>
            ))}
        </div>
    )
}

/* ═══════════════════════════════════════════ */
/*  TABLE VIEW                                */
/* ═══════════════════════════════════════════ */

function TableView({ items, openQR }) {
    return (
        <div className="bg-white dark:bg-gray-900 rounded-2xl border border-gray-100 dark:border-gray-800 overflow-hidden">
            <div className="overflow-x-auto">
                <table className="w-full text-sm">
                    <thead>
                        <tr className="bg-gray-50 dark:bg-gray-800/50">
                            <th className="text-left px-6 py-3 text-[10px] font-bold text-gray-400 uppercase tracking-wider">#</th>
                            <th className="text-left px-6 py-3 text-[10px] font-bold text-gray-400 uppercase tracking-wider">Tên hoạt động</th>
                            <th className="text-left px-6 py-3 text-[10px] font-bold text-gray-400 uppercase tracking-wider">Thời gian</th>
                            <th className="text-left px-6 py-3 text-[10px] font-bold text-gray-400 uppercase tracking-wider">Địa điểm</th>
                            <th className="text-center px-6 py-3 text-[10px] font-bold text-gray-400 uppercase tracking-wider">ĐK/Max</th>
                            <th className="text-center px-6 py-3 text-[10px] font-bold text-gray-400 uppercase tracking-wider">Trạng thái</th>
                            <th className="text-center px-6 py-3 text-[10px] font-bold text-gray-400 uppercase tracking-wider">Thao tác</th>
                        </tr>
                    </thead>
                    <tbody className="divide-y divide-gray-50 dark:divide-gray-800">
                        {items.map((a, i) => (
                            <tr key={a.id} className="hover:bg-blue-50/40 dark:hover:bg-gray-800/50 transition-colors">
                                <td className="px-6 py-3.5 text-gray-400 text-xs">{i + 1}</td>
                                <td className="px-6 py-3.5">
                                    <Link to={`/manager/activities/${a.id}`} className="font-medium text-gray-900 dark:text-white hover:text-blue-600 dark:hover:text-blue-400 transition-colors">
                                        {a.name}
                                    </Link>
                                </td>
                                <td className="px-6 py-3.5 text-gray-500 dark:text-gray-400 whitespace-nowrap text-xs">{fmtDate(a.startTime)}</td>
                                <td className="px-6 py-3.5 text-gray-500 dark:text-gray-400">{a.location || '—'}</td>
                                <td className="px-6 py-3.5 text-center">
                                    <span className="font-bold text-gray-900 dark:text-white">{a.registeredCount || 0}</span>
                                    <span className="text-gray-400">/{a.maxSlots || '∞'}</span>
                                </td>
                                <td className="px-6 py-3.5 text-center">
                                    <span className={`px-2.5 py-1 text-[10px] font-bold rounded-full ${STATUS_COLORS[a.status] || STATUS_COLORS.DRAFT}`}>
                                        {STATUS_LABELS[a.status] || a.status}
                                    </span>
                                </td>
                                <td className="px-6 py-3.5 text-center">
                                    <div className="flex items-center justify-center gap-1">
                                        <Link to={`/manager/activities/${a.id}`}
                                            className="size-8 inline-flex items-center justify-center rounded-lg hover:bg-blue-100 dark:hover:bg-gray-800 text-gray-400 hover:text-blue-600 transition-colors" title="Chi tiết">
                                            <span className="material-symbols-outlined text-lg">visibility</span>
                                        </Link>
                                        {a.status === 'OPEN' && (
                                            <button onClick={() => openQR(a.id, a.name)}
                                                className="size-8 inline-flex items-center justify-center rounded-lg hover:bg-blue-100 dark:hover:bg-gray-800 text-gray-400 hover:text-blue-600 transition-colors" title="QR Code">
                                                <span className="material-symbols-outlined text-lg">qr_code_2</span>
                                            </button>
                                        )}
                                    </div>
                                </td>
                            </tr>
                        ))}
                    </tbody>
                </table>
            </div>
        </div>
    )
}

/* ═══════════════════════════════════════════ */
/*  QR MODAL                                  */
/* ═══════════════════════════════════════════ */

function QRModal({ data, onClose }) {
    const [qrData, setQrData] = useState(null)
    const [loading, setLoading] = useState(true)
    const [error, setError] = useState(null)
    const [countdown, setCountdown] = useState(60)
    const [refreshing, setRefreshing] = useState(false)
    const canvasRef = useRef(null)
    const countdownRef = useRef(null)
    const intervalSeconds = useRef(60)

    const fetchDynamicQr = async () => {
        try {
            const res = await fetch(`/manager/api/qrcode/dynamic/${data.activityId}`, { credentials: 'include' })
            if (!res.ok) throw new Error('Không thể tải mã QR')
            const d = await res.json()
            setQrData(d)
            intervalSeconds.current = d.interval || 60
            setCountdown(d.secondsRemaining || d.interval || 60)
            setError(null)
            setRefreshing(true)
            setTimeout(() => setRefreshing(false), 600)
        } catch (err) {
            setError(err.message)
        } finally {
            setLoading(false)
        }
    }

    useEffect(() => {
        fetchDynamicQr()
        return () => clearInterval(countdownRef.current)
    }, [data.activityId])

    useEffect(() => {
        clearInterval(countdownRef.current)
        countdownRef.current = setInterval(() => {
            setCountdown(prev => {
                if (prev <= 1) { fetchDynamicQr(); return intervalSeconds.current }
                return prev - 1
            })
        }, 1000)
        return () => clearInterval(countdownRef.current)
    }, [qrData?.token])

    useEffect(() => {
        if (!qrData?.checkinUrl || !canvasRef.current) return
        import('qrcode').then(QRCode => {
            QRCode.toCanvas(canvasRef.current, qrData.checkinUrl, {
                width: 260, margin: 2,
                color: { dark: '#000000', light: '#ffffff' },
                errorCorrectionLevel: 'H',
            })
        }).catch(() => setError('Không thể render QR'))
    }, [qrData?.checkinUrl])

    const interval = intervalSeconds.current
    const countdownPercent = (countdown / interval) * 100
    const countdownMm = Math.floor(countdown / 60)
    const countdownSs = countdown % 60
    const isWarning = countdown <= 10

    return (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-sm" onClick={onClose}>
            <div className="bg-white dark:bg-gray-900 rounded-2xl border border-gray-100 dark:border-gray-800 shadow-2xl p-6 max-w-sm w-full mx-4 animate-in" onClick={e => e.stopPropagation()}>
                {/* Header */}
                <div className="flex items-center justify-between mb-4">
                    <div className="flex items-center gap-2">
                        <div className="size-9 rounded-xl bg-blue-500/10 flex items-center justify-center">
                            <span className="material-symbols-outlined text-blue-500">qr_code_2</span>
                        </div>
                        <div>
                            <h3 className="font-bold text-gray-900 dark:text-white">QR Check-in động</h3>
                            <p className="text-[10px] text-emerald-500 font-medium">Tự đổi mỗi {interval >= 60 ? `${interval / 60} phút` : `${interval} giây`}</p>
                        </div>
                    </div>
                    <button onClick={onClose} className="size-8 flex items-center justify-center rounded-lg hover:bg-gray-100 dark:hover:bg-gray-800 text-gray-400 hover:text-gray-600 transition-colors">
                        <span className="material-symbols-outlined text-lg">close</span>
                    </button>
                </div>
                <p className="text-sm text-gray-500 dark:text-gray-400 mb-4 text-center font-medium">{data.activityName}</p>
                {/* QR */}
                <div className="flex items-center justify-center p-4 bg-white rounded-xl border border-gray-200 dark:border-gray-700 min-h-[250px]">
                    {loading ? (
                        <div className="w-8 h-8 border-3 border-blue-500/30 border-t-blue-500 rounded-full animate-spin" />
                    ) : error ? (
                        <div className="text-center">
                            <span className="material-symbols-outlined text-3xl text-red-400 block mb-2">error</span>
                            <p className="text-sm text-red-500">{error}</p>
                        </div>
                    ) : (
                        <div className={`p-2 bg-white rounded-xl transition-all duration-500 ${
                            refreshing ? 'ring-2 ring-emerald-500 shadow-lg shadow-emerald-500/20 scale-[1.02]' : ''
                        }`}>
                            <canvas ref={canvasRef} className="w-56 h-56 object-contain" />
                        </div>
                    )}
                </div>
                {/* Countdown */}
                {qrData && (
                    <div className="mt-4">
                        <div className="flex items-center justify-between mb-1.5">
                            <span className="text-[10px] font-medium text-gray-400 flex items-center gap-1">
                                <span className={`material-symbols-outlined text-xs ${isWarning ? 'text-red-500 animate-pulse' : 'text-emerald-500'}`}>timer</span>
                                Mã QR mới sau
                            </span>
                            <span className={`text-sm font-bold tabular-nums ${isWarning ? 'text-red-500' : 'text-emerald-600 dark:text-emerald-400'}`}>
                                {countdownMm > 0 ? `${countdownMm}:${String(countdownSs).padStart(2, '0')}` : `${countdownSs}s`}
                            </span>
                        </div>
                        <div className="h-1.5 bg-gray-100 dark:bg-gray-800 rounded-full overflow-hidden">
                            <div
                                className={`h-full rounded-full transition-all duration-1000 ease-linear ${isWarning ? 'bg-red-500' : 'bg-gradient-to-r from-emerald-500 to-teal-500'}`}
                                style={{ width: `${countdownPercent}%` }}
                            />
                        </div>
                        <div className="flex items-center gap-1.5 mt-3 justify-center">
                            <span className="material-symbols-outlined text-sm text-amber-500">shield</span>
                            <p className="text-[10px] text-amber-600 dark:text-amber-400 font-medium">QR chống gian lận — không thể screenshot gửi từ xa</p>
                        </div>
                    </div>
                )}
            </div>
        </div>
    )
}
