/*
KPI DNS V1.1.0 - ROUTER

Trong file Mã.gs:
1) Xóa/đổi tên function doGet(e) hiện tại.
2) Xóa/đổi tên function doPost(e) hiện tại.
3) Dán 2 hàm dưới đây vào cuối file Mã.gs.

Yêu cầu: file AUTH_V110.gs đã được tạo trong cùng project Apps Script.
*/

function doGet(e) {
  return authRouterGetV110_(e);
}

function doPost(e) {
  return authRouterPostV110_(e);
}
