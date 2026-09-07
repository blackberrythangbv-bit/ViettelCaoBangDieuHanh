import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'api.dart';
import 'data.dart';
import 'session.dart';

class AuthGate extends StatefulWidget {
  const AuthGate({super.key, required this.builder});
  final Widget Function(AuthSession session) builder;

  @override
  State<AuthGate> createState() => _AuthGateState();
}

class _AuthGateState extends State<AuthGate> {
  final api = Api();
  bool loading = true;
  AuthSession? session;

  @override
  void initState() {
    super.initState();
    restore();
  }

  Future<void> restore() async {
    final saved = await SessionStore.restore();
    if (saved != null) {
      try {
        final user = await api.me();
        session = AuthSession(token: SessionStore.current!.token, user: user);
        await SessionStore.save(session!);
      } catch (_) {
        await SessionStore.clear();
        session = null;
      }
    }
    if (mounted) setState(() => loading = false);
  }

  void loggedIn(AuthSession s) {
    setState(() => session = s);
  }

  void passwordChanged(AuthSession s) {
    setState(() => session = s);
  }

  @override
  Widget build(BuildContext context) {
    if (loading) {
      return const Scaffold(body: Center(child: CircularProgressIndicator()));
    }
    final s = session;
    if (s == null) return LoginPage(onLoggedIn: loggedIn);
    if (s.user.mustChangePassword) {
      return ChangePasswordPage(
        mandatory: true,
        onChanged: passwordChanged,
      );
    }
    return widget.builder(s);
  }
}

class LoginPage extends StatefulWidget {
  const LoginPage({super.key, required this.onLoggedIn});
  final ValueChanged<AuthSession> onLoggedIn;

  @override
  State<LoginPage> createState() => _LoginPageState();
}

class _LoginPageState extends State<LoginPage> {
  final api = Api();
  final user = TextEditingController();
  final pass = TextEditingController();
  bool busy = false;
  bool obscure = true;
  String? error;

  Future<void> submit() async {
    if (user.text.trim().isEmpty || pass.text.isEmpty) {
      setState(() => error = 'Nhập đầy đủ user và mật khẩu');
      return;
    }
    setState(() {
      busy = true;
      error = null;
    });
    try {
      final s = await api.login(user.text, pass.text);
      widget.onLoggedIn(s);
    } catch (e) {
      setState(() => error = '$e'.replaceFirst('Exception: ', ''));
    }
    if (mounted) setState(() => busy = false);
  }

  @override
  Widget build(BuildContext context) => Scaffold(
        backgroundColor: const Color(0xFFF5F6F7),
        body: SafeArea(
          child: Center(
            child: SingleChildScrollView(
              padding: const EdgeInsets.all(24),
              child: ConstrainedBox(
                constraints: const BoxConstraints(maxWidth: 440),
                child: Card(
                  child: Padding(
                    padding: const EdgeInsets.all(24),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.stretch,
                      children: [
                        const Icon(Icons.shield_outlined, size: 56, color: Color(0xFFE80500)),
                        const SizedBox(height: 14),
                        const Text(
                          'KPI DNS - VIETTEL CAO BẰNG',
                          textAlign: TextAlign.center,
                          style: TextStyle(fontSize: 21, fontWeight: FontWeight.w900),
                        ),
                        const SizedBox(height: 4),
                        const Text('Đăng nhập để tiếp tục', textAlign: TextAlign.center),
                        const SizedBox(height: 24),
                        TextField(
                          controller: user,
                          textInputAction: TextInputAction.next,
                          decoration: const InputDecoration(
                            labelText: 'User',
                            prefixIcon: Icon(Icons.person_outline),
                            border: OutlineInputBorder(),
                          ),
                        ),
                        const SizedBox(height: 12),
                        TextField(
                          controller: pass,
                          obscureText: obscure,
                          onSubmitted: (_) => busy ? null : submit(),
                          decoration: InputDecoration(
                            labelText: 'Mật khẩu',
                            prefixIcon: const Icon(Icons.lock_outline),
                            border: const OutlineInputBorder(),
                            suffixIcon: IconButton(
                              onPressed: () => setState(() => obscure = !obscure),
                              icon: Icon(obscure ? Icons.visibility : Icons.visibility_off),
                            ),
                          ),
                        ),
                        if (error != null) ...[
                          const SizedBox(height: 12),
                          Text(error!, style: const TextStyle(color: Color(0xFFE80500), fontWeight: FontWeight.w700)),
                        ],
                        const SizedBox(height: 18),
                        FilledButton.icon(
                          onPressed: busy ? null : submit,
                          icon: busy
                              ? const SizedBox(width: 18, height: 18, child: CircularProgressIndicator(strokeWidth: 2))
                              : const Icon(Icons.login),
                          label: const Text('Đăng nhập'),
                        ),
                      ],
                    ),
                  ),
                ),
              ),
            ),
          ),
        ),
      );

  @override
  void dispose() {
    user.dispose();
    pass.dispose();
    super.dispose();
  }
}

