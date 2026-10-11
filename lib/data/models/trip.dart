class Trip {
  final int id;
  final int timestamp;
  final double pickupDistanceKm;
  final double tripDistanceKm;
  final int estimatedMinutes;
  final double offeredFare;
  final double fuelCost;
  final double commissionCost;
  final double wearAndTearCost;
  final double netProfit;
  final double profitPerKm;
  final String status; // OFFERED, ACCEPTED, REJECTED, COMPLETED
  final String pickupLocation;
  final String dropoffLocation;
  final String notes;

  Trip({
    int? id,
    int? timestamp,
    required this.pickupDistanceKm,
    required this.tripDistanceKm,
    this.estimatedMinutes = 20,
    required this.offeredFare,
    required this.fuelCost,
    required this.commissionCost,
    required this.wearAndTearCost,
    required this.netProfit,
    required this.profitPerKm,
    this.status = "COMPLETED",
    this.pickupLocation = "",
    this.dropoffLocation = "",
    this.notes = "",
  })  : id = id ?? DateTime.now().millisecondsSinceEpoch,
        timestamp = timestamp ?? DateTime.now().millisecondsSinceEpoch;

  double get totalDistanceKm => pickupDistanceKm + tripDistanceKm;

  Trip copyWith({
    int? id,
    int? timestamp,
    double? pickupDistanceKm,
    double? tripDistanceKm,
    int? estimatedMinutes,
    double? offeredFare,
    double? fuelCost,
    double? commissionCost,
    double? wearAndTearCost,
    double? netProfit,
    double? profitPerKm,
    String? status,
    String? pickupLocation,
    String? dropoffLocation,
    String? notes,
  }) {
    return Trip(
      id: id ?? this.id,
      timestamp: timestamp ?? this.timestamp,
      pickupDistanceKm: pickupDistanceKm ?? this.pickupDistanceKm,
      tripDistanceKm: tripDistanceKm ?? this.tripDistanceKm,
      estimatedMinutes: estimatedMinutes ?? this.estimatedMinutes,
      offeredFare: offeredFare ?? this.offeredFare,
      fuelCost: fuelCost ?? this.fuelCost,
      commissionCost: commissionCost ?? this.commissionCost,
      wearAndTearCost: wearAndTearCost ?? this.wearAndTearCost,
      netProfit: netProfit ?? this.netProfit,
      profitPerKm: profitPerKm ?? this.profitPerKm,
      status: status ?? this.status,
      pickupLocation: pickupLocation ?? this.pickupLocation,
      dropoffLocation: dropoffLocation ?? this.dropoffLocation,
      notes: notes ?? this.notes,
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'id': id,
      'timestamp': timestamp,
      'pickupDistanceKm': pickupDistanceKm,
      'tripDistanceKm': tripDistanceKm,
      'estimatedMinutes': estimatedMinutes,
      'offeredFare': offeredFare,
      'fuelCost': fuelCost,
      'commissionCost': commissionCost,
      'wearAndTearCost': wearAndTearCost,
      'netProfit': netProfit,
      'profitPerKm': profitPerKm,
      'status': status,
      'pickupLocation': pickupLocation,
      'dropoffLocation': dropoffLocation,
      'notes': notes,
    };
  }

  factory Trip.fromJson(Map<String, dynamic> json) {
    return Trip(
      id: (json['id'] as num?)?.toInt(),
      timestamp: (json['timestamp'] as num?)?.toInt(),
      pickupDistanceKm: (json['pickupDistanceKm'] as num?)?.toDouble() ?? 0.0,
      tripDistanceKm: (json['tripDistanceKm'] as num?)?.toDouble() ?? 0.0,
      estimatedMinutes: (json['estimatedMinutes'] as num?)?.toInt() ?? 20,
      offeredFare: (json['offeredFare'] as num?)?.toDouble() ?? 0.0,
      fuelCost: (json['fuelCost'] as num?)?.toDouble() ?? 0.0,
      commissionCost: (json['commissionCost'] as num?)?.toDouble() ?? 0.0,
      wearAndTearCost: (json['wearAndTearCost'] as num?)?.toDouble() ?? 0.0,
      netProfit: (json['netProfit'] as num?)?.toDouble() ?? 0.0,
      profitPerKm: (json['profitPerKm'] as num?)?.toDouble() ?? 0.0,
      status: json['status'] as String? ?? "COMPLETED",
      pickupLocation: json['pickupLocation'] as String? ?? "",
      dropoffLocation: json['dropoffLocation'] as String? ?? "",
      notes: json['notes'] as String? ?? "",
    );
  }
}
