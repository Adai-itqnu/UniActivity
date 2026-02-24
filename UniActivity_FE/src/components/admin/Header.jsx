import { useState, useRef, useEffect, useCallback } from 'react'
import { useDarkMode } from '../../contexts/DarkModeContext'
import { useNavigate } from 'react-router-dom'

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

// Map NotificationType sang icon + color
const notifTypeIcons = {
  JOIN_REQUEST_APPROVED: { icon: 'how_to_reg', iconColor: 'text-green-500 bg-green-100 dark:bg-green-900/40' },
  JOIN_REQUEST_REJECTED: { icon: 'person_off', iconColor: 'text-red-500 bg-red-100 dark:bg-red-900/40' },
  REMOVED_FROM_CLASS: { icon: 'person_remove', iconColor: 'text-red-500 bg-red-100 dark:bg-red-900/40' },
  NEW_JOIN_REQUEST: { icon: 'person_add', iconColor: 'text-blue-500 bg-blue-100 dark:bg-blue-900/40' },
  POINT_REQUEST_APPROVED: { icon: 'check_circle', iconColor: 'text-green-500 bg-green-100 dark:bg-green-900/40' },
  POINT_REQUEST_REJECTED: { icon: 'cancel', iconColor: 'text-red-500 bg-red-100 dark:bg-red-900/40' },
  NEW_POINT_REQUEST: { icon: 'grade', iconColor: 'text-orange-500 bg-orange-100 dark:bg-orange-900/40' },
  STUDENT_CHECKED_IN: { icon: 'fact_check', iconColor: 'text-blue-500 bg-blue-100 dark:bg-blue-900/40' },
  NEW_ACTIVITY: { icon: 'event', iconColor: 'text-indigo-500 bg-indigo-100 dark:bg-indigo-900/40' },
}
const defaultNotifIcon = { icon: 'notifications', iconColor: 'text-gray-500 bg-gray-100 dark:bg-gray-900/40' }

