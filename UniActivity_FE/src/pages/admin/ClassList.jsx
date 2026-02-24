import { useState, useEffect, useRef } from 'react'
import { useOutletContext } from 'react-router-dom'

const API = '/admin/classes/api'

export default function ClassList() {
    const { currentUser } = useOutletContext()
    const [items, setItems] = useState([])
    const [faculties, setFaculties] = useState([])
    const [academicYears, setAcademicYears] = useState([])
    const [loading, setLoading] = useState(true)
    const [error, setError] = useState(null)
    const [search, setSearch] = useState('')
    const [filterFaculty, setFilterFaculty] = useState('ALL')
    const [showFilter, setShowFilter] = useState(false)
    const filterRef = useRef(null)

    // Modal
    const [modalOpen, setModalOpen] = useState(false)
    const [modalMode, setModalMode] = useState('create')
    const [editing, setEditing] = useState(null)
    const [formData, setFormData] = useState({ code: '', name: '', facultyId: '', academicYearId: '' })
    const [formError, setFormError] = useState('')
    const [formLoading, setFormLoading] = useState(false)

    // Delete
    const [deleteTarget, setDeleteTarget] = useState(null)
    const [deleteLoading, setDeleteLoading] = useState(false)

    // QR Code modal
    const [qrTarget, setQrTarget] = useState(null)

    useEffect(() => {
        const handleClick = (e) => { if (filterRef.current && !filterRef.current.contains(e.target)) setShowFilter(false) }
        document.addEventListener('mousedown', handleClick)
        return () => document.removeEventListener('mousedown', handleClick)
    }, [])

    const fetchData = async () => {
        setLoading(true)
        try {
            const [classRes, facRes, ayRes] = await Promise.all([
                fetch(API, { credentials: 'include' }),
                fetch('/admin/faculties/api/active', { credentials: 'include' }),
                fetch('/admin/academic-years/api/active', { credentials: 'include' }),
            ])
            if (!classRes.ok) throw new Error('Fetch failed')
            setItems(await classRes.json())
            if (facRes.ok) setFaculties(await facRes.json())
            if (ayRes.ok) setAcademicYears(await ayRes.json())
        } catch (e) { setError(e.message) }
        setLoading(false)
    }
    useEffect(() => { fetchData() }, [])

    const filtered = items.filter(f => {
        const matchSearch = !search || f.name?.toLowerCase().includes(search.toLowerCase()) || f.code?.toLowerCase().includes(search.toLowerCase())
        const matchFaculty = filterFaculty === 'ALL' || String(f.facultyId) === filterFaculty
        return matchSearch && matchFaculty
    })

    const openCreate = () => {
        setModalMode('create'); setEditing(null)
        setFormData({ code: '', name: '', facultyId: faculties[0]?.id || '', academicYearId: academicYears[0]?.id || '' })
        setFormError(''); setModalOpen(true)
    }
    const openEdit = (item) => {
        setModalMode('edit'); setEditing(item)
        setFormData({ code: item.code, name: item.name, facultyId: item.facultyId || '', academicYearId: item.academicYearId || '' })
        setFormError(''); setModalOpen(true)
    }

    const handleSubmit = async (e) => {
        e.preventDefault(); setFormError('')
        if (!formData.code.trim() || !formData.name.trim()) { setFormError('Mã lớp và tên lớp không được để trống.'); return }
        setFormLoading(true)
        try {
            const url = modalMode === 'edit' ? `${API}/${editing.id}` : API
            const res = await fetch(url, {
                method: modalMode === 'edit' ? 'PUT' : 'POST',
                headers: { 'Content-Type': 'application/json' },
                credentials: 'include',
                body: JSON.stringify({
                    ...formData,
                    facultyId: formData.facultyId ? Number(formData.facultyId) : null,
                    academicYearId: formData.academicYearId ? Number(formData.academicYearId) : null,
                }),
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

    const handleRegenerateCode = async (id) => {
        try {
            const res = await fetch(`${API}/${id}/regenerate-code`, { method: 'POST', credentials: 'include' })
            if (!res.ok) throw new Error('Tạo mã mới thất bại')
            fetchData()
        } catch (err) { alert(err.message) }
    }

    if (loading) return (<div className="flex items-center justify-center h-[60vh]"><div className="flex flex-col items-center gap-3"><div className="w-10 h-10 border-4 border-primary/30 border-t-primary rounded-full animate-spin" /><p className="text-sm text-gray-500 dark:text-gray-400">Đang tải dữ liệu...</p></div></div>)
    if (error) return (<div className="flex items-center justify-center h-[60vh]"><div className="text-center"><span className="material-symbols-outlined text-5xl text-red-400 mb-3 block">error</span><p className="text-gray-600 dark:text-gray-400">Lỗi: {error}</p><button onClick={fetchData} className="mt-3 px-4 py-2 bg-primary text-white rounded-lg text-sm">Thử lại</button></div></div>)

    return (
        <div className="space-y-6">
            {/* Header */}
            <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
                <div>
                    <h1 className="text-2xl font-bold text-gray-800 dark:text-white">Quản lý Lớp</h1>
                    <p className="text-sm text-gray-500 dark:text-gray-400 mt-1">Quản lý danh sách các lớp học. Tổng cộng {items.length} lớp.</p>
                </div>
                <div className="flex items-center gap-3">
                    <div className="relative" ref={filterRef}>
                        <button onClick={() => setShowFilter(!showFilter)} className="inline-flex items-center gap-2 px-4 py-2.5 rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 text-sm font-medium text-gray-700 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-700 transition-colors">
                            <span className="material-symbols-outlined text-lg">filter_list</span>Lọc khoa
                            {filterFaculty !== 'ALL' && <span className="w-2 h-2 rounded-full bg-primary" />}
                        </button>
                        {showFilter && (
                            <div className="absolute right-0 mt-2 w-56 bg-white dark:bg-gray-800 rounded-xl shadow-xl border border-gray-200 dark:border-gray-700 overflow-hidden z-20 max-h-60 overflow-y-auto">
                                <div className="py-1">
                                    <button onClick={() => { setFilterFaculty('ALL'); setShowFilter(false) }}
                                        className={`flex items-center gap-2 w-full px-4 py-2.5 text-sm transition-colors ${filterFaculty === 'ALL' ? 'bg-primary/10 text-primary font-semibold' : 'text-gray-700 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-700/50'}`}>
                                        Tất cả khoa
                                    </button>
                                    {faculties.map(f => (
                                        <button key={f.id} onClick={() => { setFilterFaculty(String(f.id)); setShowFilter(false) }}
                                            className={`flex items-center gap-2 w-full px-4 py-2.5 text-sm transition-colors ${filterFaculty === String(f.id) ? 'bg-primary/10 text-primary font-semibold' : 'text-gray-700 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-700/50'}`}>
                                            {f.name}
                                        </button>
                                    ))}
                                </div>
                            </div>
                        )}
                    </div>
                    <button onClick={openCreate} className="inline-flex items-center gap-2 px-4 py-2.5 rounded-xl bg-primary hover:bg-primary-dark text-white text-sm font-medium shadow-md shadow-primary/25 transition-colors">
                        <span className="material-symbols-outlined text-lg">add</span>Thêm lớp mới
                    </button>
                </div>
            </div>

            {/* Search */}
            <div className="max-w-md">
                <div className="relative">
                    <span className="material-symbols-outlined absolute left-3 top-1/2 -translate-y-1/2 text-gray-400 text-xl">search</span>
                    <input type="text" value={search} onChange={e => setSearch(e.target.value)} placeholder="Tìm kiếm lớp theo tên hoặc mã..."
                        className="w-full h-10 pl-10 pr-4 rounded-xl border border-gray-200 dark:border-gray-600 bg-white dark:bg-gray-800 text-sm text-gray-700 dark:text-gray-200 placeholder-gray-400 focus:outline-none focus:ring-2 focus:ring-primary/40 transition-colors" />
                </div>
            </div>

            {/* Stats */}
            <div className="grid grid-cols-1 sm:grid-cols-3 gap-5">
                {[
                    { label: 'Tổng số lớp', value: items.length, icon: 'school', iconBg: 'bg-indigo-100 dark:bg-indigo-900/40 text-indigo-600 dark:text-indigo-400' },
                    { label: 'Số khoa', value: [...new Set(items.map(i => i.facultyId).filter(Boolean))].length, icon: 'account_balance', iconBg: 'bg-blue-100 dark:bg-blue-900/40 text-blue-600 dark:text-blue-400' },
                    { label: 'Có mã tham gia', value: items.filter(i => i.joinCode).length, icon: 'qr_code', iconBg: 'bg-green-100 dark:bg-green-900/40 text-green-600 dark:text-green-400' },
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
                                <th className="text-left px-6 py-4 text-xs font-semibold uppercase tracking-wider text-gray-500 dark:text-gray-400">Mã lớp</th>
                                <th className="text-left px-6 py-4 text-xs font-semibold uppercase tracking-wider text-gray-500 dark:text-gray-400">Tên lớp</th>
                                <th className="text-left px-6 py-4 text-xs font-semibold uppercase tracking-wider text-gray-500 dark:text-gray-400">Khoa</th>
                                <th className="text-left px-6 py-4 text-xs font-semibold uppercase tracking-wider text-gray-500 dark:text-gray-400">Khóa</th>
                                <th className="text-left px-6 py-4 text-xs font-semibold uppercase tracking-wider text-gray-500 dark:text-gray-400">Mã tham gia</th>
                                <th className="text-right px-6 py-4 text-xs font-semibold uppercase tracking-wider text-gray-500 dark:text-gray-400">Thao tác</th>
                            </tr>
                        </thead>
                        <tbody className="divide-y divide-gray-50 dark:divide-gray-800">
                            {filtered.length > 0 ? filtered.map(item => (
                                <tr key={item.id} className="hover:bg-gray-50 dark:hover:bg-gray-800/50 transition-colors group">
                                    <td className="px-6 py-4">
                                        <div className="flex items-center gap-3">
                                            <div className="flex items-center justify-center w-9 h-9 rounded-lg bg-blue-100 dark:bg-blue-900/40 text-blue-600 dark:text-blue-400 shrink-0">
                                                <span className="material-symbols-outlined text-lg">school</span>
                                            </div>
                                            <span className="font-mono text-xs px-2 py-1 rounded bg-gray-100 dark:bg-gray-800 text-gray-700 dark:text-gray-300">{item.code}</span>
                                        </div>
                                    </td>
                                    <td className="px-6 py-4 font-medium text-gray-800 dark:text-white">{item.name}</td>
                                    <td className="px-6 py-4 text-gray-500 dark:text-gray-400">{item.facultyName || '—'}</td>
                                    <td className="px-6 py-4 text-gray-500 dark:text-gray-400">{item.academicYearCode || '—'}</td>
                                    <td className="px-6 py-4">
                                        {item.joinCode ? (
                                            <div className="flex items-center gap-2">
                                                <span className="font-mono text-xs px-2 py-1 rounded bg-green-100 dark:bg-green-900/40 text-green-700 dark:text-green-400">{item.joinCode}</span>
                                                <button onClick={() => setQrTarget(item)} className="text-gray-400 hover:text-primary transition-colors" title="Xem QR Code">
                                                    <span className="material-symbols-outlined text-lg">qr_code</span>
                                                </button>
                                            </div>
                                        ) : <span className="text-gray-300">—</span>}
                                    </td>
                                    <td className="px-6 py-4 text-right">
                                        <div className="inline-flex items-center gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
                                            <button onClick={() => handleRegenerateCode(item.id)} className="flex items-center justify-center w-8 h-8 rounded-lg text-gray-500 hover:bg-green-50 hover:text-green-600 dark:hover:bg-green-900/30 dark:hover:text-green-400 transition-colors" title="Tạo mã mới">
                                                <span className="material-symbols-outlined text-lg">refresh</span>
                                            </button>
                                            <button onClick={() => openEdit(item)} className="flex items-center justify-center w-8 h-8 rounded-lg text-gray-500 hover:bg-blue-50 hover:text-blue-600 dark:hover:bg-blue-900/30 dark:hover:text-blue-400 transition-colors" title="Sửa">
                                                <span className="material-symbols-outlined text-lg">edit</span>
                                            </button>
                                            <button onClick={() => setDeleteTarget(item)} className="flex items-center justify-center w-8 h-8 rounded-lg text-gray-500 hover:bg-red-50 hover:text-red-600 dark:hover:bg-red-900/30 dark:hover:text-red-400 transition-colors" title="Xóa">
                                                <span className="material-symbols-outlined text-lg">delete</span>
                                            </button>
                                        </div>
                                    </td>
                                </tr>
                            )) : (
                                <tr><td colSpan={6} className="px-6 py-12 text-center"><span className="material-symbols-outlined text-4xl text-gray-300 dark:text-gray-600 block mb-2">search_off</span><p className="text-gray-400">Không tìm thấy lớp phù hợp</p></td></tr>
                            )}
                        </tbody>
                    </table>
                </div>
                <div className="px-6 py-3 border-t border-gray-100 dark:border-gray-800"><p className="text-xs text-gray-400">Hiển thị {filtered.length} / {items.length} lớp</p></div>
            </div>

            {/* QR Code Modal */}
            {qrTarget && (
                <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-sm" onClick={() => setQrTarget(null)}>
                    <div className="bg-white dark:bg-gray-900 rounded-2xl shadow-2xl w-full max-w-sm mx-4 overflow-hidden border border-gray-200 dark:border-gray-700 p-6 text-center" onClick={e => e.stopPropagation()}>
                        <h3 className="text-lg font-semibold text-gray-800 dark:text-white mb-2">QR Code - {qrTarget.name}</h3>
                        <p className="text-sm text-gray-500 mb-4">Mã tham gia: <strong>{qrTarget.joinCode}</strong></p>
                        <img src={`${API}/${qrTarget.id}/qrcode`} alt="QR Code" className="w-48 h-48 mx-auto border rounded-lg" />
                        <div className="flex justify-center gap-3 mt-4">
                            <a href={`${API}/${qrTarget.id}/qrcode/download`} download className="inline-flex items-center gap-2 px-4 py-2 rounded-lg bg-primary text-white text-sm font-medium hover:bg-primary-dark transition-colors">
                                <span className="material-symbols-outlined text-lg">download</span>Tải về
                            </a>
                            <button onClick={() => setQrTarget(null)} className="px-4 py-2 rounded-lg text-sm font-medium text-gray-700 dark:text-gray-300 border border-gray-200 dark:border-gray-700 hover:bg-gray-50 dark:hover:bg-gray-800 transition-colors">Đóng</button>
                        </div>
                    </div>
                </div>
            )}

            {/* Create/Edit Modal */}
            {modalOpen && (
                <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-sm" onClick={() => setModalOpen(false)}>
                    <div className="bg-white dark:bg-gray-900 rounded-2xl shadow-2xl w-full max-w-md mx-4 overflow-hidden border border-gray-200 dark:border-gray-700" onClick={e => e.stopPropagation()}>
                        <div className="flex items-center justify-between px-6 py-4 border-b border-gray-100 dark:border-gray-800">
                            <h3 className="text-lg font-semibold text-gray-800 dark:text-white">{modalMode === 'edit' ? 'Chỉnh sửa lớp' : 'Thêm lớp mới'}</h3>
                            <button onClick={() => setModalOpen(false)} className="flex items-center justify-center w-8 h-8 rounded-lg text-gray-400 hover:bg-gray-100 dark:hover:bg-gray-800 transition-colors"><span className="material-symbols-outlined">close</span></button>
                        </div>
                        <form onSubmit={handleSubmit} className="p-6 space-y-4">
                            {formError && <div className="bg-red-500/10 border border-red-500/30 text-red-500 px-4 py-3 rounded-lg text-sm flex items-center gap-2"><span className="material-symbols-outlined text-lg">error</span>{formError}</div>}
                            <div className="grid grid-cols-2 gap-4">
                                <div className="space-y-1.5">
                                    <label className="text-xs font-semibold uppercase tracking-wide text-gray-500 dark:text-gray-400">Mã lớp *</label>
                                    <input type="text" value={formData.code} onChange={e => setFormData({ ...formData, code: e.target.value })} placeholder="VD: CNTT_K45A" required
                                        className="w-full h-10 px-3 rounded-lg border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 text-sm text-gray-800 dark:text-white focus:outline-none focus:ring-2 focus:ring-primary/30 transition-colors" />
                                </div>
                                <div className="space-y-1.5">
                                    <label className="text-xs font-semibold uppercase tracking-wide text-gray-500 dark:text-gray-400">Tên lớp *</label>
                                    <input type="text" value={formData.name} onChange={e => setFormData({ ...formData, name: e.target.value })} placeholder="VD: CNTT K45A" required
                                        className="w-full h-10 px-3 rounded-lg border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 text-sm text-gray-800 dark:text-white focus:outline-none focus:ring-2 focus:ring-primary/30 transition-colors" />
                                </div>
                            </div>
                            <div className="space-y-1.5">
                                <label className="text-xs font-semibold uppercase tracking-wide text-gray-500 dark:text-gray-400">Khoa</label>
                                <select value={formData.facultyId} onChange={e => setFormData({ ...formData, facultyId: e.target.value })}
                                    className="w-full h-10 px-3 rounded-lg border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 text-sm text-gray-800 dark:text-white focus:outline-none focus:ring-2 focus:ring-primary/30 transition-colors">
                                    <option value="">— Chọn khoa —</option>
                                    {faculties.map(f => <option key={f.id} value={f.id}>{f.name}</option>)}
                                </select>
                            </div>
                            <div className="space-y-1.5">
                                <label className="text-xs font-semibold uppercase tracking-wide text-gray-500 dark:text-gray-400">Khóa</label>
                                <select value={formData.academicYearId} onChange={e => setFormData({ ...formData, academicYearId: e.target.value })}
                                    className="w-full h-10 px-3 rounded-lg border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 text-sm text-gray-800 dark:text-white focus:outline-none focus:ring-2 focus:ring-primary/30 transition-colors">
                                    <option value="">— Chọn khóa —</option>
                                    {academicYears.map(a => <option key={a.id} value={a.id}>{a.code}</option>)}
                                </select>
                            </div>
                            <div className="flex items-center justify-end gap-3 pt-2">
                                <button type="button" onClick={() => setModalOpen(false)} className="px-4 py-2.5 rounded-xl text-sm font-medium text-gray-700 dark:text-gray-300 border border-gray-200 dark:border-gray-700 hover:bg-gray-50 dark:hover:bg-gray-800 transition-colors">Hủy</button>
                                <button type="submit" disabled={formLoading} className="inline-flex items-center gap-2 px-5 py-2.5 rounded-xl bg-primary hover:bg-primary-dark text-white text-sm font-medium shadow-md shadow-primary/25 transition-colors disabled:opacity-50">
                                    {formLoading ? 'Đang lưu...' : modalMode === 'edit' ? 'Cập nhật' : 'Tạo mới'}
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
                            <h3 className="text-lg font-semibold text-gray-800 dark:text-white">Xóa lớp?</h3>
                            <p className="text-sm text-gray-500 dark:text-gray-400 mt-2">Bạn có chắc muốn xóa lớp <strong className="text-gray-700 dark:text-gray-200">{deleteTarget.name}</strong>?</p>
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
