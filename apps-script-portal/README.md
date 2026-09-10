# Tích hợp Portal đầy đủ vào Apps Script hiện tại

Mục tiêu: giữ nguyên deployment `/exec` hiện tại và hiển thị trực tiếp toàn bộ thanh chức năng gồm **KPI | Báo cáo ngày | KH tuần | Trường học | Doanh nghiệp | Đăng nhập | Quản trị**.

## 1. Giữ nguyên các view hiện có của App 1

Không xóa logic hiện tại cho các view:
- `?view=kpi`
- `?view=report`
- `?view=plan`
- `?view=login`
- `?view=admin`

Các tab trên Portal gọi thẳng các view này để người dùng chuyển chức năng bằng 1 chạm.

## 2. Tạo/cập nhật file Portal.html

Trong Apps Script: **+ > HTML > đặt tên `Portal`**.

Dán toàn bộ nội dung file `Portal.html` trong thư mục này vào. Nếu đã có `Portal.html`, thay toàn bộ nội dung bằng bản mới.

## 3. Điều hướng mặc định

URL `/exec` không tham số phải trả về `Portal.html`.

Quan trọng: nếu `doGet(e)` hiện tại đã xử lý `kpi`, `report`, `plan`, `login`, `admin`, phải GIỮ NGUYÊN các nhánh đó. Chỉ bổ sung nhánh mặc định `portal`; không thay các view cũ bằng Portal vì sẽ gây lặp iframe.

Ví dụ logic cần đạt:

```javascript
function doGet(e) {
  const view = String((e && e.parameter && e.parameter.view) || 'portal').toLowerCase();

  // GIỮ NGUYÊN các nhánh hiện tại của project:
  // if (view === 'kpi') ...
  // if (view === 'report') ...
  // if (view === 'plan') ...
  // if (view === 'login') ...
  // if (view === 'admin') ...

  // Cuối cùng, nếu không có view thì mở Portal:
  return HtmlService.createTemplateFromFile('Portal')
    .evaluate()
    .setTitle('Điều hành Viettel Cao Bằng')
    .setXFrameOptionsMode(HtmlService.XFrameOptionsMode.ALLOWALL)
    .addMetaTag('viewport', 'width=device-width, initial-scale=1, viewport-fit=cover');
}
```

Project chỉ được có **một hàm `doGet(e)`**.

## 4. Hai module bổ sung

Portal đang cấu hình:
- Trường học: `https://blackberrythangbv-bit.github.io/ViettelCaoBangDieuHanh/`
- Doanh nghiệp: `https://script.google.com/macros/s/AKfycbzxSHqdzpKUSpcf2IhO_0oozRxDx_zZVtLCi7bMzQJiAIOnux7JQFI2JvP188g4RzmD/exec`

Hai module này lazy-load: không bấm thì không nạp dữ liệu.

## 5. Cơ chế tối ưu tốc độ

- Mở app: chỉ nạp KPI.
- Báo cáo ngày/KH tuần/Trường học/Doanh nghiệp/Đăng nhập/Quản trị: chỉ nạp khi bấm lần đầu.
- Sau lần nạp đầu tiên, chuyển tab không tải lại iframe.
- Chỉ nút `TẢI LẠI` mới ép làm mới tab đang mở.
- Trên điện thoại, thanh 7 tab cuộn ngang để luôn truy cập được toàn bộ chức năng.

## 6. Deploy giữ nguyên link

Apps Script > **Deploy > Manage deployments > Edit > New version > Deploy**.

Không tạo deployment mới nếu muốn giữ nguyên URL `/exec`.

## Kiểm tra sau deploy

1. Mở `/exec` -> thấy đủ 7 tab.
2. KPI hiển thị trước.
3. Bấm từng tab -> đúng chức năng mới bắt đầu nạp.
4. Chuyển qua lại -> không reload lại tab đã mở.
5. Đăng nhập/Quản trị vẫn dùng đúng logic phân quyền cũ của App 1.
