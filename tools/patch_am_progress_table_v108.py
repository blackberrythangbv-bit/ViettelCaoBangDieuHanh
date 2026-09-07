from pathlib import Path

p = Path('lib/main.dart')
s = p.read_text()

# Add helper for closed month actuals at N-1 if it is not already present.
anchor = '''List<double> weekActualForRange(AmData a, DateTime now) {\n'''
helper = '''List<double> closedMonthActualForRange(AmData a, DateTime now) {\n  final yesterday = DateTime(now.year, now.month, now.day).subtract(const Duration(days: 1));\n  final out = List<double>.filled(9, 0);\n  a.daily.forEach((k, row) {\n    final d = DateTime.tryParse(k);\n    if (d == null || d.year != now.year || d.month != now.month || d.isAfter(yesterday)) return;\n    for (var i = 0; i < 9; i++) {\n      if (row[i] != null) out[i] += row[i]!;\n    }\n  });\n  return out;\n}\n\ndouble scoreFromActuals(List<double> th, List<double> kh) {\n  var s = 0.0;\n  for (var i = 0; i < 9; i++) {\n    final c = kh[i] == 0 ? 1.0 : th[i] / kh[i];\n    s += c.clamp(0.0, 1.0) * metrics[i].weight;\n  }\n  return s;\n}\n\n'''
if 'closedMonthActualForRange' not in s:
    if anchor not in s:
        raise SystemExit('Không tìm thấy weekActualForRange để chèn helper')
    s = s.replace(anchor, helper + anchor, 1)

start = s.find('  Widget progress() {')
end = s.find('\n  @override\n  void dispose()', start)
if start < 0 or end < 0:
    raise SystemExit('Không tìm thấy hàm progress() của AM')

