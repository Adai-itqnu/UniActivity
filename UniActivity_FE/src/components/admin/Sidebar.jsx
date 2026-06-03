import { useState, useRef, useEffect } from 'react'
import { NavLink, useNavigate, useLocation } from 'react-router-dom'

const menuGroups = [
  {
    label: null,
    items: [
      { name: 'Tổng quan', icon: 'dashboard', path: '/admin/dashboard' },
    ],
  },
  {
    label: 'Cơ cấu tổ chức',
    items: [
      { name: 'Quản lý Khoa', icon: 'account_balance', path: '/admin/faculties' },
      { name: 'Quản lý Khóa', icon: 'calendar_today', path: '/admin/academic-years' },
      { name: 'Quản lý Lớp', icon: 'school', path: '/admin/classes' },
      { name: 'Quản lý Học kỳ', icon: 'date_range', path: '/admin/semesters' },
    ],
  },
  {
    label: 'Quản lý nghiệp vụ',
    items: [
      { name: 'Hoạt động', icon: 'event', path: '/admin/activities' },
      { name: 'Người dùng', icon: 'group', path: '/admin/users' },
    ],
  },
  {
    label: 'Thông báo',
    items: [
      { name: 'Thông báo', icon: 'notifications', path: '/admin/notices' },
    ],
  },
]

