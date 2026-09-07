from pathlib import Path
import re

p = Path('lib/main.dart')
s = p.read_text()

if "import 'donut.dart';" not in s:
    s = s.replace("import 'data.dart';", "import 'data.dart';\nimport 'donut.dart';")

old_load = '''  Future<void> load() async {
    setState(() {
      loading = true;
      error = null;
    });
    try {
      final r = await Future.wait([api.all(), api.targets()]);
      all = r[0] as Map<String, AmData>;
      targets = r[1] as Map<String, AmTarget>;
    } catch (e) {
      error = '$e';
    }
    if (mounted) setState(() => loading = false);
  }
'''

new_load = '''  Future<void> load() async {
    setState(() {
      loading = true;
      error = null;
    });

    final cached = await Future.wait([api.cachedAll(), api.cachedTargets()]);
    final cachedAll = cached[0] as Map<String, AmData>?;
    final cachedTargets = cached[1] as Map<String, AmTarget>?;

    if (cachedAll != null && cachedTargets != null && cachedAll.isNotEmpty) {
      all = cachedAll;
      targets = cachedTargets;
      loading = false;
      if (mounted) setState(() {});
    }

    await _networkRefresh(showSpinner: all.isEmpty);
  }

  Future<void> refresh() => _networkRefresh(showSpinner: true);

  Future<void> _networkRefresh({required bool showSpinner}) async {
    if (showSpinner && mounted) {
      setState(() {
        loading = true;
        error = null;
      });
    }
    try {
      final r = await Future.wait([api.all(), api.targets()]);
      all = r[0] as Map<String, AmData>;
      targets = r[1] as Map<String, AmTarget>;
      error = null;
    } catch (e) {
      if (all.isEmpty) error = '$e';
    }
    if (mounted) setState(() => loading = false);
  }
'''

if old_load not in s:
    raise SystemExit('Không tìm thấy hàm load cũ để patch cache-first')
s = s.replace(old_load, new_load, 1)
s = s.replace('onPressed: load, icon: const Icon(Icons.refresh)', 'onPressed: refresh, icon: const Icon(Icons.refresh)', 1)
s = s.replace('onRefresh: load,', 'onRefresh: refresh,', 1)

home_pattern = re.compile(
    r"_title\('1\. Tóm tắt kết quả toàn tỉnh'\),.*?_title\('2\. Đánh giá & cảnh báo'\),",
    re.S,
)
home_repl = """_title('1. Tóm tắt kết quả toàn tỉnh'),
          KpiDonutGrid(
            monthTh: totalTh,
            monthKh: totalKh,
            weekTh: totalWeekTh,
            weekKh: totalWeekKh,
            monthElapsed: monthElapsedRatio(now),
            weekElapsed: weekElapsedRatio(now),
          ),
          _title('2. Đánh giá & cảnh báo'),"""
s, n1 = home_pattern.subn(home_repl, s, count=1)
if n1 != 1:
    raise SystemExit('Không patch được dashboard donut')

am_pattern = re.compile(
    r"Text\('Tuần hiện tại: Tuần \$\{wi \+ 1\}'.*?\n\s*\.\.\.List\.generate\(.*?\n\s*\),\n\s*\],\n\s*\);",
    re.S,
)
am_repl = """Text('Tuần hiện tại: Tuần ${wi + 1}', style: const TextStyle(fontSize: 19, fontWeight: FontWeight.w900)),
        const SizedBox(height: 10),
        KpiDonutGrid(
          monthTh: a.th,
          monthKh: a.kh,
          weekTh: wa,
          weekKh: wp,
          monthElapsed: monthElapsedRatio(now),
          weekElapsed: weekElapsedRatio(now),
        ),
      ],
    );"""
s, n2 = am_pattern.subn(am_repl, s, count=1)
if n2 != 1:
    raise SystemExit('Không patch được donut từng AM')

p.write_text(s)

m = Path('android/app/src/main/AndroidManifest.xml')
s = m.read_text()
if 'android.permission.INTERNET' not in s:
    pos = s.find('>') + 1
    s = s[:pos] + '\n    <uses-permission android:name="android.permission.INTERNET" />' + s[pos:]
s = s.replace('android:label="viettel_cao_bang_kpi"', 'android:label="KPI DNS Cao Bằng"')
m.write_text(s)

print('FAST_PATCH_OK')