new_progress = r'''  Widget progress() {
    final a = data;
    if (error != null) return Center(child: Text(error!));
    if (a == null) return const Center(child: CircularProgressIndicator());

    final now = DateTime.now();
    final wi = targetWeekWindow(now).index;
    final monthTh = closedMonthActualForRange(a, now);
    final monthKh = a.kh;
    final weekTh = weekActualForRange(a, now);
    final weekKh = widget.target.weeks[wi];
    final daysLeft = weekRemainingDays(now);

    final cumulativeWeekKh = List<double>.filled(9, 0);
    for (var w = 0; w <= wi && w < widget.target.weeks.length; w++) {
      for (var i = 0; i < 9; i++) {
        cumulativeWeekKh[i] += widget.target.weeks[w][i];
      }
    }

    final remaining = List<double>.generate(
      9,
      (i) => math.max(0.0, cumulativeWeekKh[i] - monthTh[i]),
    );
    final avgPerDay = List<double>.generate(
      9,
      (i) => daysLeft > 0 ? remaining[i] / daysLeft : remaining[i],
    );

    String pctText(double th, double kh) {
      if (kh == 0) return '100%';
      return '${(th / kh * 100).toStringAsFixed(1)}%';
    }

    Color pctColor(double th, double kh, double elapsed) {
      if (kh == 0) return Colors.green;
      final ratio = th / kh;
      if (ratio >= elapsed) return Colors.green;
      if (ratio >= elapsed * 0.8) return const Color(0xFFFF8F00);
      return const Color(0xFFE80500);
    }

    DataCell numCell(String text, {Color? color, bool bold = false}) => DataCell(
          Align(
            alignment: Alignment.centerRight,
            child: Text(
              text,
              textAlign: TextAlign.right,
              style: TextStyle(
                fontWeight: bold ? FontWeight.w900 : FontWeight.w600,
                color: color,
              ),
            ),
          ),
        );

    return ListView(
      padding: const EdgeInsets.all(12),
      children: [
        Card(
          child: Padding(
            padding: const EdgeInsets.all(18),
            child: Column(children: [
              const Text(
                'Điểm PL05 lũy kế tháng (chốt N-1)',
                style: TextStyle(fontWeight: FontWeight.w700),
              ),
              Text(
                '${scoreFromActuals(monthTh, monthKh).toStringAsFixed(1)}/100',
                style: const TextStyle(
                  fontSize: 36,
                  fontWeight: FontWeight.w900,
                  color: Color(0xFFE80500),
                ),
              ),
            ]),
          ),
        ),
        EvaluationPanel(
          monthTh: monthTh,
          monthKh: monthKh,
          weekTh: weekTh,
          weekKh: weekKh,
          now: now,
        ),
        const SizedBox(height: 4),
        Row(
          children: [
            Expanded(
              child: Text(
                'BẢNG TỔNG HỢP TIẾN ĐỘ - TUẦN ${wi + 1}',
                style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w900),
              ),
            ),
            Text(
              'Còn $daysLeft ngày',
              style: const TextStyle(
                color: Color(0xFFE80500),
                fontWeight: FontWeight.w800,
              ),
            ),
          ],
        ),
        const SizedBox(height: 8),
        Card(
          clipBehavior: Clip.antiAlias,
          child: SingleChildScrollView(
            scrollDirection: Axis.horizontal,
            child: DataTable(
              headingRowColor: WidgetStateProperty.all(const Color(0xFFFFE9E7)),
              headingTextStyle: const TextStyle(
                fontWeight: FontWeight.w900,
                color: Color(0xFF42494D),
              ),
              dataRowMinHeight: 58,
              dataRowMaxHeight: 72,
              columnSpacing: 20,
              horizontalMargin: 14,
              columns: const [
                DataColumn(label: Text('Chỉ tiêu')),
                DataColumn(label: Text('KH tháng'), numeric: true),
                DataColumn(label: Text('TH lũy kế'), numeric: true),
                DataColumn(label: Text('% tháng'), numeric: true),
                DataColumn(label: Text('KH tuần'), numeric: true),
                DataColumn(label: Text('TH tuần'), numeric: true),
                DataColumn(label: Text('% tuần'), numeric: true),
                DataColumn(label: Text('Còn phải làm'), numeric: true),
                DataColumn(label: Text('BQ/ngày'), numeric: true),
              ],
              rows: List.generate(9, (i) {
                final m = metrics[i];
                final monthColor = pctColor(monthTh[i], monthKh[i], monthElapsedRatio(now));
                final weekColor = pctColor(weekTh[i], weekKh[i], weekElapsedRatio(now));
                return DataRow(
                  cells: [
                    DataCell(
                      SizedBox(
                        width: 190,
                        child: Column(
                          mainAxisAlignment: MainAxisAlignment.center,
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              '${i + 1}. ${m.name}',
                              style: const TextStyle(fontWeight: FontWeight.w800),
                            ),
                            Text(
                              m.unit,
                              style: const TextStyle(fontSize: 11, color: Colors.black54),
                            ),
                          ],
                        ),
                      ),
                    ),
                    numCell(fmt(monthKh[i])),
                    numCell(fmt(monthTh[i]), bold: true),
                    numCell(pctText(monthTh[i], monthKh[i]), color: monthColor, bold: true),
                    numCell(fmt(weekKh[i])),
                    numCell(fmt(weekTh[i]), bold: true),
                    numCell(pctText(weekTh[i], weekKh[i]), color: weekColor, bold: true),
                    numCell(fmt(remaining[i]), color: remaining[i] > 0 ? const Color(0xFFE80500) : Colors.green, bold: true),
                    numCell(fmt(avgPerDay[i]), bold: true),
                  ],
                );
              }),
            ),
          ),
        ),
        const Padding(
          padding: EdgeInsets.only(top: 4, bottom: 12),
          child: Text(
            'Kéo bảng sang trái/phải để xem đầy đủ. TH tháng chốt đến N-1; “Còn phải làm” = KH lũy kế các tuần đến tuần hiện tại trừ TH lũy kế.',
            style: TextStyle(fontSize: 12, color: Colors.black54, fontStyle: FontStyle.italic),
          ),
        ),
      ],
    );
  }
'''

s = s[:start] + new_progress + s[end:]
p.write_text(s)
print('AM_PROGRESS_TABLE_V108_PATCH_OK')
