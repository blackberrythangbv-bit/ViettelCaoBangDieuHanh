from pathlib import Path

p = Path('lib/main.dart')
s = p.read_text()

# 1) Thêm trạng thái đồng bộ nền.
s = s.replace(
    "  bool loading = true;\n  String? error;",
    "  bool loading = true;\n  bool syncing = false;\n  String? error;",
    1,
)

# 2) Cache-first + bootstrap 1 request, fallback 2 request song song.
old_load = '''  Future<void> load() async {
    setState(() { loading = true; error = null; });
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
    error = null;
    final cached = await api.cachedBootstrap();

    if (cached != null) {
      all = cached.all;
      targets = cached.targets;
      loading = false;
      syncing = true;
      if (mounted) setState(() {});
      await _networkRefresh(showSpinner: false, probeBootstrap: true);
      return;
    }

    await _networkRefresh(showSpinner: true, probeBootstrap: false);
  }

  Future<void> refresh() => _networkRefresh(
        showSpinner: all.isEmpty,
        probeBootstrap: true,
      );

  Future<void> _networkRefresh({
    required bool showSpinner,
    required bool probeBootstrap,
  }) async {
    if (mounted) {
      setState(() {
        if (showSpinner) loading = true;
        syncing = true;
        error = null;
      });
    }
    try {
      final r = await api.bootstrap(probeBootstrap: probeBootstrap);
      all = r.all;
      targets = r.targets;
      error = null;
    } catch (e) {
      if (all.isEmpty || targets.isEmpty) error = '$e';
    }
    if (mounted) {
      setState(() {
        loading = false;
        syncing = false;
      });
    }
  }
'''

if old_load not in s:
    raise SystemExit('Không tìm thấy hàm load hiện tại để patch cache-first/bootstrap')
s = s.replace(old_load, new_load, 1)

# 3) Không khóa giao diện khi đã có cache; hiển thị trạng thái đồng bộ nhỏ trên AppBar.
s = s.replace(
    "actions: [IconButton(onPressed: load, icon: const Icon(Icons.refresh))],",
    """actions: [
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
          ],""",
    1,
)
s = s.replace('onRefresh: load,', 'onRefresh: refresh,', 1)
s = s.replace(
    "FilledButton(onPressed: load, child: const Text('Thử lại'))",
    "FilledButton(onPressed: refresh, child: const Text('Thử lại'))",
    1,
)

# 4) Mở AM bằng dữ liệu dashboard có sẵn, refresh chi tiết ở nền.
s = s.replace(
    "MaterialPageRoute(builder: (_) => AmPage(code: code, target: t))",
    "MaterialPageRoute(builder: (_) => AmPage(code: code, target: t, initialData: all[code]))",
    1,
)
s = s.replace(".then((_) => load());", ".then((_) => refresh());", 1)

old_ctor = '''class AmPage extends StatefulWidget {
  const AmPage({super.key, required this.code, required this.target});
  final String code;
  final AmTarget target;
'''
new_ctor = '''class AmPage extends StatefulWidget {
  const AmPage({
    super.key,
    required this.code,
    required this.target,
    this.initialData,
  });
  final String code;
  final AmTarget target;
  final AmData? initialData;
'''
if old_ctor not in s:
    raise SystemExit('Không tìm thấy constructor AmPage để patch initialData')
s = s.replace(old_ctor, new_ctor, 1)

old_init = '''  void initState() {
    super.initState();
    tabs = TabController(length: 2, vsync: this);
    load();
  }
'''
new_init = '''  void initState() {
    super.initState();
    tabs = TabController(length: 2, vsync: this);
    data = widget.initialData;
    if (data != null) fill();
    load();
  }
'''
# Chỉ thay initState của AmPage (lần cuối phù hợp), tránh Home.initState.
pos = s.find('class _AmPageState')
if pos < 0:
    raise SystemExit('Không tìm thấy _AmPageState')
head, tail = s[:pos], s[pos:]
if old_init not in tail:
    raise SystemExit('Không tìm thấy initState của AmPage')
tail = tail.replace(old_init, new_init, 1)
s = head + tail

p.write_text(s)

# Android network + nhãn app.
m = Path('android/app/src/main/AndroidManifest.xml')
ms = m.read_text()
if 'android.permission.INTERNET' not in ms:
    pos = ms.find('>') + 1
    ms = ms[:pos] + '\n    <uses-permission android:name="android.permission.INTERNET" />' + ms[pos:]
ms = ms.replace('android:label="viettel_cao_bang_kpi"', 'android:label="KPI DNS Cao Bằng"')
m.write_text(ms)

print('BALANCED_FAST_PATCH_OK')
