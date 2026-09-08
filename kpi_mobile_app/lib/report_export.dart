import 'dart:io';
import 'dart:math' as math;
import 'dart:typed_data';
import 'dart:ui' as ui;

import 'package:archive/archive.dart';
import 'package:cross_file/cross_file.dart';
import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import 'package:path_provider/path_provider.dart';
import 'package:share_plus/share_plus.dart';

import 'data.dart';

class DailyKpiReportExporter {
  static const _red = Color(0xFFE80500);
  static const _darkRed = Color(0xFF9E1015);
  static const _pink = Color(0xFFFFF0F0);
  static const _yellow = Color(0xFFFFF4C8);
  static const _green = Color(0xFFE8F4DF);
  static const _gray = Color(0xFF596169);
  static const _lightGray = Color(0xFFF2F3F4);

  static final NumberFormat _nf = NumberFormat('#,##0', 'vi_VN');

  static Future<File> exportAndShare({
    required Map<String, AmData> all,
    required Map<String, AmTarget> targets,
    required DateTime reportDate,
  }) async {
    final date = DateTime(reportDate.year, reportDate.month, reportDate.day);
    final missingData = amCodes.where((x) => !all.containsKey(x)).toList();
    final missingTarget = amCodes.where((x) => !targets.containsKey(x)).toList();
    if (missingData.isNotEmpty || missingTarget.isNotEmpty) {
      throw Exception(
        'Thiếu dữ liệu báo cáo: '
        '${missingData.isNotEmpty ? 'AM ${missingData.join(', ')} ' : ''}'
        '${missingTarget.isNotEmpty ? 'chỉ tiêu ${missingTarget.join(', ')}' : ''}',
      );
    }

    final model = _ReportModel(all: all, targets: targets, reportDate: date);
    final temp = await getTemporaryDirectory();
    final stamp = DateFormat('dd-MM-yyyy').format(date);
    final dir = Directory('${temp.path}/KPI_ngay_$stamp');
    if (await dir.exists()) await dir.delete(recursive: true);
    await dir.create(recursive: true);

    final files = <File>[];

    final xlsx = File('${dir.path}/Bao_cao_KQ_HoanThanh_KPI_ngay_$stamp.xlsx');
    await xlsx.writeAsBytes(_buildXlsx(model), flush: true);
    files.add(xlsx);

    final overall = File('${dir.path}/KetQuaKPI_ngay_$stamp.png');
    await overall.writeAsBytes(await _buildOverallPng(model), flush: true);
    files.add(overall);

    for (final code in amCodes) {
      final f = File('${dir.path}/ChiTieu_${stamp}_$code.png');
      await f.writeAsBytes(await _buildAmPng(model, code), flush: true);
      files.add(f);
    }

    final archive = Archive();
    for (final f in files) {
      final bytes = await f.readAsBytes();
      archive.addFile(ArchiveFile(f.uri.pathSegments.last, bytes.length, bytes));
    }
    final zipBytes = ZipEncoder().encode(archive);
    if (zipBytes == null) throw Exception('Không tạo được file ZIP');

    final zip = File('${temp.path}/KPI_ngay_$stamp.zip');
    await zip.writeAsBytes(zipBytes, flush: true);

    await Share.shareXFiles(
      [XFile(zip.path)],
      subject: 'Báo cáo KPI ngày ${DateFormat('dd/MM/yyyy').format(date)}',
      text: 'Báo cáo KPI ngày ${DateFormat('dd/MM/yyyy').format(date)} - Kênh KHDN Viettel Cao Bằng',
    );
    return zip;
  }

