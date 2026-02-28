import { useState, useEffect } from 'react'
import { useOutletContext } from 'react-router-dom'

const CATEGORY_NAMES = ['', 'Ý thức học tập', 'Chấp hành nội quy', 'Hoạt động CT-XH', 'Phẩm chất công dân', 'Lớp, Đoàn', 'Thành tích đặc biệt']
const CATEGORY_COLORS = ['', '#3b82f6', '#10b981', '#06b6d4', '#f59e0b', '#8b5cf6', '#ef4444']

const CLASSIFICATIONS = [
    { min: 90, max: 100, label: 'Xuất sắc', cls: 'bg-emerald-100 dark:bg-emerald-900/30 text-emerald-600 dark:text-emerald-400' },
    { min: 80, max: 89, label: 'Tốt', cls: 'bg-blue-100 dark:bg-blue-900/30 text-blue-600 dark:text-blue-400' },
    { min: 65, max: 79, label: 'Khá', cls: 'bg-cyan-100 dark:bg-cyan-900/30 text-cyan-600 dark:text-cyan-400' },
    { min: 50, max: 64, label: 'Trung bình', cls: 'bg-amber-100 dark:bg-amber-900/30 text-amber-600 dark:text-amber-400' },
    { min: 35, max: 49, label: 'Yếu', cls: 'bg-orange-100 dark:bg-orange-900/30 text-orange-600 dark:text-orange-400' },
    { min: 0, max: 34, label: 'Kém', cls: 'bg-red-100 dark:bg-red-900/30 text-red-600 dark:text-red-400' },
]

const REQUEST_STATUS = {
    PENDING: { label: 'Chờ duyệt', cls: 'bg-amber-100 dark:bg-amber-900/30 text-amber-600 dark:text-amber-400' },
    APPROVED: { label: 'Đã duyệt', cls: 'bg-emerald-100 dark:bg-emerald-900/30 text-emerald-600 dark:text-emerald-400' },
    REJECTED: { label: 'Từ chối', cls: 'bg-red-100 dark:bg-red-900/30 text-red-600 dark:text-red-400' },
}

