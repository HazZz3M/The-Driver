import '../data/models/analysis_prefs.dart';
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

  /// Requests per area name as read (before the driver's renames and hiding), pickups and
  /// drop-offs together: what the Areas page lists.
  final Map<String, int> rawAreaCounts = {};

  /// [zones] (the driver's favourite + blacklisted zones) are used as area names when they match.
  /// [prefs]: the driver's area names, hidden areas, area source and which requests count.
  factory RequestInsights.from(List<RequestRecord> all,
      {List<String> zones = const [], DateTime? since, AnalysisPrefs prefs = const AnalysisPrefs()}) {
    final list = all.where((r) {
      if (since != null && r.firstSeen.isBefore(since)) return false;
      if (prefs.onlyOpened && !r.opened) return false;
      if (prefs.excludeEstimated && !r.distancesKnown) return false;
      return true;
    }).toList();
    final days = list.map((r) => DateTime(r.firstSeen.year, r.firstSeen.month, r.firstSeen.day)).toSet().length;
    final ins = RequestInsights._(list, days == 0 ? 1 : days);
    String area(String address) {
      final raw = rawArea(address, zones, prefs.areaSource);
      if (raw.isEmpty) return "";
      ins.rawAreaCounts[raw] = (ins.rawAreaCounts[raw] ?? 0) + 1;
      final name = prefs.rename(raw);
      return prefs.isHidden(name) ? "" : name;
    }

    for (final r in list) {
      ins.total.add(r);
      if (r.opened) ins.opened++;
      if (r.status == "BID_SENT") ins.bidsSent++;
      final h = r.firstSeen.hour;
      ins.byHour[h].add(r);
      ins.byWeekday[r.firstSeen.weekday - 1].add(r);
      final from = area(r.pickupAddress);
      final to = area(r.dropoffAddress);
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

  /// The area of [address] for the driver's chosen [source] (see AnalysisPrefs.areaSource).
  static String rawArea(String address, List<String> zones, String source) {
    switch (source) {
      case "ZONES_ONLY":
        return FareCalculatorEngine.matchZone(address, zones) ?? "";
      case "PLACE":
        final zone = FareCalculatorEngine.matchZone(address, zones);
        if (zone != null) return zone;
        final clean = address.replaceAll(_plusCode, " ").trim();
        final open = clean.indexOf('(');
        final outer = (open >= 0 ? clean.substring(0, open) : clean.split(RegExp(r'[,،]')).first).trim();
        return outer.isEmpty || _isGeneric(outer) ? areaOf(address, zones) : outer;
      default:
        return areaOf(address, zones);
    }
  }

  /// The neighbourhood of an inDrive address, used to group requests by area.
  ///
  /// inDrive (Google) addresses look like "Place (street, neighbourhood, police district, city,
  /// country)", e.g. "التجنيد (الحمراء الثانية، قسم أول أسيوط، مصر)" → "الحمراء الثانية".
  /// The country, police districts (قسم / مركز / أول / ثان), the city and plus codes are never
  /// areas; streets are used only when nothing better is left. A matching zone of the driver wins.
  static String areaOf(String address, List<String> zones) {
    final a = address.trim();
    if (a.isEmpty) return "";
    final zone = FareCalculatorEngine.matchZone(a, zones);
    if (zone != null) return zone;

    final clean = a.replaceAll(_plusCode, " ");
    final open = clean.indexOf('(');
    final outer = (open >= 0 ? clean.substring(0, open) : clean).trim();
    final inner = open >= 0 ? clean.substring(open + 1) : "";
    // Inside the parentheses, the parts read from specific to general; without them, a comma
    // list does too ("شارع 9، المعادي"). A dash is part of names ("طريق القاهرة - أسوان"), not a separator.
    final parts = (open >= 0 ? inner : clean)
        .split(RegExp(r'[(),،]'))
        .map((p) => p.trim())
        .where((p) => p.isNotEmpty)
        .toList();
    if (open < 0 && parts.length <= 1) {
      final only = parts.isEmpty ? outer : parts.first;
      return _isGeneric(only) ? a : only;
    }

    final useful = parts.where((p) => !_isGeneric(p)).toList();
    final notStreet = useful.where((p) => !_isStreet(p)).toList();
    if (notStreet.isNotEmpty) return notStreet.last;
    if (outer.isNotEmpty && !_isGeneric(outer)) return outer;
    if (useful.isNotEmpty) return useful.last;
    return outer.isNotEmpty ? outer : a;
  }

  static final _plusCode = RegExp(r'\b[0-9A-Z]{4}\+[0-9A-Z]{2,3}\b');
  static final _hasLetters = RegExp(r'\p{L}{2,}', unicode: true);
  static const _countries = {"مصر", "egypt", "جمهورية مصر العربية"};
  static const _districtPrefixes = [
    "قسم ", "مركز ", "محافظة ", "حي ", "حى ", "اول ", "أول ", "ثان ", "ثاني ", "qism ", "kism ", "markaz ",
  ];
  static const _cities = {
    "القاهرة", "cairo", "الجيزة", "giza", "الإسكندرية", "الاسكندرية", "alexandria", "أسيوط", "اسيوط", "asyut",
    "assiut", "asyout", "سوهاج", "sohag", "المنيا", "minya", "قنا", "qena", "الأقصر", "الاقصر", "luxor", "أسوان",
    "اسوان", "aswan", "بني سويف", "beni suef", "الفيوم", "faiyum", "fayoum", "طنطا", "tanta", "المنصورة", "mansoura",
    "الزقازيق", "zagazig", "بورسعيد", "port said", "السويس", "suez", "الإسماعيلية", "الاسماعيلية", "ismailia",
    "دمياط", "damietta",
  };

  /// Country, police district, city ("Asyut 2" too), digits only, or too short: not an area.
  static bool _isGeneric(String part) {
    final p = part.trim().toLowerCase();
    if (p.length < 3 || !_hasLetters.hasMatch(p)) return true;
    if (_countries.contains(p)) return true;
    if (_districtPrefixes.any((prefix) => p.startsWith(prefix))) return true;
    final cityPart = p.replaceAll(RegExp(r'\s*\d+$'), '');
    return _cities.contains(cityPart);
  }

  static bool _isStreet(String part) {
    final p = part.trim().toLowerCase();
    return p.startsWith("شارع") || p.startsWith("طريق") || p.endsWith(" street") || p.endsWith(" st") ||
        p.endsWith(" rd") || p.endsWith(" road") || p == "st";
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
