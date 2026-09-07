from pathlib import Path

p = Path('lib/main.dart')
s = p.read_text()

# Imports.
if "import 'auth_ui.dart';" not in s:
    s = s.replace("import 'donut.dart';", "import 'donut.dart';\nimport 'auth_ui.dart';\nimport 'session.dart';")

# Root app -> AuthGate.
s = s.replace(
    "home: const Home(),",
    "home: AuthGate(builder: (_) => const Home()),",
    1,
)

# Thêm menu tài khoản vào AppBar sau patch fast.
old_actions_tail = '''            else
              IconButton(onPressed: refresh, icon: const Icon(Icons.refresh)),
          ],'''
new_actions_tail = '''            else
              IconButton(onPressed: refresh, icon: const Icon(Icons.refresh)),
            PopupMenuButton<String>(
              icon: const Icon(Icons.account_circle),
              tooltip: 'Tài khoản',
              onSelected: accountAction,
              itemBuilder: (_) => [
                PopupMenuItem<String>(
                  enabled: false,
                  child: Text(
                    '${SessionStore.current?.user.displayName ?? ''} • ${SessionStore.current?.user.permissionLabel ?? ''}',
                    style: const TextStyle(fontWeight: FontWeight.w800),
                  ),
                ),
                if (SessionStore.current?.user.canManageUsers == true)
                  const PopupMenuItem(value: 'users', child: Text('Quản trị người dùng')),
                const PopupMenuItem(value: 'password', child: Text('Đổi mật khẩu')),
                const PopupMenuItem(value: 'logout', child: Text('Đăng xuất')),
              ],
            ),
          ],'''
if old_actions_tail not in s:
    raise SystemExit('Không tìm thấy AppBar actions sau fast patch')
s = s.replace(old_actions_tail, new_actions_tail, 1)

# Thêm handler menu tài khoản trước titleBar.
marker = '''  Widget titleBar(String s) => Container('''
handler = '''  Future<void> accountAction(String action) async {
    if (action == 'users') {
      if (SessionStore.current?.user.canManageUsers == true && mounted) {
        await Navigator.push(
          context,
          MaterialPageRoute(builder: (_) => const AdminUsersPage()),
        );
      }
      return;
    }
    if (action == 'password') {
      if (mounted) {
        await Navigator.push(
          context,
          MaterialPageRoute(builder: (_) => const ChangePasswordPage()),
        );
      }
      return;
    }
    if (action == 'logout') {
      await SessionStore.clear();
      if (!mounted) return;
      Navigator.of(context).pushAndRemoveUntil(
        MaterialPageRoute(builder: (_) => const KpiApp()),
        (_) => false,
      );
    }
  }

'''
if marker not in s:
    raise SystemExit('Không tìm thấy titleBar để chèn accountAction')
s = s.replace(marker, handler + marker, 1)

# Chỉ hiển thị AM mà backend đã cấp quyền/target.
s = s.replace(
    "children: amCodes.map((x) => FilledButton.tonal(onPressed: () => openAm(x), child: Text(x))).toList(),",
    "children: amCodes.where((x) => targets.containsKey(x)).map((x) => FilledButton.tonal(onPressed: () => openAm(x), child: Text(x))).toList(),",
    1,
)

# Vô hiệu hóa nút lưu nếu user chỉ có quyền xem.
old_save = "FilledButton.icon(onPressed: saving ? null : save, icon: const Icon(Icons.save), label: Text(saving ? 'Đang lưu...' : 'Lưu kết quả'))"
new_save = "FilledButton.icon(onPressed: saving || SessionStore.current?.user.canEdit != true ? null : save, icon: const Icon(Icons.save), label: Text(SessionStore.current?.user.canEdit == true ? (saving ? 'Đang lưu...' : 'Lưu kết quả') : 'Tài khoản chỉ có quyền xem'))"
if old_save not in s:
    raise SystemExit('Không tìm thấy nút lưu để áp quyền')
s = s.replace(old_save, new_save, 1)

p.write_text(s)
print('AUTH_V110_PATCH_OK')
