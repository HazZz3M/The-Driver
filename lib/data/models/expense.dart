class Expense {
  final int id;
  final int timestamp;
  final String category; // FUEL, OIL_CHANGE, SPARE_PART, MAINTENANCE, CAR_WASH, OTHER
  final double amount;

  // Fuel Refill Specific Fields (تفويلة البنزين)
  final double? liters;
  final double? pricePerLiter;
  final double? kmSinceLastRefill;
  final double? consumptionLPer100Km; // (liters / km) * 100
  final double? fuelCostPerKm; // amount / km

  // Engine Oil Specific Fields (غيار الزيت)
  final double? oilPricePerLiter;
  final int? oilVolumeMl;
  final double? oilLifespanKm; // الزيت نزل بعد كام كم
  final double? oilCostPerKm; // amount / oilLifespanKm

  // Spare Parts Specific Fields (قطع الغيار والصيانة الدورية)
  final String? partName;
  final double? partLifespanKm; // العمر الافتراضي لقطعة الغيار بالكيلومتر
  final double? partCostPerKm; // amount / partLifespanKm
  /// Amount added to the vehicle's wear rate when this part was saved; removed again on delete.
  final double? appliedWearPerKm;

  final double? odometerKm;
  final String description;

  Expense({
    int? id,
    int? timestamp,
    required this.category,
    required this.amount,
    this.liters,
    this.pricePerLiter,
    this.kmSinceLastRefill,
    this.consumptionLPer100Km,
    this.fuelCostPerKm,
    this.oilPricePerLiter,
    this.oilVolumeMl,
    this.oilLifespanKm,
    this.oilCostPerKm,
    this.partName,
    this.partLifespanKm,
    this.partCostPerKm,
    this.appliedWearPerKm,
    this.odometerKm,
    this.description = "",
  })  : id = id ?? DateTime.now().microsecondsSinceEpoch,
        timestamp = timestamp ?? DateTime.now().millisecondsSinceEpoch;

  Expense copyWith({
    int? id,
    int? timestamp,
    String? category,
    double? amount,
    double? liters,
    double? pricePerLiter,
    double? kmSinceLastRefill,
    double? consumptionLPer100Km,
    double? fuelCostPerKm,
    double? oilPricePerLiter,
    int? oilVolumeMl,
    double? oilLifespanKm,
    double? oilCostPerKm,
    String? partName,
    double? partLifespanKm,
    double? partCostPerKm,
    double? appliedWearPerKm,
    double? odometerKm,
    String? description,
  }) {
    return Expense(
      id: id ?? this.id,
      timestamp: timestamp ?? this.timestamp,
      category: category ?? this.category,
      amount: amount ?? this.amount,
      liters: liters ?? this.liters,
      pricePerLiter: pricePerLiter ?? this.pricePerLiter,
      kmSinceLastRefill: kmSinceLastRefill ?? this.kmSinceLastRefill,
      consumptionLPer100Km: consumptionLPer100Km ?? this.consumptionLPer100Km,
      fuelCostPerKm: fuelCostPerKm ?? this.fuelCostPerKm,
      oilPricePerLiter: oilPricePerLiter ?? this.oilPricePerLiter,
      oilVolumeMl: oilVolumeMl ?? this.oilVolumeMl,
      oilLifespanKm: oilLifespanKm ?? this.oilLifespanKm,
      oilCostPerKm: oilCostPerKm ?? this.oilCostPerKm,
      partName: partName ?? this.partName,
      partLifespanKm: partLifespanKm ?? this.partLifespanKm,
      partCostPerKm: partCostPerKm ?? this.partCostPerKm,
      appliedWearPerKm: appliedWearPerKm ?? this.appliedWearPerKm,
      odometerKm: odometerKm ?? this.odometerKm,
      description: description ?? this.description,
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'id': id,
      'timestamp': timestamp,
      'category': category,
      'amount': amount,
      'liters': liters,
      'pricePerLiter': pricePerLiter,
      'kmSinceLastRefill': kmSinceLastRefill,
      'consumptionLPer100Km': consumptionLPer100Km,
      'fuelCostPerKm': fuelCostPerKm,
      'oilPricePerLiter': oilPricePerLiter,
      'oilVolumeMl': oilVolumeMl,
      'oilLifespanKm': oilLifespanKm,
      'oilCostPerKm': oilCostPerKm,
      'partName': partName,
      'partLifespanKm': partLifespanKm,
      'partCostPerKm': partCostPerKm,
      'appliedWearPerKm': appliedWearPerKm,
      'odometerKm': odometerKm,
      'description': description,
    };
  }

  factory Expense.fromJson(Map<String, dynamic> json) {
    return Expense(
      id: (json['id'] as num?)?.toInt(),
      timestamp: (json['timestamp'] as num?)?.toInt(),
      category: json['category'] as String? ?? "OTHER",
      amount: (json['amount'] as num?)?.toDouble() ?? 0.0,
      liters: (json['liters'] as num?)?.toDouble(),
      pricePerLiter: (json['pricePerLiter'] as num?)?.toDouble(),
      kmSinceLastRefill: (json['kmSinceLastRefill'] as num?)?.toDouble(),
      consumptionLPer100Km: (json['consumptionLPer100Km'] as num?)?.toDouble(),
      fuelCostPerKm: (json['fuelCostPerKm'] as num?)?.toDouble(),
      oilPricePerLiter: (json['oilPricePerLiter'] as num?)?.toDouble(),
      oilVolumeMl: (json['oilVolumeMl'] as num?)?.toInt(),
      oilLifespanKm: (json['oilLifespanKm'] as num?)?.toDouble(),
      oilCostPerKm: (json['oilCostPerKm'] as num?)?.toDouble(),
      partName: json['partName'] as String?,
      partLifespanKm: (json['partLifespanKm'] as num?)?.toDouble(),
      partCostPerKm: (json['partCostPerKm'] as num?)?.toDouble(),
      appliedWearPerKm: (json['appliedWearPerKm'] as num?)?.toDouble(),
      odometerKm: (json['odometerKm'] as num?)?.toDouble(),
      description: json['description'] as String? ?? "",
    );
  }
}
