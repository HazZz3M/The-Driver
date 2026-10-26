import '../../engine/fare_calculator_engine.dart';

class DriverSettings {
  final double fuelConsumptionLPer100Km;
  final double fuelPricePerLiter;
  final double inDriveCommissionPercent;
  final double wearAndTearPerKm;
  final double targetDailyEarnings;
  final double minProfitPerKm;
  /// Trip length assumed when a request card doesn't show it (list cards show pickup only).
  final double assumedTripKm;
  final String currency;
  final List<String> favoriteZones;
  final List<String> blacklistedZones;

  // Auto-Accept Automation
  final bool autoAcceptEnabled;
  final double autoAcceptMinProfitPerKm;
  final double autoAcceptMinNetProfit;
  final double autoAcceptMaxPickupKm;
  final bool autoAcceptOnlyFavoriteZones;
  final bool autoAcceptRejectBlacklisted;
  final double autoAcceptDelaySeconds;

  // Pickup pricing: the km to the passenger are priced cheaper than trip km (see fairPrice).
  final bool pickupPricingEnabled;
  final double pickupProfitPerKm;
  /// Share of the inDrive commission counted on pickup km, in % (50 = half of the commission).
  final double pickupCommissionSharePercent;
  /// When pickup pricing applies. No rule on = always. Rules: pickup longer than the trip,
  /// and/or pickup longer than [pickupRuleThresholdKm]; [pickupRuleMatchAll] = both must hold.
  final bool pickupRuleLongerThanTrip;
  final bool pickupRuleOverKm;
  final double pickupRuleThresholdKm;
  final bool pickupRuleMatchAll;
  /// Pricing of the pickup km when the rules don't hold: COST_ONLY | FREE | AS_TRIP.
  final String pickupShortMode;

  // Auto-accept: when several requests qualify, take the best one first.
  final bool autoAcceptPickBest;
  /// PROFIT_PER_KM | NET_PROFIT | MARGIN
  final String autoAcceptRankBy;

  // Auto-hide: swipe away losing and/or blocked (zone or passenger) request cards.
  final bool autoDismissLoss;
  final bool autoDismissBlocked;
  /// Swipe direction on the card: RIGHT | LEFT.
  final String autoDismissDirection;
  final double autoDismissDelaySeconds;

  // Passenger lists (matched by the name shown on the card)
  final List<String> favoritePassengers;
  final List<String> blacklistedPassengers;

  const DriverSettings({
    this.fuelConsumptionLPer100Km = 8.5,
    this.fuelPricePerLiter = 15.25,
    this.inDriveCommissionPercent = 10.0,
    this.wearAndTearPerKm = 1.2,
    this.targetDailyEarnings = 1500.0,
    this.minProfitPerKm = 8.0,
    this.assumedTripKm = 4.0,
    this.currency = "ج.م",
    // No zones until the driver picks them: preset lists coloured rides the driver never chose.
    this.favoriteZones = const [],
    this.blacklistedZones = const [],
    this.autoAcceptEnabled = false,
    this.autoAcceptMinProfitPerKm = 10.0,
    this.autoAcceptMinNetProfit = 25.0,
    this.autoAcceptMaxPickupKm = 3.5,
    this.autoAcceptOnlyFavoriteZones = false,
    this.autoAcceptRejectBlacklisted = true,
    this.autoAcceptDelaySeconds = 1.5,
    this.pickupPricingEnabled = true,
    this.pickupProfitPerKm = 3.0,
    this.pickupCommissionSharePercent = 50.0,
    this.pickupRuleLongerThanTrip = false,
    this.pickupRuleOverKm = false,
    this.pickupRuleThresholdKm = 2.0,
    this.pickupRuleMatchAll = false,
    this.pickupShortMode = "COST_ONLY",
    this.autoAcceptPickBest = false,
    this.autoAcceptRankBy = "PROFIT_PER_KM",
    this.autoDismissLoss = false,
    this.autoDismissBlocked = false,
    this.autoDismissDirection = "RIGHT",
    this.autoDismissDelaySeconds = 2.0,
    this.favoritePassengers = const [],
    this.blacklistedPassengers = const [],
  });

