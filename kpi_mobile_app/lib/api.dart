import 'dart:async';
import 'dart:convert';
import 'package:http/http.dart' as http;
import 'package:shared_preferences/shared_preferences.dart';
import 'data.dart';
import 'session.dart';

const apiEndpoint = 'https://script.google.com/macros/s/AKfycbxwDT_LV1D49SKfZkv0_CfmBcRcpubbJGnd9TFBL5b1y0AHQ-a1zbRQf83CBWDeRkaApQ/exec';

class BootstrapData {
  const BootstrapData({required this.all, required this.targets});
  final Map<String, AmData> all;
  final Map<String, AmTarget> targets;
}

class AdminSaveResult {
  const AdminSaveResult({required this.user, this.temporaryPassword});
  final AuthUser user;
  final String? temporaryPassword;
}

class Api {
  static const timeout = Duration(seconds: 15);
  static const bootstrapProbeTimeout = Duration(seconds: 7);
  static final http.Client _client = http.Client();

  String get _username => SessionStore.current?.user.username ?? 'guest';
  String get _allCacheKey => 'kpi_all_v2_$_username';
  String get _targetsCacheKey => 'kpi_targets_v2_$_username';

  String _tokenOrThrow() {
    final token = SessionStore.current?.token;
    if (token == null || token.isEmpty) throw Exception('Phiên đăng nhập không hợp lệ');
    return token;
  }

  Uri uri(
    String action, [
    Map<String, String>? q,
    bool auth = true,
  ]) {
    final params = <String, String>{'action': action, ...?q};
    if (auth) params['token'] = _tokenOrThrow();
    return Uri.parse(apiEndpoint).replace(queryParameters: params);
  }

  dynamic unwrap(http.Response r) {
    if (r.statusCode < 200 || r.statusCode >= 300) {
      throw Exception('HTTP ${r.statusCode}');
    }
    final x = jsonDecode(r.body);
    if (x is! Map || x['ok'] != true) {
      if (x is Map) {
        final msg = x['message'] ?? x['error'] ?? x['code'] ?? 'Lỗi API';
        throw Exception(msg.toString());
      }
      throw Exception('Dữ liệu API không hợp lệ');
    }
    return x['data'];
  }

  Future<dynamic> _post(
    String action,
    Map<String, dynamic> body, {
    bool auth = true,
  }) async {
    final payload = <String, dynamic>{'action': action, ...body};
    if (auth) payload['token'] = _tokenOrThrow();
    final r = await _client
        .post(
          Uri.parse(apiEndpoint),
          headers: {'Content-Type': 'application/json'},
          body: jsonEncode(payload),
        )
        .timeout(timeout);
    return unwrap(r);
  }

  Future<AuthSession> login(String username, String password) async {
    final raw = await _post(
      'login',
      {'username': username.trim(), 'password': password},
      auth: false,
    );
    final session = AuthSession.fromJson((raw as Map).cast<String, dynamic>());
    await SessionStore.save(session);
    return session;
  }

  Future<AuthUser> me() async {
    final raw = unwrap(await _client.get(uri('me')).timeout(timeout));
    final user = AuthUser.fromJson((raw as Map).cast<String, dynamic>());
    final current = SessionStore.current;
    if (current != null) await SessionStore.save(AuthSession(token: current.token, user: user));
    return user;
  }

  Future<AuthSession> changePassword(String oldPassword, String newPassword) async {
    final raw = await _post('changePassword', {
      'oldPassword': oldPassword,
      'newPassword': newPassword,
    });
    final session = AuthSession.fromJson((raw as Map).cast<String, dynamic>());
    await SessionStore.save(session);
    return session;
  }

  Future<List<AuthUser>> adminUsers() async {
    final raw = unwrap(await _client.get(uri('adminUsers')).timeout(timeout));
    final list = raw is List ? raw : const [];
    return list
        .whereType<Map>()
        .map((x) => AuthUser.fromJson(x.cast<String, dynamic>()))
        .toList();
  }

  Future<AdminSaveResult> adminUpsertUser({
    required String username,
    required String displayName,
    required String scope,
    required bool canEdit,
    required bool canManageUsers,
    required bool active,
    String? password,
  }) async {
    final raw = await _post('adminUpsertUser', {
      'user': {
        'username': username.trim(),
        'displayName': displayName.trim(),
        'scope': scope,
        'canEdit': canEdit,
        'canManageUsers': canManageUsers,
        'active': active,
      },
      if (password != null && password.isNotEmpty) 'password': password,
    });
    final m = (raw as Map).cast<String, dynamic>();
    return AdminSaveResult(
      user: AuthUser.fromJson((m['user'] as Map).cast<String, dynamic>()),
      temporaryPassword: m['temporaryPassword']?.toString(),
    );
  }