export default function Sidebar({ collapsed, setCollapsed, currentUser, mobileOpen, setMobileOpen }) {
  const navigate = useNavigate()
  const location = useLocation()
  const [showUserMenu, setShowUserMenu] = useState(false)
  const userMenuRef = useRef(null)

  // Close mobile sidebar on route change
  useEffect(() => {
    if (mobileOpen) setMobileOpen(false)
  }, [location.pathname])

  // Close user menu on outside click
  useEffect(() => {
    const handleClick = (e) => {
      if (userMenuRef.current && !userMenuRef.current.contains(e.target)) {
        setShowUserMenu(false)
      }
    }
    document.addEventListener('mousedown', handleClick)
    return () => document.removeEventListener('mousedown', handleClick)
  }, [])

  const handleLogout = async () => {
    try {
      await fetch('/logout', { method: 'POST', credentials: 'include' })
    } catch (_) { /* ignore */ }
    // Clear JWT tokens
    sessionStorage.removeItem('accessToken')
    sessionStorage.removeItem('refreshToken')
    sessionStorage.removeItem('user')
    window.location.href = '/login'
  }

  const userName = currentUser?.fullName || 'Admin'
  const userRole = currentUser?.role === 'ADMIN' ? 'Administrator' : (currentUser?.role || 'User')
  const avatarUrl = currentUser?.avatarUrl
  // Lấy chữ cái đầu cho avatar fallback
  const initials = userName
    .split(' ')
    .map((w) => w[0])
    .join('')
    .toUpperCase()
    .slice(0, 2)

  const sidebarContent = (isMobile = false) => (
    <>
      {/* Logo + Toggle */}
      <div className="flex items-center gap-3 px-4 h-16 border-b border-gray-200 dark:border-gray-700 shrink-0">
        {isMobile ? (
          <button
            onClick={() => setMobileOpen(false)}
            className="flex items-center justify-center w-10 h-10 rounded-xl bg-primary text-white hover:bg-primary-dark transition-colors shrink-0"
          >
            <span className="material-symbols-outlined text-xl">close</span>
          </button>
        ) : (
          <button
            onClick={() => setCollapsed(!collapsed)}
            className="flex items-center justify-center w-10 h-10 rounded-xl bg-primary text-white hover:bg-primary-dark transition-colors shrink-0"
          >
            <span className="material-symbols-outlined text-xl">
              {collapsed ? 'menu' : 'menu_open'}
            </span>
          </button>
        )}
        {(!collapsed || isMobile) && (
          <div className="overflow-hidden">
            <h1 className="text-lg font-bold text-gray-800 dark:text-white leading-tight whitespace-nowrap">
              UniActivity
            </h1>
            <p className="text-[10px] text-gray-400 dark:text-gray-500 leading-tight whitespace-nowrap">
              Admin Console
            </p>
          </div>
        )}
      </div>

      {/* Navigation */}
      <nav className="flex-1 overflow-y-auto py-4 px-3 space-y-1 scrollbar-thin">
        {menuGroups.map((group, gi) => (
          <div key={gi} className={gi > 0 ? 'mt-4' : ''}>
            {group.label && (!collapsed || isMobile) && (
              <p className="px-3 mb-2 text-[10px] font-semibold uppercase tracking-wider text-gray-400 dark:text-gray-500">
                {group.label}
              </p>
            )}
            {group.label && collapsed && !isMobile && gi > 0 && (
              <div className="mx-3 mb-2 border-t border-gray-200 dark:border-gray-700" />
            )}
            {group.items.map((item) => (
              <NavLink
                key={item.path}
                to={item.path}
                className={({ isActive }) =>
                  `flex items-center gap-3 px-3 py-2.5 rounded-xl text-sm font-medium transition-all duration-200 group ${
                    isActive
                      ? 'bg-primary text-white shadow-md shadow-primary/25'
                      : 'text-gray-600 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-gray-800'
                  } ${collapsed && !isMobile ? 'justify-center' : ''}`
                }
                title={collapsed && !isMobile ? item.name : undefined}
              >
                <span className="material-symbols-outlined text-xl shrink-0">
                  {item.icon}
                </span>
                {(!collapsed || isMobile) && <span className="whitespace-nowrap">{item.name}</span>}
              </NavLink>
            ))}
          </div>
        ))}
      </nav>

      {/* User Profile Section */}
      <div className="border-t border-gray-200 dark:border-gray-700 p-3 shrink-0 relative" ref={userMenuRef}>
        {/* User Dropdown (appears above) */}
        {showUserMenu && (
          <div
            className={`absolute bottom-full mb-2 bg-white dark:bg-gray-800 rounded-xl shadow-xl border border-gray-200 dark:border-gray-700 overflow-hidden animate-in ${
              collapsed && !isMobile ? 'left-1/2 -translate-x-1/2 w-48' : 'left-3 right-3'
            }`}
          >
            {/* User info header */}
            <div className="px-4 py-3 border-b border-gray-100 dark:border-gray-700">
              <p className="text-sm font-semibold text-gray-800 dark:text-white truncate">
                {userName}
              </p>
              <p className="text-xs text-gray-400 dark:text-gray-500 truncate">
                {currentUser?.email || ''}
              </p>
            </div>

            {/* Menu items */}
            <div className="py-1">
              <button
                onClick={() => { setShowUserMenu(false); navigate('/admin/profile') }}
                className="flex items-center gap-2.5 w-full px-4 py-2.5 text-sm text-gray-700 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-700/50 transition-colors"
              >
                <span className="material-symbols-outlined text-lg">person</span>
                Chỉnh sửa hồ sơ
              </button>
            </div>

            {/* Logout */}
            <div className="border-t border-gray-100 dark:border-gray-700 py-1">
              <button
                onClick={handleLogout}
                className="flex items-center gap-2.5 w-full px-4 py-2.5 text-sm text-red-500 hover:bg-red-50 dark:hover:bg-red-900/20 transition-colors"
              >
                <span className="material-symbols-outlined text-lg">logout</span>
                Đăng xuất
              </button>
            </div>
          </div>
        )}

        {/* User button trigger */}
        <button
          onClick={() => setShowUserMenu(!showUserMenu)}
          className={`flex items-center gap-3 w-full rounded-xl border border-gray-200 dark:border-gray-700 hover:bg-gray-50 dark:hover:bg-gray-800 transition-colors ${
            collapsed && !isMobile ? 'justify-center p-2' : 'px-3 py-2.5'
          }`}
          title={collapsed && !isMobile ? userName : undefined}
        >
          {/* Avatar */}
          {avatarUrl ? (
            <img
              src={avatarUrl}
              alt={userName}
              className="w-9 h-9 rounded-full object-cover shrink-0 border-2 border-gray-200 dark:border-gray-600"
            />
          ) : (
            <div className="flex items-center justify-center w-9 h-9 rounded-full bg-primary/10 text-primary font-semibold text-sm shrink-0 border-2 border-primary/20">
              {initials}
            </div>
          )}

          {(!collapsed || isMobile) && (
            <>
              <div className="flex-1 text-left min-w-0">
                <p className="text-sm font-semibold text-gray-800 dark:text-white truncate">
                  {userName}
                </p>
                <p className="text-[10px] text-gray-400 dark:text-gray-500 truncate">
                  {userRole}
                </p>
              </div>
              <span className="material-symbols-outlined text-gray-400 dark:text-gray-500 text-lg shrink-0">
                {showUserMenu ? 'expand_more' : 'expand_less'}
              </span>
            </>
          )}
        </button>
      </div>
    </>
  )

  return (
    <>
      {/* Desktop Sidebar */}
      <aside
        className={`fixed top-0 left-0 z-40 h-screen flex flex-col bg-white dark:bg-gray-900 border-r border-gray-200 dark:border-gray-700 transition-all duration-300 ${
          collapsed ? 'w-[72px]' : 'w-64'
        } hidden lg:flex`}
      >
        {sidebarContent(false)}
      </aside>

      {/* Mobile Sidebar Overlay */}
      {mobileOpen && (
        <>
          {/* Backdrop */}
          <div
            className="fixed inset-0 z-40 bg-black/50 backdrop-blur-sm lg:hidden"
            onClick={() => setMobileOpen(false)}
          />
          {/* Mobile Sidebar */}
          <aside className="fixed top-0 left-0 z-50 h-screen w-64 flex flex-col bg-white dark:bg-gray-900 border-r border-gray-200 dark:border-gray-700 shadow-2xl lg:hidden animate-slide-in">
            {sidebarContent(true)}
          </aside>
        </>
      )}
    </>
  )
}
