import 'dart:math' as math;
import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import 'api.dart';
import 'data.dart';
import 'donut.dart';

void main() => runApp(const KpiApp());

class KpiApp extends StatelessWidget {
  const KpiApp({super.key});
  @override
  Widget build(BuildContext context) => MaterialApp(
        debugShowCheckedModeBanner: false,
        title: 'KPI DNS Cao Bằng',
        theme: ThemeData(
          useMaterial3: true,
          colorScheme: ColorScheme.fromSeed(
            seedColor: const Color(0xFFE80500),
            primary: const Color(0xFFE80500),
          ),
          scaffoldBackgroundColor: const Color(0xFFF5F6F7),
        ),
        home: const Home(),
      );
}

class WeekWindow {
  const WeekWindow(this.start, this.end, this.index);
  final DateTime start;
  final DateTime end;
  final int index;
  int get totalDays => end.difference(start).inDays + 1;
}

WeekWindow targetWeekWindow(DateTime now) {
  final monthEnd = DateTime(now.year, now.month + 1, 0);
  var start = DateTime(now.year, now.month, 1);
  var idx = 0;
  while (true) {
    var end = start.add(Duration(days: 7 - start.weekday));
    if (end.isAfter(monthEnd)) end = monthEnd;
    if (!now.isBefore(start) && !now.isAfter(end)) {
      return WeekWindow(start, end, idx.clamp(0, 4));
    }
    start = end.add(const Duration(days: 1));
    idx++;
    if (start.month != now.month || idx >= 5) return WeekWindow(monthEnd, monthEnd, 4);
  }
}

double monthElapsedRatio(DateTime now) {
  final total = DateTime(now.year, now.month + 1, 0).day;
  final closed = (now.day - 1).clamp(0, total);
  return total == 0 ? 0 : closed / total;
}

double weekElapsedRatio(DateTime now) {
  final w = targetWeekWindow(now);
  final yesterday = DateTime(now.year, now.month, now.day).subtract(const Duration(days: 1));
  if (yesterday.isBefore(w.start)) return 0;
  final end = yesterday.isAfter(w.end) ? w.end : yesterday;
  final closed = end.difference(w.start).inDays + 1;
  return w.totalDays == 0 ? 0 : closed / w.totalDays;
}

int monthRemainingDays(DateTime now) {
  final total = DateTime(now.year, now.month + 1, 0).day;
  return math.max(0, total - (now.day - 1));
}

int weekRemainingDays(DateTime now) {
  final w = targetWeekWindow(now);
  final closed = (weekElapsedRatio(now) * w.totalDays).round();
  return math.max(0, w.totalDays - closed);
}

List<double> weekActualForRange(AmData a, DateTime now) {
  final w = targetWeekWindow(now);
  final yesterday = DateTime(now.year, now.month, now.day).subtract(const Duration(days: 1));
  final out = List<double>.filled(9, 0);
  a.daily.forEach((k, row) {
    final d = DateTime.tryParse(k);
    if (d == null || d.isBefore(w.start) || d.isAfter(w.end) || d.isAfter(yesterday)) return;
    for (var i = 0; i < 9; i++) {
      if (row[i] != null) out[i] += row[i]!;
    }
  });
  return out;
}

class AlertItem {
  const AlertItem({
    required this.scope,
    required this.metricIndex,
    required this.actualRatio,
    required this.elapsedRatio,
    required this.remaining,
    required this.requiredPerDay,
  });
  final String scope;
  final int metricIndex;
  final double actualRatio;
  final double elapsedRatio;
  final double remaining;
  final double requiredPerDay;
  double get gap => elapsedRatio - actualRatio;
}

