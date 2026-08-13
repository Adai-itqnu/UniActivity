import { useState, useEffect } from 'react'

export default function Members() {
    const [members, setMembers] = useState([])
    const [loading, setLoading] = useState(true)
    const [search, setSearch] = useState('')
    const [toast, setToast] = useState(null)

    const [currentPage, setCurrentPage] = useState(1)
    const ITEMS_PER_PAGE = 50

    useEffect(() => {
        setCurrentPage(1)
    }, [members])

    const showToast = (title, text, type = 'info') => {
        setToast({ title, type, text })
        setTimeout(() => setToast(null), 4000)
    }

    const fetchMembers = (q = '') => {
        setLoading(true)
        const url = q ? `/manager/api/members?search=${encodeURIComponent(q)}` : '/manager/api/members'
        fetch(url, { credentials: 'include' })
            .then(r => r.ok ? r.json() : [])
            .then(setMembers)
            .catch(() => setMembers([]))
            .finally(() => setLoading(false))
    }

    useEffect(() => { fetchMembers() }, [])

    const handleSearch = () => fetchMembers(search)

    const handleRemove = async (userId, name) => {
        if (!confirm(`Bạn có chắc muốn xóa ${name} khỏi lớp?`)) return
        try {
            const res = await fetch(`/manager/api/members/${userId}`, { method: 'DELETE', credentials: 'include' })
            const data = await res.json()
            if (!res.ok) throw new Error(data.message)
            showToast('Thành công', data.message, 'success')
            fetchMembers(search)
        } catch (e) {
            showToast('Có lỗi xảy ra', e.message, 'error')
        }
    }

    const managerCount = members.filter(m => m.role === 'MANAGER').length
    const studentCount = members.filter(m => m.role !== 'MANAGER').length

    const totalPages = Math.ceil(members.length / ITEMS_PER_PAGE)
    const paginated = members.slice((currentPage - 1) * ITEMS_PER_PAGE, currentPage * ITEMS_PER_PAGE)

    return (
        <div className="space-y-6">
            {/* ── Header ── */}
            <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
                <div className="flex items-center gap-3">
                    <div className="size-11 rounded-xl bg-gradient-to-br from-blue-500 to-cyan-500 flex items-center justify-center shadow-sm">
                        <span className="material-symbols-outlined text-white text-xl">groups</span>
                    </div>
                    <div>
                        <h2 className="text-2xl font-bold text-gray-900 dark:text-white tracking-tight">Thành viên lớp</h2>
                        <p className="text-sm text-gray-500 dark:text-gray-400">Quản lý thành viên trong lớp của bạn</p>
                    </div>
                </div>
            </div>

            {/* ── Stats ── */}
            <div className="grid grid-cols-1 sm:grid-cols-3 gap-5">
                <div className="bg-gradient-to-br from-blue-500 to-indigo-600 rounded-2xl p-5 text-white relative overflow-hidden">
                    <div className="absolute -right-4 -top-4 size-20 rounded-full bg-white/10 blur-xl" />
                    <div className="relative z-10 flex items-center gap-4">
                        <div className="size-11 rounded-xl bg-white/20 flex items-center justify-center"><span className="material-symbols-outlined text-xl">groups</span></div>
                        <div><h3 className="text-2xl font-bold">{members.length}</h3><p className="text-sm text-white/80">Tổng thành viên</p></div>
                    </div>
                </div>
                <div className="bg-gradient-to-br from-cyan-500 to-blue-500 rounded-2xl p-5 text-white relative overflow-hidden">
                    <div className="absolute -right-4 -top-4 size-20 rounded-full bg-white/10 blur-xl" />
                    <div className="relative z-10 flex items-center gap-4">
                        <div className="size-11 rounded-xl bg-white/20 flex items-center justify-center"><span className="material-symbols-outlined text-xl">shield_person</span></div>
                        <div><h3 className="text-2xl font-bold">{managerCount}</h3><p className="text-sm text-white/80">Quản lý</p></div>
                    </div>
                </div>
                <div className="bg-gradient-to-br from-emerald-500 to-teal-500 rounded-2xl p-5 text-white relative overflow-hidden">
                    <div className="absolute -right-4 -top-4 size-20 rounded-full bg-white/10 blur-xl" />
                    <div className="relative z-10 flex items-center gap-4">
                        <div className="size-11 rounded-xl bg-white/20 flex items-center justify-center"><span className="material-symbols-outlined text-xl">school</span></div>
                        <div><h3 className="text-2xl font-bold">{studentCount}</h3><p className="text-sm text-white/80">Sinh viên</p></div>
                    </div>
                </div>
            </div>

            {/* ── Toast ── */}
            {toast && (
                <div className={`px-4 py-3 rounded-xl text-sm font-medium flex items-center gap-2 shadow-sm ${toast.type === 'success' ? 'bg-emerald-100 dark:bg-emerald-900/30 text-emerald-700 dark:text-emerald-400' : 'bg-red-100 dark:bg-red-900/30 text-red-700 dark:text-red-400'}`}>
                    <span className="material-symbols-outlined text-lg">{toast.type === 'success' ? 'check_circle' : 'error'}</span>
                    {toast.text}
                </div>
            )}

            {/* ── Search ── */}
            <div className="bg-white dark:bg-gray-900 rounded-2xl border border-gray-100 dark:border-gray-800 p-4">
                <div className="flex gap-3">
                    <div className="relative flex-1">
                        <span className="material-symbols-outlined absolute left-3 top-1/2 -translate-y-1/2 text-gray-400 text-lg">search</span>
                        <input type="text" value={search} onChange={e => setSearch(e.target.value)}
                            onKeyDown={e => e.key === 'Enter' && handleSearch()}
                            placeholder="Tìm theo tên hoặc MSSV..."
                            className="w-full pl-10 pr-4 py-2.5 rounded-xl border border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-800 text-gray-900 dark:text-white text-sm focus:outline-none focus:ring-2 focus:ring-blue-500/50 focus:border-transparent transition" />
                    </div>
                    <button onClick={handleSearch}
                        className="px-5 py-2.5 bg-blue-600 text-white text-sm font-semibold rounded-xl hover:bg-blue-700 transition-colors flex items-center gap-2 shadow-sm">
                        <span className="material-symbols-outlined text-lg">search</span>Tìm kiếm
                    </button>
                </div>
            </div>

            {/* ── Table ── */}
            <div className="bg-white dark:bg-gray-900 rounded-2xl border border-gray-100 dark:border-gray-800 overflow-hidden">
                {loading ? (
                    <div className="flex items-center justify-center py-16">
                        <div className="text-center">
                            <div className="w-8 h-8 border-3 border-blue-500/30 border-t-blue-500 rounded-full animate-spin mx-auto mb-3" />
                            <p className="text-sm text-gray-400">Đang tải...</p>
                        </div>
                    </div>
                ) : (
                    <>
                        <div className="overflow-x-auto">
                            <table className="w-full text-sm">
                                <thead>
                                    <tr className="bg-gray-50 dark:bg-gray-800/50">
                                        <th className="text-left px-6 py-3 text-[10px] font-bold text-gray-400 uppercase tracking-wider">#</th>
                                        <th className="text-left px-6 py-3 text-[10px] font-bold text-gray-400 uppercase tracking-wider">Thành viên</th>
                                        <th className="text-left px-6 py-3 text-[10px] font-bold text-gray-400 uppercase tracking-wider">MSSV</th>
                                        <th className="text-left px-6 py-3 text-[10px] font-bold text-gray-400 uppercase tracking-wider">Email</th>
                                        <th className="text-left px-6 py-3 text-[10px] font-bold text-gray-400 uppercase tracking-wider">SĐT</th>
                                        <th className="text-center px-6 py-3 text-[10px] font-bold text-gray-400 uppercase tracking-wider">Vai trò</th>
                                        <th className="text-center px-6 py-3 text-[10px] font-bold text-gray-400 uppercase tracking-wider">Thao tác</th>
                                    </tr>
                                </thead>
                                <tbody className="divide-y divide-gray-50 dark:divide-gray-800">
                                    {paginated.length > 0 ? paginated.map((m, i) => {
                                        const initials = (m.fullName || '?').split(' ').map(w => w[0]).join('').toUpperCase().slice(0, 2)
                                        return (
                                            <tr key={m.id} className="hover:bg-blue-50/40 dark:hover:bg-gray-800/50 transition-colors">
                                                <td className="px-6 py-3.5 text-gray-400 text-xs">{(currentPage - 1) * ITEMS_PER_PAGE + i + 1}</td>
                                                <td className="px-6 py-3.5">
                                                    <div className="flex items-center gap-3">
                                                        {m.avatarUrl ? (
                                                            <img src={m.avatarUrl} alt={m.fullName} className="w-10 h-10 rounded-xl object-cover" />
                                                        ) : (
                                                            <div className="w-10 h-10 rounded-xl bg-gradient-to-br from-blue-400 to-indigo-600 flex items-center justify-center">
                                                                <span className="text-xs font-bold text-white">{initials}</span>
                                                            </div>
                                                        )}
                                                        <span className="font-medium text-gray-900 dark:text-white">{m.fullName}</span>
                                                    </div>
                                                </td>
                                                <td className="px-6 py-3.5 text-gray-500 dark:text-gray-400 font-mono text-xs">{m.username}</td>
                                                <td className="px-6 py-3.5 text-gray-500 dark:text-gray-400 text-sm">{m.email}</td>
                                                <td className="px-6 py-3.5 text-gray-500 dark:text-gray-400 text-sm">{m.phone || '—'}</td>
                                                <td className="px-6 py-3.5 text-center">
                                                    <span className={`px-2.5 py-1 text-[10px] font-bold rounded-full ${m.role === 'MANAGER'
                                                        ? 'bg-blue-100 dark:bg-blue-900/30 text-blue-600 dark:text-blue-400'
                                                        : 'bg-gray-100 dark:bg-gray-800 text-gray-500'}`}>
                                                        {m.role === 'MANAGER' ? 'Quản lý' : 'Sinh viên'}
                                                    </span>
                                                </td>
                                                <td className="px-6 py-3.5 text-center">
                                                    {m.role !== 'MANAGER' && (
                                                        <button onClick={() => handleRemove(m.id, m.fullName)}
                                                            className="size-8 inline-flex items-center justify-center rounded-lg bg-red-50 dark:bg-red-900/20 text-red-500 hover:bg-red-100 dark:hover:bg-red-900/30 transition-colors"
                                                            title="Xóa khỏi lớp">
                                                            <span className="material-symbols-outlined text-lg">person_remove</span>
                                                        </button>
                                                    )}
                                                </td>
                                            </tr>
                                        )
                                    }) : (
                                        <tr>
                                            <td colSpan={7} className="px-6 py-16 text-center">
                                                <span className="material-symbols-outlined text-5xl text-gray-300 dark:text-gray-600 block mb-3">group_off</span>
                                                <p className="text-gray-400 font-medium">Không tìm thấy thành viên nào</p>
                                            </td>
                                        </tr>
                                    )}
                                </tbody>
                            </table>
                        </div>
                        {/* Pagination Controls */}
                        <div className="px-6 py-4 border-t border-gray-100 dark:border-gray-800 flex flex-col sm:flex-row items-center justify-between gap-4 bg-gray-50/50 dark:bg-gray-800/30">
                            <p className="text-xs text-gray-500 dark:text-gray-400">
                                Hiển thị <span className="font-semibold text-gray-700 dark:text-gray-300">{members.length > 0 ? (currentPage - 1) * ITEMS_PER_PAGE + 1 : 0}</span> đến <span className="font-semibold text-gray-700 dark:text-gray-300">{Math.min(members.length, currentPage * ITEMS_PER_PAGE)}</span> trong <span className="font-semibold text-gray-700 dark:text-gray-300">{members.length}</span> thành viên
                            </p>
                            {totalPages > 1 && (
                                <div className="inline-flex items-center gap-1">
                                    <button
                                        type="button"
                                        disabled={currentPage === 1}
                                        onClick={() => setCurrentPage(1)}
                                        className="p-1.5 rounded-lg border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 text-gray-500 hover:text-blue-600 disabled:opacity-40 disabled:hover:text-gray-500 transition-colors flex items-center justify-center"
                                        title="Trang đầu"
                                    >
                                        <span className="material-symbols-outlined text-sm">first_page</span>
                                    </button>
                                    <button
                                        type="button"
                                        disabled={currentPage === 1}
                                        onClick={() => setCurrentPage(prev => Math.max(1, prev - 1))}
                                        className="p-1.5 rounded-lg border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 text-gray-500 hover:text-blue-600 disabled:opacity-40 disabled:hover:text-gray-500 transition-colors flex items-center justify-center"
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
                                        className="p-1.5 rounded-lg border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 text-gray-500 hover:text-blue-600 disabled:opacity-40 disabled:hover:text-gray-500 transition-colors flex items-center justify-center"
                                        title="Trang sau"
                                    >
                                        <span className="material-symbols-outlined text-sm">chevron_right</span>
                                    </button>
                                    <button
                                        type="button"
                                        disabled={currentPage === totalPages}
                                        onClick={() => setCurrentPage(totalPages)}
                                        className="p-1.5 rounded-lg border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 text-gray-500 hover:text-blue-600 disabled:opacity-40 disabled:hover:text-gray-500 transition-colors flex items-center justify-center"
                                        title="Trang cuối"
                                    >
                                        <span className="material-symbols-outlined text-sm">last_page</span>
                                    </button>
                                </div>
                            )}
                        </div>
                    </>
                )}
            </div>
        </div>
    )
}
