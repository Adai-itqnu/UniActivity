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
import AdminNotifications from './pages/admin/AdminNotifications'

// Student
import StudentLayout from './components/student/StudentLayout'
import StudentDashboard from './pages/student/Dashboard'
import MyClass from './pages/student/MyClass'
import StudentActivities from './pages/student/Activities'
import MyRegistrations from './pages/student/MyRegistrations'
import MyScores from './pages/student/MyScores'
import Checkin from './pages/student/Checkin'
import StudentNotifications from './pages/student/Notifications'
import Profile from './pages/student/Profile'

// Manager
import ManagerLayout from './components/manager/ManagerLayout'
import ManagerDashboard from './pages/manager/Dashboard'
import ManagerActivities from './pages/manager/Activities'
import ActivityDetail from './pages/manager/ActivityDetail'
import Members from './pages/manager/Members'
import JoinRequests from './pages/manager/JoinRequests'
import PointRequests from './pages/manager/PointRequests'
import Reports from './pages/manager/Reports'
import ManagerNotifications from './pages/manager/Notifications'

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
            <Route path="notices" element={<AdminNotifications />} />
            <Route path="profile" element={<Profile />} />
          </Route>

          {/* Student routes */}
          <Route path="/student" element={<StudentLayout />}>
            <Route index element={<Navigate to="home" replace />} />
            <Route path="home" element={<StudentDashboard />} />
            <Route path="my-class" element={<MyClass />} />
            <Route path="activities" element={<StudentActivities />} />
            <Route path="my-registrations" element={<MyRegistrations />} />
            <Route path="my-scores" element={<MyScores />} />
            <Route path="checkin" element={<Checkin />} />
            <Route path="checkin/:activityId" element={<Checkin />} />
            <Route path="notifications" element={<StudentNotifications />} />
            <Route path="settings" element={<Profile />} />
            <Route path="help" element={<PlaceholderPage title="Trợ giúp" />} />
          </Route>

          {/* Manager routes */}
          <Route path="/manager" element={<ManagerLayout />}>
            <Route index element={<Navigate to="dashboard" replace />} />
            <Route path="dashboard" element={<ManagerDashboard />} />
            <Route path="activities" element={<ManagerActivities />} />
            <Route path="activities/:activityId" element={<ActivityDetail />} />
            <Route path="members" element={<Members />} />
            <Route path="join-requests" element={<JoinRequests />} />
            <Route path="point-requests" element={<PointRequests />} />
            <Route path="reports" element={<Reports />} />
            <Route path="notifications" element={<ManagerNotifications />} />
            <Route path="profile" element={<Profile />} />
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