export default function MyScores() {
    const { currentUser } = useOutletContext()
    const [data, setData] = useState(null)
    const [loading, setLoading] = useState(true)
    const [showClassModal, setShowClassModal] = useState(false)

    // Point request form
    const [selectedCriteria, setSelectedCriteria] = useState('')
    const [criteriaInfo, setCriteriaInfo] = useState(null)
    const [description, setDescription] = useState('')
    const [claimedScore, setClaimedScore] = useState('')
    const [evidenceFiles, setEvidenceFiles] = useState([])
    const [submitting, setSubmitting] = useState(false)
    const [toast, setToast] = useState(null)

    // GPA calculator
    const [currentGpa, setCurrentGpa] = useState('')
    const [previousGpa, setPreviousGpa] = useState('')
    const [gpaScore, setGpaScore] = useState(null)

    const fetchData = () => {
        setLoading(true)
        fetch('/student/api/my-scores', { credentials: 'include' })
            .then(r => { if (!r.ok) throw new Error('Lỗi'); return r.json() })
            .then(json => { setData(json); setLoading(false) })
            .catch(() => setLoading(false))
    }

    useEffect(() => { fetchData() }, [])

    const showToast = (type, text) => {
        setToast({ type, text })
        setTimeout(() => setToast(null), 4000)
    }

    // Load criteria info when selected
    const handleCriteriaChange = async (code) => {
        setSelectedCriteria(code)
        setClaimedScore('')
        setGpaScore(null)
        if (!code) { setCriteriaInfo(null); return }
        try {
            const res = await fetch(`/student/api/scoring-rules/${code}`, { credentials: 'include' })
            if (res.ok) setCriteriaInfo(await res.json())
        } catch { setCriteriaInfo(null) }
    }

    // GPA score calculation
    const handleCalcGpa = async () => {
        if (!currentGpa || !previousGpa) return
        try {
            const res = await fetch('/student/api/calculate-gpa-score', {
                method: 'POST', credentials: 'include',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ currentGpa: parseFloat(currentGpa), previousGpa: parseFloat(previousGpa) }),
            })
            const json = await res.json()
            if (res.ok) {
                setGpaScore(json.score)
                setClaimedScore(json.score.toString())
            }
        } catch { /* ignore */ }
    }

    // Submit point request
    const handleSubmitRequest = async () => {
        if (!selectedCriteria || !description.trim()) {
            showToast('error', 'Vui lòng điền đầy đủ thông tin')
            return
        }
        setSubmitting(true)

        try {
            // Upload evidence files first if any
            let evidenceImageUrl = ''
            if (evidenceFiles.length > 0) {
                const fd = new FormData()
                evidenceFiles.forEach(f => fd.append('files', f))
                const uploadRes = await fetch('/student/api/upload-evidence', {
                    method: 'POST', credentials: 'include', body: fd,
                })
                const uploadJson = await uploadRes.json()
                if (!uploadRes.ok) throw new Error(uploadJson.message)
                evidenceImageUrl = uploadJson.paths?.join(',') || ''
            }

            // If 1.1 with GPA, save via dedicated endpoint
            if (selectedCriteria === '1.1' && currentGpa && previousGpa) {
                const res = await fetch('/student/api/save-gpa-score', {
                    method: 'POST', credentials: 'include',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ currentGpa: parseFloat(currentGpa), previousGpa: parseFloat(previousGpa) }),
                })
                const json = await res.json()
                if (!res.ok) throw new Error(json.message)
                showToast('success', json.message)
            } else {
                // Normal point request
                const res = await fetch('/student/api/point-requests', {
                    method: 'POST', credentials: 'include',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({
                        criteriaCode: selectedCriteria,
                        claimedScore: claimedScore ? parseInt(claimedScore) : null,
                        description: description.trim(),
                        evidenceImageUrl: evidenceImageUrl || null,
                    }),
                })
                const json = await res.json()
                if (!res.ok) throw new Error(json.message)
                showToast('success', json.message)
            }

            // Reset form
            setSelectedCriteria('')
            setCriteriaInfo(null)
            setDescription('')
            setClaimedScore('')
            setEvidenceFiles([])
            setCurrentGpa('')
            setPreviousGpa('')
            setGpaScore(null)
            fetchData()
        } catch (e) {
            showToast('error', e.message)
        } finally {
            setSubmitting(false)
        }
    }

    if (loading) return <Loading />
    if (!data) return null

    const { categoryTotals = {}, totalScore = 0, classification = '', user = {}, currentSemester, scores = {}, approvedActivities = [], myRequests = [], scoringRules } = data

    // Build subcategory list from scoring rules
    const allSubcategories = []
    if (scoringRules?.categories) {
        for (const cat of scoringRules.categories) {
            for (const sub of (cat.subcategories || [])) {
                if (sub.type === 'MANUAL') {
                    allSubcategories.push({ code: sub.id, name: `${sub.id} - ${sub.name}` })
                }
            }
        }
    }

    return (
        <div className="space-y-6">
            {/* Header */}
            <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
                <div className="flex items-center gap-3">
                    <div className="size-10 rounded-xl bg-purple-500/10 flex items-center justify-center">
                        <span className="material-symbols-outlined text-purple-500">star</span>
                    </div>
                    <div>
                        <h2 className="text-2xl font-bold text-gray-900 dark:text-white">Phiếu Đánh Giá Kết Quả Rèn Luyện</h2>
                        {currentSemester && <p className="text-sm text-gray-500 dark:text-gray-400">{currentSemester.name}</p>}
                    </div>
                </div>
                <button onClick={() => setShowClassModal(true)} className="px-4 py-2 text-sm font-medium border border-gray-300 dark:border-gray-600 text-gray-600 dark:text-gray-300 rounded-xl hover:bg-gray-50 dark:hover:bg-gray-800 transition-colors flex items-center gap-2">
                    <span className="material-symbols-outlined text-lg">info</span>
                    Cách xếp loại
                </button>
            </div>

            {/* Toast */}
            {toast && (
                <div className={`px-4 py-3 rounded-xl text-sm font-medium flex items-center gap-2 ${toast.type === 'success' ? 'bg-emerald-100 dark:bg-emerald-900/30 text-emerald-700 dark:text-emerald-400' : 'bg-red-100 dark:bg-red-900/30 text-red-700 dark:text-red-400'}`}>
                    <span className="material-symbols-outlined text-lg">{toast.type === 'success' ? 'check_circle' : 'error'}</span>
                    {toast.text}
                </div>
            )}

            {/* Student Info */}
            <div className="bg-white dark:bg-gray-900 rounded-2xl border border-gray-200 dark:border-gray-700 shadow-sm p-5">
                <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                    <InfoItem label="Họ tên" value={user.fullName} />
                    <InfoItem label="Lớp" value={user.className || 'Chưa tham gia lớp'} />
                    <InfoItem label="Khoa" value={user.facultyName || 'N/A'} />
                </div>
            </div>

            {/* Score Summary Table */}
            <div className="bg-white dark:bg-gray-900 rounded-2xl border border-gray-200 dark:border-gray-700 shadow-sm overflow-hidden">
                <div className="bg-gradient-to-r from-emerald-500 to-teal-500 px-6 py-4 flex items-center gap-3">
                    <span className="material-symbols-outlined text-white text-xl">table_chart</span>
                    <h3 className="text-white font-bold">Bảng Điểm Tổng Hợp</h3>
                </div>
                <div className="overflow-x-auto">
                    <table className="w-full">
                        <thead>
                            <tr className="bg-gray-50 dark:bg-gray-800/50">
                                {[1, 2, 3, 4, 5, 6].map(i => (
                                    <th key={i} className="px-4 py-3 text-center text-xs font-semibold text-gray-500 dark:text-gray-400">
                                        <div className="font-bold text-gray-700 dark:text-gray-200">Mục {i}</div>
                                        <div className="font-normal text-[10px] mt-0.5">{CATEGORY_NAMES[i]}</div>
                                    </th>
                                ))}
                                <th className="px-4 py-3 text-center text-xs font-semibold bg-emerald-500/10 text-emerald-600 dark:text-emerald-400">
                                    <div className="font-bold">Tổng</div>
                                    <div className="font-normal text-[10px] mt-0.5">max 100</div>
                                </th>
                                <th className="px-4 py-3 text-center text-xs font-semibold bg-purple-500/10 text-purple-600 dark:text-purple-400">
                                    <div className="font-bold">Xếp loại</div>
                                </th>
                            </tr>
                        </thead>
                        <tbody>
                            <tr>
                                {[1, 2, 3, 4, 5, 6].map(i => (
                                    <td key={i} className="px-4 py-4 text-center text-lg font-bold text-gray-900 dark:text-white">
                                        {categoryTotals[i] || 0}
                                    </td>
                                ))}
                                <td className="px-4 py-4 text-center text-xl font-bold bg-emerald-500/10 text-emerald-600 dark:text-emerald-400">
                                    {totalScore}
                                </td>
                                <td className="px-4 py-4 text-center">
                                    <ClassificationBadge score={totalScore} classification={classification} />
                                </td>
                            </tr>
                        </tbody>
                    </table>
                </div>
            </div>

            {/* Score Detail Cards */}
            <div className="bg-white dark:bg-gray-900 rounded-2xl border border-gray-200 dark:border-gray-700 shadow-sm overflow-hidden">
                <div className="px-6 py-4 border-b border-gray-200 dark:border-gray-700 flex items-center gap-2">
                    <span className="material-symbols-outlined text-emerald-500">list_alt</span>
                    <h3 className="font-bold text-gray-900 dark:text-white">Chi Tiết Điểm Từng Mục</h3>
                </div>
                <div className="p-5 grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
                    {scoringRules?.categories?.map(cat => (
                        <div key={cat.id} className="bg-gray-50 dark:bg-gray-800/50 rounded-xl p-4 border border-gray-100 dark:border-gray-700">
                            <div className="flex items-center justify-between mb-3">
                                <h4 className="text-sm font-bold text-gray-900 dark:text-white flex items-center gap-2">
                                    <span className="size-2.5 rounded-full shrink-0" style={{ backgroundColor: CATEGORY_COLORS[parseInt(cat.id)] || '#888' }} />
                                    Mục {cat.id}
                                </h4>
                                <span className="text-sm font-bold text-emerald-500">{categoryTotals[parseInt(cat.id)] || 0}đ</span>
                            </div>
                            <p className="text-xs text-gray-500 dark:text-gray-400 mb-3">{cat.name}</p>
                            <div className="space-y-1.5">
                                {cat.subcategories?.map(sub => (
                                    <div key={sub.id} className="flex items-center justify-between text-xs">
                                        <span className="text-gray-600 dark:text-gray-300 truncate pr-2">{sub.id}. {sub.name}</span>
                                        <span className="font-bold text-gray-900 dark:text-white shrink-0">{scores[sub.id] || 0}</span>
                                    </div>
                                ))}
                            </div>
                        </div>
                    ))}
                </div>
            </div>

            {/* Two columns: Score Entry + History */}
            <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
                {/* Score Entry Form */}
                <div className="bg-white dark:bg-gray-900 rounded-2xl border border-gray-200 dark:border-gray-700 shadow-sm overflow-hidden">
                    <div className="bg-gradient-to-r from-emerald-500 to-green-500 px-6 py-4 flex items-center gap-3">
                        <span className="material-symbols-outlined text-white text-xl">edit_note</span>
                        <h3 className="text-white font-bold">Nhập Điểm Rèn Luyện (Nhóm A)</h3>
                    </div>
                    <div className="p-5 space-y-4">
                        <p className="text-xs text-gray-400 flex items-center gap-1">
                            <span className="material-symbols-outlined text-sm">info</span>
                            Các mục dưới đây cần Manager xác nhận trước khi được tính điểm.
                        </p>

                        {/* Criteria select */}
                        <div>
                            <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">Chọn mục điểm <span className="text-red-500">*</span></label>
                            <select
                                value={selectedCriteria}
                                onChange={e => handleCriteriaChange(e.target.value)}
                                className="w-full px-3 py-2.5 rounded-xl border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-800 text-gray-900 dark:text-white text-sm focus:outline-none focus:ring-2 focus:ring-emerald-500/50"
                            >
                                <option value="">-- Chọn mục --</option>
                                {allSubcategories.map(s => (
                                    <option key={s.code} value={s.code}>{s.name}</option>
                                ))}
                            </select>
                        </div>

                        {/* Rules info */}
                        {criteriaInfo?.rulesHtml && (
                            <div className="p-3 bg-gray-50 dark:bg-gray-800/50 border border-gray-200 dark:border-gray-700 rounded-xl text-xs text-gray-600 dark:text-gray-300" dangerouslySetInnerHTML={{ __html: criteriaInfo.rulesHtml }} />
                        )}

                        {/* GPA section for 1.1 */}
                        {selectedCriteria === '1.1' && (
                            <div className="p-4 bg-blue-50 dark:bg-blue-900/10 border border-blue-200 dark:border-blue-800/50 rounded-xl space-y-3">
                                <h4 className="text-sm font-bold text-blue-700 dark:text-blue-400 flex items-center gap-2">
                                    <span className="material-symbols-outlined text-lg">calculate</span>
                                    Tính điểm từ ĐTB
                                </h4>
                                <div className="grid grid-cols-2 gap-3">
                                    <div>
                                        <label className="block text-xs text-gray-500 mb-1">ĐTB kỳ trước</label>
                                        <input type="number" step="0.01" min="0" max="10" value={previousGpa} onChange={e => setPreviousGpa(e.target.value)} placeholder="VD: 7.5" className="w-full px-3 py-2 rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-800 text-sm text-gray-900 dark:text-white" />
                                    </div>
                                    <div>
                                        <label className="block text-xs text-gray-500 mb-1">ĐTB kỳ này</label>
                                        <input type="number" step="0.01" min="0" max="10" value={currentGpa} onChange={e => setCurrentGpa(e.target.value)} placeholder="VD: 8.0" className="w-full px-3 py-2 rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-800 text-sm text-gray-900 dark:text-white" />
                                    </div>
                                </div>
                                <button onClick={handleCalcGpa} className="w-full py-2 bg-blue-500 text-white text-sm font-medium rounded-lg hover:bg-blue-600 transition-colors flex items-center justify-center gap-2">
                                    <span className="material-symbols-outlined text-lg">calculate</span>
                                    Tính điểm
                                </button>
                                {gpaScore !== null && (
                                    <p className="text-xs text-emerald-600 dark:text-emerald-400 bg-emerald-50 dark:bg-emerald-900/20 px-3 py-2 rounded-lg">
                                        Điểm tính được: <strong>{gpaScore}</strong>đ (đã điền vào ô điểm)
                                    </p>
                                )}
                            </div>
                        )}

                        {/* Manual fields */}
                        {selectedCriteria && (
                            <>
                                <div>
                                    <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">Mô tả <span className="text-red-500">*</span></label>
                                    <textarea value={description} onChange={e => setDescription(e.target.value)} rows={2} placeholder="VD: ĐTB kỳ này 8.0, kỳ trước 7.5" className="w-full px-3 py-2.5 rounded-xl border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-800 text-gray-900 dark:text-white text-sm focus:outline-none focus:ring-2 focus:ring-emerald-500/50 resize-none" />
                                </div>
                                <div>
                                    <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">Điểm <span className="text-red-500">*</span></label>
                                    <input type="number" value={claimedScore} onChange={e => setClaimedScore(e.target.value)} placeholder="Nhập điểm" className="w-full px-3 py-2.5 rounded-xl border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-800 text-gray-900 dark:text-white text-sm focus:outline-none focus:ring-2 focus:ring-emerald-500/50" />
                                </div>
                                {criteriaInfo?.requiresEvidence && (
                                    <div>
                                        <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">Minh chứng (ảnh) <span className="text-red-500">*</span></label>
                                        <input type="file" accept="image/*" multiple onChange={e => setEvidenceFiles([...e.target.files])} className="w-full text-sm text-gray-500 file:mr-4 file:py-2 file:px-4 file:rounded-xl file:border-0 file:text-sm file:font-medium file:bg-emerald-50 dark:file:bg-emerald-900/30 file:text-emerald-600 dark:file:text-emerald-400" />
                                    </div>
                                )}
                                <button onClick={handleSubmitRequest} disabled={submitting} className="w-full py-2.5 bg-emerald-500 text-white font-bold rounded-xl hover:bg-emerald-600 transition-colors disabled:opacity-50 flex items-center justify-center gap-2 text-sm">
                                    {submitting ? 'Đang gửi...' : (
                                        <>
                                            <span className="material-symbols-outlined text-lg">send</span>
                                            Gửi Yêu Cầu
                                        </>
                                    )}
                                </button>
                            </>
                        )}
                    </div>
                </div>

                {/* History */}
                <div className="bg-white dark:bg-gray-900 rounded-2xl border border-gray-200 dark:border-gray-700 shadow-sm overflow-hidden">
                    <div className="bg-gradient-to-r from-blue-500 to-cyan-500 px-6 py-4 flex items-center gap-3">
                        <span className="material-symbols-outlined text-white text-xl">history</span>
                        <h3 className="text-white font-bold">Lịch Sử Điểm Rèn Luyện</h3>
                    </div>
                    <div className="max-h-[600px] overflow-y-auto">
                        {/* Approved activities */}
                        {approvedActivities.length > 0 && (
                            <>
                                <div className="px-5 py-3 bg-gray-50 dark:bg-gray-800/50 border-b border-gray-200 dark:border-gray-700">
                                    <h4 className="text-xs font-bold text-emerald-600 dark:text-emerald-400 uppercase tracking-wider flex items-center gap-1">
                                        <span className="material-symbols-outlined text-sm">event_available</span>
                                        Điểm từ Hoạt động
                                    </h4>
                                </div>
                                <div className="divide-y divide-gray-100 dark:divide-gray-800">
                                    {approvedActivities.map((a, i) => (
                                        <div key={i} className="px-5 py-3 flex items-center justify-between">
                                            <div className="min-w-0">
                                                <p className="text-sm text-gray-900 dark:text-white truncate">{a.activityName}</p>
                                                <p className="text-xs text-gray-400">Mục {a.scoreCategory || '-'}</p>
                                            </div>
                                            <span className="px-2.5 py-1 text-xs font-bold bg-emerald-100 dark:bg-emerald-900/30 text-emerald-600 dark:text-emerald-400 rounded-full shrink-0">
                                                +{a.scoreValue || 0}
                                            </span>
                                        </div>
                                    ))}
                                </div>
                            </>
                        )}

                        {/* Point requests */}
                        {myRequests.length > 0 && (
                            <>
                                <div className="px-5 py-3 bg-gray-50 dark:bg-gray-800/50 border-b border-gray-200 dark:border-gray-700">
                                    <h4 className="text-xs font-bold text-blue-600 dark:text-blue-400 uppercase tracking-wider flex items-center gap-1">
                                        <span className="material-symbols-outlined text-sm">edit_note</span>
                                        Điểm tự khai (Nhóm A)
                                    </h4>
                                </div>
                                <div className="divide-y divide-gray-100 dark:divide-gray-800">
                                    {myRequests.map(req => {
                                        const st = REQUEST_STATUS[req.status] || REQUEST_STATUS.PENDING
                                        return (
                                            <div key={req.id} className="px-5 py-3">
                                                <div className="flex items-center justify-between">
                                                    <div className="min-w-0">
                                                        <p className="text-sm font-medium text-gray-900 dark:text-white">Mục {req.criteriaCode}</p>
                                                        <p className="text-xs text-gray-400 truncate">{req.description}</p>
                                                    </div>
                                                    <div className="flex items-center gap-2 shrink-0">
                                                        <span className="text-sm font-bold text-gray-900 dark:text-white">{req.claimedScore}đ</span>
                                                        <span className={`px-2 py-0.5 text-[10px] font-bold rounded-full ${st.cls}`}>{st.label}</span>
                                                    </div>
                                                </div>
                                                {req.reviewComment && (
                                                    <p className="mt-1 text-xs text-gray-400 italic">"{req.reviewComment}"</p>
                                                )}
                                            </div>
                                        )
                                    })}
                                </div>
                            </>
                        )}

                        {approvedActivities.length === 0 && myRequests.length === 0 && (
                            <div className="py-12 text-center">
                                <span className="material-symbols-outlined text-5xl text-gray-300 dark:text-gray-600 block mb-2">inbox</span>
                                <p className="text-sm text-gray-400">Chưa có điểm nào</p>
                            </div>
                        )}
                    </div>
                </div>
            </div>

            {/* Classification Modal */}
            {showClassModal && (
                <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-sm p-4" onClick={() => setShowClassModal(false)}>
                    <div className="bg-white dark:bg-gray-900 rounded-2xl shadow-2xl w-full max-w-md border border-gray-200 dark:border-gray-700" onClick={e => e.stopPropagation()}>
                        <div className="px-6 py-4 border-b border-gray-200 dark:border-gray-700 flex items-center justify-between">
                            <h3 className="font-bold text-gray-900 dark:text-white">Phân loại kết quả rèn luyện</h3>
                            <button onClick={() => setShowClassModal(false)} className="p-1 hover:bg-gray-100 dark:hover:bg-gray-800 rounded-lg transition-colors">
                                <span className="material-symbols-outlined text-gray-400">close</span>
                            </button>
                        </div>
                        <div className="p-6 space-y-2">
                            {CLASSIFICATIONS.map((c, i) => (
                                <div key={i} className={`flex items-center justify-between px-4 py-3 rounded-xl ${c.cls}`}>
                                    <span className="text-sm">{c.min} ≤ Điểm {c.max < 100 ? `< ${c.max + 1}` : `≤ ${c.max}`}</span>
                                    <span className="text-sm font-bold">{c.label}</span>
                                </div>
                            ))}
                        </div>
                    </div>
                </div>
            )}
        </div>
    )
}

