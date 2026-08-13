# Security and logic follow-up — 2026-08-13

## Phạm vi đã xử lý

- Khóa IDOR ở các API activity, registration, evidence, QR, điểm và hồ sơ sinh viên theo user/lớp đang xác thực.
- Gom check-in vào một service transaction; bắt buộc QR, class, thời gian, GPS, accuracy, bán kính và registration hợp lệ.
- Duyệt evidence/point request bằng pessimistic lock; score ledger idempotent theo `sourceType/referenceId`, không cộng điểm lặp khi retry.
- Registration/cancel chạy trong transaction riêng cho từng optimistic retry; reactivation kiểm tra lại visibility, trạng thái, deadline, slot và capacity.
- Upload dùng thư mục cấu hình ngoài source, UUID thay tên client, kiểm tra chữ ký/khả năng decode, giới hạn 5 MiB và tối đa 3 ảnh minh chứng, đồng thời chặn traversal khi ghi/xóa.
- OTP dùng `SecureRandom`, chỉ lưu BCrypt hash, hết hạn sau 5 phút, khóa sau 5 lần sai, tiêu thụ dưới write lock và thu hồi JWT cũ bằng `tokenVersion` khi reset password.
- Xóa đường DOM XSS đã phát hiện trong toast; nội dung động chỉ được tạo bằng text node/React escaping.
- Chuẩn hóa lỗi validation/security chính và không trả raw exception từ upload hoặc lỗi nội bộ.
- Karate tự khởi động ứng dụng trên random port với H2 và user test được seed trong runner.
- Frontend đã hết lỗi ESLint, route được lazy-load, production build thành công và runtime dependency audit không có advisory.

## Cổng xác minh

Các lệnh bắt buộc trước khi bàn giao:

```bash
cd UniActivity_BE
./mvnw clean test
./mvnw -DskipTests package

cd ../UniActivity_FE
node --test src/utils/*.test.js
npm run lint
npm run build
npm audit --omit=dev
```

Kết quả gần nhất: 117 backend tests (bao gồm 8 scenario Karate) và 9 frontend Node assertions đều pass; package/build thành công; ESLint 0 lỗi; `npm audit --omit=dev` báo 0 vulnerability.

## Rủi ro còn lại

1. **Database rollout:** Flyway đã được bật với baseline version 1 cho database hiện hữu, V2/V3 và `ddl-auto=validate`. Trước lần deploy đầu tiên vẫn phải backup và chạy migration trên bản sao dữ liệu production; duplicate registration/source key sẽ làm unique constraint thất bại có chủ đích để dữ liệu được xử lý trước khi phát hành.
2. **Security architecture:** API đã chặn session fallback trong JWT filter nhưng vẫn nên tách một `SecurityFilterChain` stateless riêng cho toàn bộ API/SSE.
3. **Rate limit phân tán:** OTP có cooldown/giới hạn theo token, nhưng chưa có rate limiter dùng Redis hoặc gateway cho IP/email trên nhiều instance.
4. **Upload WebP:** kiểm tra RIFF/WEBP ở mức container và giới hạn pixel; production nên dùng decoder WebP được duy trì hoặc bỏ WebP khỏi allowlist nếu không cần định dạng này.
5. **Audit/CI:** chưa có audit trail bất biến cho thay đổi điểm và thao tác admin; chưa tự động hóa SAST/dependency scan/migration validation trong CI.
6. **Tích hợp nhánh:** thay đổi nằm trong worktree/nhánh `security/p0-access-checkin-score`; chưa tự động merge vào nhánh chính để tránh đè các thay đổi local hiện có của người dùng.
