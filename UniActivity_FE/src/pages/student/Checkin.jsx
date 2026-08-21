import { useState, useEffect, useRef } from 'react'
import { useParams, useSearchParams, NavLink, useOutletContext } from 'react-router-dom'
import { Html5Qrcode, Html5QrcodeSupportedFormats } from 'html5-qrcode'
import { isCompleteUserCode, normalizeUserCode } from '../../utils/userCode.js'

/* ===========================================
   CHECKIN PAGE — 2 chế độ:
   1. URL mode: /student/checkin/:activityId?classId=...
   2. Scanner mode: /student/checkin (không có activityId)
   Sau khi checkin thành công → form nộp minh chứng inline
   =========================================== */

export default function Checkin() {
    const { currentUser } = useOutletContext()
    const { activityId: paramActivityId } = useParams()
    const [searchParams] = useSearchParams()
    const paramClassId = searchParams.get('classId')

    const apiPrefix = currentUser?.role === 'MANAGER' ? '/manager/api' : '/student/api'
    const activitiesUrl = currentUser?.role === 'MANAGER' ? '/manager/api/my-activities' : '/student/api/activities'
    const myRegistrationsPath = currentUser?.role === 'MANAGER' ? '/manager/my-registrations' : '/student/my-registrations'
    const activitiesPath = currentUser?.role === 'MANAGER' ? '/manager/my-activities' : '/student/activities'
    const homePath = currentUser?.role === 'MANAGER' ? '/manager/dashboard' : '/student/home'

    /* ---------- STATE ---------- */
    const [mode, setMode] = useState(paramActivityId ? 'url' : 'loading') // 'loading' | 'scanner' | 'url' | 'checkin' | 'evidence' | 'done' | 'blocked'
    const [scannerTab, setScannerTab] = useState('qr') // 'qr' | 'code'
    const [manualCode, setManualCode] = useState('')
    const [selectedActivityIdForCode, setSelectedActivityIdForCode] = useState('')
    const [activityId, setActivityId] = useState(paramActivityId || null)
    const [classId, setClassId] = useState(paramClassId || null)
    const [qrToken, setQrToken] = useState(searchParams.get('token') || null) // Dynamic QR token
    const [loading, setLoading] = useState(!!paramActivityId)
    const [activity, setActivity] = useState(null)
    const [registration, setRegistration] = useState(null)
    const [checkinState, setCheckinState] = useState(null)
    const [checkinLoading, setCheckinLoading] = useState(false)
    const [result, setResult] = useState(null)
    const [blockReason, setBlockReason] = useState(null) // { type, title, message }
    const [registeredActivities, setRegisteredActivities] = useState([]) // for display in scanner mode

    /* Evidence state */
    const [scoreOptions, setScoreOptions] = useState([])
    const [selectedScoreOption, setSelectedScoreOption] = useState('')
    const [evidenceFiles, setEvidenceFiles] = useState([])
    const [previews, setPreviews] = useState([])
    const [uploading, setUploading] = useState(false)
    const [evidenceResult, setEvidenceResult] = useState(null)

    /* ---------- PRE-CHECK: verify class + registrations before showing scanner ---------- */
    useEffect(() => {
        if (paramActivityId) return // skip if coming from URL with activityId
        if (!currentUser) return
        const preCheck = async () => {
            try {
                const res = await fetch(`${apiPrefix}/my-registrations`, { credentials: 'include' })
                if (!res.ok) throw new Error('Lỗi tải dữ liệu')
                const data = await res.json()

                if (!data.hasClass) {
                    setBlockReason({
                        type: 'no_class',
                        title: 'Bạn chưa tham gia lớp',
                        message: 'Vui lòng tham gia lớp học trước khi check-in hoạt động.',
                    })
                    setMode('blocked')
                    return
                }

                // Filter registrations that can be checked in (REGISTERED status)
                const canCheckin = (data.registrations || []).filter(r => r.status === 'REGISTERED')
                if (canCheckin.length === 0) {
                    setBlockReason({
                        type: 'no_registration',
                        title: 'Bạn chưa đăng ký hoạt động nào',
                        message: 'Bạn cần đăng ký tham gia hoạt động trước khi có thể quét mã QR check-in.',
                    })
                    setMode('blocked')
                    return
                }

                setRegisteredActivities(canCheckin)
                if (canCheckin.length > 0 && !selectedActivityIdForCode) {
                    setSelectedActivityIdForCode(String(canCheckin[0].activity?.id || ''))
                }
                setMode('scanner')
            } catch (e) {
                setBlockReason({
                    type: 'error',
                    title: 'Lỗi tải dữ liệu',
                    message: e.message,
                })
                setMode('blocked')
            }
        }
        preCheck()
    }, [paramActivityId, currentUser, apiPrefix])

    /* ---------- LOAD DATA (khi có activityId) ---------- */
    useEffect(() => {
        if (!activityId || !currentUser) return
        const init = async () => {
            setLoading(true)
            try {
                const [actRes, regRes] = await Promise.all([
                    fetch(activitiesUrl, { credentials: 'include' }),
                    fetch(`${apiPrefix}/my-registrations`, { credentials: 'include' }),
                ])
                if (!actRes.ok || !regRes.ok) throw new Error('Không thể tải dữ liệu')
                const actData = await actRes.json()
                const regData = await regRes.json()

                if (!actData.hasClass) { setCheckinState('no_class'); setLoading(false); setMode('checkin'); return }

                const act = actData.activities?.find(a => String(a.id) === String(activityId))
                if (act) setActivity(act)

                const reg = regData.registrations?.find(r => String(r.activity?.id) === String(activityId))
                setRegistration(reg)

                if (!reg) setCheckinState('not_registered')
                else if (reg.status === 'ATTENDED') setCheckinState('already')
                else if (reg.status === 'CANCELLED') setCheckinState('cancelled')
                else setCheckinState('can_checkin')

                setMode('checkin')
            } catch (e) {
                setCheckinState('error')
                setResult({ type: 'error', message: e.message })
                setMode('checkin')
            } finally {
                setLoading(false)
            }
        }
        init()
    }, [activityId, currentUser, apiPrefix, activitiesUrl])

    /* ---------- HANDLE QR SCAN RESULT ---------- */
    const handleScanned = (text) => {
        // QR content: http://host:port/student/checkin/{activityId}?classId={classId}&token={token}
        try {
            const url = new URL(text)
            const match = url.pathname.match(/\/student\/checkin\/(\d+)/)
            if (!match) throw new Error('invalid')
            setActivityId(match[1])
            setClassId(url.searchParams.get('classId'))
            setQrToken(url.searchParams.get('token')) // Extract dynamic token
        } catch {
            // If not a URL, try plain number
            const num = text.trim()
            if (/^\d+$/.test(num)) {
                setActivityId(num)
            } else {
                setResult({ type: 'error', message: 'Mã QR không hợp lệ. Vui lòng quét mã QR check-in hoạt động.' })
            }
        }
    }

    const handleManualCodeSubmit = (e) => {
        e.preventDefault()
        const cleanCode = normalizeUserCode(manualCode)
        if (!isCompleteUserCode(cleanCode)) {
            setResult({ type: 'error', message: 'Vui lòng nhập đủ 6 ký tự chữ và số của mã check-in' })
            return
        }
        if (!selectedActivityIdForCode) {
            setResult({ type: 'error', message: 'Vui lòng chọn hoạt động cần check-in' })
            return
        }
        setActivityId(selectedActivityIdForCode)
        const userClassId = currentUser?.studentClass?.id ? String(currentUser.studentClass.id) : null
        setClassId(userClassId)
        setQrToken(cleanCode)
        handleCheckin(selectedActivityIdForCode, userClassId, cleanCode)
    }

    /* ---------- CHECKIN ---------- */
    const handleCheckin = async (overrideActId = null, overrideClassId = null, overrideToken = null) => {
        const targetActId = overrideActId || activityId
        const targetClassId = overrideClassId || classId || (currentUser?.studentClass?.id ? String(currentUser.studentClass.id) : null)
        const targetToken = overrideToken || qrToken

        setCheckinLoading(true)
        try {
            // 1. Lấy vị trí GPS trước khi check-in
            let gpsLat = null, gpsLng = null, gpsAccuracy = null
            try {
                const pos = await new Promise((resolve, reject) => {
                    if (!navigator.geolocation) {
                        reject(new Error('Trình duyệt không hỗ trợ GPS'))
                        return
                    }
                    navigator.geolocation.getCurrentPosition(resolve, reject, {
                        enableHighAccuracy: true,
                        timeout: 15000,
                        maximumAge: 0,
                    })
                })
                gpsLat = pos.coords.latitude
                gpsLng = pos.coords.longitude
                gpsAccuracy = pos.coords.accuracy
            } catch (gpsErr) {
                // GPS failed — vẫn gửi request (backend sẽ kiểm tra nếu activity yêu cầu GPS)
                console.warn('GPS unavailable:', gpsErr.message)
            }

            // 2. Build URL với classId + dynamic token + GPS
            let url = `${apiPrefix}/checkin/${targetActId}`
            const params = new URLSearchParams()
            if (targetClassId) params.set('classId', targetClassId)
            if (targetToken) params.set('token', targetToken)
            if (gpsLat != null) params.set('lat', gpsLat)
            if (gpsLng != null) params.set('lng', gpsLng)
            if (gpsAccuracy != null) params.set('accuracy', gpsAccuracy)
            if (params.toString()) url += '?' + params.toString()

            const res = await fetch(url, { method: 'POST', credentials: 'include' })
            const data = await res.json()
            if (!res.ok) {
                // Handle specific error types
                if (data.expired) {
                    setResult({ type: 'error', message: data.message || 'Mã QR hoặc mã check-in đã hết hạn. Vui lòng thử mã mới.' })
                } else if (data.gpsRequired) {
                    setResult({ type: 'error', message: '📍 ' + (data.message || 'Hoạt động này yêu cầu GPS. Vui lòng cấp quyền vị trí.') })
                } else if (data.gpsInaccurate) {
                    setResult({ type: 'error', message: '📡 ' + (data.message || 'Tín hiệu GPS không chính xác. Vui lòng thử lại.') })
                } else if (data.tooFar) {
                    setResult({ type: 'error', message: '🗺️ ' + (data.message || 'Bạn ở ngoài phạm vi check-in.') })
                } else {
                    throw new Error(data.message || 'Lỗi check-in')
                }
                return
            }
            setResult({ type: 'success', message: data.message })
            setCheckinState('already')
            setMode('checkin')
            loadScoreOptions(targetActId)
        } catch (e) {
            setResult({ type: 'error', message: e.message })
        } finally {
            setCheckinLoading(false)
        }
    }

    /* ---------- LOAD SCORE OPTIONS ---------- */
    const loadScoreOptions = async (actId = null) => {
        const targetId = actId || activityId
        try {
            const res = await fetch(`${apiPrefix}/activities/${targetId}/score-options`, { credentials: 'include' })
            if (res.ok) setScoreOptions(await res.json())
        } catch {
            setScoreOptions([])
        }
    }

    /* ---------- FILE PREVIEW ---------- */
    const handleFileChange = (e) => {
        const files = [...e.target.files].slice(0, 3)
        setEvidenceFiles(files)
        const urls = files.map(f => URL.createObjectURL(f))
        previews.forEach(u => URL.revokeObjectURL(u))
        setPreviews(urls)
    }

    const removeFile = (idx) => {
        URL.revokeObjectURL(previews[idx])
        setEvidenceFiles(prev => prev.filter((_, i) => i !== idx))
        setPreviews(prev => prev.filter((_, i) => i !== idx))
    }

    /* ---------- SUBMIT EVIDENCE ---------- */
    const handleSubmitEvidence = async () => {
        if (!selectedScoreOption || evidenceFiles.length === 0) {
            setEvidenceResult({ type: 'error', message: 'Vui lòng chọn mục điểm và ảnh minh chứng' })
            return
        }
        if (evidenceFiles.length > 3) {
            setEvidenceResult({ type: 'error', message: 'Tối đa 3 ảnh' })
            return
        }
        setUploading(true)
        setEvidenceResult(null)
        try {
            const fd = new FormData()
            fd.append('scoreOptionId', selectedScoreOption)
            evidenceFiles.forEach(f => fd.append('files', f))
            const res = await fetch(`${apiPrefix}/activities/${activityId}/evidence`, {
                method: 'POST', credentials: 'include', body: fd,
            })
            const json = await res.json()
            if (!res.ok) throw new Error(json.message || 'Lỗi gửi minh chứng')
            setEvidenceResult({ type: 'success', message: json.message || 'Gửi minh chứng thành công! Manager sẽ duyệt.' })
            setMode('done')
        } catch (e) {
            setEvidenceResult({ type: 'error', message: e.message })
        } finally {
            setUploading(false)
        }
    }

    /* ---------- GO TO EVIDENCE FORM ---------- */
    const goToEvidence = () => {
        loadScoreOptions()
        setMode('evidence')
    }

    /* =========================================
       RENDER
       ========================================= */

    if (loading) return <Loading text="Đang xác thực check-in..." />

    return (
        <div className="max-w-lg mx-auto space-y-6 pb-12">
            {/* Header */}
            <div className="text-center">
                <div className="size-16 rounded-2xl bg-gradient-to-br from-emerald-500 to-teal-500 flex items-center justify-center mx-auto mb-4 shadow-lg shadow-emerald-500/20">
                    <span className="material-symbols-outlined text-white text-3xl">qr_code_scanner</span>
                </div>
                <h2 className="text-2xl font-bold text-gray-900 dark:text-white">Check-in hoạt động</h2>
                <p className="text-sm text-gray-500 dark:text-gray-400 mt-1">
                    {mode === 'scanner' ? 'Quét mã QR để check-in' : mode === 'evidence' ? 'Nộp minh chứng tham gia' : mode === 'loading' ? 'Đang kiểm tra...' : mode === 'blocked' ? '' : 'Xác nhận tham gia hoạt động'}
                </p>
            </div>

            {/* ===== LOADING MODE (pre-check) ===== */}
            {mode === 'loading' && <Loading text="Đang kiểm tra đăng ký hoạt động..." />}

            {/* ===== BLOCKED MODE ===== */}
            {mode === 'blocked' && blockReason && (
                <div className="bg-white dark:bg-gray-900 rounded-2xl border border-gray-200 dark:border-gray-700 shadow-sm p-8 text-center">
                    <StateIcon
                        icon={blockReason.type === 'no_class' ? 'school' : blockReason.type === 'no_registration' ? 'event_busy' : 'error'}
                        color={blockReason.type === 'error' ? 'red' : 'amber'}
                    />
                    <h3 className="text-lg font-bold text-gray-900 dark:text-white mb-2">{blockReason.title}</h3>
                    <p className="text-sm text-gray-500 dark:text-gray-400 mb-6">{blockReason.message}</p>
                    <div className="flex flex-col sm:flex-row gap-3 justify-center">
                        {blockReason.type === 'no_class' && (
                            <NavLink to={homePath} className="inline-flex items-center justify-center gap-2 px-6 py-3 bg-emerald-500 text-white font-bold rounded-xl hover:bg-emerald-600 transition-colors">
                                <span className="material-symbols-outlined text-lg">home</span> Tham gia lớp
                            </NavLink>
                        )}
                        {blockReason.type === 'no_registration' && (
                            <NavLink to={activitiesPath} className="inline-flex items-center justify-center gap-2 px-6 py-3 bg-emerald-500 text-white font-bold rounded-xl hover:bg-emerald-600 transition-colors">
                                <span className="material-symbols-outlined text-lg">event</span> Đăng ký hoạt động
                            </NavLink>
                        )}
                    </div>
                </div>
            )}

            {/* ===== SCANNER MODE ===== */}
            {mode === 'scanner' && (
                <>
                    {/* Tabs: Quét QR / Nhập mã 6 ký tự */}
                    <div className="flex rounded-2xl bg-gray-100 dark:bg-gray-800 p-1 mb-4">
                        <button
                            onClick={() => setScannerTab('qr')}
                            className={`flex-1 py-2.5 px-4 rounded-xl text-sm font-bold transition-all flex items-center justify-center gap-2 ${
                                scannerTab === 'qr'
                                    ? 'bg-white dark:bg-gray-900 text-emerald-600 dark:text-emerald-400 shadow-sm'
                                    : 'text-gray-500 hover:text-gray-800 dark:hover:text-gray-200'
                            }`}
                        >
                            <span className="material-symbols-outlined text-lg">qr_code_scanner</span>
                            Quét camera QR
                        </button>
                        <button
                            onClick={() => setScannerTab('code')}
                            className={`flex-1 py-2.5 px-4 rounded-xl text-sm font-bold transition-all flex items-center justify-center gap-2 ${
                                scannerTab === 'code'
                                    ? 'bg-white dark:bg-gray-900 text-emerald-600 dark:text-emerald-400 shadow-sm'
                                    : 'text-gray-500 hover:text-gray-800 dark:hover:text-gray-200'
                            }`}
                        >
                            <span className="material-symbols-outlined text-lg">pin</span>
                            Nhập mã 6 ký tự
                        </button>
                    </div>

                    {/* Registered activities hint */}
                    {registeredActivities.length > 0 && scannerTab === 'qr' && (
                        <div className="bg-blue-50 dark:bg-blue-900/20 border border-blue-200 dark:border-blue-800 rounded-2xl p-4 mb-4">
                            <p className="text-xs font-semibold text-blue-600 dark:text-blue-400 mb-2 flex items-center gap-1.5">
                                <span className="material-symbols-outlined text-sm">info</span>
                                Bạn có {registeredActivities.length} hoạt động chờ check-in
                            </p>
                            <div className="space-y-1.5">
                                {registeredActivities.slice(0, 3).map(r => (
                                    <div key={r.id} className="text-xs text-blue-700 dark:text-blue-300 flex items-center gap-1.5">
                                        <span className="material-symbols-outlined text-xs">event</span>
                                        <span className="truncate">{r.activity?.name}</span>
                                    </div>
                                ))}
                                {registeredActivities.length > 3 && (
                                    <p className="text-xs text-blue-400">...và {registeredActivities.length - 3} hoạt động khác</p>
                                )}
                            </div>
                        </div>
                    )}

                    {scannerTab === 'qr' ? (
                        <QrScannerCard onScanned={handleScanned} />
                    ) : (
                        <div className="bg-white dark:bg-gray-900 rounded-2xl border border-gray-200 dark:border-gray-700 shadow-sm p-6">
                            <div className="text-center mb-6">
                                <div className="size-12 rounded-2xl bg-emerald-100 dark:bg-emerald-900/30 text-emerald-600 dark:text-emerald-400 flex items-center justify-center mx-auto mb-3">
                                    <span className="material-symbols-outlined text-2xl">pin</span>
                                </div>
                                <h3 className="text-base font-bold text-gray-900 dark:text-white">Nhập mã check-in 6 ký tự</h3>
                                <p className="text-xs text-gray-500 dark:text-gray-400 mt-1">
                                    Nhập mã hiển thị ngay bên dưới mã QR của ban tổ chức (mã đổi mỗi 1 phút).
                                </p>
                            </div>

                            <form onSubmit={handleManualCodeSubmit} className="space-y-4 max-w-sm mx-auto">
                                <div>
                                    <label className="block text-xs font-bold text-gray-700 dark:text-gray-300 mb-1.5">
                                        Hoạt động
                                    </label>
                                    <select
                                        value={selectedActivityIdForCode}
                                        onChange={e => setSelectedActivityIdForCode(e.target.value)}
                                        className="w-full px-3 py-2.5 rounded-xl border border-gray-300 dark:border-gray-700 bg-white dark:bg-gray-800 text-sm text-gray-800 dark:text-white focus:ring-2 focus:ring-emerald-500"
                                        required
                                    >
                                        {registeredActivities.map(r => (
                                            <option key={r.id} value={r.activity?.id}>
                                                {r.activity?.name}
                                            </option>
                                        ))}
                                    </select>
                                </div>

                                <div>
                                    <label
                                        htmlFor="manual-checkin-code"
                                        className="block text-xs font-bold text-gray-700 dark:text-gray-300 mb-1.5"
                                    >
                                        Mã check-in (6 ký tự)
                                    </label>
                                    <input
                                        id="manual-checkin-code"
                                        name="checkinCode"
                                        type="text"
                                        inputMode="text"
                                        autoCapitalize="characters"
                                        autoComplete="off"
                                        spellCheck={false}
                                        maxLength={6}
                                        value={manualCode}
                                        onChange={e => setManualCode(normalizeUserCode(e.target.value))}
                                        placeholder="A7K9P2"
                                        className="w-full text-center text-2xl font-mono font-bold tracking-widest px-4 py-3 rounded-xl border border-gray-300 dark:border-gray-700 bg-white dark:bg-gray-800 text-gray-900 dark:text-white focus:ring-2 focus:ring-emerald-500"
                                        required
                                    />
                                </div>

                                <button
                                    type="submit"
                                    disabled={!isCompleteUserCode(manualCode) || checkinLoading}
                                    className="w-full py-3.5 px-4 bg-emerald-500 hover:bg-emerald-600 text-white font-bold text-sm rounded-xl transition-all shadow-md shadow-emerald-500/20 disabled:opacity-40 disabled:cursor-not-allowed flex items-center justify-center gap-2"
                                >
                                    {checkinLoading ? (
                                        <><div className="w-4 h-4 border-2 border-white/50 border-t-white rounded-full animate-spin" /> Đang xác thực...</>
                                    ) : (
                                        <><span className="material-symbols-outlined text-lg">verified</span> Xác thực & Check-in</>
                                    )}
                                </button>
                            </form>
                        </div>
                    )}

                    {result && <Toast result={result} />}
                </>
            )}

            {/* ===== CHECKIN MODE (after activity identified) ===== */}
            {mode === 'checkin' && (
                <>
                    {activity && <ActivityInfoCard activity={activity} />}
                    {result && <Toast result={result} />}

                    <div className="bg-white dark:bg-gray-900 rounded-2xl border border-gray-200 dark:border-gray-700 shadow-sm p-8 text-center">
                        {checkinState === 'can_checkin' && (
                            <>
                                <StateIcon icon="touch_app" color="emerald" />
                                <h3 className="text-lg font-bold text-gray-900 dark:text-white mb-2">Sẵn sàng check-in!</h3>
                                <p className="text-sm text-gray-500 dark:text-gray-400 mb-6">Nhấn nút bên dưới để xác nhận tham gia hoạt động này.</p>
                                <button
                                    onClick={handleCheckin}
                                    disabled={checkinLoading}
                                    className="w-full py-4 px-6 bg-gradient-to-r from-emerald-500 to-teal-500 text-white font-bold text-lg rounded-2xl hover:shadow-lg hover:shadow-emerald-500/30 transition-all disabled:opacity-50 flex items-center justify-center gap-3"
                                >
                                    {checkinLoading ? (
                                        <><div className="w-5 h-5 border-2 border-white/50 border-t-white rounded-full animate-spin" /> Đang check-in...</>
                                    ) : (
                                        <><span className="material-symbols-outlined text-xl">check_circle</span> Check-in ngay</>
                                    )}
                                </button>
                            </>
                        )}

                        {checkinState === 'already' && result?.type === 'success' && (
                            <>
                                <StateIcon icon="celebration" color="emerald" />
                                <h3 className="text-lg font-bold text-emerald-600 dark:text-emerald-400 mb-2">Check-in thành công!</h3>
                                <p className="text-sm text-gray-500 dark:text-gray-400 mb-6">Tiến hành nộp minh chứng để manager duyệt điểm.</p>
                                <button
                                    onClick={goToEvidence}
                                    className="w-full py-4 px-6 bg-gradient-to-r from-blue-500 to-indigo-500 text-white font-bold text-lg rounded-2xl hover:shadow-lg hover:shadow-blue-500/30 transition-all flex items-center justify-center gap-3"
                                >
                                    <span className="material-symbols-outlined text-xl">upload_file</span>
                                    Nộp minh chứng ngay
                                </button>
                            </>
                        )}

                        {checkinState === 'already' && !result && (
                            <>
                                <StateIcon icon="verified" color="blue" />
                                <h3 className="text-lg font-bold text-gray-900 dark:text-white mb-2">Đã check-in rồi!</h3>
                                <p className="text-sm text-gray-500 dark:text-gray-400 mb-6">Bạn đã check-in hoạt động này trước đó.</p>
                                {registration && !registration.evidenceUrl ? (
                                    <button
                                        onClick={goToEvidence}
                                        className="w-full py-3 px-6 bg-amber-500 text-white font-bold rounded-2xl hover:bg-amber-600 transition-all flex items-center justify-center gap-2"
                                    >
                                        <span className="material-symbols-outlined text-lg">photo_camera</span>
                                        Nộp minh chứng
                                    </button>
                                ) : registration?.evidenceUrl ? (
                                    <div className="space-y-3">
                                        <p className="text-sm text-emerald-500 font-medium flex items-center justify-center gap-1">
                                            <span className="material-symbols-outlined text-lg">check_circle</span>
                                            Đã nộp minh chứng{registration.isApproved === null ? ' — đang chờ duyệt' : registration.isApproved ? ' — đã duyệt' : ''}
                                        </p>
                                        {registration.isApproved === false && (
                                            <button
                                                onClick={goToEvidence}
                                                className="px-5 py-2 border-2 border-amber-500 text-amber-600 font-bold rounded-xl hover:bg-amber-500 hover:text-white transition-all"
                                            >
                                                Nộp lại
                                            </button>
                                        )}
                                    </div>
                                ) : null}
                            </>
                        )}

                        {checkinState === 'not_registered' && (
                            <>
                                <StateIcon icon="warning" color="amber" />
                                <h3 className="text-lg font-bold text-gray-900 dark:text-white mb-2">Bạn chưa đăng ký</h3>
                                <p className="text-sm text-gray-500 dark:text-gray-400 mb-6">Bạn cần đăng ký hoạt động này trước khi check-in.</p>
                                <NavLink to={activitiesPath} className="inline-flex items-center gap-2 px-6 py-3 bg-emerald-500 text-white font-bold rounded-xl hover:bg-emerald-600 transition-colors">
                                    <span className="material-symbols-outlined text-lg">event</span> Xem hoạt động
                                </NavLink>
                            </>
                        )}

                        {checkinState === 'cancelled' && (
                            <>
                                <StateIcon icon="block" color="gray" />
                                <h3 className="text-lg font-bold text-gray-900 dark:text-white mb-2">Đăng ký đã bị hủy</h3>
                                <p className="text-sm text-gray-500 dark:text-gray-400">Bạn đã hủy đăng ký hoạt động này nên không thể check-in.</p>
                            </>
                        )}

                        {checkinState === 'no_class' && (
                            <>
                                <StateIcon icon="school" color="amber" />
                                <h3 className="text-lg font-bold text-gray-900 dark:text-white mb-2">Bạn chưa tham gia lớp</h3>
                                <p className="text-sm text-gray-500 dark:text-gray-400 mb-6">Vui lòng tham gia lớp trước khi check-in hoạt động.</p>
                                <NavLink to={homePath} className="inline-flex items-center gap-2 px-6 py-3 bg-emerald-500 text-white font-bold rounded-xl">
                                    <span className="material-symbols-outlined text-lg">home</span> Về trang chủ
                                </NavLink>
                            </>
                        )}

                        {checkinState === 'error' && (
                            <>
                                <StateIcon icon="error" color="red" />
                                <h3 className="text-lg font-bold text-gray-900 dark:text-white mb-2">Có lỗi xảy ra</h3>
                                <p className="text-sm text-gray-500 dark:text-gray-400">Không thể tải thông tin check-in. Vui lòng thử lại.</p>
                            </>
                        )}
                    </div>
                </>
            )}

            {/* ===== EVIDENCE MODE ===== */}
            {mode === 'evidence' && (
                <>
                    {activity && <ActivityInfoCard activity={activity} />}

                    <div className="bg-white dark:bg-gray-900 rounded-2xl border border-gray-200 dark:border-gray-700 shadow-sm overflow-hidden">
                        <div className="px-6 py-4 border-b border-gray-200 dark:border-gray-700 bg-gradient-to-r from-amber-500/5 to-orange-500/5">
                            <h3 className="font-bold text-gray-900 dark:text-white flex items-center gap-2">
                                <span className="material-symbols-outlined text-amber-500">photo_camera</span>
                                Nộp minh chứng tham gia
                            </h3>
                            <p className="text-xs text-gray-400 mt-1">Chọn mục điểm và tải lên ảnh minh chứng (tối đa 3 ảnh)</p>
                        </div>

                        <div className="p-6 space-y-5">
                            {/* Score option selection */}
                            <div>
                                <label className="block text-sm font-semibold text-gray-700 dark:text-gray-300 mb-2">
                                    Chọn mục điểm rèn luyện <span className="text-red-500">*</span>
                                </label>
                                {scoreOptions.length > 0 ? (
                                    <div className="space-y-2">
                                        {scoreOptions.map(opt => (
                                            <label
                                                key={opt.id}
                                                className={`flex items-center gap-3 p-3 rounded-xl border-2 cursor-pointer transition-all ${
                                                    selectedScoreOption === String(opt.id)
                                                        ? 'border-emerald-500 bg-emerald-50 dark:bg-emerald-900/20'
                                                        : 'border-gray-200 dark:border-gray-700 hover:border-gray-300 dark:hover:border-gray-600'
                                                }`}
                                            >
                                                <input
                                                    type="radio"
                                                    name="scoreOption"
                                                    value={opt.id}
                                                    checked={selectedScoreOption === String(opt.id)}
                                                    onChange={(e) => setSelectedScoreOption(e.target.value)}
                                                    className="accent-emerald-500"
                                                />
                                                <div className="flex-1">
                                                    <span className="text-sm font-medium text-gray-900 dark:text-white">{opt.name}</span>
                                                    <span className="ml-2 text-xs text-gray-400">Mục {opt.scoreCategory}</span>
                                                </div>
                                                <span className="text-sm font-bold text-emerald-600 dark:text-emerald-400">+{opt.scoreValue}đ</span>
                                            </label>
                                        ))}
                                    </div>
                                ) : (
                                    <p className="text-sm text-gray-400 italic">Không có mục điểm nào cho hoạt động này.</p>
                                )}
                            </div>

                            {/* File upload */}
                            <div>
                                <label className="block text-sm font-semibold text-gray-700 dark:text-gray-300 mb-2">
                                    Ảnh minh chứng (tối đa 3) <span className="text-red-500">*</span>
                                </label>
                                <label className="flex flex-col items-center justify-center w-full h-32 border-2 border-dashed border-gray-300 dark:border-gray-600 rounded-xl cursor-pointer hover:border-emerald-400 hover:bg-emerald-50/50 dark:hover:bg-emerald-900/10 transition-all">
                                    <span className="material-symbols-outlined text-3xl text-gray-400 mb-1">cloud_upload</span>
                                    <span className="text-sm text-gray-500">Nhấn để chọn ảnh</span>
                                    <span className="text-xs text-gray-400 mt-0.5">JPG, PNG, WebP — Tối đa 5MB/ảnh</span>
                                    <input
                                        type="file"
                                        accept="image/*"
                                        multiple
                                        onChange={handleFileChange}
                                        className="hidden"
                                    />
                                </label>

                                {/* Preview grid */}
                                {previews.length > 0 && (
                                    <div className="grid grid-cols-3 gap-3 mt-3">
                                        {previews.map((url, i) => (
                                            <div key={i} className="relative group">
                                                <img src={url} alt={`Preview ${i + 1}`} className="w-full h-24 object-cover rounded-xl border border-gray-200 dark:border-gray-700" />
                                                <button
                                                    onClick={() => removeFile(i)}
                                                    className="absolute -top-2 -right-2 size-6 bg-red-500 text-white rounded-full flex items-center justify-center opacity-0 group-hover:opacity-100 transition-opacity shadow-lg"
                                                >
                                                    <span className="material-symbols-outlined text-sm">close</span>
                                                </button>
                                            </div>
                                        ))}
                                    </div>
                                )}
                            </div>

                            {evidenceResult && <Toast result={evidenceResult} />}
                        </div>

                        <div className="px-6 py-4 border-t border-gray-200 dark:border-gray-700 flex gap-3">
                            <button
                                onClick={() => setMode('checkin')}
                                className="px-4 py-2.5 text-sm font-medium text-gray-600 dark:text-gray-300 border border-gray-300 dark:border-gray-600 rounded-xl hover:bg-gray-50 dark:hover:bg-gray-800"
                            >
                                Quay lại
                            </button>
                            <button
                                onClick={handleSubmitEvidence}
                                disabled={uploading || !selectedScoreOption || evidenceFiles.length === 0}
                                className="flex-1 py-2.5 text-sm font-bold bg-emerald-500 text-white rounded-xl hover:bg-emerald-600 transition-colors disabled:opacity-50 flex items-center justify-center gap-2"
                            >
                                {uploading ? (
                                    <><div className="w-4 h-4 border-2 border-white/50 border-t-white rounded-full animate-spin" /> Đang gửi...</>
                                ) : (
                                    <><span className="material-symbols-outlined text-lg">send</span> Gửi minh chứng cho Manager</>
                                )}
                            </button>
                        </div>
                    </div>
                </>
            )}

            {/* ===== DONE MODE ===== */}
            {mode === 'done' && (
                <div className="bg-white dark:bg-gray-900 rounded-2xl border border-gray-200 dark:border-gray-700 shadow-sm p-8 text-center">
                    <div className="size-20 rounded-full bg-emerald-50 dark:bg-emerald-900/20 flex items-center justify-center mx-auto mb-5">
                        <span className="material-symbols-outlined text-4xl text-emerald-500">task_alt</span>
                    </div>
                    <h3 className="text-xl font-bold text-emerald-600 dark:text-emerald-400 mb-2">Hoàn thành!</h3>
                    <p className="text-sm text-gray-500 dark:text-gray-400 mb-6">
                        Minh chứng đã được gửi. Manager sẽ duyệt và cộng điểm rèn luyện cho bạn.
                    </p>
                    {evidenceResult && <Toast result={evidenceResult} />}
                    <div className="flex flex-col sm:flex-row gap-3 mt-6 justify-center">
                        <NavLink
                            to={myRegistrationsPath}
                            className="inline-flex items-center justify-center gap-2 px-5 py-3 bg-blue-500 text-white font-bold rounded-xl hover:bg-blue-600 transition-colors"
                        >
                            <span className="material-symbols-outlined text-lg">list_alt</span>
                            Xem lịch sử đăng ký
                        </NavLink>
                        <NavLink
                            to={homePath}
                            className="inline-flex items-center justify-center gap-2 px-5 py-3 border border-gray-300 dark:border-gray-600 text-gray-600 dark:text-gray-300 font-bold rounded-xl hover:bg-gray-50 dark:hover:bg-gray-800 transition-colors"
                        >
                            <span className="material-symbols-outlined text-lg">home</span>
                            Về trang chủ
                        </NavLink>
                    </div>
                </div>
            )}

            {/* Back link */}
            <div className="text-center">
                <NavLink to={myRegistrationsPath} className="text-sm text-gray-400 hover:text-gray-600 dark:hover:text-gray-300 transition-colors flex items-center gap-1 justify-center">
                    <span className="material-symbols-outlined text-base">arrow_back</span>
                    Quay lại Lịch sử đăng ký
                </NavLink>
            </div>
        </div>
    )
}

