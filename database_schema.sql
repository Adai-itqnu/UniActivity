CREATE DATABASE IF NOT EXISTS uni_activitydb;
USE uni_activitydb;

-- 1. Bảng Khoa (Faculties)  [để khớp phần INSERT mẫu]
CREATE TABLE faculties (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(20) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    status VARCHAR(20) DEFAULT 'ACTIVE'
);

-- 2. Bảng Khóa học (Academic Years)  [để khớp phần INSERT mẫu]
CREATE TABLE academic_years (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(20) NOT NULL UNIQUE,
    start_year INT NOT NULL,
    end_year INT NOT NULL,
    status VARCHAR(20) DEFAULT 'ACTIVE'
);

-- 5. Bảng Học kỳ (Semesters)  [để khớp phần INSERT mẫu]
CREATE TABLE semesters (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    start_date DATE,
    end_date DATE,
    is_current BOOLEAN DEFAULT FALSE
);

-- 3. Bảng Lớp (Classes)  [KHỚP StudentClass.java]
-- @Table(name="classes")
-- fields: code (unique), name, faculty_id, academic_year_id, joinCode, qrCodeUrl(TEXT)
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

-- 4. Bảng Người dùng (Users)  [để khớp phần INSERT mẫu + FK class_id]
CREATE TABLE users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(100) NOT NULL,
    email VARCHAR(100) NOT NULL UNIQUE,
    phone VARCHAR(20),
    role VARCHAR(20) NOT NULL,
    class_id BIGINT,
    status VARCHAR(20) DEFAULT 'ACTIVE',
    FOREIGN KEY (class_id) REFERENCES classes(id),
    INDEX idx_users_class_id (class_id),
    INDEX idx_users_role (role)
);

-- 7. Bảng Hoạt động (Activities)
-- (Tối thiểu để FK từ registrations/activity_registrations chạy được)
CREATE TABLE activities (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    created_by BIGINT,
    FOREIGN KEY (created_by) REFERENCES users(id),
    INDEX idx_activities_created_by (created_by)
);

-- 6. Bảng Yêu cầu tham gia lớp (Class Join Requests) (tối thiểu)
CREATE TABLE class_join_requests (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    processed_by BIGINT,
    FOREIGN KEY (processed_by) REFERENCES users(id),
    INDEX idx_class_join_requests_processed_by (processed_by)
);

-- 8. Bảng Cấu hình Slot (Activity Slots) (tối thiểu để FK từ activity_registrations)
CREATE TABLE activity_slots (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    class_id BIGINT,
    FOREIGN KEY (class_id) REFERENCES classes(id),
    INDEX idx_activity_slots_class_id (class_id)
);

-- 9. Bảng Tùy chọn điểm (Score Options) (tối thiểu để FK từ activity_registrations)
CREATE TABLE score_options (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    activity_id BIGINT,
    FOREIGN KEY (activity_id) REFERENCES activities(id),
    INDEX idx_score_options_activity_id (activity_id)
);

-- Registration.java  [BẠN ĐANG CÓ ENTITY NÀY => PHẢI CÓ BẢNG]
CREATE TABLE registrations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    student_id BIGINT NOT NULL,
    activity_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    registered_at DATETIME,
    check_in_time DATETIME,
    FOREIGN KEY (student_id) REFERENCES users(id),
    FOREIGN KEY (activity_id) REFERENCES activities(id),
    UNIQUE KEY uk_registrations_student_activity (student_id, activity_id),
    INDEX idx_registrations_student_id (student_id),
    INDEX idx_registrations_activity_id (activity_id)
);

-- ActivityRegistration.java  [KHỚP entity bạn gửi]
CREATE TABLE activity_registrations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    student_id BIGINT NOT NULL,
    activity_id BIGINT NOT NULL,
    activity_slot_id BIGINT,
    score_option_id BIGINT,
    registered_at DATETIME,
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

-- Các bảng dưới đây giữ tối thiểu (vì bạn chưa gửi entity chi tiết)
CREATE TABLE checkin_sessions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    created_by BIGINT,
    FOREIGN KEY (created_by) REFERENCES users(id)
);

