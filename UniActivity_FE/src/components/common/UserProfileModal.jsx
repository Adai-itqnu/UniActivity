import { useState, useEffect } from 'react'

const CATEGORY_NAMES = ['', 'Ý thức học tập', 'Ý thức chấp hành', 'Hoạt động CT-XH', 'Quan hệ cộng đồng', 'Phẩm chất công dân', 'Thành tích đặc biệt']
const CATEGORY_COLORS = ['', '#3b82f6', '#10b981', '#06b6d4', '#f59e0b', '#8b5cf6', '#ef4444']

const roleConfig = {
    ADMIN: { label: 'Quản trị viên', icon: 'admin_panel_settings', bg: 'bg-red-100 dark:bg-red-900/30 text-red-600 dark:text-red-400' },
    MANAGER: { label: 'Quản lý lớp', icon: 'shield_person', bg: 'bg-blue-100 dark:bg-blue-900/30 text-blue-600 dark:text-blue-400' },
    STUDENT: { label: 'Sinh viên', icon: 'school', bg: 'bg-emerald-100 dark:bg-emerald-900/30 text-emerald-600 dark:text-emerald-400' },
}

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

function fmtDate(s) {
    if (!s) return '—'
    try { const d = new Date(s), p = n => String(n).padStart(2, '0'); return `${p(d.getDate())}/${p(d.getMonth() + 1)}/${d.getFullYear()}` } catch { return '—' }
}