/* ==========================================
   SUB-COMPONENTS
   ========================================== */

function Loading({ text }) {
    return (
        <div className="flex items-center justify-center h-[70vh]">
            <div className="text-center">
                <div className="w-12 h-12 border-3 border-emerald-500/30 border-t-emerald-500 rounded-full animate-spin mx-auto mb-4" />
                <p className="text-sm text-gray-400">{text || 'Đang tải...'}</p>
            </div>
        </div>
    )
}

function Toast({ result }) {
    const isOk = result.type === 'success'
    return (
        <div className={`px-5 py-4 rounded-2xl text-sm font-medium flex items-center gap-3 shadow-sm ${isOk
            ? 'bg-emerald-50 dark:bg-emerald-900/20 text-emerald-700 dark:text-emerald-400 border border-emerald-200 dark:border-emerald-800/50'
            : 'bg-red-50 dark:bg-red-900/20 text-red-700 dark:text-red-400 border border-red-200 dark:border-red-800/50'
            }`}>
            <span className={`material-symbols-outlined text-xl ${isOk ? 'text-emerald-500' : 'text-red-500'}`}>
                {isOk ? 'check_circle' : 'error'}
            </span>
            {result.message}
        </div>
    )
}

function StateIcon({ icon, color }) {
    const colorMap = {
        emerald: 'bg-emerald-50 dark:bg-emerald-900/20 text-emerald-500',
        blue: 'bg-blue-50 dark:bg-blue-900/20 text-blue-500',
        amber: 'bg-amber-50 dark:bg-amber-900/20 text-amber-500',
        red: 'bg-red-50 dark:bg-red-900/20 text-red-500',
        gray: 'bg-gray-100 dark:bg-gray-800 text-gray-400',
    }
    return (
        <div className={`size-20 rounded-full ${colorMap[color]} flex items-center justify-center mx-auto mb-5`}>
            <span className="material-symbols-outlined text-4xl">{icon}</span>
        </div>
    )
}

