import { useState, useEffect } from 'react'
import { useOutletContext, useNavigate } from 'react-router-dom'
import {
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
} from 'recharts'

/* ── Placeholder chart data ── */
const defaultChartData = [
  { day: 'T2', value: 0 },
  { day: 'T3', value: 0 },
  { day: 'T4', value: 0 },
  { day: 'T5', value: 0 },
  { day: 'T6', value: 0 },
  { day: 'T7', value: 0 },
  { day: 'CN', value: 0 },
]

/* ── Helpers ── */
function timeAgo(dateStr) {
  if (!dateStr) return ''
  const now = new Date()
  const d = new Date(dateStr)
  const diffMs = now - d
  const mins = Math.floor(diffMs / 60000)
  if (mins < 1) return 'Vừa xong'
  if (mins < 60) return `${mins} phút trước`
  const hours = Math.floor(mins / 60)
  if (hours < 24) return `${hours} giờ trước`
  const days = Math.floor(hours / 24)
  if (days < 30) return `${days} ngày trước`
  return `${Math.floor(days / 30)} tháng trước`
}

function formatDateTime(dateStr) {
  if (!dateStr) return ''
  const d = new Date(dateStr)
  const p = n => String(n).padStart(2, '0')
  return `${p(d.getDate())}/${p(d.getMonth() + 1)}/${d.getFullYear()} ${p(d.getHours())}:${p(d.getMinutes())}`
}

const statusIcons = {
  OPEN: { icon: 'event_available', bg: 'bg-green-100 dark:bg-green-900/40 text-green-500' },
  DRAFT: { icon: 'edit_note', bg: 'bg-gray-100 dark:bg-gray-800 text-gray-500' },
  FINISHED: { icon: 'check_circle', bg: 'bg-blue-100 dark:bg-blue-900/40 text-blue-500' },
  CANCELLED: { icon: 'cancel', bg: 'bg-red-100 dark:bg-red-900/40 text-red-500' },
}

const statusLabels = {
  OPEN: 'Đang mở',
  DRAFT: 'Bản nháp',
  FINISHED: 'Đã kết thúc',
  CANCELLED: 'Đã hủy',
}

/* ── Custom tooltip for chart ── */
function CustomTooltip({ active, payload, label }) {
  if (!active || !payload?.length) return null
  return (
    <div className="bg-white dark:bg-gray-800 px-3 py-2 rounded-lg shadow-lg border border-gray-200 dark:border-gray-700 text-sm">
      <p className="font-medium text-gray-700 dark:text-gray-200">
        {label}: <span className="text-primary">{payload[0].value}</span>
      </p>
    </div>
  )
}

/* ── Custom bar shape with rounded top ── */
function RoundedBar(props) {
  const { x, y, width, height } = props
  const radius = 6
  if (height <= 0) return null
  return (
    <path
      d={`M${x},${y + height} 
          L${x},${y + radius} 
          Q${x},${y} ${x + radius},${y} 
          L${x + width - radius},${y} 
          Q${x + width},${y} ${x + width},${y + radius} 
          L${x + width},${y + height} Z`}
      fill={props.fill}
    />
  )
}

/* ═══════════════════════════════════════════
   Admin Dashboard Page
   ═══════════════════════════════════════════ */
