# KPI → Tammi V2 (GitHub Build)

Ứng dụng Android mới, package riêng: `vn.viettel.caobang.kpitammi.v2`.

Chức năng:
- Kiểm tra nguồn KPI → Tammi.
- Tải bộ báo cáo ngày từ nguồn KPI DNS V2.
- Hậu kiểm đúng 8 file trước khi báo hoàn tất.
- Chia sẻ đồng thời 8 file qua Android Share Sheet.
- Thiết lập lịch tải nền hàng ngày.
- Không dùng package/chữ ký của APK cũ.

Build:
- GitHub Actions: **Build KPI Tammi V2 APK**
- Artifact: **KPI_Tammi_V2_GitHub**


## 2.0.5 — Gửi thủ công từ điện thoại

Bấm **Gửi báo cáo thủ công** để tải ZIP mới nhất trực tiếp từ gateway KPI; không cần Mac. Màn hình tách Excel nhóm, ảnh tổng hợp nhóm và ảnh riêng của 6 AM. Xem ảnh, sao chép tên người nhận rồi chia sẻ từng file; chọn Tammi, chọn đúng người nhận và bấm Gửi trong Tammi.

THAODP7 là **Đinh Phương Thảo CBG Thảo - AM**. QUYENLTN là **Lô Thị Ngọc Quyên CBG Quyên - AM**. Không chia sẻ cả bộ ảnh riêng vào nhóm.

Gateway hiện chỉ cung cấp ZIP 8 file. Dán tin chữ điều hành từ KPI DNS vào ô tin chữ; app yêu cầu ngày báo cáo xuất hiện trong nội dung. Tin chữ được chia sẻ và xác nhận riêng.

Mở bảng chia sẻ không chứng minh đã gửi. Sau khi gửi, quay lại app bấm **Xác nhận đã gửi**. Trạng thái là xác nhận của người dùng, không phải biên nhận Tammi. Trạng thái gắn với ngày, người nhận và SHA-256 nội dung; đổi file hoặc người nhận sẽ không thừa hưởng xác nhận cũ. Chia sẻ lại mục đã xác nhận có cảnh báo trùng.

Kiểm tra mã nguồn: `bash tests/run-manual-report-checks.sh`. Build: `gradle assembleDebug`. Cần thử trên điện thoại với Tammi đã đăng nhập để xác minh khả năng nhận ảnh, Excel và tin chữ qua bảng chia sẻ Android.