export default function Header() {
  const { isDark, toggleDarkMode } = useDarkMode()
  const navigate = useNavigate()
  const [showNotifications, setShowNotifications] = useState(false)
  const [searchQuery, setSearchQuery] = useState('')
  const [searchResults, setSearchResults] = useState([])
  const [showSearchResults, setShowSearchResults] = useState(false)
  const [searchLoading, setSearchLoading] = useState(false)
  const notifRef = useRef(null)
  const searchRef = useRef(null)

  // Notifications state - lấy từ backend
  const [notifications, setNotifications] = useState([])
  const [unreadCount, setUnreadCount] = useState(0)
  const [notifLoading, setNotifLoading] = useState(false)

  // Close dropdown on outside click
  useEffect(() => {
    const handleClick = (e) => {
      if (notifRef.current && !notifRef.current.contains(e.target)) {
        setShowNotifications(false)
      }
      if (searchRef.current && !searchRef.current.contains(e.target)) {
        setShowSearchResults(false)
      }
    }
    document.addEventListener('mousedown', handleClick)
    return () => document.removeEventListener('mousedown', handleClick)
  }, [])

  // Fetch notifications từ backend
  const fetchNotifications = useCallback(() => {
    setNotifLoading(true)
    fetch('/admin/api/notifications', { credentials: 'include' })
      .then((res) => {
        if (!res.ok) throw new Error('Failed')
        return res.json()
      })
      .then((data) => {
        setNotifications(data.notifications || [])
        setUnreadCount(data.unreadCount || 0)
        setNotifLoading(false)
      })
      .catch(() => {
        setNotifications([])
        setUnreadCount(0)
        setNotifLoading(false)
      })
  }, [])

  // Load notifications on mount và mỗi 30 giây
  useEffect(() => {
    fetchNotifications()
    const interval = setInterval(fetchNotifications, 30000)
    return () => clearInterval(interval)
  }, [fetchNotifications])

  // Đánh dấu đã đọc tất cả
  const handleMarkAllRead = () => {
    fetch('/admin/api/notifications/mark-all-read', {
      method: 'POST',
      credentials: 'include',
    })
      .then(() => {
        setNotifications((prev) => prev.map((n) => ({ ...n, isRead: true })))
        setUnreadCount(0)
      })
      .catch(() => { })
  }

  // Search handler với debounce
  const searchTimeoutRef = useRef(null)
  const handleSearchChange = (e) => {
    const q = e.target.value
    setSearchQuery(q)

    if (searchTimeoutRef.current) clearTimeout(searchTimeoutRef.current)

    if (q.trim().length < 2) {
      setSearchResults([])
      setShowSearchResults(false)
      return
    }

    searchTimeoutRef.current = setTimeout(() => {
      setSearchLoading(true)
      setShowSearchResults(true)
      fetch(`/admin/api/search?q=${encodeURIComponent(q.trim())}`, { credentials: 'include' })
        .then((res) => {
          if (!res.ok) throw new Error('Failed')
          return res.json()
        })
        .then((data) => {
          setSearchResults(data.activities || [])
          setSearchLoading(false)
        })
        .catch(() => {
          setSearchResults([])
          setSearchLoading(false)
        })
    }, 300)
  }

  const handleSearchKeyDown = (e) => {
    if (e.key === 'Enter' && searchQuery.trim()) {
      navigate(`/admin/activities?search=${encodeURIComponent(searchQuery.trim())}`)
      setShowSearchResults(false)
    }
  }

  return (
    <header className="sticky top-0 z-30 flex items-center h-16 px-6 bg-white dark:bg-gray-900 border-b border-gray-200 dark:border-gray-700 shrink-0">
      {/* Left — Logo (visible on mobile or when sidebar collapsed) */}
      <div className="flex items-center gap-2 mr-4 lg:hidden">
        <div className="flex items-center justify-center w-9 h-9 rounded-lg bg-primary text-white">
          <span className="material-symbols-outlined text-lg">school</span>
        </div>
        <span className="font-bold text-gray-800 dark:text-white text-sm">UniActivity</span>
      </div>

      {/* Center — Search */}
      <div className="flex-1 max-w-xl mx-auto relative" ref={searchRef}>
        <div className="relative">
          <span className="material-symbols-outlined absolute left-3 top-1/2 -translate-y-1/2 text-gray-400 text-xl">
            search
          </span>
          <input
            type="text"
            value={searchQuery}
            onChange={handleSearchChange}
            onKeyDown={handleSearchKeyDown}
            onFocus={() => { if (searchResults.length > 0) setShowSearchResults(true) }}
            placeholder="Tìm kiếm hoạt động, lớp, sinh viên..."
            className="w-full h-10 pl-10 pr-4 rounded-xl border border-gray-200 dark:border-gray-600 bg-gray-50 dark:bg-gray-800 text-sm text-gray-700 dark:text-gray-200 placeholder-gray-400 dark:placeholder-gray-500 focus:outline-none focus:ring-2 focus:ring-primary/40 focus:border-primary transition-colors"
          />
        </div>

        {/* Search results dropdown */}
        {showSearchResults && (
          <div className="absolute top-full left-0 right-0 mt-1 bg-white dark:bg-gray-800 rounded-xl shadow-xl border border-gray-200 dark:border-gray-700 overflow-hidden z-50">
            {searchLoading ? (
              <div className="flex items-center justify-center py-4">
                <div className="w-5 h-5 border-2 border-primary/30 border-t-primary rounded-full animate-spin" />
                <span className="ml-2 text-sm text-gray-400">Đang tìm kiếm...</span>
              </div>
            ) : searchResults.length > 0 ? (
              <div className="max-h-72 overflow-y-auto">
                {searchResults.map((item) => (
                  <button
                    key={item.id}
                    onClick={() => {
                      setShowSearchResults(false)
                      setSearchQuery('')
                      navigate('/admin/activities')
                    }}
                    className="flex items-center gap-3 w-full px-4 py-3 text-left hover:bg-gray-50 dark:hover:bg-gray-700/50 transition-colors"
                  >
                    <span className="material-symbols-outlined text-lg text-primary">event</span>
                    <div className="min-w-0 flex-1">
                      <p className="text-sm text-gray-700 dark:text-gray-200 truncate">{item.name}</p>
                      <p className="text-xs text-gray-400 flex items-center gap-2">
                        <span className={`inline-block w-1.5 h-1.5 rounded-full ${item.status === 'OPEN' ? 'bg-green-500' : item.status === 'DRAFT' ? 'bg-gray-400' : 'bg-blue-500'}`} />
                        {item.status === 'OPEN' ? 'Đang mở' : item.status === 'DRAFT' ? 'Bản nháp' : item.status === 'FINISHED' ? 'Đã kết thúc' : item.status}
                        {item.location && ` · ${item.location}`}
                      </p>
                    </div>
                  </button>
                ))}
              </div>
            ) : (
              <div className="px-4 py-4 text-sm text-gray-400 text-center">
                Không tìm thấy kết quả cho "{searchQuery}"
              </div>
            )}
          </div>
        )}
      </div>

      {/* Right — Notifications + Dark mode */}
      <div className="flex items-center gap-2 ml-4">
        {/* Help */}
        <button
          className="flex items-center justify-center w-10 h-10 rounded-xl text-gray-500 dark:text-gray-400 hover:bg-gray-100 dark:hover:bg-gray-800 transition-colors"
          title="Trợ giúp"
        >
          <span className="material-symbols-outlined text-xl">help_outline</span>
        </button>

        {/* Notification Bell */}
        <div className="relative" ref={notifRef}>
          <button
            onClick={() => {
              setShowNotifications(!showNotifications)
              if (!showNotifications) fetchNotifications()
            }}
            className="relative flex items-center justify-center w-10 h-10 rounded-xl text-gray-500 dark:text-gray-400 hover:bg-gray-100 dark:hover:bg-gray-800 transition-colors"
            title="Thông báo"
          >
            <span className="material-symbols-outlined text-xl">notifications</span>
            {/* Badge - chỉ hiện khi có thông báo chưa đọc */}
            {unreadCount > 0 && (
              <span className="absolute -top-0.5 -right-0.5 min-w-[18px] h-[18px] flex items-center justify-center bg-red-500 rounded-full border-2 border-white dark:border-gray-900 text-[10px] font-bold text-white px-1">
                {unreadCount > 99 ? '99+' : unreadCount}
              </span>
            )}
          </button>

          {/* Dropdown */}
          {showNotifications && (
            <div className="absolute right-0 mt-2 w-80 bg-white dark:bg-gray-800 rounded-2xl shadow-xl border border-gray-200 dark:border-gray-700 overflow-hidden animate-in fade-in slide-in-from-top-2 duration-200">
              <div className="flex items-center justify-between px-4 py-3 border-b border-gray-100 dark:border-gray-700">
                <h3 className="font-semibold text-gray-800 dark:text-white text-sm">
                  Thông báo {unreadCount > 0 && `(${unreadCount})`}
                </h3>
                {unreadCount > 0 && (
                  <button
                    onClick={handleMarkAllRead}
                    className="text-xs text-primary font-medium cursor-pointer hover:underline"
                  >
                    Đánh dấu đã đọc
                  </button>
                )}
              </div>
              <div className="max-h-80 overflow-y-auto">
                {notifLoading ? (
                  <div className="flex items-center justify-center py-6">
                    <div className="w-5 h-5 border-2 border-primary/30 border-t-primary rounded-full animate-spin" />
                  </div>
                ) : notifications.length > 0 ? (
                  notifications.map((notif) => {
                    const typeInfo = notifTypeIcons[notif.type] || defaultNotifIcon
                    return (
                      <div
                        key={notif.id}
                        className={`flex items-start gap-3 px-4 py-3 hover:bg-gray-50 dark:hover:bg-gray-700/50 cursor-pointer transition-colors ${!notif.isRead ? 'bg-primary/5' : ''
                          }`}
                      >
                        <div className={`flex items-center justify-center w-9 h-9 rounded-lg shrink-0 ${typeInfo.iconColor}`}>
                          <span className="material-symbols-outlined text-lg">{typeInfo.icon}</span>
                        </div>
                        <div className="min-w-0">
                          <p className="text-sm text-gray-700 dark:text-gray-200 leading-snug">
                            {notif.title}
                          </p>
                          {notif.message && (
                            <p className="text-xs text-gray-400 mt-0.5 line-clamp-2">{notif.message}</p>
                          )}
                          <p className="text-xs text-green-500 mt-0.5 flex items-center gap-1">
                            <span className="w-1.5 h-1.5 rounded-full bg-green-500 inline-block" />
                            {timeAgo(notif.createdAt)}
                          </p>
                        </div>
                      </div>
                    )
                  })
                ) : (
                  <div className="px-4 py-6 text-center">
                    <span className="material-symbols-outlined text-3xl text-gray-300 dark:text-gray-600 block mb-1">notifications_off</span>
                    <p className="text-sm text-gray-400">Không có thông báo mới</p>
                  </div>
                )}
              </div>
              <div className="border-t border-gray-100 dark:border-gray-700 px-4 py-2.5">
                <button
                  onClick={() => {
                    setShowNotifications(false)
                    navigate('/admin/notices')
                  }}
                  className="w-full text-center text-sm text-primary font-medium hover:underline"
                >
                  Xem tất cả thông báo →
                </button>
              </div>
            </div>
          )}
        </div>

        {/* Dark Mode Toggle */}
        <button
          onClick={toggleDarkMode}
          className="flex items-center justify-center w-10 h-10 rounded-xl text-gray-500 dark:text-gray-400 hover:bg-gray-100 dark:hover:bg-gray-800 transition-colors"
          title={isDark ? 'Chế độ sáng' : 'Chế độ tối'}
        >
          <span className="material-symbols-outlined text-xl">
            {isDark ? 'light_mode' : 'dark_mode'}
          </span>
        </button>
      </div>
    </header>
  )
}