  static Uint8List _buildXlsx(_ReportModel m) {
    final sheet1 = <_XlsxRow>[];
    sheet1.add(_XlsxRow(1, [
      _XlsxCell(1, 'BÁO CÁO KẾT QUẢ HOÀN THÀNH KPI NGÀY - KÊNH KHDN - VIETTEL CAO BẰNG', style: 1),
    ]));
    sheet1.add(_XlsxRow(2, [_XlsxCell(1, 'Tháng áp dụng:', style: 6), _XlsxCell(2, '${m.reportDate.month}/${m.reportDate.year}')]));
    sheet1.add(_XlsxRow(3, [_XlsxCell(1, 'Ngày báo cáo:', style: 6), _XlsxCell(2, m.reportDateText)]));
    sheet1.add(_XlsxRow(4, [_XlsxCell(1, 'TH lũy kế đến:', style: 6), _XlsxCell(2, m.closedDateText)]));
    sheet1.add(_XlsxRow(5, [_XlsxCell(1, 'Tuần áp dụng:', style: 6), _XlsxCell(2, 'Tuần ${m.week.index + 1}')]));
    sheet1.add(_XlsxRow(7, [
      _XlsxCell(1, 'STT', style: 3),
      _XlsxCell(2, 'Chỉ tiêu', style: 3),
      _XlsxCell(3, 'Đơn vị', style: 3),
      _XlsxCell(4, 'KH tháng (toàn tỉnh)', style: 3),
      _XlsxCell(5, 'TH lũy kế (toàn tỉnh)', style: 3),
      _XlsxCell(6, '%HT', style: 3),
      _XlsxCell(7, 'Tiến độ', style: 3),
    ]));
    for (var i = 0; i < 9; i++) {
      final ratio = m.totalMonthKh[i] <= 0 ? 1.0 : m.totalMonthTh[i] / m.totalMonthKh[i];
      sheet1.add(_XlsxRow(8 + i, [
        _XlsxCell(1, i + 1),
        _XlsxCell(2, metrics[i].name),
        _XlsxCell(3, _unit(metrics[i].unit)),
        _XlsxCell(4, m.totalMonthKh[i], style: 4),
        _XlsxCell(5, m.totalMonthTh[i], style: 4),
        _XlsxCell(6, ratio, style: 5),
        _XlsxCell(7, ratio + 1e-12 >= m.monthElapsed ? 'Đạt tiến độ' : 'Chậm tiến độ', style: ratio + 1e-12 >= m.monthElapsed ? 8 : 7),
      ]));
    }

    sheet1.add(_XlsxRow(18, [_XlsxCell(1, 'BẢNG XẾP HẠNG ĐIỂM KPI (6 AM)', style: 2)]));
    sheet1.add(_XlsxRow(19, [
      _XlsxCell(1, 'STT', style: 3),
      _XlsxCell(2, 'Mã AM', style: 3),
      _XlsxCell(3, 'Điểm KPI', style: 3),
      _XlsxCell(4, 'Xếp hạng', style: 3),
    ]));
    for (var i = 0; i < m.ranking.length; i++) {
      final r = m.ranking[i];
      sheet1.add(_XlsxRow(20 + i, [
        _XlsxCell(1, i + 1),
        _XlsxCell(2, i == 0 ? 'TOP1 - ${r.code}' : r.code),
        _XlsxCell(3, r.score),
        _XlsxCell(4, i + 1),
      ]));
    }
    sheet1.add(_XlsxRow(27, [_XlsxCell(1, '⚠️ CẢNH BÁO TRỌNG TÂM', style: 2)]));
    sheet1.add(_XlsxRow(28, [_XlsxCell(1, '• 9 KPI toàn tỉnh được đối chiếu đến ${m.closedDateText}; thời gian tháng đã qua ${(m.monthElapsed * 100).toStringAsFixed(1)}%.', style: 9)]));
    sheet1.add(_XlsxRow(29, [_XlsxCell(1, '• Tuần ${m.week.index + 1}: ${m.slowAmKpiCount}/54 lượt AM–KPI chậm tiến độ.', style: 9)]));
    sheet1.add(_XlsxRow(30, [_XlsxCell(1, '• Cảnh báo AM không có kết quả liên tiếp: ${m.inactivitySummary}.', style: 9)]));
    sheet1.add(_XlsxRow(31, [_XlsxCell(1, '• Yêu cầu AM bị cảnh báo cập nhật kết quả ngay; 6 AM bảo đảm tiến độ Tuần/tháng.', style: 9)]));

    final sheet2 = <_XlsxRow>[];
    sheet2.add(_XlsxRow(1, [
      _XlsxCell(1, 'Mã AM', style: 3),
      _XlsxCell(2, 'STT', style: 3),
      _XlsxCell(3, 'Chỉ tiêu', style: 3),
      _XlsxCell(4, 'Đơn vị', style: 3),
      _XlsxCell(5, 'Tồn tuần trước', style: 3),
      _XlsxCell(6, 'Giao chỉ tiêu Tuần ${m.week.index + 1}', style: 3),
      _XlsxCell(7, 'Tổng giao Tuần ${m.week.index + 1}', style: 3),
      _XlsxCell(8, 'TH lũy kế tuần', style: 3),
      _XlsxCell(9, '% HT tuần', style: 3),
      _XlsxCell(10, 'Còn phải TH trong tuần', style: 3),
      _XlsxCell(11, 'Giao ${DateFormat('dd/MM').format(m.reportDate)}', style: 3),
      _XlsxCell(12, 'Ghi chú', style: 3),
      _XlsxCell(13, 'Điểm KPI', style: 3),
      _XlsxCell(14, 'Xếp hạng', style: 3),
    ]));
    var rowNo = 2;
    for (final code in amCodes) {
      final rank = m.rankOf(code);
      final score = m.scoreOf(code);
      final rows = m.rowsFor(code);
      for (var i = 0; i < rows.length; i++) {
        final r = rows[i];
        final rowStyle = r.weekRatio >= 1 ? 8 : (r.weekRatio > 0 ? 10 : 0);
        sheet2.add(_XlsxRow(rowNo++, [
          _XlsxCell(1, code, style: rowStyle),
          _XlsxCell(2, i + 1, style: rowStyle),
          _XlsxCell(3, metrics[i].name, style: rowStyle),
          _XlsxCell(4, _unit(metrics[i].unit), style: rowStyle),
          _XlsxCell(5, r.carry, style: 4),
          _XlsxCell(6, r.currentTarget, style: 4),
          _XlsxCell(7, r.totalTarget, style: 4),
          _XlsxCell(8, r.weekActual, style: 4),
          _XlsxCell(9, r.weekRatio, style: 5),
          _XlsxCell(10, r.remaining, style: 4),
          _XlsxCell(11, r.todayTarget, style: 4),
          _XlsxCell(12, 'Yêu cầu bảo đảm tiến độ Tuần/tháng', style: rowStyle),
          _XlsxCell(13, score),
          _XlsxCell(14, rank),
        ]));
      }
      rowNo++;
    }

    return _xlsxArchive(
      sheet1: _sheetXml(
        sheet1,
        columnWidths: const [8, 34, 12, 23, 23, 12, 22],
        merges: const ['A1:G1', 'A18:D18', 'A27:G27', 'A28:G28', 'A29:G29', 'A30:G30', 'A31:G31'],
      ),
      sheet2: _sheetXml(
        sheet2,
        columnWidths: const [14, 7, 32, 10, 18, 21, 21, 19, 14, 23, 16, 34, 14, 12],
      ),
    );
  }

