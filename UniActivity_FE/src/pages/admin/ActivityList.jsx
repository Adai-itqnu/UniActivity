import { useState, useEffect, useRef } from 'react'
import { useOutletContext, useSearchParams } from 'react-router-dom'
import ActivityWizard from './ActivityWizard'

const API = '/admin/activities/api'

const statusConfig = {
    OPEN: { label: 'Đang mở', bg: 'bg-green-100 dark:bg-green-900/40 text-green-600 dark:text-green-400', dot: 'bg-green-500' },
    DRAFT: { label: 'Bản nháp', bg: 'bg-gray-100 dark:bg-gray-800 text-gray-500 dark:text-gray-400', dot: 'bg-gray-400' },
    FINISHED: { label: 'Đã kết thúc', bg: 'bg-blue-100 dark:bg-blue-900/40 text-blue-600 dark:text-blue-400', dot: 'bg-blue-500' },
    CANCELLED: { label: 'Đã hủy', bg: 'bg-red-100 dark:bg-red-900/40 text-red-600 dark:text-red-400', dot: 'bg-red-500' },
}
const scopeLabels = { SCHOOL: 'Toàn trường', FACULTY: 'Khoa' }

function formatDT(s) {
    if (!s) return '—'
    const d = new Date(s)
    const p = n => String(n).padStart(2, '0')
    return `${p(d.getDate())}/${p(d.getMonth() + 1)}/${d.getFullYear()} ${p(d.getHours())}:${p(d.getMinutes())}`
}