class ChangePasswordPage extends StatefulWidget {
  const ChangePasswordPage({
    super.key,
    this.mandatory = false,
    this.onChanged,
  });
  final bool mandatory;
  final ValueChanged<AuthSession>? onChanged;

  @override
  State<ChangePasswordPage> createState() => _ChangePasswordPageState();
}

class _ChangePasswordPageState extends State<ChangePasswordPage> {
  final api = Api();
  final oldPass = TextEditingController();
  final newPass = TextEditingController();
  final confirm = TextEditingController();
  bool busy = false;
  String? error;

  Future<void> save() async {
    if (newPass.text.length < 8) {
      setState(() => error = 'Mật khẩu mới phải có ít nhất 8 ký tự');
      return;
    }
    if (newPass.text != confirm.text) {
      setState(() => error = 'Mật khẩu nhập lại chưa khớp');
      return;
    }
    setState(() {
      busy = true;
      error = null;
    });
    try {
      final s = await api.changePassword(oldPass.text, newPass.text);
      if (widget.onChanged != null) {
        widget.onChanged!(s);
      } else if (mounted) {
        Navigator.pop(context, true);
      }
    } catch (e) {
      setState(() => error = '$e'.replaceFirst('Exception: ', ''));
    }
    if (mounted) setState(() => busy = false);
  }

  @override
  Widget build(BuildContext context) => Scaffold(
        appBar: widget.mandatory ? null : AppBar(title: const Text('Đổi mật khẩu')),
        body: SafeArea(
          child: Center(
            child: SingleChildScrollView(
              padding: const EdgeInsets.all(20),
              child: ConstrainedBox(
                constraints: const BoxConstraints(maxWidth: 480),
                child: Card(
                  child: Padding(
                    padding: const EdgeInsets.all(22),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.stretch,
                      children: [
                        Text(
                          widget.mandatory ? 'ĐỔI MẬT KHẨU LẦN ĐẦU' : 'ĐỔI MẬT KHẨU',
                          textAlign: TextAlign.center,
                          style: const TextStyle(fontSize: 20, fontWeight: FontWeight.w900, color: Color(0xFFE80500)),
                        ),
                        const SizedBox(height: 18),
                        TextField(
                          controller: oldPass,
                          obscureText: true,
                          decoration: const InputDecoration(labelText: 'Mật khẩu hiện tại', border: OutlineInputBorder()),
                        ),
                        const SizedBox(height: 12),
                        TextField(
                          controller: newPass,
                          obscureText: true,
                          decoration: const InputDecoration(labelText: 'Mật khẩu mới (>= 8 ký tự)', border: OutlineInputBorder()),
                        ),
                        const SizedBox(height: 12),
                        TextField(
                          controller: confirm,
                          obscureText: true,
                          decoration: const InputDecoration(labelText: 'Nhập lại mật khẩu mới', border: OutlineInputBorder()),
                        ),
                        if (error != null) ...[
                          const SizedBox(height: 12),
                          Text(error!, style: const TextStyle(color: Color(0xFFE80500), fontWeight: FontWeight.w700)),
                        ],
                        const SizedBox(height: 16),
                        FilledButton(onPressed: busy ? null : save, child: Text(busy ? 'Đang lưu...' : 'Đổi mật khẩu')),
                      ],
                    ),
                  ),
                ),
              ),
            ),
          ),
        ),
      );

  @override
  void dispose() {
    oldPass.dispose();
    newPass.dispose();
    confirm.dispose();
    super.dispose();
  }
}

class AdminUsersPage extends StatefulWidget {
  const AdminUsersPage({super.key});

  @override
  State<AdminUsersPage> createState() => _AdminUsersPageState();
}

class _AdminUsersPageState extends State<AdminUsersPage> {
  final api = Api();
  bool loading = true;
  String? error;
  List<AuthUser> users = [];

  @override
  void initState() {
    super.initState();
    load();
  }

  Future<void> load() async {
    setState(() {
      loading = true;
      error = null;
    });
    try {
      users = await api.adminUsers();
      users.sort((a, b) {
        if (a.isOwner != b.isOwner) return a.isOwner ? -1 : 1;
        return a.username.compareTo(b.username);
      });
    } catch (e) {
      error = '$e'.replaceFirst('Exception: ', '');
    }
    if (mounted) setState(() => loading = false);
  }