  static Future<Uint8List> _buildOverallPng(_ReportModel m) async {
    const width = 1600.0;
    const height = 900.0;
    final recorder = ui.PictureRecorder();
    final canvas = Canvas(recorder);
    canvas.drawRect(const Rect.fromLTWH(0, 0, width, height), Paint()..color = Colors.white);

    canvas.drawRect(const Rect.fromLTWH(0, 0, width, 155), Paint()..color = _red);
    _text(canvas, 'BÁO CÁO KẾT QUẢ HOÀN THÀNH KPI', const Offset(42, 22), width: 1510, size: 39, weight: FontWeight.w900, color: Colors.white);
    _text(canvas, 'KÊNH KHDN - VIETTEL CAO BẰNG', const Offset(42, 67), width: 1510, size: 27, weight: FontWeight.w800, color: Colors.white);
    _text(canvas, 'ĐẾN ${m.closedDateText} • BÁO CÁO NGÀY ${m.reportDateText}', const Offset(42, 105), width: 1510, size: 24, weight: FontWeight.w700, color: Colors.white);

    _text(
      canvas,
      'THỜI GIAN TUẦN ĐÃ QUA ${(m.weekElapsedOperational * 100).toStringAsFixed(1)}% • CÒN ${m.monthRemainingOperational} NGÀY THỰC HIỆN TRONG THÁNG',
      const Offset(42, 170),
      width: 1510,
      size: 22,
      weight: FontWeight.w800,
      color: _darkRed,
    );

    const left = 42.0;
    const gap = 16.0;
    const cardW = (1600 - 84 - gap * 2) / 3;
    const cardH = 126.0;
    const startY = 210.0;
    for (var i = 0; i < 9; i++) {
      final col = i % 3;
      final row = i ~/ 3;
      final x = left + col * (cardW + gap);
      final y = startY + row * (cardH + 12);
      final ratio = m.totalMonthKh[i] <= 0 ? 1.0 : m.totalMonthTh[i] / m.totalMonthKh[i];
      final ok = ratio + 1e-12 >= m.monthElapsed;
      _rounded(canvas, Rect.fromLTWH(x, y, cardW, cardH), _pink, _red, radius: 12, stroke: 1.4);
      _text(canvas, metrics[i].name, Offset(x + 15, y + 11), width: cardW - 170, size: 20, weight: FontWeight.w800, color: _gray, maxLines: 2);
      _text(canvas, '${(ratio * 100).toStringAsFixed(1)}%', Offset(x + cardW - 135, y + 14), width: 120, size: 32, weight: FontWeight.w900, color: ok ? const Color(0xFF167A3E) : _red, align: TextAlign.right);
      _text(canvas, 'KH ${_fmt(m.totalMonthKh[i])} ${_unit(metrics[i].unit)}', Offset(x + 15, y + 63), width: cardW - 30, size: 17, weight: FontWeight.w600, color: _gray);
      _text(canvas, 'TH ${_fmt(m.totalMonthTh[i])} ${_unit(metrics[i].unit)}', Offset(x + 15, y + 87), width: cardW - 220, size: 17, weight: FontWeight.w800, color: const Color(0xFF303438));
      final label = ok ? 'ĐẠT TIẾN ĐỘ' : 'CHẬM TIẾN ĐỘ';
      final labelColor = ok ? const Color(0xFF167A3E) : _red;
      _text(canvas, label, Offset(x + cardW - 205, y + 88), width: 190, size: 16, weight: FontWeight.w900, color: labelColor, align: TextAlign.right);
    }

    const bottomY = 632.0;
    _rounded(canvas, const Rect.fromLTWH(42, bottomY, 515, 207), Colors.white, const Color(0xFFD7D9DB), radius: 10);
    canvas.drawRect(const Rect.fromLTWH(42, bottomY, 515, 38), Paint()..color = _darkRed);
    _text(canvas, 'XẾP HẠNG ĐIỂM KPI - 6 AM', const Offset(58, bottomY + 8), width: 480, size: 18, weight: FontWeight.w900, color: Colors.white);
    for (var i = 0; i < m.ranking.length; i++) {
      final r = m.ranking[i];
      final y = bottomY + 46 + i * 25;
      _text(canvas, '${i + 1}. ${r.code}', Offset(58, y), width: 260, size: 17, weight: i == 0 ? FontWeight.w900 : FontWeight.w700, color: const Color(0xFF303438));
      _text(canvas, r.score.toStringAsFixed(3), Offset(365, y), width: 165, size: 17, weight: FontWeight.w900, color: i == 0 ? _red : const Color(0xFF303438), align: TextAlign.right);
    }

    _rounded(canvas, const Rect.fromLTWH(574, bottomY, 984, 207), Colors.white, const Color(0xFFD7D9DB), radius: 10);
    _text(canvas, 'PHÁT SINH NGÀY ${m.closedDateText}', const Offset(593, bottomY + 10), width: 410, size: 18, weight: FontWeight.w900, color: _darkRed);
    var dy = bottomY + 40;
    final daily = m.totalDailyClosed;
    final active = <int>[];
    for (var i = 0; i < 9; i++) {
      if (daily[i] > 0) active.add(i);
    }
    for (final i in active.take(5)) {
      _text(canvas, '• ${metrics[i].name}: ${_fmt(daily[i])} ${_unit(metrics[i].unit)}', Offset(593, dy), width: 455, size: 15.5, weight: FontWeight.w700, color: const Color(0xFF303438));
      dy += 22;
    }
    _text(canvas, 'CẢNH BÁO VÀ YÊU CẦU THỰC HIỆN', const Offset(1065, bottomY + 10), width: 470, size: 18, weight: FontWeight.w900, color: _darkRed);
    _text(canvas, 'Tuần ${m.week.index + 1}: ${m.slowAmKpiCount}/54 lượt AM-KPI chậm tiến độ.', const Offset(1065, bottomY + 41), width: 470, size: 15, weight: FontWeight.w800, color: _red, maxLines: 2);
    _text(canvas, 'AM không có kết quả: ${m.inactivitySummary}.', const Offset(1065, bottomY + 83), width: 470, size: 15, weight: FontWeight.w800, color: const Color(0xFFD47B00), maxLines: 2);
    _text(canvas, 'Bám đuổi Tổng giao tuần đã gồm tồn tuần trước.', const Offset(1065, bottomY + 126), width: 470, size: 15, weight: FontWeight.w700, color: _gray, maxLines: 2);
    _text(canvas, 'Yêu cầu AM bị cảnh báo cập nhật ngay; chốt số liệu trước 17h00.', const Offset(1065, bottomY + 163), width: 470, size: 15, weight: FontWeight.w800, color: _red, maxLines: 2);

    canvas.drawRect(const Rect.fromLTWH(0, 858, 1600, 42), Paint()..color = _red);
    _text(canvas, 'VIETTEL', const Offset(42, 868), width: 250, size: 20, weight: FontWeight.w900, color: Colors.white);
    _text(canvas, 'KÊNH KHDN - VIETTEL CAO BẰNG', const Offset(1030, 868), width: 528, size: 18, weight: FontWeight.w800, color: Colors.white, align: TextAlign.right);

    final picture = recorder.endRecording();
    final image = await picture.toImage(width.toInt(), height.toInt());
    final data = await image.toByteData(format: ui.ImageByteFormat.png);
    image.dispose();
    picture.dispose();
    if (data == null) throw Exception('Không tạo được ảnh báo cáo tổng hợp');
    return data.buffer.asUint8List();
  }

