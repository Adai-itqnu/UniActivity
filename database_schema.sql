CREATE DATABASE IF NOT EXISTS uni_activitydb;
USE uni_activitydb;

-- -------------------------------------------------------
-- 1. Bảng Khoa (Faculties)
-- -------------------------------------------------------
CREATE TABLE faculties (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    status VARCHAR(20) DEFAULT 'ACTIVE'
);

-- -------------------------------------------------------
-- 2. Bảng Khóa học (Academic Years)
-- -------------------------------------------------------
CREATE TABLE academic_years (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    start_year INT NOT NULL,
    end_year INT NOT NULL,
    status VARCHAR(20) DEFAULT 'ACTIVE'
);

-- -------------------------------------------------------
-- 3. Bảng Học kỳ (Semesters)
-- -------------------------------------------------------
CREATE TABLE semesters (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    start_date DATE,
    end_date DATE,
    is_current BOOLEAN DEFAULT FALSE
);

-- -------------------------------------------------------
-- 4. Bảng Lớp học (Classes)
-- -------------------------------------------------------
CREATE TABLE classes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    faculty_id BIGINT,
    academic_year_id BIGINT,
    join_code VARCHAR(50),
    qr_code_url TEXT,
    FOREIGN KEY (faculty_id) REFERENCES faculties(id),
    FOREIGN KEY (academic_year_id) REFERENCES academic_years(id),
    INDEX idx_classes_faculty_id (faculty_id),
    INDEX idx_classes_academic_year_id (academic_year_id)
);

-- -------------------------------------------------------
-- 5. Bảng Người dùng (Users)
-- -------------------------------------------------------
CREATE TABLE users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(100) NOT NULL,
    email VARCHAR(100) NOT NULL UNIQUE,
    phone VARCHAR(20),
    role VARCHAR(20) NOT NULL,
    class_id BIGINT,
    avatar_url TEXT,
    status VARCHAR(20) DEFAULT 'ACTIVE',
    google_id VARCHAR(255),
    provider VARCHAR(50) DEFAULT 'LOCAL',
    email_verified BOOLEAN DEFAULT FALSE,
    token_version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (class_id) REFERENCES classes(id),
    INDEX idx_users_class_id (class_id),
    INDEX idx_users_role (role)
);

-- -------------------------------------------------------
-- 6. Bảng Thông báo (Notifications)
-- -------------------------------------------------------
CREATE TABLE notifications (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    type VARCHAR(50) NOT NULL,
    title VARCHAR(200) NOT NULL,
    message TEXT,
    link VARCHAR(500),
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_notification_user_id (user_id),
    INDEX idx_notification_created_at (created_at),
    INDEX idx_notification_user_read (user_id, is_read)
);

-- -------------------------------------------------------
-- 7. Bảng Yêu cầu tham gia lớp (Class Join Requests)
-- -------------------------------------------------------
CREATE TABLE class_join_requests (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    class_id BIGINT NOT NULL,
    status VARCHAR(20) DEFAULT 'PENDING',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    processed_at DATETIME,
    processed_by BIGINT,
    FOREIGN KEY (user_id) REFERENCES users(id),
    FOREIGN KEY (class_id) REFERENCES classes(id),
    FOREIGN KEY (processed_by) REFERENCES users(id),
    INDEX idx_class_join_requests_user_id (user_id),
    INDEX idx_class_join_requests_class_id (class_id)
);

-- -------------------------------------------------------
-- 8. Bảng OTP Đặt lại mật khẩu (Password Reset Tokens)
-- -------------------------------------------------------
CREATE TABLE password_reset_tokens (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(100) NOT NULL,
    otp_code VARCHAR(10) NOT NULL,
    expiry_time DATETIME NOT NULL,
    used BOOLEAN DEFAULT FALSE,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_password_reset_email (email)
);

-- OAuth login one-time exchange codes (only SHA-256 digests are stored)
CREATE TABLE oauth_exchange_codes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code_hash CHAR(64) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL,
    expires_at DATETIME NOT NULL,
    consumed_at DATETIME,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id),
    INDEX idx_oauth_exchange_user_id (user_id),
    INDEX idx_oauth_exchange_expires_at (expires_at)
);

