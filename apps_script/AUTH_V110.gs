/*
 * KPI DNS Viettel Cao Bằng - AUTH V1.1.0
 * Đăng nhập + phân quyền + quản trị user.
 *
 * Cách dùng:
 * 1) Tạo file AUTH_V110.gs trong Apps Script và dán toàn bộ nội dung này.
 * 2) Thay doGet/doPost trong Mã.gs bằng 2 wrapper ở file ROUTER_V110.gs.
 * 3) Chạy setupAuthV110() đúng 1 lần để tạo user ADMIN đầu tiên.
 * 4) Xem Nhật ký thực thi để lấy mật khẩu tạm của user admin.
 * 5) Triển khai phiên bản Web App mới.
 */

var AUTH_V110_ = {
  USERS_SHEET: 'USERS',
  AUDIT_SHEET: 'AUDIT_LOG',
  TOKEN_HOURS: 12,
  USER_CACHE_SECONDS: 30,
  AM_CODES: ['HOAIBT4', 'NUONGPM', 'THAODP7', 'LANHT22', 'HUEHT16', 'QUYENLTN'],
  DAILY_START_ROW: 23,
  DAILY_COLS: [2,4,6,8,10,12,14,16,18],
  KPI_COUNT: 9
};

var AUTH_USER_HEADERS_ = [
  'username', 'displayName', 'salt', 'passwordHash', 'scope',
  'canEdit', 'canManageUsers', 'active', 'mustChangePassword', 'isOwner',
  'tokenVersion', 'createdAt', 'updatedAt', 'updatedBy'
];

function setupAuthV110() {
  var ss = SpreadsheetApp.getActiveSpreadsheet();
  if (!ss) throw new Error('Không tìm thấy Spreadsheet đang gắn Apps Script');

  authEnsureSecrets_();
  var users = authEnsureUsersSheet_();
  authEnsureAuditSheet_();

  var existing = authFindUser_('admin');
  if (existing) {
    Logger.log('AUTH V1.1.0 đã được thiết lập. User admin đã tồn tại.');
    return { ok: true, username: 'admin', alreadyExists: true };
  }

  var temp = authGeneratePassword_();
  var salt = Utilities.getUuid().replace(/-/g, '');
  var now = new Date();
  users.appendRow([
    'admin',
    'Quản trị hệ thống',
    salt,
    authHashPassword_(temp, salt),
    'ALL',
    true,
    true,
    true,
    true,
    true,
    1,
    now,
    now,
    'SYSTEM'
  ]);
  authInvalidateUsers_();
  try { users.hideColumns(3, 2); } catch (_) {}

  authAudit_('SYSTEM', 'SETUP_AUTH', 'admin', 'Tạo tài khoản owner admin');
  Logger.log('===== KPI DNS AUTH V1.1.0 =====');
  Logger.log('ADMIN USER: admin');
  Logger.log('MẬT KHẨU TẠM: ' + temp);
  Logger.log('Đăng nhập app và đổi mật khẩu ngay lần đầu.');
  return { ok: true, username: 'admin', temporaryPassword: temp };
}

function resetOwnerPasswordV110() {
  var owner = authFindOwner_();
  if (!owner) throw new Error('Chưa có owner. Hãy chạy setupAuthV110() trước.');
  var temp = authGeneratePassword_();
  authSetPasswordForRecord_(owner, temp, true, 'SYSTEM_RESET');
  Logger.log('OWNER USER: ' + owner.username);
  Logger.log('MẬT KHẨU TẠM MỚI: ' + temp);
  return { ok: true, username: owner.username, temporaryPassword: temp };
}

