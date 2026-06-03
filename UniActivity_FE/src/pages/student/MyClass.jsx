import { useState, useEffect } from 'react'
import { useOutletContext, NavLink } from 'react-router-dom'

export default function MyClass() {
    const { currentUser } = useOutletContext()
    const [data, setData] = useState(null)
    const [loading, setLoading] = useState(true)
    const [search, setSearch] = useState('')
    const [searchInput, setSearchInput] = useState('')
    const [currentPage, setCurrentPage] = useState(1)
    const ITEMS_PER_PAGE = 50

    useEffect(() => {
        setLoading(true)
        const params = search ? `?search=${encodeURIComponent(search)}` : ''
        fetch(`/student/api/my-class${params}`, { credentials: 'include' })
            .then(r => { if (!r.ok) throw new Error('Lỗi tải dữ liệu'); return r.json() })
            .then(json => { setData(json); setLoading(false) })
            .catch(() => setLoading(false))
    }, [search])

    useEffect(() => {
        setCurrentPage(1)
    }, [data])

    const handleSearch = (e) => {
        e.preventDefault()
        setSearch(searchInput.trim())
    }

    const clearSearch = () => {
        setSearchInput('')
        setSearch('')
    }

    if (loading) return <Loading />

    if (!data?.hasClass) {
        return (
            <div className="space-y-6">
                <PageHeader />
                <NoClassWarning />
            </div>
        )
    }

    const { studentClass: cls, members = [], memberCount = 0 } = data
    const totalPages = Math.ceil(members.length / ITEMS_PER_PAGE)
    const paginated = members.slice((currentPage - 1) * ITEMS_PER_PAGE, currentPage * ITEMS_PER_PAGE)

    return (
        <div className="space-y-6">
            <PageHeader />

            {/* Class Info + Member Count */}
            <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
                {/* Class Info Card */}
                <div className="lg:col-span-2 bg-white dark:bg-gray-900 rounded-2xl border border-gray-200 dark:border-gray-700 shadow-sm overflow-hidden">
                    <div className="bg-gradient-to-r from-emerald-500 to-teal-500 px-6 py-4 flex items-center gap-3">
                        <span className="material-symbols-outlined text-white text-xl">info</span>
                        <h3 className="text-white font-bold">Thông tin lớp</h3>
                    </div>
                    <div className="p-6">
                        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                            <InfoRow label="Tên lớp" value={cls.name} />
                            <InfoRow label="Mã lớp" value={cls.code} />
                            <InfoRow label="Khoa" value={cls.facultyName || 'Chưa xác định'} />
                            <InfoRow label="Niên khóa" value={cls.academicYearCode || 'N/A'} />
                        </div>
                    </div>
                </div>

                {/* Member Count Card */}
                <div className="bg-gradient-to-br from-blue-500 to-cyan-500 rounded-2xl p-6 flex flex-col items-center justify-center text-white relative overflow-hidden">
                    <div className="absolute -right-6 -top-6 size-32 rounded-full bg-white/10 blur-xl" />
                    <div className="relative z-10 text-center">
                        <span className="text-6xl font-bold">{memberCount}</span>
                        <p className="mt-2 text-white/80 font-medium">Thành viên trong lớp</p>
                    </div>
                </div>
            </div>

            {/* Search */}
            <div className="bg-white dark:bg-gray-900 rounded-2xl border border-gray-200 dark:border-gray-700 shadow-sm p-5">
                <form onSubmit={handleSearch} className="flex flex-col sm:flex-row gap-3">
                    <div className="relative flex-1">
                        <span className="material-symbols-outlined absolute left-3 top-1/2 -translate-y-1/2 text-gray-400">search</span>
                        <input
                            type="text"
                            value={searchInput}
                            onChange={e => setSearchInput(e.target.value)}
                            placeholder="Nhập tên hoặc mã sinh viên..."
                            className="w-full pl-10 pr-4 py-2.5 rounded-xl border border-gray-300 dark:border-gray-600 bg-gray-50 dark:bg-gray-800 text-gray-900 dark:text-white placeholder-gray-400 focus:outline-none focus:ring-2 focus:ring-emerald-500/50 focus:border-emerald-500 text-sm"
                        />
                    </div>
                    <div className="flex gap-2 shrink-0">
                        <button type="submit" className="px-5 py-2.5 bg-emerald-500 text-white font-medium rounded-xl hover:bg-emerald-600 transition-colors flex items-center gap-2 text-sm">
                            <span className="material-symbols-outlined text-lg">search</span>
                            Tìm kiếm
                        </button>
                        {search && (
                            <button type="button" onClick={clearSearch} className="px-4 py-2.5 border border-gray-300 dark:border-gray-600 text-gray-600 dark:text-gray-300 rounded-xl hover:bg-gray-50 dark:hover:bg-gray-800 transition-colors flex items-center gap-2 text-sm">
                                <span className="material-symbols-outlined text-lg">close</span>
                                Xóa lọc
                            </button>
                        )}
                    </div>
                </form>
            </div>

            {/* Members Table */}
            <div className="bg-white dark:bg-gray-900 rounded-2xl border border-gray-200 dark:border-gray-700 shadow-sm overflow-hidden">
                <div className="px-6 py-4 border-b border-gray-200 dark:border-gray-700 flex items-center justify-between">
                    <h3 className="font-bold text-gray-900 dark:text-white flex items-center gap-2">
                        <span className="material-symbols-outlined text-emerald-500">list</span>
                        Danh sách thành viên
                        {search && (
                            <span className="ml-2 px-2.5 py-0.5 text-xs font-medium bg-blue-100 dark:bg-blue-900/30 text-blue-600 dark:text-blue-400 rounded-full">
                                Tìm: "{search}"
                            </span>
                        )}
                    </h3>
                    <span className="text-sm text-gray-400">{members.length} kết quả</span>
                </div>

                {paginated.length > 0 ? (
                    <>
                        <div className="overflow-x-auto">
                            <table className="w-full">
                                <thead>
                                    <tr className="bg-gray-50 dark:bg-gray-800/50">
                                        <th className="px-6 py-3 text-left text-xs font-semibold text-gray-500 dark:text-gray-400 uppercase tracking-wider">#</th>
                                        <th className="px-6 py-3 text-left text-xs font-semibold text-gray-500 dark:text-gray-400 uppercase tracking-wider">Mã SV</th>
                                        <th className="px-6 py-3 text-left text-xs font-semibold text-gray-500 dark:text-gray-400 uppercase tracking-wider">Họ tên</th>
                                        <th className="px-6 py-3 text-left text-xs font-semibold text-gray-500 dark:text-gray-400 uppercase tracking-wider">Email</th>
                                        <th className="px-6 py-3 text-left text-xs font-semibold text-gray-500 dark:text-gray-400 uppercase tracking-wider">Vai trò</th>
                                    </tr>
                                </thead>
                                <tbody className="divide-y divide-gray-100 dark:divide-gray-800">
                                    {paginated.map((m, i) => (
                                        <tr key={m.id} className="hover:bg-gray-50 dark:hover:bg-gray-800/30 transition-colors">
                                            <td className="px-6 py-4 text-sm text-gray-500 dark:text-gray-400">{(currentPage - 1) * ITEMS_PER_PAGE + i + 1}</td>
                                            <td className="px-6 py-4">
                                                <span className="px-2.5 py-1 text-xs font-medium bg-gray-100 dark:bg-gray-800 text-gray-600 dark:text-gray-300 rounded-lg">
                                                    {m.username}
                                                </span>
                                            </td>
                                            <td className="px-6 py-4">
                                                <div className="flex items-center gap-3">
                                                    {m.avatarUrl ? (
                                                        <img src={m.avatarUrl} alt="" className="w-8 h-8 rounded-full object-cover" />
                                                    ) : (
                                                        <div className="w-8 h-8 rounded-full bg-emerald-500/10 flex items-center justify-center text-emerald-600 text-xs font-bold">
                                                            {m.fullName?.charAt(0) || '?'}
                                                        </div>
                                                    )}
                                                    <span className="text-sm font-medium text-gray-900 dark:text-white">{m.fullName}</span>
                                                </div>
                                            </td>
                                            <td className="px-6 py-4 text-sm text-gray-500 dark:text-gray-400">{m.email}</td>
                                            <td className="px-6 py-4">
                                                {m.role === 'MANAGER' ? (
                                                    <span className="inline-flex items-center gap-1 px-2.5 py-1 text-xs font-bold bg-amber-100 dark:bg-amber-900/30 text-amber-600 dark:text-amber-400 rounded-full">
                                                        <span className="material-symbols-outlined text-sm">star</span>
                                                        Quản lý lớp
                                                    </span>
                                                ) : (
                                                    <span className="inline-flex items-center gap-1 px-2.5 py-1 text-xs font-bold bg-blue-100 dark:bg-blue-900/30 text-blue-600 dark:text-blue-400 rounded-full">
                                                        <span className="material-symbols-outlined text-sm">school</span>
                                                        Sinh viên
                                                    </span>
                                                )}
                                            </td>
                                        </tr>
                                    ))}
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
                                        className="p-1.5 rounded-lg border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-850 text-gray-500 hover:text-emerald-600 disabled:opacity-40 disabled:hover:text-gray-500 transition-colors flex items-center justify-center"
                                        title="Trang đầu"
                                    >
                                        <span className="material-symbols-outlined text-sm">first_page</span>
                                    </button>
                                    <button
                                        type="button"
                                        disabled={currentPage === 1}
                                        onClick={() => setCurrentPage(prev => Math.max(1, prev - 1))}
                                        className="p-1.5 rounded-lg border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-850 text-gray-500 hover:text-emerald-600 disabled:opacity-40 disabled:hover:text-gray-500 transition-colors flex items-center justify-center"
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
                                        className="p-1.5 rounded-lg border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-850 text-gray-500 hover:text-emerald-600 disabled:opacity-40 disabled:hover:text-gray-500 transition-colors flex items-center justify-center"
                                        title="Trang sau"
                                    >
                                        <span className="material-symbols-outlined text-sm">chevron_right</span>
                                    </button>
                                    <button
                                        type="button"
                                        disabled={currentPage === totalPages}
                                        onClick={() => setCurrentPage(totalPages)}
                                        className="p-1.5 rounded-lg border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-850 text-gray-500 hover:text-emerald-600 disabled:opacity-40 disabled:hover:text-gray-500 transition-colors flex items-center justify-center"
                                        title="Trang cuối"
                                    >
                                        <span className="material-symbols-outlined text-sm">last_page</span>
                                    </button>
                                </div>
                            )}
                        </div>
                    </>
                ) : (
                    <div className="py-12 text-center">
                        <span className="material-symbols-outlined text-5xl text-gray-300 dark:text-gray-600 block mb-2">search_off</span>
                        {search ? (
                            <p className="text-gray-400">Không tìm thấy thành viên với từ khóa "{search}"</p>
                        ) : (
                            <p className="text-gray-400">Chưa có thành viên nào trong lớp</p>
                        )}
                    </div>
                )}
            </div>
        </div>
    )
}

