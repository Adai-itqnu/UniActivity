import { useState } from 'react';
import { useDarkMode } from '../contexts/darkMode';

export default function ForgotPasswordModal({ isOpen, onClose }) {
    const { isDark } = useDarkMode();
    const [step, setStep] = useState(1);
    const [email, setEmail] = useState('');
    const [otp, setOtp] = useState('');
    const [newPassword, setNewPassword] = useState('');
    const [confirmPassword, setConfirmPassword] = useState('');
    const [error, setError] = useState('');
    const [success, setSuccess] = useState('');
    const [isLoading, setIsLoading] = useState(false);
    const [showPassword, setShowPassword] = useState(false);

    if (!isOpen) return null;

    const inputCls = (hasIcon = false) =>
        `flex h-11 w-full rounded-lg border text-sm placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500 transition-all shadow-sm ${hasIcon ? 'pl-10 pr-10' : 'px-3'} ${isDark
            ? 'border-slate-600 bg-slate-700 text-white placeholder:text-slate-500'
            : 'border-slate-300 bg-white text-slate-900'
        }`;

    const labelCls = `text-xs font-semibold uppercase tracking-wide ${isDark ? 'text-slate-400' : 'text-slate-500'}`;

    const handleSendOtp = async (e) => {
        e.preventDefault();
        setError('');
        setIsLoading(true);
        try {
            const res = await fetch('/api/auth/forgot-password', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ email })
            });
            const data = await res.json();
            if (res.ok) {
                setSuccess(data.message || 'Mã OTP đã được gửi.');
                setStep(2);
            } else {
                setError(data.message || 'Lỗi gửi OTP');
            }
        } catch {
            setError('Có lỗi xảy ra khi kết nối máy chủ.');
        } finally {
            setIsLoading(false);
        }
    };

    const handleVerifyOtp = async (e) => {
        e.preventDefault();
        setError('');
        setSuccess('');
        setIsLoading(true);
        try {
            const res = await fetch('/api/auth/verify-reset-otp', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ email, otp })
            });
            const data = await res.json();
            if (res.ok) {
                setStep(3);
                setSuccess('OTP hợp lệ, vui lòng nhập mật khẩu mới.');
            } else {
                setError(data.message || 'OTP không hợp lệ');
            }
        } catch {
            setError('Có lỗi xảy ra khi kết nối máy chủ.');
        } finally {
            setIsLoading(false);
        }
    };

    const handleResetPassword = async (e) => {
        e.preventDefault();
        setError('');
        if (newPassword !== confirmPassword) {
            setError('Mật khẩu không khớp.');
            return;
        }
        setIsLoading(true);
        try {
            const res = await fetch('/api/auth/reset-password', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ email, otp, newPassword })
            });
            const data = await res.json();
            if (res.ok) {
                setSuccess('Đổi mật khẩu thành công! Bạn có thể đăng nhập bằng mật khẩu mới.');
                setTimeout(() => {
                    onClose();
                    setStep(1);
                    setEmail('');
                    setOtp('');
                    setNewPassword('');
                    setConfirmPassword('');
                    setSuccess('');
                }, 3000);
            } else {
                setError(data.message || 'Đổi mật khẩu thất bại');
            }
        } catch {
            setError('Có lỗi xảy ra khi kết nối máy chủ.');
        } finally {
            setIsLoading(false);
        }
    };

    return (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/50 backdrop-blur-sm">
            <div className={`w-full max-w-md rounded-2xl p-6 shadow-xl ${isDark ? 'bg-slate-800 border border-slate-700' : 'bg-white'}`}>
                <div className="flex justify-between items-center mb-4">
                    <h2 className={`text-xl font-bold ${isDark ? 'text-white' : 'text-slate-900'}`}>Quên mật khẩu</h2>
                    <button onClick={onClose} className={`p-1 rounded-full ${isDark ? 'text-slate-400 hover:bg-slate-700' : 'text-slate-500 hover:bg-slate-100'}`}>
                        <span className="material-symbols-outlined">close</span>
                    </button>
                </div>

                {error && (
                    <div className="mb-4 bg-red-500/10 border border-red-500/30 text-red-500 px-4 py-3 rounded-lg text-sm flex items-center gap-2">
                        <span className="material-symbols-outlined text-[18px]">error</span>
                        {error}
                    </div>
                )}
                {success && (
                    <div className="mb-4 bg-green-500/10 border border-green-500/30 text-green-500 px-4 py-3 rounded-lg text-sm flex items-center gap-2">
                        <span className="material-symbols-outlined text-[18px]">check_circle</span>
                        {success}
                    </div>
                )}

                {step === 1 && (
                    <form onSubmit={handleSendOtp} className="space-y-4">
                        <p className={`text-sm mb-2 ${isDark ? 'text-slate-300' : 'text-slate-600'}`}>
                            Nhập email của bạn để nhận mã xác thực (OTP).
                        </p>
                        <div className="space-y-1.5">
                            <label className={labelCls}>Email</label>
                            <div className="relative group">
                                <span className={`absolute left-3 top-3 ${isDark ? 'text-slate-500' : 'text-slate-400'}`}>
                                    <span className="material-symbols-outlined text-[20px]">mail</span>
                                </span>
                                <input className={inputCls(true)} type="email" placeholder="Nhập email" value={email} onChange={(e) => setEmail(e.target.value)} required />
                            </div>
                        </div>
                        <button type="submit" disabled={isLoading} className="w-full h-11 bg-indigo-600 text-white rounded-xl font-bold hover:bg-indigo-700 disabled:opacity-50">
                            {isLoading ? 'Đang gửi...' : 'Gửi mã OTP'}
                        </button>
                    </form>
                )}

                {step === 2 && (
                    <form onSubmit={handleVerifyOtp} className="space-y-4">
                        <p className={`text-sm mb-2 ${isDark ? 'text-slate-300' : 'text-slate-600'}`}>
                            Đã gửi mã OTP đến <strong>{email}</strong>. Vui lòng kiểm tra hộp thư.
                        </p>
                        <div className="space-y-1.5">
                            <label className={labelCls}>Mã OTP</label>
                            <input className={inputCls()} type="text" placeholder="Nhập mã 6 chữ số" value={otp} onChange={(e) => setOtp(e.target.value)} required maxLength={6} />
                        </div>
                        <button type="submit" disabled={isLoading} className="w-full h-11 bg-indigo-600 text-white rounded-xl font-bold hover:bg-indigo-700 disabled:opacity-50">
                            {isLoading ? 'Đang xác thực...' : 'Xác thực'}
                        </button>
                    </form>
                )}

                {step === 3 && (
                    <form onSubmit={handleResetPassword} className="space-y-4">
                        <div className="space-y-1.5">
                            <label className={labelCls}>Mật khẩu mới</label>
                            <div className="relative group">
                                <span className={`absolute left-3 top-3 ${isDark ? 'text-slate-500' : 'text-slate-400'}`}>
                                    <span className="material-symbols-outlined text-[20px]">lock</span>
                                </span>
                                <input className={inputCls(true)} type={showPassword ? 'text' : 'password'} placeholder="Ít nhất 6 ký tự" value={newPassword} onChange={(e) => setNewPassword(e.target.value)} required minLength={6} />
                                <button type="button" className={`absolute right-3 top-3 ${isDark ? 'text-slate-500' : 'text-slate-400'}`} onClick={() => setShowPassword(!showPassword)}>
                                    <span className="material-symbols-outlined text-[20px]">{showPassword ? 'visibility_off' : 'visibility'}</span>
                                </button>
                            </div>
                        </div>
                        <div className="space-y-1.5">
                            <label className={labelCls}>Nhập lại mật khẩu</label>
                            <div className="relative group">
                                <span className={`absolute left-3 top-3 ${isDark ? 'text-slate-500' : 'text-slate-400'}`}>
                                    <span className="material-symbols-outlined text-[20px]">lock_reset</span>
                                </span>
                                <input className={inputCls(true)} type={showPassword ? 'text' : 'password'} placeholder="Nhập lại mật khẩu mới" value={confirmPassword} onChange={(e) => setConfirmPassword(e.target.value)} required minLength={6} />
                            </div>
                        </div>
                        <button type="submit" disabled={isLoading} className="w-full h-11 bg-indigo-600 text-white rounded-xl font-bold hover:bg-indigo-700 disabled:opacity-50">
                            {isLoading ? 'Đang xử lý...' : 'Xác nhận đổi mật khẩu'}
                        </button>
                    </form>
                )}
            </div>
        </div>
    );
}