-- -------------------------------------------------------
-- 9. Bảng Hoạt động (Activities)
-- -------------------------------------------------------
CREATE TABLE activities (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    banner_url TEXT,
    location VARCHAR(255),
    latitude DOUBLE,
    longitude DOUBLE,
    checkin_radius INT,
    start_time DATETIME,
    end_time DATETIME,
    registration_deadline DATETIME,
    status VARCHAR(20) DEFAULT 'DRAFT',
    scope VARCHAR(20) NOT NULL,
    semester_id BIGINT,
    created_by BIGINT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (semester_id) REFERENCES semesters(id),
    FOREIGN KEY (created_by) REFERENCES users(id),
    INDEX idx_activities_semester_id (semester_id),
    INDEX idx_activities_created_by (created_by)
);

-- -------------------------------------------------------
-- 10. Bảng Cấu hình Slot theo Lớp/Khóa (Activity Slots)
-- -------------------------------------------------------
CREATE TABLE activity_slots (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    activity_id BIGINT NOT NULL,
    faculty_id BIGINT,
    academic_year_id BIGINT,
    class_id BIGINT,
    max_quantity INT NOT NULL,
    current_quantity INT DEFAULT 0,
    FOREIGN KEY (activity_id) REFERENCES activities(id),
    FOREIGN KEY (faculty_id) REFERENCES faculties(id),
    FOREIGN KEY (academic_year_id) REFERENCES academic_years(id),
    FOREIGN KEY (class_id) REFERENCES classes(id),
    INDEX idx_activity_slots_activity_id (activity_id),
    INDEX idx_activity_slots_class_id (class_id)
);

-- -------------------------------------------------------
-- 11. Bảng Thang điểm/Vai trò (Score Options)
-- -------------------------------------------------------
CREATE TABLE score_options (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    activity_id BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    score_category VARCHAR(20) NOT NULL,
    score_value INT NOT NULL,
    description TEXT,
    FOREIGN KEY (activity_id) REFERENCES activities(id),
    INDEX idx_score_options_activity_id (activity_id)
);

-- -------------------------------------------------------
-- 12. Bảng Đăng ký cơ bản (Registrations)
-- -------------------------------------------------------
CREATE TABLE registrations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    student_id BIGINT NOT NULL,
    activity_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'REGISTERED',
    registered_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    check_in_time DATETIME,
    FOREIGN KEY (student_id) REFERENCES users(id),
    FOREIGN KEY (activity_id) REFERENCES activities(id),
    UNIQUE KEY uk_registrations_student_activity (student_id, activity_id),
    INDEX idx_registrations_student_id (student_id),
    INDEX idx_registrations_activity_id (activity_id)
);

-- -------------------------------------------------------
-- 13. Bảng Chi tiết Đăng ký & Điểm danh (Activity Registrations)
-- -------------------------------------------------------
CREATE TABLE activity_registrations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    student_id BIGINT NOT NULL,
    activity_id BIGINT NOT NULL,
    activity_slot_id BIGINT,
    score_option_id BIGINT,
    registered_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(20) DEFAULT 'REGISTERED',
    attendance_confirmed BOOLEAN DEFAULT FALSE,
    confirmed_at DATETIME,
    notes VARCHAR(500),
    evidence_url VARCHAR(1000),
    is_approved BOOLEAN,
    rejection_reason VARCHAR(500),
    FOREIGN KEY (student_id) REFERENCES users(id),
    FOREIGN KEY (activity_id) REFERENCES activities(id),
    FOREIGN KEY (activity_slot_id) REFERENCES activity_slots(id),
    FOREIGN KEY (score_option_id) REFERENCES score_options(id),
    UNIQUE KEY uk_activity_regs_student_activity (student_id, activity_id),
    INDEX idx_activity_regs_student_id (student_id),
    INDEX idx_activity_regs_activity_id (activity_id)
);

