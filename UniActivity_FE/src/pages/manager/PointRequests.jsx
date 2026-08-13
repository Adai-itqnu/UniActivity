import { useState, useEffect, useCallback } from 'react'
import SecureImage from '../../components/common/SecureImage'

function timeAgo(dateStr) {
    if (!dateStr) return ''
    const now = new Date(), d = new Date(dateStr)
    const mins = Math.floor((now - d) / 60000)
    if (mins < 1) return 'Vừa xong'
    if (mins < 60) return `${mins} phút trước`
    const hours = Math.floor(mins / 60)
    if (hours < 24) return `${hours} giờ trước`
    const days = Math.floor(hours / 24)
    return `${days} ngày trước`
}

export default function PointRequests() {
    const [requests, setRequests] = useState([])
    const [loading, setLoading] = useState(true)
    const [search, setSearch] = useState('')
    const [toast, setToast] = useState(null)
    const [actionModal, setActionModal] = useState(null)
    const [comment, setComment] = useState('')
    const [evidenceModal, setEvidenceModal] = useState(null)

    const showToast = (title, text, type = 'info') => {
        setToast({ title, type, text })
        setTimeout(() => setToast(null), 4000)
    }

    const fetchData = useCallback(() => {
        setLoading(true)
        fetch('/manager/api/point-requests', { credentials: 'include' })
            .then(r => r.ok ? r.json() : [])
            .then(setRequests)
            .catch(() => setRequests([]))
            .finally(() => setLoading(false))
    }, [])

    useEffect(() => { fetchData() }, [fetchData])

    // Lắng nghe sự kiện SSE để cập nhật danh sách yêu cầu điểm ngầm lập tức
    useEffect(() => {
        const handleRefresh = (e) => {
            // Làm mới nếu nhận sự kiện dashboard-update hoặc thông báo yêu cầu điểm mới
            if (e.type === 'dashboard-update' || e.detail?.type === 'POINT_REQUEST_SUBMITTED') {
                console.log('[PointRequests] 📊 Nhận tín hiệu SSE, cập nhật danh sách yêu cầu ngầm...')
                fetch('/manager/api/point-requests', { credentials: 'include' })
                    .then(r => r.ok ? r.json() : null)
                    .then(d => {
                        if (d) setRequests(d)
                    })
                    .catch(() => {})
            }
        }
        window.addEventListener('dashboard-update', handleRefresh)
        window.addEventListener('new-notification', handleRefresh)
        return () => {
            window.removeEventListener('dashboard-update', handleRefresh)
            window.removeEventListener('new-notification', handleRefresh)
        }
    }, [])

    const handleAction = async () => {
        if (!actionModal) return
        const { id, action } = actionModal
        try {
            const res = await fetch(`/manager/api/point-requests/${id}/${action}`, {
                method: 'POST', credentials: 'include',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ comment: comment.trim() || null }),
            })
            const data = await res.json()
            if (!res.ok) throw new Error(data.message)
            showToast('Thành công', data.message, 'success')
            setActionModal(null)
            setComment('')
            fetchData()
        } catch (e) {
            showToast('Có lỗi xảy ra', e.message, 'error')
        }
    }

    const filtered = requests.filter(r => {
        if (!search) return true
        const q = search.toLowerCase()
        return r.studentName?.toLowerCase().includes(q) || r.studentCode?.toLowerCase().includes(q) || r.criteriaCode?.toLowerCase().includes(q)
    })

    const totalScore = requests.reduce((s, r) => s + (r.claimedScore || 0), 0)

    return (
        <div className="space-y-6">
            {/* ── Header ── */}
            <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
                <div className="flex items-center gap-3">
                    <div className="size-11 rounded-xl bg-gradient-to-br from-purple-500 to-pink-500 flex items-center justify-center shadow-sm">
                        <span className="material-symbols-outlined text-white text-xl">grade</span>
                    </div>
                    <div>
                        <h2 className="text-2xl font-bold text-gray-900 dark:text-white tracking-tight">Yêu cầu điểm rèn luyện</h2>
                        <p className="text-sm text-gray-500 dark:text-gray-400">Kiểm duyệt yêu cầu cộng điểm từ sinh viên</p>
                    </div>
                </div>
            </div>

            {/* ── Stats ── */}
            <div className="grid grid-cols-1 sm:grid-cols-3 gap-5">
                <div className="bg-gradient-to-br from-purple-500 to-pink-500 rounded-2xl p-5 text-white relative overflow-hidden">
                    <div className="absolute -right-4 -top-4 size-20 rounded-full bg-white/10 blur-xl" />
                    <div className="relative z-10 flex items-center gap-4">
                        <div className="size-11 rounded-xl bg-white/20 flex items-center justify-center"><span className="material-symbols-outlined text-xl">pending_actions</span></div>
                        <div><h3 className="text-2xl font-bold">{requests.length}</h3><p className="text-sm text-white/80">Yêu cầu chờ</p></div>
                    </div>
                </div>
                <div className="bg-gradient-to-br from-amber-400 to-orange-500 rounded-2xl p-5 text-white relative overflow-hidden">
                    <div className="absolute -right-4 -top-4 size-20 rounded-full bg-white/10 blur-xl" />
                    <div className="relative z-10 flex items-center gap-4">
                        <div className="size-11 rounded-xl bg-white/20 flex items-center justify-center"><span className="material-symbols-outlined text-xl">star</span></div>
                        <div><h3 className="text-2xl font-bold">{totalScore}</h3><p className="text-sm text-white/80">Tổng điểm yêu cầu</p></div>
                    </div>
                </div>
                <div className="bg-gradient-to-br from-blue-500 to-indigo-600 rounded-2xl p-5 text-white relative overflow-hidden">
                    <div className="absolute -right-4 -top-4 size-20 rounded-full bg-white/10 blur-xl" />
                    <div className="relative z-10 flex items-center gap-4">
                        <div className="size-11 rounded-xl bg-white/20 flex items-center justify-center"><span className="material-symbols-outlined text-xl">description</span></div>
                        <div><h3 className="text-2xl font-bold">{requests.filter(r => r.evidenceImageUrl).length}</h3><p className="text-sm text-white/80">Có minh chứng</p></div>
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
                <div className="relative">
                    <span className="material-symbols-outlined absolute left-3 top-1/2 -translate-y-1/2 text-gray-400 text-lg">search</span>
                    <input type="text" value={search} onChange={e => setSearch(e.target.value)}
                        placeholder="Tìm theo tên SV, MSSV, mã tiêu chí..."
                        className="w-full pl-10 pr-4 py-2.5 rounded-xl border border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-800 text-gray-900 dark:text-white text-sm focus:outline-none focus:ring-2 focus:ring-purple-500/50 focus:border-transparent transition" />
                </div>
            </div>

            {/* ── Table ── */}
            <div className="bg-white dark:bg-gray-900 rounded-2xl border border-gray-100 dark:border-gray-800 overflow-hidden">
                <div className="px-6 py-4 border-b border-gray-100 dark:border-gray-800 flex items-center gap-2">
                    <span className="material-symbols-outlined text-purple-500">list_alt</span>
                    <h3 className="font-bold text-gray-900 dark:text-white">Danh sách yêu cầu</h3>
                    <span className="px-2 py-0.5 text-xs font-bold bg-purple-100 dark:bg-purple-900/30 text-purple-600 dark:text-purple-400 rounded-full">{filtered.length}</span>
                </div>
                {loading ? (
                    <div className="flex items-center justify-center py-16">
                        <div className="text-center">
                            <div className="w-8 h-8 border-3 border-purple-500/30 border-t-purple-500 rounded-full animate-spin mx-auto mb-3" />
                            <p className="text-sm text-gray-400">Đang tải...</p>
                        </div>
                    </div>
                ) : (
                    <div className="overflow-x-auto">
                        <table className="w-full text-sm">
                            <thead>
                                <tr className="bg-gray-50 dark:bg-gray-800/50">
                                    <th className="text-left px-6 py-3 text-[10px] font-bold text-gray-400 uppercase tracking-wider">#</th>
                                    <th className="text-left px-6 py-3 text-[10px] font-bold text-gray-400 uppercase tracking-wider">Sinh viên</th>
                                    <th className="text-center px-6 py-3 text-[10px] font-bold text-gray-400 uppercase tracking-wider">Mã TC</th>
                                    <th className="text-center px-6 py-3 text-[10px] font-bold text-gray-400 uppercase tracking-wider">Điểm</th>
                                    <th className="text-left px-6 py-3 text-[10px] font-bold text-gray-400 uppercase tracking-wider">Mô tả</th>
                                    <th className="text-center px-6 py-3 text-[10px] font-bold text-gray-400 uppercase tracking-wider">Minh chứng</th>
                                    <th className="text-left px-6 py-3 text-[10px] font-bold text-gray-400 uppercase tracking-wider">Thời gian</th>
                                    <th className="text-center px-6 py-3 text-[10px] font-bold text-gray-400 uppercase tracking-wider">Thao tác</th>
                                </tr>
                            </thead>
                            <tbody className="divide-y divide-gray-50 dark:divide-gray-800">
                                {filtered.length > 0 ? filtered.map((r, i) => {
                                    const initials = (r.studentName || '?').split(' ').map(w => w[0]).join('').toUpperCase().slice(0, 2)
                                    return (
                                        <tr key={r.id} className="hover:bg-blue-50/40 dark:hover:bg-gray-800/50 transition-colors">
                                            <td className="px-6 py-3.5 text-gray-400 text-xs">{i + 1}</td>
                                            <td className="px-6 py-3.5">
                                                <div className="flex items-center gap-3">
                                                    <div className="w-10 h-10 rounded-xl bg-gradient-to-br from-purple-400 to-pink-500 flex items-center justify-center shrink-0">
                                                        <span className="text-xs font-bold text-white">{initials}</span>
                                                    </div>
                                                    <div>
                                                        <p className="font-medium text-gray-900 dark:text-white text-sm">{r.studentName}</p>
                                                        <p className="text-xs text-gray-400 font-mono">{r.studentCode}</p>
                                                    </div>
                                                </div>
                                            </td>
                                            <td className="px-6 py-3.5 text-center">
                                                <span className="px-2.5 py-1 text-[10px] font-bold bg-blue-100 dark:bg-blue-900/30 text-blue-600 dark:text-blue-400 rounded-full">
                                                    {r.criteriaCode}
                                                </span>
                                            </td>
                                            <td className="px-6 py-3.5 text-center">
                                                <span className="text-lg font-bold text-purple-600 dark:text-purple-400">{r.claimedScore}</span>
                                            </td>
                                            <td className="px-6 py-3.5 text-gray-500 dark:text-gray-400 max-w-[200px] truncate text-sm">{r.description || '—'}</td>
                                            <td className="px-6 py-3.5 text-center">
                                                {r.evidenceImageUrl ? (
                                                    <button onClick={() => setEvidenceModal(r.evidenceImageUrl)}
                                                        className="inline-flex items-center gap-1 px-2.5 py-1 text-xs font-medium text-blue-600 dark:text-blue-400 bg-blue-50 dark:bg-blue-900/20 rounded-lg hover:bg-blue-100 dark:hover:bg-blue-900/30 transition-colors">
                                                        <span className="material-symbols-outlined text-sm">image</span>Xem
                                                    </button>
                                                ) : (
                                                    <span className="text-xs text-gray-400">—</span>
                                                )}
                                            </td>
                                            <td className="px-6 py-3.5 text-xs text-gray-400">{timeAgo(r.createdAt)}</td>
                                            <td className="px-6 py-3.5 text-center">
                                                <div className="flex items-center justify-center gap-1.5">
                                                    <button onClick={() => { setActionModal({ id: r.id, action: 'approve', studentName: r.studentName }); setComment('') }}
                                                        className="px-3.5 py-1.5 bg-emerald-500 text-white text-xs font-bold rounded-lg hover:bg-emerald-600 transition-colors flex items-center gap-1 shadow-sm">
                                                        <span className="material-symbols-outlined text-sm">check</span>Duyệt
                                                    </button>
                                                    <button onClick={() => { setActionModal({ id: r.id, action: 'reject', studentName: r.studentName }); setComment('') }}
                                                        className="px-3.5 py-1.5 bg-red-500 text-white text-xs font-bold rounded-lg hover:bg-red-600 transition-colors flex items-center gap-1 shadow-sm">
                                                        <span className="material-symbols-outlined text-sm">close</span>Từ chối
                                                    </button>
                                                </div>
                                            </td>
                                        </tr>
                                    )
                                }) : (
                                    <tr>
                                        <td colSpan={8} className="px-6 py-16 text-center">
                                            <span className="material-symbols-outlined text-5xl text-gray-300 dark:text-gray-600 block mb-3">inbox</span>
                                            <p className="text-gray-400 font-medium">Không có yêu cầu điểm nào</p>
                                        </td>
                                    </tr>
                                )}
                            </tbody>
                        </table>
                    </div>
                )}
            </div>

            {/* ── Action Modal ── */}
            {actionModal && (
                <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-sm" onClick={() => { setActionModal(null); setComment('') }}>
                    <div className="bg-white dark:bg-gray-900 rounded-2xl border border-gray-100 dark:border-gray-800 shadow-2xl p-6 max-w-md w-full mx-4" onClick={e => e.stopPropagation()}>
                        <div className="flex items-center gap-3 mb-4">
                            <div className={`size-10 rounded-xl flex items-center justify-center ${actionModal.action === 'approve' ? 'bg-emerald-100 dark:bg-emerald-900/30' : 'bg-red-100 dark:bg-red-900/30'}`}>
                                <span className={`material-symbols-outlined ${actionModal.action === 'approve' ? 'text-emerald-500' : 'text-red-500'}`}>
                                    {actionModal.action === 'approve' ? 'check_circle' : 'block'}
                                </span>
                            </div>
                            <div>
                                <h3 className="font-bold text-gray-900 dark:text-white">
                                    {actionModal.action === 'approve' ? 'Duyệt yêu cầu điểm' : 'Từ chối yêu cầu điểm'}
                                </h3>
                                <p className="text-xs text-gray-500 dark:text-gray-400">SV: <span className="font-medium text-gray-700 dark:text-gray-200">{actionModal.studentName}</span></p>
                            </div>
                        </div>
                        <textarea
                            value={comment}
                            onChange={e => setComment(e.target.value)}
                            rows={3}
                            placeholder={actionModal.action === 'approve' ? 'Nhận xét (tùy chọn)...' : 'Lý do từ chối...'}
                            className="w-full px-4 py-2.5 rounded-xl border border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-800 text-gray-900 dark:text-white text-sm focus:outline-none focus:ring-2 focus:ring-blue-500/50 focus:border-transparent resize-none transition"
                        />
                        <div className="flex justify-end gap-2 mt-4">
                            <button onClick={() => { setActionModal(null); setComment('') }}
                                className="px-4 py-2.5 border border-gray-200 dark:border-gray-700 text-gray-600 dark:text-gray-300 text-sm font-medium rounded-xl hover:bg-gray-50 dark:hover:bg-gray-800 transition-colors">
                                Hủy
                            </button>
                            <button onClick={handleAction}
                                className={`px-4 py-2.5 text-white text-sm font-semibold rounded-xl transition-colors shadow-sm ${actionModal.action === 'approve' ? 'bg-emerald-500 hover:bg-emerald-600' : 'bg-red-500 hover:bg-red-600'}`}>
                                {actionModal.action === 'approve' ? 'Duyệt' : 'Từ chối'}
                            </button>
                        </div>
                    </div>
                </div>
            )}

            {/* ── Evidence Modal ── */}
            {evidenceModal && (
                <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-sm" onClick={() => setEvidenceModal(null)}>
                    <div className="bg-white dark:bg-gray-900 rounded-2xl border border-gray-100 dark:border-gray-800 shadow-2xl p-5 max-w-lg w-full mx-4" onClick={e => e.stopPropagation()}>
                        <div className="flex items-center justify-between mb-4">
                            <div className="flex items-center gap-2">
                                <div className="size-9 rounded-xl bg-blue-500/10 flex items-center justify-center">
                                    <span className="material-symbols-outlined text-blue-500">image</span>
                                </div>
                                <h3 className="font-bold text-gray-900 dark:text-white">Minh chứng</h3>
                            </div>
                            <button onClick={() => setEvidenceModal(null)} className="size-8 flex items-center justify-center rounded-lg hover:bg-gray-100 dark:hover:bg-gray-800 text-gray-400 hover:text-gray-600 transition-colors">
                                <span className="material-symbols-outlined text-lg">close</span>
                            </button>
                        </div>
                        <div className="grid grid-cols-1 gap-4 max-h-[60vh] overflow-y-auto">
                            {evidenceModal.split(',').map((url, i) => (
                                <SecureImage key={i} src={url.trim()} alt={`Minh chứng ${i + 1}`} className="w-full rounded-xl object-contain" />
                            ))}
                        </div>
                    </div>
                </div>
            )}
        </div>
    )
}
