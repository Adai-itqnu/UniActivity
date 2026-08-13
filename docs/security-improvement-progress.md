# Tiến độ cải thiện an toàn và ổn định

Ngày cập nhật: 2026-08-13

Quy ước:

- `[x]` Đã triển khai và có test tự động xác minh.
- `[~]` Đã giảm rủi ro nhưng còn bước thay thế hoàn chỉnh.
- `[ ]` Chưa triển khai.

## P0 — Rủi ro nghiêm trọng

### P0-A — Nền JWT và đăng ký tài khoản

- [x] Xóa JWT secret mặc định khỏi source; ứng dụng fail-fast nếu secret thiếu, rỗng hoặc ngắn hơn 32 byte UTF-8.
- [x] Phân biệt rõ access token (`type=access`) và refresh token (`type=refresh`).
- [x] Gắn `tokenVersion` vào access/refresh token và lưu version trong bảng `users`.
- [x] JWT filter chỉ xác thực access token của tài khoản `ACTIVE` có version trùng database.
- [x] Refresh token bị từ chối khi tài khoản bị khóa hoặc token đã bị thu hồi.
- [x] Logout tăng `tokenVersion` bằng một câu lệnh update nguyên tử, vô hiệu hóa toàn bộ access/refresh token cũ.
- [x] Public registration luôn tạo `STUDENT`; không còn cơ chế “tài khoản đầu tiên là ADMIN”.
- [x] Các điểm phát token của local login và Google OAuth đã dùng token version hiện tại.
- [x] JWT filter chỉ đọc access token từ header; không còn đọc bearer credential từ query string.
- [x] Đã cấu hình `JWT_SECRET` và `QR_SECRET` local riêng, khác nhau trong `UniActivity_BE/.env`; file vẫn bị Git bỏ qua.

Test P0-A hiện có: `JwtTokenProviderTest`, `UserServiceTest`, `JwtAuthenticationFilterTest`, `JwtAuthControllerTest`.

### P0-B — Token URL và bí mật QR

- [x] Google OAuth redirect chỉ chứa exchange code 256-bit ngẫu nhiên; database chỉ lưu SHA-256 hash.
- [x] Exchange code hết hạn sau 60 giây, bị khóa pessimistic và chỉ tiêu thụ một lần.
- [x] `POST /api/auth/oauth2/exchange` trả access/refresh JWT sau khi tiêu thụ code hợp lệ.
- [x] Google account linking bắt buộc claim `email_verified=true` và giữ nguyên role/status hiện có.
- [x] SSE dùng purpose-bound `type=sse` ticket tối đa 60 giây; ticket không xác thực được API thường.
- [x] Frontend lấy ticket qua authenticated POST và chỉ đưa ticket ngắn hạn vào URL EventSource.
- [x] `QR_SECRET` tách khỏi `JWT_SECRET`, bắt buộc tối thiểu 32 byte, không có fallback source-code và ứng dụng fail-fast nếu hai secret trùng nhau.
- [x] QR HMAC được so sánh constant-time và token từ secret khác bị từ chối.
- [x] Session form/OAuth không thể xác thực các route API/SSE; các route này luôn phải qua access JWT hoặc purpose-bound SSE ticket.
- [x] OAuth exchange single-flight chống gọi lặp trong React StrictMode nhưng giải phóng cache sau khi request hoàn tất để có thể retry lỗi tạm thời.

Xác minh gần nhất: 117 backend tests (bao gồm 8 scenario Karate), 9 frontend Node assertions, backend/Karate chạy bằng H2 cô lập, frontend production build, ESLint và `npm audit` đều đạt.

### P0-C — Phân quyền, check-in và toàn vẹn điểm

- [x] Đã chặn IDOR theo lớp cho activity, registration, evidence, user score và profile điểm; manager chỉ thao tác tài nguyên thuộc lớp mình quản lý.
- [x] Check-in bắt buộc QR hợp lệ, đúng activity/class, đúng cửa sổ thời gian, GPS hữu hạn/đúng miền, độ chính xác cho phép và registration hợp lệ.
- [x] Duyệt/từ chối minh chứng và yêu cầu điểm có pessimistic lock, idempotent theo nguồn/reference; retry hoặc chạy đồng thời không cộng điểm lặp.
- [x] Đăng ký/hủy hoạt động chạy trong transaction riêng cho từng lần optimistic retry và có unique `(student_id, activity_id)` chống đăng ký trùng.
- [x] GPA và điểm tự đề xuất không còn ghi thẳng vào sổ điểm; phải tạo yêu cầu chờ manager duyệt và bị giới hạn theo tiêu chí.
- [x] Sổ điểm giữ từng contribution theo source/reference, chống ghi trùng và cộng đúng nhiều hoạt động cùng tiêu chí.
- [x] Loại bỏ đường XSS đã phát hiện trong toast: không còn đưa nội dung động vào `innerHTML`; rule React được render dưới dạng text node.
- [x] OTP/reset password dùng `SecureRandom`, BCrypt hash, hết hạn 5 phút, khóa sau 5 lần sai, pessimistic lock và tăng `tokenVersion` sau đổi mật khẩu.
- [x] Upload tập trung qua một storage boundary: kiểm tra bytes ảnh, tên UUID, tối đa 5 MiB/tệp và 3 ảnh minh chứng, chặn path traversal khi ghi/xóa.
- [~] API đã chặn fallback session tại JWT filter; vẫn cần tách `SecurityFilterChain` stateless riêng để loại bỏ hoàn toàn session machinery khỏi API.

## P1 — Độ ổn định và khả năng kiểm thử

- [x] Test context dùng JWT/QR secret giả và H2 in-memory độc lập, không đọc `.env` hoặc MySQL thật.
- [x] Karate khởi động Spring trên random port, seed user test trực tiếp và lấy JWT động; không còn phụ thuộc localhost/tài khoản demo.
- [x] Chuẩn hóa các exception chính trong `GlobalExceptionHandler`; lỗi upload/nội bộ không trả raw exception ra client.
- [ ] Bổ sung audit log cho thay đổi điểm, duyệt minh chứng, khóa user và thao tác admin.
- [x] Bổ sung pessimistic lock cho duyệt evidence/point request/OTP và transaction riêng cho registration retry.
- [x] Bật Flyway baseline cho database hiện hữu, thêm V2/V3 có version và chuyển production sang `ddl-auto=validate`.
- [ ] Đưa CORS/frontend URL và cookie policy sang cấu hình theo môi trường.
- [x] Bổ sung test hồi quy cho phân quyền IDOR, check-in, điểm, OTP, upload và Karate API.

## P2 — Chất lượng và vận hành lâu dài

- [ ] Chuẩn hóa service boundary; controller không trực tiếp ôm business logic phức tạp.
- [ ] Dùng DTO response thay vì trả entity/map tùy ý.
- [ ] Thêm pagination, index và giới hạn truy vấn ở danh sách lớn.
- [ ] Metrics/health/log correlation và cảnh báo lỗi xác thực hoặc thao tác điểm bất thường.
- [~] Frontend runtime dependencies hiện `npm audit` = 0 vulnerability, ESLint = 0 lỗi và các route đã lazy-load; chưa tự động hóa dependency/security scanning trong CI và lịch xoay secret.
- [ ] Tài liệu hóa state machine cho activity, registration, evidence và score ledger.
- [ ] E2E test các luồng người dùng chính trên frontend.
