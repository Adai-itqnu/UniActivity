import { useState, useEffect } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { useDarkMode } from '../contexts/darkMode'
import ForgotPasswordModal from './ForgotPasswordModal'
import bannerImg from '../assets/img/banner_QNU.jpg'
import { exchangeOAuthCodeOnce, homePathForRole } from '../utils/oauthExchange'

export default function AuthPage({ defaultTab = 'login' }) {
    const { isDark, toggleDarkMode } = useDarkMode()
    const [searchParams] = useSearchParams()
    const [activeTab, setActiveTab] = useState(defaultTab)
    const [showPassword, setShowPassword] = useState(false)
    const [showRegPassword, setShowRegPassword] = useState(false)
    const [showRegConfirm, setShowRegConfirm] = useState(false)
    const [isForgotModalOpen, setIsForgotModalOpen] = useState(false)

    const [loginForm, setLoginForm] = useState({ username: '', password: '' })
    const [registerForm, setRegisterForm] = useState({
        fullName: '', email: '', phone: '',
        password: '', confirmPassword: '', termsAccepted: false,
    })

    const [loginError, setLoginError] = useState('')
    const [registerError, setRegisterError] = useState('')
    const [registerSuccess, setRegisterSuccess] = useState('')
    const [isLoading, setIsLoading] = useState(false)

    // Đổi one-time code từ Google OAuth và xử lý lỗi callback.
    useEffect(() => {
        const error = searchParams.get('error')
        const message = searchParams.get('message')
        const code = searchParams.get('code')
        let cancelled = false

        if (code) {
            // Xóa code khỏi browser history ngay; code cũng chỉ dùng được một lần ở backend.
            window.history.replaceState({}, document.title, window.location.pathname)
            setIsLoading(true)
            exchangeOAuthCodeOnce(code)
                .then((data) => {
                    if (cancelled) return
                    sessionStorage.setItem('accessToken', data.accessToken)
                    sessionStorage.setItem('refreshToken', data.refreshToken)
                    sessionStorage.setItem('user', JSON.stringify(data.user))
                    window.location.href = homePathForRole(data.user.role)
                })
                .catch((exchangeError) => {
                    if (!cancelled) {
                        setLoginError(exchangeError.message || 'Đăng nhập bằng Google thất bại.')
                    }
                })
                .finally(() => {
                    if (!cancelled) setIsLoading(false)
                })
        }

        if (error === 'google') {
            setLoginError(message || 'Đăng nhập bằng Google thất bại. Vui lòng thử lại.')
        } else if (error === 'session') {
            setLoginError(message || 'Phiên đăng nhập không hợp lệ. Vui lòng đăng nhập lại.')
        }

        return () => {
            cancelled = true
        }
    }, [searchParams])

    const handleLogin = async (e) => {
        e.preventDefault()
        setLoginError('')
        setIsLoading(true)
        try {
            const response = await fetch('/api/auth/login', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    username: loginForm.username,
                    password: loginForm.password,
                }),
            })
            const data = await response.json()
            if (response.ok && data.accessToken) {
                // Lưu tokens vào sessionStorage
                sessionStorage.setItem('accessToken', data.accessToken)
                sessionStorage.setItem('refreshToken', data.refreshToken)
                sessionStorage.setItem('user', JSON.stringify(data.user))

                // Redirect theo role
                const role = data.user?.role
                if (role === 'ADMIN') {
                    window.location.href = '/admin/dashboard'
                } else if (role === 'MANAGER') {
                    window.location.href = '/manager/dashboard'
                } else if (role === 'STUDENT') {
                    window.location.href = '/student/home'
                } else {
                    window.location.href = '/'
                }
            } else {
                setLoginError(data.error || 'Tên đăng nhập hoặc mật khẩu không đúng.')
            }
        } catch {
            setLoginError('Có lỗi xảy ra. Vui lòng thử lại.')
        } finally {
            setIsLoading(false)
        }
    }

    const handleRegister = async (e) => {
        e.preventDefault()
        setRegisterError('')
        setRegisterSuccess('')
        setIsLoading(true)
        if (registerForm.password !== registerForm.confirmPassword) {
            setRegisterError('Mật khẩu nhập lại không khớp.')
            setIsLoading(false)
            return
        }
        if (!registerForm.termsAccepted) {
            setRegisterError('Bạn phải chấp nhận các điều khoản và điều kiện.')
            setIsLoading(false)
            return
        }
        try {
            // Gửi JSON tới API endpoint thay vì form submit
            const response = await fetch('/api/auth/register', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    fullName: registerForm.fullName,
                    email: registerForm.email,
                    phone: registerForm.phone,
                    password: registerForm.password,
                    confirmPassword: registerForm.confirmPassword,
                    termsAccepted: registerForm.termsAccepted,
                }),
                credentials: 'include',
            })
            const data = await response.json()
            if (response.ok) {
                const generatedUsername = data.username || ''
                setRegisterSuccess(`Đăng ký thành công! Mã sinh viên của bạn là: ${generatedUsername}. Hãy ghi nhớ mã này để đăng nhập.`)
                setActiveTab('login')
                setRegisterForm({ fullName: '', email: '', phone: '', password: '', confirmPassword: '', termsAccepted: false })
            } else {
                setRegisterError(data.error || 'Đăng ký thất bại. Vui lòng kiểm tra lại thông tin.')
            }
        } catch {
            setRegisterError('Có lỗi xảy ra. Vui lòng thử lại.')
        } finally {
            setIsLoading(false)
        }
    }

    // Spinner component
    const Spinner = () => (
        <span className="flex items-center gap-2">
            <svg className="animate-spin h-4 w-4" viewBox="0 0 24 24">
                <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" fill="none" />
                <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
            </svg>
            Đang xử lý...
        </span>
    )

    // Input class helper
    const inputCls = (hasIcon = false) =>
        `flex h-11 w-full rounded-lg border text-sm placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500 transition-all shadow-sm ${hasIcon ? 'pl-10 pr-10' : 'px-3'
        } ${isDark
            ? 'border-slate-600 bg-slate-700 text-white placeholder:text-slate-500'
            : 'border-slate-300 bg-white text-slate-900'
        }`

    const inputClsSmall = (hasIcon = false) =>
        `flex h-10 w-full rounded-lg border text-sm placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500 transition-all shadow-sm ${hasIcon ? 'pl-10 pr-10' : 'px-3'
        } ${isDark
            ? 'border-slate-600 bg-slate-700 text-white placeholder:text-slate-500'
            : 'border-slate-300 bg-white text-slate-900'
        }`

    const labelCls = `text-xs font-semibold uppercase tracking-wide ${isDark ? 'text-slate-400' : 'text-slate-500'}`

    return (
        <div className="min-h-screen flex font-display antialiased">
            <ForgotPasswordModal isOpen={isForgotModalOpen} onClose={() => setIsForgotModalOpen(false)} />

            {/* ===== BÊN TRÁI: Ảnh banner — chiếm 60% (w-3/5) — CỐ ĐỊNH ===== */}
            <div className="hidden lg:block lg:w-3/5 relative">
                {/* Ảnh cố định, không bị ảnh hưởng bởi nội dung bên phải */}
                <div className="fixed top-0 left-0 w-3/5 h-screen">
                    <img
                        src={bannerImg}
                        alt="Trường Đại học Quy Nhơn"
                        className="absolute inset-0 w-full h-full object-cover"
                    />
                    {/* Overlay gradient */}
                    <div className="absolute inset-0 bg-gradient-to-t from-slate-900/80 via-slate-900/30 to-slate-900/10" />
                    {/* Text overlay */}
                    <div className="absolute bottom-0 left-0 right-0 p-10">
                        <div className="flex items-center gap-3 mb-4">
                            <div className="flex h-12 w-12 items-center justify-center rounded-xl bg-white/20 backdrop-blur-sm text-white shadow-lg">
                                <span className="material-symbols-outlined text-[28px]">school</span>
                            </div>
                            <div>
                                <h1 className="text-2xl font-bold text-white">UniActivity</h1>
                                <p className="text-white/70 text-sm">Hệ thống quản lý hoạt động sinh viên</p>
                            </div>
                        </div>
                        <p className="text-white/60 text-sm max-w-lg">
                            Quản lý, đăng ký và theo dõi các hoạt động ngoại khóa dễ dàng. Tích lũy điểm rèn luyện một cách hiệu quả.
                        </p>
                    </div>
                </div>
            </div>

            {/* ===== BÊN PHẢI: Form — chiếm 40% (w-2/5) ===== */}
            <div className={`w-full lg:w-2/5 flex flex-col min-h-screen ${isDark ? 'bg-slate-900' : 'bg-slate-50'}`}>
                {/* Header */}
                <header className={`px-6 py-4 flex items-center justify-between ${isDark ? 'border-slate-700/50' : ''}`}>
                    <div className="flex items-center gap-2 lg:hidden">
                        <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-indigo-600 text-white shadow-sm">
                            <span className="material-symbols-outlined text-[20px]">school</span>
                        </div>
                        <span className={`text-lg font-semibold tracking-tight ${isDark ? 'text-white' : 'text-slate-800'}`}>UniActivity</span>
                    </div>
                    <div className="lg:ml-auto">
                        <button
                            onClick={toggleDarkMode}
                            aria-label="Chuyển đổi chế độ tối"
                            className={`flex items-center justify-center h-10 w-10 rounded-full transition-colors duration-200 focus:outline-none focus:ring-2 focus:ring-indigo-500/20 ${isDark ? 'text-yellow-400 hover:bg-slate-800' : 'text-slate-500 hover:bg-slate-100 hover:text-indigo-600'
                                }`}
                        >
                            <span className="material-symbols-outlined">{isDark ? 'light_mode' : 'dark_mode'}</span>
                        </button>
                    </div>
                </header>

                {/* Form Container */}
                <div className="flex-grow flex items-center justify-center p-4 sm:p-6">
                    <div className="w-full max-w-[400px]">
                        {/* Logo + Title */}
                        <div className="text-center mb-5">
                            <div className={`mx-auto flex h-14 w-14 items-center justify-center rounded-full mb-3 shadow-sm ${isDark ? 'bg-indigo-900/50 text-indigo-400' : 'bg-indigo-100 text-indigo-600'
                                }`}>
                                <span className="material-symbols-outlined text-[28px]">school</span>
                            </div>
                            <h2 className={`text-xl font-bold tracking-tight ${isDark ? 'text-white' : 'text-slate-800'}`}>
                                {activeTab === 'login' ? 'Đăng nhập' : 'Đăng ký'}
                            </h2>
                            <p className={`mt-1 text-sm font-medium ${isDark ? 'text-slate-400' : 'text-slate-500'}`}>
                                {activeTab === 'login' ? 'Cổng thông tin đào tạo' : 'Tạo tài khoản sinh viên mới'}
                            </p>
                        </div>

                        {/* Auth Card */}
                        <div className={`rounded-2xl shadow-xl p-5 sm:p-6 ${isDark ? 'bg-slate-800 shadow-slate-900/50' : 'bg-white'}`}>
                            {/* Tab Navigation */}
                            <div className={`flex border-b mb-5 ${isDark ? 'border-slate-700' : 'border-slate-200'}`}>
                                <button
                                    className={`flex-1 pb-3 text-sm font-semibold border-b-2 transition-all focus:outline-none ${activeTab === 'login' ? 'tab-active' : 'tab-inactive'
                                        }`}
                                    onClick={() => setActiveTab('login')}
                                >
                                    Đăng nhập
                                </button>
                                <button
                                    className={`flex-1 pb-3 text-sm font-semibold border-b-2 transition-all focus:outline-none ${activeTab === 'register' ? 'tab-active' : 'tab-inactive'
                                        }`}
                                    onClick={() => setActiveTab('register')}
                                >
                                    Đăng ký
                                </button>
                            </div>

                            {/* ===== LOGIN FORM ===== */}
                            {activeTab === 'login' && (
                                <div className="space-y-4">
                                    {loginError && (
                                        <div className="bg-red-500/10 border border-red-500/30 text-red-400 px-4 py-3 rounded-lg text-sm flex items-center gap-2">
                                            <span className="material-symbols-outlined text-[18px]">error</span>
                                            {loginError}
                                        </div>
                                    )}
                                    {registerSuccess && (
                                        <div className="bg-green-500/10 border border-green-500/30 text-green-400 px-4 py-3 rounded-lg text-sm flex items-center gap-2">
                                            <span className="material-symbols-outlined text-[18px]">check_circle</span>
                                            {registerSuccess}
                                        </div>
                                    )}

                                    <form className="space-y-4" onSubmit={handleLogin}>
                                        <div className="space-y-1.5">
                                            <label className={labelCls} htmlFor="login-email">Tên đăng nhập</label>
                                            <div className="relative group">
                                                <span className={`absolute left-3 top-3 ${isDark ? 'text-slate-500' : 'text-slate-400'} group-focus-within:text-indigo-500 transition-colors`}>
                                                    <span className="material-symbols-outlined text-[20px]">person</span>
                                                </span>
                                                <input className={inputCls(true)} id="login-email" type="text"
                                                    placeholder="Nhập mã sinh viên hoặc email"
                                                    value={loginForm.username}
                                                    onChange={(e) => setLoginForm({ ...loginForm, username: e.target.value })}
                                                    required autoFocus
                                                />
                                            </div>
                                        </div>

                                        <div className="space-y-1.5">
                                            <label className={labelCls} htmlFor="login-password">Mật khẩu</label>
                                            <div className="relative group">
                                                <span className={`absolute left-3 top-3 ${isDark ? 'text-slate-500' : 'text-slate-400'} group-focus-within:text-indigo-500 transition-colors`}>
                                                    <span className="material-symbols-outlined text-[20px]">lock</span>
                                                </span>
                                                <input className={inputCls(true)} id="login-password"
                                                    type={showPassword ? 'text' : 'password'}
                                                    placeholder="Nhập mật khẩu"
                                                    value={loginForm.password}
                                                    onChange={(e) => setLoginForm({ ...loginForm, password: e.target.value })}
                                                    required
                                                />
                                                <button type="button" className={`absolute right-3 top-3 ${isDark ? 'text-slate-500 hover:text-slate-300' : 'text-slate-400 hover:text-slate-600'} transition-colors`}
                                                    onClick={() => setShowPassword(!showPassword)}>
                                                    <span className="material-symbols-outlined text-[20px]">{showPassword ? 'visibility_off' : 'visibility'}</span>
                                                </button>
                                            </div>
                                        </div>

                                        <button type="submit" disabled={isLoading}
                                            className="w-full h-11 inline-flex items-center justify-center rounded-xl text-sm font-bold bg-indigo-600 text-white hover:bg-indigo-700 hover:shadow-lg hover:shadow-indigo-500/30 active:scale-[0.98] transition-all duration-200 disabled:opacity-50 disabled:cursor-not-allowed">
                                            {isLoading ? <Spinner /> : 'Đăng nhập'}
                                        </button>
                                    </form>

                                    <button
                                        type="button"
                                        onClick={() => window.location.href = '/oauth2/authorization/google'}
                                        className={`w-full inline-flex items-center justify-center rounded-lg border h-10 px-4 transition-all text-sm font-medium ${isDark ? 'border-slate-600 bg-slate-700 text-slate-300 hover:bg-slate-600' : 'border-slate-200 bg-white text-slate-600 hover:bg-slate-50 hover:border-slate-300'
                                            }`}>
                                        <img alt="Google" className="mr-2 h-5 w-5" src="https://www.svgrepo.com/show/475656/google-color.svg" />
                                        Đăng nhập với Google
                                    </button>

                                    <div className="text-center">
                                        <button
                                            type="button"
                                            onClick={() => setIsForgotModalOpen(true)}
                                            className="text-sm font-medium text-indigo-500 hover:text-indigo-400 transition-colors hover:underline">
                                            Sinh viên quên mật khẩu
                                        </button>
                                    </div>

                                    <div className="text-center">
                                        <p className={`text-sm ${isDark ? 'text-slate-400' : 'text-slate-500'}`}>
                                            Chưa có tài khoản?{' '}
                                            <button className="font-bold text-indigo-500 hover:text-indigo-400 hover:underline" onClick={() => setActiveTab('register')}>
                                                Đăng ký ngay
                                            </button>
                                        </p>
                                    </div>
                                </div>
                            )}

                            {/* ===== REGISTER FORM ===== */}
                            {activeTab === 'register' && (
                                <div className="space-y-3">
                                    {registerError && (
                                        <div className="bg-red-500/10 border border-red-500/30 text-red-400 px-4 py-3 rounded-lg text-sm flex items-center gap-2">
                                            <span className="material-symbols-outlined text-[18px]">error</span>
                                            {registerError}
                                        </div>
                                    )}

                                    <form className="space-y-3" onSubmit={handleRegister}>
                                        <div className="space-y-1">
                                            <label className={labelCls} htmlFor="reg-name">Họ và tên</label>
                                            <input className={inputClsSmall()} id="reg-name" type="text" placeholder="Nguyễn Văn A"
                                                value={registerForm.fullName}
                                                onChange={(e) => setRegisterForm({ ...registerForm, fullName: e.target.value })}
                                                required />
                                        </div>

                                        <div className="space-y-1">
                                            <label className={labelCls} htmlFor="reg-email">Email</label>
                                            <div className="relative group">
                                                <span className={`absolute left-3 top-2.5 ${isDark ? 'text-slate-500' : 'text-slate-400'} group-focus-within:text-indigo-500 transition-colors`}>
                                                    <span className="material-symbols-outlined text-[20px]">mail</span>
                                                </span>
                                                <input className={inputClsSmall(true)} id="reg-email" type="email" placeholder="sv@gmail.com"
                                                    value={registerForm.email}
                                                    onChange={(e) => setRegisterForm({ ...registerForm, email: e.target.value })}
                                                    required />
                                            </div>
                                        </div>

                                        <div className="space-y-1">
                                            <label className={labelCls} htmlFor="reg-phone">Số điện thoại <span className={`text-xs font-normal ${isDark ? 'text-slate-500' : 'text-slate-400'}`}>(không bắt buộc)</span></label>
                                            <div className="relative group">
                                                <span className={`absolute left-3 top-2.5 ${isDark ? 'text-slate-500' : 'text-slate-400'} group-focus-within:text-indigo-500 transition-colors`}>
                                                    <span className="material-symbols-outlined text-[20px]">phone</span>
                                                </span>
                                                <input className={inputClsSmall(true)} id="reg-phone" type="text" placeholder="Có thể bỏ qua, điền sau cũng được"
                                                    value={registerForm.phone}
                                                    onChange={(e) => setRegisterForm({ ...registerForm, phone: e.target.value })} />
                                            </div>
                                        </div>

                                        <div className="space-y-1">
                                            <label className={labelCls} htmlFor="reg-password">Mật khẩu</label>
                                            <div className="relative group">
                                                <span className={`absolute left-3 top-2.5 ${isDark ? 'text-slate-500' : 'text-slate-400'} group-focus-within:text-indigo-500 transition-colors`}>
                                                    <span className="material-symbols-outlined text-[20px]">lock</span>
                                                </span>
                                                <input className={inputClsSmall(true)} id="reg-password"
                                                    type={showRegPassword ? 'text' : 'password'} placeholder="Tối thiểu 6 ký tự"
                                                    value={registerForm.password}
                                                    onChange={(e) => setRegisterForm({ ...registerForm, password: e.target.value })}
                                                    required />
                                                <button type="button" className={`absolute right-3 top-2.5 ${isDark ? 'text-slate-500 hover:text-slate-300' : 'text-slate-400 hover:text-slate-600'} transition-colors`}
                                                    onClick={() => setShowRegPassword(!showRegPassword)}>
                                                    <span className="material-symbols-outlined text-[20px]">{showRegPassword ? 'visibility_off' : 'visibility'}</span>
                                                </button>
                                            </div>
                                        </div>

                                        <div className="space-y-1">
                                            <label className={labelCls} htmlFor="reg-confirm">Nhập lại mật khẩu</label>
                                            <div className="relative group">
                                                <span className={`absolute left-3 top-2.5 ${isDark ? 'text-slate-500' : 'text-slate-400'} group-focus-within:text-indigo-500 transition-colors`}>
                                                    <span className="material-symbols-outlined text-[20px]">lock_reset</span>
                                                </span>
                                                <input className={inputClsSmall(true)} id="reg-confirm"
                                                    type={showRegConfirm ? 'text' : 'password'} placeholder="Nhập lại mật khẩu"
                                                    value={registerForm.confirmPassword}
                                                    onChange={(e) => setRegisterForm({ ...registerForm, confirmPassword: e.target.value })}
                                                    required />
                                                <button type="button" className={`absolute right-3 top-2.5 ${isDark ? 'text-slate-500 hover:text-slate-300' : 'text-slate-400 hover:text-slate-600'} transition-colors`}
                                                    onClick={() => setShowRegConfirm(!showRegConfirm)}>
                                                    <span className="material-symbols-outlined text-[20px]">{showRegConfirm ? 'visibility_off' : 'visibility'}</span>
                                                </button>
                                            </div>
                                        </div>

                                        <div className="flex items-start gap-2">
                                            <input type="checkbox" id="termsAccepted"
                                                className="mt-1 h-4 w-4 rounded border-slate-300 text-indigo-600 focus:ring-indigo-500/20"
                                                checked={registerForm.termsAccepted}
                                                onChange={(e) => setRegisterForm({ ...registerForm, termsAccepted: e.target.checked })} />
                                            <label htmlFor="termsAccepted" className={`text-sm ${isDark ? 'text-slate-400' : 'text-slate-500'}`}>
                                                Tôi chấp nhận các{' '}
                                                <Link to="/terms" className="text-indigo-500 hover:text-indigo-400 font-medium hover:underline" target="_blank">
                                                    điều khoản và điều kiện
                                                </Link>
                                            </label>
                                        </div>

                                        <button type="submit" disabled={isLoading}
                                            className="w-full h-11 mt-1 inline-flex items-center justify-center rounded-xl text-sm font-bold bg-indigo-600 text-white hover:bg-indigo-700 hover:shadow-lg hover:shadow-indigo-500/30 active:scale-[0.98] transition-all duration-200 disabled:opacity-50 disabled:cursor-not-allowed">
                                            {isLoading ? <Spinner /> : 'Tạo tài khoản'}
                                        </button>
                                    </form>

                                    <div className="text-center pt-1">
                                        <p className={`text-sm ${isDark ? 'text-slate-400' : 'text-slate-500'}`}>
                                            Đã có tài khoản?{' '}
                                            <button className="font-bold text-indigo-500 hover:text-indigo-400 hover:underline" onClick={() => setActiveTab('login')}>
                                                Đăng nhập
                                            </button>
                                        </p>
                                    </div>
                                </div>
                            )}
                        </div>

                        {/* Footer */}
                        <div className="mt-5 text-center text-xs text-slate-400">
                            <p>©Copyright © 2026 UniActivity System | All Rights Reserved</p>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    )
}
