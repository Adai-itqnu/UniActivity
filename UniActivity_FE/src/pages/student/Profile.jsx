import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'

const CATEGORY_NAMES = ['', 'Ý thức học tập', 'Ý thức chấp hành', 'Hoạt động CT-XH', 'Quan hệ cộng đồng', 'Phẩm chất công dân', 'Thành tích đặc biệt']
const CATEGORY_COLORS = ['', '#3b82f6', '#10b981', '#06b6d4', '#f59e0b', '#8b5cf6', '#ef4444']

function getClassColor(cls) {
    if (!cls) return '#6b7280'
    const l = cls.toLowerCase()
    if (l.includes('xuất sắc')) return '#10b981'
    if (l.includes('tốt')) return '#3b82f6'
    if (l.includes('khá')) return '#06b6d4'
    if (l.includes('trung bình')) return '#f59e0b'
    if (l.includes('yếu')) return '#f97316'
    if (l.includes('kém')) return '#ef4444'
    return '#6b7280'
}

export default function Profile() {
    const navigate = useNavigate()
    const [profile, setProfile] = useState(null)
    const [loading, setLoading] = useState(true)
    const [scoreData, setScoreData] = useState(null)

    // Edit profile
    const [editMode, setEditMode] = useState(false)
    const [fullName, setFullName] = useState('')
    const [phone, setPhone] = useState('')
    const [saving, setSaving] = useState(false)

    // Change password
    const [showPwForm, setShowPwForm] = useState(false)
    const [currentPassword, setCurrentPassword] = useState('')
    const [newPassword, setNewPassword] = useState('')
    const [confirmPassword, setConfirmPassword] = useState('')
    const [changingPw, setChangingPw] = useState(false)
    const [showCurrentPw, setShowCurrentPw] = useState(false)
    const [showNewPw, setShowNewPw] = useState(false)

    // Toast
    const [toast, setToast] = useState(null)

    // Email verification
    const [showVerifyModal, setShowVerifyModal] = useState(false)
    const [verifyOtp, setVerifyOtp] = useState('')
    const [sendingVerify, setSendingVerify] = useState(false)
    const [verifying, setVerifying] = useState(false)
    const [verifyCooldown, setVerifyCooldown] = useState(0)

    const showToast = (type, text) => {
        setToast({ type, text })
        setTimeout(() => setToast(null), 4000)
    }

    // Countdown timer cho nút gửi lại OTP
    useEffect(() => {
        if (verifyCooldown <= 0) return
        const timer = setTimeout(() => setVerifyCooldown(verifyCooldown - 1), 1000)
        return () => clearTimeout(timer)
    }, [verifyCooldown])

    const handleSendVerifyEmail = async () => {
        if (!profile?.email) return
        setSendingVerify(true)
        try {
            const res = await fetch('/api/auth/send-verification-email', {
                method: 'POST', credentials: 'include',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ email: profile.email }),
            })
            const data = await res.json()
            if (!res.ok) throw new Error(data.message)
            showToast('success', data.message)
            setShowVerifyModal(true)
            setVerifyOtp('')
            setVerifyCooldown(60)
        } catch (e) {
            showToast('error', e.message)
        } finally {
            setSendingVerify(false)
        }
    }

    const handleVerifyEmail = async () => {
        if (!verifyOtp.trim()) { showToast('error', 'Vui lòng nhập mã OTP'); return }
        setVerifying(true)
        try {
            const res = await fetch('/api/auth/verify-email', {
                method: 'POST', credentials: 'include',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ email: profile.email, otp: verifyOtp.trim() }),
            })
            const data = await res.json()
            if (!res.ok) throw new Error(data.message)
            showToast('success', data.message)
            setShowVerifyModal(false)
            setVerifyOtp('')
            fetchProfile()
        } catch (e) {
            showToast('error', e.message)
        } finally {
            setVerifying(false)
        }
    }

    const fetchProfile = () => {
        setLoading(true)
        fetch('/api/profile', { credentials: 'include' })
            .then(r => { if (!r.ok) throw new Error('Lỗi'); return r.json() })
            .then(data => {
                setProfile(data)
                setFullName(data.fullName || '')
                setPhone(data.phone || '')
                setLoading(false)

                // Fetch score data based on role
                const apiPrefix = data.role === 'MANAGER' ? '/manager/api' : '/student/api'
                fetch(`${apiPrefix}/my-scores`, { credentials: 'include' })
                    .then(r => { if (!r.ok) throw new Error(); return r.json() })
                    .then(setScoreData)
                    .catch(() => {})
            })
            .catch(() => setLoading(false))
    }

    useEffect(() => { fetchProfile() }, [])

    const handleSaveProfile = async () => {
        if (!fullName.trim()) {
            showToast('error', 'Họ tên không được để trống')
            return
        }
        setSaving(true)
        try {
            const res = await fetch('/api/profile', {
                method: 'PUT', credentials: 'include',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ fullName: fullName.trim(), phone: phone.trim() }),
            })
            const data = await res.json()
            if (!res.ok) throw new Error(data.message)
            showToast('success', data.message)
            setEditMode(false)
            fetchProfile()
        } catch (e) {
            showToast('error', e.message)
        } finally {
            setSaving(false)
        }
    }

    const handleChangePassword = async () => {
        if (!currentPassword) { showToast('error', 'Vui lòng nhập mật khẩu hiện tại'); return }
        if (!newPassword) { showToast('error', 'Vui lòng nhập mật khẩu mới'); return }
        if (newPassword.length < 6) { showToast('error', 'Mật khẩu mới phải có ít nhất 6 ký tự'); return }
        if (newPassword !== confirmPassword) { showToast('error', 'Mật khẩu xác nhận không khớp'); return }

        setChangingPw(true)
        try {
            const res = await fetch('/api/profile/password', {
                method: 'PUT', credentials: 'include',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ currentPassword, newPassword, confirmPassword }),
            })
            const data = await res.json()
            if (!res.ok) throw new Error(data.message)
            showToast('success', data.message)
            setShowPwForm(false)
            setCurrentPassword('')
            setNewPassword('')
            setConfirmPassword('')
        } catch (e) {
            showToast('error', e.message)
        } finally {
            setChangingPw(false)
        }
    }

    if (loading) {
        return (
            <div className="flex items-center justify-center h-[60vh]">
                <div className="text-center">
                    <div className="w-10 h-10 border-3 border-emerald-500/30 border-t-emerald-500 rounded-full animate-spin mx-auto mb-4" />
                    <p className="text-sm text-gray-400">Đang tải dữ liệu...</p>
                </div>
            </div>
        )
    }

    if (!profile) return null

    const isGoogleAccount = profile.provider === 'GOOGLE'
    const initials = (profile.fullName || '?')
        .split(' ').map(w => w[0]).join('').toUpperCase().slice(0, 2)

    const roleName = { ADMIN: 'Quản trị viên', MANAGER: 'Quản lý lớp', STUDENT: 'Sinh viên' }[profile.role] || profile.role

    return (
        <div className="space-y-6 max-w-4xl mx-auto">

            {/* Page Header */}
            <div className="flex items-center gap-3">
                <div className="size-10 rounded-xl bg-emerald-500/10 flex items-center justify-center">
                    <span className="material-symbols-outlined text-emerald-500">person</span>
                </div>
                <div>
                    <h2 className="text-2xl font-bold text-gray-900 dark:text-white">Hồ sơ cá nhân</h2>
                    <p className="text-sm text-gray-500 dark:text-gray-400">Quản lý thông tin tài khoản của bạn</p>
                </div>
            </div>

            {/* Toast */}
            {toast && (
                <div className={`px-4 py-3 rounded-xl text-sm font-medium flex items-center gap-2 animate-in ${toast.type === 'success' ? 'bg-emerald-100 dark:bg-emerald-900/30 text-emerald-700 dark:text-emerald-400' : 'bg-red-100 dark:bg-red-900/30 text-red-700 dark:text-red-400'}`}>
                    <span className="material-symbols-outlined text-lg">{toast.type === 'success' ? 'check_circle' : 'error'}</span>
                    {toast.text}
                </div>
            )}

            {/* ===== Email Verification Modal ===== */}
            {showVerifyModal && (
                <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-sm" onClick={() => setShowVerifyModal(false)}>
                    <div className="bg-white dark:bg-gray-900 rounded-2xl border border-gray-200 dark:border-gray-700 shadow-2xl w-full max-w-md mx-4 p-6" onClick={e => e.stopPropagation()}>
                        <div className="flex items-center gap-3 mb-4">
                            <div className="size-10 rounded-xl bg-emerald-500/10 flex items-center justify-center">
                                <span className="material-symbols-outlined text-emerald-500">mark_email_read</span>
                            </div>
                            <div>
                                <h3 className="text-lg font-bold text-gray-900 dark:text-white">Xác thực Email</h3>
                                <p className="text-xs text-gray-400">Mã OTP đã được gửi đến {profile.email}</p>
                            </div>
                        </div>

                        <div className="space-y-4">
                            <div>
                                <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1.5">
                                    Nhập mã OTP (6 chữ số)
                                </label>
                                <input
                                    type="text"
                                    maxLength={6}
                                    value={verifyOtp}
                                    onChange={e => setVerifyOtp(e.target.value.replace(/\D/g, ''))}
                                    className="w-full px-4 py-3 text-center text-2xl font-mono font-bold tracking-[0.5em] rounded-xl border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-800 text-gray-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-emerald-500/50 focus:border-emerald-500"
                                    placeholder="000000"
                                    autoFocus
                                    onKeyDown={e => e.key === 'Enter' && verifyOtp.length === 6 && handleVerifyEmail()}
                                />
                            </div>

                            <div className="flex items-center gap-3">
                                <button
                                    onClick={handleVerifyEmail}
                                    disabled={verifying || verifyOtp.length !== 6}
                                    className="flex-1 px-5 py-2.5 bg-emerald-500 text-white font-bold rounded-xl text-sm hover:bg-emerald-600 transition-colors disabled:opacity-50 flex items-center justify-center gap-2"
                                >
                                    {verifying ? (
                                        <>
                                            <div className="w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin" />
                                            Đang xác thực...
                                        </>
                                    ) : (
                                        <>
                                            <span className="material-symbols-outlined text-lg">verified</span>
                                            Xác thực
                                        </>
                                    )}
                                </button>
                                <button
                                    onClick={() => setShowVerifyModal(false)}
                                    className="px-5 py-2.5 border border-gray-300 dark:border-gray-600 text-gray-600 dark:text-gray-300 font-medium rounded-xl text-sm hover:bg-gray-50 dark:hover:bg-gray-800 transition-colors"
                                >
                                    Đóng
                                </button>
                            </div>

                            <div className="text-center">
                                {verifyCooldown > 0 ? (
                                    <p className="text-xs text-gray-400">Gửi lại mã sau <span className="font-bold text-emerald-500">{verifyCooldown}s</span></p>
                                ) : (
                                    <button
                                        onClick={handleSendVerifyEmail}
                                        disabled={sendingVerify}
                                        className="text-xs text-emerald-500 font-medium hover:underline disabled:opacity-50"
                                    >
                                        {sendingVerify ? 'Đang gửi...' : 'Gửi lại mã OTP'}
                                    </button>
                                )}
                            </div>
                        </div>
                    </div>
                </div>
            )}

            {/* ===== Profile Card ===== */}
            <div className="bg-white dark:bg-gray-900 rounded-2xl border border-gray-200 dark:border-gray-700 shadow-sm overflow-hidden">
                {/* Banner */}
                <div className="h-32 bg-gradient-to-r from-emerald-500 via-teal-500 to-cyan-500 relative">
                    <div className="absolute inset-0 bg-[url('data:image/svg+xml;base64,PHN2ZyB3aWR0aD0iNDAiIGhlaWdodD0iNDAiIHhtbG5zPSJodHRwOi8vd3d3LnczLm9yZy8yMDAwL3N2ZyI+PGNpcmNsZSBjeD0iMjAiIGN5PSIyMCIgcj0iMiIgZmlsbD0id2hpdGUiIG9wYWNpdHk9IjAuMSIvPjwvc3ZnPg==')] opacity-50" />
                </div>

                <div className="px-6 pb-6 relative">
                    {/* Avatar */}
                    <div className="relative -mt-14 mb-4 flex items-end gap-5">
                        {profile.avatarUrl ? (
                            <img src={profile.avatarUrl} alt={profile.fullName} className="w-24 h-24 rounded-2xl object-cover border-4 border-white dark:border-gray-900 shadow-lg" />
                        ) : (
                            <div className="w-24 h-24 rounded-2xl bg-gradient-to-br from-emerald-400 to-teal-500 border-4 border-white dark:border-gray-900 shadow-lg flex items-center justify-center">
                                <span className="text-3xl font-bold text-white">{initials}</span>
                            </div>
                        )}
                        <div className="pb-1">
                            <h3 className="text-xl font-bold text-gray-900 dark:text-white">{profile.fullName}</h3>
                            <div className="flex items-center gap-2 mt-1">
                                <span className="px-2.5 py-0.5 text-xs font-bold bg-emerald-100 dark:bg-emerald-900/30 text-emerald-600 dark:text-emerald-400 rounded-full">
                                    {roleName}
                                </span>
                                {isGoogleAccount && (
                                    <span className="px-2.5 py-0.5 text-xs font-bold bg-blue-100 dark:bg-blue-900/30 text-blue-600 dark:text-blue-400 rounded-full flex items-center gap-1">
                                        <svg className="w-3 h-3" viewBox="0 0 24 24"><path fill="currentColor" d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92a5.06 5.06 0 0 1-2.2 3.32v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.1z"/><path fill="currentColor" d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z"/><path fill="currentColor" d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z"/><path fill="currentColor" d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z"/></svg>
                                        Google
                                    </span>
                                )}
                                <span className={`px-2.5 py-0.5 text-xs font-bold rounded-full ${profile.status === 'ACTIVE' ? 'bg-emerald-500 text-white' : 'bg-red-100 dark:bg-red-900/30 text-red-500'}`}>
                                    {profile.status === 'ACTIVE' ? 'Hoạt động' : profile.status}
                                </span>
                            </div>
                        </div>
                    </div>

                    {/* Info Grid */}
                    <div className="grid grid-cols-1 md:grid-cols-2 gap-4 mb-6">
                        <InfoField icon="badge" label="Tên đăng nhập" value={profile.username} />
                        <div className="flex items-start gap-3 p-3 bg-gray-50 dark:bg-gray-800/50 rounded-xl">
                            <div className="size-9 rounded-lg bg-white dark:bg-gray-700 flex items-center justify-center shrink-0 border border-gray-200 dark:border-gray-600">
                                <span className="material-symbols-outlined text-gray-500 dark:text-gray-400 text-lg">mail</span>
                            </div>
                            <div className="min-w-0 flex-1">
                                <p className="text-xs text-gray-400 uppercase tracking-wider">Email</p>
                                <p className="text-sm font-medium mt-0.5 text-gray-900 dark:text-white truncate">{profile.email}</p>
                                {profile.emailVerified ? (
                                    <span className="inline-flex items-center gap-1 mt-1.5 px-2 py-0.5 text-xs font-bold bg-emerald-100 dark:bg-emerald-900/30 text-emerald-600 dark:text-emerald-400 rounded-full">
                                        <span className="material-symbols-outlined text-xs">verified</span>
                                        Đã xác thực
                                    </span>
                                ) : (
                                    <button
                                        onClick={handleSendVerifyEmail}
                                        disabled={sendingVerify}
                                        className="inline-flex items-center gap-1 mt-1.5 px-2 py-0.5 text-xs font-bold bg-amber-100 dark:bg-amber-900/30 text-amber-600 dark:text-amber-400 rounded-full hover:bg-amber-200 dark:hover:bg-amber-900/50 transition-colors cursor-pointer disabled:opacity-50"
                                    >
                                        <span className="material-symbols-outlined text-xs">warning</span>
                                        {sendingVerify ? 'Đang gửi...' : 'Chưa xác thực — Nhấn để xác thực'}
                                    </button>
                                )}
                            </div>
                        </div>
                        <InfoField icon="phone" label="Số điện thoại" value={profile.phone || 'Chưa cập nhật'} muted={!profile.phone} />
                        <InfoField icon="calendar_today" label="Ngày tạo tài khoản" value={profile.createdAt ? (() => { const d = new Date(profile.createdAt), p = n => String(n).padStart(2, '0'); return `${p(d.getDate())}/${p(d.getMonth() + 1)}/${d.getFullYear()}` })() : 'N/A'} />
                        {profile.studentClass && (
                            <>
                                <InfoField icon="school" label="Lớp" value={`${profile.studentClass.name} (${profile.studentClass.code})`} />
                                <InfoField icon="domain" label="Khoa" value={profile.studentClass.facultyName || 'N/A'} />
                            </>
                        )}
                    </div>

                    {/* Edit toggle */}
                    {!editMode && (
                        <button
                            onClick={() => setEditMode(true)}
                            className="px-5 py-2.5 bg-emerald-500 text-white font-medium rounded-xl text-sm hover:bg-emerald-600 transition-colors flex items-center gap-2"
                        >
                            <span className="material-symbols-outlined text-lg">edit</span>
                            Chỉnh sửa thông tin
                        </button>
                    )}
                </div>
            </div>

            {/* ===== Training Score Chart ===== */}
            {scoreData && (
                <div className="bg-white dark:bg-gray-900 rounded-2xl border border-gray-200 dark:border-gray-700 shadow-sm overflow-hidden">
                    <div className="px-6 py-4 border-b border-gray-200 dark:border-gray-700 flex items-center justify-between">
                        <div className="flex items-center gap-2">
                            <span className="material-symbols-outlined text-purple-500">star</span>
                            <h3 className="font-bold text-gray-900 dark:text-white">Phân phối điểm rèn luyện</h3>
                        </div>
                        <span className="text-sm text-gray-400">{scoreData.currentSemester?.name || 'Tổng điểm tích lũy học kỳ này'}</span>
                    </div>
                    <div className="p-6">
                        <div className="flex items-center gap-8">
                            {/* Donut */}
                            <div className="shrink-0">
                                <ScoreDonut categoryTotals={scoreData.categoryTotals} totalScore={scoreData.totalScore} classification={scoreData.classification} />
                            </div>
                            {/* Legend + Total */}
                            <div className="flex-1 min-w-0">
                                <div className="space-y-2">
                                    {[1, 2, 3, 4, 5, 6].map(i => (
                                        <div key={i} className="flex items-center justify-between text-sm">
                                            <div className="flex items-center gap-2.5 min-w-0">
                                                <span className="size-2.5 rounded-full shrink-0" style={{ backgroundColor: CATEGORY_COLORS[i] }} />
                                                <span className="text-gray-600 dark:text-gray-300 truncate">{CATEGORY_NAMES[i]}</span>
                                            </div>
                                            <span className="font-bold text-gray-800 dark:text-white ml-4 tabular-nums">{scoreData.categoryTotals?.[i] || 0}</span>
                                        </div>
                                    ))}
                                </div>
                                {/* Total */}
                                <div className="pt-3 mt-3 border-t border-gray-200 dark:border-gray-700 flex items-end justify-between">
                                    <div>
                                        <span className="text-3xl font-black text-gray-900 dark:text-white">{scoreData.totalScore}</span>
                                        <span className="text-sm font-normal text-gray-400 ml-1">pts</span>
                                    </div>
                                    <div className="flex items-center gap-3">
                                        {/* Progress bar */}
                                        <div className="w-24 h-2 bg-gray-200 dark:bg-gray-700 rounded-full overflow-hidden">
                                            <div className="h-full rounded-full transition-all duration-500" style={{ width: `${Math.min(scoreData.totalScore, 100)}%`, backgroundColor: getClassColor(scoreData.classification) }} />
                                        </div>
                                        <span className="text-xs font-bold px-2.5 py-1 rounded-full" style={{ color: getClassColor(scoreData.classification), backgroundColor: `${getClassColor(scoreData.classification)}18` }}>
                                            {scoreData.classification || 'Chưa xếp loại'}
                                        </span>
                                    </div>
                                </div>
                                {/* Detail link */}
                                <button onClick={() => navigate(profile?.role === 'MANAGER' ? '/manager/my-scores' : '/student/my-scores')} className="mt-3 text-sm text-emerald-500 font-medium hover:underline flex items-center gap-1">
                                    Chi tiết →
                                </button>
                            </div>
                        </div>
                    </div>
                </div>
            )}

            {/* ===== Edit Info Form ===== */}
            {editMode && (
                <div className="bg-white dark:bg-gray-900 rounded-2xl border border-gray-200 dark:border-gray-700 shadow-sm overflow-hidden">
                    <div className="px-6 py-4 border-b border-gray-200 dark:border-gray-700 flex items-center gap-2">
                        <span className="material-symbols-outlined text-emerald-500">edit_note</span>
                        <h3 className="font-bold text-gray-900 dark:text-white">Chỉnh sửa thông tin</h3>
                    </div>
                    <div className="p-6 space-y-5">
                        {/* Full Name */}
                        <div>
                            <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1.5">
                                Họ và tên <span className="text-red-500">*</span>
                            </label>
                            <div className="relative">
                                <span className="material-symbols-outlined absolute left-3 top-1/2 -translate-y-1/2 text-gray-400 text-lg">person</span>
                                <input
                                    type="text"
                                    value={fullName}
                                    onChange={e => setFullName(e.target.value)}
                                    className="w-full pl-10 pr-4 py-2.5 rounded-xl border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-800 text-gray-900 dark:text-white text-sm focus:outline-none focus:ring-2 focus:ring-emerald-500/50 focus:border-emerald-500"
                                    placeholder="Nhập họ và tên"
                                />
                            </div>
                        </div>

                        {/* Phone */}
                        <div>
                            <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1.5">
                                Số điện thoại
                            </label>
                            <div className="relative">
                                <span className="material-symbols-outlined absolute left-3 top-1/2 -translate-y-1/2 text-gray-400 text-lg">phone</span>
                                <input
                                    type="tel"
                                    value={phone}
                                    onChange={e => setPhone(e.target.value)}
                                    className="w-full pl-10 pr-4 py-2.5 rounded-xl border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-800 text-gray-900 dark:text-white text-sm focus:outline-none focus:ring-2 focus:ring-emerald-500/50 focus:border-emerald-500"
                                    placeholder="VD: 0901234567"
                                />
                            </div>
                            <p className="mt-1 text-xs text-gray-400">Để trống nếu không muốn cung cấp</p>
                        </div>

                        {/* Read-only fields notice */}
                        <div className="px-4 py-3 bg-blue-50 dark:bg-blue-900/10 border border-blue-200 dark:border-blue-800/50 rounded-xl text-xs text-blue-600 dark:text-blue-400 flex items-start gap-2">
                            <span className="material-symbols-outlined text-sm mt-0.5">info</span>
                            <span>Tên đăng nhập và Email không thể thay đổi. Liên hệ Quản trị viên nếu cần cập nhật.</span>
                        </div>

                        {/* Actions */}
                        <div className="flex items-center gap-3 pt-2">
                            <button
                                onClick={handleSaveProfile}
                                disabled={saving}
                                className="px-6 py-2.5 bg-emerald-500 text-white font-bold rounded-xl text-sm hover:bg-emerald-600 transition-colors disabled:opacity-50 flex items-center gap-2"
                            >
                                {saving ? (
                                    <>
                                        <div className="w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin" />
                                        Đang lưu...
                                    </>
                                ) : (
                                    <>
                                        <span className="material-symbols-outlined text-lg">save</span>
                                        Lưu thay đổi
                                    </>
                                )}
                            </button>
                            <button
                                onClick={() => { setEditMode(false); setFullName(profile.fullName || ''); setPhone(profile.phone || '') }}
                                className="px-5 py-2.5 border border-gray-300 dark:border-gray-600 text-gray-600 dark:text-gray-300 font-medium rounded-xl text-sm hover:bg-gray-50 dark:hover:bg-gray-800 transition-colors"
                            >
                                Hủy
                            </button>
                        </div>
                    </div>
                </div>
            )}

            {/* ===== Change Password Section ===== */}
            <div className="bg-white dark:bg-gray-900 rounded-2xl border border-gray-200 dark:border-gray-700 shadow-sm overflow-hidden">
                <div className="px-6 py-4 border-b border-gray-200 dark:border-gray-700 flex items-center justify-between">
                    <div className="flex items-center gap-2">
                        <span className="material-symbols-outlined text-amber-500">lock</span>
                        <h3 className="font-bold text-gray-900 dark:text-white">Mật khẩu & Bảo mật</h3>
                    </div>
                    {!showPwForm && !isGoogleAccount && (
                        <button
                            onClick={() => setShowPwForm(true)}
                            className="px-4 py-2 text-sm font-medium border border-gray-300 dark:border-gray-600 text-gray-600 dark:text-gray-300 rounded-xl hover:bg-gray-50 dark:hover:bg-gray-800 transition-colors flex items-center gap-2"
                        >
                            <span className="material-symbols-outlined text-lg">key</span>
                            Đổi mật khẩu
                        </button>
                    )}
                </div>

                <div className="p-6">
                    {isGoogleAccount ? (
                        /* Google account notice */
                        <div className="flex items-start gap-4 p-4 bg-blue-50 dark:bg-blue-900/10 border border-blue-200 dark:border-blue-800/50 rounded-xl">
                            <div className="size-10 rounded-xl bg-blue-100 dark:bg-blue-900/30 flex items-center justify-center shrink-0">
                                <svg className="w-5 h-5 text-blue-500" viewBox="0 0 24 24"><path fill="currentColor" d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92a5.06 5.06 0 0 1-2.2 3.32v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.1z"/><path fill="currentColor" d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z"/><path fill="currentColor" d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z"/><path fill="currentColor" d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z"/></svg>
                            </div>
                            <div>
                                <h4 className="text-sm font-bold text-blue-700 dark:text-blue-400">Tài khoản Google</h4>
                                <p className="text-xs text-blue-600/80 dark:text-blue-400/80 mt-1">
                                    Bạn đang đăng nhập bằng Google. Mật khẩu được quản lý bởi Google — hãy đổi mật khẩu tại trang quản lý tài khoản Google của bạn.
                                </p>
                            </div>
                        </div>
                    ) : !showPwForm ? (
                        /* Default: show last changed info */
                        <div className="flex items-center gap-4">
                            <div className="size-10 rounded-xl bg-emerald-500/10 flex items-center justify-center">
                                <span className="material-symbols-outlined text-emerald-500">shield</span>
                            </div>
                            <div>
                                <p className="text-sm font-medium text-gray-900 dark:text-white">Mật khẩu đã được thiết lập</p>
                                <p className="text-xs text-gray-400 mt-0.5">Nhấn "Đổi mật khẩu" để cập nhật mật khẩu mới</p>
                            </div>
                        </div>
                    ) : (
                        /* Password change form */
                        <div className="space-y-4 max-w-md">
                            {/* Current password */}
                            <div>
                                <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1.5">
                                    Mật khẩu hiện tại <span className="text-red-500">*</span>
                                </label>
                                <div className="relative">
                                    <span className="material-symbols-outlined absolute left-3 top-1/2 -translate-y-1/2 text-gray-400 text-lg">lock</span>
                                    <input
                                        type={showCurrentPw ? 'text' : 'password'}
                                        value={currentPassword}
                                        onChange={e => setCurrentPassword(e.target.value)}
                                        className="w-full pl-10 pr-10 py-2.5 rounded-xl border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-800 text-gray-900 dark:text-white text-sm focus:outline-none focus:ring-2 focus:ring-emerald-500/50 focus:border-emerald-500"
                                        placeholder="Nhập mật khẩu hiện tại"
                                    />
                                    <button
                                        type="button"
                                        onClick={() => setShowCurrentPw(!showCurrentPw)}
                                        className="absolute right-3 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-600 dark:hover:text-gray-300"
                                    >
                                        <span className="material-symbols-outlined text-lg">{showCurrentPw ? 'visibility_off' : 'visibility'}</span>
                                    </button>
                                </div>
                            </div>

                            {/* New password */}
                            <div>
                                <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1.5">
                                    Mật khẩu mới <span className="text-red-500">*</span>
                                </label>
                                <div className="relative">
                                    <span className="material-symbols-outlined absolute left-3 top-1/2 -translate-y-1/2 text-gray-400 text-lg">key</span>
                                    <input
                                        type={showNewPw ? 'text' : 'password'}
                                        value={newPassword}
                                        onChange={e => setNewPassword(e.target.value)}
                                        className="w-full pl-10 pr-10 py-2.5 rounded-xl border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-800 text-gray-900 dark:text-white text-sm focus:outline-none focus:ring-2 focus:ring-emerald-500/50 focus:border-emerald-500"
                                        placeholder="Ít nhất 6 ký tự"
                                    />
                                    <button
                                        type="button"
                                        onClick={() => setShowNewPw(!showNewPw)}
                                        className="absolute right-3 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-600 dark:hover:text-gray-300"
                                    >
                                        <span className="material-symbols-outlined text-lg">{showNewPw ? 'visibility_off' : 'visibility'}</span>
                                    </button>
                                </div>
                                {/* Password strength indicator */}
                                {newPassword && (
                                    <PasswordStrength password={newPassword} />
                                )}
                            </div>

                            {/* Confirm password */}
                            <div>
                                <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1.5">
                                    Xác nhận mật khẩu mới <span className="text-red-500">*</span>
                                </label>
                                <div className="relative">
                                    <span className="material-symbols-outlined absolute left-3 top-1/2 -translate-y-1/2 text-gray-400 text-lg">lock_reset</span>
                                    <input
                                        type="password"
                                        value={confirmPassword}
                                        onChange={e => setConfirmPassword(e.target.value)}
                                        className="w-full pl-10 pr-4 py-2.5 rounded-xl border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-800 text-gray-900 dark:text-white text-sm focus:outline-none focus:ring-2 focus:ring-emerald-500/50 focus:border-emerald-500"
                                        placeholder="Nhập lại mật khẩu mới"
                                    />
                                </div>
                                {confirmPassword && newPassword !== confirmPassword && (
                                    <p className="mt-1 text-xs text-red-500 flex items-center gap-1">
                                        <span className="material-symbols-outlined text-xs">error</span>
                                        Mật khẩu xác nhận không khớp
                                    </p>
                                )}
                                {confirmPassword && newPassword === confirmPassword && (
                                    <p className="mt-1 text-xs text-emerald-500 flex items-center gap-1">
                                        <span className="material-symbols-outlined text-xs">check_circle</span>
                                        Mật khẩu khớp
                                    </p>
                                )}
                            </div>

                            {/* Actions */}
                            <div className="flex items-center gap-3 pt-2">
                                <button
                                    onClick={handleChangePassword}
                                    disabled={changingPw}
                                    className="px-6 py-2.5 bg-amber-500 text-white font-bold rounded-xl text-sm hover:bg-amber-600 transition-colors disabled:opacity-50 flex items-center gap-2"
                                >
                                    {changingPw ? (
                                        <>
                                            <div className="w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin" />
                                            Đang đổi...
                                        </>
                                    ) : (
                                        <>
                                            <span className="material-symbols-outlined text-lg">lock_reset</span>
                                            Đổi mật khẩu
                                        </>
                                    )}
                                </button>
                                <button
                                    onClick={() => { setShowPwForm(false); setCurrentPassword(''); setNewPassword(''); setConfirmPassword('') }}
                                    className="px-5 py-2.5 border border-gray-300 dark:border-gray-600 text-gray-600 dark:text-gray-300 font-medium rounded-xl text-sm hover:bg-gray-50 dark:hover:bg-gray-800 transition-colors"
                                >
                                    Hủy
                                </button>
                            </div>
                        </div>
                    )}
                </div>
            </div>

            {/* ===== Account Info Card ===== */}
            <div className="bg-white dark:bg-gray-900 rounded-2xl border border-gray-200 dark:border-gray-700 shadow-sm overflow-hidden">
                <div className="px-6 py-4 border-b border-gray-200 dark:border-gray-700 flex items-center gap-2">
                    <span className="material-symbols-outlined text-blue-500">info</span>
                    <h3 className="font-bold text-gray-900 dark:text-white">Thông tin tài khoản</h3>
                </div>
                <div className="p-6">
                    <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                        <AccountInfoRow label="ID tài khoản" value={`#${profile.id}`} />
                        <AccountInfoRow label="Loại đăng nhập" value={isGoogleAccount ? 'Google OAuth 2.0' : 'Tài khoản nội bộ'} />
                        <AccountInfoRow label="Vai trò" value={roleName} />
                        <AccountInfoRow label="Trạng thái" value={profile.status === 'ACTIVE' ? 'Đang hoạt động' : profile.status} />
                    </div>
                </div>
            </div>
        </div>
    )
}

