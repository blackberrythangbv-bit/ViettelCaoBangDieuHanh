import 'dart:math' as math;
import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import 'data.dart';

const _viettelRed = Color(0xFFE80500);
const _warnOrange = Color(0xFFFF8F00);
const _specialPurple = Color(0xFF8E24AA);
const _okGreen = Color(0xFF00A651);
const _neutralGray = Color(0xFF687078);

class ProgressDonut extends StatelessWidget {
  const ProgressDonut({
    super.key,
    required this.ratio,
    required this.label,
    required this.progressColor,
    this.size = 112,
  });

  final double ratio;
  final String label;
  final Color progressColor;
  final double size;

  @override
  Widget build(BuildContext context) {
    final safe = ratio.isFinite ? ratio.clamp(0.0, 1.0) : 0.0;
    return SizedBox(
      width: size,
      height: size,
      child: TweenAnimationBuilder<double>(
        tween: Tween(begin: 0, end: safe),
        duration: const Duration(milliseconds: 600),
        curve: Curves.easeOutCubic,
        builder: (context, value, _) => CustomPaint(
          painter: _DonutPainter(value, progressColor),
          child: Center(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                Text(
                  label,
                  style: const TextStyle(fontSize: 22, fontWeight: FontWeight.w900),
                ),
                const Text(
                  'HT',
                  style: TextStyle(fontSize: 10, fontWeight: FontWeight.w800, color: _neutralGray),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _DonutPainter extends CustomPainter {
  _DonutPainter(this.ratio, this.color);
  final double ratio;
  final Color color;

  @override
  void paint(Canvas canvas, Size size) {
    final center = Offset(size.width / 2, size.height / 2);
    final radius = math.min(size.width, size.height) / 2 - 9;
    final track = Paint()
      ..color = const Color(0xFFE9ECEF)
      ..style = PaintingStyle.stroke
      ..strokeWidth = 13
      ..strokeCap = StrokeCap.round;
    final progress = Paint()
      ..color = color
      ..style = PaintingStyle.stroke
      ..strokeWidth = 13
      ..strokeCap = StrokeCap.round;

    canvas.drawCircle(center, radius, track);
    if (ratio > 0) {
      canvas.drawArc(
        Rect.fromCircle(center: center, radius: radius),
        -math.pi / 2,
        math.pi * 2 * ratio,
        false,
        progress,
      );
    }
  }

  @override
  bool shouldRepaint(covariant _DonutPainter oldDelegate) =>
      oldDelegate.ratio != ratio || oldDelegate.color != color;
}

class KpiDonutGrid extends StatelessWidget {
  const KpiDonutGrid({
    super.key,
    required this.monthTh,
    required this.monthKh,
    required this.weekTh,
    required this.weekKh,
    required this.monthElapsed,
    required this.weekElapsed,
  });

  final List<double> monthTh;
  final List<double> monthKh;
  final List<double> weekTh;
  final List<double> weekKh;
  final double monthElapsed;
  final double weekElapsed;

  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(
      builder: (context, c) {
        final twoCols = c.maxWidth >= 760;
        final width = twoCols ? (c.maxWidth - 12) / 2 : c.maxWidth;
        return Wrap(
          spacing: 12,
          runSpacing: 12,
          children: List.generate(
            9,
            (i) => SizedBox(
              width: width,
              child: _KpiDonutCard(
                index: i,
                monthTh: monthTh[i],
                monthKh: monthKh[i],
                weekTh: weekTh[i],
                weekKh: weekKh[i],
                monthElapsed: monthElapsed,
                weekElapsed: weekElapsed,
              ),
            ),
          ),
        );
      },
    );
  }
}

class _KpiDonutCard extends StatelessWidget {
  const _KpiDonutCard({
    required this.index,
    required this.monthTh,
    required this.monthKh,
    required this.weekTh,
    required this.weekKh,
    required this.monthElapsed,
    required this.weekElapsed,
  });

  final int index;
  final double monthTh;
  final double monthKh;
  final double weekTh;
  final double weekKh;
  final double monthElapsed;
  final double weekElapsed;

  @override
  Widget build(BuildContext context) {
    final now = DateTime.now();
    final monthRatio = monthKh > 0 ? monthTh / monthKh : 0.0;
    final weekRatio = weekKh > 0 ? weekTh / weekKh : 0.0;
    final monthGap = monthElapsed - monthRatio;
    final weekGap = weekElapsed - weekRatio;

    final monthStatus = _status(monthTh, monthKh, monthRatio, monthElapsed, 'tháng');
    final weekStatus = _status(weekTh, weekKh, weekRatio, weekElapsed, 'tuần');

    final monthRemain = math.max(0.0, monthKh - monthTh);
    final weekRemain = math.max(0.0, weekKh - weekTh);
    final monthDays = _monthRemainingDays(now);
    final weekDays = _weekRemainingDays(now);
    final monthPerDay = monthDays > 0 ? monthRemain / monthDays : monthRemain;
    final weekPerDay = weekDays > 0 ? weekRemain / weekDays : weekRemain;

    return Card(
      margin: EdgeInsets.zero,
      elevation: 0,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(14),
        side: BorderSide(color: monthStatus.color.withValues(alpha: 0.28)),
      ),
      child: Padding(
        padding: const EdgeInsets.all(14),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              crossAxisAlignment: CrossAxisAlignment.center,
              children: [
                ProgressDonut(
                  ratio: monthRatio,
                  label: '${(monthRatio * 100).clamp(0, 999).toStringAsFixed(0)}%',
                  progressColor: monthStatus.color,
                ),
                const SizedBox(width: 14),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        metrics[index].name,
                        style: const TextStyle(fontSize: 17, fontWeight: FontWeight.w900),
                      ),
                      const SizedBox(height: 7),
                      Text(
                        '${_fmt(monthTh)} ${metrics[index].unit}',
                        style: const TextStyle(fontSize: 21, fontWeight: FontWeight.w900, color: Color(0xFF343A40)),
                      ),
                      Text(
                        '/ KH ${_fmt(monthKh)} ${metrics[index].unit}',
                        style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w700, color: _neutralGray),
                      ),
                      const SizedBox(height: 7),
                      _statusBadge(monthStatus),
                    ],
                  ),
                ),
              ],
            ),
            const SizedBox(height: 12),
            _progressLine(
              label: 'THÁNG',
              actualRatio: monthRatio,
              elapsedRatio: monthElapsed,
              gap: monthGap,
              status: monthStatus,
            ),
            const SizedBox(height: 8),
            _progressLine(
              label: 'TUẦN',
              actualRatio: weekRatio,
              elapsedRatio: weekElapsed,
              gap: weekGap,
              status: weekStatus,
            ),
            const Divider(height: 20),
            Row(
              children: [
                Expanded(
                  child: _actionBox(
                    title: 'Còn tháng',
                    value: '${_fmt(monthRemain)} ${metrics[index].unit}',
                    note: 'BQ ${_fmt(monthPerDay)}/ngày',
                    color: monthStatus.color,
                  ),
                ),
                const SizedBox(width: 8),
                Expanded(
                  child: _actionBox(
                    title: 'Còn tuần',
                    value: '${_fmt(weekRemain)} ${metrics[index].unit}',
                    note: 'BQ ${_fmt(weekPerDay)}/ngày',
                    color: weekStatus.color,
                  ),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }

  _Status _status(double th, double kh, double ratio, double elapsed, String scope) {
    if (kh <= 0) {
      return const _Status('Không giao KH', _neutralGray, Icons.remove_circle_outline);
    }
    if (elapsed <= 0) {
      return const _Status('Chưa đến kỳ đánh giá', _neutralGray, Icons.schedule);
    }
    if (th == 0) {
      return _Status('0 kết quả $scope', _specialPurple, Icons.error_outline);
    }
    if (ratio + 1e-9 >= elapsed) {
      return const _Status('Đạt/vượt tiến độ', _okGreen, Icons.check_circle_outline);
    }
    final gap = elapsed - ratio;
    if (gap <= 0.05) {
      return const _Status('Sát tiến độ', _warnOrange, Icons.warning_amber_rounded);
    }
    return const _Status('Chậm tiến độ', _viettelRed, Icons.error_outline);
  }

  Widget _statusBadge(_Status s) => Container(
        padding: const EdgeInsets.symmetric(horizontal: 9, vertical: 5),
        decoration: BoxDecoration(
          color: s.color.withValues(alpha: 0.10),
          borderRadius: BorderRadius.circular(8),
        ),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(s.icon, size: 15, color: s.color),
            const SizedBox(width: 5),
            Flexible(
              child: Text(
                s.text,
                style: TextStyle(color: s.color, fontWeight: FontWeight.w900, fontSize: 12),
              ),
            ),
          ],
        ),
      );

  Widget _progressLine({
    required String label,
    required double actualRatio,
    required double elapsedRatio,
    required double gap,
    required _Status status,
  }) {
    final actualPct = actualRatio * 100;
    final elapsedPct = elapsedRatio * 100;
    final gapPct = gap.abs() * 100;
    final comparison = gap <= 0
        ? 'Vượt ${gapPct.toStringAsFixed(1)} điểm %'
        : 'Chậm ${gapPct.toStringAsFixed(1)} điểm %';
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 9),
      decoration: BoxDecoration(
        color: status.color.withValues(alpha: 0.06),
        borderRadius: BorderRadius.circular(10),
      ),
      child: Row(
        children: [
          SizedBox(
            width: 52,
            child: Text(label, style: TextStyle(fontSize: 11, fontWeight: FontWeight.w900, color: status.color)),
          ),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  'HT ${actualPct.toStringAsFixed(1)}%  •  Tiến độ thời gian ${elapsedPct.toStringAsFixed(1)}%',
                  style: const TextStyle(fontSize: 12, fontWeight: FontWeight.w800),
                ),
                const SizedBox(height: 3),
                LinearProgressIndicator(
                  value: actualRatio.clamp(0.0, 1.0),
                  minHeight: 6,
                  color: status.color,
                  backgroundColor: const Color(0xFFE9ECEF),
                  borderRadius: BorderRadius.circular(8),
                ),
              ],
            ),
          ),
          const SizedBox(width: 8),
          Text(
            comparison,
            style: TextStyle(fontSize: 11, fontWeight: FontWeight.w900, color: status.color),
          ),
        ],
      ),
    );
  }

  Widget _actionBox({
    required String title,
    required String value,
    required String note,
    required Color color,
  }) => Container(
        padding: const EdgeInsets.all(9),
        decoration: BoxDecoration(
          borderRadius: BorderRadius.circular(10),
          border: Border.all(color: color.withValues(alpha: 0.22)),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(title, style: const TextStyle(fontSize: 11, color: _neutralGray, fontWeight: FontWeight.w800)),
            const SizedBox(height: 2),
            Text(value, maxLines: 2, overflow: TextOverflow.ellipsis, style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w900)),
            Text(note, style: TextStyle(fontSize: 11, fontWeight: FontWeight.w800, color: color)),
          ],
        ),
      );

  int _monthRemainingDays(DateTime now) {
    final total = DateTime(now.year, now.month + 1, 0).day;
    return math.max(0, total - (now.day - 1));
  }

  int _weekRemainingDays(DateTime now) {
    final monthEnd = DateTime(now.year, now.month + 1, 0);
    var start = DateTime(now.year, now.month, 1);
    while (true) {
      var end = start.add(Duration(days: 7 - start.weekday));
      if (end.isAfter(monthEnd)) end = monthEnd;
      if (!now.isBefore(start) && !now.isAfter(end)) {
        final yesterday = DateTime(now.year, now.month, now.day).subtract(const Duration(days: 1));
        if (yesterday.isBefore(start)) return end.difference(start).inDays + 1;
        final closedEnd = yesterday.isAfter(end) ? end : yesterday;
        final closedDays = closedEnd.difference(start).inDays + 1;
        return math.max(0, end.difference(start).inDays + 1 - closedDays);
      }
      start = end.add(const Duration(days: 1));
      if (start.month != now.month) return 0;
    }
  }

  String _fmt(double v) => NumberFormat('#,##0.##', 'vi_VN').format(v);
}

class _Status {
  const _Status(this.text, this.color, this.icon);
  final String text;
  final Color color;
  final IconData icon;
}
