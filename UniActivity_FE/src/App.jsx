import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router-dom'
import { DarkModeProvider } from './contexts/DarkModeContext'
import AuthPage from './pages/AuthPage'
import TermsPage from './pages/TermsPage'

// Admin
import AdminLayout from './components/admin/AdminLayout'
import AdminDashboard from './pages/admin/Dashboard'
import FacultyList from './pages/admin/FacultyList'
import AcademicYearList from './pages/admin/AcademicYearList'
import ClassList from './pages/admin/ClassList'
import SemesterList from './pages/admin/SemesterList'
import ActivityList from './pages/admin/ActivityList'
import UserList from './pages/admin/UserList'

function App() {
  return (
    <DarkModeProvider>
      <Router>
        <Routes>
          {/* Auth routes */}
          <Route path="/login" element={<AuthPage />} />
          <Route path="/register" element={<AuthPage defaultTab="register" />} />

          {/* Trang điều khoản */}
          <Route path="/terms" element={<TermsPage />} />

          {/* Admin routes */}
          <Route path="/admin" element={<AdminLayout />}>
            <Route index element={<Navigate to="dashboard" replace />} />
            <Route path="dashboard" element={<AdminDashboard />} />
            {/* Placeholder routes — sẽ thêm component sau */}
            <Route path="faculties" element={<FacultyList />} />
            <Route path="academic-years" element={<AcademicYearList />} />
            <Route path="classes" element={<ClassList />} />
            <Route path="semesters" element={<SemesterList />} />
            <Route path="activities" element={<ActivityList />} />
            <Route path="users" element={<UserList />} />
            <Route path="notices" element={<PlaceholderPage title="Thông báo" />} />
            <Route path="settings" element={<PlaceholderPage title="Cài đặt" />} />
          </Route>

          {/* Redirect mặc định sang login */}
          <Route path="*" element={<Navigate to="/login" replace />} />
        </Routes>
      </Router>
    </DarkModeProvider>
  )
}

/* Trang tạm cho các route chưa xây dựng */
function PlaceholderPage({ title }) {
  return (
    <div className="flex flex-col items-center justify-center h-[60vh] text-center">
      <span className="material-symbols-outlined text-6xl text-gray-300 dark:text-gray-600 mb-4">
        construction
      </span>
      <h2 className="text-xl font-semibold text-gray-700 dark:text-gray-300">{title}</h2>
      <p className="text-sm text-gray-400 dark:text-gray-500 mt-1">
        Trang này đang được phát triển...
      </p>
    </div>
  )
}

export default App
