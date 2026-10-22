import '../data/models/request_record.dart';
import 'fare_calculator_engine.dart';

/// Aggregate of a group of requests (an hour of the day, an area, a route…).
class Bucket {
  int count = 0;
  int accepted = 0;
  double _fareSum = 0;
  double _ppkSum = 0;
  int _ppkCount = 0;
  double _netSum = 0;
  int _netCount = 0;
  double _kmSum = 0;
  int _kmCount = 0;
  int _ratingSum = 0;
  int ratingCount = 0;

  void add(RequestRecord r) {
    count++;
    if (r.accepted) accepted++;
    _fareSum += r.fare;
    // Profit figures only count when both distances were read (estimates would skew them).
    if (r.distancesKnown && r.profitPerKm != null) {
      _ppkSum += r.profitPerKm!;
      _ppkCount++;
    }
    if (r.distancesKnown && r.netProfit != null) {
      _netSum += r.netProfit!;
      _netCount++;
    }
    if (r.tripKnown) {
      _kmSum += r.tripKm;
      _kmCount++;
    }
    if (r.rating != null) {
      _ratingSum += r.rating!;
      ratingCount++;
    }
  }

  double get avgFare => count == 0 ? 0 : _fareSum / count;
  double? get avgProfitPerKm => _ppkCount == 0 ? null : _ppkSum / _ppkCount;
  double? get avgNet => _netCount == 0 ? null : _netSum / _netCount;
  double? get avgTripKm => _kmCount == 0 ? null : _kmSum / _kmCount;
  double? get avgRating => ratingCount == 0 ? null : _ratingSum / ratingCount;
}

class Named<T> {
  final String name;
  final T value;
  const Named(this.name, this.value);
}

/// One recommendation shown on the Insights tab.
class Recommendation {
  final String title;
  final String detail;
  final String kind; // TIME | PLACE | ROUTE | PRICE | QUALITY
  const Recommendation(this.kind, this.title, this.detail);
}

/// Trip analysis and recommendations from the request log (all requests, accepted or not).
class RequestInsights {
  final List<RequestRecord> records;
  final int days; // distinct days with data (≥ 1)
  final Bucket total = Bucket();
  final List<Bucket> byHour = List.generate(24, (_) => Bucket());
  final List<Bucket> byWeekday = List.generate(7, (_) => Bucket()); // 0 = Monday
  final Map<String, Bucket> pickupAreas = {};
  final Map<String, Bucket> dropoffAreas = {};
  final Map<String, Bucket> routes = {};
  /// Requests per pickup area and hour of day: where requests start, hour by hour.
  final Map<int, Map<String, int>> areaByHour = {};
  final Map<String, int> ratingTags = {};
  int opened = 0;
  int bidsSent = 0;

  RequestInsights._(this.records, this.days);

  /// [zones] (the driver's favourite + blacklisted zones) are used as area names when they match.
  factory RequestInsights.from(List<RequestRecord> all, {List<String> zones = const [], DateTime? since}) {
    final list = since == null ? all : all.where((r) => !r.firstSeen.isBefore(since)).toList();
    final days = list.map((r) => DateTime(r.firstSeen.year, r.firstSeen.month, r.firstSeen.day)).toSet().length;
    final ins = RequestInsights._(list, days == 0 ? 1 : days);
    for (final r in list) {
      ins.total.add(r);
      if (r.opened) ins.opened++;
      if (r.status == "BID_SENT") ins.bidsSent++;
      final h = r.firstSeen.hour;
      ins.byHour[h].add(r);
      ins.byWeekday[r.firstSeen.weekday - 1].add(r);
      final from = areaOf(r.pickupAddress, zones);
      final to = areaOf(r.dropoffAddress, zones);
      if (from.isNotEmpty) {
        ins.pickupAreas.putIfAbsent(from, Bucket.new).add(r);
        final m = ins.areaByHour.putIfAbsent(h, () => {});
        m[from] = (m[from] ?? 0) + 1;
      }
      if (to.isNotEmpty) ins.dropoffAreas.putIfAbsent(to, Bucket.new).add(r);
      if (from.isNotEmpty && to.isNotEmpty) ins.routes.putIfAbsent("$from ← $to", Bucket.new).add(r);
      for (final t in r.ratingTags) {
        ins.ratingTags[t] = (ins.ratingTags[t] ?? 0) + 1;
      }
    }
    return ins;
  }

  /// The area part of an inDrive address: a matching zone of the driver, else the last
  /// comma/dash-separated part ("شارع 9، المعادي" → "المعادي").
  static String areaOf(String address, List<String> zones) {
    final a = address.trim();
    if (a.isEmpty) return "";
    final zone = FareCalculatorEngine.matchZone(a, zones);
    if (zone != null) return zone;
    final parts = a.split(RegExp(r'[،,\-–|/]')).map((p) => p.trim()).where((p) => p.length >= 2).toList();
    return parts.isEmpty ? a : parts.last;
  }

  double get acceptanceRate => opened == 0 ? 0 : total.accepted / opened;

  /// Requests per day at each hour.
  double perDay(int hour) => byHour[hour].count / days;

  List<int> get peakHours {
    final hours = List.generate(24, (h) => h)..sort((a, b) => byHour[b].count.compareTo(byHour[a].count));
    return hours.where((h) => byHour[h].count > 0).take(3).toList();
  }

  /// Hours ranked by expected earnings: requests per day × average net profit of a request.
  List<int> get bestHours {
    double score(int h) => perDay(h) * (byHour[h].avgNet ?? 0);
    final hours = List.generate(24, (h) => h).where((h) => byHour[h].count >= 2 && (byHour[h].avgNet ?? 0) > 0).toList()
      ..sort((a, b) => score(b).compareTo(score(a)));
    return hours.take(3).toList();
  }

