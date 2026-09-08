from pathlib import Path

p = Path('lib/report_export.dart')
s = p.read_text()

if "import 'package:public_file_saver/public_file_saver.dart';" not in s:
    s = s.replace(
        "import 'package:path_provider/path_provider.dart';",
        "import 'package:path_provider/path_provider.dart';\nimport 'package:public_file_saver/public_file_saver.dart';",
        1,
    )

old = '''    final zip = File('${temp.path}/KPI_ngay_$stamp.zip');
    await zip.writeAsBytes(zipBytes, flush: true);

    await Share.shareXFiles(
      [XFile(zip.path)],
      subject: 'Báo cáo KPI ngày ${DateFormat('dd/MM/yyyy').format(date)}',
      text: 'Báo cáo KPI ngày ${DateFormat('dd/MM/yyyy').format(date)} - Kênh KHDN Viettel Cao Bằng',
    );
    return zip;
'''

new = '''    final zip = File('${temp.path}/KPI_ngay_$stamp.zip');
    await zip.writeAsBytes(zipBytes, flush: true);

    // Lưu một bản thật vào bộ nhớ công khai của thiết bị trước khi mở bảng chia sẻ.
    // Android 10+ dùng MediaStore, không cần quyền lưu trữ; người dùng thấy file trong Downloads.
    final saver = PublicFileSaver();
    final saved = await saver.saveBytes(
      bytes: Uint8List.fromList(zipBytes),
      fileName: 'KPI_ngay_$stamp.zip',
      mimeType: 'application/zip',
      subDir: 'Viettel_CaoBang_KPI',
    );
    if (saved == null || !saved.isSuccess) {
      throw Exception('Đã tạo báo cáo nhưng chưa lưu được vào thư mục Downloads của máy');
    }

    await Share.shareXFiles(
      [XFile(zip.path)],
      subject: 'Báo cáo KPI ngày ${DateFormat('dd/MM/yyyy').format(date)}',
      text: 'Đã lưu tại Downloads/Viettel_CaoBang_KPI. Báo cáo KPI ngày ${DateFormat('dd/MM/yyyy').format(date)} - Kênh KHDN Viettel Cao Bằng',
    );
    return zip;
'''

if old not in s:
    raise SystemExit('Không tìm thấy block ZIP/share để patch lưu cục bộ')
s = s.replace(old, new, 1)
p.write_text(s)

# Cập nhật thông báo trên giao diện để người dùng biết file đã nằm trong máy.
m = Path('lib/main.dart')
ms = m.read_text()
ms = ms.replace(
    "Đã tạo báo cáo: 1 Excel + 7 PNG và đóng gói ZIP.",
    "Đã lưu ZIP vào Downloads/Viettel_CaoBang_KPI và mở bảng chia sẻ.",
)
m.write_text(ms)

# iOS: cho phép nhìn thấy Documents của app trong ứng dụng Files.
info = Path('ios/Runner/Info.plist')
if info.exists():
    text = info.read_text()
    if '<key>UIFileSharingEnabled</key>' not in text:
        insert = '''\n\t<key>UIFileSharingEnabled</key>\n\t<true/>\n\t<key>LSSupportsOpeningDocumentsInPlace</key>\n\t<true/>\n'''
        text = text.replace('</dict>', insert + '</dict>', 1)
        info.write_text(text)

print('SAVE_LOCAL_V110_PATCH_OK')
