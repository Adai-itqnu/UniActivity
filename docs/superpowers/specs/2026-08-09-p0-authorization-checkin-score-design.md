# P0 Authorization, Check-in, and Score Integrity Design

## Mục tiêu

Loại bỏ các đường khai thác nghiêm trọng cho phép manager thao tác ngoài lớp được phân công, sinh viên check-in không có QR hợp lệ, dùng lựa chọn điểm của hoạt động khác hoặc tự cấp điểm từ GPA do client khai báo.

Đợt này giữ nguyên các URL chính và định dạng response đang được frontend sử dụng. Mã kiểm tra nghiệp vụ được chuyển khỏi controller sang service tập trung để cùng một quy tắc được áp dụng cho cả controller chuyên biệt và các endpoint trùng trong ManagerDataApiController.

## Phạm vi

### Phân quyền manager

Mọi thao tác sau phải xác nhận manager hiện tại quản lý đúng lớp của sinh viên/registration:

- Xem danh sách đăng ký của hoạt động.
- Check-in thủ công.
- Duyệt hoặc từ chối minh chứng.
- Đọc hồ sơ và điểm của sinh viên.
- Tạo QR cho hoạt động có phạm vi liên quan tới lớp manager.

Quyền được xác định từ quan hệ User.studentClass của manager và student. Không tin classId, studentId, registrationId hoặc activityId do client gửi.

### Check-in

Check-in sinh viên chỉ thành công khi đồng thời thỏa mãn:

- Sinh viên có registration trạng thái REGISTERED.
- Activity đang ở trạng thái OPEN.
- Thời điểm hiện tại nằm trong cửa sổ từ startTime đến endTime.
- Request có đủ classId và QR token.
- classId trùng lớp hiện tại của sinh viên và token hợp lệ cho đúng activity/class.
- Nếu activity yêu cầu vị trí, latitude, longitude và accuracy đều là số hữu hạn; tọa độ nằm trong miền hợp lệ; accuracy không vượt ngưỡng và khoảng cách không vượt bán kính.

Controller chuyên biệt và endpoint tương đương trong ManagerDataApiController cùng gọi một service; không duy trì hai bản logic độc lập.

### Minh chứng và điểm

- scoreOptionId chỉ hợp lệ khi option thuộc đúng activity của registration.
- Chỉ registration có evidenceUrl không rỗng và isApproved bằng null mới được approve/reject.
- Approve/reject chạy trong một transaction và khóa registration trước khi chuyển trạng thái.
- Approve lặp hoặc request đồng thời không được cộng điểm lần hai.
- Reject sau approve và approve sau reject bị từ chối; không âm thầm đảo trạng thái.
- Điểm activity được ghi theo một reference ổn định để cùng một registration không tạo nhiều ledger entry.
- Endpoint GPA không còn ghi điểm trực tiếp từ số liệu client. Request hợp lệ được chuyển thành point request chờ manager duyệt; GPA phải hữu hạn và nằm trong khoảng 0–10.
- claimedScore trong point request phải hữu hạn, không âm và không vượt điểm tối đa của tiêu chí.

## Kiến trúc

### ManagerScopeAuthorizationService

Service duy nhất cung cấp các hàm kiểm tra:

- Manager có lớp được phân công.
- Student thuộc lớp manager.
- Registration thuộc sinh viên trong lớp manager.
- Activity có registration/slot áp dụng cho lớp manager trước khi manager xem hoặc tạo QR.

Service trả entity đã được kiểm tra để controller không tải lại bằng ID và vô tình bỏ qua authorization.

### StudentCheckinService

Service nhận authenticated student, activity ID, class ID, QR token và dữ liệu vị trí. Nó thực hiện toàn bộ validation theo thứ tự, sau đó mới cập nhật registration. Service có transaction boundary duy nhất.

Các giá trị GPS dùng Double.isFinite trước mọi phép tính. GeoUtils cũng từ chối đầu vào không hữu hạn để tạo lớp phòng vệ thứ hai.

### EvidenceReviewService

Service dùng repository query có pessimistic write lock để tải registration. State transition và cập nhật điểm nằm trong cùng transaction. Điểm được cập nhật theo registration ID để bảo đảm idempotency.

### AcademicPointRequestService

Luồng GPA tạo point request thay vì ghi TrainingPointDetail trực tiếp. Service chuẩn hóa và giới hạn GPA/score theo scoring rules trước khi lưu.

## Dữ liệu và ràng buộc

Đợt này bổ sung repository query và entity constraint cần thiết nhưng chưa đưa Flyway vào dự án:

- Unique registration theo (activity_id, student_id).
- Unique training-point detail theo (student_training_point_id, criteria_code, source_type, reference_id). Mọi điểm tự động từ activity hoặc point request phải có source_type và reference_id không null; dữ liệu manual cũ không thuộc ràng buộc idempotency của đợt này.
- Pessimistic lock query cho registration khi review minh chứng.

Schema SQL tài liệu được cập nhật cùng entity mapping. Việc chuyển toàn bộ database sang Flyway là một đợt riêng vì cần chiến lược baseline cho database đang tồn tại.

## API và lỗi

- Thiếu hoặc sai QR: HTTP 400.
- Không thuộc phạm vi manager hoặc cố đọc tài nguyên lớp khác: HTTP 403.
- Không tìm thấy tài nguyên trong phạm vi hợp lệ: HTTP 404.
- State transition lặp hoặc xung đột: HTTP 409.
- GPS/GPA/score không hữu hạn hoặc ngoài miền: HTTP 400.

Response giữ trường message hiện tại để frontend không vỡ. Nội dung lỗi không chứa exception hoặc thông tin SQL.

## Kiểm thử

TDD được áp dụng theo từng lỗ hổng:

1. Test manager không thể đọc/check-in/review registration lớp khác.
2. Test student check-in thiếu QR, sai class, ngoài thời gian, QR sai và GPS NaN đều bị từ chối.
3. Test score option của activity khác bị từ chối.
4. Test approve hai lần và approve đồng thời chỉ tạo một lần cộng điểm.
5. Test GPA không tạo điểm trực tiếp và input ngoài 0–10 bị từ chối.
6. Test point request không chấp nhận điểm âm, vô hạn hoặc vượt trần.
7. Chạy toàn bộ unit/security suite; integration test cần database test độc lập sẽ được ghi nhận nếu MySQL local chưa chạy.

## Triển khai Git

- Tạo worktree sạch từ main để loại bỏ 170 file diff giả do line-ending trong workspace hiện tại.
- Dùng feature branch security/p0-access-checkin-score.
- Mỗi nhóm hành vi có commit riêng sau khi test đỏ rồi xanh.
- Không sao chép .env sang worktree và không commit secret.
- Chỉ push feature branch sau khi focused tests đạt; không merge vào main nếu full suite còn lỗi không được giải thích.

## Ngoài phạm vi đợt này

- OTP hashing/rate limit và thu hồi token sau đổi mật khẩu.
- Upload validation và phục vụ file qua authenticated endpoint.
- Tách stateless SecurityFilterChain.
- Flyway baseline/migration đầy đủ.
- Sửa toàn bộ lint frontend và dependency audit.
- Logging/audit trail toàn hệ thống.

Các mục này được triển khai trong các đợt P0/P1 tiếp theo sau khi đợt authorization/check-in/score ổn định.