function ActivityInfoCard({ activity }) {
    return (
        <div className="bg-white dark:bg-gray-900 rounded-2xl border border-gray-200 dark:border-gray-700 shadow-sm overflow-hidden">
            {activity.bannerUrl && (
                <img src={activity.bannerUrl} alt={activity.name} className="w-full h-40 object-cover" />
            )}
            <div className="p-5">
                <h3 className="font-bold text-lg text-gray-900 dark:text-white">{activity.name}</h3>
                <div className="flex flex-wrap items-center gap-x-4 gap-y-2 mt-3 text-sm text-gray-500 dark:text-gray-400">
                    {activity.location && (
                        <span className="flex items-center gap-1.5">
                            <span className="material-symbols-outlined text-base text-emerald-500">location_on</span>
                            {activity.location}
                        </span>
                    )}
                    {activity.startTime && (
                        <span className="flex items-center gap-1.5">
                            <span className="material-symbols-outlined text-base text-blue-500">schedule</span>
                            {(() => { const d = new Date(activity.startTime), p = n => String(n).padStart(2, '0'); return `${p(d.getDate())}/${p(d.getMonth() + 1)}/${d.getFullYear()} ${p(d.getHours())}:${p(d.getMinutes())}` })()}
                        </span>
                    )}
                </div>
            </div>
        </div>
    )
}

