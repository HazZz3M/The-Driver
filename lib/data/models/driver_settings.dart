class DriverSettings {
  final double fuelConsumptionLPer100Km;
  final double fuelPricePerLiter;
  final double inDriveCommissionPercent;
  final double wearAndTearPerKm;
  final double targetDailyEarnings;
  final double minProfitPerKm;
  final String currency;
  final List<String> favoriteZones;
  final List<String> blacklistedZones;

  // Auto-Accept & Auto-Bidding Automation
  final bool autoAcceptEnabled;
  final double autoAcceptMinProfitPerKm;
  final double autoAcceptMinNetProfit;
  final double autoAcceptMaxPickupKm;
  final bool autoAcceptOnlyFavoriteZones;
  final bool autoAcceptIgnoreBlacklist;
  final String autoBidMode; // "RECOMMENDED" or "PASSENGER_FARE"
  final double autoAcceptDelaySeconds;

  const DriverSettings({
    this.fuelConsumptionLPer100Km = 8.5,
    this.fuelPricePerLiter = 15.0,
    this.inDriveCommissionPercent = 10.0,
    this.wearAndTearPerKm = 1.2,
    this.targetDailyEarnings = 1500.0,
    this.minProfitPerKm = 8.0,
    this.currency = "EGP",
    this.favoriteZones = const ["المعادي", "التجمع", "مصر الجديدة", "الدقي", "الشيخ زايد"],
    this.blacklistedZones = const ["شبرا الخيمة", "حلوان", "المرج"],
    this.autoAcceptEnabled = false,
    this.autoAcceptMinProfitPerKm = 10.0,
    this.autoAcceptMinNetProfit = 25.0,
    this.autoAcceptMaxPickupKm = 3.5,
    this.autoAcceptOnlyFavoriteZones = false,
    this.autoAcceptIgnoreBlacklist = true,
    this.autoBidMode = "RECOMMENDED",
    this.autoAcceptDelaySeconds = 1.5,
  });

  bool isFavorite(String? zone) {
    if (zone == null || zone.trim().isEmpty) return false;
    final clean = zone.trim().toLowerCase();
    return favoriteZones.any((fav) => fav.trim().isNotEmpty && clean.contains(fav.trim().toLowerCase()));
  }

  bool isBlacklisted(String? zone) {
    if (zone == null || zone.trim().isEmpty) return false;
    final clean = zone.trim().toLowerCase();
    return blacklistedZones.any((b) => b.trim().isNotEmpty && clean.contains(b.trim().toLowerCase()));
  }

  DriverSettings copyWith({
    double? fuelConsumptionLPer100Km,
    double? fuelPricePerLiter,
    double? inDriveCommissionPercent,
    double? wearAndTearPerKm,
    double? targetDailyEarnings,
    double? minProfitPerKm,
    String? currency,
    List<String>? favoriteZones,
    List<String>? blacklistedZones,
    bool? autoAcceptEnabled,
    double? autoAcceptMinProfitPerKm,
    double? autoAcceptMinNetProfit,
    double? autoAcceptMaxPickupKm,
    bool? autoAcceptOnlyFavoriteZones,
    bool? autoAcceptIgnoreBlacklist,
    String? autoBidMode,
    double? autoAcceptDelaySeconds,
  }) {
    return DriverSettings(
      fuelConsumptionLPer100Km: fuelConsumptionLPer100Km ?? this.fuelConsumptionLPer100Km,
      fuelPricePerLiter: fuelPricePerLiter ?? this.fuelPricePerLiter,
      inDriveCommissionPercent: inDriveCommissionPercent ?? this.inDriveCommissionPercent,
      wearAndTearPerKm: wearAndTearPerKm ?? this.wearAndTearPerKm,
      targetDailyEarnings: targetDailyEarnings ?? this.targetDailyEarnings,
      minProfitPerKm: minProfitPerKm ?? this.minProfitPerKm,
      currency: currency ?? this.currency,
      favoriteZones: favoriteZones ?? this.favoriteZones,
      blacklistedZones: blacklistedZones ?? this.blacklistedZones,
      autoAcceptEnabled: autoAcceptEnabled ?? this.autoAcceptEnabled,
      autoAcceptMinProfitPerKm: autoAcceptMinProfitPerKm ?? this.autoAcceptMinProfitPerKm,
      autoAcceptMinNetProfit: autoAcceptMinNetProfit ?? this.autoAcceptMinNetProfit,
      autoAcceptMaxPickupKm: autoAcceptMaxPickupKm ?? this.autoAcceptMaxPickupKm,
      autoAcceptOnlyFavoriteZones: autoAcceptOnlyFavoriteZones ?? this.autoAcceptOnlyFavoriteZones,
      autoAcceptIgnoreBlacklist: autoAcceptIgnoreBlacklist ?? this.autoAcceptIgnoreBlacklist,
      autoBidMode: autoBidMode ?? this.autoBidMode,
      autoAcceptDelaySeconds: autoAcceptDelaySeconds ?? this.autoAcceptDelaySeconds,
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'fuelConsumptionLPer100Km': fuelConsumptionLPer100Km,
      'fuelPricePerLiter': fuelPricePerLiter,
      'inDriveCommissionPercent': inDriveCommissionPercent,
      'wearAndTearPerKm': wearAndTearPerKm,
      'targetDailyEarnings': targetDailyEarnings,
      'minProfitPerKm': minProfitPerKm,
      'currency': currency,
      'favoriteZones': favoriteZones,
      'blacklistedZones': blacklistedZones,
      'autoAcceptEnabled': autoAcceptEnabled,
      'autoAcceptMinProfitPerKm': autoAcceptMinProfitPerKm,
      'autoAcceptMinNetProfit': autoAcceptMinNetProfit,
      'autoAcceptMaxPickupKm': autoAcceptMaxPickupKm,
      'autoAcceptOnlyFavoriteZones': autoAcceptOnlyFavoriteZones,
      'autoAcceptIgnoreBlacklist': autoAcceptIgnoreBlacklist,
      'autoBidMode': autoBidMode,
      'autoAcceptDelaySeconds': autoAcceptDelaySeconds,
    };
  }

  factory DriverSettings.fromJson(Map<String, dynamic> json) {
    return DriverSettings(
      fuelConsumptionLPer100Km: (json['fuelConsumptionLPer100Km'] as num?)?.toDouble() ?? 8.5,
      fuelPricePerLiter: (json['fuelPricePerLiter'] as num?)?.toDouble() ?? 15.0,
      inDriveCommissionPercent: (json['inDriveCommissionPercent'] as num?)?.toDouble() ?? 10.0,
      wearAndTearPerKm: (json['wearAndTearPerKm'] as num?)?.toDouble() ?? 1.2,
      targetDailyEarnings: (json['targetDailyEarnings'] as num?)?.toDouble() ?? 1500.0,
      minProfitPerKm: (json['minProfitPerKm'] as num?)?.toDouble() ?? 8.0,
      currency: json['currency'] as String? ?? "EGP",
      favoriteZones: (json['favoriteZones'] as List<dynamic>?)?.map((e) => e.toString()).toList() ??
          const ["المعادي", "التجمع", "مصر الجديدة", "الدقي", "الشيخ زايد"],
      blacklistedZones: (json['blacklistedZones'] as List<dynamic>?)?.map((e) => e.toString()).toList() ??
          const ["شبرا الخيمة", "حلوان", "المرج"],
      autoAcceptEnabled: json['autoAcceptEnabled'] as bool? ?? false,
      autoAcceptMinProfitPerKm: (json['autoAcceptMinProfitPerKm'] as num?)?.toDouble() ?? 10.0,
      autoAcceptMinNetProfit: (json['autoAcceptMinNetProfit'] as num?)?.toDouble() ?? 25.0,
      autoAcceptMaxPickupKm: (json['autoAcceptMaxPickupKm'] as num?)?.toDouble() ?? 3.5,
      autoAcceptOnlyFavoriteZones: json['autoAcceptOnlyFavoriteZones'] as bool? ?? false,
      autoAcceptIgnoreBlacklist: json['autoAcceptIgnoreBlacklist'] as bool? ?? true,
      autoBidMode: json['autoBidMode'] as String? ?? "RECOMMENDED",
      autoAcceptDelaySeconds: (json['autoAcceptDelaySeconds'] as num?)?.toDouble() ?? 1.5,
    );
  }
}