  bool isFavoritePassenger(String? name) => FareCalculatorEngine.matchPassenger(name, favoritePassengers) != null;

  bool isBlacklistedPassenger(String? name) => FareCalculatorEngine.matchPassenger(name, blacklistedPassengers) != null;

  bool isFavorite(String? zone) => FareCalculatorEngine.matchZone(zone, favoriteZones) != null;

  bool isBlacklisted(String? zone) => FareCalculatorEngine.matchZone(zone, blacklistedZones) != null;

  DriverSettings copyWith({
    double? fuelConsumptionLPer100Km,
    double? fuelPricePerLiter,
    double? inDriveCommissionPercent,
    double? wearAndTearPerKm,
    double? targetDailyEarnings,
    double? minProfitPerKm,
    double? assumedTripKm,
    String? currency,
    List<String>? favoriteZones,
    List<String>? blacklistedZones,
    bool? autoAcceptEnabled,
    double? autoAcceptMinProfitPerKm,
    double? autoAcceptMinNetProfit,
    double? autoAcceptMaxPickupKm,
    bool? autoAcceptOnlyFavoriteZones,
    bool? autoAcceptRejectBlacklisted,
    double? autoAcceptDelaySeconds,
    bool? pickupPricingEnabled,
    double? pickupProfitPerKm,
    double? pickupCommissionSharePercent,
    bool? pickupRuleLongerThanTrip,
    bool? pickupRuleOverKm,
    double? pickupRuleThresholdKm,
    bool? pickupRuleMatchAll,
    String? pickupShortMode,
    bool? autoAcceptPickBest,
    String? autoAcceptRankBy,
    bool? autoDismissLoss,
    bool? autoDismissBlocked,
    String? autoDismissDirection,
    double? autoDismissDelaySeconds,
    List<String>? favoritePassengers,
    List<String>? blacklistedPassengers,
  }) {
    return DriverSettings(
      fuelConsumptionLPer100Km: fuelConsumptionLPer100Km ?? this.fuelConsumptionLPer100Km,
      fuelPricePerLiter: fuelPricePerLiter ?? this.fuelPricePerLiter,
      inDriveCommissionPercent: inDriveCommissionPercent ?? this.inDriveCommissionPercent,
      wearAndTearPerKm: wearAndTearPerKm ?? this.wearAndTearPerKm,
      targetDailyEarnings: targetDailyEarnings ?? this.targetDailyEarnings,
      minProfitPerKm: minProfitPerKm ?? this.minProfitPerKm,
      assumedTripKm: assumedTripKm ?? this.assumedTripKm,
      currency: currency ?? this.currency,
      favoriteZones: favoriteZones ?? this.favoriteZones,
      blacklistedZones: blacklistedZones ?? this.blacklistedZones,
      autoAcceptEnabled: autoAcceptEnabled ?? this.autoAcceptEnabled,
      autoAcceptMinProfitPerKm: autoAcceptMinProfitPerKm ?? this.autoAcceptMinProfitPerKm,
      autoAcceptMinNetProfit: autoAcceptMinNetProfit ?? this.autoAcceptMinNetProfit,
      autoAcceptMaxPickupKm: autoAcceptMaxPickupKm ?? this.autoAcceptMaxPickupKm,
      autoAcceptOnlyFavoriteZones: autoAcceptOnlyFavoriteZones ?? this.autoAcceptOnlyFavoriteZones,
      autoAcceptRejectBlacklisted: autoAcceptRejectBlacklisted ?? this.autoAcceptRejectBlacklisted,
      autoAcceptDelaySeconds: autoAcceptDelaySeconds ?? this.autoAcceptDelaySeconds,
      pickupPricingEnabled: pickupPricingEnabled ?? this.pickupPricingEnabled,
      pickupProfitPerKm: pickupProfitPerKm ?? this.pickupProfitPerKm,
      pickupCommissionSharePercent: pickupCommissionSharePercent ?? this.pickupCommissionSharePercent,
      pickupRuleLongerThanTrip: pickupRuleLongerThanTrip ?? this.pickupRuleLongerThanTrip,
      pickupRuleOverKm: pickupRuleOverKm ?? this.pickupRuleOverKm,
      pickupRuleThresholdKm: pickupRuleThresholdKm ?? this.pickupRuleThresholdKm,
      pickupRuleMatchAll: pickupRuleMatchAll ?? this.pickupRuleMatchAll,
      pickupShortMode: pickupShortMode ?? this.pickupShortMode,
      autoAcceptPickBest: autoAcceptPickBest ?? this.autoAcceptPickBest,
      autoAcceptRankBy: autoAcceptRankBy ?? this.autoAcceptRankBy,
      autoDismissLoss: autoDismissLoss ?? this.autoDismissLoss,
      autoDismissBlocked: autoDismissBlocked ?? this.autoDismissBlocked,
      autoDismissDirection: autoDismissDirection ?? this.autoDismissDirection,
      autoDismissDelaySeconds: autoDismissDelaySeconds ?? this.autoDismissDelaySeconds,
      favoritePassengers: favoritePassengers ?? this.favoritePassengers,
      blacklistedPassengers: blacklistedPassengers ?? this.blacklistedPassengers,
    );
  }

