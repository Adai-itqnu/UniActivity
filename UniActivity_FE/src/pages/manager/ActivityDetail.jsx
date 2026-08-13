import { useState, useEffect, useRef } from 'react'
import { useParams, Link } from 'react-router-dom'

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

export default function ActivityDetail() {
    const { activityId } = useParams()
    const [registrations, setRegistrations] = useState([])
    const [activity, setActivity] = useState(null)
    const [loading, setLoading] = useState(true)
    const [toast, setToast] = useState(null)
    const [rejectModal, setRejectModal] = useState(null)
    const [rejectReason, setRejectReason] = useState('')
    const [evidenceModal, setEvidenceModal] = useState(null)
    const [showQrModal, setShowQrModal] = useState(false)

    const showToast = (title, text, type = 'info') => {
        setToast({ title, type, text })
        setTimeout(() => setToast(null), 4000)
    }

    const fetchData = () => {
        setLoading(true)
        Promise.all([
            fetch('/manager/api/activities', { credentials: 'include' }).then(r => r.ok ? r.json() : []),
            fetch(`/manager/api/activities/${activityId}/registrations`, { credentials: 'include' }).then(r => r.ok ? r.json() : []),
        ])
            .then(([activities, regs]) => {
                const act = activities.find(a => String(a.id) === String(activityId))
                setActivity(act || null)
                setRegistrations(regs)
            })
            .catch(() => { })
            .finally(() => setLoading(false))
    }

    useEffect(() => { fetchData() }, [activityId])

    // Lắng nghe SSE: khi sinh viên đăng ký/hủy → tự re-fetch danh sách registrations
    useEffect(() => {
        const handleRegistrationUpdate = (e) => {
            const detail = e.detail
            // Only re-fetch if this event is for the current activity
            if (detail?.activityId && String(detail.activityId) === String(activityId)) {
                console.log('[ActivityDetail] 📝 Registration update received, re-fetching...')
                Promise.all([
                    fetch('/manager/api/activities', { credentials: 'include' }).then(r => r.ok ? r.json() : []),
                    fetch(`/manager/api/activities/${activityId}/registrations`, { credentials: 'include' }).then(r => r.ok ? r.json() : []),
                ])
                    .then(([activities, regs]) => {
                        const act = activities.find(a => String(a.id) === String(activityId))
                        if (act) setActivity(act)
                        setRegistrations(regs)
                    })
                    .catch(() => {})
            }
        }
        // Lắng nghe notification minh chứng → re-fetch
        const handleNotification = (e) => {
            const type = e.detail?.type
            if (type === 'EVIDENCE_SUBMITTED' || type === 'EVIDENCE_APPROVED' || type === 'EVIDENCE_REJECTED') {
                console.log('[ActivityDetail] 🔔 Evidence notification received, re-fetching...')
                Promise.all([
                    fetch('/manager/api/activities', { credentials: 'include' }).then(r => r.ok ? r.json() : []),
                    fetch(`/manager/api/activities/${activityId}/registrations`, { credentials: 'include' }).then(r => r.ok ? r.json() : []),
                ])
                    .then(([activities, regs]) => {
                        const act = activities.find(a => String(a.id) === String(activityId))
                        if (act) setActivity(act)
                        setRegistrations(regs)
                    })
                    .catch(() => {})
            }
        }
        window.addEventListener('activity-registration-update', handleRegistrationUpdate)
        window.addEventListener('new-notification', handleNotification)
        return () => {
            window.removeEventListener('activity-registration-update', handleRegistrationUpdate)
            window.removeEventListener('new-notification', handleNotification)
        }
    }, [activityId])

    const handleApprove = async (regId) => {
        try {
            const res = await fetch(`/manager/api/registrations/${regId}/approve`, { method: 'POST', credentials: 'include' })
            const data = await res.json()
            if (!res.ok) throw new Error(data.message)
            showToast('Thành công', data.message, 'success')
            fetchData()
        } catch (e) {
            showToast('Có lỗi xảy ra', e.message, 'error')
        }
    }

    const handleReject = async () => {
        if (!rejectReason.trim()) {
            showToast('Dữ liệu chưa hợp lệ', 'Vui lòng nhập lý do từ chối', 'error')
            return
        }
        try {
            const res = await fetch(`/manager/api/registrations/${rejectModal.id}/reject`, {
                method: 'POST', credentials: 'include',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ reason: rejectReason.trim() }),
            })
            const data = await res.json()
            if (!res.ok) throw new Error(data.message)
            showToast('Thành công', data.message, 'success')
            setRejectModal(null)
            setRejectReason('')
            fetchData()
        } catch (e) {
            showToast('Có lỗi xảy ra', e.message, 'error')
        }
    }

    const handleManualCheckin = async (regId) => {
        try {
            const res = await fetch(`/manager/api/registrations/${regId}/checkin`, { method: 'POST', credentials: 'include' })
            const data = await res.json()
            if (!res.ok) throw new Error(data.message)
            showToast('Thành công', data.message, 'success')
            fetchData()
        } catch (e) {
            showToast('Có lỗi xảy ra', e.message, 'error')
        }
    }

    if (loading) {
        return (
            <div className="flex items-center justify-center h-[60vh]">
                <div className="text-center">
                    <div className="w-10 h-10 border-3 border-blue-500/30 border-t-blue-500 rounded-full animate-spin mx-auto mb-4" />
                    <p className="text-sm text-slate-400">Đang tải chi tiết...</p>
                </div>
            </div>
        )
    }

    const checkedInCount = registrations.filter(r => r.status === 'ATTENDED').length
    const pendingCount = registrations.filter(r => r.isApproved == null && r.evidenceUrl).length
    const approvedCount = registrations.filter(r => r.isApproved === true).length
    const regPercent = activity?.maxSlots ? Math.round(((activity.registeredCount || 0) / activity.maxSlots) * 100) : 0

    return (
        <div className="space-y-6">
            {/* ── Header ── */}
            <div className="flex flex-col sm:flex-row sm:items-center gap-4">
                <Link to="/manager/activities" className="size-10 rounded-xl bg-gray-100 dark:bg-gray-800 flex items-center justify-center hover:bg-gray-200 dark:hover:bg-gray-700 transition-colors shrink-0 self-start">
                    <span className="material-symbols-outlined text-gray-600 dark:text-gray-300">arrow_back</span>
                </Link>
                <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-2 flex-wrap">
                        <h2 className="text-2xl font-bold text-gray-900 dark:text-white tracking-tight truncate">{activity?.name || `Hoạt động #${activityId}`}</h2>
                        {activity && (
                            <span className={`px-2.5 py-1 text-[10px] font-bold rounded-full ${STATUS_COLORS[activity.status] || STATUS_COLORS.DRAFT}`}>
                                {STATUS_LABELS[activity.status] || activity.status}
                            </span>
                        )}
                    </div>
                    <p className="text-sm text-gray-500 dark:text-gray-400 mt-0.5">Chi tiết đăng ký & kiểm duyệt minh chứng</p>
                </div>
                <button
                    onClick={() => setShowQrModal(true)}
                    className="size-10 rounded-xl bg-gradient-to-br from-emerald-500 to-teal-500 flex items-center justify-center hover:shadow-lg hover:shadow-emerald-500/30 transition-all shrink-0 self-start"
                    title="Mã QR check-in"
                >
                    <span className="material-symbols-outlined text-white text-xl">qr_code_2</span>
                </button>
            </div>

            {/* ── QR Modal ── */}
            {showQrModal && <QrCodeModal activityId={activityId} activityName={activity?.name} onClose={() => setShowQrModal(false)} />}

            {/* ── Toast ── */}
            {toast && (
                <div className={`px-4 py-3 rounded-xl text-sm font-medium flex items-center gap-2 shadow-sm ${toast.type === 'success' ? 'bg-emerald-100 dark:bg-emerald-900/30 text-emerald-700 dark:text-emerald-400' : 'bg-red-100 dark:bg-red-900/30 text-red-700 dark:text-red-400'}`}>
                    <span className="material-symbols-outlined text-lg">{toast.type === 'success' ? 'check_circle' : 'error'}</span>
                    {toast.text}
                </div>
            )}

            {/* ── Info Cards ── */}
            {activity && (
                <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-5">
                    <InfoCard icon="schedule" label="Thời gian" value={fmtDate(activity.startTime)} gradient="from-blue-500 to-indigo-600" />
                    <InfoCard icon="location_on" label="Địa điểm" value={activity.location || 'N/A'} gradient="from-emerald-500 to-teal-500" />
                    {activity.latitude && activity.longitude && (
                        <div className="flex items-center gap-2 text-xs text-emerald-600 dark:text-emerald-400 bg-emerald-50 dark:bg-emerald-900/20 px-3 py-2 rounded-xl border border-emerald-200 dark:border-emerald-800 col-span-1 sm:col-span-2 lg:col-span-4 -mt-2">
                            <span className="material-symbols-outlined text-sm">my_location</span>
                            <span>GPS check-in: <strong>{Number(activity.latitude).toFixed(5)}, {Number(activity.longitude).toFixed(5)}</strong>
                            {activity.checkinRadius && <> — Bán kính: <strong>{activity.checkinRadius}m</strong></>}</span>
                        </div>
                    )}
                    <InfoCard icon="how_to_reg" label="Đăng ký" value={`${activity.registeredCount || 0}/${activity.maxSlots || '∞'}`} gradient="from-amber-400 to-orange-500" />
                    <InfoCard icon="fact_check" label="Điểm danh" value={checkedInCount} gradient="from-purple-500 to-pink-500" />
                </div>
            )}

            {/* ── Registration Progress ── */}
            {activity?.maxSlots && (
                <div className="bg-white dark:bg-gray-900 rounded-2xl border border-gray-100 dark:border-gray-800 p-5">
                    <div className="flex items-center justify-between mb-3">
                        <span className="text-sm font-medium text-gray-700 dark:text-gray-300">Tiến độ đăng ký</span>
                        <span className="text-sm font-bold text-blue-600 dark:text-blue-400">{regPercent}%</span>
                    </div>
                    <div className="h-2.5 bg-gray-100 dark:bg-gray-800 rounded-full overflow-hidden">
                        <div className="h-full bg-gradient-to-r from-blue-500 to-cyan-500 rounded-full transition-all duration-500" style={{ width: `${Math.min(regPercent, 100)}%` }} />
                    </div>
                    <div className="flex items-center justify-between mt-2 text-xs text-gray-400">
                        <span>{activity.registeredCount || 0} đã đăng ký</span>
                        <span>{activity.maxSlots} chỗ</span>
                    </div>
                </div>
            )}

            {/* ── Quick Stats Row ── */}
            <div className="grid grid-cols-3 gap-4">
                <div className="bg-white dark:bg-gray-900 rounded-2xl border border-gray-100 dark:border-gray-800 p-4 text-center">
                    <p className="text-2xl font-bold text-amber-500">{pendingCount}</p>
                    <p className="text-[10px] text-gray-400 mt-1 font-medium uppercase tracking-wide">Chờ duyệt</p>
                </div>
                <div className="bg-white dark:bg-gray-900 rounded-2xl border border-gray-100 dark:border-gray-800 p-4 text-center">
                    <p className="text-2xl font-bold text-emerald-500">{approvedCount}</p>
                    <p className="text-[10px] text-gray-400 mt-1 font-medium uppercase tracking-wide">Đã duyệt</p>
                </div>
                <div className="bg-white dark:bg-gray-900 rounded-2xl border border-gray-100 dark:border-gray-800 p-4 text-center">
                    <p className="text-2xl font-bold text-blue-500">{checkedInCount}</p>
                    <p className="text-[10px] text-gray-400 mt-1 font-medium uppercase tracking-wide">Điểm danh</p>
                </div>
            </div>

            {/* ── Registrations Table ── */}
            <div className="bg-white dark:bg-gray-900 rounded-2xl border border-gray-100 dark:border-gray-800 overflow-hidden">
                <div className="px-6 py-4 border-b border-gray-100 dark:border-gray-800 flex items-center justify-between">
                    <div className="flex items-center gap-2">
                        <span className="material-symbols-outlined text-blue-500">list_alt</span>
                        <h3 className="font-bold text-gray-900 dark:text-white">Danh sách đăng ký</h3>
                        <span className="px-2 py-0.5 text-xs font-bold bg-blue-100 dark:bg-blue-900/30 text-blue-600 dark:text-blue-400 rounded-full">{registrations.length}</span>
                    </div>
                </div>
                <div className="overflow-x-auto">
                    <table className="w-full text-sm">
                        <thead>
                            <tr className="bg-gray-50 dark:bg-gray-800/50">
                                <th className="text-left px-6 py-3 text-[10px] font-bold text-gray-400 uppercase tracking-wider">#</th>
                                <th className="text-left px-6 py-3 text-[10px] font-bold text-gray-400 uppercase tracking-wider">Sinh viên</th>
                                <th className="text-center px-6 py-3 text-[10px] font-bold text-gray-400 uppercase tracking-wider">Điểm danh</th>
                                <th className="text-center px-6 py-3 text-[10px] font-bold text-gray-400 uppercase tracking-wider">Mục điểm</th>
                                <th className="text-center px-6 py-3 text-[10px] font-bold text-gray-400 uppercase tracking-wider">Minh chứng</th>
                                <th className="text-center px-6 py-3 text-[10px] font-bold text-gray-400 uppercase tracking-wider">Duyệt</th>
                                <th className="text-center px-6 py-3 text-[10px] font-bold text-gray-400 uppercase tracking-wider">Thao tác</th>
                            </tr>
                        </thead>
                        <tbody className="divide-y divide-gray-50 dark:divide-gray-800">
                            {registrations.length > 0 ? registrations.map((r, i) => (
                                <tr key={r.id} className="hover:bg-blue-50/40 dark:hover:bg-gray-800/50 transition-colors">
                                    <td className="px-6 py-3.5 text-gray-400 text-xs">{i + 1}</td>
                                    <td className="px-6 py-3.5">
                                        <div className="flex items-center gap-3">
                                            <div className="w-9 h-9 rounded-xl bg-gradient-to-br from-blue-400 to-indigo-600 flex items-center justify-center shrink-0">
                                                <span className="text-xs font-bold text-white">{(r.studentName || '?').split(' ').map(w => w[0]).join('').toUpperCase().slice(0, 2)}</span>
                                            </div>
                                            <div>
                                                <p className="font-medium text-gray-900 dark:text-white text-sm">{r.studentName}</p>
                                                <p className="text-xs text-gray-400">{r.studentCode}</p>
                                            </div>
                                        </div>
                                    </td>
                                    <td className="px-6 py-3.5 text-center">
                                        <span className={`px-2.5 py-1 text-[10px] font-bold rounded-full ${r.status === 'ATTENDED'
                                            ? 'bg-emerald-100 dark:bg-emerald-900/30 text-emerald-600 dark:text-emerald-400'
                                            : r.status === 'CANCELLED'
                                                ? 'bg-red-100 dark:bg-red-900/30 text-red-500'
                                                : 'bg-gray-100 dark:bg-gray-800 text-gray-500'
                                            }`}>
                                            {r.status === 'ATTENDED' ? 'Đã điểm danh' : r.status === 'CANCELLED' ? 'Đã hủy' : 'Đã ĐK'}
                                        </span>
                                    </td>
                                    <td className="px-6 py-3.5 text-center">
                                        {r.scoreOption ? (
                                            <span className="px-2 py-1 text-[10px] font-bold bg-purple-100 dark:bg-purple-900/30 text-purple-600 dark:text-purple-400 rounded-full">
                                                {r.scoreOption.scoreCategory}: +{r.scoreOption.scoreValue}đ
                                            </span>
                                        ) : (
                                            <span className="text-xs text-gray-400">—</span>
                                        )}
                                    </td>
                                    <td className="px-6 py-3.5 text-center">
                                        {r.evidenceUrl ? (
                                            <button onClick={() => setEvidenceModal(r.evidenceUrl)}
                                                className="inline-flex items-center gap-1 px-2.5 py-1 text-xs font-medium text-blue-600 dark:text-blue-400 bg-blue-50 dark:bg-blue-900/20 rounded-lg hover:bg-blue-100 dark:hover:bg-blue-900/30 transition-colors">
                                                <span className="material-symbols-outlined text-sm">image</span>Xem
                                            </button>
                                        ) : (
                                            <span className="text-xs text-gray-400">—</span>
                                        )}
                                    </td>
                                    <td className="px-6 py-3.5 text-center">
                                        {r.isApproved === true && (
                                            <span className="px-2.5 py-1 text-[10px] font-bold rounded-full bg-emerald-100 dark:bg-emerald-900/30 text-emerald-600">Đã duyệt</span>
                                        )}
                                        {r.isApproved === false && (
                                            <div>
                                                <span className="px-2.5 py-1 text-[10px] font-bold rounded-full bg-red-100 dark:bg-red-900/30 text-red-500">Từ chối</span>
                                                {r.rejectionReason && (
                                                    <p className="text-[10px] text-red-400 mt-1 max-w-[120px] truncate" title={r.rejectionReason}>{r.rejectionReason}</p>
                                                )}
                                            </div>
                                        )}
                                        {r.isApproved == null && (
                                            <span className="px-2.5 py-1 text-[10px] font-bold rounded-full bg-amber-100 dark:bg-amber-900/30 text-amber-600">Chờ duyệt</span>
                                        )}
                                    </td>
                                    <td className="px-6 py-3.5 text-center">
                                        <div className="flex items-center justify-center gap-1">
                                            {/* Manual checkin button for REGISTERED students */}
                                            {r.status === 'REGISTERED' && (
                                                <button onClick={() => handleManualCheckin(r.id)}
                                                    className="size-8 inline-flex items-center justify-center rounded-lg bg-blue-50 dark:bg-blue-900/20 text-blue-600 hover:bg-blue-100 dark:hover:bg-blue-900/30 transition-colors" title="Điểm danh thủ công">
                                                    <span className="material-symbols-outlined text-lg">how_to_reg</span>
                                                </button>
                                            )}
                                            {/* Approve/Reject for pending evidence */}
                                            {r.isApproved == null && r.evidenceUrl && (
                                                <>
                                                    <button onClick={() => handleApprove(r.id)}
                                                        className="size-8 inline-flex items-center justify-center rounded-lg bg-emerald-50 dark:bg-emerald-900/20 text-emerald-600 hover:bg-emerald-100 dark:hover:bg-emerald-900/30 transition-colors" title="Duyệt">
                                                        <span className="material-symbols-outlined text-lg">check</span>
                                                    </button>
                                                    <button onClick={() => setRejectModal(r)}
                                                        className="size-8 inline-flex items-center justify-center rounded-lg bg-red-50 dark:bg-red-900/20 text-red-500 hover:bg-red-100 dark:hover:bg-red-900/30 transition-colors" title="Từ chối">
                                                        <span className="material-symbols-outlined text-lg">close</span>
                                                    </button>
                                                </>
                                            )}
                                        </div>
                                    </td>
                                </tr>
                            )) : (
                                <tr>
                                    <td colSpan={7} className="px-6 py-16 text-center">
                                        <span className="material-symbols-outlined text-5xl text-gray-300 dark:text-gray-600 block mb-3">people</span>
                                        <p className="text-gray-400 font-medium">Chưa có sinh viên đăng ký</p>
                                    </td>
                                </tr>
                            )}
                        </tbody>
                    </table>
                </div>
            </div>

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
                                <img key={i} src={url.trim()} alt={`Minh chứng ${i + 1}`} className="w-full rounded-xl object-contain" />
                            ))}
                        </div>
                    </div>
                </div>
            )}

            {/* ── Reject Modal ── */}
            {rejectModal && (
                <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-sm" onClick={() => { setRejectModal(null); setRejectReason('') }}>
                    <div className="bg-white dark:bg-gray-900 rounded-2xl border border-gray-100 dark:border-gray-800 shadow-2xl p-6 max-w-md w-full mx-4" onClick={e => e.stopPropagation()}>
                        <div className="flex items-center gap-3 mb-4">
                            <div className="size-10 rounded-xl bg-red-100 dark:bg-red-900/30 flex items-center justify-center">
                                <span className="material-symbols-outlined text-red-500">block</span>
                            </div>
                            <div>
                                <h3 className="font-bold text-gray-900 dark:text-white">Từ chối minh chứng</h3>
                                <p className="text-xs text-gray-500 dark:text-gray-400">SV: <span className="font-medium text-gray-700 dark:text-gray-200">{rejectModal.studentName}</span></p>
                            </div>
                        </div>
                        <textarea
                            value={rejectReason}
                            onChange={e => setRejectReason(e.target.value)}
                            rows={3}
                            placeholder="Nhập lý do từ chối..."
                            className="w-full px-4 py-2.5 rounded-xl border border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-800 text-gray-900 dark:text-white text-sm focus:outline-none focus:ring-2 focus:ring-red-500/50 focus:border-transparent resize-none transition"
                        />
                        <div className="flex justify-end gap-2 mt-4">
                            <button onClick={() => { setRejectModal(null); setRejectReason('') }}
                                className="px-4 py-2.5 border border-gray-200 dark:border-gray-700 text-gray-600 dark:text-gray-300 text-sm font-medium rounded-xl hover:bg-gray-50 dark:hover:bg-gray-800 transition-colors">
                                Hủy
                            </button>
                            <button onClick={handleReject}
                                className="px-4 py-2.5 bg-red-500 text-white text-sm font-semibold rounded-xl hover:bg-red-600 transition-colors shadow-sm">
                                Từ chối
                            </button>
                        </div>
                    </div>
                </div>
            )}
        </div>
    )
}

