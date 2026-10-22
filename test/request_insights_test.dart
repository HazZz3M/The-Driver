import 'package:flutter_test/flutter_test.dart';
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
  test('areaOf takes the last part of the address, or a matching zone', () {
    expect(RequestInsights.areaOf("شارع 9، المعادي", const []), "المعادي");
    expect(RequestInsights.areaOf("التجمع الخامس - شارع التسعين", const []), "شارع التسعين");
    expect(RequestInsights.areaOf("كورنيش المعادى", const ["المعادي"]), "المعادي");
    expect(RequestInsights.areaOf("", const []), "");
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