/* ---- QR Scanner Card (camera-based) ---- */
function QrScannerCard({ onScanned }) {
    const [active, setActive] = useState(false)
    const [error, setError] = useState(null)
    const containerRef = useRef(null)
    const scannerRef = useRef(null)
    const mountedRef = useRef(false)

    // Camera list & selection states
    const [devices, setDevices] = useState([])
    const [selectedDeviceId, setSelectedDeviceId] = useState('')
    const activeDeviceIdRef = useRef('')

    const startScanner = () => {
        setActive(true)
        setError(null)
    }


    useEffect(() => {
        if (!active) return
        mountedRef.current = true
        let html5Qr = null

        const init = async () => {
            const el = containerRef.current
            if (!el || !mountedRef.current) return

            // Clean leftover DOM from StrictMode double-mount
            el.replaceChildren()

            try {
                // Step 1: Enumerate cameras FIRST (also triggers permission)
                console.log('[QR-Checkin] Enumerating cameras...')
                const cameras = await Html5Qrcode.getCameras()
                console.log('[QR-Checkin] Found cameras:', cameras.map(c => `${c.label} (${c.id})`))
                setDevices(cameras)

                if (!cameras.length) {
                    setError('Không tìm thấy camera nào.')
                    setActive(false)
                    return
                }
                if (!mountedRef.current) return

                // Step 2: Pick camera - prefer selected, virtual webcams (Camo, Iriun), or back camera on mobile
                let cameraId = selectedDeviceId
                if (!cameraId) {
                    const virtualCam = cameras.find(c => /camo|iriun|droidcam|virtual/i.test(c.label))
                    const backCam = cameras.find(c => /back|rear|environment/i.test(c.label))
                    if (virtualCam) cameraId = virtualCam.id
                    else if (backCam) cameraId = backCam.id
                    else cameraId = cameras[0].id
                }

                // If this camera is already active, don't restart
                if (activeDeviceIdRef.current === cameraId && scannerRef.current?.isScanning) {
                    return
                }

                activeDeviceIdRef.current = cameraId
                console.log('[QR-Checkin] Starting camera:', cameraId)

                // Step 3: Create scanner and start with camera ID, restricting format to QR_CODE only for maximum speed
                html5Qr = new Html5Qrcode(el.id, {
                    verbose: false,
                    formatsToSupport: [Html5QrcodeSupportedFormats.QR_CODE]
                })
                scannerRef.current = html5Qr

                await html5Qr.start(
                    cameraId,
                    {
                        fps: 20, // Increase frame rate for speed and sensitivity (default is 10)
                        qrbox: (viewfinderWidth, viewfinderHeight) => {
                            const minEdge = Math.min(viewfinderWidth, viewfinderHeight)
                            const size = Math.floor(minEdge * 0.7) // 70% of viewfinder
                            return { width: size, height: size }
                        },
                        disableFlip: false,
                        experimentalFeatures: {
                            useBarCodeDetectorIfSupported: true // Use hardware-accelerated GPU scanner if supported
                        }
                    },
                    (decodedText) => {
                        if (!mountedRef.current) return
                        mountedRef.current = false
                        const code = decodedText.trim()
                        console.log('[QR-Checkin] ✅ Decoded:', code)
                        if (html5Qr && html5Qr.isScanning) {
                            html5Qr.stop().then(() => onScanned(code)).catch(() => onScanned(code))
                        } else {
                            onScanned(code)
                        }
                    },
                    () => {} // per-frame error (ignore)
                )
                
                setSelectedDeviceId(cameraId)
                console.log('[QR-Checkin] ✅ Scanner running, waiting for QR code...')
            } catch (err) {
                console.error('[QR-Checkin] ❌ Error:', err)
                if (mountedRef.current) {
                    setError('Không thể mở camera: ' + (err?.message || err))
                    setActive(false)
                }
            }
        }

        const timer = setTimeout(init, 300)
        return () => {
            clearTimeout(timer)
            mountedRef.current = false
            const s = scannerRef.current
            scannerRef.current = null
            if (s) {
                try { if (s.isScanning) s.stop().catch(() => {}) } catch { /* Scanner already stopped. */ }
            }
            activeDeviceIdRef.current = ''
        }
    }, [active, selectedDeviceId]) // eslint-disable-line react-hooks/exhaustive-deps

    if (!active) {
        return (
            <div className="bg-white dark:bg-gray-900 rounded-2xl border border-gray-200 dark:border-gray-700 shadow-sm p-8 text-center">
                <div className="size-24 rounded-3xl bg-gradient-to-br from-emerald-500/10 to-teal-500/10 flex items-center justify-center mx-auto mb-5">
                    <span className="material-symbols-outlined text-5xl text-emerald-500">qr_code_scanner</span>
                </div>
                <h3 className="text-lg font-bold text-gray-900 dark:text-white mb-2">Quét mã QR hoạt động</h3>
                <p className="text-sm text-gray-500 dark:text-gray-400 mb-6 max-w-xs mx-auto">
                    Hướng camera vào mã QR mà Manager hiển thị để check-in hoạt động.
                </p>
                {error && (
                    <div className="mb-4 px-4 py-2 bg-red-50 dark:bg-red-900/20 text-red-600 dark:text-red-400 rounded-xl text-sm">
                        {error}
                    </div>
                )}
                <button
                    onClick={startScanner}
                    className="w-full py-4 px-6 bg-gradient-to-r from-emerald-500 to-teal-500 text-white font-bold text-lg rounded-2xl hover:shadow-lg hover:shadow-emerald-500/30 transition-all flex items-center justify-center gap-3"
                >
                    <span className="material-symbols-outlined text-xl">photo_camera</span>
                    Mở Camera quét QR
                </button>
            </div>
        )
    }

    return (
        <div className="bg-white dark:bg-gray-900 rounded-2xl border border-gray-200 dark:border-gray-700 shadow-sm overflow-hidden">
            <div className="p-4">
                <div className="relative overflow-hidden rounded-xl">
                    <div ref={containerRef} id="activity-qr-reader" className="w-full rounded-xl overflow-hidden bg-black" style={{ minHeight: 300 }} />
                    {/* Laser Scanning Overlay Target */}
                    <div className="absolute inset-0 pointer-events-none flex items-center justify-center">
                        <div className="w-56 h-56 border border-emerald-500/25 rounded-2xl relative">
                            {/* Target Corners */}
                            <div className="absolute -top-1 -left-1 w-5 h-5 border-t-4 border-l-4 border-emerald-500 rounded-tl-lg" />
                            <div className="absolute -top-1 -right-1 w-5 h-5 border-t-4 border-r-4 border-emerald-500 rounded-tr-lg" />
                            <div className="absolute -bottom-1 -left-1 w-5 h-5 border-b-4 border-l-4 border-emerald-500 rounded-bl-lg" />
                            <div className="absolute -bottom-1 -right-1 w-5 h-5 border-b-4 border-r-4 border-emerald-500 rounded-br-lg" />
                            {/* Scanning Laser Line */}
                            <div className="w-full h-0.5 bg-gradient-to-r from-transparent via-emerald-400 to-transparent absolute left-0 animate-scanner-laser shadow-md shadow-emerald-400/50" />
                        </div>
                    </div>
                </div>
                {error && (
                    <div className="mt-3 px-4 py-2 bg-red-50 dark:bg-red-900/20 text-red-600 dark:text-red-400 rounded-xl text-sm text-center">
                        {error}
                    </div>
                )}
                <p className="text-xs text-gray-400 text-center mt-3">
                    Hướng camera vào mã QR check-in hoạt động
                </p>
                <p className="text-[11px] text-amber-500 dark:text-amber-400 font-semibold text-center mt-2 flex items-center justify-center gap-1 max-w-sm mx-auto">
                    <span className="material-symbols-outlined text-sm shrink-0">info</span>
                    Mẹo: Hãy đưa điện thoại ra xa camera một chút (khoảng 20-30cm). Khi để xa, camera mới có thể lấy nét rõ ràng, giúp quét được mã ngay lập tức.
                </p>
            </div>

            {/* Camera source selector */}
            {devices.length > 1 && (
                <div className="px-4 pb-3">
                    <label className="block text-xs font-semibold text-gray-500 dark:text-gray-400 mb-1.5 flex items-center gap-1">
                        <span className="material-symbols-outlined text-sm">photo_camera</span> Chọn nguồn Camera:
                    </label>
                    <select
                        value={selectedDeviceId}
                        onChange={(e) => setSelectedDeviceId(e.target.value)}
                        className="w-full h-10 px-3 rounded-xl border border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-800 text-sm text-gray-800 dark:text-white focus:outline-none focus:ring-2 focus:ring-emerald-500/30 transition-colors"
                    >
                        {devices.map(d => (
                            <option key={d.id} value={d.id}>
                                {d.label || `Camera ${d.id.slice(0, 5)}...`}
                            </option>
                        ))}
                    </select>
                </div>
            )}

            <div className="px-4 pb-4">
                <button
                    onClick={() => setActive(false)}
                    className="w-full py-2.5 text-sm font-medium text-gray-500 border border-gray-300 dark:border-gray-600 rounded-xl hover:bg-gray-50 dark:hover:bg-gray-800 transition-colors"
                >
                    Tắt camera
                </button>
            </div>
        </div>
    )
}
