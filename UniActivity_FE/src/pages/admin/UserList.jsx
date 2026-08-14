import { useState, useEffect, useRef } from 'react'

const API = '/admin/users/api'

const roleConfig = {
    ADMIN: { label: 'Quản trị viên', bg: 'bg-purple-100 dark:bg-purple-900/40 text-purple-600 dark:text-purple-400', icon: 'admin_panel_settings' },
    MANAGER: { label: 'Quản lý', bg: 'bg-blue-100 dark:bg-blue-900/40 text-blue-600 dark:text-blue-400', icon: 'manage_accounts' },
    STUDENT: { label: 'Sinh viên', bg: 'bg-green-100 dark:bg-green-900/40 text-green-600 dark:text-green-400', icon: 'school' },
}
const statusConfig = {
    ACTIVE: { label: 'Hoạt động', bg: 'bg-green-100 dark:bg-green-900/40 text-green-600 dark:text-green-400' },
    INACTIVE: { label: 'Vô hiệu', bg: 'bg-red-100 dark:bg-red-900/40 text-red-600 dark:text-red-400' },
}

function timeAgo(d) {
    if (!d) return '—'
    const dt = new Date(d)
    const p = n => String(n).padStart(2, '0')
    return `${p(dt.getDate())}/${p(dt.getMonth() + 1)}/${dt.getFullYear()}`
}

