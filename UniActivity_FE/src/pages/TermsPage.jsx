import { Link } from 'react-router-dom'
import { useDarkMode } from '../contexts/DarkModeContext'

export default function TermsPage() {
    const { isDark, toggleDarkMode } = useDarkMode()

    return (
        <div className={`min-h-screen font-display antialiased ${isDark ? 'bg-slate-900 text-slate-200' : 'bg-slate-50 text-slate-900'}`}>
            {/* Header */}
            <header className={`sticky top-0 z-50 px-6 py-4 flex items-center justify-between backdrop-blur-md border-b ${isDark ? 'bg-slate-900/80 border-slate-700/50' : 'bg-white/70 border-slate-200/50'
                }`}>
                <Link to="/login" className="flex items-center gap-2">
                    <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-indigo-600 text-white shadow-sm">
                        <span className="material-symbols-outlined text-[20px]">school</span>
                    </div>
                    <span className={`text-lg font-semibold tracking-tight ${isDark ? 'text-white' : 'text-slate-800'}`}>
                        UniActivity
                    </span>
                </Link>
                <button
                    onClick={toggleDarkMode}
                    aria-label="Chuyển đổi chế độ tối"
                    className={`flex items-center justify-center h-10 w-10 rounded-full transition-colors duration-200 focus:outline-none focus:ring-2 focus:ring-indigo-500/20 ${isDark ? 'text-yellow-400 hover:bg-slate-800' : 'text-slate-500 hover:bg-slate-100 hover:text-indigo-600'
                        }`}
                >
                    <span className="material-symbols-outlined">{isDark ? 'light_mode' : 'dark_mode'}</span>
                </button>
            </header>

            {/* Content */}
            <main className="max-w-3xl mx-auto px-6 py-12">
                <div className={`rounded-2xl shadow-xl p-8 sm:p-10 ${isDark ? 'bg-slate-800' : 'bg-white'}`}>
                    <div className="text-center mb-8">
                        <div className="mx-auto flex h-14 w-14 items-center justify-center rounded-full bg-indigo-100 text-indigo-600 mb-4">
                            <span className="material-symbols-outlined text-[28px]">gavel</span>
                        </div>
                        <h1 className={`text-2xl font-bold ${isDark ? 'text-white' : 'text-slate-800'}`}>
                            Điều khoản và Điều kiện
                        </h1>
                        <p className={`mt-2 text-sm ${isDark ? 'text-slate-400' : 'text-slate-500'}`}>
                            Cập nhật lần cuối: 01/01/2026
                        </p>
                    </div>

                    <div className={`space-y-6 text-sm leading-relaxed ${isDark ? 'text-slate-300' : 'text-slate-600'}`}>
                        {/* Điều 1 */}
                        <section>
                            <h2 className={`text-lg font-bold mb-2 ${isDark ? 'text-white' : 'text-slate-800'}`}>
                                1. Giới thiệu
                            </h2>
                            <p>
                                Chào mừng bạn đến với UniActivity — Hệ thống quản lý hoạt động sinh viên.
                                Bằng việc đăng ký và sử dụng hệ thống, bạn đồng ý tuân thủ các điều khoản và điều kiện được nêu dưới đây.
                            </p>
                        </section>

                        {/* Điều 2 */}
                        <section>
                            <h2 className={`text-lg font-bold mb-2 ${isDark ? 'text-white' : 'text-slate-800'}`}>
                                2. Đăng ký tài khoản
                            </h2>
                            <ul className="list-disc pl-5 space-y-1">
                                <li>Bạn phải cung cấp thông tin chính xác, đầy đủ khi đăng ký tài khoản.</li>
                                <li>Mỗi sinh viên chỉ được phép đăng ký một tài khoản duy nhất.</li>
                                <li>Bạn chịu trách nhiệm bảo mật thông tin đăng nhập của mình.</li>
                                <li>Nghiêm cấm chia sẻ tài khoản cho người khác sử dụng.</li>
                            </ul>
                        </section>

                        {/* Điều 3 */}
                        <section>
                            <h2 className={`text-lg font-bold mb-2 ${isDark ? 'text-white' : 'text-slate-800'}`}>
                                3. Quy định sử dụng
                            </h2>
                            <ul className="list-disc pl-5 space-y-1">
                                <li>Sử dụng hệ thống đúng mục đích quản lý hoạt động sinh viên.</li>
                                <li>Không đăng ký hoạt động với mục đích gian lận điểm rèn luyện.</li>
                                <li>Tuân thủ nội quy của trường và các quy định pháp luật hiện hành.</li>
                                <li>Không sử dụng hệ thống để phát tán nội dung vi phạm.</li>
                            </ul>
                        </section>

                        {/* Điều 4 */}
                        <section>
                            <h2 className={`text-lg font-bold mb-2 ${isDark ? 'text-white' : 'text-slate-800'}`}>
                                4. Điểm rèn luyện
                            </h2>
                            <p>
                                Điểm rèn luyện được tính toán dựa trên sự tham gia của sinh viên trong các hoạt động.
                                Hệ thống chỉ ghi nhận điểm khi sinh viên đã điểm danh thành công.
                                Mọi khiếu nại về điểm rèn luyện cần được gửi trong vòng 7 ngày sau khi hoạt động kết thúc.
                            </p>
                        </section>

                        {/* Điều 5 */}
                        <section>
                            <h2 className={`text-lg font-bold mb-2 ${isDark ? 'text-white' : 'text-slate-800'}`}>
                                5. Bảo mật thông tin
                            </h2>
                            <p>
                                Chúng tôi cam kết bảo vệ thông tin cá nhân của bạn. Dữ liệu cá nhân chỉ được sử dụng
                                cho mục đích quản lý hoạt động và sẽ không được chia sẻ cho bên thứ ba khi chưa có sự đồng ý của bạn.
                            </p>
                        </section>

                        {/* Điều 6 */}
                        <section>
                            <h2 className={`text-lg font-bold mb-2 ${isDark ? 'text-white' : 'text-slate-800'}`}>
                                6. Quyền hạn quản trị
                            </h2>
                            <p>
                                Ban quản trị có quyền tạm khóa hoặc xóa tài khoản vi phạm điều khoản sử dụng.
                                Mọi quyết định của ban quản trị là quyết định cuối cùng.
                            </p>
                        </section>

                        {/* Điều 7 */}
                        <section>
                            <h2 className={`text-lg font-bold mb-2 ${isDark ? 'text-white' : 'text-slate-800'}`}>
                                7. Thay đổi điều khoản
                            </h2>
                            <p>
                                Chúng tôi có quyền cập nhật các điều khoản này bất cứ lúc nào.
                                Người dùng sẽ được thông báo về các thay đổi quan trọng thông qua hệ thống.
                            </p>
                        </section>
                    </div>

                    {/* Nút quay lại */}
                    <div className="mt-8 text-center">
                        <Link
                            to="/login"
                            className="inline-flex items-center gap-2 px-6 py-3 rounded-xl text-sm font-bold bg-indigo-600 text-white hover:bg-indigo-700 hover:shadow-lg hover:shadow-indigo-500/30 active:scale-[0.98] transition-all duration-200"
                        >
                            <span className="material-symbols-outlined text-[18px]">arrow_back</span>
                            Quay lại đăng nhập
                        </Link>
                    </div>
                </div>

                {/* Footer */}
                <div className="mt-6 text-center text-xs text-slate-400">
                    <p>©Copyright © 2026 UniActivity System | All Rights Reserved</p>
                </div>
            </main>
        </div>
    )
}