  static Future<Uint8List> _buildAmPng(_ReportModel m, String code) async {
    const width = 1600.0;
    const height = 900.0;
    final recorder = ui.PictureRecorder();
    final canvas = Canvas(recorder);
    canvas.drawRect(const Rect.fromLTWH(0, 0, width, height), Paint()..color = Colors.white);

    canvas.drawRect(const Rect.fromLTWH(0, 0, width, 128), Paint()..color = _red);
    _text(canvas, 'GIAO CHỈ TIÊU NGÀY ${m.reportDateText}', const Offset(40, 21), width: 1520, size: 38, weight: FontWeight.w900, color: Colors.white);
    _text(canvas, 'TỔNG GIAO TUẦN ${m.week.index + 1} = TỒN TUẦN TRƯỚC + CHỈ TIÊU TUẦN ${m.week.index + 1}', const Offset(40, 76), width: 1520, size: 22, weight: FontWeight.w800, color: Colors.white);

    canvas.drawRect(const Rect.fromLTWH(32, 145, 1536, 55), Paint()..color = _darkRed);
    _text(canvas, 'MÃ AM: $code', const Offset(52, 160), width: 720, size: 23, weight: FontWeight.w900, color: Colors.white);
    _text(canvas, 'ĐIỂM KPI: ${m.scoreOf(code).toStringAsFixed(3)} • XẾP HẠNG: ${m.rankOf(code)}', const Offset(810, 160), width: 730, size: 23, weight: FontWeight.w900, color: Colors.white, align: TextAlign.right);

    final headers = [
      'Chỉ tiêu',
      'Tồn tuần trước',
      'Giao chỉ tiêu\nTuần ${m.week.index + 1}',
      'Tổng giao\nTuần ${m.week.index + 1}',
      'TH lũy kế\ntuần',
      '% HT KH\ntuần',
      'Còn phải thực hiện\ntrong tuần',
      'Giao\n${DateFormat('dd/MM').format(m.reportDate)}',
      'Ghi chú',
    ];
    final widths = <double>[270, 145, 150, 150, 145, 120, 180, 140, 236];
    const tableX = 32.0;
    const tableY = 215.0;
    const headerH = 70.0;
    const rowH = 60.0;

    var x = tableX;
    for (var c = 0; c < headers.length; c++) {
      _cell(canvas, Rect.fromLTWH(x, tableY, widths[c], headerH), _darkRed, Colors.white);
      _text(canvas, headers[c], Offset(x + 7, tableY + 12), width: widths[c] - 14, size: 16, weight: FontWeight.w900, color: Colors.white, align: TextAlign.center, maxLines: 3);
      x += widths[c];
    }

    final rows = m.rowsFor(code);
    for (var i = 0; i < 9; i++) {
      final r = rows[i];
      final y = tableY + headerH + i * rowH;
      final fill = r.weekRatio >= 1 ? _green : (r.weekActual > 0 ? _yellow : _pink);
      final vals = <String>[
        metrics[i].name,
        _fmtOrBlank(r.carry, r.carry == 0 && r.currentTarget == 0),
        _fmtOrBlank(r.currentTarget, r.currentTarget == 0),
        _fmtOrBlank(r.totalTarget, r.totalTarget == 0),
        _fmtOrBlank(r.weekActual, r.totalTarget == 0 && r.weekActual == 0),
        '${(r.weekRatio * 100).toStringAsFixed(1)}%',
        _fmtOrBlank(r.remaining, r.totalTarget == 0),
        _fmtOrBlank(r.todayTarget, r.totalTarget == 0),
        'Yêu cầu bảo đảm tiến độ Tuần/tháng',
      ];
      x = tableX;
      for (var c = 0; c < vals.length; c++) {
        _cell(canvas, Rect.fromLTWH(x, y, widths[c], rowH), fill, const Color(0xFFD8C4C4));
        _text(
          canvas,
          vals[c],
          Offset(x + 7, y + (c == 0 || c == 8 ? 7 : 18)),
          width: widths[c] - 14,
          size: c == 8 ? 13.5 : 15,
          weight: c == 0 ? FontWeight.w800 : FontWeight.w700,
          color: const Color(0xFF303438),
          align: c == 0 || c == 8 ? TextAlign.left : TextAlign.center,
          maxLines: c == 0 || c == 8 ? 3 : 2,
        );
        x += widths[c];
      }
    }

    final noteY = tableY + headerH + 9 * rowH + 14;
    _text(canvas, 'Số liệu chốt đến ${m.closedDateText}. Giao ngày được tính trên số còn phải thực hiện và số ngày làm việc còn lại trong tuần (không tính Chủ nhật).', Offset(42, noteY), width: 1510, size: 17, weight: FontWeight.w700, color: _gray, maxLines: 2);

    canvas.drawRect(const Rect.fromLTWH(0, 858, 1600, 42), Paint()..color = _red);
    _text(canvas, 'VIETTEL', const Offset(42, 868), width: 250, size: 20, weight: FontWeight.w900, color: Colors.white);
    _text(canvas, 'KÊNH KHDN - VIETTEL CAO BẰNG', const Offset(1030, 868), width: 528, size: 18, weight: FontWeight.w800, color: Colors.white, align: TextAlign.right);

    final picture = recorder.endRecording();
    final image = await picture.toImage(width.toInt(), height.toInt());
    final data = await image.toByteData(format: ui.ImageByteFormat.png);
    image.dispose();
    picture.dispose();
    if (data == null) throw Exception('Không tạo được ảnh AM $code');
    return data.buffer.asUint8List();
  }