function authRouterGetV110_(e) {
  try {
    var action = e && e.parameter ? String(e.parameter.action || '') : '';

    // Giữ nguyên Web App cũ khi không có action.
    if (!action) {
      return HtmlService.createHtmlOutput(APP_HTML)
        .setTitle('KPI SME - Viettel Cao Bằng')
        .addMetaTag('viewport', 'width=device-width, initial-scale=1, maximum-scale=1');
    }

    var token = e && e.parameter ? String(e.parameter.token || '') : '';
    var user = authRequireToken_(token);

    if (action === 'me') {
      return authJsonOk_(authSafeUser_(user));
    }

    if (action === 'adminUsers') {
      authRequireManageUsers_(user);
      var list = authLoadUsers_().map(function(x) { return authSafeUser_(x); });
      return authJsonOk_(list);
    }

    if (action === 'bootstrap') {
      var codes = authAllowedCodes_(user);
      var allData = {};
      codes.forEach(function(code) { allData[code] = readAmSheet_(code); });
      return authJsonOk_({
        all: allData,
        targets: authFilterTargets_(getTargets(), codes)
      });
    }

    if (action === 'getTargets') {
      var targetCodes = authAllowedCodes_(user);
      return authJsonOk_(authFilterTargets_(getTargets(), targetCodes));
    }

    if (action === 'getAllAmData') {
      var allCodes = authAllowedCodes_(user);
      var result = {};
      allCodes.forEach(function(code) { result[code] = readAmSheet_(code); });
      return authJsonOk_(result);
    }

    if (action === 'getAmData') {
      var amCode = String(e.parameter.amCode || '').trim();
      authRequireAmScope_(user, amCode);
      return authJsonOk_(readAmSheet_(amCode));
    }

    throw new Error('Action GET không hợp lệ: ' + action);
  } catch (err) {
    return authJsonError_(err);
  }
}

function authRouterPostV110_(e) {
  try {
    var body = JSON.parse((e && e.postData && e.postData.contents) || '{}');
    var action = String(body.action || '');

    if (action === 'login') {
      return authJsonOk_(authLogin_(body));
    }

    var user = authRequireToken_(String(body.token || ''));

    if (action === 'changePassword') {
      return authJsonOk_(authChangePassword_(user, body));
    }

    if (action === 'adminUpsertUser') {
      authRequireManageUsers_(user);
      return authJsonOk_(authAdminUpsertUser_(user, body));
    }

    if (action === 'adminDeleteUser') {
      authRequireManageUsers_(user);
      return authJsonOk_(authAdminDeleteUser_(user, body));
    }

    if (action === 'adminResetPassword') {
      authRequireManageUsers_(user);
      return authJsonOk_(authAdminResetPassword_(user, body));
    }

    if (action === 'saveEntry') {
      return authJsonOk_(authSaveEntry_(user, body));
    }

    throw new Error('Action POST không hợp lệ: ' + action);
  } catch (err) {
    return authJsonError_(err);
  }
}

function authLogin_(body) {
  var username = authNormalizeUsername_(body.username);
  var password = String(body.password || '');
  if (!username || !password) throw new Error('Sai user hoặc mật khẩu');

  var cache = CacheService.getScriptCache();
  var failKey = 'AUTH_FAIL_' + username;
  var failCount = Number(cache.get(failKey) || 0);
  if (failCount >= 8) throw new Error('Tài khoản tạm khóa đăng nhập 5 phút do nhập sai nhiều lần');

  var user = authFindUser_(username);
  if (!user || !user.active || !authSecureEqual_(authHashPassword_(password, user.salt), user.passwordHash)) {
    cache.put(failKey, String(failCount + 1), 300);
    authAudit_(username || 'UNKNOWN', 'LOGIN_FAIL', username || '-', 'Đăng nhập thất bại');
    throw new Error('Sai user hoặc mật khẩu');
  }

  cache.remove(failKey);
  var token = authIssueToken_(user);
  authAudit_(user.username, 'LOGIN_OK', user.username, 'Đăng nhập thành công');
  return { token: token, user: authSafeUser_(user) };
}

function authChangePassword_(user, body) {
  var oldPassword = String(body.oldPassword || '');
  var newPassword = String(body.newPassword || '');
  if (!authSecureEqual_(authHashPassword_(oldPassword, user.salt), user.passwordHash)) {
    throw new Error('Mật khẩu hiện tại không đúng');
  }
  authValidatePassword_(newPassword);

  var lock = LockService.getScriptLock();
  lock.waitLock(10000);
  try {
    var fresh = authFindUserNoCache_(user.username);
    if (!fresh) throw new Error('Không tìm thấy user');
    var salt = Utilities.getUuid().replace(/-/g, '');
    var row = authRecordToRow_(fresh);
    row[2] = salt;
    row[3] = authHashPassword_(newPassword, salt);
    row[8] = false;
    row[10] = Number(fresh.tokenVersion || 0) + 1;
    row[12] = new Date();
    row[13] = user.username;
    authUsersSheet_().getRange(fresh._row, 1, 1, row.length).setValues([row]);
    authInvalidateUsers_();
  } finally {
    lock.releaseLock();
  }

  var updated = authFindUser_(user.username);
  var token = authIssueToken_(updated);
  authAudit_(user.username, 'CHANGE_PASSWORD', user.username, 'Đổi mật khẩu');
  return { token: token, user: authSafeUser_(updated) };
}

