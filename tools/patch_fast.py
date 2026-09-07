from pathlib import Path

p = Path('lib/main.dart')
s = p.read_text()

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
    setState(() { loading = true; error = null; });

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
      setState(() { loading = true; error = null; });
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
    raise SystemExit('Không tìm thấy hàm load hiện tại để patch cache-first')
s = s.replace(old_load, new_load, 1)
s = s.replace('actions: [IconButton(onPressed: load, icon: const Icon(Icons.refresh))]',
              'actions: [IconButton(onPressed: refresh, icon: const Icon(Icons.refresh))]', 1)
s = s.replace('onRefresh: load,', 'onRefresh: refresh,', 1)
s = s.replace('FilledButton(onPressed: load, child: const Text(\'Thử lại\'))',
              'FilledButton(onPressed: refresh, child: const Text(\'Thử lại\'))', 1)
p.write_text(s)

m = Path('android/app/src/main/AndroidManifest.xml')
ms = m.read_text()
if 'android.permission.INTERNET' not in ms:
    pos = ms.find('>') + 1
    ms = ms[:pos] + '\n    <uses-permission android:name="android.permission.INTERNET" />' + ms[pos:]
ms = ms.replace('android:label="viettel_cao_bang_kpi"', 'android:label="KPI DNS Cao Bằng"')
m.write_text(ms)

print('FAST_PATCH_OK')