  static void _cell(Canvas canvas, Rect rect, Color fill, Color border) {
    canvas.drawRect(rect, Paint()..color = fill);
    canvas.drawRect(rect, Paint()..color = border..style = PaintingStyle.stroke..strokeWidth = 1);
  }

  static void _rounded(Canvas canvas, Rect rect, Color fill, Color border, {double radius = 8, double stroke = 1}) {
    final rr = RRect.fromRectAndRadius(rect, Radius.circular(radius));
    canvas.drawRRect(rr, Paint()..color = fill);
    canvas.drawRRect(rr, Paint()..color = border..style = PaintingStyle.stroke..strokeWidth = stroke);
  }

  static void _text(
    Canvas canvas,
    String text,
    Offset offset, {
    required double width,
    double size = 16,
    FontWeight weight = FontWeight.w500,
    Color color = const Color(0xFF303438),
    TextAlign align = TextAlign.left,
    int? maxLines,
  }) {
    final tp = TextPainter(
      text: TextSpan(
        text: text,
        style: TextStyle(fontSize: size, fontWeight: weight, color: color, height: 1.08),
      ),
      textDirection: TextDirection.ltr,
      textAlign: align,
      maxLines: maxLines,
      ellipsis: maxLines == null ? null : '…',
    )..layout(maxWidth: width);
    tp.paint(canvas, offset);
  }

