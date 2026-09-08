from pathlib import Path

p = Path('lib/main.dart')
s = p.read_text()

old = '''        Text('Tuần hiện tại: Tuần ${wi + 1}', style: const TextStyle(fontSize: 19, fontWeight: FontWeight.w900)),
        const SizedBox(height: 10),
        KpiDonutGrid(
          monthTh: a.th,
          monthKh: a.kh,
          weekTh: wa,
          weekKh: wp,
          monthElapsed: monthElapsedRatio(now),
          weekElapsed: weekElapsedRatio(now),
        ),
'''

new = '''        const SizedBox(height: 6),
        const Text(
          'CHI TIẾT TIẾN ĐỘ THÁNG',
          style: TextStyle(fontSize: 18, fontWeight: FontWeight.w900, color: Color(0xFFE80500)),
        ),
        const SizedBox(height: 8),
        _kpiProgressTable(
          th: a.th,
          kh: a.kh,
          elapsed: monthElapsedRatio(now),
        ),
        const SizedBox(height: 18),
        Text(
          'CHI TIẾT TIẾN ĐỘ TUẦN ${wi + 1}',
          style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w900, color: Color(0xFFE80500)),
        ),
        const SizedBox(height: 8),
        _kpiProgressTable(
          th: wa,
          kh: wp,
          elapsed: weekElapsedRatio(now),
        ),
'''

if old not in s:
    raise SystemExit('Không tìm thấy block KpiDonutGrid trong tiến độ AM')
s = s.replace(old, new, 1)

marker = '''  Widget progress() {
'''

methods = '''  Widget _kpiProgressTable({
    required List<double> th,
    required List<double> kh,
    required double elapsed,
  }) {
    Widget cell(
      String text, {
      bool header = false,
      TextAlign align = TextAlign.left,
      Color? color,
      Color? background,
      FontWeight? weight,
    }) {
      return Container(
        color: background,
        padding: const EdgeInsets.symmetric(horizontal: 7, vertical: 10),
        alignment: align == TextAlign.center
            ? Alignment.center
            : align == TextAlign.right
                ? Alignment.centerRight
                : Alignment.centerLeft,
        child: Text(
          text,
          textAlign: align,
          style: TextStyle(
            fontSize: header ? 13 : 12.5,
            fontWeight: weight ?? (header ? FontWeight.w900 : FontWeight.w600),
            color: color ?? (header ? Colors.white : const Color(0xFF2E3338)),
          ),
        ),
      );
    }

    final rows = <TableRow>[
      TableRow(
        decoration: const BoxDecoration(color: Color(0xFFE80500)),
        children: [
          cell('STT', header: true, align: TextAlign.center),
          cell('Chỉ tiêu', header: true),
          cell('KH', header: true, align: TextAlign.right),
          cell('TH', header: true, align: TextAlign.right),
          cell('% HT', header: true, align: TextAlign.center),
          cell('Đánh giá', header: true, align: TextAlign.center),
        ],
      ),
    ];

    for (var i = 0; i < 9; i++) {
      final hasKh = kh[i] > 0;
      final ratio = hasKh ? th[i] / kh[i] : 0.0;
      final pending = elapsed <= 0;
      final đạt = hasKh && !pending && ratio + 1e-12 >= elapsed;
      final status = !hasKh
          ? 'Không giao'
          : pending
              ? 'Chưa đánh giá'
              : đạt
                  ? 'Đạt tiến độ'
                  : 'Không đạt';
      final statusColor = !hasKh || pending
          ? const Color(0xFF596169)
          : đạt
              ? const Color(0xFF00A651)
              : const Color(0xFFE80500);
      final statusBg = !hasKh || pending
          ? const Color(0xFFF0F1F2)
          : đạt
              ? const Color(0xFFE7F6ED)
              : const Color(0xFFFFECEB);
      final rowBg = i.isEven ? Colors.white : const Color(0xFFF8F9FA);
      rows.add(
        TableRow(
          decoration: BoxDecoration(color: rowBg),
          children: [
            cell('${i + 1}', align: TextAlign.center),
            cell('${metrics[i].name}\n(${metrics[i].unit})', weight: FontWeight.w700),
            cell(fmt(kh[i]), align: TextAlign.right),
            cell(fmt(th[i]), align: TextAlign.right),
            cell(
              hasKh ? '${(ratio * 100).toStringAsFixed(1)}%' : '-',
              align: TextAlign.center,
              weight: FontWeight.w900,
              color: statusColor,
            ),
            cell(
              status,
              align: TextAlign.center,
              weight: FontWeight.w900,
              color: statusColor,
              background: statusBg,
            ),
          ],
        ),
      );
    }

    return Card(
      margin: EdgeInsets.zero,
      clipBehavior: Clip.antiAlias,
      child: SingleChildScrollView(
        scrollDirection: Axis.horizontal,
        child: SizedBox(
          width: 760,
          child: Table(
            border: TableBorder.all(color: const Color(0xFFCCD1D5), width: 0.8),
            columnWidths: const {
              0: FixedColumnWidth(46),
              1: FixedColumnWidth(220),
              2: FixedColumnWidth(115),
              3: FixedColumnWidth(115),
              4: FixedColumnWidth(82),
              5: FixedColumnWidth(150),
            },
            defaultVerticalAlignment: TableCellVerticalAlignment.middle,
            children: rows,
          ),
        ),
      ),
    );
  }

'''

if marker not in s:
    raise SystemExit('Không tìm thấy hàm progress() để chèn bảng')
s = s.replace(marker, methods + marker, 1)

p.write_text(s)
print('AM_PROGRESS_TABLE_V1011_PATCH_OK')
