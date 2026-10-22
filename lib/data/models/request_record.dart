/// One ride request seen in inDrive, as kept on the phone by the native RequestLog.kt.
class RequestRecord {
  final int id;
  final DateTime firstSeen;
  final String status; // SEEN | OPENED | BID_SENT | ACCEPTED
  final double fare;
  final int? bidValue;
  final double pickupKm;
  final bool pickupKnown;
  final double tripKm;
  final bool tripKnown;
  final String pickupAddress;
  final String dropoffAddress;
  final String passengerName;
  final String passengerRating;
  final double? netProfit;
  final double? profitPerKm;
  final String level; // EXCELLENT_PROFIT | GOOD_PROFIT | POOR_MARGIN | LOSS_DANGER
  final double? recommendedFare;
  final List<int> bidChips;
  /// Texts of the Ride-request screen when it was opened (the "text snapshot").
  final List<String> snapshot;
  final int? rating; // 1..5
  final List<String> ratingTags;
  final String ratingNote;

  const RequestRecord({
    required this.id,
    required this.firstSeen,
    required this.status,
    required this.fare,
    this.bidValue,
    required this.pickupKm,
    required this.pickupKnown,
    required this.tripKm,
    required this.tripKnown,
    this.pickupAddress = "",
    this.dropoffAddress = "",
    this.passengerName = "",
    this.passengerRating = "",
    this.netProfit,
    this.profitPerKm,
    this.level = "",
    this.recommendedFare,
    this.bidChips = const [],
    this.snapshot = const [],
    this.rating,
    this.ratingTags = const [],
    this.ratingNote = "",
  });

  bool get opened => status != "SEEN";
  bool get accepted => status == "ACCEPTED";
  bool get distancesKnown => pickupKnown && tripKnown;
  double get totalKm => pickupKm + tripKm;

  static const statusLabels = {
    "SEEN": "ظهر في القائمة",
    "OPENED": "اتفتح",
    "BID_SENT": "اتبعتت مزايدة",
    "ACCEPTED": "اتقبل",
  };

  factory RequestRecord.fromJson(Map<String, dynamic> j) {
    double d(String k, [double fallback = 0]) => (j[k] as num?)?.toDouble() ?? fallback;
    List<T> list<T>(String k) => ((j[k] as List?) ?? const []).whereType<T>().toList();
    return RequestRecord(
      id: (j['id'] as num?)?.toInt() ?? 0,
      firstSeen: DateTime.fromMillisecondsSinceEpoch((j['firstSeen'] as num?)?.toInt() ?? 0),
      status: j['status'] as String? ?? "SEEN",
      fare: d('fare'),
      bidValue: (j['bidValue'] as num?)?.toInt(),
      pickupKm: d('pickupKm'),
      pickupKnown: j['pickupKnown'] as bool? ?? false,
      tripKm: d('tripKm'),
      tripKnown: j['tripKnown'] as bool? ?? false,
      pickupAddress: j['pickupAddress'] as String? ?? "",
      dropoffAddress: j['dropoffAddress'] as String? ?? "",
      passengerName: j['passengerName'] as String? ?? "",
      passengerRating: j['passengerRating'] as String? ?? "",
      netProfit: (j['netProfit'] as num?)?.toDouble(),
      profitPerKm: (j['profitPerKm'] as num?)?.toDouble(),
      level: j['level'] as String? ?? "",
      recommendedFare: (j['recommendedFare'] as num?)?.toDouble(),
      bidChips: list<num>('bidChips').map((e) => e.toInt()).toList(),
      snapshot: list<String>('snapshot'),
      rating: (j['rating'] as num?)?.toInt(),
      ratingTags: list<String>('ratingTags'),
      ratingNote: j['ratingNote'] as String? ?? "",
    );
  }
}