export default function UserProfileModal({ user, apiBase, onClose }) {
    const rc = roleConfig[user.role] || roleConfig.STUDENT
    const [scoreData, setScoreData] = useState(null)
    const [scoreLoading, setScoreLoading] = useState(Boolean(user.id))

    useEffect(() => {
        if (!user.id) return
        fetch(`${apiBase}/api/users/${user.id}/scores`, { credentials: 'include', headers: { 'Accept': 'application/json' } })
            .then(r => { if (!r.ok) throw new Error(); return r.json() })
            .then(data => { setScoreData(data); setScoreLoading(false) })
            .catch(() => setScoreLoading(false))
    }, [user.id, apiBase])

    const u = scoreData?.user || user

    return (
        /* Overlay - full screen backdrop */
        <div
            style={{ position: 'fixed', inset: 0, zIndex: 50, display: 'flex', alignItems: 'center', justifyContent: 'center', background: 'rgba(0,0,0,0.45)', backdropFilter: 'blur(6px)', padding: 'clamp(10px, 2vw, 24px)' }}
            onClick={onClose}
        >
            {/* Card container - scrollable */}
            <div
                className="bg-white dark:bg-gray-900 border-gray-200 dark:border-gray-700 shadow-2xl scrollbar-thin"
                style={{
                    width: 'clamp(310px, 88vw, 520px)',
                    maxHeight: '90dvh',
                    overflowY: 'auto',
                    overflowX: 'hidden',
                    borderRadius: 'clamp(14px, 2.5vw, 24px)',
                    border: '1px solid',
                }}
                onClick={e => e.stopPropagation()}
            >
                {/* ─── Gradient Banner + Avatar overlay ─── */}
                <div style={{ position: 'relative', marginBottom: 'clamp(36px, 7vw, 52px)' }}>
                    {/* Gradient background */}
                    <div style={{
                        height: 'clamp(80px, 16vw, 130px)',
                        background: 'linear-gradient(135deg, #6366f1 0%, #a855f7 50%, #ec4899 100%)',
                        borderRadius: 'clamp(14px, 2.5vw, 24px) clamp(14px, 2.5vw, 24px) 0 0',
                    }}>
                        {/* Close button */}
                        <button
                            onClick={onClose}
                            style={{
                                position: 'absolute', top: 'clamp(8px, 1.5vw, 14px)', right: 'clamp(8px, 1.5vw, 14px)',
                                width: 'clamp(30px, 5vw, 36px)', height: 'clamp(30px, 5vw, 36px)',
                                borderRadius: '10px', background: 'rgba(255,255,255,0.2)',
                                display: 'flex', alignItems: 'center', justifyContent: 'center',
                                color: '#fff', cursor: 'pointer', border: 'none', zIndex: 2,
                            }}
                            className="hover:bg-white/30 transition-colors"
                        >
                            <span className="material-symbols-outlined" style={{ fontSize: 'clamp(16px, 2.5vw, 20px)' }}>close</span>
                        </button>
                    </div>

                    {/* Avatar - absolute positioned to overlap gradient */}
                    <div style={{
                        position: 'absolute',
                        bottom: 'clamp(-30px, -6vw, -44px)',
                        left: '50%', transform: 'translateX(-50%)',
                        zIndex: 3,
                    }}>
                        <div
                            className="border-white dark:border-gray-900 bg-gray-100 dark:bg-gray-800"
                            style={{
                                width: 'clamp(64px, 13vw, 88px)', height: 'clamp(64px, 13vw, 88px)',
                                borderRadius: '50%',
                                borderWidth: 'clamp(3px, 0.5vw, 4px)', borderStyle: 'solid',
                                display: 'flex', alignItems: 'center', justifyContent: 'center',
                                overflow: 'hidden',
                                boxShadow: '0 4px 20px rgba(0,0,0,0.15)',
                            }}
                        >
                            {u.avatarUrl ? (
                                <img src={u.avatarUrl} alt="" style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
                            ) : (
                                <span className="material-symbols-outlined text-gray-400" style={{ fontSize: 'clamp(26px, 5vw, 36px)' }}>{rc.icon}</span>
                            )}
                        </div>
                    </div>
                </div>

                {/* ─── Name + Role ─── */}
                <div style={{ textAlign: 'center', padding: '0 clamp(16px, 3vw, 24px) clamp(6px, 1vw, 10px)' }}>
                    <h3 className="text-gray-900 dark:text-white" style={{ fontSize: 'clamp(15px, 2.8vw, 20px)', fontWeight: 700, margin: 0 }}>{u.fullName}</h3>
                    <p className="text-gray-400" style={{ fontSize: 'clamp(11px, 1.8vw, 14px)', marginTop: '2px' }}>@{u.username}</p>
                    <span
                        className={`inline-flex items-center gap-1 font-bold ${rc.bg}`}
                        style={{ marginTop: 'clamp(6px, 1vw, 10px)', padding: 'clamp(3px, 0.5vw, 5px) clamp(10px, 1.5vw, 14px)', borderRadius: '100px', fontSize: 'clamp(10px, 1.5vw, 12px)' }}
                    >
                        <span className="material-symbols-outlined" style={{ fontSize: 'clamp(12px, 1.8vw, 14px)' }}>{rc.icon}</span>
                        {rc.label}
                    </span>
                </div>

                {/* ─── Score Donut Chart ─── */}
                {scoreData && (
                    <div
                        className="bg-gray-50 dark:bg-gray-800/50 border-gray-100 dark:border-gray-700"
                        style={{
                            margin: 'clamp(10px, 2vw, 16px) clamp(14px, 3vw, 24px) 0',
                            borderRadius: 'clamp(10px, 2vw, 16px)',
                            padding: 'clamp(12px, 2.5vw, 20px)',
                            border: '1px solid',
                        }}
                    >
                        <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: 'clamp(10px, 2vw, 14px)' }}>
                            <span className="material-symbols-outlined text-purple-500" style={{ fontSize: 'clamp(14px, 2.2vw, 18px)' }}>star</span>
                            <h4 className="text-gray-900 dark:text-white" style={{ fontSize: 'clamp(12px, 1.8vw, 14px)', fontWeight: 700, margin: 0 }}>Phân phối điểm rèn luyện</h4>
                        </div>
                        <div style={{ display: 'flex', flexWrap: 'wrap', alignItems: 'center', justifyContent: 'center', gap: 'clamp(12px, 2.5vw, 24px)' }}>
                            <div style={{ flexShrink: 0 }}>
                                <ScoreDonut categoryTotals={scoreData.categoryTotals} totalScore={scoreData.totalScore} classification={scoreData.classification} />
                            </div>
                            <div style={{ flex: 1, minWidth: 'clamp(140px, 30vw, 200px)', display: 'flex', flexDirection: 'column', gap: 'clamp(4px, 0.8vw, 7px)' }}>
                                {[1, 2, 3, 4, 5, 6].map(i => (
                                    <div key={i} style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', fontSize: 'clamp(10px, 1.6vw, 12px)' }}>
                                        <div style={{ display: 'flex', alignItems: 'center', gap: 'clamp(4px, 0.8vw, 8px)', minWidth: 0 }}>
                                            <span style={{ width: 'clamp(6px, 1vw, 8px)', height: 'clamp(6px, 1vw, 8px)', borderRadius: '50%', backgroundColor: CATEGORY_COLORS[i], flexShrink: 0 }} />
                                            <span className="text-gray-500 dark:text-gray-400" style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{CATEGORY_NAMES[i]}</span>
                                        </div>
                                        <span className="text-gray-700 dark:text-gray-200" style={{ fontWeight: 700, marginLeft: '8px' }}>{scoreData.categoryTotals?.[i] || 0}</span>
                                    </div>
                                ))}
                                <div className="border-t border-gray-200 dark:border-gray-700" style={{ paddingTop: 'clamp(6px, 1vw, 10px)', marginTop: 'clamp(4px, 0.6vw, 6px)', display: 'flex', alignItems: 'flex-end', justifyContent: 'space-between' }}>
                                    <span className="text-gray-900 dark:text-white" style={{ fontSize: 'clamp(18px, 3.5vw, 26px)', fontWeight: 900 }}>
                                        {scoreData.totalScore}<span className="text-gray-400" style={{ fontSize: 'clamp(10px, 1.5vw, 13px)', fontWeight: 400, marginLeft: '2px' }}>pts</span>
                                    </span>
                                    <span style={{
                                        fontSize: 'clamp(9px, 1.4vw, 11px)', fontWeight: 700,
                                        padding: 'clamp(2px, 0.4vw, 4px) clamp(6px, 1vw, 10px)', borderRadius: '100px',
                                        color: getClassColor(scoreData.classification),
                                        backgroundColor: `${getClassColor(scoreData.classification)}18`,
                                    }}>
                                        {scoreData.classification || 'Chưa xếp loại'}
                                    </span>
                                </div>
                            </div>
                        </div>
                    </div>
                )}

                {scoreLoading && (
                    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', padding: 'clamp(16px, 3vw, 24px)' }}>
                        <div className="w-5 h-5 border-2 border-purple-500/30 border-t-purple-500 rounded-full animate-spin" />
                        <span className="text-gray-400" style={{ marginLeft: '8px', fontSize: 'clamp(10px, 1.5vw, 12px)' }}>Đang tải điểm...</span>
                    </div>
                )}

                {/* ─── Info Fields ─── */}
                <div style={{
                    padding: 'clamp(10px, 2vw, 16px) clamp(14px, 3vw, 24px) clamp(16px, 3vw, 24px)',
                    display: 'flex', flexDirection: 'column',
                    gap: 'clamp(6px, 1vw, 10px)',
                }}>
                    <InfoRow icon="mail" label="Email" value={u.email} />
                    <InfoRow icon="call" label="Điện thoại" value={u.phone || '—'} />
                    <InfoRow icon="school" label="Lớp" value={u.className || '—'} />
                    {u.facultyName && <InfoRow icon="domain" label="Khoa" value={u.facultyName} />}
                    <InfoRow icon="event" label="Ngày tạo" value={fmtDate(u.createdAt)} />
                    <StatusRow status={u.status} />
                </div>
            </div>
        </div>
    )
}