/* ---- Sub-components ---- */

function InfoItem({ label, value }) {
    return (
        <div>
            <span className="text-xs text-gray-400 uppercase tracking-wider">{label}</span>
            <p className="text-sm font-semibold text-gray-900 dark:text-white mt-0.5">{value}</p>
        </div>
    )
}

function ClassificationBadge({ score, classification }) {
    let cls = 'bg-gray-100 dark:bg-gray-800 text-gray-600 dark:text-gray-400'
    if (score >= 90) cls = 'bg-emerald-100 dark:bg-emerald-900/30 text-emerald-600 dark:text-emerald-400'
    else if (score >= 80) cls = 'bg-blue-100 dark:bg-blue-900/30 text-blue-600 dark:text-blue-400'
    else if (score >= 65) cls = 'bg-cyan-100 dark:bg-cyan-900/30 text-cyan-600 dark:text-cyan-400'
    else if (score >= 50) cls = 'bg-amber-100 dark:bg-amber-900/30 text-amber-600 dark:text-amber-400'
    else if (score >= 35) cls = 'bg-orange-100 dark:bg-orange-900/30 text-orange-600 dark:text-orange-400'
    else if (score > 0) cls = 'bg-red-100 dark:bg-red-900/30 text-red-600 dark:text-red-400'

    return (
        <span className={`inline-block px-3 py-1 text-xs font-bold rounded-full ${cls}`}>
            {classification || 'Chưa xếp loại'}
        </span>
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