  static String _fmt(double v) => _nf.format(v.round());
  static String _fmtOrBlank(double v, bool blank) => blank ? '' : _fmt(v);
  static String _unit(String unit) => unit == 'đồng' ? 'đ' : unit;

  static Uint8List _xlsxArchive({required String sheet1, required String sheet2}) {
    final a = Archive();
    void add(String name, String text) {
      final bytes = Uint8List.fromList(text.codeUnits);
      a.addFile(ArchiveFile(name, bytes.length, bytes));
    }
    add('[Content_Types].xml', '''<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
<Default Extension="xml" ContentType="application/xml"/>
<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
<Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
<Override PartName="/xl/worksheets/sheet2.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
</Types>''');
    add('_rels/.rels', '''<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>''');
    add('xl/workbook.xml', '''<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
<sheets><sheet name="Tong_KQ_TH_KH" sheetId="1" r:id="rId1"/><sheet name="KQ_Theo_AM" sheetId="2" r:id="rId2"/></sheets>
</workbook>''');
    add('xl/_rels/workbook.xml.rels', '''<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
<Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet2.xml"/>
<Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>''');
    add('xl/styles.xml', _stylesXml);
    add('xl/worksheets/sheet1.xml', sheet1);
    add('xl/worksheets/sheet2.xml', sheet2);
    final out = ZipEncoder().encode(a);
    if (out == null) throw Exception('Không tạo được Excel');
    return Uint8List.fromList(out);
  }

  static String _sheetXml(List<_XlsxRow> rows, {required List<double> columnWidths, List<String> merges = const []}) {
    final b = StringBuffer('<?xml version="1.0" encoding="UTF-8" standalone="yes"?>');
    b.write('<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">');
    b.write('<sheetViews><sheetView workbookViewId="0"/></sheetViews>');
    b.write('<cols>');
    for (var i = 0; i < columnWidths.length; i++) {
      b.write('<col min="${i + 1}" max="${i + 1}" width="${columnWidths[i]}" customWidth="1"/>');
    }
    b.write('</cols><sheetData>');
    for (final row in rows) {
      b.write('<row r="${row.index}"${row.index == 1 ? ' ht="30" customHeight="1"' : ''}>');
      for (final c in row.cells) {
        final ref = '${_col(c.col)}${row.index}';
        final style = c.style == 0 ? '' : ' s="${c.style}"';
        if (c.value is num) {
          b.write('<c r="$ref"$style><v>${(c.value as num).toString()}</v></c>');
        } else {
          b.write('<c r="$ref"$style t="inlineStr"><is><t xml:space="preserve">${_xml(c.value?.toString() ?? '')}</t></is></c>');
        }
      }
      b.write('</row>');
    }
    b.write('</sheetData>');
    if (merges.isNotEmpty) {
      b.write('<mergeCells count="${merges.length}">');
      for (final r in merges) b.write('<mergeCell ref="$r"/>');
      b.write('</mergeCells>');
    }
    b.write('<pageMargins left="0.3" right="0.3" top="0.5" bottom="0.5" header="0.2" footer="0.2"/>');
    b.write('</worksheet>');
    return b.toString();
  }

  static String _col(int n) {
    var x = n;
    var s = '';
    while (x > 0) {
      x--;
      s = String.fromCharCode(65 + x % 26) + s;
      x ~/= 26;
    }
    return s;
  }

  static String _xml(String s) => s
      .replaceAll('&', '&amp;')
      .replaceAll('<', '&lt;')
      .replaceAll('>', '&gt;')
      .replaceAll('"', '&quot;')
      .replaceAll("'", '&apos;');

