import { useState, useEffect, useRef } from 'react'
import { useOutletContext } from 'react-router-dom'

const statusConfig = {
    ACTIVE: { label: 'Hoạt động', bg: 'bg-green-100 dark:bg-green-900/40 text-green-600 dark:text-green-400' },
    INACTIVE: { label: 'Ngừng hoạt động', bg: 'bg-red-100 dark:bg-red-900/40 text-red-600 dark:text-red-400' },
}

const API = '/admin/academic-years/api'

export default function AcademicYearList() {
    const { currentUser } = useOutletContext()
    const [items, setItems] = useState([])
    const [loading, setLoading] = useState(true)
    const [error, setError] = useState(null)
    const [search, setSearch] = useState('')
    const [filterStatus, setFilterStatus] = useState('ALL')
    const [showFilter, setShowFilter] = useState(false)
    const filterRef = useRef(null)

    // Modal
    const [modalOpen, setModalOpen] = useState(false)
    const [modalMode, setModalMode] = useState('create')
    const [editing, setEditing] = useState(null)
    const [formData, setFormData] = useState({ code: '', startYear: '', endYear: '', status: 'ACTIVE' })
    const [formError, setFormError] = useState('')
    const [formLoading, setFormLoading] = useState(false)

    // Delete
    const [deleteTarget, setDeleteTarget] = useState(null)
    const [deleteLoading, setDeleteLoading] = useState(false)

    useEffect(() => {
        const handleClick = (e) => { if (filterRef.current && !filterRef.current.contains(e.target)) setShowFilter(false) }
        document.addEventListener('mousedown', handleClick)
        return () => document.removeEventListener('mousedown', handleClick)
    }, [])

    const fetchData = () => {
        setLoading(true)
        fetch(API, { credentials: 'include' })
            .then(r => { if (!r.ok) throw new Error('Fetch failed'); return r.json() })
            .then(d => { setItems(d); setLoading(false) })
            .catch(e => { setError(e.message); setLoading(false) })
    }
    useEffect(fetchData, [])

    const filtered = items.filter(f => {
        const matchSearch = !search || f.code?.toLowerCase().includes(search.toLowerCase())
        const matchStatus = filterStatus === 'ALL' || f.status === filterStatus
        return matchSearch && matchStatus
    })

    const openCreate = () => {
        setModalMode('create'); setEditing(null)
        setFormData({ code: '', startYear: new Date().getFullYear(), endYear: new Date().getFullYear() + 4, status: 'ACTIVE' })
        setFormError(''); setModalOpen(true)
    }
    const openEdit = (item) => {
        setModalMode('edit'); setEditing(item)
        setFormData({ code: item.code, startYear: item.startYear || '', endYear: item.endYear || '', status: item.status || 'ACTIVE' })
        setFormError(''); setModalOpen(true)
    }

    const handleSubmit = async (e) => {
        e.preventDefault(); setFormError('')
        if (!formData.code.trim()) { setFormError('Mã khóa không được để trống.'); return }
        setFormLoading(true)
        try {
            const url = modalMode === 'edit' ? `${API}/${editing.id}` : API
            const res = await fetch(url, {
                method: modalMode === 'edit' ? 'PUT' : 'POST',
                headers: { 'Content-Type': 'application/json' },
                credentials: 'include',
                body: JSON.stringify({ ...formData, startYear: Number(formData.startYear), endYear: Number(formData.endYear) }),
            })
            if (!res.ok) { const d = await res.json().catch(() => null); throw new Error(d?.message || 'Thao tác thất bại') }
            setModalOpen(false); fetchData()
        } catch (err) { setFormError(err.message) } finally { setFormLoading(false) }
    }

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
                    <h1 className="text-2xl font-bold text-gray-800 dark:text-white">Quản lý Khóa</h1>
                    <p className="text-sm text-gray-500 dark:text-gray-400 mt-1">Quản lý danh sách các khóa học. Tổng cộng {items.length} khóa.</p>
                </div>
                <div className="flex items-center gap-3">
                    <div className="relative" ref={filterRef}>
                        <button onClick={() => setShowFilter(!showFilter)} className="inline-flex items-center gap-2 px-4 py-2.5 rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 text-sm font-medium text-gray-700 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-700 transition-colors">
                            <span className="material-symbols-outlined text-lg">filter_list</span>Bộ lọc
                            {filterStatus !== 'ALL' && <span className="w-2 h-2 rounded-full bg-primary" />}
                        </button>
                        {showFilter && (
                            <div className="absolute right-0 mt-2 w-48 bg-white dark:bg-gray-800 rounded-xl shadow-xl border border-gray-200 dark:border-gray-700 overflow-hidden z-20">
                                <div className="py-1">
                                    {['ALL', 'ACTIVE', 'INACTIVE'].map(s => (
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
                        <span className="material-symbols-outlined text-lg">add</span>Thêm khóa mới
                    </button>
                </div>
            </div>

            {/* Search */}
            <div className="max-w-md">
                <div className="relative">
                    <span className="material-symbols-outlined absolute left-3 top-1/2 -translate-y-1/2 text-gray-400 text-xl">search</span>
                    <input type="text" value={search} onChange={e => setSearch(e.target.value)} placeholder="Tìm kiếm khóa theo mã..."
                        className="w-full h-10 pl-10 pr-4 rounded-xl border border-gray-200 dark:border-gray-600 bg-white dark:bg-gray-800 text-sm text-gray-700 dark:text-gray-200 placeholder-gray-400 focus:outline-none focus:ring-2 focus:ring-primary/40 transition-colors" />
                </div>
            </div>

            {/* Stats */}
            <div className="grid grid-cols-1 sm:grid-cols-3 gap-5">
                {[
                    { label: 'Tổng số khóa', value: items.length, icon: 'calendar_today', iconBg: 'bg-indigo-100 dark:bg-indigo-900/40 text-indigo-600 dark:text-indigo-400' },
                    { label: 'Đang hoạt động', value: items.filter(f => f.status === 'ACTIVE').length, icon: 'check_circle', iconBg: 'bg-green-100 dark:bg-green-900/40 text-green-600 dark:text-green-400' },
                    { label: 'Ngừng hoạt động', value: items.filter(f => f.status === 'INACTIVE').length, icon: 'cancel', iconBg: 'bg-red-100 dark:bg-red-900/40 text-red-600 dark:text-red-400' },
                ].map((card, i) => (
                    <div key={i} className="bg-white dark:bg-gray-900 rounded-2xl p-5 border border-gray-100 dark:border-gray-800 hover:shadow-lg transition-all duration-300">
                        <div className={`flex items-center justify-center w-11 h-11 rounded-xl ${card.iconBg}`}>
                            <span className="material-symbols-outlined text-2xl">{card.icon}</span>
                        </div>
                        <p className="mt-3 text-sm text-gray-500 dark:text-gray-400">{card.label}</p>
                        <p className="mt-1 text-3xl font-bold text-gray-800 dark:text-white">{card.value}</p>
                    </div>
                ))}
            </div>

            {/* Table */}
            <div className="bg-white dark:bg-gray-900 rounded-2xl border border-gray-100 dark:border-gray-800 overflow-hidden">
                <div className="overflow-x-auto">
                    <table className="w-full text-sm">
                        <thead>
                            <tr className="border-b border-gray-100 dark:border-gray-800">
                                <th className="text-left px-6 py-4 text-xs font-semibold uppercase tracking-wider text-gray-500 dark:text-gray-400">Mã</th>
                                <th className="text-left px-6 py-4 text-xs font-semibold uppercase tracking-wider text-gray-500 dark:text-gray-400">Năm bắt đầu</th>
                                <th className="text-left px-6 py-4 text-xs font-semibold uppercase tracking-wider text-gray-500 dark:text-gray-400">Năm kết thúc</th>
                                <th className="text-left px-6 py-4 text-xs font-semibold uppercase tracking-wider text-gray-500 dark:text-gray-400">Trạng thái</th>
                                <th className="text-right px-6 py-4 text-xs font-semibold uppercase tracking-wider text-gray-500 dark:text-gray-400">Thao tác</th>
                            </tr>
                        </thead>
                        <tbody className="divide-y divide-gray-50 dark:divide-gray-800">
                            {filtered.length > 0 ? filtered.map(item => {
                                const st = statusConfig[item.status] || statusConfig.ACTIVE
                                return (
                                    <tr key={item.id} className="hover:bg-gray-50 dark:hover:bg-gray-800/50 transition-colors group">
                                        <td className="px-6 py-4">
                                            <div className="flex items-center gap-3">
                                                <div className="flex items-center justify-center w-9 h-9 rounded-lg bg-indigo-100 dark:bg-indigo-900/40 text-indigo-600 dark:text-indigo-400 shrink-0">
                                                    <span className="material-symbols-outlined text-lg">calendar_today</span>
                                                </div>
                                                <span className="font-mono text-xs px-2 py-1 rounded bg-gray-100 dark:bg-gray-800 text-gray-700 dark:text-gray-300">{item.code}</span>
                                            </div>
                                        </td>
                                        <td className="px-6 py-4 text-gray-700 dark:text-gray-300">{item.startYear || '—'}</td>
                                        <td className="px-6 py-4 text-gray-700 dark:text-gray-300">{item.endYear || '—'}</td>
                                        <td className="px-6 py-4"><span className={`inline-flex items-center px-2.5 py-1 rounded-lg text-xs font-semibold ${st.bg}`}>{st.label}</span></td>
                                        <td className="px-6 py-4 text-right">
                                            <div className="inline-flex items-center gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
                                                <button onClick={() => openEdit(item)} className="flex items-center justify-center w-8 h-8 rounded-lg text-gray-500 hover:bg-blue-50 hover:text-blue-600 dark:hover:bg-blue-900/30 dark:hover:text-blue-400 transition-colors" title="Sửa"><span className="material-symbols-outlined text-lg">edit</span></button>
                                                <button onClick={() => setDeleteTarget(item)} className="flex items-center justify-center w-8 h-8 rounded-lg text-gray-500 hover:bg-red-50 hover:text-red-600 dark:hover:bg-red-900/30 dark:hover:text-red-400 transition-colors" title="Xóa"><span className="material-symbols-outlined text-lg">delete</span></button>
                                            </div>
                                        </td>
                                    </tr>
                                )
                            }) : (
                                <tr><td colSpan={5} className="px-6 py-12 text-center"><span className="material-symbols-outlined text-4xl text-gray-300 dark:text-gray-600 block mb-2">search_off</span><p className="text-gray-400">Không tìm thấy khóa phù hợp</p></td></tr>
                            )}
                        </tbody>
                    </table>
                </div>
                <div className="px-6 py-3 border-t border-gray-100 dark:border-gray-800"><p className="text-xs text-gray-400">Hiển thị {filtered.length} / {items.length} khóa</p></div>
            </div>

            {/* Modal */}
            {modalOpen && (
                <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-sm" onClick={() => setModalOpen(false)}>
                    <div className="bg-white dark:bg-gray-900 rounded-2xl shadow-2xl w-full max-w-md mx-4 overflow-hidden border border-gray-200 dark:border-gray-700" onClick={e => e.stopPropagation()}>
                        <div className="flex items-center justify-between px-6 py-4 border-b border-gray-100 dark:border-gray-800">
                            <h3 className="text-lg font-semibold text-gray-800 dark:text-white">{modalMode === 'edit' ? 'Chỉnh sửa khóa' : 'Thêm khóa mới'}</h3>
                            <button onClick={() => setModalOpen(false)} className="flex items-center justify-center w-8 h-8 rounded-lg text-gray-400 hover:bg-gray-100 dark:hover:bg-gray-800 transition-colors"><span className="material-symbols-outlined">close</span></button>
                        </div>
                        <form onSubmit={handleSubmit} className="p-6 space-y-4">
                            {formError && <div className="bg-red-500/10 border border-red-500/30 text-red-500 px-4 py-3 rounded-lg text-sm flex items-center gap-2"><span className="material-symbols-outlined text-lg">error</span>{formError}</div>}
                            <div className="space-y-1.5">
                                <label className="text-xs font-semibold uppercase tracking-wide text-gray-500 dark:text-gray-400">Mã khóa *</label>
                                <input type="text" value={formData.code} onChange={e => setFormData({ ...formData, code: e.target.value })} placeholder="VD: K45" required
                                    className="w-full h-10 px-3 rounded-lg border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 text-sm text-gray-800 dark:text-white focus:outline-none focus:ring-2 focus:ring-primary/30 transition-colors" />
                            </div>
                            <div className="grid grid-cols-2 gap-4">
                                <div className="space-y-1.5">
                                    <label className="text-xs font-semibold uppercase tracking-wide text-gray-500 dark:text-gray-400">Năm bắt đầu</label>
                                    <input type="number" value={formData.startYear} onChange={e => setFormData({ ...formData, startYear: e.target.value })} placeholder="2022"
                                        className="w-full h-10 px-3 rounded-lg border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 text-sm text-gray-800 dark:text-white focus:outline-none focus:ring-2 focus:ring-primary/30 transition-colors" />
                                </div>
                                <div className="space-y-1.5">
                                    <label className="text-xs font-semibold uppercase tracking-wide text-gray-500 dark:text-gray-400">Năm kết thúc</label>
                                    <input type="number" value={formData.endYear} onChange={e => setFormData({ ...formData, endYear: e.target.value })} placeholder="2026"
                                        className="w-full h-10 px-3 rounded-lg border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 text-sm text-gray-800 dark:text-white focus:outline-none focus:ring-2 focus:ring-primary/30 transition-colors" />
                                </div>
                            </div>
                            <div className="space-y-1.5">
                                <label className="text-xs font-semibold uppercase tracking-wide text-gray-500 dark:text-gray-400">Trạng thái</label>
                                <select value={formData.status} onChange={e => setFormData({ ...formData, status: e.target.value })}
                                    className="w-full h-10 px-3 rounded-lg border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 text-sm text-gray-800 dark:text-white focus:outline-none focus:ring-2 focus:ring-primary/30 transition-colors">
                                    <option value="ACTIVE">Hoạt động</option>
                                    <option value="INACTIVE">Ngừng hoạt động</option>
                                </select>
                            </div>
                            <div className="flex items-center justify-end gap-3 pt-2">
                                <button type="button" onClick={() => setModalOpen(false)} className="px-4 py-2.5 rounded-xl text-sm font-medium text-gray-700 dark:text-gray-300 border border-gray-200 dark:border-gray-700 hover:bg-gray-50 dark:hover:bg-gray-800 transition-colors">Hủy</button>
                                <button type="submit" disabled={formLoading} className="inline-flex items-center gap-2 px-5 py-2.5 rounded-xl bg-primary hover:bg-primary-dark text-white text-sm font-medium shadow-md shadow-primary/25 transition-colors disabled:opacity-50">
                                    {formLoading ? <span className="flex items-center gap-2"><svg className="animate-spin h-4 w-4" viewBox="0 0 24 24"><circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" fill="none" /><path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" /></svg>Đang lưu...</span> : modalMode === 'edit' ? 'Cập nhật' : 'Tạo mới'}
                                </button>
                            </div>
                        </form>
                    </div>
                </div>
            )}

            {/* Delete Modal */}
            {deleteTarget && (
                <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-sm" onClick={() => setDeleteTarget(null)}>
                    <div className="bg-white dark:bg-gray-900 rounded-2xl shadow-2xl w-full max-w-sm mx-4 overflow-hidden border border-gray-200 dark:border-gray-700" onClick={e => e.stopPropagation()}>
                        <div className="p-6 text-center">
                            <div className="mx-auto flex items-center justify-center w-14 h-14 rounded-full bg-red-100 dark:bg-red-900/40 text-red-500 mb-4"><span className="material-symbols-outlined text-3xl">delete_forever</span></div>
                            <h3 className="text-lg font-semibold text-gray-800 dark:text-white">Xóa khóa?</h3>
                            <p className="text-sm text-gray-500 dark:text-gray-400 mt-2">Bạn có chắc muốn xóa khóa <strong className="text-gray-700 dark:text-gray-200">{deleteTarget.code}</strong>?</p>
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
