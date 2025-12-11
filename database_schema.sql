-- ==========================================
-- UniActivity Database Schema
-- Updated to match Entity classes
-- ==========================================

CREATE DATABASE IF NOT EXISTS uni_activitydb;
USE uni_activitydb;

-- 1. Bảng Khoa (Faculties)
CREATE TABLE faculties (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    status VARCHAR(20) DEFAULT 'ACTIVE'
);

-- 2. Bảng Khóa học (Academic Years)
CREATE TABLE academic_years (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    start_year INT,
    end_year INT,
    status VARCHAR(20) DEFAULT 'ACTIVE'
);

-- 3. Bảng Lớp (Classes)
CREATE TABLE classes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    faculty_id BIGINT,
    academic_year_id BIGINT,
    join_code VARCHAR(20),
    qr_code_url TEXT,
    FOREIGN KEY (faculty_id) REFERENCES faculties(id),
    FOREIGN KEY (academic_year_id) REFERENCES academic_years(id)
);

-- 4. Bảng Người dùng (Users)
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
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (class_id) REFERENCES classes(id)
);

-- 5. Bảng Học kỳ (Semesters)
CREATE TABLE semesters (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(50) NOT NULL,
    start_date DATE,
    end_date DATE,
    is_current BOOLEAN DEFAULT FALSE
);

-- 6. Bảng Yêu cầu tham gia lớp (Class Join Requests)
CREATE TABLE class_join_requests (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    class_id BIGINT NOT NULL,
    status VARCHAR(20) DEFAULT 'PENDING',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    processed_at DATETIME,
    processed_by BIGINT,
    FOREIGN KEY (user_id) REFERENCES users(id),
    FOREIGN KEY (class_id) REFERENCES classes(id),
    FOREIGN KEY (processed_by) REFERENCES users(id)
);

-- 7. Bảng Hoạt động (Activities)
CREATE TABLE activities (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    banner_url TEXT,
    location VARCHAR(255),
    start_time DATETIME,
    end_time DATETIME,
    registration_deadline DATETIME,
    status VARCHAR(20) DEFAULT 'DRAFT',
    scope VARCHAR(20) NOT NULL,
    semester_id BIGINT,
    created_by BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (semester_id) REFERENCES semesters(id),
    FOREIGN KEY (created_by) REFERENCES users(id)
);

-- 8. Bảng Cấu hình Slot (Activity Slots)
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
    FOREIGN KEY (class_id) REFERENCES classes(id)
);

-- 9. Bảng Tùy chọn điểm (Score Options)
CREATE TABLE score_options (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    activity_id BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    score_category VARCHAR(20) NOT NULL,
    score_value INT NOT NULL,
    description TEXT,
    FOREIGN KEY (activity_id) REFERENCES activities(id)
);

-- 10. Bảng Đăng ký hoạt động (Activity Registrations)
-- *** UPDATED: Đổi tên từ 'registrations' và thêm các trường mới ***
CREATE TABLE activity_registrations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    student_id BIGINT NOT NULL,
    activity_id BIGINT NOT NULL,
    activity_slot_id BIGINT,
    score_option_id BIGINT,
    status VARCHAR(30) DEFAULT 'REGISTERED',
    registered_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
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
    UNIQUE (student_id, activity_id)
);

-- 11. Bảng Phiên Check-in (Checkin Sessions)
CREATE TABLE checkin_sessions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    activity_id BIGINT NOT NULL,
    token VARCHAR(100) NOT NULL,
    start_time DATETIME,
    end_time DATETIME,
    created_by BIGINT,
    FOREIGN KEY (activity_id) REFERENCES activities(id),
    FOREIGN KEY (created_by) REFERENCES users(id)
);

-- 12. Bảng Minh chứng (Evidences)
CREATE TABLE evidences (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    registration_id BIGINT NOT NULL UNIQUE,
    score_option_id BIGINT NOT NULL,
    description TEXT,
    status VARCHAR(20) DEFAULT 'PENDING',
    submitted_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    reviewer_id BIGINT,
    review_comment TEXT,
    reviewed_at DATETIME,
    FOREIGN KEY (registration_id) REFERENCES activity_registrations(id),
    FOREIGN KEY (score_option_id) REFERENCES score_options(id),
    FOREIGN KEY (reviewer_id) REFERENCES users(id)
);

-- 13. Bảng Ảnh minh chứng (Evidence Images)
CREATE TABLE evidence_images (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    evidence_id BIGINT NOT NULL,
    image_url TEXT NOT NULL,
    FOREIGN KEY (evidence_id) REFERENCES evidences(id)
);

