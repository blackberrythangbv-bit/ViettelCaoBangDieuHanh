# KPI DNS V1.1.0 - Cài đăng nhập & phân quyền

## 1. Thêm backend auth
Trong project Apps Script `Báo cáo KPI DNS`:
- Tạo file mới `AUTH_V110.gs`.
- Sao chép toàn bộ nội dung file `apps_script/AUTH_V110.gs` trong GitHub vào file mới.

## 2. Đổi router
Trong `Mã.gs`:
- Xóa/đổi tên `function doGet(e)` hiện tại.
- Xóa/đổi tên `function doPost(e)` hiện tại.
- Dán:

```javascript
function doGet(e) {
  return authRouterGetV110_(e);
}

function doPost(e) {
  return authRouterPostV110_(e);
}
```

## 3. Tạo admin đầu tiên
- Chọn hàm `setupAuthV110` trên thanh công cụ Apps Script.
- Bấm **Chạy**.
- Chấp nhận quyền nếu Google yêu cầu.
- Mở **Nhật ký thực thi**.
- Lấy:
  - User: `admin`
  - `MẬT KHẨU TẠM: ...`
- Hàm sẽ tự tạo 2 sheet: `USERS` và `AUDIT_LOG`.

## 4. Triển khai lại Web App
- **Triển khai -> Quản lý các tùy chọn triển khai**.
- Chọn deployment đang dùng bởi app.
- Bút chì -> **Phiên bản mới** -> **Triển khai**.
- Giữ nguyên URL `/exec` hiện tại.

## 5. Cài APK V1.1.0
- Mở app.
- Đăng nhập `admin` bằng mật khẩu tạm.
- App bắt buộc đổi mật khẩu lần đầu.
- Mở menu tài khoản -> **Quản trị người dùng**.

## Quyền quản trị
Admin có thể:
- Thêm user mới.
- Sửa tên hiển thị.
- Chọn phạm vi `ALL` hoặc 1 trong 6 AM.
- Bật/tắt quyền nhập/sửa KPI.
- Bật/tắt quyền quản trị user.
- Khóa/mở tài khoản.
- Reset mật khẩu.
- Xóa user.

Tài khoản owner đầu tiên không thể bị xóa, khóa hoặc mất quyền quản trị.

## Phân quyền backend
Việc giới hạn AM và quyền nhập/sửa được kiểm tra ở backend, không chỉ ẩn trên giao diện app.