-- -------------------------------------------------------
-- 14. Bảng Phiên điểm danh QR Động (Checkin Sessions)
-- -------------------------------------------------------
CREATE TABLE checkin_sessions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    activity_id BIGINT NOT NULL,
    token VARCHAR(255) NOT NULL,
    start_time DATETIME,
    end_time DATETIME,
    created_by BIGINT,
    FOREIGN KEY (activity_id) REFERENCES activities(id),
    FOREIGN KEY (created_by) REFERENCES users(id),
    INDEX idx_checkin_sessions_activity (activity_id)
);

-- -------------------------------------------------------
-- 15. Bảng Đơn nộp minh chứng (Evidences)
-- -------------------------------------------------------
CREATE TABLE evidences (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    registration_id BIGINT NOT NULL UNIQUE,
    score_option_id BIGINT NOT NULL,
    description TEXT,
    status VARCHAR(20) DEFAULT 'PENDING',
    submitted_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    reviewer_id BIGINT,
    review_comment TEXT,
    reviewed_at DATETIME,
    FOREIGN KEY (registration_id) REFERENCES registrations(id),
    FOREIGN KEY (score_option_id) REFERENCES score_options(id),
    FOREIGN KEY (reviewer_id) REFERENCES users(id)
);

-- -------------------------------------------------------
-- 16. Bảng Hình ảnh Minh chứng đính kèm (Evidence Images)
-- -------------------------------------------------------
CREATE TABLE evidence_images (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    evidence_id BIGINT NOT NULL,
    image_url TEXT NOT NULL,
    FOREIGN KEY (evidence_id) REFERENCES evidences(id)
);

-- -------------------------------------------------------
-- 17. Bảng Yêu cầu cộng điểm tự khai (Point Requests)
-- -------------------------------------------------------
CREATE TABLE point_requests (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    student_id BIGINT NOT NULL,
    semester_id BIGINT NOT NULL,
    criteria_code VARCHAR(20) NOT NULL,
    claimed_score INT,
    description TEXT,
    evidence_image_url TEXT,
    status VARCHAR(20) DEFAULT 'PENDING',
    reviewer_id BIGINT,
    review_comment TEXT,
    reviewed_at DATETIME,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (student_id) REFERENCES users(id),
    FOREIGN KEY (semester_id) REFERENCES semesters(id),
    FOREIGN KEY (reviewer_id) REFERENCES users(id),
    INDEX idx_point_requests_student (student_id),
    INDEX idx_point_requests_semester (semester_id)
);

-- -------------------------------------------------------
-- 18. Bảng Điểm rèn luyện tổng hợp (Student Training Points)
-- -------------------------------------------------------
CREATE TABLE student_training_points (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    student_id BIGINT NOT NULL,
    semester_id BIGINT NOT NULL,
    total_score INT DEFAULT 0,
    classification VARCHAR(50),
    status VARCHAR(20) DEFAULT 'DRAFT',
    FOREIGN KEY (student_id) REFERENCES users(id),
    FOREIGN KEY (semester_id) REFERENCES semesters(id),
    UNIQUE KEY uk_student_training_points_student_semester (student_id, semester_id),
    INDEX idx_student_training_points_student (student_id)
);

-- -------------------------------------------------------
-- 19. Bảng Chi tiết dòng điểm (Training Point Details)
-- -------------------------------------------------------
CREATE TABLE training_point_details (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    student_training_point_id BIGINT NOT NULL,
    criteria_code VARCHAR(20) NOT NULL,
    score INT NOT NULL,
    source_type VARCHAR(50),
    reference_id BIGINT,
    description TEXT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (student_training_point_id) REFERENCES student_training_points(id),
    UNIQUE KEY uk_tp_detail_source_reference
        (student_training_point_id, criteria_code, source_type, reference_id),
    INDEX idx_tp_details_stp_id (student_training_point_id)
);


-- =======================================================
-- DỮ LIỆU KHỞI TẠO MẪU (Seed Data)
-- =======================================================

