from pathlib import Path

p = Path('lib/main.dart')
s = p.read_text()

# Cache-first + bootstrap 1 request, giữ đúng Phương án B nhưng không thêm auth.
s = s.replace(
    "  bool loading = true;\n  String? error;",
    "  bool loading = true;\n  bool syncing = false;\n  String? error;",
    1,
)

old_load = '''  Future<void> load() async {\n    setState(() { loading = true; error = null; });\n    try {\n      final r = await Future.wait([api.all(), api.targets()]);\n      all = r[0] as Map<String, AmData>;\n      targets = r[1] as Map<String, AmTarget>;\n    } catch (e) {\n      error = '$e';\n    }\n    if (mounted) setState(() => loading = false);\n  }\n'''

new_load = '''  Future<void> load() async {\n    error = null;\n    final cached = await api.cachedBootstrap();\n\n    if (cached != null) {\n      all = cached.all;\n      targets = cached.targets;\n      loading = false;\n      syncing = true;\n      if (mounted) setState(() {});\n      await _networkRefresh(showSpinner: false, probeBootstrap: true);\n      return;\n    }\n\n    await _networkRefresh(showSpinner: true, probeBootstrap: false);\n  }\n\n  Future<void> refresh() => _networkRefresh(\n        showSpinner: all.isEmpty,\n        probeBootstrap: true,\n      );\n\n  Future<void> _networkRefresh({\n    required bool showSpinner,\n    required bool probeBootstrap,\n  }) async {\n    if (mounted) {\n      setState(() {\n        if (showSpinner) loading = true;\n        syncing = true;\n        error = null;\n      });\n    }\n    try {\n      final r = await api.bootstrap(probeBootstrap: probeBootstrap);\n      all = r.all;\n      targets = r.targets;\n      error = null;\n    } catch (e) {\n      if (all.isEmpty || targets.isEmpty) error = '$e';\n    }\n    if (mounted) {\n      setState(() {\n        loading = false;\n        syncing = false;\n      });\n    }\n  }\n'''

if old_load in s:
    s = s.replace(old_load, new_load, 1)

s = s.replace(
    "actions: [IconButton(onPressed: load, icon: const Icon(Icons.refresh))],",
    """actions: [\n            if (syncing)\n              const Padding(\n                padding: EdgeInsets.symmetric(horizontal: 16),\n                child: Center(\n                  child: SizedBox(\n                    width: 18,\n                    height: 18,\n                    child: CircularProgressIndicator(\n                      strokeWidth: 2,\n                      color: Colors.white,\n                    ),\n                  ),\n                ),\n              )\n            else\n              IconButton(onPressed: refresh, icon: const Icon(Icons.refresh)),\n          ],""",
    1,
)
s = s.replace('onRefresh: load,', 'onRefresh: refresh,', 1)
s = s.replace("FilledButton(onPressed: load, child: const Text('Thử lại'))", "FilledButton(onPressed: refresh, child: const Text('Thử lại'))", 1)

s = s.replace(
    "MaterialPageRoute(builder: (_) => AmPage(code: code, target: t))",
    "MaterialPageRoute(builder: (_) => AmPage(code: code, target: t, initialData: all[code]))",
    1,
)
s = s.replace(".then((_) => load());", ".then((_) => refresh());", 1)

old_ctor = '''class AmPage extends StatefulWidget {\n  const AmPage({super.key, required this.code, required this.target});\n  final String code;\n  final AmTarget target;\n'''
new_ctor = '''class AmPage extends StatefulWidget {\n  const AmPage({\n    super.key,\n    required this.code,\n    required this.target,\n    this.initialData,\n  });\n  final String code;\n  final AmTarget target;\n  final AmData? initialData;\n'''
if old_ctor in s:
    s = s.replace(old_ctor, new_ctor, 1)

old_init = '''  void initState() {\n    super.initState();\n    tabs = TabController(length: 2, vsync: this);\n    load();\n  }\n'''
new_init = '''  void initState() {\n    super.initState();\n    tabs = TabController(length: 2, vsync: this);\n    data = widget.initialData;\n    if (data != null) fill();\n    load();\n  }\n'''
pos = s.find('class _AmPageState')
if pos >= 0:
    head, tail = s[:pos], s[pos:]
    if old_init in tail:
        tail = tail.replace(old_init, new_init, 1)
        s = head + tail

p.write_text(s)

# Đặt tên hiển thị iOS.
plist = Path('ios/Runner/Info.plist')
ps = plist.read_text()
ps = ps.replace('<string>viettel_cao_bang_kpi</string>', '<string>KPI DNS Cao Bằng</string>')
plist.write_text(ps)
print('IOS_V101_BALANCED_PATCH_OK')
