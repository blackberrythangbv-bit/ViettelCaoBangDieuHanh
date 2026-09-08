from pathlib import Path

# Fix/prepare report exporter source.
r = Path('lib/report_export.dart')
s = r.read_text()
if "import 'dart:convert';" not in s:
    s = s.replace("import 'dart:io';", "import 'dart:convert';\nimport 'dart:io';", 1)
s = s.replace("final bytes = Uint8List.fromList(text.codeUnits);", "final bytes = Uint8List.fromList(utf8.encode(text));")
s = s.replace('formatCode="#\\,##0"', 'formatCode="#,##0"')
old_cell = '''  static void _cell(Canvas canvas, Rect rect, Color fill, Color border) {
    canvas.drawRect(rect, Paint()..color = fill);
    canvas.drawRect(rect, Paint()..color = border..style = PaintingStyle.stroke..strokeWidth = 1);
  }

  static void _rounded(Canvas canvas, Rect rect, Color fill, Color border, {double radius = 8, double stroke = 1}) {
    final rr = RRect.fromRectAndRadius(rect, Radius.circular(radius));
    canvas.drawRRect(rr, Paint()..color = fill);
    canvas.drawRRect(rr, Paint()..color = border..style = PaintingStyle.stroke..strokeWidth = stroke);
  }
'''
new_cell = '''  static void _cell(Canvas canvas, Rect rect, Color fill, Color border) {
    canvas.drawRect(rect, Paint()..color = fill);
    final outline = Paint()
      ..color = border
      ..style = PaintingStyle.stroke
      ..strokeWidth = 1;
    canvas.drawRect(rect, outline);
  }

  static void _rounded(Canvas canvas, Rect rect, Color fill, Color border, {double radius = 8, double stroke = 1}) {
    final rr = RRect.fromRectAndRadius(rect, Radius.circular(radius));
    canvas.drawRRect(rr, Paint()..color = fill);
    final outline = Paint()
      ..color = border
      ..style = PaintingStyle.stroke
      ..strokeWidth = stroke;
    canvas.drawRRect(rr, outline);
  }
'''
if old_cell not in s:
    raise SystemExit('Không tìm thấy block _cell/_rounded để sửa')
s = s.replace(old_cell, new_cell, 1)
r.write_text(s)

# Patch main.dart AFTER patch_fast.py, preserving v1.0.7 Phương án B behavior.
p = Path('lib/main.dart')
s = p.read_text()
if "import 'report_export.dart';" not in s:
    s = s.replace("import 'donut.dart';", "import 'donut.dart';\nimport 'report_export.dart';", 1)

old_state = '''  bool loading = true;
  bool syncing = false;
  String? error;'''
new_state = '''  bool loading = true;
  bool syncing = false;
  bool exporting = false;
  String? error;'''
if old_state not in s:
    raise SystemExit('Không tìm thấy state Home sau patch_fast')
s = s.replace(old_state, new_state, 1)

marker = '''  Widget titleBar(String s) => Container('''
method = '''  Future<void> exportDailyReport() async {
    if (exporting || all.isEmpty || targets.isEmpty) return;
    setState(() => exporting = true);
    try {
      // Ưu tiên dữ liệu mới nhất; nếu mạng lỗi vẫn cho phép xuất từ dữ liệu đang hiển thị.
      try {
        final fresh = await api.bootstrap(probeBootstrap: true);
        all = fresh.all;
        targets = fresh.targets;
        if (mounted) setState(() {});
      } catch (_) {}

      await DailyKpiReportExporter.exportAndShare(
        all: all,
        targets: targets,
        reportDate: DateTime.now(),
      );
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Đã tạo báo cáo: 1 Excel + 7 PNG và đóng gói ZIP.')),
        );
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Không xuất được báo cáo: $e')),
        );
      }
    } finally {
      if (mounted) setState(() => exporting = false);
    }
  }

'''
if marker not in s:
    raise SystemExit('Không tìm thấy titleBar để chèn exportDailyReport')
s = s.replace(marker, method + marker, 1)

old_actions = '''          actions: [
            if (syncing)
              const Padding(
                padding: EdgeInsets.symmetric(horizontal: 16),
                child: Center(
                  child: SizedBox(
                    width: 18,
                    height: 18,
                    child: CircularProgressIndicator(
                      strokeWidth: 2,
                      color: Colors.white,
                    ),
                  ),
                ),
              )
            else
              IconButton(onPressed: refresh, icon: const Icon(Icons.refresh)),
          ],'''
new_actions = '''          actions: [
            IconButton(
              tooltip: 'Xuất báo cáo KPI ngày',
              onPressed: exporting || all.isEmpty ? null : exportDailyReport,
              icon: const Icon(Icons.file_download_outlined),
            ),
            if (syncing)
              const Padding(
                padding: EdgeInsets.symmetric(horizontal: 16),
                child: Center(
                  child: SizedBox(
                    width: 18,
                    height: 18,
                    child: CircularProgressIndicator(
                      strokeWidth: 2,
                      color: Colors.white,
                    ),
                  ),
                ),
              )
            else
              IconButton(onPressed: refresh, icon: const Icon(Icons.refresh)),
          ],'''
if old_actions not in s:
    raise SystemExit('Không tìm thấy AppBar actions của Phương án B')
s = s.replace(old_actions, new_actions, 1)

old_body = '''        body: loading'''
new_body = '''        floatingActionButton: loading || all.isEmpty
            ? null
            : FloatingActionButton.extended(
                onPressed: exporting ? null : exportDailyReport,
                icon: exporting
                    ? const SizedBox(
                        width: 18,
                        height: 18,
                        child: CircularProgressIndicator(strokeWidth: 2),
                      )
                    : const Icon(Icons.archive_outlined),
                label: Text(exporting ? 'Đang xuất...' : 'Xuất báo cáo'),
              ),
        body: loading'''
if old_body not in s:
    raise SystemExit('Không tìm thấy Home Scaffold body')
s = s.replace(old_body, new_body, 1)

p.write_text(s)
print('REPORT_EXPORT_V109_PATCH_OK')