-- 14. Bảng Yêu cầu điểm thủ công (Point Requests)
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
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (student_id) REFERENCES users(id),
    FOREIGN KEY (semester_id) REFERENCES semesters(id),
    FOREIGN KEY (reviewer_id) REFERENCES users(id)
);

-- 15. Bảng Tổng hợp điểm rèn luyện (Student Training Points)
CREATE TABLE student_training_points (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    student_id BIGINT NOT NULL,
    semester_id BIGINT NOT NULL,
    total_score INT DEFAULT 0,
    classification VARCHAR(20),
    status VARCHAR(20) DEFAULT 'DRAFT',
    FOREIGN KEY (student_id) REFERENCES users(id),
    FOREIGN KEY (semester_id) REFERENCES semesters(id),
    UNIQUE (student_id, semester_id)
);

-- 16. Bảng Chi tiết điểm rèn luyện (Training Point Details)
CREATE TABLE training_point_details (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    student_training_point_id BIGINT NOT NULL,
    criteria_code VARCHAR(20) NOT NULL,
    score INT NOT NULL,
    source_type VARCHAR(20),
    reference_id BIGINT,
    description TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
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
-- Admin
('admin', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZRGdjGj/n3hO7Yh2dVZKb7VVR6qKu', 'Quản trị viên', 'admin@uni.edu.vn', '0123456789', 'ADMIN', NULL, 'ACTIVE'),
-- Managers (quản lý lớp)
('manager1', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZRGdjGj/n3hO7Yh2dVZKb7VVR6qKu', 'Nguyễn Văn Quản', 'manager1@uni.edu.vn', '0987654321', 'MANAGER', 1, 'ACTIVE'),
('manager2', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZRGdjGj/n3hO7Yh2dVZKb7VVR6qKu', 'Trần Thị Quản', 'manager2@uni.edu.vn', '0987654322', 'MANAGER', 2, 'ACTIVE'),
-- Students CNTT-K45A
('21110001', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZRGdjGj/n3hO7Yh2dVZKb7VVR6qKu', 'Nguyễn Văn An', 'an.nv@student.uni.edu.vn', '0901234567', 'STUDENT', 1, 'ACTIVE'),
('21110002', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZRGdjGj/n3hO7Yh2dVZKb7VVR6qKu', 'Trần Thị Bình', 'binh.tt@student.uni.edu.vn', '0901234568', 'STUDENT', 1, 'ACTIVE'),
('21110003', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZRGdjGj/n3hO7Yh2dVZKb7VVR6qKu', 'Lê Văn Cường', 'cuong.lv@student.uni.edu.vn', '0901234569', 'STUDENT', 1, 'ACTIVE'),
('21110004', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZRGdjGj/n3hO7Yh2dVZKb7VVR6qKu', 'Phạm Thị Dung', 'dung.pt@student.uni.edu.vn', '0901234570', 'STUDENT', 1, 'ACTIVE'),
('21110005', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZRGdjGj/n3hO7Yh2dVZKb7VVR6qKu', 'Hoàng Văn Em', 'em.hv@student.uni.edu.vn', '0901234571', 'STUDENT', 1, 'ACTIVE'),
-- Students CNTT-K45B
('21110006', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZRGdjGj/n3hO7Yh2dVZKb7VVR6qKu', 'Ngô Thị Phương', 'phuong.nt@student.uni.edu.vn', '0901234572', 'STUDENT', 2, 'ACTIVE'),
('21110007', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZRGdjGj/n3hO7Yh2dVZKb7VVR6qKu', 'Vũ Văn Giang', 'giang.vv@student.uni.edu.vn', '0901234573', 'STUDENT', 2, 'ACTIVE'),
-- Students KT-K45A
('21110008', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZRGdjGj/n3hO7Yh2dVZKb7VVR6qKu', 'Đặng Văn Hải', 'hai.dv@student.uni.edu.vn', '0901234574', 'STUDENT', 3, 'ACTIVE'),
('21110009', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZRGdjGj/n3hO7Yh2dVZKb7VVR6qKu', 'Bùi Thị Hương', 'huong.bt@student.uni.edu.vn', '0901234575', 'STUDENT', 3, 'ACTIVE');

-- Hoạt động
INSERT INTO activities (name, description, banner_url, location, start_time, end_time, registration_deadline, status, scope, semester_id, created_by) VALUES
('Ngày hội CNTT 2024', 'Triển lãm và workshop công nghệ thông tin', '/uploads/activities/cntt-day.jpg', 'Hội trường A', '2024-11-15 08:00:00', '2024-11-15 17:00:00', '2024-11-10 23:59:59', 'OPEN', 'SCHOOL', 3, 1),
('Cuộc thi Hackathon', 'Coding competition 48 giờ', '/uploads/activities/hackathon.jpg', 'Phòng máy B1', '2024-11-20 08:00:00', '2024-11-22 08:00:00', '2024-11-15 23:59:59', 'OPEN', 'FACULTY', 3, 1),
('Hội thảo AI/ML', 'Hội thảo chuyên đề về Trí tuệ nhân tạo', '/uploads/activities/ai-workshop.jpg', 'Phòng hội thảo C2', '2024-12-05 13:30:00', '2024-12-05 17:00:00', '2024-12-01 23:59:59', 'OPEN', 'SCHOOL', 3, 1),
('Chiến dịch Mùa hè xanh', 'Hoạt động tình nguyện mùa hè', '/uploads/activities/summer-green.jpg', 'Xã ABC, Huyện XYZ', '2024-07-01 06:00:00', '2024-07-07 18:00:00', '2024-06-25 23:59:59', 'FINISHED', 'SCHOOL', 2, 1),
('Giải bóng đá sinh viên', 'Giải đấu bóng đá giữa các khoa', '/uploads/activities/football.jpg', 'Sân vận động trường', '2024-12-10 07:00:00', '2024-12-15 18:00:00', '2024-12-05 23:59:59', 'OPEN', 'SCHOOL', 3, 1);

-- Score Options (các mức điểm cho hoạt động)
INSERT INTO score_options (activity_id, name, score_category, score_value, description) VALUES
-- Ngày hội CNTT
(1, 'Tham dự', '3.1', 3, 'Điểm cộng cho sinh viên tham dự'),
(1, 'Ban tổ chức', '3.2', 5, 'Điểm cộng cho thành viên BTC'),
(1, 'Thuyết trình', '3.1', 5, 'Điểm cộng cho sinh viên thuyết trình'),
-- Hackathon
(2, 'Cổ vũ', '3.1', 2, 'Điểm cộng cho sinh viên cổ vũ'),
(2, 'Tham gia', '3.1', 5, 'Điểm cộng cho sinh viên tham gia'),
(2, 'Giải Ba', '6.1', 8, 'Điểm cộng cho đội giải Ba'),
(2, 'Giải Nhì', '6.1', 10, 'Điểm cộng cho đội giải Nhì'),
(2, 'Giải Nhất', '6.1', 15, 'Điểm cộng cho đội giải Nhất'),
-- Hội thảo AI/ML  
(3, 'Tham dự', '3.1', 3, 'Điểm cộng cho sinh viên tham dự'),
(3, 'Báo cáo viên', '3.2', 8, 'Điểm cộng cho sinh viên báo cáo'),
-- Mùa hè xanh
(4, 'Tình nguyện viên', '3.1', 10, 'Điểm cộng cho TNV tham gia 7 ngày'),
(4, 'Đội trưởng', '3.2', 15, 'Điểm cộng cho đội trưởng'),
-- Bóng đá
(5, 'Cổ vũ', '3.1', 2, 'Điểm cộng cho sinh viên cổ vũ'),
(5, 'Cầu thủ', '3.1', 5, 'Điểm cộng cho cầu thủ tham gia'),
(5, 'Vô địch', '6.1', 10, 'Điểm cộng cho đội vô địch');

-- Activity Slots
INSERT INTO activity_slots (activity_id, faculty_id, max_quantity, current_quantity) VALUES
(1, NULL, 200, 45),  -- Ngày hội CNTT - toàn trường
(2, 1, 50, 20),      -- Hackathon - khoa CNTT
(3, NULL, 100, 35),  -- Hội thảo AI/ML - toàn trường
(4, NULL, 50, 50),   -- Mùa hè xanh - đã đầy
(5, NULL, 150, 80);  -- Bóng đá - toàn trường

-- Đăng ký hoạt động mẫu
INSERT INTO activity_registrations (student_id, activity_id, score_option_id, status, registered_at, attendance_confirmed, evidence_url, is_approved) VALUES
-- Ngày hội CNTT
(4, 1, 1, 'CHECKED_IN', '2024-11-08 10:00:00', TRUE, '/uploads/evidence/reg1.jpg', TRUE),
(5, 1, 1, 'CHECKED_IN', '2024-11-08 11:00:00', TRUE, '/uploads/evidence/reg2.jpg', TRUE),
(6, 1, 2, 'CHECKED_IN', '2024-11-05 09:00:00', TRUE, '/uploads/evidence/reg3.jpg', TRUE),
(7, 1, 1, 'REGISTERED', '2024-11-09 14:00:00', FALSE, NULL, NULL),
-- Hackathon
(4, 2, 5, 'CHECKED_IN', '2024-11-12 08:00:00', TRUE, '/uploads/evidence/hack1.jpg', TRUE),
(5, 2, 7, 'CHECKED_IN', '2024-11-12 08:30:00', TRUE, '/uploads/evidence/hack2.jpg', TRUE),
(9, 2, 4, 'REGISTERED', '2024-11-13 10:00:00', FALSE, NULL, NULL),
-- Hội thảo AI/ML
(4, 3, 9, 'REGISTERED', '2024-11-25 09:00:00', FALSE, NULL, NULL),
(6, 3, 9, 'REGISTERED', '2024-11-26 10:00:00', FALSE, NULL, NULL),
-- Mùa hè xanh (đã hoàn thành)
(4, 4, 11, 'CHECKED_IN', '2024-06-20 14:00:00', TRUE, '/uploads/evidence/summer1.jpg', TRUE),
(5, 4, 11, 'CHECKED_IN', '2024-06-20 14:30:00', TRUE, '/uploads/evidence/summer2.jpg', TRUE),
(7, 4, 12, 'CHECKED_IN', '2024-06-20 15:00:00', TRUE, '/uploads/evidence/summer3.jpg', TRUE);

-- Điểm rèn luyện tổng hợp
INSERT INTO student_training_points (student_id, semester_id, total_score, classification, status) VALUES
(4, 3, 75, 'Khá', 'DRAFT'),
(5, 3, 82, 'Tốt', 'DRAFT'),
(6, 3, 68, 'Khá', 'DRAFT'),
(7, 3, 45, 'Trung bình', 'DRAFT'),
(8, 3, 55, 'Trung bình', 'DRAFT');

-- Chi tiết điểm rèn luyện
INSERT INTO training_point_details (student_training_point_id, criteria_code, score, source_type, reference_id, description) VALUES
-- Student 4 (Nguyễn Văn An)
(1, '1.1', 15, 'AUTO_GPA', NULL, 'Điểm từ GPA 3.2'),
(1, '3.1', 8, 'AUTO_ACTIVITY', 1, 'Tham gia Ngày hội CNTT + Hackathon'),
(1, '3.1', 10, 'AUTO_ACTIVITY', 4, 'Tình nguyện viên Mùa hè xanh'),
(1, '2.1', 20, 'MANUAL', NULL, 'Chấp hành nội quy tốt'),
-- Student 5 (Trần Thị Bình)
(2, '1.1', 18, 'AUTO_GPA', NULL, 'Điểm từ GPA 3.5'),
(2, '3.1', 8, 'AUTO_ACTIVITY', 1, 'Tham gia Ngày hội CNTT'),
(2, '6.1', 10, 'AUTO_ACTIVITY', 2, 'Giải Nhì Hackathon'),
(2, '3.1', 10, 'AUTO_ACTIVITY', 4, 'Tình nguyện viên Mùa hè xanh'),
(2, '2.1', 20, 'MANUAL', NULL, 'Chấp hành nội quy tốt'),
-- Student 6 (Lê Văn Cường)
(3, '1.1', 12, 'AUTO_GPA', NULL, 'Điểm từ GPA 2.8'),
(3, '3.2', 5, 'AUTO_ACTIVITY', 1, 'Ban tổ chức Ngày hội CNTT'),
(3, '2.1', 20, 'MANUAL', NULL, 'Chấp hành nội quy tốt');

-- Yêu cầu điểm thủ công
INSERT INTO point_requests (student_id, semester_id, criteria_code, claimed_score, description, evidence_image_url, status, reviewer_id, review_comment, reviewed_at) VALUES
(4, 3, '1.3', 5, 'Có chứng chỉ TOEIC 650', '/uploads/evidence/toeic1.jpg', 'APPROVED', 2, 'Đã xác nhận', '2024-10-15 10:00:00'),
(5, 3, '5.1', 3, 'Là Bí thư chi đoàn', '/uploads/evidence/chidoan.jpg', 'APPROVED', 2, 'Đã xác nhận', '2024-10-15 11:00:00'),
(6, 3, '1.3', 5, 'Có chứng chỉ IELTS 6.0', '/uploads/evidence/ielts.jpg', 'PENDING', NULL, NULL, NULL),
(7, 3, '4.1', 5, 'Tham gia hiến máu nhân đạo', '/uploads/evidence/hienmau.jpg', 'PENDING', NULL, NULL, NULL);

-- Yêu cầu tham gia lớp
INSERT INTO class_join_requests (user_id, class_id, status, created_at, processed_at, processed_by) VALUES
(11, 1, 'PENDING', '2024-10-01 08:00:00', NULL, NULL),
(12, 1, 'APPROVED', '2024-09-15 09:00:00', '2024-09-16 10:00:00', 2);
