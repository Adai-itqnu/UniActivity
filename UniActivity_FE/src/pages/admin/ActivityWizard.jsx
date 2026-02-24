import { useState, useEffect } from 'react'

const API = '/admin/activities/api'
const steps = ['Thông tin', 'Slots', 'Điểm/Giải', 'Xem lại']

function fmt(d) { return d ? new Date(d).toISOString().slice(0, 16) : '' }
function fmtD(d) { return d ? new Date(d).toLocaleString('vi-VN') : '—' }

export default function ActivityWizard({ activity, onClose, onSaved }) {
    const isEdit = !!activity
    const [step, setStep] = useState(1)
    const [saving, setSaving] = useState(false)
    const [error, setError] = useState('')

    // Step1: basic info
    const [form, setForm] = useState({
        name: '', description: '', bannerUrl: '', location: '',
        startTime: '', endTime: '', registrationDeadline: '',
        scope: 'SCHOOL', status: 'DRAFT', semesterId: '',
    })
    const [uploading, setUploading] = useState(false)

    // Lookups
    const [semesters, setSemesters] = useState([])
    const [faculties, setFaculties] = useState([])
    const [classes, setClasses] = useState([])

    // Step2: slots
    const [slots, setSlots] = useState([])
    const [slotForm, setSlotForm] = useState({ facultyId: '', classId: '', maxQuantity: '' })

    // Step3: score options
    const [scores, setScores] = useState([])
    const [scoreForm, setScoreForm] = useState({ scoreCategory: '', name: '', scoreValue: '', description: '' })
    const [scoringCategories, setScoringCategories] = useState([])

    useEffect(() => {
        Promise.all([
            fetch('/admin/semesters/api', { credentials: 'include' }).then(r => r.json()),
            fetch('/admin/faculties/api/active', { credentials: 'include' }).then(r => r.json()),
            fetch('/admin/classes/api', { credentials: 'include' }).then(r => r.json()),
            fetch(`${API}/scoring-rules`, { credentials: 'include' }).then(r => r.json()),
        ]).then(([sem, fac, cls, rules]) => {
            setSemesters(sem || [])
            setFaculties(fac || [])
            setClasses(cls || [])
            // Extract AUTO_ACTIVITY categories
            const cats = []
            if (rules?.categories) {
                rules.categories.forEach(cat => {
                    cat.subcategories?.filter(s => s.type === 'AUTO_ACTIVITY').forEach(sub => {
                        cats.push({ id: sub.id, name: `${sub.id}. ${sub.name}`, group: `${cat.id}. ${cat.name}` })
                    })
                })
            }
            setScoringCategories(cats)
        }).catch(() => { })

        if (isEdit) {
            setForm({
                name: activity.name || '', description: activity.description || '',
                bannerUrl: activity.bannerUrl || '', location: activity.location || '',
                startTime: fmt(activity.startTime), endTime: fmt(activity.endTime),
                registrationDeadline: fmt(activity.registrationDeadline),
                scope: activity.scope || 'SCHOOL', status: activity.status || 'DRAFT',
                semesterId: activity.semesterId || '',
            })
            // Load existing slots & scores
            fetch(`${API}/${activity.id}/slots`, { credentials: 'include' })
                .then(r => r.json()).then(d => setSlots(d.map(s => ({ ...s, _existing: true })))).catch(() => { })
            fetch(`${API}/${activity.id}/score-options`, { credentials: 'include' })
                .then(r => r.json()).then(d => setScores(d.map(s => ({ ...s, _existing: true })))).catch(() => { })
        }
    }, [])

    const handleBannerUpload = async (e) => {
        const file = e.target.files[0]; if (!file) return
        setUploading(true)
        try {
            const fd = new FormData(); fd.append('file', file)
            const res = await fetch(`${API}/upload-banner`, { method: 'POST', credentials: 'include', body: fd })
            if (!res.ok) throw new Error()
            const data = await res.json()
            setForm(p => ({ ...p, bannerUrl: data.bannerUrl }))
        } catch { setError('Upload thất bại') }
        setUploading(false)
    }

    // Slot helpers
    const filteredClasses = classes.filter(c => !slotForm.facultyId || String(c.facultyId) === slotForm.facultyId)
    const addSlot = () => {
        if (form.scope === 'SCHOOL') {
            if (!slotForm.maxQuantity) return
            setSlots([{ facultyId: null, facultyName: 'Toàn trường', classId: null, className: 'Tất cả', maxQuantity: Number(slotForm.maxQuantity) }])
        } else {
            if (!slotForm.facultyId || !slotForm.maxQuantity) return
            const fac = faculties.find(f => String(f.id) === slotForm.facultyId)
            const cls = slotForm.classId ? classes.find(c => String(c.id) === slotForm.classId) : null
            setSlots(p => [...p, {
                facultyId: Number(slotForm.facultyId), facultyName: fac?.name || '',
                classId: cls ? cls.id : null, className: cls?.name || 'Tất cả lớp',
                maxQuantity: Number(slotForm.maxQuantity)
            }])
        }
        setSlotForm({ facultyId: '', classId: '', maxQuantity: '' })
    }
    const removeSlot = (i) => setSlots(p => p.filter((_, idx) => idx !== i))

    // Score helpers  
    const addScore = () => {
        if (!scoreForm.scoreCategory || !scoreForm.name || !scoreForm.scoreValue) return
        const cat = scoringCategories.find(c => c.id === scoreForm.scoreCategory)
        setScores(p => [...p, { ...scoreForm, scoreValue: Number(scoreForm.scoreValue), categoryName: cat?.name || scoreForm.scoreCategory }])
        setScoreForm({ scoreCategory: '', name: '', scoreValue: '', description: '' })
    }
    const removeScore = (i) => setScores(p => p.filter((_, idx) => idx !== i))

    const goNext = () => {
        if (step === 1 && !form.name.trim()) { setError('Tên hoạt động không được để trống'); return }
        setError(''); setStep(s => Math.min(s + 1, 4))
    }

    const handleSave = async () => {
        setSaving(true); setError('')
        try {
            // Save activity
            const url = isEdit ? `${API}/${activity.id}` : API
            const body = { ...form, semesterId: form.semesterId ? Number(form.semesterId) : null }
            const res = await fetch(url, { method: isEdit ? 'PUT' : 'POST', headers: { 'Content-Type': 'application/json' }, credentials: 'include', body: JSON.stringify(body) })
            if (!res.ok) throw new Error('Lưu hoạt động thất bại')
            const saved = await res.json()
            const actId = saved.id

            // Save slots (new ones only)
            for (const slot of slots.filter(s => !s._existing)) {
                await fetch(`${API}/${actId}/slots`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, credentials: 'include', body: JSON.stringify(slot) })
            }
            // Save scores (new ones only)
            for (const score of scores.filter(s => !s._existing)) {
                await fetch(`${API}/${actId}/score-options`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, credentials: 'include', body: JSON.stringify(score) })
            }
            onSaved()
        } catch (err) { setError(err.message) } finally { setSaving(false) }
    }

    const inp = "w-full h-10 px-3 rounded-lg border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 text-sm text-gray-800 dark:text-white focus:outline-none focus:ring-2 focus:ring-primary/30 transition-colors"
    const lbl = "text-xs font-semibold uppercase tracking-wide text-gray-500 dark:text-gray-400"

    return (
        <div className="fixed inset-0 z-50 bg-black/40 backdrop-blur-sm overflow-y-auto" onClick={onClose}>
            <div className="min-h-screen flex items-start justify-center py-8">
                <div className="bg-white dark:bg-gray-900 rounded-2xl shadow-2xl w-full max-w-3xl mx-4 border border-gray-200 dark:border-gray-700" onClick={e => e.stopPropagation()}>
                    {/* Header */}
                    <div className="flex items-center justify-between px-6 py-4 border-b border-gray-100 dark:border-gray-800">
                        <h3 className="text-lg font-semibold text-gray-800 dark:text-white">{isEdit ? 'Chỉnh sửa hoạt động' : 'Tạo hoạt động mới'}</h3>
                        <button onClick={onClose} className="w-8 h-8 rounded-lg text-gray-400 hover:bg-gray-100 dark:hover:bg-gray-800 flex items-center justify-center"><span className="material-symbols-outlined">close</span></button>
                    </div>

                    {/* Wizard Steps */}
                    <div className="px-6 pt-4">
                        <div className="flex items-center justify-between mb-6">
                            {steps.map((s, i) => (
                                <div key={i} className="flex items-center gap-2 flex-1">
                                    <div className={`w-8 h-8 rounded-full flex items-center justify-center text-sm font-bold shrink-0 ${i + 1 < step ? 'bg-green-500 text-white' : i + 1 === step ? 'bg-primary text-white' : 'bg-gray-200 dark:bg-gray-700 text-gray-500'}`}>
                                        {i + 1 < step ? <span className="material-symbols-outlined text-base">check</span> : i + 1}
                                    </div>
                                    <span className={`text-xs font-medium hidden sm:block ${i + 1 === step ? 'text-primary' : 'text-gray-400'}`}>{s}</span>
                                    {i < 3 && <div className={`flex-1 h-0.5 mx-2 ${i + 1 < step ? 'bg-green-400' : 'bg-gray-200 dark:bg-gray-700'}`} />}
                                </div>
                            ))}
                        </div>
                    </div>

                    {error && <div className="mx-6 mb-3 bg-red-500/10 border border-red-500/30 text-red-500 px-4 py-3 rounded-lg text-sm flex items-center gap-2"><span className="material-symbols-outlined text-lg">error</span>{error}</div>}

                    <div className="px-6 pb-6 max-h-[60vh] overflow-y-auto">
                        {/* ═══ STEP 1: Basic Info ═══ */}
                        {step === 1 && (
                            <div className="space-y-4">
                                <div className="space-y-1.5">
                                    <label className={lbl}>Ảnh banner</label>
                                    <div className="flex items-center gap-3">
                                        {form.bannerUrl && <div className="relative w-32 h-20 rounded-lg overflow-hidden border"><img src={form.bannerUrl} alt="" className="w-full h-full object-cover" /><button type="button" onClick={() => setForm(p => ({ ...p, bannerUrl: '' }))} className="absolute top-1 right-1 w-5 h-5 rounded-full bg-red-500 text-white flex items-center justify-center text-xs">✕</button></div>}
                                        <label className="inline-flex items-center gap-2 px-3 py-2 rounded-lg bg-gray-100 dark:bg-gray-800 text-sm cursor-pointer hover:bg-gray-200 dark:hover:bg-gray-700"><span className="material-symbols-outlined text-lg">upload</span>{uploading ? 'Đang tải...' : 'Chọn ảnh'}<input type="file" accept="image/*" className="hidden" onChange={handleBannerUpload} disabled={uploading} /></label>
                                    </div>
                                </div>
                                <div className="grid grid-cols-3 gap-4">
                                    <div className="col-span-2 space-y-1.5"><label className={lbl}>Tên hoạt động *</label><input type="text" value={form.name} onChange={e => setForm(p => ({ ...p, name: e.target.value }))} className={inp} placeholder="VD: Hội thao sinh viên" /></div>
                                    <div className="space-y-1.5"><label className={lbl}>Phạm vi *</label><select value={form.scope} onChange={e => { setForm(p => ({ ...p, scope: e.target.value })); setSlots([]) }} className={inp}><option value="SCHOOL">🏫 Toàn trường</option><option value="FACULTY">🏛️ Theo khoa</option></select></div>
                                </div>
                                <div className="space-y-1.5"><label className={lbl}>Mô tả</label><textarea value={form.description} onChange={e => setForm(p => ({ ...p, description: e.target.value }))} rows={2} className={inp + " h-auto py-2"} placeholder="Mô tả ngắn..." /></div>
                                <div className="space-y-1.5"><label className={lbl}>Địa điểm</label><input type="text" value={form.location} onChange={e => setForm(p => ({ ...p, location: e.target.value }))} className={inp} placeholder="VD: Hội trường A" /></div>
                                <div className="grid grid-cols-3 gap-4">
                                    <div className="space-y-1.5"><label className={lbl}>Bắt đầu</label><input type="datetime-local" value={form.startTime} onChange={e => setForm(p => ({ ...p, startTime: e.target.value }))} className={inp} /></div>
                                    <div className="space-y-1.5"><label className={lbl}>Kết thúc</label><input type="datetime-local" value={form.endTime} onChange={e => setForm(p => ({ ...p, endTime: e.target.value }))} className={inp} /></div>
                                    <div className="space-y-1.5"><label className={lbl}>Hạn đăng ký</label><input type="datetime-local" value={form.registrationDeadline} onChange={e => setForm(p => ({ ...p, registrationDeadline: e.target.value }))} className={inp} /></div>
                                </div>
                                <div className="grid grid-cols-2 gap-4">
                                    <div className="space-y-1.5"><label className={lbl}>Trạng thái</label><select value={form.status} onChange={e => setForm(p => ({ ...p, status: e.target.value }))} className={inp}><option value="DRAFT">📝 Nháp</option><option value="OPEN">✅ Mở đăng ký</option></select></div>
                                    <div className="space-y-1.5"><label className={lbl}>Học kỳ</label><select value={form.semesterId} onChange={e => setForm(p => ({ ...p, semesterId: e.target.value }))} className={inp}><option value="">— Chọn —</option>{semesters.map(s => <option key={s.id} value={s.id}>{s.name}</option>)}</select></div>
                                </div>
                            </div>
                        )}

                        {/* ═══ STEP 2: Slots ═══ */}
                        {step === 2 && (
                            <div className="space-y-4">
                                <div className="bg-blue-50 dark:bg-blue-900/20 border border-blue-200 dark:border-blue-800 rounded-xl p-3 text-sm text-blue-700 dark:text-blue-300 flex items-start gap-2">
                                    <span className="material-symbols-outlined text-lg mt-0.5">info</span>
                                    {form.scope === 'SCHOOL' ? 'Hoạt động toàn trường: Nhập số lượng tối đa.' : 'Hoạt động theo khoa: Chọn khoa, lớp (tùy chọn) và số lượng slot.'}
                                </div>

                                {form.scope === 'SCHOOL' ? (
                                    <div className="flex items-end gap-3">
                                        <div className="flex-1 space-y-1.5"><label className={lbl}>Số lượng tối đa *</label><input type="number" min="1" value={slotForm.maxQuantity} onChange={e => setSlotForm(p => ({ ...p, maxQuantity: e.target.value }))} className={inp} placeholder="VD: 100" /></div>
                                        <button onClick={addSlot} className="h-10 px-4 rounded-lg bg-green-500 hover:bg-green-600 text-white text-sm font-medium flex items-center gap-1"><span className="material-symbols-outlined text-lg">add</span>Đặt</button>
                                    </div>
                                ) : (
                                    <div className="grid grid-cols-4 gap-3 items-end">
                                        <div className="space-y-1.5"><label className={lbl}>Khoa *</label><select value={slotForm.facultyId} onChange={e => setSlotForm(p => ({ ...p, facultyId: e.target.value, classId: '' }))} className={inp}><option value="">— Chọn —</option>{faculties.map(f => <option key={f.id} value={f.id}>{f.name}</option>)}</select></div>
                                        <div className="space-y-1.5"><label className={lbl}>Lớp (tùy chọn)</label><select value={slotForm.classId} onChange={e => setSlotForm(p => ({ ...p, classId: e.target.value }))} className={inp}><option value="">Tất cả lớp</option>{filteredClasses.map(c => <option key={c.id} value={c.id}>{c.name}</option>)}</select></div>
                                        <div className="space-y-1.5"><label className={lbl}>Số lượng *</label><input type="number" min="1" value={slotForm.maxQuantity} onChange={e => setSlotForm(p => ({ ...p, maxQuantity: e.target.value }))} className={inp} placeholder="50" /></div>
                                        <button onClick={addSlot} className="h-10 px-4 rounded-lg bg-green-500 hover:bg-green-600 text-white text-sm font-medium flex items-center gap-1"><span className="material-symbols-outlined text-lg">add</span>Thêm</button>
                                    </div>
                                )}

                                <div className="border border-gray-200 dark:border-gray-700 rounded-xl overflow-hidden">
                                    <table className="w-full text-sm">
                                        <thead><tr className="bg-gray-50 dark:bg-gray-800"><th className="text-left px-4 py-2.5 text-xs font-semibold text-gray-500">Khoa</th><th className="text-left px-4 py-2.5 text-xs font-semibold text-gray-500">Lớp</th><th className="text-center px-4 py-2.5 text-xs font-semibold text-gray-500">Số lượng</th><th className="w-12"></th></tr></thead>
                                        <tbody className="divide-y divide-gray-100 dark:divide-gray-800">
                                            {slots.length > 0 ? slots.map((s, i) => (
                                                <tr key={i} className="hover:bg-gray-50 dark:hover:bg-gray-800/50">
                                                    <td className="px-4 py-2.5 text-gray-700 dark:text-gray-300">{s.facultyName || 'Toàn trường'}</td>
                                                    <td className="px-4 py-2.5 text-gray-500">{s.className || 'Tất cả'}</td>
                                                    <td className="px-4 py-2.5 text-center"><span className="inline-flex items-center px-2 py-0.5 rounded bg-primary/10 text-primary text-xs font-bold">{s.maxQuantity}</span></td>
                                                    <td className="px-4 py-2.5">{!s._existing && <button onClick={() => removeSlot(i)} className="text-red-400 hover:text-red-600"><span className="material-symbols-outlined text-lg">close</span></button>}</td>
                                                </tr>
                                            )) : <tr><td colSpan={4} className="px-4 py-6 text-center text-gray-400">Chưa thêm slot nào</td></tr>}
                                        </tbody>
                                    </table>
                                </div>
                            </div>
                        )}

                        {/* ═══ STEP 3: Score Options ═══ */}
                        {step === 3 && (
                            <div className="space-y-4">
                                <div className="bg-amber-50 dark:bg-amber-900/20 border border-amber-200 dark:border-amber-800 rounded-xl p-3 text-sm text-amber-700 dark:text-amber-300 flex items-start gap-2">
                                    <span className="material-symbols-outlined text-lg mt-0.5">emoji_events</span>
                                    Thêm các mức điểm/giải thưởng. VD: Cổ vũ = 2đ, Giải nhất = 10đ.
                                </div>

                                <div className="grid grid-cols-5 gap-3 items-end">
                                    <div className="space-y-1.5"><label className={lbl}>Mục điểm *</label><select value={scoreForm.scoreCategory} onChange={e => setScoreForm(p => ({ ...p, scoreCategory: e.target.value }))} className={inp}><option value="">— Chọn —</option>{scoringCategories.map(c => <option key={c.id} value={c.id}>{c.name}</option>)}</select></div>
                                    <div className="space-y-1.5"><label className={lbl}>Loại tham gia *</label><input type="text" value={scoreForm.name} onChange={e => setScoreForm(p => ({ ...p, name: e.target.value }))} className={inp} placeholder="VD: Giải nhất" /></div>
                                    <div className="space-y-1.5"><label className={lbl}>Điểm *</label><input type="number" min="0" value={scoreForm.scoreValue} onChange={e => setScoreForm(p => ({ ...p, scoreValue: e.target.value }))} className={inp} placeholder="10" /></div>
                                    <div className="space-y-1.5"><label className={lbl}>Ghi chú</label><input type="text" value={scoreForm.description} onChange={e => setScoreForm(p => ({ ...p, description: e.target.value }))} className={inp} placeholder="Tùy chọn" /></div>
                                    <button onClick={addScore} className="h-10 px-4 rounded-lg bg-amber-500 hover:bg-amber-600 text-white text-sm font-medium flex items-center gap-1"><span className="material-symbols-outlined text-lg">add</span>Thêm</button>
                                </div>

                                <div className="border border-gray-200 dark:border-gray-700 rounded-xl overflow-hidden">
                                    <table className="w-full text-sm">
                                        <thead><tr className="bg-gray-50 dark:bg-gray-800"><th className="text-left px-4 py-2.5 text-xs font-semibold text-gray-500">Mục điểm</th><th className="text-left px-4 py-2.5 text-xs font-semibold text-gray-500">Loại tham gia</th><th className="text-center px-4 py-2.5 text-xs font-semibold text-gray-500">Điểm</th><th className="text-left px-4 py-2.5 text-xs font-semibold text-gray-500">Ghi chú</th><th className="w-12"></th></tr></thead>
                                        <tbody className="divide-y divide-gray-100 dark:divide-gray-800">
                                            {scores.length > 0 ? scores.map((s, i) => (
                                                <tr key={i} className="hover:bg-gray-50 dark:hover:bg-gray-800/50">
                                                    <td className="px-4 py-2.5"><span className="inline-flex items-center px-2 py-0.5 rounded bg-blue-100 dark:bg-blue-900/40 text-blue-600 dark:text-blue-400 text-xs font-medium">{s.categoryName || s.scoreCategory}</span></td>
                                                    <td className="px-4 py-2.5 text-gray-700 dark:text-gray-300">{s.name}</td>
                                                    <td className="px-4 py-2.5 text-center"><span className="inline-flex items-center px-2 py-0.5 rounded bg-green-100 dark:bg-green-900/40 text-green-600 text-xs font-bold">+{s.scoreValue}đ</span></td>
                                                    <td className="px-4 py-2.5 text-gray-400 text-xs">{s.description || '—'}</td>
                                                    <td className="px-4 py-2.5">{!s._existing && <button onClick={() => removeScore(i)} className="text-red-400 hover:text-red-600"><span className="material-symbols-outlined text-lg">close</span></button>}</td>
                                                </tr>
                                            )) : <tr><td colSpan={5} className="px-4 py-6 text-center text-gray-400">Chưa thêm mức điểm nào</td></tr>}
                                        </tbody>
                                    </table>
                                </div>
                            </div>
                        )}

                        {/* ═══ STEP 4: Review ═══ */}
                        {step === 4 && (
                            <div className="space-y-4">
                                <div className="grid grid-cols-2 gap-4">
                                    <div className="bg-gray-50 dark:bg-gray-800 rounded-xl p-4">
                                        <h4 className="text-sm font-semibold text-gray-700 dark:text-gray-300 mb-3 flex items-center gap-2"><span className="material-symbols-outlined text-lg">info</span>Thông tin cơ bản</h4>
                                        <div className="space-y-2 text-xs">
                                            <div className="flex"><span className="w-24 text-gray-400 shrink-0">Tên:</span><span className="font-medium text-gray-700 dark:text-gray-200">{form.name}</span></div>
                                            <div className="flex"><span className="w-24 text-gray-400 shrink-0">Phạm vi:</span><span>{form.scope === 'SCHOOL' ? '🏫 Toàn trường' : '🏛️ Theo khoa'}</span></div>
                                            <div className="flex"><span className="w-24 text-gray-400 shrink-0">Địa điểm:</span><span>{form.location || '—'}</span></div>
                                            <div className="flex"><span className="w-24 text-gray-400 shrink-0">Bắt đầu:</span><span>{fmtD(form.startTime)}</span></div>
                                            <div className="flex"><span className="w-24 text-gray-400 shrink-0">Trạng thái:</span><span>{form.status === 'DRAFT' ? '📝 Nháp' : '✅ Mở'}</span></div>
                                        </div>
                                    </div>
                                    <div className="space-y-4">
                                        <div className="bg-green-50 dark:bg-green-900/20 rounded-xl p-4">
                                            <h4 className="text-sm font-semibold text-gray-700 dark:text-gray-300 mb-2 flex items-center gap-2"><span className="material-symbols-outlined text-lg">groups</span>Slots ({slots.length})</h4>
                                            {slots.length > 0 ? slots.map((s, i) => <div key={i} className="text-xs text-gray-600 dark:text-gray-400">{s.facultyName || 'Toàn trường'} / {s.className || 'Tất cả'}: <strong>{s.maxQuantity}</strong></div>) : <p className="text-xs text-gray-400">Không có</p>}
                                        </div>
                                        <div className="bg-amber-50 dark:bg-amber-900/20 rounded-xl p-4">
                                            <h4 className="text-sm font-semibold text-gray-700 dark:text-gray-300 mb-2 flex items-center gap-2"><span className="material-symbols-outlined text-lg">emoji_events</span>Điểm ({scores.length})</h4>
                                            {scores.length > 0 ? scores.map((s, i) => <div key={i} className="text-xs text-gray-600 dark:text-gray-400">{s.name}: <strong className="text-green-600">+{s.scoreValue}đ</strong></div>) : <p className="text-xs text-gray-400">Không có</p>}
                                        </div>
                                    </div>
                                </div>
                            </div>
                        )}
                    </div>

                    {/* Footer */}
                    <div className="px-6 py-4 border-t border-gray-100 dark:border-gray-800 flex items-center justify-between">
                        {step > 1 ? <button onClick={() => setStep(s => s - 1)} className="inline-flex items-center gap-2 px-4 py-2.5 rounded-xl text-sm font-medium text-gray-700 dark:text-gray-300 border border-gray-200 dark:border-gray-700 hover:bg-gray-50 dark:hover:bg-gray-800"><span className="material-symbols-outlined text-lg">arrow_back</span>Quay lại</button> : <div />}
                        {step < 4 ? (
                            <button onClick={goNext} className="inline-flex items-center gap-2 px-5 py-2.5 rounded-xl bg-primary hover:bg-primary-dark text-white text-sm font-medium shadow-md shadow-primary/25">Tiếp theo<span className="material-symbols-outlined text-lg">arrow_forward</span></button>
                        ) : (
                            <button onClick={handleSave} disabled={saving} className="inline-flex items-center gap-2 px-5 py-2.5 rounded-xl bg-green-500 hover:bg-green-600 text-white text-sm font-medium shadow-md shadow-green-500/25 disabled:opacity-50">
                                {saving ? 'Đang lưu...' : <><span className="material-symbols-outlined text-lg">save</span>Lưu hoạt động</>}
                            </button>
                        )}
                    </div>
                </div>
            </div>
        </div>
    )
}