List<AlertItem> buildAlerts({
  required List<double> monthTh,
  required List<double> monthKh,
  required List<double> weekTh,
  required List<double> weekKh,
  required DateTime now,
}) {
  final out = <AlertItem>[];
  final mElapsed = monthElapsedRatio(now);
  final wElapsed = weekElapsedRatio(now);
  final mDays = monthRemainingDays(now);
  final wDays = weekRemainingDays(now);
  for (var i = 0; i < 9; i++) {
    if (monthKh[i] > 0 && mElapsed > 0) {
      final ratio = monthTh[i] / monthKh[i];
      if (ratio < mElapsed) {
        final remain = math.max(0.0, monthKh[i] - monthTh[i]);
        out.add(AlertItem(
          scope: 'THÁNG',
          metricIndex: i,
          actualRatio: ratio,
          elapsedRatio: mElapsed,
          remaining: remain,
          requiredPerDay: mDays > 0 ? remain / mDays : remain,
        ));
      }
    }
    if (weekKh[i] > 0 && wElapsed > 0) {
      final ratio = weekTh[i] / weekKh[i];
      if (ratio < wElapsed) {
        final remain = math.max(0.0, weekKh[i] - weekTh[i]);
        out.add(AlertItem(
          scope: 'TUẦN',
          metricIndex: i,
          actualRatio: ratio,
          elapsedRatio: wElapsed,
          remaining: remain,
          requiredPerDay: wDays > 0 ? remain / wDays : remain,
        ));
      }
    }
  }
  out.sort((a, b) => b.gap.compareTo(a.gap));
  return out;
}

class EvaluationPanel extends StatelessWidget {
  const EvaluationPanel({
    super.key,
    required this.monthTh,
    required this.monthKh,
    required this.weekTh,
    required this.weekKh,
    required this.now,
    this.compact = false,
  });
  final List<double> monthTh;
  final List<double> monthKh;
  final List<double> weekTh;
  final List<double> weekKh;
  final DateTime now;
  final bool compact;

  int slowCount(List<double> th, List<double> kh, double elapsed) {
    if (elapsed <= 0) return 0;
    var c = 0;
    for (var i = 0; i < 9; i++) {
      if (kh[i] > 0 && th[i] / kh[i] < elapsed) c++;
    }
    return c;
  }

  @override
  Widget build(BuildContext context) {
    final w = targetWeekWindow(now);
    final mElapsed = monthElapsedRatio(now);
    final wElapsed = weekElapsedRatio(now);
    final alerts = buildAlerts(
      monthTh: monthTh,
      monthKh: monthKh,
      weekTh: weekTh,
      weekKh: weekKh,
      now: now,
    );
    final shown = compact ? alerts.take(6).toList() : alerts;
    return Card(
      margin: const EdgeInsets.symmetric(vertical: 8),
      child: Padding(
        padding: const EdgeInsets.all(14),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Row(children: [
              Icon(Icons.warning_amber_rounded, color: Color(0xFFE80500)),
              SizedBox(width: 8),
              Expanded(child: Text('ĐÁNH GIÁ & CẢNH BÁO TIẾN ĐỘ', style: TextStyle(fontSize: 17, fontWeight: FontWeight.w900))),
            ]),
            const SizedBox(height: 10),
            summary('Tháng', mElapsed, slowCount(monthTh, monthKh, mElapsed), 'Số liệu chốt N-1: ${DateFormat('dd/MM').format(now.subtract(const Duration(days: 1)))}'),
            const SizedBox(height: 6),
            summary('Tuần ${w.index + 1}', wElapsed, slowCount(weekTh, weekKh, wElapsed), '${DateFormat('dd/MM').format(w.start)}-${DateFormat('dd/MM').format(w.end)}'),
            const Divider(height: 22),
            if (alerts.isEmpty)
              const Row(children: [
                Icon(Icons.check_circle, color: Colors.green),
                SizedBox(width: 8),
                Expanded(child: Text('Không có chỉ tiêu chậm hơn tiến độ thời gian tại kỳ chốt N-1.', style: TextStyle(fontWeight: FontWeight.w700))),
              ])
            else ...[
              Text('Cảnh báo ưu tiên (${alerts.length})', style: const TextStyle(color: Color(0xFFE80500), fontWeight: FontWeight.w900)),
              const SizedBox(height: 6),
              ...shown.map(alertRow),
              if (compact && alerts.length > shown.length)
                Text('Còn ${alerts.length - shown.length} cảnh báo khác — xem chi tiết tại từng AM.', style: const TextStyle(fontStyle: FontStyle.italic)),
            ],
          ],
        ),
      ),
    );
  }

  Widget summary(String label, double elapsed, int slow, String note) {
    final color = elapsed <= 0 ? Colors.blueGrey : (slow == 0 ? Colors.green : const Color(0xFFE80500));
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(10),
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.08),
        borderRadius: BorderRadius.circular(10),
        border: Border.all(color: color.withValues(alpha: 0.25)),
      ),
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Text(
          elapsed <= 0 ? '$label: chưa đến kỳ đánh giá' : '$label: $slow/9 chỉ tiêu chậm • thời gian đã qua ${(elapsed * 100).toStringAsFixed(1)}%',
          style: TextStyle(color: color, fontWeight: FontWeight.w900),
        ),
        Text(note, style: const TextStyle(fontSize: 12)),
      ]),
    );
  }

  Widget alertRow(AlertItem a) {
    final m = metrics[a.metricIndex];
    return Container(
      width: double.infinity,
      margin: const EdgeInsets.only(bottom: 8),
      padding: const EdgeInsets.all(10),
      decoration: BoxDecoration(
        color: const Color(0xFFFFF3F2),
        borderRadius: BorderRadius.circular(10),
        border: Border.all(color: const Color(0xFFFFD0CC)),
      ),
      child: Text(
        '[${a.scope}] ${m.name}: HT ${(a.actualRatio * 100).toStringAsFixed(1)}% < tiến độ ${(a.elapsedRatio * 100).toStringAsFixed(1)}% '
        '(chậm ${(a.gap * 100).toStringAsFixed(1)} điểm %). Còn ${fmt(a.remaining)} ${m.unit}; yêu cầu ${fmt(a.requiredPerDay)} ${m.unit}/ngày.',
        style: const TextStyle(fontWeight: FontWeight.w700),
      ),
    );
  }
}

