class MetricMeta {
  const MetricMeta(this.name, this.unit, this.weight);
  final String name;
  final String unit;
  final double weight;
}

const metrics = <MetricMeta>[
  MetricMeta('Doanh thu truyền thống', 'đồng', 25),
  MetricMeta('Tổng thâm nhập vào DN MTL', 'DN', 10),
  MetricMeta('Tổng gia hạn', 'TB', 10),
  MetricMeta('Tendoo', 'TB', 15),
  MetricMeta('TB Mysign', 'TB', 5),
  MetricMeta('DT Dịch vụ mới', 'đồng', 10),
  MetricMeta('M2M/IoT', 'TB', 10),
  MetricMeta('FTTH', 'TB', 10),
  MetricMeta('Doanh thu Giáo dục, y tế', 'đồng', 5),
];

const amCodes = <String>['HOAIBT4','NUONGPM','THAODP7','LANHT22','HUEHT16','QUYENLTN'];

List<double> doubles9(dynamic v) {
  final x = v is List ? v : const [];
  return List.generate(9, (i) => i < x.length ? ((x[i] as num?)?.toDouble() ?? 0) : 0);
}

class AmData {
  AmData(this.code, this.kh, this.th, this.daily);
  final String code;
  final List<double> kh;
  final List<double> th;
  final Map<String,List<double?>> daily;

  factory AmData.fromJson(String code, Map<String,dynamic> j) {
    final d = <String,List<double?>>{};
    final raw = j['daily'];
    if (raw is Map) {
      raw.forEach((k,v) {
        final a = v is List ? v : const [];
        d[k.toString()] = List.generate(9, (i) {
          if (i >= a.length || a[i] == null || a[i] == '') return null;
          return (a[i] as num).toDouble();
        });
      });
    }
    return AmData(code, doubles9(j['khThang']), doubles9(j['thLuyKe']), d);
  }
}

class AmTarget {
  AmTarget(this.kh, this.weeks);
  final List<double> kh;
  final List<List<double>> weeks;
  factory AmTarget.fromJson(Map<String,dynamic> j) {
    final w = j['tuan'] is List ? j['tuan'] as List : const [];
    return AmTarget(doubles9(j['khThang']), List.generate(5, (i) => i < w.length ? doubles9(w[i]) : List.filled(9,0)));
  }
}

double completion(double th, double kh) => kh == 0 ? 1 : th / kh;
double score(AmData a) {
  var s = 0.0;
  for (var i=0;i<9;i++) s += completion(a.th[i], a.kh[i]).clamp(0.0,1.0) * metrics[i].weight;
  return s;
}

int currentWeekIndex(DateTime now) {
  final first = DateTime(now.year, now.month, 1);
  var start = first;
  var idx = 0;
  while (true) {
    final end = start.add(Duration(days: 7 - start.weekday));
    final capped = end.month == now.month ? end : DateTime(now.year,now.month+1,0);
    if (!now.isBefore(start) && !now.isAfter(capped)) return idx.clamp(0,4);
    start = capped.add(const Duration(days:1));
    idx++;
    if (start.month != now.month || idx >= 5) return 4;
  }
}

List<double> weekActual(AmData a, DateTime now) {
  final monday = DateTime(now.year,now.month,now.day).subtract(Duration(days: now.weekday-1));
  final closed = DateTime(now.year,now.month,now.day).subtract(const Duration(days:1));
  final out = List<double>.filled(9,0);
  a.daily.forEach((k,row) {
    final d = DateTime.tryParse(k);
    if (d == null || d.isBefore(monday) || d.isAfter(closed)) return;
    for (var i=0;i<9;i++) if (row[i] != null) out[i] += row[i]!;
  });
  return out;
}