export default function UserList() {
    const [items, setItems] = useState([])
    const [classes, setClasses] = useState([])
    const [loading, setLoading] = useState(true)
    const [error, setError] = useState(null)
    const [search, setSearch] = useState('')
    const [filterRole, setFilterRole] = useState('ALL')
    const [showFilter, setShowFilter] = useState(false)
    const filterRef = useRef(null)

    const [currentPage, setCurrentPage] = useState(1)
    const ITEMS_PER_PAGE = 50

    // Reset page to 1 when filters change
    useEffect(() => {
        setCurrentPage(1)
    }, [search, filterRole])

    // Modal
    const [modalOpen, setModalOpen] = useState(false)
    const [modalMode, setModalMode] = useState('create')
    const [editing, setEditing] = useState(null)
    const [formData, setFormData] = useState({ username: '', email: '', fullName: '', phone: '', password: '', role: 'STUDENT', classId: '' })
    const [formError, setFormError] = useState('')
    const [formLoading, setFormLoading] = useState(false)

    // Delete
    const [deleteTarget, setDeleteTarget] = useState(null)
    const [deleteLoading, setDeleteLoading] = useState(false)

    // Reset password
    const [resetTarget, setResetTarget] = useState(null)
    const [newPassword, setNewPassword] = useState('')
    const [resetLoading, setResetLoading] = useState(false)

    useEffect(() => {
        const h = (e) => { if (filterRef.current && !filterRef.current.contains(e.target)) setShowFilter(false) }
        document.addEventListener('mousedown', h)
        return () => document.removeEventListener('mousedown', h)
    }, [])

    const fetchData = async () => {
        setLoading(true)
        try {
            const [uRes, cRes] = await Promise.all([
                fetch(API, { credentials: 'include' }),
                fetch('/admin/classes/api', { credentials: 'include' }),
            ])
            if (!uRes.ok) throw new Error('Fetch failed')
            setItems(await uRes.json())
            if (cRes.ok) setClasses(await cRes.json())
        } catch (e) { setError(e.message) }
        setLoading(false)
    }
    useEffect(() => { fetchData() }, [])

    const filtered = items.filter(u => {
        const ms = !search || u.fullName?.toLowerCase().includes(search.toLowerCase()) || u.username?.toLowerCase().includes(search.toLowerCase()) || u.email?.toLowerCase().includes(search.toLowerCase())
        const mf = filterRole === 'ALL' || u.role === filterRole
        return ms && mf
    })

    const totalPages = Math.ceil(filtered.length / ITEMS_PER_PAGE)
    const paginated = filtered.slice((currentPage - 1) * ITEMS_PER_PAGE, currentPage * ITEMS_PER_PAGE)

    const openCreate = () => {
        setModalMode('create'); setEditing(null)
        setFormData({ username: '', email: '', fullName: '', phone: '', password: '', role: 'STUDENT', classId: '' })
        setFormError(''); setModalOpen(true)
    }
    const openEdit = (u) => {
        setModalMode('edit'); setEditing(u)
        setFormData({ username: u.username, email: u.email, fullName: u.fullName, phone: u.phone || '', password: '', role: u.role, classId: u.classId || '' })
        setFormError(''); setModalOpen(true)
    }

    const handleSubmit = async (e) => {
        e.preventDefault(); setFormError('')
        if (!formData.email.trim() || !formData.fullName.trim()) { setFormError('Email và họ tên không được để trống.'); return }
        if (modalMode === 'create' && formData.role === 'ADMIN' && !formData.username.trim()) { setFormError('Username ADMIN không được để trống.'); return }
        if (modalMode === 'create' && !formData.password) { setFormError('Mật khẩu không được để trống khi tạo mới.'); return }
        setFormLoading(true)
        try {
            const url = modalMode === 'edit' ? `${API}/${editing.id}` : API
            const body = { ...formData, classId: formData.classId ? Number(formData.classId) : null }
            if (modalMode === 'edit' && !body.password) delete body.password
            const res = await fetch(url, { method: modalMode === 'edit' ? 'PUT' : 'POST', headers: { 'Content-Type': 'application/json' }, credentials: 'include', body: JSON.stringify(body) })
            if (!res.ok) { const d = await res.json().catch(() => null); throw new Error(d?.message || d?.error || 'Thao tác thất bại') }
            setModalOpen(false); fetchData()
        } catch (err) { setFormError(err.message) } finally { setFormLoading(false) }
    }

    const handleToggleStatus = async (id) => {
        try {
            const res = await fetch(`${API}/${id}/toggle-status`, { method: 'POST', credentials: 'include' })
            if (!res.ok) throw new Error('Thay đổi trạng thái thất bại')
            fetchData()
        } catch (err) { alert(err.message) }
    }

    const handleResetPassword = async () => {
        if (!resetTarget || !newPassword || newPassword.length < 6) { alert('Mật khẩu phải có ít nhất 6 ký tự'); return }
        setResetLoading(true)
        try {
            const res = await fetch(`${API}/${resetTarget.id}/reset-password`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, credentials: 'include', body: JSON.stringify({ newPassword }) })
            if (!res.ok) throw new Error('Reset mật khẩu thất bại')
            setResetTarget(null); setNewPassword('')
            alert('Đã reset mật khẩu thành công!')
        } catch (err) { alert(err.message) } finally { setResetLoading(false) }
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
                    <h1 className="text-2xl font-bold text-gray-800 dark:text-white">Quản lý Người dùng</h1>
                    <p className="text-sm text-gray-500 dark:text-gray-400 mt-1">Tổng cộng {items.length} người dùng.</p>
                </div>
                <div className="flex items-center gap-3">
                    <div className="relative" ref={filterRef}>
                        <button onClick={() => setShowFilter(!showFilter)} className="inline-flex items-center gap-2 px-4 py-2.5 rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 text-sm font-medium text-gray-700 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-700 transition-colors">
                            <span className="material-symbols-outlined text-lg">filter_list</span>Vai trò
                            {filterRole !== 'ALL' && <span className="w-2 h-2 rounded-full bg-primary" />}
                        </button>
                        {showFilter && (
                            <div className="absolute right-0 mt-2 w-48 bg-white dark:bg-gray-800 rounded-xl shadow-xl border border-gray-200 dark:border-gray-700 overflow-hidden z-20">
                                <div className="py-1">
                                    {['ALL', 'ADMIN', 'MANAGER', 'STUDENT'].map(s => (
                                        <button key={s} onClick={() => { setFilterRole(s); setShowFilter(false) }}
                                            className={`flex items-center gap-2 w-full px-4 py-2.5 text-sm transition-colors ${filterRole === s ? 'bg-primary/10 text-primary font-semibold' : 'text-gray-700 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-700/50'}`}>
                                            {s === 'ALL' ? 'Tất cả' : roleConfig[s]?.label}
                                        </button>
                                    ))}
                                </div>
                            </div>
                        )}
                    </div>
                    <button onClick={openCreate} className="inline-flex items-center gap-2 px-4 py-2.5 rounded-xl bg-primary hover:bg-primary-dark text-white text-sm font-medium shadow-md shadow-primary/25 transition-colors">
                        <span className="material-symbols-outlined text-lg">person_add</span>Thêm người dùng
                    </button>
                </div>
            </div>

            {/* Search */}
            <div className="max-w-md">
                <div className="relative">
                    <span className="material-symbols-outlined absolute left-3 top-1/2 -translate-y-1/2 text-gray-400 text-xl">search</span>
                    <input type="text" value={search} onChange={e => setSearch(e.target.value)} placeholder="Tìm theo tên, mã tài khoản hoặc email..."
                        className="w-full h-10 pl-10 pr-4 rounded-xl border border-gray-200 dark:border-gray-600 bg-white dark:bg-gray-800 text-sm text-gray-700 dark:text-gray-200 placeholder-gray-400 focus:outline-none focus:ring-2 focus:ring-primary/40 transition-colors" />
                </div>
            </div>

            {/* Stats */}
            <div className="grid grid-cols-2 sm:grid-cols-4 gap-5">
                {[
                    { label: 'Tổng người dùng', value: items.length, icon: 'groups', iconBg: 'bg-indigo-100 dark:bg-indigo-900/40 text-indigo-600 dark:text-indigo-400' },
                    { label: 'Quản trị viên', value: items.filter(u => u.role === 'ADMIN').length, icon: 'admin_panel_settings', iconBg: 'bg-purple-100 dark:bg-purple-900/40 text-purple-600 dark:text-purple-400' },
                    { label: 'Quản lý', value: items.filter(u => u.role === 'MANAGER').length, icon: 'manage_accounts', iconBg: 'bg-blue-100 dark:bg-blue-900/40 text-blue-600 dark:text-blue-400' },
                    { label: 'Sinh viên', value: items.filter(u => u.role === 'STUDENT').length, icon: 'school', iconBg: 'bg-green-100 dark:bg-green-900/40 text-green-600 dark:text-green-400' },
                ].map((c, i) => (
                    <div key={i} className="bg-white dark:bg-gray-900 rounded-2xl p-5 border border-gray-100 dark:border-gray-800 hover:shadow-lg transition-all duration-300">
                        <div className={`flex items-center justify-center w-11 h-11 rounded-xl ${c.iconBg}`}><span className="material-symbols-outlined text-2xl">{c.icon}</span></div>
                        <p className="mt-3 text-sm text-gray-500 dark:text-gray-400">{c.label}</p>
                        <p className="mt-1 text-3xl font-bold text-gray-800 dark:text-white">{c.value}</p>
                    </div>
                ))}
            </div>

            {/* Table */}
            <div className="bg-white dark:bg-gray-900 rounded-2xl border border-gray-100 dark:border-gray-800 overflow-hidden">
                <div className="overflow-x-auto">
                    <table className="w-full text-sm">
                        <thead>
                            <tr className="border-b border-gray-100 dark:border-gray-800">
                                <th className="text-left px-6 py-4 text-xs font-semibold uppercase tracking-wider text-gray-500 dark:text-gray-400">Người dùng</th>
                                <th className="text-left px-6 py-4 text-xs font-semibold uppercase tracking-wider text-gray-500 dark:text-gray-400">Email</th>
                                <th className="text-left px-6 py-4 text-xs font-semibold uppercase tracking-wider text-gray-500 dark:text-gray-400">Vai trò</th>
                                <th className="text-left px-6 py-4 text-xs font-semibold uppercase tracking-wider text-gray-500 dark:text-gray-400">Lớp</th>
                                <th className="text-left px-6 py-4 text-xs font-semibold uppercase tracking-wider text-gray-500 dark:text-gray-400">Trạng thái</th>
                                <th className="text-left px-6 py-4 text-xs font-semibold uppercase tracking-wider text-gray-500 dark:text-gray-400">Ngày tạo</th>
                                <th className="text-right px-6 py-4 text-xs font-semibold uppercase tracking-wider text-gray-500 dark:text-gray-400">Thao tác</th>
                            </tr>
                        </thead>
                        <tbody className="divide-y divide-gray-50 dark:divide-gray-800">
                            {paginated.length > 0 ? paginated.map(u => {
                                const rc = roleConfig[u.role] || roleConfig.STUDENT
                                const sc = statusConfig[u.status] || statusConfig.ACTIVE
                                return (
                                    <tr key={u.id} className="hover:bg-gray-50 dark:hover:bg-gray-800/50 transition-colors group">
                                        <td className="px-6 py-3">
                                            <div className="flex items-center gap-3">
                                                <div className="w-9 h-9 rounded-full overflow-hidden shrink-0 bg-gray-100 dark:bg-gray-800 flex items-center justify-center">
                                                    {u.avatarUrl ? <img src={u.avatarUrl} alt="" className="w-full h-full object-cover" /> : <span className="material-symbols-outlined text-lg text-gray-400">{rc.icon}</span>}
                                                </div>
                                                <div className="min-w-0">
                                                    <p className="font-medium text-gray-800 dark:text-white truncate">{u.fullName}</p>
                                                    <p className="text-xs text-gray-400">{u.role === 'ADMIN' ? 'Username' : 'Mã tài khoản'}: {u.username}</p>
                                                </div>
                                            </div>
                                        </td>
                                        <td className="px-6 py-3 text-gray-500 dark:text-gray-400 text-xs">{u.email}</td>
                                        <td className="px-6 py-3"><span className={`inline-flex items-center px-2.5 py-1 rounded-lg text-xs font-semibold ${rc.bg}`}>{rc.label}</span></td>
                                        <td className="px-6 py-3 text-gray-500 dark:text-gray-400 text-xs">{u.className || '—'}</td>
                                        <td className="px-6 py-3">
                                            <button onClick={() => handleToggleStatus(u.id)} className={`inline-flex items-center gap-1 px-2.5 py-1 rounded-lg text-xs font-semibold cursor-pointer hover:opacity-80 transition-opacity ${sc.bg}`} title="Bấm để thay đổi trạng thái">
                                                <span className={`w-1.5 h-1.5 rounded-full ${u.status === 'ACTIVE' ? 'bg-green-500' : 'bg-red-500'}`} />{sc.label}
                                            </button>
                                        </td>
                                        <td className="px-6 py-3 text-gray-400 text-xs">{timeAgo(u.createdAt)}</td>
                                        <td className="px-6 py-3 text-right">
                                            <div className="inline-flex items-center gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
                                                <button onClick={() => { setResetTarget(u); setNewPassword('') }} className="flex items-center justify-center w-8 h-8 rounded-lg text-gray-500 hover:bg-orange-50 hover:text-orange-600 dark:hover:bg-orange-900/30 dark:hover:text-orange-400 transition-colors" title="Reset mật khẩu">
                                                    <span className="material-symbols-outlined text-lg">lock_reset</span>
                                                </button>
                                                <button onClick={() => openEdit(u)} className="flex items-center justify-center w-8 h-8 rounded-lg text-gray-500 hover:bg-blue-50 hover:text-blue-600 dark:hover:bg-blue-900/30 dark:hover:text-blue-400 transition-colors" title="Sửa">
                                                    <span className="material-symbols-outlined text-lg">edit</span>
                                                </button>
                                                <button onClick={() => setDeleteTarget(u)} className="flex items-center justify-center w-8 h-8 rounded-lg text-gray-500 hover:bg-red-50 hover:text-red-600 dark:hover:bg-red-900/30 dark:hover:text-red-400 transition-colors" title="Xóa">
                                                    <span className="material-symbols-outlined text-lg">delete</span>
                                                </button>
                                            </div>
                                        </td>
                                    </tr>
                                )
                            }) : (
                                <tr><td colSpan={7} className="px-6 py-12 text-center"><span className="material-symbols-outlined text-4xl text-gray-300 dark:text-gray-600 block mb-2">search_off</span><p className="text-gray-400">Không tìm thấy người dùng phù hợp</p></td></tr>
                            )}
                        </tbody>
                    </table>
                </div>
                {/* Pagination Controls */}
                <div className="px-6 py-4 border-t border-gray-100 dark:border-gray-800 flex flex-col sm:flex-row items-center justify-between gap-4 bg-gray-50/50 dark:bg-gray-800/30">
                    <p className="text-xs text-gray-500 dark:text-gray-400">
                        Hiển thị <span className="font-semibold text-gray-700 dark:text-gray-300">{filtered.length > 0 ? (currentPage - 1) * ITEMS_PER_PAGE + 1 : 0}</span> đến <span className="font-semibold text-gray-700 dark:text-gray-300">{Math.min(filtered.length, currentPage * ITEMS_PER_PAGE)}</span> trong <span className="font-semibold text-gray-700 dark:text-gray-300">{filtered.length}</span> người dùng
                    </p>
                    {totalPages > 1 && (
                        <div className="inline-flex items-center gap-1">
                            <button
                                type="button"
                                disabled={currentPage === 1}
                                onClick={() => setCurrentPage(1)}
                                className="p-1.5 rounded-lg border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 text-gray-500 hover:text-primary disabled:opacity-40 disabled:hover:text-gray-500 transition-colors flex items-center justify-center"
                                title="Trang đầu"
                            >
                                <span className="material-symbols-outlined text-sm">first_page</span>
                            </button>
                            <button
                                type="button"
                                disabled={currentPage === 1}
                                onClick={() => setCurrentPage(prev => Math.max(1, prev - 1))}
                                className="p-1.5 rounded-lg border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 text-gray-500 hover:text-primary disabled:opacity-40 disabled:hover:text-gray-500 transition-colors flex items-center justify-center"
                                title="Trang trước"
                            >
                                <span className="material-symbols-outlined text-sm">chevron_left</span>
                            </button>
                            <span className="text-xs font-semibold text-gray-700 dark:text-gray-300 px-3">
                                Trang {currentPage} / {totalPages}
                            </span>
                            <button
                                type="button"
                                disabled={currentPage === totalPages}
                                onClick={() => setCurrentPage(prev => Math.min(totalPages, prev + 1))}
                                className="p-1.5 rounded-lg border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 text-gray-500 hover:text-primary disabled:opacity-40 disabled:hover:text-gray-500 transition-colors flex items-center justify-center"
                                title="Trang sau"
                            >
                                <span className="material-symbols-outlined text-sm">chevron_right</span>
                            </button>
                            <button
                                type="button"
                                disabled={currentPage === totalPages}
                                onClick={() => setCurrentPage(totalPages)}
                                className="p-1.5 rounded-lg border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 text-gray-500 hover:text-primary disabled:opacity-40 disabled:hover:text-gray-500 transition-colors flex items-center justify-center"
                                title="Trang cuối"
                            >
                                <span className="material-symbols-outlined text-sm">last_page</span>
                            </button>
                        </div>
                    )}
                </div>
            </div>

            {/* Create/Edit Modal */}
            {modalOpen && (
                <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-sm" onClick={() => setModalOpen(false)}>
                    <div className="bg-white dark:bg-gray-900 rounded-2xl shadow-2xl w-full max-w-md mx-4 overflow-hidden border border-gray-200 dark:border-gray-700" onClick={e => e.stopPropagation()}>
                        <div className="flex items-center justify-between px-6 py-4 border-b border-gray-100 dark:border-gray-800">
                            <h3 className="text-lg font-semibold text-gray-800 dark:text-white">{modalMode === 'edit' ? 'Chỉnh sửa người dùng' : 'Thêm người dùng mới'}</h3>
                            <button onClick={() => setModalOpen(false)} className="flex items-center justify-center w-8 h-8 rounded-lg text-gray-400 hover:bg-gray-100 dark:hover:bg-gray-800 transition-colors"><span className="material-symbols-outlined">close</span></button>
                        </div>
                        <form onSubmit={handleSubmit} className="p-6 space-y-4 max-h-[70vh] overflow-y-auto">
                            {formError && <div className="bg-red-500/10 border border-red-500/30 text-red-500 px-4 py-3 rounded-lg text-sm flex items-center gap-2"><span className="material-symbols-outlined text-lg">error</span>{formError}</div>}
                            <div className="grid grid-cols-2 gap-4">
                                {modalMode === 'create' && formData.role === 'ADMIN' ? (
                                    <div className="space-y-1.5">
                                        <label className="text-xs font-semibold uppercase tracking-wide text-gray-500 dark:text-gray-400">Username ADMIN *</label>
                                        <input type="text" value={formData.username} onChange={e => setFormData({ ...formData, username: e.target.value })} placeholder="admin" required
                                            className="w-full h-10 px-3 rounded-lg border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 text-sm text-gray-800 dark:text-white focus:outline-none focus:ring-2 focus:ring-primary/30 transition-colors" />
                                    </div>
                                ) : modalMode === 'edit' ? (
                                    <div className="space-y-1.5">
                                        <label className="text-xs font-semibold uppercase tracking-wide text-gray-500 dark:text-gray-400">{formData.role === 'ADMIN' ? 'Username ADMIN' : 'Mã tài khoản'}</label>
                                        <input type="text" value={formData.username} readOnly
                                            className="w-full h-10 px-3 rounded-lg border border-gray-200 dark:border-gray-700 bg-gray-100 dark:bg-gray-800 text-sm text-gray-500 dark:text-gray-400 cursor-not-allowed" />
                                        {formData.role !== 'ADMIN' && !/^\d{8}$/.test(formData.username) && <p className="text-[11px] text-amber-600 dark:text-amber-400">Mã 8 số mới sẽ được cấp khi lưu.</p>}
                                    </div>
                                ) : (
                                    <div className="space-y-1.5">
                                        <label className="text-xs font-semibold uppercase tracking-wide text-gray-500 dark:text-gray-400">Mã tài khoản</label>
                                        <div className="min-h-10 px-3 py-2 rounded-lg border border-dashed border-primary/40 bg-primary/5 text-xs text-gray-600 dark:text-gray-300">Hệ thống tự cấp mã 8 số sau khi tạo.</div>
                                    </div>
                                )}
                                <div className="space-y-1.5">
                                    <label className="text-xs font-semibold uppercase tracking-wide text-gray-500 dark:text-gray-400">Họ tên *</label>
                                    <input type="text" value={formData.fullName} onChange={e => setFormData({ ...formData, fullName: e.target.value })} placeholder="Nguyễn Văn A" required
                                        className="w-full h-10 px-3 rounded-lg border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 text-sm text-gray-800 dark:text-white focus:outline-none focus:ring-2 focus:ring-primary/30 transition-colors" />
                                </div>
                            </div>
                            <div className="space-y-1.5">
                                <label className="text-xs font-semibold uppercase tracking-wide text-gray-500 dark:text-gray-400">Email *</label>
                                <input type="email" value={formData.email} onChange={e => setFormData({ ...formData, email: e.target.value })} placeholder="user@email.com" required
                                    className="w-full h-10 px-3 rounded-lg border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 text-sm text-gray-800 dark:text-white focus:outline-none focus:ring-2 focus:ring-primary/30 transition-colors" />
                            </div>
                            <div className="grid grid-cols-2 gap-4">
                                <div className="space-y-1.5">
                                    <label className="text-xs font-semibold uppercase tracking-wide text-gray-500 dark:text-gray-400">Số điện thoại</label>
                                    <input type="text" value={formData.phone} onChange={e => setFormData({ ...formData, phone: e.target.value })} placeholder="0901234567"
                                        className="w-full h-10 px-3 rounded-lg border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 text-sm text-gray-800 dark:text-white focus:outline-none focus:ring-2 focus:ring-primary/30 transition-colors" />
                                </div>
                                <div className="space-y-1.5">
                                    <label className="text-xs font-semibold uppercase tracking-wide text-gray-500 dark:text-gray-400">{modalMode === 'create' ? 'Mật khẩu *' : 'Mật khẩu mới'}</label>
                                    <input type="password" value={formData.password} onChange={e => setFormData({ ...formData, password: e.target.value })} placeholder={modalMode === 'edit' ? 'Để trống nếu không đổi' : '••••••'}
                                        className="w-full h-10 px-3 rounded-lg border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 text-sm text-gray-800 dark:text-white focus:outline-none focus:ring-2 focus:ring-primary/30 transition-colors" />
                                </div>
                            </div>
                            <div className="grid grid-cols-2 gap-4">
                                <div className="space-y-1.5">
                                    <label className="text-xs font-semibold uppercase tracking-wide text-gray-500 dark:text-gray-400">Vai trò</label>
                                    <select value={formData.role} onChange={e => setFormData({ ...formData, role: e.target.value })}
                                        className="w-full h-10 px-3 rounded-lg border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 text-sm text-gray-800 dark:text-white focus:outline-none focus:ring-2 focus:ring-primary/30 transition-colors">
                                        <option value="STUDENT">Sinh viên</option><option value="MANAGER">Quản lý</option><option value="ADMIN">Quản trị viên</option>
                                    </select>
                                </div>
                                <div className="space-y-1.5">
                                    <label className="text-xs font-semibold uppercase tracking-wide text-gray-500 dark:text-gray-400">Lớp</label>
                                    <select value={formData.classId} onChange={e => setFormData({ ...formData, classId: e.target.value })}
                                        className="w-full h-10 px-3 rounded-lg border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 text-sm text-gray-800 dark:text-white focus:outline-none focus:ring-2 focus:ring-primary/30 transition-colors">
                                        <option value="">— Không chọn —</option>
                                        {classes.map(c => <option key={c.id} value={c.id}>{c.name}</option>)}
                                    </select>
                                </div>
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

            {/* Reset Password Modal */}
            {resetTarget && (
                <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-sm" onClick={() => setResetTarget(null)}>
                    <div className="bg-white dark:bg-gray-900 rounded-2xl shadow-2xl w-full max-w-sm mx-4 overflow-hidden border border-gray-200 dark:border-gray-700" onClick={e => e.stopPropagation()}>
                        <div className="p-6">
                            <div className="mx-auto flex items-center justify-center w-14 h-14 rounded-full bg-orange-100 dark:bg-orange-900/40 text-orange-500 mb-4"><span className="material-symbols-outlined text-3xl">lock_reset</span></div>
                            <h3 className="text-lg font-semibold text-gray-800 dark:text-white text-center">Reset mật khẩu</h3>
                            <p className="text-sm text-gray-500 dark:text-gray-400 mt-1 text-center">Đặt mật khẩu mới cho <strong>{resetTarget.fullName}</strong></p>
                            <div className="mt-4 space-y-1.5">
                                <label className="text-xs font-semibold uppercase tracking-wide text-gray-500 dark:text-gray-400">Mật khẩu mới</label>
                                <input type="password" value={newPassword} onChange={e => setNewPassword(e.target.value)} placeholder="Ít nhất 6 ký tự" className="w-full h-10 px-3 rounded-lg border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 text-sm text-gray-800 dark:text-white focus:outline-none focus:ring-2 focus:ring-primary/30 transition-colors" />
                            </div>
                            <div className="flex items-center justify-center gap-3 mt-6">
                                <button onClick={() => setResetTarget(null)} className="px-4 py-2.5 rounded-xl text-sm font-medium text-gray-700 dark:text-gray-300 border border-gray-200 dark:border-gray-700 hover:bg-gray-50 dark:hover:bg-gray-800 transition-colors">Hủy</button>
                                <button onClick={handleResetPassword} disabled={resetLoading} className="inline-flex items-center gap-2 px-5 py-2.5 rounded-xl bg-orange-500 hover:bg-orange-600 text-white text-sm font-medium shadow-md shadow-orange-500/25 transition-colors disabled:opacity-50">
                                    {resetLoading ? 'Đang xử lý...' : 'Reset'}
                                </button>
                            </div>
                        </div>
                    </div>
                </div>
            )}

            {/* Delete Modal */}
            {deleteTarget && (
                <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-sm" onClick={() => setDeleteTarget(null)}>
                    <div className="bg-white dark:bg-gray-900 rounded-2xl shadow-2xl w-full max-w-sm mx-4 overflow-hidden border border-gray-200 dark:border-gray-700" onClick={e => e.stopPropagation()}>
                        <div className="p-6 text-center">
                            <div className="mx-auto flex items-center justify-center w-14 h-14 rounded-full bg-red-100 dark:bg-red-900/40 text-red-500 mb-4"><span className="material-symbols-outlined text-3xl">delete_forever</span></div>
                            <h3 className="text-lg font-semibold text-gray-800 dark:text-white">Xóa người dùng?</h3>
                            <p className="text-sm text-gray-500 dark:text-gray-400 mt-2">Bạn có chắc muốn xóa <strong className="text-gray-700 dark:text-gray-200">{deleteTarget.fullName}</strong>?</p>
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
