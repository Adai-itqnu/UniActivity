import { useState } from 'react'

const REPORTS = [
    {
        id: 'members',
        title: 'Danh sách thành viên',
        description: 'Xuất danh sách toàn bộ thành viên trong lớp với thông tin chi tiết.',
        icon: 'groups',
        gradient: 'from-blue-500 to-indigo-600',
        url: '/manager/api/reports/members',
    },
    {
        id: 'point-requests',
        title: 'Yêu cầu điểm rèn luyện',
        description: 'Xuất danh sách tất cả yêu cầu cộng điểm rèn luyện từ sinh viên.',
        icon: 'grade',
        gradient: 'from-purple-500 to-pink-500',
        url: '/manager/api/reports/point-requests',
    },
    {
        id: 'student-points',
        title: 'Tổng hợp điểm rèn luyện',
        description: 'Xuất bảng tổng hợp điểm rèn luyện của tất cả sinh viên trong lớp.',
        icon: 'trending_up',
        gradient: 'from-emerald-500 to-teal-500',
        url: '/manager/api/reports/student-points',
    },
]

export default function Reports() {
    const [downloading, setDownloading] = useState(null)

    const handleDownload = async (report) => {
        setDownloading(report.id)
        try {
            const res = await fetch(report.url, { credentials: 'include' })
            if (!res.ok) throw new Error('Lỗi tải báo cáo')
            const blob = await res.blob()
            const url = URL.createObjectURL(blob)
            const a = document.createElement('a')
            a.href = url
            const cd = res.headers.get('Content-Disposition')
            let filename = `${report.id}.xlsx`
            if (cd) {
                const match = cd.match(/filename\*?=(?:UTF-8'')?([^;\n]+)/i)
                if (match) filename = decodeURIComponent(match[1].replace(/"/g, ''))
            }
            a.download = filename
            document.body.appendChild(a)
            a.click()
            a.remove()
            URL.revokeObjectURL(url)
        } catch (e) {
            alert(e.message)
        } finally {
            setDownloading(null)
        }
    }

    return (
        <div className="space-y-6">
            {/* ── Header ── */}
            <div className="flex items-center gap-3">
                <div className="size-11 rounded-xl bg-gradient-to-br from-cyan-500 to-blue-500 flex items-center justify-center shadow-sm">
                    <span className="material-symbols-outlined text-white text-xl">description</span>
                </div>
                <div>
                    <h2 className="text-2xl font-bold text-gray-900 dark:text-white tracking-tight">Xuất báo cáo</h2>
                    <p className="text-sm text-gray-500 dark:text-gray-400">Tải xuống báo cáo dưới dạng file Excel</p>
                </div>
            </div>

            {/* ── Report Cards ── */}
            <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
                {REPORTS.map(report => (
                    <div key={report.id}
                        className={`bg-gradient-to-br ${report.gradient} rounded-2xl p-6 text-white relative overflow-hidden hover:-translate-y-1 hover:shadow-xl transition-all duration-300 group`}>
                        {/* Decorative circles */}
                        <div className="absolute -right-6 -top-6 w-28 h-28 rounded-full bg-white/10" />
                        <div className="absolute -left-4 -bottom-4 w-20 h-20 rounded-full bg-white/10" />

                        <div className="relative z-10">
                            <div className="size-14 rounded-2xl bg-white/20 flex items-center justify-center mb-5">
                                <span className="material-symbols-outlined text-2xl">{report.icon}</span>
                            </div>
                            <h3 className="text-lg font-bold mb-2">{report.title}</h3>
                            <p className="text-sm text-white/80 mb-6 leading-relaxed">{report.description}</p>
                            <button
                                onClick={() => handleDownload(report)}
                                disabled={downloading === report.id}
                                className="w-full px-4 py-3 bg-white/20 backdrop-blur-sm text-white font-bold rounded-xl text-sm hover:bg-white/30 transition-colors disabled:opacity-50 flex items-center justify-center gap-2 border border-white/20 group-hover:bg-white/25"
                            >
                                {downloading === report.id ? (
                                    <>
                                        <div className="w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin" />
                                        Đang tải...
                                    </>
                                ) : (
                                    <>
                                        <span className="material-symbols-outlined text-lg">download</span>
                                        Tải xuống (.xlsx)
                                    </>
                                )}
                            </button>
                        </div>
                    </div>
                ))}
            </div>

            {/* ── Info ── */}
            <div className="bg-blue-50 dark:bg-blue-900/10 border border-blue-200 dark:border-blue-800/50 rounded-2xl p-6 flex items-start gap-4">
                <div className="size-10 rounded-xl bg-blue-100 dark:bg-blue-900/30 flex items-center justify-center shrink-0">
                    <span className="material-symbols-outlined text-blue-500">info</span>
                </div>
                <div>
                    <h4 className="font-bold text-blue-700 dark:text-blue-400 text-sm">Lưu ý</h4>
                    <p className="text-sm text-blue-600/80 dark:text-blue-400/80 mt-1 leading-relaxed">
                        Các báo cáo được xuất dưới định dạng Excel (.xlsx) và chứa dữ liệu mới nhất tại thời điểm tải xuống.
                        Bạn có thể mở bằng Microsoft Excel, Google Sheets hoặc LibreOffice Calc.
                    </p>
                </div>
            </div>
        </div>
    )
}