-- 1. Khoa
INSERT INTO faculties (code, name, description, status) VALUES
('CNTT', 'Công nghệ Thông tin', 'Khoa Công nghệ Thông tin và Truyền thông', 'ACTIVE'),
('KT', 'Kinh tế', 'Khoa Kinh tế và Quản trị Kinh doanh', 'ACTIVE'),
('NN', 'Ngoại ngữ', 'Khoa Ngoại ngữ', 'ACTIVE'),
('XD', 'Xây dựng', 'Khoa Kỹ thuật Xây dựng', 'ACTIVE'),
('DT', 'Điện tử', 'Khoa Điện - Điện tử', 'ACTIVE');

-- 2. Khóa học
INSERT INTO academic_years (code, start_year, end_year, status) VALUES
('K43', 2020, 2024, 'ACTIVE'),
('K44', 2021, 2025, 'ACTIVE'),
('K45', 2022, 2026, 'ACTIVE'),
('K46', 2023, 2027, 'ACTIVE'),
('K47', 2024, 2028, 'ACTIVE');

-- 3. Lớp học hành chính
INSERT INTO classes (code, name, faculty_id, academic_year_id, join_code) VALUES
('CNTT-K45A', 'CNTT K45A', 1, 3, 'ABC123'),
('CNTT-K45B', 'CNTT K45B', 1, 3, 'DEF456'),
('KT-K45A', 'Kinh tế K45A', 2, 3, 'GHI789'),
('NN-K45A', 'Ngoại ngữ K45A', 3, 3, 'JKL012'),
('XD-K45A', 'Xây dựng K45A', 4, 3, 'MNO345'),
('DT-K45A', 'Điện tử K45A', 5, 3, 'PQR678'),
('CNTT-K46A', 'CNTT K46A', 1, 4, 'STU901'),
('KT-K46A', 'Kinh tế K46A', 2, 4, 'VWX234');

-- 4. Học kỳ
INSERT INTO semesters (name, start_date, end_date, is_current) VALUES
('Học kỳ 1 2023-2024', '2023-09-01', '2024-01-15', FALSE),
('Học kỳ 2 2023-2024', '2024-02-01', '2024-06-15', FALSE),
('Học kỳ 1 2024-2025', '2024-09-01', '2025-01-15', TRUE),
('Học kỳ 2 2024-2025', '2025-02-01', '2025-06-15', FALSE);

-- 5. Tài khoản người dùng mẫu (Mật khẩu đã mã hóa Bcrypt của: "123456")
INSERT INTO users (username, password_hash, full_name, email, phone, role, class_id, status, provider) VALUES
-- ADMIN
('admin', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZRGdjGj/n3hO7Yh2dVZKb7VVR6qKu', 'Quản trị viên', 'admin@uni.edu.vn', '0123456789', 'ADMIN', NULL, 'ACTIVE', 'LOCAL'),

-- MANAGERS (Lớp trưởng/Bí thư)
('manager1', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZRGdjGj/n3hO7Yh2dVZKb7VVR6qKu', 'Nguyễn Văn Quản', 'manager1@uni.edu.vn', '0987654321', 'MANAGER', 1, 'ACTIVE', 'LOCAL'),
('manager2', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZRGdjGj/n3hO7Yh2dVZKb7VVR6qKu', 'Trần Thị Quản', 'manager2@uni.edu.vn', '0987654322', 'MANAGER', 2, 'ACTIVE', 'LOCAL'),

-- STUDENTS (Sinh viên)
('student1', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZRGdjGj/n3hO7Yh2dVZKb7VVR6qKu', 'Nguyễn Văn An', 'student1@uni.edu.vn', '0912345678', 'STUDENT', 1, 'ACTIVE', 'LOCAL'),
('student2', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZRGdjGj/n3hO7Yh2dVZKb7VVR6qKu', 'Lê Thị Bình', 'student2@uni.edu.vn', '0912345679', 'STUDENT', 1, 'ACTIVE', 'LOCAL'),
('student3', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZRGdjGj/n3hO7Yh2dVZKb7VVR6qKu', 'Trần Văn Cường', 'student3@uni.edu.vn', '0912345680', 'STUDENT', 2, 'ACTIVE', 'LOCAL');
