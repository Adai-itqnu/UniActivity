# Account-code migration runbook

## Điều kiện trước khi triển khai

- MySQL phải từ 8.0.16 trở lên để `CHECK` constraint được thực thi.
- Tạo backup có thể phục hồi và diễn tập V4/V5 trên một database được restore từ production.
- Tạm dừng các luồng tạo/cập nhật user trong thời gian migration.
- Kiểm tra trước bằng `SELECT VERSION();` và xác nhận không có username trùng.

## Trình tự

1. Triển khai ứng dụng và để Flyway chạy V4 trước: chỉ chuẩn hóa username của `STUDENT`/`MANAGER`, giữ nguyên `ADMIN`, tăng `token_version` cho user bị đổi.
2. V5 kiểm tra phiên bản database và dữ liệu đã chuẩn hóa trước khi chạy DDL.
3. V5 thêm `chk_users_non_admin_account_code` rồi kiểm tra constraint tồn tại và có trạng thái `ENFORCED = YES`.
4. Chỉ mở traffic sau khi `flyway_schema_history` ghi nhận cả V4 và V5 thành công.

Kiểm tra sau migration:

```sql
SELECT id, username, role
FROM users
WHERE role IN ('STUDENT', 'MANAGER')
  AND username NOT REGEXP '^[0-9]{8}$';

SELECT tc.constraint_name, tc.constraint_type, tc.enforced, cc.check_clause
FROM information_schema.table_constraints tc
JOIN information_schema.check_constraints cc
  ON cc.constraint_schema = tc.constraint_schema
 AND cc.constraint_name = tc.constraint_name
WHERE tc.constraint_schema = DATABASE()
  AND tc.table_name = 'users'
  AND tc.constraint_name = 'chk_users_non_admin_account_code'
  AND tc.constraint_type = 'CHECK';
```

Query thứ nhất phải trả 0 dòng; query thứ hai phải trả đúng một `CHECK`, có `enforced = YES`, và `check_clause` phải tương đương `role = 'ADMIN' OR username REGEXP '^[0-9]{8}$'`.

## Phục hồi khi migration lỗi

- Nếu V4 lỗi: giữ ứng dụng dừng, phục hồi backup hoặc sửa nguyên nhân cấp mã/unique trên bản restore rồi chạy lại. Không bỏ qua user lỗi.
- Nếu V5 dừng trước `ALTER TABLE`: nâng MySQL lên 8.0.16+ hoặc sửa dữ liệu còn sai, sau đó chạy lại.
- Nếu MySQL đã tạo constraint nhưng Flyway ghi V5 thất bại: xác minh constraint đúng và `ENFORCED = YES`, dùng quy trình `flyway repair` đã diễn tập để xóa bản ghi failed rồi chạy lại V5. V5 sẽ nhận ra constraint hiện hữu và không thêm trùng.
- Không sửa trực tiếp `flyway_schema_history` và không mở traffic khi V4/V5 chưa được xác nhận thành công.