  Future<void> edit([AuthUser? user]) async {
    final changed = await Navigator.push<bool>(
      context,
      MaterialPageRoute(builder: (_) => UserEditPage(existing: user)),
    );
    if (changed == true) await load();
  }

  Future<void> remove(AuthUser user) async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (_) => AlertDialog(
        title: const Text('Xóa user?'),
        content: Text('Xóa tài khoản ${user.username} - ${user.displayName}?'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('Hủy')),
          FilledButton(onPressed: () => Navigator.pop(context, true), child: const Text('Xóa')),
        ],
      ),
    );
    if (ok != true) return;
    try {
      await api.adminDeleteUser(user.username);
      await load();
    } catch (e) {
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('$e')));
    }
  }

  Future<void> resetPassword(AuthUser user) async {
    try {
      final p = await api.adminResetPassword(user.username);
      if (!mounted) return;
      await showTemporaryPassword(context, user.username, p);
      await load();
    } catch (e) {
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('$e')));
    }
  }

  @override
  Widget build(BuildContext context) => Scaffold(
        appBar: AppBar(
          title: const Text('Quản trị người dùng'),
          backgroundColor: const Color(0xFFE80500),
          foregroundColor: Colors.white,
          actions: [IconButton(onPressed: load, icon: const Icon(Icons.refresh))],
        ),
        floatingActionButton: FloatingActionButton.extended(
          onPressed: () => edit(),
          icon: const Icon(Icons.person_add_alt_1),
          label: const Text('Thêm user'),
        ),
        body: loading
            ? const Center(child: CircularProgressIndicator())
            : error != null
                ? Center(child: Text(error!))
                : ListView.builder(
                    padding: const EdgeInsets.fromLTRB(12, 12, 12, 90),
                    itemCount: users.length,
                    itemBuilder: (_, i) {
                      final u = users[i];
                      return Card(
                        child: ListTile(
                          onTap: () => edit(u),
                          leading: CircleAvatar(
                            child: Icon(u.canManageUsers ? Icons.admin_panel_settings : Icons.person),
                          ),
                          title: Text('${u.displayName} (${u.username})', style: const TextStyle(fontWeight: FontWeight.w800)),
                          subtitle: Text(
                            '${u.permissionLabel} • Phạm vi: ${u.scope}\n${u.active ? 'Đang hoạt động' : 'Đã khóa'}${u.mustChangePassword ? ' • Chờ đổi mật khẩu' : ''}',
                          ),
                          isThreeLine: true,
                          trailing: PopupMenuButton<String>(
                            onSelected: (v) {
                              if (v == 'reset') resetPassword(u);
                              if (v == 'delete') remove(u);
                            },
                            itemBuilder: (_) => [
                              const PopupMenuItem(value: 'reset', child: Text('Reset mật khẩu')),
                              if (!u.isOwner)
                                const PopupMenuItem(value: 'delete', child: Text('Xóa user')),
                            ],
                          ),
                        ),
                      );
                    },
                  ),
      );
}

class UserEditPage extends StatefulWidget {
  const UserEditPage({super.key, this.existing});
  final AuthUser? existing;

  @override
  State<UserEditPage> createState() => _UserEditPageState();
}

class _UserEditPageState extends State<UserEditPage> {
  final api = Api();
  late final TextEditingController username;
  late final TextEditingController displayName;
  final password = TextEditingController();
  late String scope;
  late bool canEdit;
  late bool canManageUsers;
  late bool active;
  bool busy = false;
  String? error;

  bool get isNew => widget.existing == null;
  bool get isOwner => widget.existing?.isOwner == true;

  @override
  void initState() {
    super.initState();
    final u = widget.existing;
    username = TextEditingController(text: u?.username ?? '');
    displayName = TextEditingController(text: u?.displayName ?? '');
    scope = u?.scope ?? 'ALL';
    canEdit = u?.canEdit ?? false;
    canManageUsers = u?.canManageUsers ?? false;
    active = u?.active ?? true;
  }

