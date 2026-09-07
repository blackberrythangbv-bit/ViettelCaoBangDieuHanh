import 'dart:math' as math;
import 'package:flutter/material.dart';
import 'data.dart';

class ProgressDonut extends StatelessWidget {
  const ProgressDonut({
    super.key,
    required this.ratio,
    required this.label,
    required this.progressColor,
    this.size = 92,
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
        duration: const Duration(milliseconds: 500),
        builder: (context, value, _) => CustomPaint(
          painter: _DonutPainter(value, progressColor),
          child: Center(
            child: Text(
              label,
              style: const TextStyle(fontSize: 17, fontWeight: FontWeight.w900),
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
    final radius = math.min(size.width, size.height) / 2 - 8;
    final track = Paint()
      ..color = const Color(0xFFE9ECEF)
      ..style = PaintingStyle.stroke
      ..strokeWidth = 11
      ..strokeCap = StrokeCap.round;
    final progress = Paint()
      ..color = color
      ..style = PaintingStyle.stroke
      ..strokeWidth = 11
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
        final twoCols = c.maxWidth >= 680;
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
    final mRatio = monthKh > 0 ? monthTh / monthKh : 0.0;
    final wRatio = weekKh > 0 ? weekTh / weekKh : 0.0;
    final mOk = monthElapsed <= 0 || mRatio + 1e-9 >= monthElapsed;
    final wOk = weekElapsed <= 0 || weekKh <= 0 || wRatio + 1e-9 >= weekElapsed;
    final ok = mOk && wOk;
    final color = ok ? Colors.green : const Color(0xFFE80500);
    final monthPct = (mRatio * 100).clamp(0, 999).toStringAsFixed(0);
    final weekPct = (wRatio * 100).clamp(0, 999).toStringAsFixed(0);
    final mGap = math.max(0.0, (monthElapsed - mRatio) * 100);
    final wGap = math.max(0.0, (weekElapsed - wRatio) * 100);

    String status;
    if (ok) {
      status = 'Đạt tiến độ';
    } else if (!mOk && !wOk) {
      status = 'Chậm T ${mGap.toStringAsFixed(1)}đ% • W ${wGap.toStringAsFixed(1)}đ%';
    } else if (!mOk) {
      status = 'Chậm tháng ${mGap.toStringAsFixed(1)}đ%';
    } else {
      status = 'Chậm tuần ${wGap.toStringAsFixed(1)}đ%';
    }

    return Card(
      margin: EdgeInsets.zero,
      child: Padding(
        padding: const EdgeInsets.all(14),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              metrics[index].name,
              style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w900),
            ),
            const SizedBox(height: 10),
            Row(
              children: [
                Expanded(
                  child: Column(
                    children: [
                      ProgressDonut(
                        ratio: mRatio,
                        label: '$monthPct%',
                        progressColor: color,
                      ),
                      const SizedBox(height: 4),
                      const Text('THÁNG', style: TextStyle(fontSize: 11, fontWeight: FontWeight.w900)),
                    ],
                  ),
                ),
                const SizedBox(width: 10),
                Expanded(
                  child: Column(
                    children: [
                      ProgressDonut(
                        ratio: wRatio,
                        label: '$weekPct%',
                        progressColor: wOk ? Colors.green : const Color(0xFFE80500),
                      ),
                      const SizedBox(height: 4),
                      const Text('TUẦN', style: TextStyle(fontSize: 11, fontWeight: FontWeight.w900)),
                    ],
                  ),
                ),
              ],
            ),
            const SizedBox(height: 10),
            Text('Tháng: ${_fmt(monthTh)} / ${_fmt(monthKh)} ${metrics[index].unit}'),
            Text('Tuần: ${_fmt(weekTh)} / ${_fmt(weekKh)} ${metrics[index].unit}'),
            const SizedBox(height: 8),
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 9, vertical: 5),
              decoration: BoxDecoration(
                color: color.withValues(alpha: 0.10),
                borderRadius: BorderRadius.circular(8),
              ),
              child: Text(
                status,
                style: TextStyle(color: color, fontWeight: FontWeight.w900),
              ),
            ),
          ],
        ),
      ),
    );
  }

  String _fmt(double v) {
    if (v == v.roundToDouble()) return v.toInt().toString();
    return v.toStringAsFixed(2);
  }
}