/* ---- Sub Components ---- */

function InfoField({ icon, label, value, muted = false }) {
    return (
        <div className="flex items-start gap-3 p-3 bg-gray-50 dark:bg-gray-800/50 rounded-xl">
            <div className="size-9 rounded-lg bg-white dark:bg-gray-700 flex items-center justify-center shrink-0 border border-gray-200 dark:border-gray-600">
                <span className="material-symbols-outlined text-gray-500 dark:text-gray-400 text-lg">{icon}</span>
            </div>
            <div className="min-w-0">
                <p className="text-xs text-gray-400 uppercase tracking-wider">{label}</p>
                <p className={`text-sm font-medium mt-0.5 truncate ${muted ? 'text-gray-400 italic' : 'text-gray-900 dark:text-white'}`}>{value}</p>
            </div>
        </div>
    )
}

function AccountInfoRow({ label, value }) {
    return (
        <div className="flex items-center justify-between py-2 border-b border-gray-100 dark:border-gray-800 last:border-0">
            <span className="text-sm text-gray-500 dark:text-gray-400">{label}</span>
            <span className="text-sm font-medium text-gray-900 dark:text-white">{value}</span>
        </div>
    )
}

function PasswordStrength({ password }) {
    let strength = 0
    if (password.length >= 6) strength++
    if (password.length >= 10) strength++
    if (/[A-Z]/.test(password)) strength++
    if (/[0-9]/.test(password)) strength++
    if (/[^A-Za-z0-9]/.test(password)) strength++

    const levels = [
        { label: 'Rất yếu', cls: 'bg-red-500', width: '20%' },
        { label: 'Yếu', cls: 'bg-orange-500', width: '40%' },
        { label: 'Trung bình', cls: 'bg-amber-500', width: '60%' },
        { label: 'Mạnh', cls: 'bg-emerald-500', width: '80%' },
        { label: 'Rất mạnh', cls: 'bg-emerald-600', width: '100%' },
    ]

    const level = levels[Math.min(strength, levels.length) - 1] || levels[0]

    return (
        <div className="mt-2">
            <div className="h-1.5 bg-gray-200 dark:bg-gray-700 rounded-full overflow-hidden">
                <div className={`h-full ${level.cls} rounded-full transition-all duration-300`} style={{ width: level.width }} />
            </div>
            <p className="text-xs text-gray-400 mt-1">Độ mạnh: <span className="font-medium">{level.label}</span></p>
        </div>
    )
}