export default function ActivityList() {
    const { currentUser } = useOutletContext()
    const [items, setItems] = useState([])
    const [semesters, setSemesters] = useState([])
    const [loading, setLoading] = useState(true)
    const [error, setError] = useState(null)
    const [urlSearchParams] = useSearchParams()
    const [search, setSearch] = useState(urlSearchParams.get('search') || '')
    const [filterStatus, setFilterStatus] = useState('ALL')
    const [showFilter, setShowFilter] = useState(false)
    const filterRef = useRef(null)
    const [viewMode, setViewMode] = useState('grid') // 'grid' | 'list'

    // Wizard modal
    const [wizardOpen, setWizardOpen] = useState(false)
    const [wizardActivity, setWizardActivity] = useState(null)

    // Delete
    const [deleteTarget, setDeleteTarget] = useState(null)
    const [deleteLoading, setDeleteLoading] = useState(false)

    useEffect(() => {
        const h = (e) => { if (filterRef.current && !filterRef.current.contains(e.target)) setShowFilter(false) }
        document.addEventListener('mousedown', h)
        return () => document.removeEventListener('mousedown', h)
    }, [])

    const fetchData = async () => {
        setLoading(true)
        try {
            const [aRes, sRes] = await Promise.all([
                fetch(API, { credentials: 'include' }),
                fetch('/admin/semesters/api', { credentials: 'include' }),
            ])
            if (!aRes.ok) throw new Error('Fetch failed')
            setItems(await aRes.json())
            if (sRes.ok) setSemesters(await sRes.json())
        } catch (e) { setError(e.message) }
        setLoading(false)
    }
    useEffect(() => { fetchData() }, [])

    const filtered = items.filter(a => {
        const ms = !search || a.name?.toLowerCase().includes(search.toLowerCase()) || a.location?.toLowerCase().includes(search.toLowerCase())
        const mf = filterStatus === 'ALL' || a.status === filterStatus
        return ms && mf
    })

    const openCreate = () => { setWizardActivity(null); setWizardOpen(true) }
    const openEdit = (a) => { setWizardActivity(a); setWizardOpen(true) }



    const handleDelete = async () => {
        if (!deleteTarget) return; setDeleteLoading(true)
        try {
            const res = await fetch(`${API}/${deleteTarget.id}`, { method: 'DELETE', credentials: 'include' })
            if (!res.ok) throw new Error('Xóa thất bại')
            setDeleteTarget(null); fetchData()
        } catch (err) { alert(err.message) } finally { setDeleteLoading(false) }
    }

    if (loading) return (<div className="flex items-center justify-center h-[60vh]"><div className="flex flex-col items-center gap-3"><div className="w-10 h-10 border-4 border-primary/30 border-t-primary rounded-full animate-spin" /><p className="text-sm text-gray-500 dark:text-gray-400">Đang tải dữ liệu...</p></div></div>)
    if (error) return (<div className="flex items-center justify-center h-[60vh]"><div className="text-center"><span className="material-symbols-outlined text-5xl text-red-400 mb-3 block">error</span><p className="text-gray-600 dark:text-gray-400">Lỗi: {error}</p><button onClick={fetchData} className="mt-3 px-4 py-2 bg-primary text-white rounded-lg text-sm">Thử lại</button></div></div>)

    return (
        <div className="space-y-6">
            {/* Header */}
            <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
                <div>
                    <h1 className="text-2xl font-bold text-gray-800 dark:text-white">Quản lý Hoạt động</h1>
                    <p className="text-sm text-gray-500 dark:text-gray-400 mt-1">Tổng cộng {items.length} hoạt động.</p>
                </div>
                <div className="flex items-center gap-3">
                    {/* View toggle */}
                    <div className="inline-flex rounded-xl border border-gray-200 dark:border-gray-700 overflow-hidden">
                        <button onClick={() => setViewMode('grid')} className={`px-3 py-2 text-sm ${viewMode === 'grid' ? 'bg-primary text-white' : 'bg-white dark:bg-gray-800 text-gray-500'}`} title="Dạng thẻ"><span className="material-symbols-outlined text-lg">grid_view</span></button>
                        <button onClick={() => setViewMode('list')} className={`px-3 py-2 text-sm ${viewMode === 'list' ? 'bg-primary text-white' : 'bg-white dark:bg-gray-800 text-gray-500'}`} title="Dạng bảng"><span className="material-symbols-outlined text-lg">view_list</span></button>
                    </div>
                    <div className="relative" ref={filterRef}>
                        <button onClick={() => setShowFilter(!showFilter)} className="inline-flex items-center gap-2 px-4 py-2.5 rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 text-sm font-medium text-gray-700 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-700 transition-colors">
                            <span className="material-symbols-outlined text-lg">filter_list</span>Bộ lọc
                            {filterStatus !== 'ALL' && <span className="w-2 h-2 rounded-full bg-primary" />}
                        </button>
                        {showFilter && (
                            <div className="absolute right-0 mt-2 w-48 bg-white dark:bg-gray-800 rounded-xl shadow-xl border border-gray-200 dark:border-gray-700 overflow-hidden z-20">
                                <div className="py-1">
                                    {['ALL', 'OPEN', 'DRAFT', 'FINISHED', 'CANCELLED'].map(s => (
                                        <button key={s} onClick={() => { setFilterStatus(s); setShowFilter(false) }}
                                            className={`flex items-center gap-2 w-full px-4 py-2.5 text-sm transition-colors ${filterStatus === s ? 'bg-primary/10 text-primary font-semibold' : 'text-gray-700 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-700/50'}`}>
                                            {s === 'ALL' ? 'Tất cả' : statusConfig[s]?.label}
                                        </button>
                                    ))}
                                </div>
                            </div>
                        )}
                    </div>
                    <button onClick={openCreate} className="inline-flex items-center gap-2 px-4 py-2.5 rounded-xl bg-primary hover:bg-primary-dark text-white text-sm font-medium shadow-md shadow-primary/25 transition-colors">
                        <span className="material-symbols-outlined text-lg">add</span>Tạo hoạt động
                    </button>
                </div>
            </div>

            {/* Search */}
            <div className="max-w-md">
                <div className="relative">
                    <span className="material-symbols-outlined absolute left-3 top-1/2 -translate-y-1/2 text-gray-400 text-xl">search</span>
                    <input type="text" value={search} onChange={e => setSearch(e.target.value)} placeholder="Tìm kiếm hoạt động..."
                        className="w-full h-10 pl-10 pr-4 rounded-xl border border-gray-200 dark:border-gray-600 bg-white dark:bg-gray-800 text-sm text-gray-700 dark:text-gray-200 placeholder-gray-400 focus:outline-none focus:ring-2 focus:ring-primary/40 transition-colors" />
                </div>
            </div>

            {/* Stats */}
            <div className="grid grid-cols-2 sm:grid-cols-4 gap-5">
                {[
                    { label: 'Tổng hoạt động', value: items.length, icon: 'event', iconBg: 'bg-indigo-100 dark:bg-indigo-900/40 text-indigo-600 dark:text-indigo-400' },
                    { label: 'Đang mở', value: items.filter(a => a.status === 'OPEN').length, icon: 'event_available', iconBg: 'bg-green-100 dark:bg-green-900/40 text-green-600 dark:text-green-400' },
                    { label: 'Bản nháp', value: items.filter(a => a.status === 'DRAFT').length, icon: 'edit_note', iconBg: 'bg-orange-100 dark:bg-orange-900/40 text-orange-600 dark:text-orange-400' },
                    { label: 'Đã kết thúc', value: items.filter(a => a.status === 'FINISHED').length, icon: 'check_circle', iconBg: 'bg-blue-100 dark:bg-blue-900/40 text-blue-600 dark:text-blue-400' },
                ].map((c, i) => (
                    <div key={i} className="bg-white dark:bg-gray-900 rounded-2xl p-5 border border-gray-100 dark:border-gray-800 hover:shadow-lg transition-all duration-300">
                        <div className={`flex items-center justify-center w-11 h-11 rounded-xl ${c.iconBg}`}><span className="material-symbols-outlined text-2xl">{c.icon}</span></div>
                        <p className="mt-3 text-sm text-gray-500 dark:text-gray-400">{c.label}</p>
                        <p className="mt-1 text-3xl font-bold text-gray-800 dark:text-white">{c.value}</p>
                    </div>
                ))}
            </div>

            {/* ═══ GRID VIEW ═══ */}
            {viewMode === 'grid' ? (
                <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-5">
                    {filtered.length > 0 ? filtered.map(a => {
                        const st = statusConfig[a.status] || statusConfig.DRAFT
                        return (
                            <div key={a.id} className="bg-white dark:bg-gray-900 rounded-2xl border border-gray-100 dark:border-gray-800 overflow-hidden hover:shadow-xl hover:shadow-gray-200/60 dark:hover:shadow-gray-900/60 transition-all duration-300 group">
                                {/* Banner image */}
                                <div className="relative h-44 bg-gradient-to-br from-indigo-100 to-blue-50 dark:from-gray-800 dark:to-gray-700 overflow-hidden">
                                    {a.bannerUrl ? (
                                        <img src={a.bannerUrl} alt={a.name} className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-500" />
                                    ) : (
                                        <div className="flex items-center justify-center h-full">
                                            <span className="material-symbols-outlined text-5xl text-indigo-300 dark:text-gray-600">image</span>
                                        </div>
                                    )}
                                    {/* Status badge */}
                                    <div className="absolute top-3 left-3">
                                        <span className={`inline-flex items-center gap-1 px-2.5 py-1 rounded-lg text-xs font-semibold backdrop-blur-sm bg-white/80 dark:bg-gray-900/80 ${st.bg}`}>
                                            <span className={`w-1.5 h-1.5 rounded-full ${st.dot}`} />{st.label}
                                        </span>
                                    </div>
                                    {/* Scope badge */}
                                    <div className="absolute top-3 right-3">
                                        <span className="inline-flex items-center px-2 py-0.5 rounded-md text-[10px] font-semibold backdrop-blur-sm bg-white/80 dark:bg-gray-900/80 text-gray-600 dark:text-gray-300">
                                            {scopeLabels[a.scope] || a.scope}
                                        </span>
                                    </div>
                                    {/* Actions overlay */}
                                    <div className="absolute bottom-3 right-3 flex gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
                                        <button onClick={() => openEdit(a)} className="flex items-center justify-center w-8 h-8 rounded-lg bg-white/90 dark:bg-gray-900/90 text-blue-600 hover:bg-blue-50 shadow-sm transition-colors" title="Sửa">
                                            <span className="material-symbols-outlined text-lg">edit</span>
                                        </button>
                                        <button onClick={() => setDeleteTarget(a)} className="flex items-center justify-center w-8 h-8 rounded-lg bg-white/90 dark:bg-gray-900/90 text-red-500 hover:bg-red-50 shadow-sm transition-colors" title="Xóa">
                                            <span className="material-symbols-outlined text-lg">delete</span>
                                        </button>
                                    </div>
                                </div>
                                {/* Content */}
                                <div className="p-4">
                                    <h3 className="font-semibold text-gray-800 dark:text-white text-sm leading-snug line-clamp-2 min-h-[2.5rem]">{a.name}</h3>
                                    <p className="text-xs text-gray-400 dark:text-gray-500 mt-1 line-clamp-2">{a.description || 'Không có mô tả'}</p>
                                    <div className="mt-3 space-y-1.5">
                                        {a.location && (
                                            <div className="flex items-center gap-1.5 text-xs text-gray-500 dark:text-gray-400">
                                                <span className="material-symbols-outlined text-sm">location_on</span>{a.location}
                                            </div>
                                        )}
                                        <div className="flex items-center gap-1.5 text-xs text-gray-500 dark:text-gray-400">
                                            <span className="material-symbols-outlined text-sm">schedule</span>{formatDT(a.startTime)}
                                        </div>
                                        {a.semesterName && (
                                            <div className="flex items-center gap-1.5 text-xs text-gray-500 dark:text-gray-400">
                                                <span className="material-symbols-outlined text-sm">school</span>{a.semesterName}
                                            </div>
                                        )}
                                    </div>
                                    {/* Footer stats */}
                                    <div className="mt-3 pt-3 border-t border-gray-100 dark:border-gray-800 flex items-center justify-between">
                                        <div className="flex items-center gap-3 text-xs text-gray-400">
                                            <span className="flex items-center gap-1"><span className="material-symbols-outlined text-sm">group</span>{a.registeredCount ?? 0}/{a.maxSlots ?? 0}</span>
                                            <span className="flex items-center gap-1"><span className="material-symbols-outlined text-sm">fact_check</span>{a.checkedInCount ?? 0}</span>
                                        </div>
                                    </div>
                                </div>
                            </div>
                        )
                    }) : (
                        <div className="col-span-full flex flex-col items-center justify-center py-16"><span className="material-symbols-outlined text-5xl text-gray-300 dark:text-gray-600 mb-3">search_off</span><p className="text-gray-400">Không tìm thấy hoạt động phù hợp</p></div>
                    )}
                </div>
            ) : (
                /* ═══ LIST/TABLE VIEW ═══ */
                <div className="bg-white dark:bg-gray-900 rounded-2xl border border-gray-100 dark:border-gray-800 overflow-hidden">
                    <div className="overflow-x-auto">
                        <table className="w-full text-sm">
                            <thead>
                                <tr className="border-b border-gray-100 dark:border-gray-800">
                                    <th className="text-left px-6 py-4 text-xs font-semibold uppercase tracking-wider text-gray-500 dark:text-gray-400">Hoạt động</th>
                                    <th className="text-left px-6 py-4 text-xs font-semibold uppercase tracking-wider text-gray-500 dark:text-gray-400">Thời gian</th>
                                    <th className="text-left px-6 py-4 text-xs font-semibold uppercase tracking-wider text-gray-500 dark:text-gray-400">Phạm vi</th>
                                    <th className="text-left px-6 py-4 text-xs font-semibold uppercase tracking-wider text-gray-500 dark:text-gray-400">Trạng thái</th>
                                    <th className="text-left px-6 py-4 text-xs font-semibold uppercase tracking-wider text-gray-500 dark:text-gray-400">Đăng ký</th>
                                    <th className="text-right px-6 py-4 text-xs font-semibold uppercase tracking-wider text-gray-500 dark:text-gray-400">Thao tác</th>
                                </tr>
                            </thead>
                            <tbody className="divide-y divide-gray-50 dark:divide-gray-800">
                                {filtered.length > 0 ? filtered.map(a => {
                                    const st = statusConfig[a.status] || statusConfig.DRAFT
                                    return (
                                        <tr key={a.id} className="hover:bg-gray-50 dark:hover:bg-gray-800/50 transition-colors group">
                                            <td className="px-6 py-3">
                                                <div className="flex items-center gap-3">
                                                    <div className="w-12 h-12 rounded-lg overflow-hidden shrink-0 bg-gray-100 dark:bg-gray-800">
                                                        {a.bannerUrl ? <img src={a.bannerUrl} alt="" className="w-full h-full object-cover" /> : <div className="flex items-center justify-center h-full"><span className="material-symbols-outlined text-gray-400 text-lg">image</span></div>}
                                                    </div>
                                                    <div className="min-w-0">
                                                        <p className="font-medium text-gray-800 dark:text-white truncate max-w-[200px]">{a.name}</p>
                                                        {a.location && <p className="text-xs text-gray-400 flex items-center gap-1 mt-0.5"><span className="material-symbols-outlined text-xs">location_on</span>{a.location}</p>}
                                                    </div>
                                                </div>
                                            </td>
                                            <td className="px-6 py-3 text-xs text-gray-500 dark:text-gray-400 whitespace-nowrap">{formatDT(a.startTime)}</td>
                                            <td className="px-6 py-3"><span className="text-xs text-gray-500">{scopeLabels[a.scope] || a.scope}</span></td>
                                            <td className="px-6 py-3"><span className={`inline-flex items-center gap-1 px-2.5 py-1 rounded-lg text-xs font-semibold ${st.bg}`}><span className={`w-1.5 h-1.5 rounded-full ${st.dot}`} />{st.label}</span></td>
                                            <td className="px-6 py-3 text-xs text-gray-500">{a.registeredCount ?? 0}/{a.maxSlots ?? 0}</td>
                                            <td className="px-6 py-3 text-right">
                                                <div className="inline-flex items-center gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
                                                    <button onClick={() => openEdit(a)} className="flex items-center justify-center w-8 h-8 rounded-lg text-gray-500 hover:bg-blue-50 hover:text-blue-600 dark:hover:bg-blue-900/30 dark:hover:text-blue-400 transition-colors"><span className="material-symbols-outlined text-lg">edit</span></button>
                                                    <button onClick={() => setDeleteTarget(a)} className="flex items-center justify-center w-8 h-8 rounded-lg text-gray-500 hover:bg-red-50 hover:text-red-600 dark:hover:bg-red-900/30 dark:hover:text-red-400 transition-colors"><span className="material-symbols-outlined text-lg">delete</span></button>
                                                </div>
                                            </td>
                                        </tr>
                                    )
                                }) : (
                                    <tr><td colSpan={6} className="px-6 py-12 text-center"><span className="material-symbols-outlined text-4xl text-gray-300 dark:text-gray-600 block mb-2">search_off</span><p className="text-gray-400">Không tìm thấy hoạt động phù hợp</p></td></tr>
                                )}
                            </tbody>
                        </table>
                    </div>
                    <div className="px-6 py-3 border-t border-gray-100 dark:border-gray-800"><p className="text-xs text-gray-400">Hiển thị {filtered.length} / {items.length}</p></div>
                </div>
            )}

            {/* ═══ Activity Wizard ═══ */}
            {wizardOpen && <ActivityWizard activity={wizardActivity} onClose={() => setWizardOpen(false)} onSaved={() => { setWizardOpen(false); fetchData() }} />}

            {/* Delete Modal */}
            {deleteTarget && (
                <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-sm" onClick={() => setDeleteTarget(null)}>
                    <div className="bg-white dark:bg-gray-900 rounded-2xl shadow-2xl w-full max-w-sm mx-4 overflow-hidden border border-gray-200 dark:border-gray-700" onClick={e => e.stopPropagation()}>
                        <div className="p-6 text-center">
                            <div className="mx-auto flex items-center justify-center w-14 h-14 rounded-full bg-red-100 dark:bg-red-900/40 text-red-500 mb-4"><span className="material-symbols-outlined text-3xl">delete_forever</span></div>
                            <h3 className="text-lg font-semibold text-gray-800 dark:text-white">Xóa hoạt động?</h3>
                            <p className="text-sm text-gray-500 dark:text-gray-400 mt-2">Bạn có chắc muốn xóa <strong className="text-gray-700 dark:text-gray-200">{deleteTarget.name}</strong>?</p>
                            <div className="flex items-center justify-center gap-3 mt-6">
                                <button onClick={() => setDeleteTarget(null)} className="px-4 py-2.5 rounded-xl text-sm font-medium text-gray-700 dark:text-gray-300 border border-gray-200 dark:border-gray-700 hover:bg-gray-50 dark:hover:bg-gray-800 transition-colors">Hủy</button>
                                <button onClick={handleDelete} disabled={deleteLoading} className="inline-flex items-center gap-2 px-5 py-2.5 rounded-xl bg-red-500 hover:bg-red-600 text-white text-sm font-medium shadow-md shadow-red-500/25 transition-colors disabled:opacity-50">
                                    {deleteLoading ? 'Đang xóa...' : 'Xóa'}
                                </button>
                            </div>
                        </div>
                    </div>
                </div>
            )}
        </div>
    )
}