function InfoCard({ icon, label, value, gradient }) {
    return (
        <div className={`bg-gradient-to-br ${gradient} rounded-2xl p-5 text-white relative overflow-hidden`}>
            <div className="absolute -right-4 -top-4 size-16 rounded-full bg-white/10 blur-xl" />
            <div className="relative z-10">
                <div className="size-10 rounded-xl bg-white/20 flex items-center justify-center mb-3">
                    <span className="material-symbols-outlined text-lg">{icon}</span>
                </div>
                <p className="text-lg font-bold">{value}</p>
                <p className="text-xs text-white/80 mt-0.5">{label}</p>
            </div>
        </div>
    )
}

function QrCodeModal({ activityId, activityName, onClose }) {
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
            const res = await fetch(`/manager/api/qrcode/dynamic/${activityId}`, { credentials: 'include' })
            if (!res.ok) throw new Error('Không thể tải mã QR')
            const data = await res.json()
            setQrData(data)
            intervalSeconds.current = data.interval || 60
            setCountdown(data.secondsRemaining || data.interval || 60)
            setError(null)
            // Flash animation
            setRefreshing(true)
            setTimeout(() => setRefreshing(false), 600)
        } catch (err) {
            setError(err.message)
        } finally {
            setLoading(false)
        }
    }

    // Init fetch
    useEffect(() => {
        fetchDynamicQr()
        return () => clearInterval(countdownRef.current)
    }, [activityId])

    // Countdown timer — dùng 1 interval duy nhất
    useEffect(() => {
        clearInterval(countdownRef.current)
        countdownRef.current = setInterval(() => {
            setCountdown(prev => {
                if (prev <= 1) {
                    fetchDynamicQr()
                    return intervalSeconds.current
                }
                return prev - 1
            })
        }, 1000)
        return () => clearInterval(countdownRef.current)
    }, [qrData?.token])

    // Render QR code on canvas
    useEffect(() => {
        if (!qrData?.checkinUrl || !canvasRef.current) return
        import('qrcode').then(QRCode => {
            QRCode.toCanvas(canvasRef.current, qrData.checkinUrl, {
                width: 280,
                margin: 2,
                color: { dark: '#000000', light: '#ffffff' },
                errorCorrectionLevel: 'H',
            })
        }).catch(() => {
            setError('Không thể render QR. Vui lòng cài thêm thư viện qrcode.')
        })
    }, [qrData?.checkinUrl])

    const interval = intervalSeconds.current
    const countdownPercent = (countdown / interval) * 100
    const countdownMm = Math.floor(countdown / 60)
    const countdownSs = countdown % 60
    const isWarning = countdown <= 10

    return (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm" onClick={onClose}>
            <div className="bg-white dark:bg-gray-900 rounded-2xl shadow-2xl w-full max-w-sm mx-4 overflow-hidden" onClick={e => e.stopPropagation()}>
                {/* Header */}
                <div className="flex items-center justify-between px-5 py-4 border-b border-gray-200 dark:border-gray-700">
                    <div className="flex items-center gap-2 min-w-0">
                        <span className="material-symbols-outlined text-emerald-500">qr_code_2</span>
                        <div className="min-w-0">
                            <h3 className="font-bold text-gray-900 dark:text-white truncate text-sm">QR Check-in động</h3>
                            <p className="text-[10px] text-emerald-500 font-medium">Tự đổi mỗi {interval >= 60 ? `${interval / 60} phút` : `${interval} giây`}</p>
                        </div>
                    </div>
                    <button onClick={onClose} className="size-8 rounded-lg bg-gray-100 dark:bg-gray-800 flex items-center justify-center hover:bg-red-100 hover:text-red-500 transition-colors shrink-0">
                        <span className="material-symbols-outlined text-lg">close</span>
                    </button>
                </div>

                {/* Body */}
                <div className="p-6 flex flex-col items-center">
                    {loading && (
                        <div className="py-12 text-center">
                            <div className="w-8 h-8 border-3 border-emerald-500/30 border-t-emerald-500 rounded-full animate-spin mx-auto mb-3" />
                            <p className="text-sm text-gray-400">Đang tạo mã QR...</p>
                        </div>
                    )}
                    {error && (
                        <div className="py-8 text-center">
                            <span className="material-symbols-outlined text-4xl text-red-400 mb-2">error</span>
                            <p className="text-sm text-red-500">{error}</p>
                        </div>
                    )}
                    {qrData && !loading && (
                        <>
                            {/* QR with refresh animation */}
                            <div className={`p-3 bg-white rounded-2xl border-2 border-dashed transition-all duration-500 ${
                                refreshing ? 'border-emerald-500 shadow-lg shadow-emerald-500/20 scale-[1.02]' : 'border-emerald-300 dark:border-emerald-700'
                            }`}>
                                <canvas ref={canvasRef} className="w-64 h-64 object-contain" />
                            </div>

                            {/* Countdown timer */}
                            <div className="w-full mt-4">
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
                                        className={`h-full rounded-full transition-all duration-1000 ease-linear ${
                                            isWarning ? 'bg-red-500' : 'bg-gradient-to-r from-emerald-500 to-teal-500'
                                        }`}
                                        style={{ width: `${countdownPercent}%` }}
                                    />
                                </div>
                            </div>

                            <p className="text-sm font-semibold text-gray-900 dark:text-white mt-4 text-center truncate max-w-full">
                                {activityName || `Hoạt động #${activityId}`}
                            </p>
                            <div className="flex items-center gap-2 mt-2">
                                <span className="material-symbols-outlined text-sm text-amber-500">shield</span>
                                <p className="text-[11px] text-amber-600 dark:text-amber-400 font-medium">
                                    QR chống gian lận — không thể screenshot gửi từ xa
                                </p>
                            </div>
                        </>
                    )}
                </div>
            </div>
        </div>
    )
}
