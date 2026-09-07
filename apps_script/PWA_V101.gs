/*
 * KPI DNS Cao Bang - PWA V1.0.1 - Phuong an B
 * Dung cung Google Apps Script Web App hien tai.
 * - Cache-first nam o client (localStorage)
 * - 1 lan goi pwaBootstrapV101() de lay toan bo 6 AM + chi tieu
 * - Ghi KPI giu dung quy tac blank != 0
 */

var PWA_V101_ = {
  AM_CODES: ['HOAIBT4', 'NUONGPM', 'THAODP7', 'LANHT22', 'HUEHT16', 'QUYENLTN'],
  DAILY_START_ROW: 23,
  DAILY_COLS: [2, 4, 6, 8, 10, 12, 14, 16, 18],
  KPI_COUNT: 9
};

function servePwaV101_() {
  return HtmlService.createHtmlOutputFromFile('PWA_V101')
    .setTitle('KPI DNS Cao Bang')
    .addMetaTag('viewport', 'width=device-width, initial-scale=1, maximum-scale=1, viewport-fit=cover')
    .setXFrameOptionsMode(HtmlService.XFrameOptionsMode.ALLOWALL);
}

function pwaBootstrapV101() {
  var all = {};
  PWA_V101_.AM_CODES.forEach(function(code) {
    all[code] = readAmSheet_(code);
  });

  return {
    ok: true,
    data: {
      all: all,
      targets: getTargets(),
      serverTime: new Date().toISOString()
    }
  };
}

function pwaGetAmV101(amCode) {
  amCode = String(amCode || '').trim();
  if (PWA_V101_.AM_CODES.indexOf(amCode) < 0) {
    throw new Error('Ma AM khong hop le');
  }
  return { ok: true, data: readAmSheet_(amCode) };
}

function pwaSaveEntryV101(amCode, dateStr, values) {
  amCode = String(amCode || '').trim();
  dateStr = String(dateStr || '').trim();

  if (PWA_V101_.AM_CODES.indexOf(amCode) < 0) {
    throw new Error('Ma AM khong hop le');
  }
  if (!/^\d{4}-\d{2}-\d{2}$/.test(dateStr)) {
    throw new Error('Ngay phai co dang YYYY-MM-DD');
  }
  if (!Array.isArray(values) || values.length !== PWA_V101_.KPI_COUNT) {
    throw new Error('Du lieu phai co dung 9 KPI');
  }

  var parts = dateStr.split('-').map(Number);
  var year = parts[0];
  var month = parts[1];
  var day = parts[2];
  var now = new Date();

  if (year !== now.getFullYear() || month !== now.getMonth() + 1) {
    throw new Error('Chi cho phep nhap du lieu trong thang hien tai');
  }

  var maxDay = new Date(year, month, 0).getDate();
  if (day < 1 || day > maxDay) throw new Error('Ngay khong hop le');

  var sh = SpreadsheetApp.getActiveSpreadsheet().getSheetByName(amCode);
  if (!sh) throw new Error('Khong tim thay Sheet AM: ' + amCode);

  var row = PWA_V101_.DAILY_START_ROW + day - 1;
  var changed = [];

  PWA_V101_.DAILY_COLS.forEach(function(col, i) {
    var raw = values[i];
    // Blank/null = khong ghi de tranh ghi de du lieu cu.
    if (raw === null || raw === undefined || raw === '') return;

    var n = Number(raw);
    if (!isFinite(n) || n < 0) {
      throw new Error('Gia tri KPI ' + (i + 1) + ' khong hop le');
    }

    // So 0 la gia tri hop le va bat buoc phai ghi.
    sh.getRange(row, col).setValue(n);
    changed.push((i + 1) + '=' + n);
  });

  SpreadsheetApp.flush();

  var refreshed = readAmSheet_(amCode);
  return {
    ok: true,
    data: {
      amCode: amCode,
      dateStr: dateStr,
      changed: changed,
      amData: refreshed
    }
  };
}
