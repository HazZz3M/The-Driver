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
  });

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
    };
  }
}