  /// Snapshot sent to the native HUD / accessibility layer (see PreferencesManager.applyFromFlutter).
  Map<String, dynamic> toJson() {
    return {
      'fuelConsumptionLPer100Km': fuelConsumptionLPer100Km,
      'fuelPricePerLiter': fuelPricePerLiter,
      'inDriveCommissionPercent': inDriveCommissionPercent,
      'wearAndTearPerKm': wearAndTearPerKm,
      'targetDailyEarnings': targetDailyEarnings,
      'minProfitPerKm': minProfitPerKm,
      'assumedTripKm': assumedTripKm,
      'currency': currency,
      'favoriteZones': favoriteZones,
      'blacklistedZones': blacklistedZones,
      'autoAcceptEnabled': autoAcceptEnabled,
      'autoAcceptMinProfitPerKm': autoAcceptMinProfitPerKm,
      'autoAcceptMinNetProfit': autoAcceptMinNetProfit,
      'autoAcceptMaxPickupKm': autoAcceptMaxPickupKm,
      'autoAcceptOnlyFavoriteZones': autoAcceptOnlyFavoriteZones,
      'autoAcceptRejectBlacklisted': autoAcceptRejectBlacklisted,
      'autoAcceptDelaySeconds': autoAcceptDelaySeconds,
      'pickupPricingEnabled': pickupPricingEnabled,
      'pickupProfitPerKm': pickupProfitPerKm,
      'pickupCommissionSharePercent': pickupCommissionSharePercent,
      'pickupRuleLongerThanTrip': pickupRuleLongerThanTrip,
      'pickupRuleOverKm': pickupRuleOverKm,
      'pickupRuleThresholdKm': pickupRuleThresholdKm,
      'pickupRuleMatchAll': pickupRuleMatchAll,
      'pickupShortMode': pickupShortMode,
      'autoAcceptPickBest': autoAcceptPickBest,
      'autoAcceptRankBy': autoAcceptRankBy,
      'autoDismissLoss': autoDismissLoss,
      'autoDismissBlocked': autoDismissBlocked,
      'autoDismissDirection': autoDismissDirection,
      'autoDismissDelaySeconds': autoDismissDelaySeconds,
      'favoritePassengers': favoritePassengers,
      'blacklistedPassengers': blacklistedPassengers,
    };
  }
}
