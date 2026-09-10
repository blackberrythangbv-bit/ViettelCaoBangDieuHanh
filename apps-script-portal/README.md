# Tích hợp Portal vào Apps Script hiện tại

Mục tiêu: giữ nguyên deployment `/exec` hiện tại, mặc định mở Portal có 3 tab **KPI | Trường học | Doanh nghiệp**.

## 1. Giữ nguyên HTML KPI hiện tại

Trong Apps Script project, xác định file HTML đang hiển thị KPI hiện nay.

- Nếu file tên `Index.html`: không cần đổi gì trong `Code.gs`.
- Nếu file có tên khác, ví dụ `Dashboard.html`, sửa dòng:

```javascript
const KPI_FILE = 'Index';
```

thành:

```javascript
const KPI_FILE = 'Dashboard';
```

Không xóa hay thay nội dung file KPI hiện tại.

## 2. Tạo file Portal.html

Trong Apps Script: **+ > HTML > đặt tên `Portal`**.

Dán toàn bộ nội dung file `Portal.html` trong thư mục này vào.

## 3. Sửa Code.gs

Không xóa các hàm API/đọc Google Sheet đang có. Chỉ thay hàm `doGet(e)` hiện tại bằng router trong file `Code.gs` này.

Nếu project hiện có nhiều hàm khác, giữ nguyên toàn bộ các hàm đó.

Điểm quan trọng: project chỉ được có **một hàm `doGet(e)`**.

## 4. Deploy lại đúng deployment hiện tại

Apps Script > **Deploy > Manage deployments > Edit** deployment Web App đang dùng > chọn **New version > Deploy**.

Không tạo deployment mới nếu muốn giữ nguyên URL `/exec` đang dùng.

## 5. Cơ chế tải đã tối ưu

- Mở app: chỉ nạp KPI.
- Trường học: chỉ nạp lần đầu khi bấm tab.
- Doanh nghiệp: chỉ nạp lần đầu khi bấm tab.
- Chuyển tab sau lần đầu: không tải lại iframe.
- Chỉ nút `TẢI LẠI` mới ép tải mới module đang mở.

## 6. URL module đang cấu hình

- Trường học: `https://blackberrythangbv-bit.github.io/ViettelCaoBangDieuHanh/`
- Doanh nghiệp: `https://script.google.com/macros/s/AKfycbzxSHqdzpKUSpcf2IhO_0oozRxDx_zZVtLCi7bMzQJiAIOnux7JQFI2JvP188g4RzmD/exec`
- KPI: nội bộ cùng project qua `?view=kpi`

## Kiểm tra sau deploy

1. Mở URL `/exec` không tham số -> thấy 3 tab.
2. KPI hiển thị ngay.
3. Bấm Trường học -> dữ liệu mới bắt đầu nạp.
4. Bấm Doanh nghiệp -> dữ liệu mới bắt đầu nạp.
5. Chuyển qua lại -> không tải lại module.