function authAdminUpsertUser_(admin, body) {
  var input = body.user || {};
  var username = authNormalizeUsername_(input.username);
  if (!/^[A-Za-z0-9._-]{3,40}$/.test(username)) {
    throw new Error('User chỉ gồm chữ, số, dấu . _ - và dài 3-40 ký tự');
  }
  var displayName = String(input.displayName || username).trim() || username;
  var scope = String(input.scope || 'ALL').trim().toUpperCase();
  var canEdit = input.canEdit === true;
  var canManageUsers = input.canManageUsers === true;
  var active = input.active !== false;
  if (canManageUsers) scope = 'ALL';
  if (scope !== 'ALL' && AUTH_V110_.AM_CODES.indexOf(scope) < 0) throw new Error('Phạm vi dữ liệu không hợp lệ');

  var suppliedPassword = String(body.password || '');
  if (suppliedPassword) authValidatePassword_(suppliedPassword);

  var lock = LockService.getScriptLock();
  lock.waitLock(10000);
  var temporaryPassword = null;
  try {
    var current = authFindUserNoCache_(username);
    var now = new Date();

    if (!current) {
      var initialPassword = suppliedPassword || authGeneratePassword_();
      if (!suppliedPassword) temporaryPassword = initialPassword;
      var salt = Utilities.getUuid().replace(/-/g, '');
      authUsersSheet_().appendRow([
        username,
        displayName,
        salt,
        authHashPassword_(initialPassword, salt),
        scope,
        canEdit,
        canManageUsers,
        active,
        true,
        false,
        1,
        now,
        now,
        admin.username
      ]);
      authAudit_(admin.username, 'USER_CREATE', username, 'scope=' + scope + '; edit=' + canEdit + '; manage=' + canManageUsers);
    } else {
      var row = authRecordToRow_(current);
      if (current.isOwner) {
        scope = 'ALL';
        canEdit = true;
        canManageUsers = true;
        active = true;
      }
      row[1] = displayName;
      row[4] = scope;
      row[5] = canEdit;
      row[6] = canManageUsers;
      row[7] = active;
      if (suppliedPassword) {
        var newSalt = Utilities.getUuid().replace(/-/g, '');
        row[2] = newSalt;
        row[3] = authHashPassword_(suppliedPassword, newSalt);
        row[8] = true;
      }
      row[10] = Number(current.tokenVersion || 0) + 1;
      row[12] = now;
      row[13] = admin.username;
      authUsersSheet_().getRange(current._row, 1, 1, row.length).setValues([row]);
      authAudit_(admin.username, 'USER_UPDATE', username, 'scope=' + scope + '; edit=' + canEdit + '; manage=' + canManageUsers + '; active=' + active);
    }
    authInvalidateUsers_();
  } finally {
    lock.releaseLock();
  }

  var updated = authFindUser_(username);
  return { user: authSafeUser_(updated), temporaryPassword: temporaryPassword };
}

function authAdminDeleteUser_(admin, body) {
  var username = authNormalizeUsername_(body.username);
  var target = authFindUser_(username);
  if (!target) throw new Error('Không tìm thấy user');
  if (target.isOwner) throw new Error('Không thể xóa tài khoản owner');
  if (target.username === admin.username) throw new Error('Không thể tự xóa tài khoản đang đăng nhập');

  var lock = LockService.getScriptLock();
  lock.waitLock(10000);
  try {
    var fresh = authFindUserNoCache_(username);
    if (!fresh) throw new Error('User đã bị xóa');
    authUsersSheet_().deleteRow(fresh._row);
    authInvalidateUsers_();
  } finally {
    lock.releaseLock();
  }
  authAudit_(admin.username, 'USER_DELETE', username, 'Xóa user');
  return { username: username, deleted: true };
}

function authAdminResetPassword_(admin, body) {
  var username = authNormalizeUsername_(body.username);
  var target = authFindUser_(username);
  if (!target) throw new Error('Không tìm thấy user');
  var temp = authGeneratePassword_();
  authSetPasswordForRecord_(target, temp, true, admin.username);
  authAudit_(admin.username, 'PASSWORD_RESET', username, 'Reset mật khẩu tạm');
  return { username: username, temporaryPassword: temp };
}