export default function AdminDashboard() {
  const { currentUser } = useOutletContext()
  const navigate = useNavigate()
  const [timeRange, setTimeRange] = useState('7days')
  const [stats, setStats] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  // State cho bộ lọc
  const [showFilter, setShowFilter] = useState(false)
  const [filterStatus, setFilterStatus] = useState('ALL')

  // State cho "Xem tất cả" hoạt động
  const [showAllActivities, setShowAllActivities] = useState(false)
  const [allActivities, setAllActivities] = useState([])
  const [loadingAll, setLoadingAll] = useState(false)

  useEffect(() => {
    fetch('/admin/api/dashboard-stats', {
      credentials: 'include',
      headers: { 'Accept': 'application/json' },
    })
      .then((res) => {
        if (!res.ok) throw new Error('Failed to fetch stats')
        return res.json()
      })
      .then((data) => {
        setStats(data)
        setLoading(false)
      })
      .catch((err) => {
        setError(err.message)
        setLoading(false)
      })
  }, [])

  // Fetch tất cả hoạt động khi bấm "Xem tất cả"
  const handleViewAllActivities = () => {
    if (showAllActivities) {
      setShowAllActivities(false)
      return
    }
    setLoadingAll(true)
    fetch('/admin/activities/api/list', {
      credentials: 'include',
      headers: { 'Accept': 'application/json' },
    })
      .then((res) => {
        if (!res.ok) throw new Error('Failed')
        return res.json()
      })
      .then((data) => {
        setAllActivities(data)
        setShowAllActivities(true)
        setLoadingAll(false)
      })
      .catch(() => {
        // Fallback: dùng recentActivities nếu API list không có
        setAllActivities(stats?.recentActivities || [])
        setShowAllActivities(true)
        setLoadingAll(false)
      })
  }

  // Build stat cards from API data
  const statsCards = stats
    ? [
      {
        label: 'Tổng Hoạt động',
        value: stats.totalActivities,
        icon: 'event',
        iconBg: 'bg-indigo-100 dark:bg-indigo-900/40 text-indigo-600 dark:text-indigo-400',
        trend: `${stats.activeActivities} đang mở`,
        trendIcon: 'trending_up',
        trendColor: 'text-green-500',
      },
      {
        label: 'Tổng Sinh viên',
        value: stats.totalStudents,
        icon: 'groups',
        iconBg: 'bg-blue-100 dark:bg-blue-900/40 text-blue-600 dark:text-blue-400',
        trend: `${stats.totalUsers} người dùng`,
        trendIcon: 'person',
        trendColor: 'text-blue-500',
      },
      {
        label: 'Khoa / Lớp',
        value: `${stats.totalFaculties} / ${stats.totalClasses}`,
        icon: 'account_balance',
        iconBg: 'bg-orange-100 dark:bg-orange-900/40 text-orange-600 dark:text-orange-400',
        trend: `${stats.totalAcademicYears} khóa học`,
        trendIcon: 'school',
        trendColor: 'text-orange-500',
      },
      {
        label: 'Hoạt động đang mở',
        value: stats.activeActivities,
        icon: 'pending_actions',
        iconBg: 'bg-green-100 dark:bg-green-900/40 text-green-600 dark:text-green-400',
        badge: stats.activeActivities > 0 ? 'Đang diễn ra' : null,
        badgeColor: 'bg-green-100 dark:bg-green-900/40 text-green-600 dark:text-green-400',
      },
    ]
    : []

  // Recent activities from API
  const recentActivities = stats?.recentActivities || []

  // Lọc hoạt động theo trạng thái
  const filteredRecentActivities = filterStatus === 'ALL'
    ? recentActivities
    : recentActivities.filter((a) => a.status === filterStatus)

  // Chart data from recent activities
  const chartData = recentActivities.length > 0
    ? (() => {
      const days = ['CN', 'T2', 'T3', 'T4', 'T5', 'T6', 'T7']
      const counts = { T2: 0, T3: 0, T4: 0, T5: 0, T6: 0, T7: 0, CN: 0 }
      recentActivities.forEach((a) => {
        const d = new Date(a.createdAt)
        const dayName = days[d.getDay()]
        counts[dayName] = (counts[dayName] || 0) + 1
      })
      return ['T2', 'T3', 'T4', 'T5', 'T6', 'T7', 'CN'].map((day) => ({
        day,
        value: counts[day],
      }))
    })()
    : defaultChartData

  // Sự kiện sắp tới từ backend
  const upcomingEvent = stats?.upcomingEvent || null

  if (loading) {
    return (
      <div className="flex items-center justify-center h-[60vh]">
        <div className="flex flex-col items-center gap-3">
          <div className="w-10 h-10 border-4 border-primary/30 border-t-primary rounded-full animate-spin" />
          <p className="text-sm text-gray-500 dark:text-gray-400">Đang tải dữ liệu...</p>
        </div>
      </div>
    )
  }

  if (error) {
    return (
      <div className="flex items-center justify-center h-[60vh]">
        <div className="text-center">
          <span className="material-symbols-outlined text-5xl text-red-400 mb-3 block">error</span>
          <p className="text-gray-600 dark:text-gray-400">Không thể tải dữ liệu: {error}</p>
          <button
            onClick={() => window.location.reload()}
            className="mt-3 px-4 py-2 bg-primary text-white rounded-lg text-sm hover:bg-primary-dark transition-colors"
          >
            Thử lại
          </button>
        </div>
      </div>
    )
  }

  return (
    <div className="space-y-6">
      {/* ── Page header ── */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-gray-800 dark:text-white">
            Tổng quan Dashboard
          </h1>
          <p className="text-sm text-gray-500 dark:text-gray-400 mt-1">
            Chào mừng trở lại, {currentUser?.fullName || 'Quản trị viên'}. Đây là những gì đang diễn ra hôm nay.
          </p>
        </div>
        <div className="flex items-center gap-3 relative">
          {/* Bộ lọc trạng thái */}
          <div className="relative">
            <button
              onClick={() => setShowFilter(!showFilter)}
              className="inline-flex items-center gap-2 px-4 py-2.5 rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 text-sm font-medium text-gray-700 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-700 transition-colors"
            >
              <span className="material-symbols-outlined text-lg">filter_list</span>
              Bộ lọc
              {filterStatus !== 'ALL' && (
                <span className="w-2 h-2 rounded-full bg-primary" />
              )}
            </button>
            {showFilter && (
              <div className="absolute right-0 mt-2 w-48 bg-white dark:bg-gray-800 rounded-xl shadow-xl border border-gray-200 dark:border-gray-700 overflow-hidden z-20">
                <div className="py-1">
                  {['ALL', 'OPEN', 'DRAFT', 'FINISHED', 'CANCELLED'].map((status) => (
                    <button
                      key={status}
                      onClick={() => { setFilterStatus(status); setShowFilter(false) }}
                      className={`flex items-center gap-2 w-full px-4 py-2.5 text-sm transition-colors ${filterStatus === status
                          ? 'bg-primary/10 text-primary font-semibold'
                          : 'text-gray-700 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-700/50'
                        }`}
                    >
                      <span className="material-symbols-outlined text-lg">
                        {status === 'ALL' ? 'select_all' : statusIcons[status]?.icon || 'filter_list'}
                      </span>
                      {status === 'ALL' ? 'Tất cả' : statusLabels[status] || status}
                    </button>
                  ))}
                </div>
              </div>
            )}
          </div>
          <button
            onClick={() => navigate('/admin/activities')}
            className="inline-flex items-center gap-2 px-4 py-2.5 rounded-xl bg-primary hover:bg-primary-dark text-white text-sm font-medium shadow-md shadow-primary/25 transition-colors"
          >
            <span className="material-symbols-outlined text-lg">add</span>
            Hoạt động mới
          </button>
        </div>
      </div>

      {/* ── Stat cards ── */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-5">
        {statsCards.map((card, i) => (
          <div
            key={i}
            className="bg-white dark:bg-gray-900 rounded-2xl p-5 border border-gray-100 dark:border-gray-800 hover:shadow-lg hover:shadow-gray-200/50 dark:hover:shadow-gray-900/50 transition-all duration-300"
          >
            <div className="flex items-start justify-between">
              <div
                className={`flex items-center justify-center w-11 h-11 rounded-xl ${card.iconBg}`}
              >
                <span className="material-symbols-outlined text-2xl">{card.icon}</span>
              </div>
              {card.trend && (
                <span className={`inline-flex items-center gap-1 text-xs font-medium ${card.trendColor}`}>
                  <span className="material-symbols-outlined text-sm">{card.trendIcon}</span>
                  {card.trend}
                </span>
              )}
              {card.badge && (
                <span
                  className={`inline-flex items-center px-2 py-0.5 rounded-md text-xs font-semibold ${card.badgeColor}`}
                >
                  {card.badge}
                </span>
              )}
            </div>
            <p className="mt-3 text-sm text-gray-500 dark:text-gray-400">{card.label}</p>
            <p className="mt-1 text-3xl font-bold text-gray-800 dark:text-white">{card.value}</p>
          </div>
        ))}
      </div>

      {/* ── Chart + Activities row ── */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-5">
        {/* Activity Timeline chart — 2/3 width */}
        <div className="lg:col-span-2 bg-white dark:bg-gray-900 rounded-2xl p-6 border border-gray-100 dark:border-gray-800">
          <div className="flex items-center justify-between mb-6">
            <div>
              <h2 className="text-lg font-semibold text-gray-800 dark:text-white">
                Hoạt động theo thời gian
              </h2>
              <p className="text-xs text-gray-400 dark:text-gray-500 mt-0.5">
                Mức độ tham gia trong 7 ngày qua
              </p>
            </div>
            <select
              value={timeRange}
              onChange={(e) => setTimeRange(e.target.value)}
              className="text-sm border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 text-gray-600 dark:text-gray-300 rounded-lg px-3 py-1.5 focus:outline-none focus:ring-2 focus:ring-primary/30"
            >
              <option value="7days">7 ngày qua</option>
              <option value="30days">30 ngày qua</option>
              <option value="3months">3 tháng</option>
            </select>
          </div>
          <div className="h-64">
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={chartData} barSize={36}>
                <CartesianGrid
                  strokeDasharray="3 3"
                  vertical={false}
                  stroke="currentColor"
                  className="text-gray-100 dark:text-gray-800"
                />
                <XAxis
                  dataKey="day"
                  axisLine={false}
                  tickLine={false}
                  tick={{ fill: '#9ca3af', fontSize: 13 }}
                />
                <YAxis
                  axisLine={false}
                  tickLine={false}
                  tick={{ fill: '#9ca3af', fontSize: 12 }}
                />
                <Tooltip content={<CustomTooltip />} cursor={false} />
                <Bar
                  dataKey="value"
                  fill="#818cf8"
                  shape={<RoundedBar />}
                  radius={[6, 6, 0, 0]}
                />
              </BarChart>
            </ResponsiveContainer>
          </div>
        </div>

        {/* Recent Activities — 1/3 width */}
        <div className="bg-white dark:bg-gray-900 rounded-2xl p-6 border border-gray-100 dark:border-gray-800">
          <div className="flex items-center justify-between mb-5">
            <h2 className="text-lg font-semibold text-gray-800 dark:text-white">
              Hoạt động gần đây
            </h2>
            <button
              onClick={handleViewAllActivities}
              className="text-xs font-medium text-primary hover:underline"
            >
              {showAllActivities ? 'Thu gọn' : 'Xem tất cả'}
            </button>
          </div>

          {/* Danh sách hoạt động */}
          <div className="space-y-4 max-h-80 overflow-y-auto">
            {showAllActivities ? (
              // Hiển thị tất cả hoạt động
              loadingAll ? (
                <div className="flex items-center justify-center py-6">
                  <div className="w-6 h-6 border-2 border-primary/30 border-t-primary rounded-full animate-spin" />
                </div>
              ) : allActivities.length > 0 ? (
                allActivities.map((a) => {
                  const si = statusIcons[a.status] || statusIcons.DRAFT
                  return (
                    <div key={a.id} className="flex items-start gap-3 group cursor-pointer">
                      <div className={`flex items-center justify-center w-9 h-9 rounded-lg shrink-0 ${si.bg}`}>
                        <span className="material-symbols-outlined text-lg">{si.icon}</span>
                      </div>
                      <div className="min-w-0 flex-1">
                        <p className="text-sm text-gray-700 dark:text-gray-200 leading-snug group-hover:text-primary transition-colors">
                          {a.name}
                        </p>
                        <div className="flex items-center gap-2 mt-1">
                          <span className={`text-[10px] px-1.5 py-0.5 rounded font-medium ${si.bg}`}>
                            {statusLabels[a.status] || a.status}
                          </span>
                          <p className="text-xs text-gray-400">{timeAgo(a.createdAt)}</p>
                        </div>
                      </div>
                    </div>
                  )
                })
              ) : (
                <p className="text-sm text-gray-400 dark:text-gray-500 text-center py-4">
                  Chưa có hoạt động nào
                </p>
              )
            ) : (
              // Hiển thị recent activities (mặc định 5 mục)
              filteredRecentActivities.length > 0 ? (
                filteredRecentActivities.map((a) => {
                  const si = statusIcons[a.status] || statusIcons.DRAFT
                  return (
                    <div key={a.id} className="flex items-start gap-3 group cursor-pointer">
                      <div className={`flex items-center justify-center w-9 h-9 rounded-lg shrink-0 ${si.bg}`}>
                        <span className="material-symbols-outlined text-lg">{si.icon}</span>
                      </div>
                      <div className="min-w-0">
                        <p className="text-sm text-gray-700 dark:text-gray-200 leading-snug group-hover:text-primary transition-colors">
                          {a.name}
                        </p>
                        <p className="text-xs text-green-500 mt-1 flex items-center gap-1">
                          <span className="w-1.5 h-1.5 rounded-full bg-green-500 inline-block" />
                          {timeAgo(a.createdAt)}
                        </p>
                      </div>
                    </div>
                  )
                })
              ) : (
                <p className="text-sm text-gray-400 dark:text-gray-500 text-center py-4">
                  {filterStatus !== 'ALL' ? 'Không có hoạt động nào với trạng thái này' : 'Chưa có hoạt động nào'}
                </p>
              )
            )}
          </div>
          <button
            onClick={handleViewAllActivities}
            className="mt-5 w-full text-center text-sm text-gray-500 dark:text-gray-400 hover:text-primary transition-colors"
          >
            {showAllActivities
              ? '← Thu gọn danh sách'
              : `Xem tất cả ${stats?.totalActivities || 0} hoạt động →`}
          </button>
        </div>
      </div>

      {/* ── Banner / Sự kiện sắp tới — LẤY TỪ BACKEND ── */}
      {upcomingEvent ? (
        <div className="relative rounded-2xl overflow-hidden bg-gradient-to-r from-primary to-indigo-700 p-6 sm:p-8">
          <div className="absolute -top-10 -right-10 w-40 h-40 bg-white/10 rounded-full" />
          <div className="absolute -bottom-6 -left-6 w-24 h-24 bg-white/10 rounded-full" />
          <div className="relative flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
            <div className="text-white">
              <h3 className="text-xl font-bold">
                Sự kiện sắp tới: {upcomingEvent.name}
              </h3>
              <p className="mt-1 text-sm text-indigo-200 max-w-lg">
                {upcomingEvent.description || 'Không có mô tả'}
              </p>
              <div className="mt-2 flex items-center gap-4 text-xs text-indigo-200">
                {upcomingEvent.startTime && (
                  <span className="flex items-center gap-1">
                    <span className="material-symbols-outlined text-sm">schedule</span>
                    {formatDateTime(upcomingEvent.startTime)}
                  </span>
                )}
                {upcomingEvent.location && (
                  <span className="flex items-center gap-1">
                    <span className="material-symbols-outlined text-sm">location_on</span>
                    {upcomingEvent.location}
                  </span>
                )}
              </div>
            </div>
            <button
              onClick={() => navigate('/admin/activities')}
              className="inline-flex items-center gap-2 px-5 py-2.5 rounded-xl bg-white text-primary font-semibold text-sm hover:bg-gray-100 transition-colors shrink-0 shadow-lg"
            >
              Xem chi tiết
            </button>
          </div>
        </div>
      ) : (
        <div className="relative rounded-2xl overflow-hidden bg-gradient-to-r from-gray-600 to-gray-700 p-6 sm:p-8">
          <div className="absolute -top-10 -right-10 w-40 h-40 bg-white/10 rounded-full" />
          <div className="absolute -bottom-6 -left-6 w-24 h-24 bg-white/10 rounded-full" />
          <div className="relative flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
            <div className="text-white">
              <h3 className="text-xl font-bold">Không có sự kiện sắp tới</h3>
              <p className="mt-1 text-sm text-gray-300 max-w-lg">
                Hiện tại chưa có hoạt động nào đang mở với thời gian diễn ra trong tương lai. Hãy tạo hoạt động mới!
              </p>
            </div>
            <button
              onClick={() => navigate('/admin/activities')}
              className="inline-flex items-center gap-2 px-5 py-2.5 rounded-xl bg-white text-gray-700 font-semibold text-sm hover:bg-gray-100 transition-colors shrink-0 shadow-lg"
            >
              Tạo hoạt động
            </button>
          </div>
        </div>
      )}
    </div>
  )
}
