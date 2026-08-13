import { useState, useEffect } from 'react'
import { NavLink, useOutletContext } from 'react-router-dom'

const STATUS_MAP = {
    REGISTERED: { label: 'Chưa check-in', icon: 'schedule', cls: 'bg-amber-100 dark:bg-amber-900/30 text-amber-600 dark:text-amber-400' },
    ATTENDED: { label: 'Đã check-in', icon: 'check_circle', cls: 'bg-emerald-100 dark:bg-emerald-900/30 text-emerald-600 dark:text-emerald-400' },
    CANCELLED: { label: 'Đã hủy', icon: 'block', cls: 'bg-gray-100 dark:bg-gray-800 text-gray-500 dark:text-gray-400' },
}

const EVIDENCE_STATUS = {
    approved: { label: 'Đã duyệt', icon: 'verified', cls: 'bg-emerald-100 dark:bg-emerald-900/30 text-emerald-600 dark:text-emerald-400' },
    pending: { label: 'Chờ duyệt', icon: 'hourglass_top', cls: 'bg-blue-100 dark:bg-blue-900/30 text-blue-600 dark:text-blue-400' },
    rejected: { label: 'Từ chối', icon: 'cancel', cls: 'bg-red-100 dark:bg-red-900/30 text-red-600 dark:text-red-400' },
    none: { label: 'Chưa nộp', icon: 'image', cls: 'bg-gray-100 dark:bg-gray-800 text-gray-500 dark:text-gray-400' },
}