function authSaveEntry_(user, body) {
  if (!user.canEdit) throw new Error('Tài khoản không có quyền nhập/sửa KPI');

  var amCode = String(body.amCode || '').trim();
  authRequireAmScope_(user, amCode);
  var dateStr = String(body.dateStr || '').trim();
  var values = Array.isArray(body.values) ? body.values : [];
  if (!/^\d{4}-\d{2}-\d{2}$/.test(dateStr)) throw new Error('Ngày phải có dạng YYYY-MM-DD');
  if (values.length !== AUTH_V110_.KPI_COUNT) throw new Error('values phải có đúng 9 phần tử');

  var parts = dateStr.split('-').map(Number);
  var year = parts[0], month = parts[1], day = parts[2];
  var now = new Date();
  if (year !== now.getFullYear() || month !== now.getMonth() + 1) {
    throw new Error('Chỉ cho phép nhập dữ liệu trong tháng hiện tại');
  }
  var maxDay = new Date(year, month, 0).getDate();
  if (day < 1 || day > maxDay) throw new Error('Ngày không hợp lệ');

  var sh = SpreadsheetApp.getActiveSpreadsheet().getSheetByName(amCode);
  if (!sh) throw new Error('Không tìm thấy Sheet AM: ' + amCode);
  var row = AUTH_V110_.DAILY_START_ROW + day - 1;
  var changed = [];
  AUTH_V110_.DAILY_COLS.forEach(function(col, i) {
    var raw = values[i];
    if (raw === null || raw === undefined || raw === '') return;
    var n = Number(raw);
    if (!isFinite(n) || n < 0) throw new Error('Giá trị KPI ' + (i + 1) + ' không hợp lệ');
    sh.getRange(row, col).setValue(n);
    changed.push((i + 1) + '=' + n);
  });
  SpreadsheetApp.flush();
  authAudit_(user.username, 'KPI_SAVE', amCode + ':' + dateStr, changed.join('; '));
  return { amCode: amCode, dateStr: dateStr };
}

function authRequireToken_(token) {
  if (!token) throw new Error('AUTH_REQUIRED: Vui lòng đăng nhập');
  var parts = String(token).split('.');
  if (parts.length !== 2) throw new Error('AUTH_REQUIRED: Phiên đăng nhập không hợp lệ');

  var payloadText;
  try {
    payloadText = Utilities.newBlob(Utilities.base64DecodeWebSafe(parts[0])).getDataAsString();
  } catch (_) {
    throw new Error('AUTH_REQUIRED: Phiên đăng nhập không hợp lệ');
  }

  var expected = authSignTokenPayload_(parts[0]);
  if (!authSecureEqual_(expected, parts[1])) throw new Error('AUTH_REQUIRED: Chữ ký phiên không hợp lệ');

  var payload;
  try { payload = JSON.parse(payloadText); } catch (_) { throw new Error('AUTH_REQUIRED: Phiên đăng nhập không hợp lệ'); }
  if (!payload.u || Number(payload.exp || 0) < Date.now()) throw new Error('AUTH_REQUIRED: Phiên đã hết hạn');

  var user = authFindUser_(payload.u);
  if (!user || !user.active) throw new Error('AUTH_REQUIRED: Tài khoản đã bị khóa hoặc xóa');
  if (Number(user.tokenVersion || 0) !== Number(payload.v || 0)) throw new Error('AUTH_REQUIRED: Quyền hoặc mật khẩu đã thay đổi, vui lòng đăng nhập lại');
  return user;
}

function authIssueToken_(user) {
  authEnsureSecrets_();
  var payload = JSON.stringify({
    u: user.username,
    exp: Date.now() + AUTH_V110_.TOKEN_HOURS * 60 * 60 * 1000,
    v: Number(user.tokenVersion || 0),
    n: Utilities.getUuid().slice(0, 12)
  });
  var payloadB64 = authB64Url_(Utilities.newBlob(payload).getBytes());
  return payloadB64 + '.' + authSignTokenPayload_(payloadB64);
}

function authSignTokenPayload_(payloadB64) {
  var secret = authEnsureSecrets_().token;
  return authB64Url_(Utilities.computeHmacSha256Signature(payloadB64, secret));
}

