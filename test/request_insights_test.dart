import 'package:flutter_test/flutter_test.dart';
import 'package:indrive_assistant/data/models/analysis_prefs.dart';
import 'package:indrive_assistant/data/models/request_record.dart';
import 'package:indrive_assistant/engine/request_insights.dart';

RequestRecord rec(DateTime at, {String from = "", String to = "", String status = "SEEN", double net = 40, int? rating}) =>
    RequestRecord(
      id: at.millisecondsSinceEpoch,
      firstSeen: at,
      status: status,
      fare: 100,
      pickupKm: 2,
      pickupKnown: true,
      tripKm: 8,
      tripKnown: true,
      pickupAddress: from,
      dropoffAddress: to,
      netProfit: net,
      profitPerKm: net / 10,
      rating: rating,
    );

void main() {
  group('areaOf (real inDrive addresses)', () {
    String area(String a, [List<String> zones = const []]) => RequestInsights.areaOf(a, zones);

    test('the neighbourhood inside the parentheses, never the country', () {
      expect(area("التجنيد (الحمراء الثانية، قسم أول أسيوط، مصر)"), "الحمراء الثانية");
      expect(area("مستشفى سانت ماريا (الحكمدار، حى شرق، مركز أسيوط، مصر)"), "الحكمدار");
      expect(area("House Cafe (شارع تقسيم الطرابيشي، الوليدية القبلية، قسم ثان أسيوط، مصر)"), "الوليدية القبلية");
      expect(area("ابراج التوحيد القديمة (يسري راغب، الحمراء الثانية، اول السادات، مصر)"), "الحمراء الثانية");
      expect(area("Gisr Al Soltan (Al Hamraa Ath Thaneyah, Asyut 1)"), "Al Hamraa Ath Thaneyah");
      expect(area("Vodafone (El-Gomhoreya, st, Asyut 2)"), "El-Gomhoreya");
      expect(area("التكية (مول داون تاون، مدينة اسيوط الجديدة، مصر)"), "مدينة اسيوط الجديدة");
    });

    test('only country / city in the parentheses: the place before them', () {
      expect(area("الوليدية (مصر)"), "الوليدية");
      expect(area("الإبراهيمية (مركز أسيوط)"), "الإبراهيمية");
      expect(area("Al Ibrahimeya (Asyut)"), "Al Ibrahimeya");
      expect(area("اسيوط الجديدة (Egypt)"), "اسيوط الجديدة");
    });

    test('no parentheses: last comma part, dashes kept, plus codes dropped', () {
      expect(area("شارع 9، المعادي"), "المعادي");
      expect(area("طريق القاهرة - أسوان الصحراوي الشرقي"), "طريق القاهرة - أسوان الصحراوي الشرقي");
      expect(area("مدينة مبارك"), "مدينة مبارك");
      expect(area("55PG+444، الهلالي"), "الهلالي");
    });

    test('a matching zone wins; empty stays empty', () {
      expect(area("كورنيش المعادى", const ["المعادي"]), "المعادي");
      expect(area(""), "");
    });

    test('never returns the country', () {
      for (final a in ["(دوار العمدة (آل علي (بني مر، الفتح، مصر)", "أسيوط (مصر)", "2070122 (قسم أول أسيوط، مصر)"]) {
        expect(area(a), isNot(anyOf("مصر", "مصر)", "Egypt")), reason: a);
      }
    });
  });

  test('peak hours, areas, routes and acceptance are counted', () {
    final d = DateTime(2026, 10, 20);
    final list = [
      for (var i = 0; i < 5; i++) rec(d.add(Duration(hours: 18, minutes: i)), from: "x، المعادي", to: "y، الدقي"),
      for (var i = 0; i < 2; i++) rec(d.add(Duration(hours: 9, minutes: i)), from: "z، التجمع", to: "w، المعادي"),
      rec(d.add(const Duration(hours: 9, minutes: 30)), from: "z، التجمع", status: "ACCEPTED", rating: 2),
    ];
    final ins = RequestInsights.from(list);
    expect(ins.total.count, 8);
    expect(ins.peakHours.first, 18);
    expect(ins.top(ins.pickupAreas).first.name, "المعادي");
    expect(ins.top(ins.routes).first.name, "المعادي ← الدقي");
    expect(ins.opened, 1);
    expect(ins.acceptanceRate, 1.0);
    expect(ins.bestAreasAt(18).first.name, "المعادي");
  });

  group('analysis prefs (area names)', () {
    test('Arabic and English spellings of a place share a skeleton', () {
      expect(AnalysisPrefs.skeleton("الحمراء الثانية"), AnalysisPrefs.skeleton("Al Hamraa Ath Thaneyah"));
      expect(AnalysisPrefs.skeleton("الجمهورية"), AnalysisPrefs.skeleton("El-Gomhoreya"));
      expect(AnalysisPrefs.skeleton("الحمراء الثانيه"), AnalysisPrefs.skeleton("الحمرا الثانية"));
      expect(AnalysisPrefs.skeleton("المعادي"), isNot(AnalysisPrefs.skeleton("الدقي")));
    });

    test('suggestMerges groups same-place names, most requests first', () {
      final s = AnalysisPrefs.suggestMerges({"Al Hamraa Ath Thaneyah": 2, "الحمراء الثانية": 9, "المعادي": 4});
      expect(s, [
        ["الحمراء الثانية", "Al Hamraa Ath Thaneyah"]
      ]);
      expect(AnalysisPrefs.suggestMerges({"Al Hamraa Ath Thaneyah": 2, "الحمراء الثانية": 9},
          ignore: {AnalysisPrefs.skeleton("الحمراء الثانية")}), isEmpty);
    });

    test('merge, rename chains and unmerge', () {
      var a = const AnalysisPrefs().merge(["Al Hamraa Ath Thaneyah", "الحمراء الثانية"], "الحمراء الثانية");
      expect(a.rename("Al Hamraa Ath Thaneyah"), "الحمراء الثانية");
      a = a.merge(["الحمراء الثانية"], "الحمرا");
      expect(a.rename("Al Hamraa Ath Thaneyah"), "الحمرا");
      expect(a.rename("الحمراء الثانية"), "الحمرا");
      expect(a.unmerge("الحمرا").aliases, isEmpty);
    });

    test('insights apply merges, hiding, area source and filters', () {
      final d = DateTime(2026, 10, 20, 18);
      final list = [
        rec(d, from: "x (الحمراء الثانية، قسم أول أسيوط، مصر)", to: "y (المعادي، مصر)", status: "OPENED"),
        rec(d.add(const Duration(minutes: 1)), from: "z (Al Hamraa Ath Thaneyah, Asyut 1)", to: "w (الدقي، مصر)"),
      ];
      final merged = const AnalysisPrefs().merge(["Al Hamraa Ath Thaneyah"], "الحمراء الثانية");
      var ins = RequestInsights.from(list, prefs: merged);
      expect(ins.pickupAreas.keys, ["الحمراء الثانية"]);
      expect(ins.pickupAreas["الحمراء الثانية"]!.count, 2);
      expect(ins.rawAreaCounts.length, 4);

      ins = RequestInsights.from(list, prefs: merged.copyWith(hidden: {"الدقي"}));
      expect(ins.dropoffAreas.keys, ["المعادي"]);

      ins = RequestInsights.from(list, prefs: const AnalysisPrefs(areaSource: "PLACE"));
      expect(ins.pickupAreas.keys.toSet(), {"الحمراء الثانية", "Al Hamraa Ath Thaneyah"}); // x / z are too short

      ins = RequestInsights.from(list, prefs: const AnalysisPrefs(areaSource: "ZONES_ONLY"), zones: const ["المعادي"]);
      expect(ins.pickupAreas, isEmpty);
      expect(ins.dropoffAreas.keys, ["المعادي"]);

      expect(RequestInsights.from(list, prefs: const AnalysisPrefs(onlyOpened: true)).total.count, 1);
    });
  });

  test('few records give the "not enough data" recommendation', () {
    final ins = RequestInsights.from([rec(DateTime(2026, 10, 20, 12))]);
    final recs = ins.recommendations(now: DateTime(2026, 10, 21, 18), currency: "ج.م");
    expect(recs.single.title, contains("قليلة"));
  });

  test('RequestRecord parses the native JSON', () {
    final r = RequestRecord.fromJson({
      'id': 5,
      'firstSeen': 1000,
      'status': 'ACCEPTED',
      'fare': 85,
      'pickupKm': 1.5,
      'pickupKnown': true,
      'tripKm': 7,
      'tripKnown': false,
      'bidChips': [90, 95],
      'snapshot': ['Ride request', 'EGP 85'],
      'rating': 4,
      'ratingTags': ['راكب محترم'],
    });
    expect(r.accepted, isTrue);
    expect(r.distancesKnown, isFalse);
    expect(r.bidChips, [90, 95]);
    expect(r.snapshot.length, 2);
    expect(r.rating, 4);
  });
}