  static const _stylesXml = '''<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
<numFmts count="2"><numFmt numFmtId="164" formatCode="#\,##0"/><numFmt numFmtId="165" formatCode="0.0%"/></numFmts>
<fonts count="4">
<font><sz val="11"/><name val="Arial"/></font>
<font><b/><color rgb="FFFFFFFF"/><sz val="14"/><name val="Arial"/></font>
<font><b/><color rgb="FFFFFFFF"/><sz val="11"/><name val="Arial"/></font>
<font><b/><color rgb="FFE80500"/><sz val="11"/><name val="Arial"/></font>
</fonts>
<fills count="6">
<fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill>
<fill><patternFill patternType="solid"><fgColor rgb="FFE80500"/><bgColor indexed="64"/></patternFill></fill>
<fill><patternFill patternType="solid"><fgColor rgb="FF9E1015"/><bgColor indexed="64"/></patternFill></fill>
<fill><patternFill patternType="solid"><fgColor rgb="FF596169"/><bgColor indexed="64"/></patternFill></fill>
<fill><patternFill patternType="solid"><fgColor rgb="FFFFF0F0"/><bgColor indexed="64"/></patternFill></fill>
</fills>
<borders count="2"><border/><border><left style="thin"><color rgb="FFD0D0D0"/></left><right style="thin"><color rgb="FFD0D0D0"/></right><top style="thin"><color rgb="FFD0D0D0"/></top><bottom style="thin"><color rgb="FFD0D0D0"/></bottom></border></borders>
<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
<cellXfs count="11">
<xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
<xf numFmtId="0" fontId="1" fillId="2" borderId="0" xfId="0" applyAlignment="1"><alignment horizontal="center" vertical="center"/></xf>
<xf numFmtId="0" fontId="2" fillId="3" borderId="0" xfId="0" applyAlignment="1"><alignment horizontal="center" vertical="center"/></xf>
<xf numFmtId="0" fontId="2" fillId="4" borderId="1" xfId="0" applyAlignment="1"><alignment horizontal="center" vertical="center" wrapText="1"/></xf>
<xf numFmtId="164" fontId="0" fillId="0" borderId="1" xfId="0" applyNumberFormat="1"/>
<xf numFmtId="165" fontId="0" fillId="0" borderId="1" xfId="0" applyNumberFormat="1" applyAlignment="1"><alignment horizontal="center"/></xf>
<xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"><alignment horizontal="left"/></xf>
<xf numFmtId="0" fontId="3" fillId="5" borderId="1" xfId="0" applyAlignment="1"><alignment wrapText="1"/></xf>
<xf numFmtId="0" fontId="0" fillId="0" borderId="1" xfId="0" applyAlignment="1"><alignment wrapText="1"/></xf>
<xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0" applyAlignment="1"><alignment wrapText="1"/></xf>
<xf numFmtId="0" fontId="0" fillId="5" borderId="1" xfId="0" applyAlignment="1"><alignment wrapText="1"/></xf>
</cellXfs>
<cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles>
</styleSheet>''';
}

class _ReportModel {
  _ReportModel({required this.all, required this.targets, required DateTime reportDate})
      : reportDate = DateTime(reportDate.year, reportDate.month, reportDate.day),
        closedDate = DateTime(reportDate.year, reportDate.month, reportDate.day).subtract(const Duration(days: 1)),
        week = _reportWeek(DateTime(reportDate.year, reportDate.month, reportDate.day));

  final Map<String, AmData> all;
  final Map<String, AmTarget> targets;
  final DateTime reportDate;
  final DateTime closedDate;
  final _Week week;

  String get reportDateText => DateFormat('dd/MM/yyyy').format(reportDate);
  String get closedDateText => DateFormat('dd/MM/yyyy').format(closedDate);

  List<double> monthActual(String code) => _sumDaily(all[code]!, from: DateTime(reportDate.year, reportDate.month, 1), through: closedDate);
  List<double> weekActual(String code) => _sumDaily(all[code]!, from: week.start, through: closedDate.isAfter(week.end) ? week.end : closedDate);
  List<double> beforeWeekActual(String code) => _sumDaily(all[code]!, from: DateTime(reportDate.year, reportDate.month, 1), through: week.start.subtract(const Duration(days: 1)));

  List<double> get totalMonthKh => List.generate(9, (i) => amCodes.fold<double>(0, (s, c) => s + all[c]!.kh[i]));
  List<double> get totalMonthTh => List.generate(9, (i) => amCodes.fold<double>(0, (s, c) => s + monthActual(c)[i]));
  List<double> get totalDailyClosed => List.generate(9, (i) => amCodes.fold<double>(0, (s, c) => s + _dailyValue(all[c]!, closedDate, i)));

  double get monthElapsed {
    final total = DateTime(reportDate.year, reportDate.month + 1, 0).day;
    if (closedDate.month != reportDate.month) return 0;
    return closedDate.day / total;
  }

  double get weekElapsedOperational {
    final total = _operationalDays(week.start, week.end);
    if (closedDate.isBefore(week.start) || total == 0) return 0;
    final end = closedDate.isAfter(week.end) ? week.end : closedDate;
    return _operationalDays(week.start, end) / total;
  }

  int get monthRemainingOperational {
    final end = DateTime(reportDate.year, reportDate.month + 1, 0);
    return _operationalDays(reportDate, end);
  }

  List<_AmWeekRow> rowsFor(String code) {
    final target = targets[code]!;
    final actualBefore = beforeWeekActual(code);
    final actualWeek = weekActual(code);
    final daysLeft = math.max(1, _operationalDays(reportDate, week.end));
    return List.generate(9, (i) {
      var previousTarget = 0.0;
      for (var w = 0; w < week.index; w++) previousTarget += target.weeks[w][i];
      final carry = math.max(0.0, previousTarget - actualBefore[i]);
      final current = target.weeks[week.index][i];
      final total = carry + current;
      final remain = math.max(0.0, total - actualWeek[i]);
      final ratio = total <= 0 ? 0.0 : actualWeek[i] / total;
      final today = total <= 0 ? 0.0 : (remain / daysLeft).ceilToDouble();
      return _AmWeekRow(
        carry: carry,
        currentTarget: current,
        totalTarget: total,
        weekActual: actualWeek[i],
        weekRatio: ratio,
        remaining: remain,
        todayTarget: today,
      );
    });
  }

