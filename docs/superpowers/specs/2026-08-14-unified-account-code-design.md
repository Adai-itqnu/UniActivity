# Thiết kế mã tài khoản thống nhất cho Google OAuth và tài khoản nội bộ

Ngày: 2026-08-14

## Mục tiêu

Mọi tài khoản `STUDENT` và `MANAGER` phải có `username` là một mã tài khoản ngẫu nhiên gồm đúng 8 chữ số, bất kể tài khoản được tạo qua đăng ký công khai, Google OAuth hay trang quản trị. Tài khoản `ADMIN` giữ cơ chế username riêng hiện tại.

Người dùng đăng nhập bằng email hoặc mã tài khoản. Google OAuth tiếp tục liên kết danh tính bằng email Google đã xác thực.

## Quy tắc định danh

- `STUDENT` và `MANAGER`: `username` phải khớp `^[0-9]{8}$`.
- `ADMIN`: username không bị ép sang 8 chữ số và được giữ nguyên khi migration.
- Mã được sinh trong khoảng `10000000`–`99999999` bằng `SecureRandom`.
- `users.username` tiếp tục có unique constraint và là lớp bảo vệ cuối chống trùng mã.
- Email tiếp tục là duy nhất và là khóa liên kết tài khoản nội bộ với Google.
- Username không phải email và không được lấy từ Google `sub`.

## Kiến trúc

Tạo một component dùng chung chịu trách nhiệm sinh và kiểm tra mã tài khoản. Mọi luồng tạo `STUDENT` hoặc `MANAGER` phải gọi component này thay vì tự tạo username:

1. `UserService` cho đăng ký công khai.
2. `CustomOAuth2UserService` cho Google OAuth mới.
3. `UserManagementService` cho admin tạo tài khoản.
4. `UserManagementService` khi đổi role từ `ADMIN` sang `STUDENT` hoặc `MANAGER`.

Component thử tối đa 10 mã khác nhau. Nếu không tìm được mã khả dụng, thao tác phải thất bại nguyên tử và trả lỗi chung; không lưu user ở trạng thái thiếu hoặc sai mã.

`username` hiện tại được giữ để tránh thay đổi lớn tới JWT, DTO, repository và các quan hệ đang dùng mã sinh viên. Trên giao diện, tên hiển thị của trường này đổi thành “Mã tài khoản”.

## Luồng tạo và cập nhật tài khoản

### Đăng ký công khai

- Client không cần gửi username.
- Backend luôn tạo role `STUDENT` và cấp mã 8 số.
- Response đăng ký trả mã mới để người dùng lưu lại.

### Google OAuth

- Chỉ chấp nhận Google email có `email_verified=true`.
- Nếu email đã tồn tại, liên kết Google vào đúng user đó và giữ role/status hiện tại.
- Nếu user hiện có là `ADMIN`, giữ nguyên username.
- Nếu user hiện có là `STUDENT` hoặc `MANAGER` nhưng username sai định dạng, cấp mã 8 số như cơ chế tự phục hồi; migration vẫn là đường chuyển đổi chính.
- Nếu email chưa tồn tại, tạo `STUDENT` mới với mã 8 số, không dùng `google_<sub>`.

### Quản trị người dùng

- Tạo `ADMIN`: admin nhập username riêng và backend kiểm tra duy nhất.
- Tạo `STUDENT`/`MANAGER`: backend bỏ qua username từ request và tự cấp mã 8 số.
- Cập nhật `STUDENT`/`MANAGER`: không cho sửa mã qua DTO thông thường.
- Đổi `ADMIN` sang `STUDENT`/`MANAGER`: nếu username chưa đúng 8 số, tự cấp mã mới.
- Đổi sang `ADMIN`: giữ username hiện tại; thao tác đổi role không tự thay username.

## Đăng nhập và giao diện