  Future<void> adminDeleteUser(String username) async {
    await _post('adminDeleteUser', {'username': username});
  }

  Future<String> adminResetPassword(String username) async {
    final raw = await _post('adminResetPassword', {'username': username});
    final m = (raw as Map).cast<String, dynamic>();
    return (m['temporaryPassword'] ?? '').toString();
  }

  Map<String, AmData> _parseAll(dynamic raw) {
    final x = raw as Map;
    return x.map(
      (k, v) => MapEntry(
        k.toString(),
        AmData.fromJson(k.toString(), (v as Map).cast<String, dynamic>()),
      ),
    );
  }

  Map<String, AmTarget> _parseTargets(dynamic raw) {
    final x = raw as Map;
    final out = <String, AmTarget>{};
    for (final code in amCodes) {
      final v = x[code];
      if (v is Map) out[code] = AmTarget.fromJson(v.cast<String, dynamic>());
    }
    return out;
  }

  Future<Map<String, AmData>?> cachedAll() async {
    try {
      final p = await SharedPreferences.getInstance();
      final s = p.getString(_allCacheKey);
      if (s == null || s.isEmpty) return null;
      return _parseAll(jsonDecode(s));
    } catch (_) {
      return null;
    }
  }

  Future<Map<String, AmTarget>?> cachedTargets() async {
    try {
      final p = await SharedPreferences.getInstance();
      final s = p.getString(_targetsCacheKey);
      if (s == null || s.isEmpty) return null;
      return _parseTargets(jsonDecode(s));
    } catch (_) {
      return null;
    }
  }

  Future<BootstrapData?> cachedBootstrap() async {
    final r = await Future.wait([cachedAll(), cachedTargets()]);
    final a = r[0] as Map<String, AmData>?;
    final t = r[1] as Map<String, AmTarget>?;
    if (a == null || t == null || a.isEmpty || t.isEmpty) return null;
    return BootstrapData(all: a, targets: t);
  }

  Future<Map<String, AmData>> all() async {
    final r = await _client.get(uri('getAllAmData')).timeout(timeout);
    final raw = unwrap(r);
    unawaited(_saveCache(_allCacheKey, raw));
    return _parseAll(raw);
  }

  Future<Map<String, AmTarget>> targets() async {
    final r = await _client.get(uri('getTargets')).timeout(timeout);
    final raw = unwrap(r);
    unawaited(_saveCache(_targetsCacheKey, raw));
    return _parseTargets(raw);
  }

  Future<BootstrapData> bootstrap({bool probeBootstrap = true}) async {
    if (probeBootstrap) {
      try {
        final r = await _client.get(uri('bootstrap')).timeout(bootstrapProbeTimeout);
        final raw = unwrap(r) as Map;
        final allRaw = raw['all'];
        final targetsRaw = raw['targets'];
        if (allRaw is Map && targetsRaw is Map) {
          unawaited(_saveCache(_allCacheKey, allRaw));
          unawaited(_saveCache(_targetsCacheKey, targetsRaw));
          return BootstrapData(
            all: _parseAll(allRaw),
            targets: _parseTargets(targetsRaw),
          );
        }
      } catch (_) {
        // Fallback an toàn xuống 2 API song song.
      }
    }

    final r = await Future.wait([all(), targets()]);
    return BootstrapData(
      all: r[0] as Map<String, AmData>,
      targets: r[1] as Map<String, AmTarget>,
    );
  }

  Future<void> _saveCache(String key, dynamic raw) async {
    try {
      final p = await SharedPreferences.getInstance();
      await p.setString(key, jsonEncode(raw));
    } catch (_) {
      // Cache không được ảnh hưởng dữ liệu thật.
    }
  }

  Future<AmData> one(String code) async {
    final x = unwrap(
      await _client.get(uri('getAmData', {'amCode': code})).timeout(timeout),
    );
    return AmData.fromJson(code, (x as Map).cast<String, dynamic>());
  }

  Future<void> save(String code, DateTime date, List<double?> values) async {
    if (SessionStore.current?.user.canEdit != true) {
      throw Exception('Tài khoản không có quyền nhập/sửa KPI');
    }
    final ds =
        '${date.year.toString().padLeft(4, '0')}-${date.month.toString().padLeft(2, '0')}-${date.day.toString().padLeft(2, '0')}';
    await _post('saveEntry', {
      'amCode': code,
      'dateStr': ds,
      'values': values,
    });

    final refreshed = await one(code);
    final row = refreshed.daily[ds];
    if (row == null) throw Exception('Không đọc lại được ngày $ds');
    for (var i = 0; i < 9; i++) {
      final e = values[i];
      if (e == null) continue;
      final g = row[i];
      if (g == null || (g - e).abs() > 0.000001) {
        throw Exception('Xác minh ghi KPI ${i + 1} thất bại');
      }
    }
  }
}
