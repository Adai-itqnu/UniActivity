import { useState, useEffect, useCallback, useRef } from 'react'
import { QRCodeCanvas } from 'qrcode.react'

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

export default function JoinRequests() {
    const [data, setData] = useState({ requests: [], joinCode: '' })
    const [loading, setLoading] = useState(true)
    const [toast, setToast] = useState(null)
    const [copied, setCopied] = useState(false)
    const [showQr, setShowQr] = useState(false)
    const qrRef = useRef(null)

    const showToast = (type, text) => {
        setToast({ type, text })
        setTimeout(() => setToast(null), 4000)
    }

    const fetchData = useCallback(() => {
        setLoading(true)
        fetch('/manager/api/join-requests', { credentials: 'include' })
            .then(r => r.ok ? r.json() : { requests: [], joinCode: '' })
            .then(setData)
            .catch(() => setData({ requests: [], joinCode: '' }))
            .finally(() => setLoading(false))
    }, [])

    useEffect(() => { fetchData() }, [fetchData])

    const handleApprove = async (id) => {
        try {
            const res = await fetch(`/manager/api/join-requests/${id}/approve`, { method: 'POST', credentials: 'include' })
            const d = await res.json()
            if (!res.ok) throw new Error(d.message)
            showToast('success', d.message)
            fetchData()
        } catch (e) {
            showToast('error', e.message)
        }
    }

    const handleReject = async (id) => {
        try {
            const res = await fetch(`/manager/api/join-requests/${id}/reject`, { method: 'POST', credentials: 'include' })
            const d = await res.json()
            if (!res.ok) throw new Error(d.message)
            showToast('success', d.message)
            fetchData()
        } catch (e) {
            showToast('error', e.message)
        }
    }

    const handleRegenerate = async () => {
        try {
            const res = await fetch('/manager/api/regenerate-join-code', { method: 'POST', credentials: 'include' })
            const d = await res.json()
            if (!res.ok) throw new Error(d.message)
            showToast('success', d.message)
            setData(prev => ({ ...prev, joinCode: d.joinCode }))
        } catch (e) {
            showToast('error', e.message)
        }
    }

    const copyJoinCode = () => {
        navigator.clipboard.writeText(data.joinCode)
        setCopied(true)
        setTimeout(() => setCopied(false), 2000)
    }

    const downloadQr = () => {
        const canvas = qrRef.current?.querySelector('canvas')
        if (!canvas) return
        const link = document.createElement('a')
        link.download = `join-code-${data.joinCode}.png`
        link.href = canvas.toDataURL('image/png')
        link.click()
    }

    return (
        <div className="space-y-6">
            {/* ── Header ── */}
            <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
                <div className="flex items-center gap-3">
                    <div className="size-11 rounded-xl bg-gradient-to-br from-amber-400 to-orange-500 flex items-center justify-center shadow-sm">
                        <span className="material-symbols-outlined text-white text-xl">person_add</span>
                    </div>
                    <div>
                        <h2 className="text-2xl font-bold text-gray-900 dark:text-white tracking-tight">Yêu cầu tham gia</h2>
                        <p className="text-sm text-gray-500 dark:text-gray-400">Duyệt yêu cầu gia nhập lớp</p>
                    </div>
                </div>
                <span className="px-3 py-1.5 text-xs font-bold bg-amber-100 dark:bg-amber-900/30 text-amber-600 dark:text-amber-400 rounded-full self-start">
                    {data.requests.length} yêu cầu chờ
                </span>
            </div>

            {/* ── Toast ── */}
            {toast && (
                <div className={`px-4 py-3 rounded-xl text-sm font-medium flex items-center gap-2 shadow-sm ${toast.type === 'success' ? 'bg-emerald-100 dark:bg-emerald-900/30 text-emerald-700 dark:text-emerald-400' : 'bg-red-100 dark:bg-red-900/30 text-red-700 dark:text-red-400'}`}>
                    <span className="material-symbols-outlined text-lg">{toast.type === 'success' ? 'check_circle' : 'error'}</span>
                    {toast.text}
                </div>
            )}

            {/* ── Join Code Card ── */}
            <div className="bg-white dark:bg-gray-900 rounded-2xl border border-gray-100 dark:border-gray-800 overflow-hidden">
                <div className="bg-gradient-to-r from-emerald-500 to-teal-500 px-6 py-4 flex items-center justify-between">
                    <div className="flex items-center gap-3">
                        <span className="material-symbols-outlined text-white text-xl">vpn_key</span>
                        <h3 className="text-white font-bold">Mã tham gia lớp</h3>
                    </div>
                    <button
                        onClick={() => setShowQr(v => !v)}
                        className="px-3 py-1.5 bg-white/20 hover:bg-white/30 text-white text-xs font-semibold rounded-lg transition-colors flex items-center gap-1.5 backdrop-blur-sm"
                    >
                        <span className="material-symbols-outlined text-base">{showQr ? 'pin' : 'qr_code_2'}</span>
                        {showQr ? 'Xem mã text' : 'Xem mã QR'}
                    </button>
                </div>
                <div className="p-6">
                    <p className="text-sm text-gray-500 dark:text-gray-400 mb-6 text-center">
                        Chia sẻ mã này cho sinh viên để họ có thể yêu cầu gia nhập lớp.
                    </p>

                    {showQr ? (
                        /* ── QR Code View ── */
                        <div className="flex flex-col items-center gap-5">
                            <div ref={qrRef} className="p-4 bg-white rounded-2xl border-2 border-dashed border-emerald-300 dark:border-emerald-700 shadow-sm">
                                {data.joinCode ? (
                                    <QRCodeCanvas
                                        value={data.joinCode}
                                        size={220}
                                        bgColor="#ffffff"
                                        fgColor="#059669"
                                        level="H"
                                        marginSize={2}
                                    />
                                ) : (
                                    <div className="w-[220px] h-[220px] bg-gray-100 dark:bg-gray-800 rounded-xl flex items-center justify-center">
                                        <span className="text-gray-300 text-sm">Không có mã</span>
                                    </div>
                                )}
                            </div>
                            <div className="text-center">
                                <p className="text-xs text-gray-400 dark:text-gray-500 mb-1">Mã tham gia</p>
                                <span className="text-2xl font-mono font-bold text-emerald-600 dark:text-emerald-400 tracking-[0.15em]">
                                    {data.joinCode || '------'}
                                </span>
                            </div>
                            <p className="text-xs text-gray-400 dark:text-gray-500 max-w-[280px] text-center">
                                Cho sinh viên quét mã QR này bằng camera trên trang tham gia lớp.
                            </p>
                            <div className="flex flex-wrap gap-2 justify-center">
                                <button onClick={downloadQr}
                                    className="px-4 py-2.5 bg-emerald-500 text-white text-sm font-semibold rounded-xl hover:bg-emerald-600 transition-colors flex items-center gap-2 shadow-sm">
                                    <span className="material-symbols-outlined text-lg">download</span>
                                    Tải mã QR
                                </button>
                                <button onClick={copyJoinCode}
                                    className="px-4 py-2.5 border border-gray-200 dark:border-gray-700 text-gray-600 dark:text-gray-300 text-sm font-medium rounded-xl hover:bg-gray-50 dark:hover:bg-gray-800 transition-colors flex items-center gap-2">
                                    <span className="material-symbols-outlined text-lg">{copied ? 'check' : 'content_copy'}</span>
                                    {copied ? 'Đã sao chép!' : 'Sao chép mã'}
                                </button>
                                <button onClick={handleRegenerate}
                                    className="px-4 py-2.5 border border-gray-200 dark:border-gray-700 text-gray-600 dark:text-gray-300 text-sm font-medium rounded-xl hover:bg-gray-50 dark:hover:bg-gray-800 transition-colors flex items-center gap-2">
                                    <span className="material-symbols-outlined text-lg">refresh</span>
                                    Tạo mã mới
                                </button>
                            </div>
                        </div>
                    ) : (
                        /* ── Text Code View ── */
                        <div className="flex flex-col items-center gap-5">
                            <div className="px-8 py-5 bg-emerald-50 dark:bg-emerald-900/20 border-2 border-dashed border-emerald-300 dark:border-emerald-700 rounded-2xl">
                                <span className="text-4xl font-mono font-bold text-emerald-600 dark:text-emerald-400 tracking-[0.2em]">
                                    {data.joinCode || '------'}
                                </span>
                            </div>
                            <div className="flex flex-wrap gap-2 justify-center">
                                <button onClick={copyJoinCode}
                                    className="px-4 py-2.5 bg-emerald-500 text-white text-sm font-semibold rounded-xl hover:bg-emerald-600 transition-colors flex items-center gap-2 shadow-sm">
                                    <span className="material-symbols-outlined text-lg">{copied ? 'check' : 'content_copy'}</span>
                                    {copied ? 'Đã sao chép!' : 'Sao chép'}
                                </button>
                                <button onClick={handleRegenerate}
                                    className="px-4 py-2.5 border border-gray-200 dark:border-gray-700 text-gray-600 dark:text-gray-300 text-sm font-medium rounded-xl hover:bg-gray-50 dark:hover:bg-gray-800 transition-colors flex items-center gap-2">
                                    <span className="material-symbols-outlined text-lg">refresh</span>
                                    Tạo mã mới
                                </button>
                            </div>
                        </div>
                    )}
                </div>
            </div>

            {/* ── Pending Requests Table ── */}
            <div className="bg-white dark:bg-gray-900 rounded-2xl border border-gray-100 dark:border-gray-800 overflow-hidden">
                <div className="px-6 py-4 border-b border-gray-100 dark:border-gray-800 flex items-center justify-between">
                    <div className="flex items-center gap-2">
                        <span className="material-symbols-outlined text-amber-500">pending_actions</span>
                        <h3 className="font-bold text-gray-900 dark:text-white">Yêu cầu chờ duyệt</h3>
                        <span className="px-2 py-0.5 text-xs font-bold bg-amber-100 dark:bg-amber-900/30 text-amber-600 dark:text-amber-400 rounded-full">{data.requests.length}</span>
                    </div>
                </div>
                {loading ? (
                    <div className="flex items-center justify-center py-16">
                        <div className="text-center">
                            <div className="w-8 h-8 border-3 border-blue-500/30 border-t-blue-500 rounded-full animate-spin mx-auto mb-3" />
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
                                    <th className="text-left px-6 py-3 text-[10px] font-bold text-gray-400 uppercase tracking-wider">Username</th>
                                    <th className="text-left px-6 py-3 text-[10px] font-bold text-gray-400 uppercase tracking-wider">Email</th>
                                    <th className="text-left px-6 py-3 text-[10px] font-bold text-gray-400 uppercase tracking-wider">Thời gian</th>
                                    <th className="text-center px-6 py-3 text-[10px] font-bold text-gray-400 uppercase tracking-wider">Thao tác</th>
                                </tr>
                            </thead>
                            <tbody className="divide-y divide-gray-50 dark:divide-gray-800">
                                {data.requests.length > 0 ? data.requests.map((r, i) => {
                                    const initials = (r.fullName || '?').split(' ').map(w => w[0]).join('').toUpperCase().slice(0, 2)
                                    return (
                                        <tr key={r.id} className="hover:bg-blue-50/40 dark:hover:bg-gray-800/50 transition-colors">
                                            <td className="px-6 py-3.5 text-gray-400 text-xs">{i + 1}</td>
                                            <td className="px-6 py-3.5">
                                                <div className="flex items-center gap-3">
                                                    <div className="w-10 h-10 rounded-xl bg-gradient-to-br from-amber-400 to-orange-500 flex items-center justify-center shrink-0">
                                                        <span className="text-xs font-bold text-white">{initials}</span>
                                                    </div>
                                                    <span className="font-medium text-gray-900 dark:text-white">{r.fullName}</span>
                                                </div>
                                            </td>
                                            <td className="px-6 py-3.5 text-gray-500 dark:text-gray-400 font-mono text-xs">{r.username}</td>
                                            <td className="px-6 py-3.5 text-gray-500 dark:text-gray-400 text-sm">{r.email}</td>
                                            <td className="px-6 py-3.5 text-gray-400 text-xs">{timeAgo(r.createdAt)}</td>
                                            <td className="px-6 py-3.5 text-center">
                                                <div className="flex items-center justify-center gap-1.5">
                                                    <button onClick={() => handleApprove(r.id)}
                                                        className="px-3.5 py-1.5 bg-emerald-500 text-white text-xs font-bold rounded-lg hover:bg-emerald-600 transition-colors flex items-center gap-1 shadow-sm">
                                                        <span className="material-symbols-outlined text-sm">check</span>Duyệt
                                                    </button>
                                                    <button onClick={() => handleReject(r.id)}
                                                        className="px-3.5 py-1.5 bg-red-500 text-white text-xs font-bold rounded-lg hover:bg-red-600 transition-colors flex items-center gap-1 shadow-sm">
                                                        <span className="material-symbols-outlined text-sm">close</span>Từ chối
                                                    </button>
                                                </div>
                                            </td>
                                        </tr>
                                    )
                                }) : (
                                    <tr>
                                        <td colSpan={6} className="px-6 py-16 text-center">
                                            <span className="material-symbols-outlined text-5xl text-gray-300 dark:text-gray-600 block mb-3">inbox</span>
                                            <p className="text-gray-400 font-medium">Không có yêu cầu nào đang chờ duyệt</p>
                                            <p className="text-xs text-gray-300 dark:text-gray-600 mt-1">Chia sẻ mã tham gia ở trên để sinh viên gửi yêu cầu</p>
                                        </td>
                                    </tr>
                                )}
                            </tbody>
                        </table>
                    </div>
                )}
            </div>
        </div>
    )
}
