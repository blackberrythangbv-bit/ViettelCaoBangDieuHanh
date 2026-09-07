# KPI DNS Cao Bằng - PWA v1.0.1 Phương án B

Mục tiêu: cài trên iPhone như app mà không cần Xcode, Apple Developer hay TestFlight.

## Thành phần
- `PWA_V101.gs`: backend PWA, 1 bootstrap lấy 6 AM + chỉ tiêu; ghi KPI giữ đúng blank != 0.
- `PWA_V101.html`: giao diện iPhone, cache-first bằng localStorage, tự đồng bộ nền.

## Cài vào Google Apps Script
1. Trong project `Báo cáo KPI DNS`, tạo **Tập lệnh** mới tên `PWA_V101` và dán toàn bộ nội dung `PWA_V101.gs`.
2. Tạo **HTML** mới tên `PWA_V101` và dán toàn bộ nội dung `PWA_V101.html`.
3. Trong `function doGet(e)` hiện tại, thêm ngay dòng đầu bên trong hàm:

```javascript
if (e && e.parameter && String(e.parameter.pwa || '') === '1') {
  return servePwaV101_();
}
```

Ví dụ:

```javascript
function doGet(e) {
  if (e && e.parameter && String(e.parameter.pwa || '') === '1') {
    return servePwaV101_();
  }

  // các route hiện tại giữ nguyên ở dưới
  ...
}
```

4. Lưu project.
5. `Triển khai` -> `Quản lý các tùy chọn triển khai` -> bút chì -> `Phiên bản mới` -> `Triển khai`.
6. Giữ nguyên URL `/exec` hiện tại.

## URL PWA
Dùng Web App URL hiện tại và nối:

```text
?pwa=1
```

Ví dụ endpoint hiện tại:

```text
https://script.google.com/macros/s/AKfycbxwDT_LV1D49SKfZkv0_CfmBcRcpubbJGnd9TFBL5b1y0AHQ-a1zbRQf83CBWDeRkaApQ/exec?pwa=1
```

## Cài trên iPhone
1. Mở URL PWA bằng Safari.
2. Chạm nút **Chia sẻ**.
3. Chọn **Thêm vào Màn hình chính**.
4. Chọn **Thêm**.
5. Từ lần sau mở icon `KPI DNS Cao Bằng` như một app.

## Phương án B
- Lần mở đầu: lấy dữ liệu thật từ Apps Script.
- Những lần sau: hiển thị cache ngay từ `localStorage`, đồng thời gọi 1 `pwaBootstrapV101()` ở nền để cập nhật dữ liệu.
- Khi mất mạng: vẫn xem được dữ liệu cache gần nhất; nhập/lưu KPI cần có mạng.

## Lưu ý bảo mật
PWA v1.0.1 này là bản test tương đương giai đoạn trước login/phân quyền. Khi nghiệm thu giao diện/tốc độ, nên ghép PWA với AUTH v1.1.0 để bắt buộc user/password và phân quyền theo AM.