  double scoreOf(String code) {
    final a = all[code]!;
    final th = monthActual(code);
    var score = 0.0;
    for (var i = 0; i < 9; i++) {
      final ratio = a.kh[i] <= 0 ? 1.0 : th[i] / a.kh[i];
      score += ratio.clamp(0.0, 1.0) * metrics[i].weight;
    }
    return score;
  }

  List<_Rank> get ranking {
    final list = amCodes.map((c) => _Rank(c, scoreOf(c))).toList();
    list.sort((a, b) => b.score.compareTo(a.score));
    return list;
  }

  int rankOf(String code) => ranking.indexWhere((r) => r.code == code) + 1;

  int get slowAmKpiCount {
    final elapsed = weekElapsedOperational;
    var count = 0;
    for (final code in amCodes) {
      final rows = rowsFor(code);
      for (var i = 0; i < 9; i++) {
        if (rows[i].totalTarget <= 0) {
          if (rows[i].weekActual <= 0) count++;
        } else if (rows[i].weekRatio + 1e-12 < elapsed) {
          count++;
        }
      }
    }
    return count;
  }

  int inactivityStreak(String code) {
    var d = closedDate;
    var streak = 0;
    while (d.month == reportDate.month) {
      final row = all[code]!.daily[_key(d)];
      final hasPositive = row != null && row.any((v) => v != null && v! > 0);
      if (hasPositive) break;
      streak++;
      d = d.subtract(const Duration(days: 1));
    }
    return streak;
  }

  String get inactivitySummary {
    final one = <String>[];
    final two = <String>[];
    final three = <String>[];
    for (final c in amCodes) {
      final s = inactivityStreak(c);
      if (s == 1) one.add(c);
      if (s == 2) two.add(c);
      if (s >= 3) three.add('$c ($s ngày)');
    }
    return '1 ngày: ${one.isEmpty ? 'Không' : one.join(', ')}; '
        '2 ngày: ${two.isEmpty ? 'Không' : two.join(', ')}; '
        '>=3 ngày: ${three.isEmpty ? 'Không' : three.join(', ')}';
  }
}

class _Week {
  const _Week(this.start, this.end, this.index);
  final DateTime start;
  final DateTime end;
  final int index;
}

_Week _reportWeek(DateTime d) {
  final monthEnd = DateTime(d.year, d.month + 1, 0);
  var start = DateTime(d.year, d.month, 1);
  var idx = 0;
  while (true) {
    var end = start.add(Duration(days: 7 - start.weekday));
    if (end.isAfter(monthEnd)) end = monthEnd;
    if (!d.isBefore(start) && !d.isAfter(end)) return _Week(start, end, idx.clamp(0, 4));
    start = end.add(const Duration(days: 1));
    idx++;
    if (start.month != d.month || idx >= 5) return _Week(monthEnd, monthEnd, 4);
  }
}

List<double> _sumDaily(AmData a, {required DateTime from, required DateTime through}) {
  final out = List<double>.filled(9, 0);
  if (through.isBefore(from)) return out;
  a.daily.forEach((k, row) {
    final d = DateTime.tryParse(k);
    if (d == null || d.isBefore(from) || d.isAfter(through)) return;
    for (var i = 0; i < 9; i++) {
      final v = row[i];
      if (v != null) out[i] += v;
    }
  });
  return out;
}

double _dailyValue(AmData a, DateTime d, int i) => a.daily[_key(d)]?[i] ?? 0;
String _key(DateTime d) => '${d.year.toString().padLeft(4, '0')}-${d.month.toString().padLeft(2, '0')}-${d.day.toString().padLeft(2, '0')}';

int _operationalDays(DateTime from, DateTime through) {
  if (through.isBefore(from)) return 0;
  var count = 0;
  var d = DateTime(from.year, from.month, from.day);
  final end = DateTime(through.year, through.month, through.day);
  while (!d.isAfter(end)) {
    if (d.weekday != DateTime.sunday) count++;
    d = d.add(const Duration(days: 1));
  }
  return count;
}

class _AmWeekRow {
  const _AmWeekRow({
    required this.carry,
    required this.currentTarget,
    required this.totalTarget,
    required this.weekActual,
    required this.weekRatio,
    required this.remaining,
    required this.todayTarget,
  });
  final double carry;
  final double currentTarget;
  final double totalTarget;
  final double weekActual;
  final double weekRatio;
  final double remaining;
  final double todayTarget;
}

class _Rank {
  const _Rank(this.code, this.score);
  final String code;
  final double score;
}

class _XlsxCell {
  const _XlsxCell(this.col, this.value, {this.style = 0});
  final int col;
  final Object? value;
  final int style;
}

class _XlsxRow {
  const _XlsxRow(this.index, this.cells);
  final int index;
  final List<_XlsxCell> cells;
}