function authHashPassword_(password, salt) {
  var secret = authEnsureSecrets_().pepper;
  return authB64Url_(Utilities.computeHmacSha256Signature(String(password) + ':' + String(salt), secret));
}

function authEnsureSecrets_() {
  var p = PropertiesService.getScriptProperties();
  var pepper = p.getProperty('AUTH_PEPPER_V110');
  var token = p.getProperty('AUTH_TOKEN_SECRET_V110');
  if (!pepper) {
    pepper = Utilities.getUuid() + Utilities.getUuid();
    p.setProperty('AUTH_PEPPER_V110', pepper);
  }
  if (!token) {
    token = Utilities.getUuid() + Utilities.getUuid();
    p.setProperty('AUTH_TOKEN_SECRET_V110', token);
  }
  return { pepper: pepper, token: token };
}

function authValidatePassword_(password) {
  if (String(password || '').length < 8) throw new Error('Mật khẩu phải có ít nhất 8 ký tự');
}

function authGeneratePassword_() {
  var raw = Utilities.getUuid().replace(/-/g, '') + 'Aa9!';
  return raw.slice(0, 14);
}

function authNormalizeUsername_(v) {
  return String(v || '').trim().toLowerCase();
}

function authAllowedCodes_(user) {
  if (user.scope === 'ALL') return AUTH_V110_.AM_CODES.slice();
  if (AUTH_V110_.AM_CODES.indexOf(user.scope) >= 0) return [user.scope];
  return [];
}

function authRequireAmScope_(user, amCode) {
  if (AUTH_V110_.AM_CODES.indexOf(amCode) < 0) throw new Error('Mã AM không hợp lệ');
  if (user.scope !== 'ALL' && user.scope !== amCode) throw new Error('PERMISSION_DENIED: Không có quyền xem/sửa AM ' + amCode);
}

function authRequireManageUsers_(user) {
  if (!user.canManageUsers) throw new Error('PERMISSION_DENIED: Không có quyền quản trị user');
}

function authFilterTargets_(targets, codes) {
  var out = {};
  codes.forEach(function(code) {
    if (targets && targets[code] !== undefined) out[code] = targets[code];
  });
  return out;
}

function authSafeUser_(u) {
  return {
    username: u.username,
    displayName: u.displayName,
    scope: u.scope,
    canEdit: !!u.canEdit,
    canManageUsers: !!u.canManageUsers,
    active: !!u.active,
    mustChangePassword: !!u.mustChangePassword,
    isOwner: !!u.isOwner
  };
}

function authEnsureUsersSheet_() {
  var ss = SpreadsheetApp.getActiveSpreadsheet();
  var sh = ss.getSheetByName(AUTH_V110_.USERS_SHEET);
  if (!sh) sh = ss.insertSheet(AUTH_V110_.USERS_SHEET);
  if (sh.getLastRow() === 0) sh.appendRow(AUTH_USER_HEADERS_);
  else {
    var existing = sh.getRange(1, 1, 1, AUTH_USER_HEADERS_.length).getValues()[0];
    if (String(existing[0] || '') !== 'username') {
      sh.insertRowBefore(1);
      sh.getRange(1, 1, 1, AUTH_USER_HEADERS_.length).setValues([AUTH_USER_HEADERS_]);
    }
  }
  sh.setFrozenRows(1);
  return sh;
}

function authEnsureAuditSheet_() {
  var ss = SpreadsheetApp.getActiveSpreadsheet();
  var sh = ss.getSheetByName(AUTH_V110_.AUDIT_SHEET);
  if (!sh) sh = ss.insertSheet(AUTH_V110_.AUDIT_SHEET);
  if (sh.getLastRow() === 0) sh.appendRow(['timestamp', 'username', 'action', 'target', 'details']);
  sh.setFrozenRows(1);
  return sh;
}

function authUsersSheet_() {
  return authEnsureUsersSheet_();
}

function authLoadUsers_() {
  var cache = CacheService.getScriptCache();
  var cached = cache.get('AUTH_USERS_V110');
  if (cached) {
    try { return JSON.parse(cached); } catch (_) {}
  }
  var list = authLoadUsersNoCache_();
  try { cache.put('AUTH_USERS_V110', JSON.stringify(list), AUTH_V110_.USER_CACHE_SECONDS); } catch (_) {}
  return list;
}