class Home extends StatefulWidget {
  const Home({super.key});
  @override
  State<Home> createState() => _HomeState();
}

class _HomeState extends State<Home> {
  final api = Api();
  bool loading = true;
  String? error;
  Map<String, AmData> all = {};
  Map<String, AmTarget> targets = {};

  @override
  void initState() {
    super.initState();
    load();
  }

  Future<void> load() async {
    setState(() { loading = true; error = null; });
    try {
      final r = await Future.wait([api.all(), api.targets()]);
      all = r[0] as Map<String, AmData>;
      targets = r[1] as Map<String, AmTarget>;
    } catch (e) {
      error = '$e';
    }
    if (mounted) setState(() => loading = false);
  }

  @override
  Widget build(BuildContext context) => Scaffold(
        appBar: AppBar(
          backgroundColor: const Color(0xFFE80500),
          foregroundColor: Colors.white,
          title: const Text('KPI DNS - Viettel Cao Bằng'),
          actions: [IconButton(onPressed: load, icon: const Icon(Icons.refresh))],
        ),
        body: loading
            ? const Center(child: CircularProgressIndicator())
            : error != null
                ? Center(child: Padding(
                    padding: const EdgeInsets.all(24),
                    child: Column(mainAxisSize: MainAxisSize.min, children: [
                      Text('Không lấy được dữ liệu\n$error', textAlign: TextAlign.center),
                      const SizedBox(height: 12),
                      FilledButton(onPressed: load, child: const Text('Thử lại')),
                    ]),
                  ))
                : bodyContent(),
      );

  Widget titleBar(String s) => Container(
        margin: const EdgeInsets.only(top: 12),
        padding: const EdgeInsets.all(12),
        decoration: BoxDecoration(color: const Color(0xFFE80500), borderRadius: BorderRadius.circular(10)),
        child: Text(s, style: const TextStyle(color: Colors.white, fontSize: 19, fontWeight: FontWeight.w900)),
      );