- API login giữ field request `username` để tương thích ngược nhưng chấp nhận cả email lẫn mã tài khoản như hiện tại.
- Form login đổi nhãn/placeholder thành “Email hoặc mã tài khoản”.
- Hồ sơ, danh sách thành viên và quản trị đổi nhãn “Username/Tên đăng nhập/Mã sinh viên” thành “Mã tài khoản” khi hiển thị trường `username`.
- Sau Google OAuth, response user phải chứa mã 8 số vừa cấp giống tài khoản đăng ký thường.
- Username cũ của tài khoản bị migration sẽ không đăng nhập được; email và mã mới vẫn dùng được.

## Migration dữ liệu

Thêm Java Flyway migration V4 để xử lý dữ liệu hiện hữu bằng JDBC và `SecureRandom`:

1. Khóa/đọc danh sách user thuộc role `STUDENT` hoặc `MANAGER`.
2. Giữ nguyên username đã đúng 8 chữ số.
3. Cấp mã duy nhất cho username dạng `google_...`, username chữ hoặc định dạng khác.
4. Tăng `token_version` cho từng user bị đổi để thu hồi token chứa định danh cũ.
5. Không thay đổi username của `ADMIN`.
6. V5 kiểm tra MySQL 8.0.16+, xác nhận V4 đã chuẩn hóa toàn bộ dữ liệu rồi mới thêm database check constraint để `STUDENT`/`MANAGER` phải có username 8 chữ số, trong khi `ADMIN` được phép dùng username riêng.

V4 và V5 được tách riêng vì MySQL implicit commit khi chạy DDL. Nhờ đó bước chuẩn hóa dữ liệu không bị trộn với `ALTER TABLE`, và V5 có thể dừng trước DDL nếu server chưa thực thi `CHECK` constraint.

Migration phải được chạy thử trên bản sao database production và có backup trước lần deploy đầu tiên. Nếu không thể cấp mã hoặc vi phạm unique/check constraint, migration phải thất bại thay vì bỏ qua dữ liệu. Quy trình triển khai và phục hồi nằm trong `docs/account-code-migration-runbook.md`.

## Xử lý lỗi và cạnh tranh

- Generator kiểm tra mã đã tồn tại trước khi trả về.
- Unique constraint xử lý trường hợp hai request đồng thời chọn cùng mã.
- Lỗi collision tại database không được làm lộ thông tin nội bộ; request thất bại với thông báo có thể thử lại.
- Email trùng vẫn liên kết Google vào user hiện có, không tạo user thứ hai.
- Việc đổi role và đổi mã nằm trong cùng transaction.

## Kiểm thử

Backend cần có test cho:

- Generator chỉ trả đúng 8 chữ số và thử lại khi gặp mã trùng.
- Đăng ký công khai luôn cấp mã hợp lệ.
- Google user mới nhận mã 8 số thay vì `google_<sub>`.
- Google liên kết user `ADMIN` không đổi username/role/status.
- Google liên kết `STUDENT`/`MANAGER` legacy tự sửa mã sai định dạng.
- Admin tạo `STUDENT`/`MANAGER` không thể ép username tùy ý.
- Đổi role `ADMIN` sang role khác tự cấp mã khi cần.
- Login thành công bằng email và mã 8 số.
- Migration giữ admin, giữ mã hợp lệ, đổi mã legacy, không trùng và tăng `tokenVersion` đúng đối tượng.

Frontend cần giữ lint/build hiện có và có test hoặc kiểm tra component cho nhãn “Email hoặc mã tài khoản” cùng việc hiển thị mã trả về sau đăng ký.

## Ngoài phạm vi

- Không đổi primary key `users.id`.
- Không dùng mã trường hoặc định dạng mã sinh viên theo quy tắc nghiệp vụ khác ngoài 8 chữ số ngẫu nhiên.
- Không cho phép người dùng tự chọn hoặc tự sửa mã tài khoản.
- Không thay đổi cơ chế xác thực Google, JWT hoặc refresh token ngoài việc thu hồi token khi migration đổi username.