CREATE TABLE evidences (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    reviewer_id BIGINT,
    FOREIGN KEY (reviewer_id) REFERENCES users(id)
);

CREATE TABLE evidence_images (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    evidence_id BIGINT,
    FOREIGN KEY (evidence_id) REFERENCES evidences(id)
);

CREATE TABLE point_requests (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    reviewer_id BIGINT,
    FOREIGN KEY (reviewer_id) REFERENCES users(id)
);

CREATE TABLE student_training_points (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    student_id BIGINT,
    semester_id BIGINT,
    UNIQUE KEY uk_student_training_points_student_semester (student_id, semester_id),
    FOREIGN KEY (student_id) REFERENCES users(id),
    FOREIGN KEY (semester_id) REFERENCES semesters(id)
);

CREATE TABLE training_point_details (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    student_training_point_id BIGINT,
    FOREIGN KEY (student_training_point_id) REFERENCES student_training_points(id)
);

-- ==========================================
-- DỮ LIỆU MẪU (Sample Data)
-- ==========================================

-- Khoa
INSERT INTO faculties (code, name, description, status) VALUES
('CNTT', 'Công nghệ Thông tin', 'Khoa Công nghệ Thông tin và Truyền thông', 'ACTIVE'),
('KT', 'Kinh tế', 'Khoa Kinh tế và Quản trị Kinh doanh', 'ACTIVE'),
('NN', 'Ngoại ngữ', 'Khoa Ngoại ngữ', 'ACTIVE'),
('XD', 'Xây dựng', 'Khoa Kỹ thuật Xây dựng', 'ACTIVE'),
('DT', 'Điện tử', 'Khoa Điện - Điện tử', 'ACTIVE');

-- Khóa học
INSERT INTO academic_years (code, start_year, end_year, status) VALUES
('K43', 2020, 2024, 'ACTIVE'),
('K44', 2021, 2025, 'ACTIVE'),
('K45', 2022, 2026, 'ACTIVE'),
('K46', 2023, 2027, 'ACTIVE'),
('K47', 2024, 2028, 'ACTIVE');

-- Lớp
INSERT INTO classes (code, name, faculty_id, academic_year_id, join_code) VALUES
('CNTT-K45A', 'CNTT K45A', 1, 3, 'ABC123'),
('CNTT-K45B', 'CNTT K45B', 1, 3, 'DEF456'),
('KT-K45A', 'Kinh tế K45A', 2, 3, 'GHI789'),
('NN-K45A', 'Ngoại ngữ K45A', 3, 3, 'JKL012'),
('XD-K45A', 'Xây dựng K45A', 4, 3, 'MNO345'),
('DT-K45A', 'Điện tử K45A', 5, 3, 'PQR678'),
('CNTT-K46A', 'CNTT K46A', 1, 4, 'STU901'),
('KT-K46A', 'Kinh tế K46A', 2, 4, 'VWX234');

-- Học kỳ
INSERT INTO semesters (name, start_date, end_date, is_current) VALUES
('Học kỳ 1 2023-2024', '2023-09-01', '2024-01-15', FALSE),
('Học kỳ 2 2023-2024', '2024-02-01', '2024-06-15', FALSE),
('Học kỳ 1 2024-2025', '2024-09-01', '2025-01-15', TRUE),
('Học kỳ 2 2024-2025', '2025-02-01', '2025-06-15', FALSE);

-- Người dùng (password: 123456)
INSERT INTO users (username, password_hash, full_name, email, phone, role, class_id, status) VALUES
('admin', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZRGdjGj/n3hO7Yh2dVZKb7VVR6qKu', 'Quản trị viên', 'admin@uni.edu.vn', '0123456789', 'ADMIN', NULL, 'ACTIVE'),
('manager1', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZRGdjGj/n3hO7Yh2dVZKb7VVR6qKu', 'Nguyễn Văn Quản', 'manager1@uni.edu.vn', '0987654321', 'MANAGER', 1, 'ACTIVE'),
('manager2', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZRGdjGj/n3hO7Yh2dVZKb7VVR6qKu', 'Trần Thị Quản', 'manager2@uni.edu.vn', '0987654322', 'MANAGER', 2, 'ACTIVE');
-- ...existing code...