/* ─── Score Donut (SVG viewBox = fixed, container = fluid) ─── */
function ScoreDonut({ categoryTotals, classification }) {
    const segments = []
    let offset = 0
    const C = 2 * Math.PI * 54 // circumference for r=54
    for (let i = 1; i <= 6; i++) {
        const value = categoryTotals?.[i] || 0
        if (value > 0) { const pct = value / 100; segments.push({ color: CATEGORY_COLORS[i], pct, offset }); offset += pct }
    }
    const classColor = getClassColor(classification)
    return (
        <div style={{ width: 'clamp(84px, 16vw, 120px)', height: 'clamp(84px, 16vw, 120px)', position: 'relative' }}>
            <svg width="100%" height="100%" viewBox="0 0 120 120" style={{ transform: 'rotate(-90deg)' }}>
                <circle cx="60" cy="60" r="54" fill="none" stroke="currentColor" strokeWidth="12" className="text-gray-200 dark:text-gray-700" />
                {segments.map((seg, idx) => (
                    <circle key={idx} cx="60" cy="60" r="54" fill="none" stroke={seg.color} strokeWidth="12" strokeLinecap="round"
                        strokeDasharray={`${seg.pct * C} ${C}`} strokeDashoffset={-seg.offset * C}
                        style={{ transition: 'stroke-dasharray 0.8s ease, stroke-dashoffset 0.8s ease' }} />
                ))}
            </svg>
            <div style={{ position: 'absolute', inset: 0, display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center' }}>
                <span style={{ fontSize: 'clamp(11px, 2.2vw, 18px)', fontWeight: 900, color: classColor, lineHeight: 1.2 }}>{classification || '—'}</span>
                <span className="text-gray-400" style={{ fontSize: 'clamp(7px, 1vw, 9px)', textTransform: 'uppercase', letterSpacing: '0.06em', fontWeight: 600 }}>Xếp loại</span>
            </div>
        </div>
    )
}

/* ─── Info Row ─── */
function InfoRow({ icon, label, value }) {
    return (
        <div
            className="bg-gray-50 dark:bg-gray-800/50"
            style={{
                display: 'flex', alignItems: 'center',
                gap: 'clamp(8px, 1.5vw, 12px)',
                padding: 'clamp(8px, 1.5vw, 12px) clamp(10px, 2vw, 16px)',
                borderRadius: 'clamp(8px, 1.5vw, 12px)',
                fontSize: 'clamp(11px, 1.8vw, 14px)',
            }}
        >
            <span className="material-symbols-outlined text-gray-400" style={{ fontSize: 'clamp(14px, 2vw, 16px)' }}>{icon}</span>
            <span className="text-gray-400" style={{ width: 'clamp(55px, 10vw, 80px)', flexShrink: 0, fontSize: 'clamp(10px, 1.5vw, 12px)' }}>{label}</span>
            <span className="text-gray-700 dark:text-gray-200" style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', flex: 1 }}>{value}</span>
        </div>
    )
}

/* ─── Status Row ─── */
function StatusRow({ status }) {
    const active = status === 'ACTIVE'
    return (
        <div
            className="bg-gray-50 dark:bg-gray-800/50"
            style={{
                display: 'flex', alignItems: 'center',
                gap: 'clamp(8px, 1.5vw, 12px)',
                padding: 'clamp(8px, 1.5vw, 12px) clamp(10px, 2vw, 16px)',
                borderRadius: 'clamp(8px, 1.5vw, 12px)',
                fontSize: 'clamp(11px, 1.8vw, 14px)',
            }}
        >
            <span className="material-symbols-outlined text-gray-400" style={{ fontSize: 'clamp(14px, 2vw, 16px)' }}>circle</span>
            <span className="text-gray-400" style={{ width: 'clamp(55px, 10vw, 80px)', flexShrink: 0, fontSize: 'clamp(10px, 1.5vw, 12px)' }}>Trạng thái</span>
            <span className={`inline-flex items-center gap-1 font-semibold ${active ? 'text-green-600' : 'text-red-500'}`}>
                <span className={`rounded-full ${active ? 'bg-green-500' : 'bg-red-500'}`} style={{ width: 'clamp(5px, 0.8vw, 7px)', height: 'clamp(5px, 0.8vw, 7px)' }} />
                {active ? 'Đang hoạt động' : 'Đã khóa'}
            </span>
        </div>
    )
}
