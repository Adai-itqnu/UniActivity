# Security follow-up — 2026-08-09

## Đã xử lý trong nhánh này

- Chặn manager đọc/sửa activity, registration, evidence và điểm của lớp khác; student chỉ đọc profile điểm của chính mình.
- Gom hai endpoint check-in về một service transaction dùng chung; bắt buộc QR/class/time window/GPS/accuracy/radius/registration hợp lệ.
- Khóa pessimistic khi duyệt evidence và point request; mọi contribution điểm có source/reference duy nhất để retry không cộng lặp hoặc ghi đè nguồn khác.
- GPA và điểm tự khai chuyển sang trạng thái `PENDING`, bắt buộc manager duyệt trước khi vào sổ điểm.
- Thêm unique constraint cho registration và score ledger; sửa phép tổng hợp nhiều activity cùng tiêu chí.
- Sửa registration/cancellation thực sự chạy trong transaction. Mỗi optimistic-lock retry dùng transaction mới thay vì gọi hàm `private @Transactional` không có hiệu lực.
- Nâng frontend lockfile để loại bỏ 5 lỗ hổng runtime được `npm audit` phát hiện.
- Test application context dùng H2 in-memory, không phụ thuộc `.env` hoặc MySQL local.

## Kết quả xác minh

- Backend: `73` tests, `0` failures, `0` errors.
- Frontend utilities: `7` tests, `7` pass.
- Frontend production build: thành công.
- Frontend dependency audit: `0 vulnerabilities`.
- ESLint: còn `36 errors`, `15 warnings` trong code cũ; phần lớn là hook dependency/set-state-in-effect, biến thừa và empty block.
- Build warning: JS bundle chính khoảng `1.47 MB` trước gzip; cần code splitting ở batch hiệu năng.

## Rủi ro còn tồn đọng, theo ưu tiên

1. **P0 — OTP/password reset:** OTP đang dùng `Random`, lưu plaintext, không giới hạn số lần đoán và đổi mật khẩu chưa tăng `tokenVersion` để thu hồi session/JWT cũ.
2. **P0 — Upload:** một số endpoint chỉ tin `Content-Type`, giữ extension từ tên client và ghi file trực tiếp; cần magic-byte allowlist, giới hạn kích thước/số lượng, storage path cố định và kiểm tra quyền download.
3. **P0/P1 — XSS:** rà mọi chỗ render HTML/nội dung từ người dùng, đặc biệt các đường dùng `dangerouslySetInnerHTML` hoặc URL upload.
4. **P1 — Logout frontend:** logout hiện chỉ xóa `sessionStorage`; cần gọi `POST /api/auth/logout-jwt` trước khi xóa local token để thu hồi token server-side.
5. **P1 — API architecture:** tách filter chain stateless cho API, chuẩn hóa exception thay vì controller bắt `Exception`, và thêm integration test IDOR/upload.
6. **P1 — Database deployment:** thay `ddl-auto=update` bằng Flyway/Liquibase để unique constraints được rollout có kiểm soát trên dữ liệu hiện hữu.
7. **P2 — Frontend quality/performance:** xử lý 36 lỗi lint, 15 warning và chia nhỏ bundle lớn.