function ScoreDonut({ categoryTotals, totalScore, classification }) {
    const size = 140
    const strokeWidth = 14
    const radius = (size - strokeWidth) / 2
    const circumference = 2 * Math.PI * radius

    const segments = []
    let offset = 0
    for (let i = 1; i <= 6; i++) {
        const value = categoryTotals?.[i] || 0
        if (value > 0) {
            const pct = value / 100
            segments.push({ color: CATEGORY_COLORS[i], pct, offset })
            offset += pct
        }
    }

    const classColor = getClassColor(classification)

    return (
        <div className="relative" style={{ width: size, height: size }}>
            <svg width={size} height={size} viewBox={`0 0 ${size} ${size}`} className="transform -rotate-90">
                <circle cx={size / 2} cy={size / 2} r={radius} fill="none" stroke="currentColor" strokeWidth={strokeWidth} className="text-gray-200 dark:text-gray-700" />
                {segments.map((seg, idx) => (
                    <circle key={idx} cx={size / 2} cy={size / 2} r={radius} fill="none"
                        stroke={seg.color} strokeWidth={strokeWidth} strokeLinecap="round"
                        strokeDasharray={`${seg.pct * circumference} ${circumference}`}
                        strokeDashoffset={-seg.offset * circumference}
                        style={{ transition: 'stroke-dasharray 0.8s ease, stroke-dashoffset 0.8s ease' }} />
                ))}
            </svg>
            <div className="absolute inset-0 flex flex-col items-center justify-center">
                <span className="text-xl font-black" style={{ color: classColor }}>{classification || '—'}</span>
                <span className="text-[10px] text-gray-400 uppercase tracking-wider font-semibold">Xếp loại</span>
            </div>
        </div>
    )
}