  List<Named<Bucket>> top(Map<String, Bucket> m, {int n = 5, bool byProfit = false}) {
    final e = m.entries.where((x) => !byProfit || (x.value.count >= 2 && x.value.avgProfitPerKm != null)).toList()
      ..sort((a, b) => byProfit
          ? b.value.avgProfitPerKm!.compareTo(a.value.avgProfitPerKm!)
          : b.value.count.compareTo(a.value.count));
    return e.take(n).map((x) => Named(x.key, x.value)).toList();
  }

  /// Pickup areas with the most requests around [hour] (that hour and the next one).
  List<Named<int>> bestAreasAt(int hour, {int n = 3}) {
    final merged = <String, int>{};
    for (final h in [hour, (hour + 1) % 24]) {
      (areaByHour[h] ?? const {}).forEach((k, v) => merged[k] = (merged[k] ?? 0) + v);
    }
    final e = merged.entries.toList()..sort((a, b) => b.value.compareTo(a.value));
    return e.take(n).map((x) => Named(x.key, x.value)).toList();
  }

  static String hourLabel(int h) {
    final h12 = h % 12 == 0 ? 12 : h % 12;
    return "$h12 ${h < 12 ? "ص" : "م"}";
  }

  static String hourRange(int h) => "${hourLabel(h)} – ${hourLabel((h + 1) % 24)}";

  static const weekdayNames = ["الاتنين", "التلات", "الأربع", "الخميس", "الجمعة", "السبت", "الحد"];

  List<Recommendation> recommendations({required DateTime now, required String currency}) {
    final out = <Recommendation>[];
    if (records.length < 5) {
      out.add(const Recommendation("TIME", "لسه البيانات قليلة",
          "شغّل المساعد وافتح طلبات في إن درايف. كل طلب بيتسجل لوحده، والتوصيات بتبان بعد يوم أو اتنين شغل."));
      return out;
    }
    final best = bestHours;
    if (best.isNotEmpty) {
      final h = best.first;
      out.add(Recommendation("TIME", "أفضل وقت تشتغل فيه: ${hourRange(h)}",
          "حوالي ${perDay(h).toStringAsFixed(1)} طلب في اليوم في الساعة دي، ومتوسط صافي الطلب "
          "${byHour[h].avgNet!.toStringAsFixed(0)} $currency."
          "${best.length > 1 ? " وبعدها: ${best.skip(1).map(hourRange).join(" و ")}." : ""}"));
    }
    final peaks = peakHours;
    if (peaks.isNotEmpty) {
      out.add(Recommendation("TIME", "ساعات الذروة (أكتر طلبات)", peaks.map(hourRange).join("، ")));
    }
    final nextHour = (now.hour + 1) % 24;
    final here = bestAreasAt(now.hour);
    if (here.isNotEmpty) {
      out.add(Recommendation("PLACE", "تقف فين دلوقتي؟",
          "في الساعة دي واللي بعدها، أكتر طلبات بتبدأ من: ${here.map((a) => "${a.name} (${a.value})").join("، ")}."));
    } else {
      final later = bestAreasAt(nextHour);
      if (later.isNotEmpty) {
        out.add(Recommendation("PLACE", "استعد للساعة الجاية", "أكتر طلبات بتبدأ من: ${later.map((a) => a.name).join("، ")}."));
      }
    }
    final profitable = top(pickupAreas, n: 3, byProfit: true);
    if (profitable.isNotEmpty) {
      out.add(Recommendation("PLACE", "أكتر مناطق بتكسّبك لكل كيلو",
          profitable.map((a) => "${a.name} (${a.value.avgProfitPerKm!.toStringAsFixed(1)} $currency/كم)").join("، ")));
    }
    final routesTop = top(routes, n: 3);
    if (routesTop.isNotEmpty && routesTop.first.value.count >= 2) {
      out.add(Recommendation("ROUTE", "أكتر مشاوير بتتكرر",
          routesTop.map((r) => "${r.name} (${r.value.count} مرة)").join("، ")));
    }
    final days = List.generate(7, (d) => d).where((d) => byWeekday[d].count > 0).toList()
      ..sort((a, b) => byWeekday[b].count.compareTo(byWeekday[a].count));
    if (days.length >= 3) {
      out.add(Recommendation("TIME", "أكتر أيام فيها طلبات", days.take(2).map((d) => weekdayNames[d]).join(" و ")));
    }
    final avgPpk = total.avgProfitPerKm;
    if (avgPpk != null) {
      final poor = records.where((r) => r.distancesKnown && (r.level == "POOR_MARGIN" || r.level == "LOSS_DANGER")).length;
      final share = poor / records.where((r) => r.distancesKnown).length.clamp(1, 1 << 30);
      if (share >= 0.5) {
        out.add(Recommendation("PRICE", "أغلب العروض أقل من هدفك",
            "${(share * 100).round()}% من الطلبات تحت هدف الربح لكل كم. زايد بالسعر المقترح بدل ما تقبل العرض، "
            "أو راجع هدفك في الإعدادات لو هو أعلى من السوق."));
      }
    }
    final rated = records.where((r) => r.rating != null).toList();
    if (rated.length >= 3) {
      final low = pickupAreas.entries.where((e) => e.value.ratingCount >= 2 && e.value.avgRating! < 3).map((e) => e.key).toList();
      out.add(Recommendation("QUALITY", "تقييم رحلاتك: ${total.avgRating!.toStringAsFixed(1)} من 5",
          low.isEmpty ? "مفيش مناطق تقييمها وحش لحد دلوقتي." : "مناطق تقييم رحلاتها ضعيف: ${low.join("، ")}. فكّر تضيفها للمحظورة."));
    }
    return out;
  }
}