export default function MyRegistrations() {
    const { currentUser } = useOutletContext()
    const [data, setData] = useState(null)
    const [loading, setLoading] = useState(true)
    const [actionLoading, setActionLoading] = useState(null)
    const [toast, setToast] = useState(null)

    // Evidence modal
    const [evidenceModal, setEvidenceModal] = useState(null)
    const [evidenceFiles, setEvidenceFiles] = useState([])
    const [scoreOptions, setScoreOptions] = useState([])
    const [selectedScoreOption, setSelectedScoreOption] = useState('')
    const [uploading, setUploading] = useState(false)

    // View evidence modal
    const [viewEvidence, setViewEvidence] = useState(null)

    const apiPrefix = currentUser?.role === 'MANAGER' ? '/manager/api' : '/student/api'
    const checkinPath = currentUser?.role === 'MANAGER' ? '/manager/checkin' : '/student/checkin'
    const activitiesPath = currentUser?.role === 'MANAGER' ? '/manager/my-activities' : '/student/activities'

    const fetchData = () => {
        setLoading(true)
        fetch(`${apiPrefix}/my-registrations`, { credentials: 'include' })
            .then(r => { if (!r.ok) throw new Error('Lỗi'); return r.json() })
            .then(json => { setData(json); setLoading(false) })
            .catch(() => setLoading(false))
    }

    useEffect(() => {
        if (currentUser) fetchData()
    }, [currentUser, apiPrefix])

    // Lắng nghe sự kiện SSE để cập nhật lịch sử đăng ký ngầm ngay lập tức khi minh chứng được duyệt/từ chối
    useEffect(() => {
        const handleNotification = (e) => {
            const type = e.detail?.type
            if (type === 'EVIDENCE_APPROVED' || type === 'EVIDENCE_REJECTED') {
                console.log('[MyRegistrations] 🔔 Nhận thông báo duyệt minh chứng, cập nhật danh sách ngầm...')
                fetch(`${apiPrefix}/my-registrations`, { credentials: 'include' })
                    .then(r => r.ok ? r.json() : null)
                    .then(json => {
                        if (json) setData(json)
                    })
                    .catch(() => {})
            }
        }
        window.addEventListener('new-notification', handleNotification)
        return () => window.removeEventListener('new-notification', handleNotification)
    }, [apiPrefix])

    const showToast = (title, text, type = 'info') => {
        setToast({ title, type, text })
        setTimeout(() => setToast(null), 3000)
    }

    const handleCancel = async (activityId) => {
        if (!confirm('Bạn có chắc muốn hủy đăng ký hoạt động này?')) return
        setActionLoading(activityId)
        try {
            const res = await fetch(`${apiPrefix}/activities/${activityId}/register`, { method: 'DELETE', credentials: 'include' })
            const json = await res.json()
            if (!res.ok) throw new Error(json.message)
            showToast('Thành công', json.message, 'success')
            fetchData()
        } catch (e) {
            showToast('Có lỗi xảy ra', e.message, 'error')
        } finally {
            setActionLoading(null)
        }
    }

    const openEvidenceModal = async (reg) => {
        setEvidenceModal(reg)
        setEvidenceFiles([])
        setSelectedScoreOption('')
        try {
            const res = await fetch(`${apiPrefix}/activities/${reg.activity.id}/score-options`, { credentials: 'include' })
            if (res.ok) setScoreOptions(await res.json())
        } catch { setScoreOptions([]) }
    }

    const handleSubmitEvidence = async () => {
        if (!selectedScoreOption || evidenceFiles.length === 0) {
            showToast('Dữ liệu chưa hợp lệ', 'Vui lòng chọn mục điểm và ảnh minh chứng', 'error')
            return
        }
        if (evidenceFiles.length > 3) {
            showToast('Dữ liệu chưa hợp lệ', 'Tối đa 3 ảnh', 'error')
            return
        }
        setUploading(true)
        try {
            const fd = new FormData()
            fd.append('scoreOptionId', selectedScoreOption)
            evidenceFiles.forEach(f => fd.append('files', f))
            const res = await fetch(`${apiPrefix}/activities/${evidenceModal.activity.id}/evidence`, {
                method: 'POST', credentials: 'include', body: fd,
            })
            const json = await res.json()
            if (!res.ok) throw new Error(json.message)
            showToast('Thành công', json.message, 'success')
            setEvidenceModal(null)
            fetchData()
        } catch (e) {
            showToast('Có lỗi xảy ra', e.message, 'error')
        } finally {
            setUploading(false)
        }
    }

    const getEvidenceStatus = (reg) => {
        if (!reg.evidenceUrl) return 'none'
        if (reg.isApproved === true) return 'approved'
        if (reg.isApproved === false) return 'rejected'
        return 'pending'
    }

    if (loading) return <Loading />

    const registrations = data?.registrations || []

    return (
        <div className="space-y-6">
            {/* Header */}
            <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
                <div className="flex items-center gap-3">
                    <div className="size-10 rounded-xl bg-blue-500/10 flex items-center justify-center">
                        <span className="material-symbols-outlined text-blue-500">content_paste</span>
                    </div>
                    <div>
                        <h2 className="text-2xl font-bold text-gray-900 dark:text-white">Lịch sử đăng ký</h2>
                        <p className="text-sm text-gray-500 dark:text-gray-400">Quản lý các hoạt động bạn đã đăng ký</p>
                    </div>
                </div>
                <NavLink
                    to={checkinPath}
                    className="inline-flex items-center gap-2 px-4 py-2.5 bg-gradient-to-r from-emerald-500 to-teal-500 text-white font-bold text-sm rounded-xl hover:shadow-lg hover:shadow-emerald-500/30 transition-all shrink-0"
                >
                    <span className="material-symbols-outlined text-lg">qr_code_scanner</span>
                    Quét QR Check-in
                </NavLink>
            </div>

            {/* Toast */}
            {toast && (
                <div className={`px-4 py-3 rounded-xl text-sm font-medium flex items-center gap-2 ${toast.type === 'success' ? 'bg-emerald-100 dark:bg-emerald-900/30 text-emerald-700 dark:text-emerald-400' : 'bg-red-100 dark:bg-red-900/30 text-red-700 dark:text-red-400'}`}>
                    <span className="material-symbols-outlined text-lg">{toast.type === 'success' ? 'check_circle' : 'error'}</span>
                    {toast.text}
                </div>
            )}

            {registrations.length === 0 ? (
                /* Empty State */
                <div className="bg-white dark:bg-gray-900 rounded-2xl border border-gray-200 dark:border-gray-700 shadow-sm p-12 text-center">
                    <span className="material-symbols-outlined text-6xl text-gray-300 dark:text-gray-600 block mb-3">inbox</span>
                    <h3 className="text-lg font-bold text-gray-700 dark:text-gray-300 mb-1">Bạn chưa đăng ký hoạt động nào</h3>
                    <p className="text-sm text-gray-400 mb-4">Hãy khám phá và đăng ký các hoạt động.</p>
                    <NavLink to={activitiesPath} className="inline-flex items-center gap-2 px-5 py-2.5 bg-emerald-500 text-white font-medium rounded-xl hover:bg-emerald-600 transition-colors text-sm">
                        <span className="material-symbols-outlined text-lg">event</span>
                        Xem hoạt động
                    </NavLink>
                </div>
            ) : (
                /* Registration Cards (responsive, not table) */
                <div className="space-y-4">
                    <div className="text-sm text-gray-500 dark:text-gray-400 font-medium">
                        {registrations.length} đăng ký
                    </div>

                    {registrations.map((reg, i) => {
                        const status = STATUS_MAP[reg.status] || STATUS_MAP.REGISTERED
                        const evStatus = EVIDENCE_STATUS[getEvidenceStatus(reg)]
                        return (
                            <div key={reg.id} className="bg-white dark:bg-gray-900 rounded-2xl border border-gray-200 dark:border-gray-700 shadow-sm overflow-hidden hover:shadow-md transition-shadow">
                                <div className="p-5">
                                    <div className="flex flex-col lg:flex-row lg:items-center gap-4">
                                        {/* Index */}
                                        <div className="hidden lg:flex shrink-0 size-10 rounded-full bg-gray-100 dark:bg-gray-800 items-center justify-center text-sm font-bold text-gray-500">
                                            {i + 1}
                                        </div>

                                        {/* Activity Info */}
                                        <div className="flex-1 min-w-0">
                                            <h4 className="font-bold text-gray-900 dark:text-white truncate">{reg.activity.name}</h4>
                                            <div className="flex flex-wrap items-center gap-x-4 gap-y-1 mt-1.5">
                                                {reg.activity.location && (
                                                    <span className="text-xs text-gray-400 flex items-center gap-1">
                                                        <span className="material-symbols-outlined text-sm">location_on</span>
                                                        {reg.activity.location}
                                                    </span>
                                                )}
                                                {reg.registeredAt && (
                                                    <span className="text-xs text-gray-400 flex items-center gap-1">
                                                        <span className="material-symbols-outlined text-sm">calendar_today</span>
                                                        Đăng ký: {reg.registeredAt}
                                                    </span>
                                                )}
                                            </div>
                                        </div>

                                        {/* Badges */}
                                        <div className="flex flex-wrap items-center gap-2 shrink-0">
                                            {/* Check-in status */}
                                            <span className={`inline-flex items-center gap-1 px-2.5 py-1 text-xs font-bold rounded-full ${status.cls}`}>
                                                <span className="material-symbols-outlined text-sm">{status.icon}</span>
                                                {status.label}
                                            </span>
                                            {/* Evidence status */}
                                            <span className={`inline-flex items-center gap-1 px-2.5 py-1 text-xs font-bold rounded-full ${evStatus.cls}`}>
                                                <span className="material-symbols-outlined text-sm">{evStatus.icon}</span>
                                                {evStatus.label}
                                            </span>
                                        </div>

                                        {/* Actions */}
                                        <div className="flex items-center gap-2 shrink-0">
                                            {/* Cancel for REGISTERED */}
                                            {reg.status === 'REGISTERED' && (
                                                <button
                                                    onClick={() => handleCancel(reg.activity.id)}
                                                    disabled={actionLoading === reg.activity.id}
                                                    className="px-3 py-1.5 text-xs font-bold border border-red-300 dark:border-red-800 text-red-500 rounded-xl hover:bg-red-50 dark:hover:bg-red-900/20 transition-colors disabled:opacity-50"
                                                >
                                                    {actionLoading === reg.activity.id ? 'Đang hủy...' : 'Hủy ĐK'}
                                                </button>
                                            )}
                                            {/* Upload evidence for ATTENDED + no evidence */}
                                            {reg.status === 'ATTENDED' && !reg.evidenceUrl && (
                                                <button
                                                    onClick={() => openEvidenceModal(reg)}
                                                    className="px-3 py-1.5 text-xs font-bold bg-amber-500 text-white rounded-xl hover:bg-amber-600 transition-colors flex items-center gap-1"
                                                >
                                                    <span className="material-symbols-outlined text-sm">photo_camera</span>
                                                    Nộp MC
                                                </button>
                                            )}
                                            {/* View evidence */}
                                            {reg.evidenceUrl && (
                                                <button
                                                    onClick={() => setViewEvidence(reg.evidenceUrl)}
                                                    className="px-3 py-1.5 text-xs font-bold border border-blue-300 dark:border-blue-800 text-blue-500 rounded-xl hover:bg-blue-50 dark:hover:bg-blue-900/20 transition-colors flex items-center gap-1"
                                                >
                                                    <span className="material-symbols-outlined text-sm">visibility</span>
                                                    Xem MC
                                                </button>
                                            )}
                                            {/* Re-submit for rejected */}
                                            {reg.isApproved === false && (
                                                <button
                                                    onClick={() => openEvidenceModal(reg)}
                                                    className="px-3 py-1.5 text-xs font-bold border border-amber-300 dark:border-amber-800 text-amber-500 rounded-xl hover:bg-amber-50 dark:hover:bg-amber-900/20 transition-colors flex items-center gap-1"
                                                >
                                                    <span className="material-symbols-outlined text-sm">refresh</span>
                                                    Nộp lại
                                                </button>
                                            )}
                                        </div>
                                    </div>

                                    {/* Rejection reason */}
                                    {reg.isApproved === false && reg.rejectionReason && (
                                        <div className="mt-3 px-3 py-2 bg-red-50 dark:bg-red-900/10 border border-red-200 dark:border-red-800/50 rounded-xl text-xs text-red-600 dark:text-red-400 flex items-start gap-2">
                                            <span className="material-symbols-outlined text-sm mt-0.5">warning</span>
                                            <span>Lý do từ chối: {reg.rejectionReason}</span>
                                        </div>
                                    )}

                                    {/* Score info */}
                                    {reg.scoreOption && (
                                        <div className="mt-3 px-3 py-2 bg-emerald-50 dark:bg-emerald-900/10 border border-emerald-200 dark:border-emerald-800/50 rounded-xl text-xs text-emerald-600 dark:text-emerald-400 flex items-center gap-2">
                                            <span className="material-symbols-outlined text-sm">star</span>
                                            <span>Mục {reg.scoreOption.scoreCategory}: +{reg.scoreOption.scoreValue} điểm</span>
                                        </div>
                                    )}
                                </div>
                            </div>
                        )
                    })}
                </div>
            )}

            {/* ===== Evidence Upload Modal ===== */}
            {evidenceModal && (
                <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-sm p-4" onClick={() => setEvidenceModal(null)}>
                    <div className="bg-white dark:bg-gray-900 rounded-2xl shadow-2xl w-full max-w-lg border border-gray-200 dark:border-gray-700" onClick={e => e.stopPropagation()}>
                        <div className="px-6 py-4 border-b border-gray-200 dark:border-gray-700 flex items-center justify-between">
                            <h3 className="font-bold text-gray-900 dark:text-white flex items-center gap-2">
                                <span className="material-symbols-outlined text-amber-500">photo_camera</span>
                                Nộp minh chứng
                            </h3>
                            <button onClick={() => setEvidenceModal(null)} className="p-1 hover:bg-gray-100 dark:hover:bg-gray-800 rounded-lg transition-colors">
                                <span className="material-symbols-outlined text-gray-400">close</span>
                            </button>
                        </div>
                        <div className="p-6 space-y-4">
                            <p className="text-sm font-medium text-gray-700 dark:text-gray-300">{evidenceModal.activity.name}</p>

                            {/* Score option selection */}
                            <div>
                                <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">Chọn mục điểm rèn luyện <span className="text-red-500">*</span></label>
                                <select
                                    value={selectedScoreOption}
                                    onChange={e => setSelectedScoreOption(e.target.value)}
                                    className="w-full px-3 py-2.5 rounded-xl border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-800 text-gray-900 dark:text-white text-sm focus:outline-none focus:ring-2 focus:ring-emerald-500/50"
                                >
                                    <option value="">-- Chọn mục điểm --</option>
                                    {scoreOptions.map(opt => (
                                        <option key={opt.id} value={opt.id}>
                                            {opt.name} (Mục {opt.scoreCategory}: +{opt.scoreValue}đ)
                                        </option>
                                    ))}
                                </select>
                            </div>

                            {/* File upload */}
                            <div>
                                <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">Ảnh minh chứng (tối đa 3) <span className="text-red-500">*</span></label>
                                <input
                                    type="file"
                                    accept="image/*"
                                    multiple
                                    onChange={e => setEvidenceFiles([...e.target.files].slice(0, 3))}
                                    className="w-full text-sm text-gray-500 file:mr-4 file:py-2 file:px-4 file:rounded-xl file:border-0 file:text-sm file:font-medium file:bg-emerald-50 dark:file:bg-emerald-900/30 file:text-emerald-600 dark:file:text-emerald-400 hover:file:bg-emerald-100 dark:hover:file:bg-emerald-900/50"
                                />
                                {evidenceFiles.length > 0 && (
                                    <p className="mt-1 text-xs text-gray-400">Đã chọn {evidenceFiles.length} ảnh</p>
                                )}
                            </div>
                        </div>
                        <div className="px-6 py-4 border-t border-gray-200 dark:border-gray-700 flex justify-end gap-3">
                            <button onClick={() => setEvidenceModal(null)} className="px-4 py-2 text-sm font-medium text-gray-600 dark:text-gray-300 border border-gray-300 dark:border-gray-600 rounded-xl hover:bg-gray-50 dark:hover:bg-gray-800">
                                Hủy
                            </button>
                            <button
                                onClick={handleSubmitEvidence}
                                disabled={uploading || !selectedScoreOption || evidenceFiles.length === 0}
                                className="px-5 py-2 text-sm font-bold bg-emerald-500 text-white rounded-xl hover:bg-emerald-600 transition-colors disabled:opacity-50 flex items-center gap-2"
                            >
                                {uploading ? 'Đang gửi...' : (
                                    <>
                                        <span className="material-symbols-outlined text-lg">upload</span>
                                        Gửi minh chứng
                                    </>
                                )}
                            </button>
                        </div>
                    </div>
                </div>
            )}

            {/* ===== View Evidence Modal ===== */}
            {viewEvidence && (
                <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-sm p-4" onClick={() => setViewEvidence(null)}>
                    <div className="bg-white dark:bg-gray-900 rounded-2xl shadow-2xl w-full max-w-2xl border border-gray-200 dark:border-gray-700" onClick={e => e.stopPropagation()}>
                        <div className="px-6 py-4 border-b border-gray-200 dark:border-gray-700 flex items-center justify-between">
                            <h3 className="font-bold text-gray-900 dark:text-white flex items-center gap-2">
                                <span className="material-symbols-outlined text-blue-500">image</span>
                                Xem minh chứng
                            </h3>
                            <button onClick={() => setViewEvidence(null)} className="p-1 hover:bg-gray-100 dark:hover:bg-gray-800 rounded-lg transition-colors">
                                <span className="material-symbols-outlined text-gray-400">close</span>
                            </button>
                        </div>
                        <div className="p-6 max-h-[70vh] overflow-y-auto">
                            {(() => {
                                const urls = viewEvidence.split(',').map(u => u.trim()).filter(Boolean)
                                return (
                                    <div className={`grid gap-4 ${
                                        urls.length === 1 ? 'grid-cols-1' : urls.length === 2 ? 'grid-cols-2' : 'grid-cols-2 sm:grid-cols-3'
                                    }`}>
                                        {urls.map((url, i) => (
                                            <div key={i} className={`rounded-xl border border-gray-200 dark:border-gray-700 overflow-hidden bg-gray-50 dark:bg-gray-800 ${urls.length === 1 ? 'max-w-md mx-auto' : ''}`}>
                                                <img
                                                    src={url}
                                                    alt={`Minh chứng ${i + 1}`}
                                                    className="w-full h-full object-contain max-h-[50vh]"
                                                />
                                            </div>
                                        ))}
                                    </div>
                                )
                            })()}
                        </div>
                    </div>
                </div>
            )}
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
