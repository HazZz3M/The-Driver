class ParsedRideData {
  final String id;
  final String passengerName;
  final String passengerRating;
  final double fare;
  final double pickupDistanceKm;
  final double tripDistanceKm;
  final String pickupAddress;
  final String dropoffAddress;
  final List<int> counterBids;
  final int timestamp;

  ParsedRideData({
    String? id,
    this.passengerName = "عميل إن درايف",
    this.passengerRating = "",
    required this.fare,
    required this.pickupDistanceKm,
    required this.tripDistanceKm,
    this.pickupAddress = "",
    this.dropoffAddress = "",
    this.counterBids = const [],
    int? timestamp,
  })  : id = id ?? DateTime.now().millisecondsSinceEpoch.toString(),
        timestamp = timestamp ?? DateTime.now().millisecondsSinceEpoch;

  double get totalDistanceKm => pickupDistanceKm + tripDistanceKm;

  String get fingerprint =>
      "${passengerName.trim()}_${fare.toInt()}_${pickupDistanceKm.toStringAsFixed(1)}_${tripDistanceKm.toStringAsFixed(1)}";

  Map<String, dynamic> toJson() {
    return {
      'id': id,
      'passengerName': passengerName,
      'passengerRating': passengerRating,
      'fare': fare,
      'pickupDistanceKm': pickupDistanceKm,
      'tripDistanceKm': tripDistanceKm,
      'pickupAddress': pickupAddress,
      'dropoffAddress': dropoffAddress,
      'counterBids': counterBids,
      'timestamp': timestamp,
    };
  }

  factory ParsedRideData.fromJson(Map<String, dynamic> json) {
    return ParsedRideData(
      id: json['id'] as String?,
      passengerName: json['passengerName'] as String? ?? "عميل إن درايف",
      passengerRating: json['passengerRating'] as String? ?? "",
      fare: (json['fare'] as num?)?.toDouble() ?? 0.0,
      pickupDistanceKm: (json['pickupDistanceKm'] as num?)?.toDouble() ?? 0.0,
      tripDistanceKm: (json['tripDistanceKm'] as num?)?.toDouble() ?? 0.0,
      pickupAddress: json['pickupAddress'] as String? ?? "",
      dropoffAddress: json['dropoffAddress'] as String? ?? "",
      counterBids: (json['counterBids'] as List<dynamic>?)?.map((e) => (e as num).toInt()).toList() ?? const [],
      timestamp: (json['timestamp'] as num?)?.toInt(),
    );
  }
}
