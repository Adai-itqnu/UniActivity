import { lazy, Suspense } from 'react'
import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router-dom'
import { DarkModeProvider } from './contexts/DarkModeContext'

const AuthPage = lazy(() => import('./pages/AuthPage'))
const TermsPage = lazy(() => import('./pages/TermsPage'))
const AdminLayout = lazy(() => import('./components/admin/AdminLayout'))
const AdminDashboard = lazy(() => import('./pages/admin/Dashboard'))
const FacultyList = lazy(() => import('./pages/admin/FacultyList'))
const AcademicYearList = lazy(() => import('./pages/admin/AcademicYearList'))
const ClassList = lazy(() => import('./pages/admin/ClassList'))
const SemesterList = lazy(() => import('./pages/admin/SemesterList'))
const ActivityList = lazy(() => import('./pages/admin/ActivityList'))
const UserList = lazy(() => import('./pages/admin/UserList'))
const AdminNotifications = lazy(() => import('./pages/admin/AdminNotifications'))
const StudentLayout = lazy(() => import('./components/student/StudentLayout'))
const StudentDashboard = lazy(() => import('./pages/student/Dashboard'))
const MyClass = lazy(() => import('./pages/student/MyClass'))
const StudentActivities = lazy(() => import('./pages/student/Activities'))
const MyRegistrations = lazy(() => import('./pages/student/MyRegistrations'))
const MyScores = lazy(() => import('./pages/student/MyScores'))
const Checkin = lazy(() => import('./pages/student/Checkin'))
const StudentNotifications = lazy(() => import('./pages/student/Notifications'))
const Profile = lazy(() => import('./pages/student/Profile'))
const ManagerLayout = lazy(() => import('./components/manager/ManagerLayout'))
const ManagerDashboard = lazy(() => import('./pages/manager/Dashboard'))
const ManagerActivities = lazy(() => import('./pages/manager/Activities'))
const ActivityDetail = lazy(() => import('./pages/manager/ActivityDetail'))
const Members = lazy(() => import('./pages/manager/Members'))
const JoinRequests = lazy(() => import('./pages/manager/JoinRequests'))
const PointRequests = lazy(() => import('./pages/manager/PointRequests'))
const Reports = lazy(() => import('./pages/manager/Reports'))
const ManagerNotifications = lazy(() => import('./pages/manager/Notifications'))

function RouteLoading() {
  return (
    <div className="flex min-h-screen items-center justify-center bg-white dark:bg-gray-950">
      <div className="size-10 animate-spin rounded-full border-4 border-gray-200 border-t-emerald-500" />
    </div>
  )
}

function App() {
  return (
    <DarkModeProvider>
      <Router>
        <Suspense fallback={<RouteLoading />}>
          <Routes>
            <Route path="/login" element={<AuthPage />} />
            <Route path="/register" element={<AuthPage defaultTab="register" />} />
            <Route path="/terms" element={<TermsPage />} />

            <Route path="/admin" element={<AdminLayout />}>
              <Route index element={<Navigate to="dashboard" replace />} />
              <Route path="dashboard" element={<AdminDashboard />} />
              <Route path="faculties" element={<FacultyList />} />
              <Route path="academic-years" element={<AcademicYearList />} />
              <Route path="classes" element={<ClassList />} />
              <Route path="semesters" element={<SemesterList />} />
              <Route path="activities" element={<ActivityList />} />
              <Route path="users" element={<UserList />} />
              <Route path="notices" element={<AdminNotifications />} />
              <Route path="profile" element={<Profile />} />
            </Route>

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
              <Route path="my-activities" element={<StudentActivities />} />
              <Route path="my-registrations" element={<MyRegistrations />} />
              <Route path="my-scores" element={<MyScores />} />
              <Route path="checkin" element={<Checkin />} />
              <Route path="checkin/:activityId" element={<Checkin />} />
            </Route>

            <Route path="*" element={<Navigate to="/login" replace />} />
          </Routes>
        </Suspense>
      </Router>
    </DarkModeProvider>
  )
}

function PlaceholderPage({ title }) {
  return (
    <div className="flex flex-col items-center justify-center h-[60vh] text-center">
      <span className="material-symbols-outlined text-6xl text-gray-300 dark:text-gray-600 mb-4">construction</span>
      <h2 className="text-xl font-semibold text-gray-700 dark:text-gray-300">{title}</h2>
      <p className="text-sm text-gray-400 dark:text-gray-500 mt-1">Trang này đang được phát triển...</p>
    </div>
  )
}

export default App