  Widget bodyContent() {
    final now = DateTime.now();
    final wi = targetWeekWindow(now).index;
    final totalKh = List.generate(9, (i) => all.values.fold<double>(0, (s, a) => s + a.kh[i]));
    final totalTh = List.generate(9, (i) => all.values.fold<double>(0, (s, a) => s + a.th[i]));
    final totalWeekTh = List<double>.filled(9, 0);
    for (final a in all.values) {
      final w = weekActualForRange(a, now);
      for (var i = 0; i < 9; i++) totalWeekTh[i] += w[i];
    }
    final totalWeekKh = List.generate(9, (i) => targets.values.fold<double>(0, (s, t) => s + t.weeks[wi][i]));
    final ranks = all.values.toList()..sort((a, b) => score(b).compareTo(score(a)));

    return RefreshIndicator(
      onRefresh: load,
      child: ListView(
        padding: const EdgeInsets.all(12),
        children: [
          titleBar('1. Tóm tắt kết quả toàn tỉnh'),
          const SizedBox(height: 10),
          KpiDonutGrid(
            monthTh: totalTh,
            monthKh: totalKh,
            weekTh: totalWeekTh,
            weekKh: totalWeekKh,
            monthElapsed: monthElapsedRatio(now),
            weekElapsed: weekElapsedRatio(now),
          ),
          titleBar('2. Đánh giá & cảnh báo'),
          EvaluationPanel(
            monthTh: totalTh,
            monthKh: totalKh,
            weekTh: totalWeekTh,
            weekKh: totalWeekKh,
            now: now,
            compact: true,
          ),
          titleBar('3. Xếp hạng PL05'),
          ...List.generate(ranks.length, (i) => Card(
                child: ListTile(
                  leading: CircleAvatar(child: Text('${i + 1}')),
                  title: Text(ranks[i].code, style: const TextStyle(fontWeight: FontWeight.w800)),
                  trailing: Text('${score(ranks[i]).toStringAsFixed(1)}/100', style: const TextStyle(fontWeight: FontWeight.w900)),
                  onTap: () => openAm(ranks[i].code),
                ),
              )),
          titleBar('4. 6 Account Manager'),
          Wrap(
            spacing: 8,
            runSpacing: 8,
            children: amCodes.map((x) => FilledButton.tonal(onPressed: () => openAm(x), child: Text(x))).toList(),
          ),
          const SizedBox(height: 28),
        ],
      ),
    );
  }

  void openAm(String code) {
    final t = targets[code];
    if (t == null) return;
    Navigator.push(context, MaterialPageRoute(builder: (_) => AmPage(code: code, target: t))).then((_) => load());
  }
}

class AmPage extends StatefulWidget {
  const AmPage({super.key, required this.code, required this.target});
  final String code;
  final AmTarget target;
  @override
  State<AmPage> createState() => _AmPageState();
}

class _AmPageState extends State<AmPage> with SingleTickerProviderStateMixin {
  final api = Api();
  late TabController tabs;
  AmData? data;
  String? error;
  bool saving = false;
  DateTime date = DateTime.now();
  final ctrls = List.generate(9, (_) => TextEditingController());

  @override
  void initState() {
    super.initState();
    tabs = TabController(length: 2, vsync: this);
    load();
  }

  Future<void> load() async {
    try {
      data = await api.one(widget.code);
      error = null;
      fill();
    } catch (e) {
      error = '$e';
    }
    if (mounted) setState(() {});
  }

  void fill() {
    final d = data;
    if (d == null) return;
    final key = '${date.year.toString().padLeft(4, '0')}-${date.month.toString().padLeft(2, '0')}-${date.day.toString().padLeft(2, '0')}';
    final row = d.daily[key];
    for (var i = 0; i < 9; i++) {
      final v = row == null ? null : row[i];
      ctrls[i].text = v == null ? '' : (v == v.roundToDouble() ? v.toInt().toString() : v.toString());
    }
  }

  Future<void> pick() async {
    final now = DateTime.now();
    final p = await showDatePicker(
      context: context,
      initialDate: date,
      firstDate: DateTime(now.year, now.month, 1),
      lastDate: DateTime(now.year, now.month + 1, 0),
    );
    if (p != null) { date = p; fill(); setState(() {}); }
  }

