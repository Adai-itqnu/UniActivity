# Local Environment Secrets Design

## Mục tiêu

Cấu hình local cho UniActivity Backend có hai secret độc lập, đủ mạnh cho JWT và mã QR động, trong khi giữ nguyên toàn bộ thông tin kết nối database, Google OAuth và SMTP hiện có.

## Phạm vi

- Thêm `JWT_SECRET` và `QR_SECRET` vào `UniActivity_BE/.env`.
- Mỗi secret gồm 32 byte ngẫu nhiên mật mã, biểu diễn bằng 64 ký tự hexadecimal.
- Hai secret phải khác nhau và không được suy ra từ nhau.
- Không thay đổi các biến `DB_*`, `GOOGLE_*` hoặc `MAIL_*` hiện có.
- Không in giá trị secret trong log, báo cáo hoặc tài liệu.
- Xác nhận `UniActivity_BE/.env` tiếp tục bị Git bỏ qua.

## Thiết kế

Ứng dụng tiếp tục nạp `UniActivity_BE/.env` qua `spring.config.import`. Hai giá trị mới đáp ứng điều kiện fail-fast hiện có: tối thiểu 32 byte UTF-8 và `JWT_SECRET` khác `QR_SECRET`.

Việc sinh secret dùng bộ sinh số ngẫu nhiên mật mã của hệ điều hành. Giá trị chỉ được ghi vào file local đã bị Git bỏ qua. `.env.example` giữ placeholder để mô tả cấu hình và không chứa secret thật.

## Luồng xác minh

1. Kiểm tra `.env` bị Git ignore và không được track.
2. Kiểm tra hai biến tồn tại, không rỗng, dài ít nhất 32 byte và khác nhau mà không hiển thị giá trị.
3. Chạy các unit test kiểm tra JWT/QR secret separation.
4. Chạy Spring Boot test suite nếu môi trường cho phép.
5. Kiểm tra Git diff để đảm bảo không có secret nào lọt vào file được theo dõi.

## Xử lý lỗi

- Nếu không sinh được nguồn ngẫu nhiên mật mã, dừng và không sửa `.env`.
- Nếu một trong hai secret không đạt độ dài hoặc bị trùng, sinh lại trước khi xác minh.
- Nếu test thất bại do cấu hình secret, không báo hoàn tất cho tới khi tìm được nguyên nhân.
- Nếu test bị chặn bởi dependency hoặc môi trường, báo rõ lệnh và lỗi thay vì suy đoán.

## Production

Thiết kế này dành cho phát triển local. Khi triển khai production, `JWT_SECRET` và `QR_SECRET` phải được cấp qua secret manager hoặc biến môi trường của nền tảng, không sao chép file `.env` local lên máy chủ.

## Ngoài phạm vi

- Không xoay hoặc thay credential database, Google OAuth hay SMTP.
- Không sửa các lỗi phân quyền, check-in, điểm, OTP, upload hoặc transaction trong đợt cấu hình này.
- Không thay đổi thời hạn access token hoặc refresh token.
