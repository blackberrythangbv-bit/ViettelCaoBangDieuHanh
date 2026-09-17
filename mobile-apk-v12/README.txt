APK V1.2.5 WEBAPP FULL - VIETTEL CAO BẰNG

Production URL:
https://script.google.com/macros/s/AKfycbxwDT_LV1D49SKfZkv0_CfmBcRcpubbJGnd9TFBL5b1y0AHQ-a1zbRQf83CBWDeRkaApQ/exec

Điểm sửa:
- Mở production Web App trực tiếp bằng Android WebView.
- User-Agent có ViettelCaoBangApp/1.2.5.
- Bắt tải HTTP/HTTPS bằng Android DownloadManager.
- Bắt blob: download bằng JavascriptInterface.
- Tự lưu báo cáo vào Download/ViettelCaoBang.
- Hỗ trợ ZIP/XLS/XLSX/PNG và các blob download khác.
- Có cookie Google/Apps Script để tải file cần phiên đăng nhập.
- Không cần sửa lại APK khi Web App cập nhật nội dung.

Build trên GitHub Actions / Android Studio.
Nếu Android báo không thể cập nhật bản cũ do chữ ký debug khác, gỡ APK cũ rồi cài V1.2.

Cập nhật V1.2.5:
- Quy trình build không còn tự thay HOME_URL sang deployment khác.
- HOME_URL trong MainActivity.java là nguồn cấu hình duy nhất cho hai workflow Android V1.2.x.
- Workflow build-apk.yml tự chạy khi mã mobile-apk-v12 thay đổi trên main.
- Workflow build-mobile-v12-apk.yml chỉ chạy thủ công để tránh tạo hai APK tự động.
- versionCode 17, versionName 1.2.5.
- Đây là sửa đồng nhất deployment, chưa bổ sung chức năng nghiệp vụ mới.
- Cần đăng nhập app web và kiểm thử trên Android trước khi phát hành.