/* ---- Sub-components ---- */

function PageHeader() {
    return (
        <div className="flex items-center gap-3">
            <div className="size-10 rounded-xl bg-emerald-500/10 flex items-center justify-center">
                <span className="material-symbols-outlined text-emerald-500">groups</span>
            </div>
            <div>
                <h2 className="text-2xl font-bold text-gray-900 dark:text-white">Lớp của tôi</h2>
                <p className="text-sm text-gray-500 dark:text-gray-400">Thông tin lớp và danh sách thành viên</p>
            </div>
        </div>
    )
}

function InfoRow({ label, value }) {
    return (
        <div className="flex items-center gap-3 py-2">
            <span className="text-sm text-gray-500 dark:text-gray-400 w-24 shrink-0">{label}:</span>
            <span className="text-sm font-semibold text-gray-900 dark:text-white">{value}</span>
        </div>
    )
}

function NoClassWarning() {
    return (
        <div className="bg-amber-50 dark:bg-amber-900/10 border border-amber-200 dark:border-amber-800/50 rounded-2xl p-8 text-center">
            <span className="material-symbols-outlined text-5xl text-amber-500 mb-4 block">warning</span>
            <h3 className="text-xl font-bold text-gray-900 dark:text-white mb-2">Bạn chưa tham gia lớp nào</h3>
            <p className="text-gray-500 dark:text-gray-400 mb-4">
                Vui lòng vào <NavLink to="/student/home" className="text-emerald-500 font-bold hover:underline">trang chủ</NavLink> để tham gia lớp.
            </p>
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
