import 'dart:async';
import 'dart:convert';
import 'package:http/http.dart' as http;
import 'data.dart';

const apiEndpoint = 'https://script.google.com/macros/s/AKfycbxwDT_LV1D49SKfZkv0_CfmBcRcpubbJGnd9TFBL5b1y0AHQ-a1zbRQf83CBWDeRkaApQ/exec';

class Api {
  static const timeout = Duration(seconds:25);
  Uri uri(String action,[Map<String,String>? q]) => Uri.parse(apiEndpoint).replace(queryParameters:{'action':action,...?q});
  dynamic unwrap(http.Response r) {
    if (r.statusCode < 200 || r.statusCode >= 300) throw Exception('HTTP ${r.statusCode}');
    final x = jsonDecode(r.body);
    if (x is! Map || x['ok'] != true) throw Exception(x is Map ? (x['error'] ?? 'Lỗi API') : 'Dữ liệu API không hợp lệ');
    return x['data'];
  }
  Future<Map<String,AmData>> all() async {
    final x = unwrap(await http.get(uri('getAllAmData')).timeout(timeout)) as Map;
    return x.map((k,v)=>MapEntry(k.toString(),AmData.fromJson(k.toString(),(v as Map).cast<String,dynamic>())));
  }
  Future<Map<String,AmTarget>> targets() async {
    final x = unwrap(await http.get(uri('getTargets')).timeout(timeout)) as Map;
    final out=<String,AmTarget>{};
    for (final code in amCodes) {
      final v=x[code]; if (v is Map) out[code]=AmTarget.fromJson(v.cast<String,dynamic>());
    }
    return out;
  }
  Future<AmData> one(String code) async {
    final x=unwrap(await http.get(uri('getAmData',{'amCode':code})).timeout(timeout));
    return AmData.fromJson(code,(x as Map).cast<String,dynamic>());
  }
  Future<void> save(String code,DateTime date,List<double?> values) async {
    final ds='${date.year.toString().padLeft(4,'0')}-${date.month.toString().padLeft(2,'0')}-${date.day.toString().padLeft(2,'0')}';
    final r=await http.post(Uri.parse(apiEndpoint),headers:{'Content-Type':'application/json'},body:jsonEncode({'action':'saveEntry','amCode':code,'dateStr':ds,'values':values})).timeout(timeout);
    unwrap(r);
    final refreshed=await one(code);
    final row=refreshed.daily[ds];
    if (row==null) throw Exception('Không đọc lại được ngày $ds');
    for(var i=0;i<9;i++) {
      final e=values[i]; if(e==null) continue;
      final g=row[i]; if(g==null || (g-e).abs()>0.000001) throw Exception('Xác minh ghi KPI ${i+1} thất bại');
    }
  }
}