  Future<void> save() async {
    if (username.text.trim().isEmpty) {
      setState(() => error = 'User không được để trống');
      return;
    }
    if (password.text.isNotEmpty && password.text.length < 8) {
      setState(() => error = 'Mật khẩu phải có ít nhất 8 ký tự');
      return;
    }
    setState(() {
      busy = true;
      error = null;
    });
    try {
      final r = await api.adminUpsertUser(
        username: username.text,
        displayName: displayName.text,
        scope: canManageUsers ? 'ALL' : scope,
        canEdit: isOwner ? true : canEdit,
        canManageUsers: isOwner ? true : canManageUsers,
        active: isOwner ? true : active,
        password: password.text.isEmpty ? null : password.text,
      );
      if (!mounted) return;
      if (r.temporaryPassword != null && r.temporaryPassword!.isNotEmpty) {
        await showTemporaryPassword(context, r.user.username, r.temporaryPassword!);
      }
      if (mounted) Navigator.pop(context, true);
    } catch (e) {
      setState(() => error = '$e'.replaceFirst('Exception: ', ''));
    }
    if (mounted) setState(() => busy = false);
  }

  @override
  Widget build(BuildContext context) => Scaffold(
        appBar: AppBar(title: Text(isNew ? 'Thêm user' : 'Sửa user')),
        body: ListView(
          padding: const EdgeInsets.all(16),
          children: [
            TextField(
              controller: username,
              enabled: isNew,
              decoration: const InputDecoration(labelText: 'User đăng nhập', border: OutlineInputBorder()),
            ),
            const SizedBox(height: 12),
            TextField(
              controller: displayName,
              decoration: const InputDecoration(labelText: 'Họ tên / tên hiển thị', border: OutlineInputBorder()),
            ),
            const SizedBox(height: 12),
            DropdownButtonFormField<String>(
              value: canManageUsers ? 'ALL' : scope,
              decoration: const InputDecoration(labelText: 'Phạm vi dữ liệu', border: OutlineInputBorder()),
              items: [
                const DropdownMenuItem(value: 'ALL', child: Text('ALL - Toàn bộ 6 AM')),
                ...amCodes.map((x) => DropdownMenuItem(value: x, child: Text(x))),
              ],
              onChanged: (v) => setState(() => scope = v ?? 'ALL'),
            ),
            const SizedBox(height: 6),
            SwitchListTile(
              value: isOwner ? true : canEdit,
              onChanged: isOwner ? null : (v) => setState(() => canEdit = v),
              title: const Text('Được nhập / sửa KPI'),
            ),
            SwitchListTile(
              value: isOwner ? true : canManageUsers,
              onChanged: isOwner
                  ? null
                  : (v) => setState(() {
                        canManageUsers = v;
                        if (v) scope = 'ALL';
                      }),
              title: const Text('Quản trị user & phân quyền'),
              subtitle: const Text('Bật quyền này sẽ tự động có phạm vi ALL'),
            ),
            SwitchListTile(
              value: isOwner ? true : active,
              onChanged: isOwner ? null : (v) => setState(() => active = v),
              title: const Text('Tài khoản hoạt động'),
            ),
            const SizedBox(height: 8),
            TextField(
              controller: password,
              obscureText: true,
              decoration: InputDecoration(
                labelText: isNew ? 'Mật khẩu ban đầu (để trống = tự sinh)' : 'Mật khẩu mới (để trống = giữ nguyên)',
                border: const OutlineInputBorder(),
              ),
            ),
            if (error != null) ...[
              const SizedBox(height: 12),
              Text(error!, style: const TextStyle(color: Color(0xFFE80500), fontWeight: FontWeight.w700)),
            ],
            const SizedBox(height: 18),
            FilledButton.icon(
              onPressed: busy ? null : save,
              icon: const Icon(Icons.save),
              label: Text(busy ? 'Đang lưu...' : 'Lưu user & quyền'),
            ),
          ],
        ),
      );

  @override
  void dispose() {
    username.dispose();
    displayName.dispose();
    password.dispose();
    super.dispose();
  }
}

Future<void> showTemporaryPassword(BuildContext context, String username, String password) async {
  await showDialog<void>(
    context: context,
    barrierDismissible: false,
    builder: (_) => AlertDialog(
      title: const Text('Mật khẩu tạm'),
      content: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text('User: $username'),
          const SizedBox(height: 8),
          SelectableText(password, style: const TextStyle(fontSize: 20, fontWeight: FontWeight.w900)),
          const SizedBox(height: 8),
          const Text('Người dùng sẽ phải đổi mật khẩu ngay lần đăng nhập đầu tiên.'),
        ],
      ),
      actions: [
        TextButton.icon(
          onPressed: () {
            Clipboard.setData(ClipboardData(text: password));
            ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('Đã sao chép mật khẩu')));
          },
          icon: const Icon(Icons.copy),
          label: const Text('Sao chép'),
        ),
        FilledButton(onPressed: () => Navigator.pop(context), child: const Text('Đóng')),
      ],
    ),
  );
}
