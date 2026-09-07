import 'dart:convert';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';

class AuthUser {
  const AuthUser({
    required this.username,
    required this.displayName,
    required this.scope,
    required this.canEdit,
    required this.canManageUsers,
    required this.mustChangePassword,
    required this.isOwner,
    required this.active,
  });

  final String username;
  final String displayName;
  final String scope;
  final bool canEdit;
  final bool canManageUsers;
  final bool mustChangePassword;
  final bool isOwner;
  final bool active;

  bool get viewAll => scope == 'ALL';
  bool canViewCode(String code) => viewAll || scope == code;

  String get permissionLabel {
    if (canManageUsers) return 'ADMIN';
    if (viewAll && canEdit) return 'QUẢN LÝ';
    if (viewAll) return 'XEM TOÀN KÊNH';
    return canEdit ? 'AM - NHẬP/SỬA' : 'AM - CHỈ XEM';
  }

  factory AuthUser.fromJson(Map<String, dynamic> j) => AuthUser(
        username: (j['username'] ?? '').toString(),
        displayName: (j['displayName'] ?? j['username'] ?? '').toString(),
        scope: (j['scope'] ?? '').toString(),
        canEdit: j['canEdit'] == true,
        canManageUsers: j['canManageUsers'] == true,
        mustChangePassword: j['mustChangePassword'] == true,
        isOwner: j['isOwner'] == true,
        active: j['active'] != false,
      );

  Map<String, dynamic> toJson() => {
        'username': username,
        'displayName': displayName,
        'scope': scope,
        'canEdit': canEdit,
        'canManageUsers': canManageUsers,
        'mustChangePassword': mustChangePassword,
        'isOwner': isOwner,
        'active': active,
      };
}

class AuthSession {
  const AuthSession({required this.token, required this.user});
  final String token;
  final AuthUser user;

  factory AuthSession.fromJson(Map<String, dynamic> j) => AuthSession(
        token: (j['token'] ?? '').toString(),
        user: AuthUser.fromJson((j['user'] as Map).cast<String, dynamic>()),
      );

  Map<String, dynamic> toJson() => {'token': token, 'user': user.toJson()};
}

class SessionStore {
  static const _storage = FlutterSecureStorage();
  static const _key = 'kpi_dns_auth_session_v110';
  static AuthSession? current;

  static Future<AuthSession?> restore() async {
    try {
      final raw = await _storage.read(key: _key);
      if (raw == null || raw.isEmpty) return null;
      current = AuthSession.fromJson(jsonDecode(raw) as Map<String, dynamic>);
      return current;
    } catch (_) {
      await clear();
      return null;
    }
  }

  static Future<void> save(AuthSession session) async {
    current = session;
    await _storage.write(key: _key, value: jsonEncode(session.toJson()));
  }

  static Future<void> clear() async {
    current = null;
    await _storage.delete(key: _key);
  }
}