  Future<void> save() async {
    final vals = <double?>[];
    for (final c in ctrls) {
      final s = c.text.trim().replaceAll(',', '.');
      if (s.isEmpty) { vals.add(null); continue; }
      final v = double.tryParse(s);
      if (v == null || v < 0) { snack('Có giá trị không hợp lệ'); return; }
      vals.add(v);
    }
    setState(() => saving = true);
    try {
      await api.save(widget.code, date, vals);
      await load();
      snack('Đã lưu và xác minh dữ liệu');
    } catch (e) {
      snack('Lỗi lưu: $e');
    }
    if (mounted) setState(() => saving = false);
  }

  void snack(String s) {
    if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(s)));
  }

  @override
  Widget build(BuildContext context) => Scaffold(
        appBar: AppBar(
          backgroundColor: const Color(0xFFE80500),
          foregroundColor: Colors.white,
          title: Text(widget.code),
          bottom: TabBar(
            controller: tabs,
            labelColor: Colors.white,
            unselectedLabelColor: Colors.white70,
            tabs: const [Tab(text: 'Nhập KQ'), Tab(text: 'Tiến độ')],
          ),
        ),
        body: TabBarView(controller: tabs, children: [entry(), progress()]),
      );

  Widget entry() => ListView(
        padding: const EdgeInsets.all(14),
        children: [
          OutlinedButton.icon(onPressed: pick, icon: const Icon(Icons.calendar_month), label: Text(DateFormat('dd/MM/yyyy').format(date))),
          ...List.generate(9, (i) => Padding(
                padding: const EdgeInsets.symmetric(vertical: 5),
                child: TextField(
                  controller: ctrls[i],
                  keyboardType: const TextInputType.numberWithOptions(decimal: true),
                  decoration: InputDecoration(
                    border: const OutlineInputBorder(),
                    labelText: '${i + 1}. ${metrics[i].name} (${metrics[i].unit})',
                    helperText: 'Điểm chuẩn ${metrics[i].weight.toStringAsFixed(0)}',
                  ),
                ),
              )),
          const SizedBox(height: 10),
          FilledButton.icon(onPressed: saving ? null : save, icon: const Icon(Icons.save), label: Text(saving ? 'Đang lưu...' : 'Lưu kết quả')),
          const Padding(
            padding: EdgeInsets.only(top: 10),
            child: Text('Để trống = chưa nhập; nhập 0 = đã xác nhận không phát sinh.', style: TextStyle(color: Color(0xFFE80500), fontWeight: FontWeight.w800)),
          ),
        ],
      );

  Widget progress() {
    final a = data;
    if (error != null) return Center(child: Text(error!));
    if (a == null) return const Center(child: CircularProgressIndicator());
    final now = DateTime.now();
    final wi = targetWeekWindow(now).index;
    final wa = weekActualForRange(a, now);
    final wp = widget.target.weeks[wi];
    return ListView(
      padding: const EdgeInsets.all(12),
      children: [
        Card(
          child: Padding(
            padding: const EdgeInsets.all(18),
            child: Column(children: [
              const Text('Điểm PL05 lũy kế tháng', style: TextStyle(fontWeight: FontWeight.w700)),
              Text('${score(a).toStringAsFixed(1)}/100', style: const TextStyle(fontSize: 36, fontWeight: FontWeight.w900, color: Color(0xFFE80500))),
            ]),
          ),
        ),
        EvaluationPanel(monthTh: a.th, monthKh: a.kh, weekTh: wa, weekKh: wp, now: now),
        Text('Tuần hiện tại: Tuần ${wi + 1}', style: const TextStyle(fontSize: 19, fontWeight: FontWeight.w900)),
        const SizedBox(height: 10),
        KpiDonutGrid(
          monthTh: a.th,
          monthKh: a.kh,
          weekTh: wa,
          weekKh: wp,
          monthElapsed: monthElapsedRatio(now),
          weekElapsed: weekElapsedRatio(now),
        ),
      ],
    );
  }

  @override
  void dispose() {
    tabs.dispose();
    for (final c in ctrls) c.dispose();
    super.dispose();
  }
}

String fmt(double v) => NumberFormat('#,##0.##', 'vi_VN').format(v);