function authLoadUsersNoCache_() {
  var sh = authUsersSheet_();
  var last = sh.getLastRow();
  if (last < 2) return [];
  var rows = sh.getRange(2, 1, last - 1, AUTH_USER_HEADERS_.length).getValues();
  return rows.map(function(r, i) { return authRowToRecord_(r, i + 2); }).filter(function(x) { return !!x.username; });
}

function authFindUser_(username) {
  username = authNormalizeUsername_(username);
  var list = authLoadUsers_();
  for (var i = 0; i < list.length; i++) if (list[i].username === username) return list[i];
  return null;
}

function authFindUserNoCache_(username) {
  username = authNormalizeUsername_(username);
  var list = authLoadUsersNoCache_();
  for (var i = 0; i < list.length; i++) if (list[i].username === username) return list[i];
  return null;
}

function authFindOwner_() {
  var list = authLoadUsers_();
  for (var i = 0; i < list.length; i++) if (list[i].isOwner) return list[i];
  return null;
}

function authRowToRecord_(r, rowNum) {
  return {
    _row: rowNum,
    username: authNormalizeUsername_(r[0]),
    displayName: String(r[1] || ''),
    salt: String(r[2] || ''),
    passwordHash: String(r[3] || ''),
    scope: String(r[4] || '').toUpperCase(),
    canEdit: authBool_(r[5]),
    canManageUsers: authBool_(r[6]),
    active: authBool_(r[7]),
    mustChangePassword: authBool_(r[8]),
    isOwner: authBool_(r[9]),
    tokenVersion: Number(r[10] || 0),
    createdAt: r[11],
    updatedAt: r[12],
    updatedBy: String(r[13] || '')
  };
}

function authRecordToRow_(x) {
  return [
    x.username, x.displayName, x.salt, x.passwordHash, x.scope,
    !!x.canEdit, !!x.canManageUsers, !!x.active, !!x.mustChangePassword, !!x.isOwner,
    Number(x.tokenVersion || 0), x.createdAt || new Date(), x.updatedAt || new Date(), x.updatedBy || ''
  ];
}

function authSetPasswordForRecord_(record, password, mustChange, updatedBy) {
  authValidatePassword_(password);
  var lock = LockService.getScriptLock();
  lock.waitLock(10000);
  try {
    var fresh = authFindUserNoCache_(record.username);
    if (!fresh) throw new Error('Không tìm thấy user');
    var row = authRecordToRow_(fresh);
    var salt = Utilities.getUuid().replace(/-/g, '');
    row[2] = salt;
    row[3] = authHashPassword_(password, salt);
    row[8] = !!mustChange;
    row[10] = Number(fresh.tokenVersion || 0) + 1;
    row[12] = new Date();
    row[13] = updatedBy || 'SYSTEM';
    authUsersSheet_().getRange(fresh._row, 1, 1, row.length).setValues([row]);
    authInvalidateUsers_();
  } finally {
    lock.releaseLock();
  }
}

function authInvalidateUsers_() {
  try { CacheService.getScriptCache().remove('AUTH_USERS_V110'); } catch (_) {}
}

function authAudit_(username, action, target, details) {
  try {
    authEnsureAuditSheet_().appendRow([new Date(), username, action, target, details || '']);
  } catch (_) {}
}

function authBool_(v) {
  return v === true || v === 1 || String(v).toLowerCase() === 'true';
}

function authB64Url_(bytes) {
  return Utilities.base64EncodeWebSafe(bytes).replace(/=+$/g, '');
}

function authSecureEqual_(a, b) {
  a = String(a || '');
  b = String(b || '');
  if (a.length !== b.length) return false;
  var diff = 0;
  for (var i = 0; i < a.length; i++) diff |= a.charCodeAt(i) ^ b.charCodeAt(i);
  return diff === 0;
}

function authJsonOk_(data) {
  return ContentService.createTextOutput(JSON.stringify({ ok: true, data: data }))
    .setMimeType(ContentService.MimeType.JSON);
}

function authJsonError_(err) {
  var msg = err && err.message ? String(err.message) : String(err);
  var code = msg.indexOf('AUTH_REQUIRED') === 0 ? 'AUTH_REQUIRED' :
             msg.indexOf('PERMISSION_DENIED') === 0 ? 'PERMISSION_DENIED' : 'ERROR';
  return ContentService.createTextOutput(JSON.stringify({ ok: false, code: code, error: msg, message: msg }))
    .setMimeType(ContentService.MimeType.JSON);
}
