import 'dart:convert';
import 'package:flutter/foundation.dart';
import 'package:shared_preferences/shared_preferences.dart';
import '../models/driver_settings.dart';
import '../models/hud_settings.dart';
import '../models/vehicle_profile.dart';

class PreferencesManager {
  static const String keyFuelConsumption = "fuel_consumption";
  static const String keyFuelPrice = "fuel_price";
  static const String keyCommission = "in_drive_commission";
  static const String keyWearPerKm = "wear_per_km";
  static const String keyDailyTarget = "daily_target";
  static const String keyMinProfitKm = "min_profit_km";
  static const String keyAssumedTripKm = "assumed_trip_km";
  static const String keyCurrency = "currency";
  static const String keyFavoriteZones = "favorite_zones";
  static const String keyBlacklistedZones = "blacklisted_zones";

  static const String keyOverlayStyle = "overlay_style";
  static const String keyOverlaySize = "overlay_size";
  static const String keyHudSettings = "hud_settings_json";
  static const String keyCalculatorInputs = "calculator_last_inputs";
  static const String keyVehicleProfiles = "vehicle_profiles";
  static const String keyActiveVehicleId = "active_vehicle_id";

  static const String keyAutoAcceptEnabled = "auto_accept_enabled";
  static const String keyAutoAcceptMinProfitKm = "auto_accept_min_profit_km";
  static const String keyAutoAcceptMinNetProfit = "auto_accept_min_net_profit";
  static const String keyAutoAcceptMaxPickupKm = "auto_accept_max_pickup_km";
  static const String keyAutoAcceptOnlyFav = "auto_accept_only_fav";
  static const String keyAutoAcceptRejectBlack = "auto_accept_reject_black";
  static const String keyAutoAcceptDelay = "auto_accept_delay";
  static const String keyAutoAcceptConsentGiven = "auto_accept_consent_given";

  static const String keyPickupPricingEnabled = "pickup_pricing_enabled";
  static const String keyPickupProfitKm = "pickup_profit_km";
  static const String keyPickupCommissionShare = "pickup_commission_share";
  static const String keyPickupRuleLonger = "pickup_rule_longer_than_trip";
  static const String keyPickupRuleOverKm = "pickup_rule_over_km";
  static const String keyPickupRuleThreshold = "pickup_rule_threshold_km";
  static const String keyPickupRuleMatchAll = "pickup_rule_match_all";
  static const String keyPickupShortMode = "pickup_short_mode";
  static const String keyAutoAcceptPickBest = "auto_accept_pick_best";
  static const String keyAutoAcceptRankBy = "auto_accept_rank_by";
  static const String keyAutoDismissLoss = "auto_dismiss_loss";
  static const String keyAutoDismissBlocked = "auto_dismiss_blocked";
  static const String keyAutoDismissDirection = "auto_dismiss_direction";
  static const String keyAutoDismissDelay = "auto_dismiss_delay";
  static const String keyTutorialSeen = "tutorial_seen";
  static const String keyFavoritePassengers = "favorite_passengers";
  static const String keyBlacklistedPassengers = "blacklisted_passengers";

  static const String keyShiftActive = "shift_active";
  static const String keyShiftStartTime = "shift_start_time";

  final SharedPreferences prefs;

  PreferencesManager(this.prefs);

  static Future<PreferencesManager> init() async {
    final sp = await SharedPreferences.getInstance();
    return PreferencesManager(sp);
  }

  DriverSettings loadSettings() {
    const d = DriverSettings();
    return DriverSettings(
      fuelConsumptionLPer100Km: prefs.getDouble(keyFuelConsumption) ?? d.fuelConsumptionLPer100Km,
      fuelPricePerLiter: prefs.getDouble(keyFuelPrice) ?? d.fuelPricePerLiter,
      inDriveCommissionPercent: prefs.getDouble(keyCommission) ?? d.inDriveCommissionPercent,
      wearAndTearPerKm: prefs.getDouble(keyWearPerKm) ?? d.wearAndTearPerKm,
      targetDailyEarnings: prefs.getDouble(keyDailyTarget) ?? d.targetDailyEarnings,
      minProfitPerKm: prefs.getDouble(keyMinProfitKm) ?? d.minProfitPerKm,
      assumedTripKm: prefs.getDouble(keyAssumedTripKm) ?? d.assumedTripKm,
      // Builds before 1.13 stored the Latin "EGP"; the UI is Arabic, so show "ج.م" everywhere.
      currency: switch (prefs.getString(keyCurrency)) { null || "EGP" => d.currency, final c => c },
      favoriteZones: prefs.getStringList(keyFavoriteZones) ?? d.favoriteZones,
      blacklistedZones: prefs.getStringList(keyBlacklistedZones) ?? d.blacklistedZones,
      autoAcceptEnabled: prefs.getBool(keyAutoAcceptEnabled) ?? d.autoAcceptEnabled,
      autoAcceptMinProfitPerKm: prefs.getDouble(keyAutoAcceptMinProfitKm) ?? d.autoAcceptMinProfitPerKm,
      autoAcceptMinNetProfit: prefs.getDouble(keyAutoAcceptMinNetProfit) ?? d.autoAcceptMinNetProfit,
      autoAcceptMaxPickupKm: prefs.getDouble(keyAutoAcceptMaxPickupKm) ?? d.autoAcceptMaxPickupKm,
      autoAcceptOnlyFavoriteZones: prefs.getBool(keyAutoAcceptOnlyFav) ?? d.autoAcceptOnlyFavoriteZones,
      autoAcceptRejectBlacklisted: prefs.getBool(keyAutoAcceptRejectBlack) ?? d.autoAcceptRejectBlacklisted,
      autoAcceptDelaySeconds: prefs.getDouble(keyAutoAcceptDelay) ?? d.autoAcceptDelaySeconds,
      pickupPricingEnabled: prefs.getBool(keyPickupPricingEnabled) ?? d.pickupPricingEnabled,
      pickupProfitPerKm: prefs.getDouble(keyPickupProfitKm) ?? d.pickupProfitPerKm,
      pickupCommissionSharePercent: prefs.getDouble(keyPickupCommissionShare) ?? d.pickupCommissionSharePercent,
      pickupRuleLongerThanTrip: prefs.getBool(keyPickupRuleLonger) ?? d.pickupRuleLongerThanTrip,
      pickupRuleOverKm: prefs.getBool(keyPickupRuleOverKm) ?? d.pickupRuleOverKm,
      pickupRuleThresholdKm: prefs.getDouble(keyPickupRuleThreshold) ?? d.pickupRuleThresholdKm,
      pickupRuleMatchAll: prefs.getBool(keyPickupRuleMatchAll) ?? d.pickupRuleMatchAll,
      pickupShortMode: prefs.getString(keyPickupShortMode) ?? d.pickupShortMode,
      autoAcceptPickBest: prefs.getBool(keyAutoAcceptPickBest) ?? d.autoAcceptPickBest,
      autoAcceptRankBy: prefs.getString(keyAutoAcceptRankBy) ?? d.autoAcceptRankBy,
      autoDismissLoss: prefs.getBool(keyAutoDismissLoss) ?? d.autoDismissLoss,
      autoDismissBlocked: prefs.getBool(keyAutoDismissBlocked) ?? d.autoDismissBlocked,
      autoDismissDirection: prefs.getString(keyAutoDismissDirection) ?? d.autoDismissDirection,
      autoDismissDelaySeconds: prefs.getDouble(keyAutoDismissDelay) ?? d.autoDismissDelaySeconds,
      favoritePassengers: prefs.getStringList(keyFavoritePassengers) ?? d.favoritePassengers,
      blacklistedPassengers: prefs.getStringList(keyBlacklistedPassengers) ?? d.blacklistedPassengers,
    );
  }

  Future<void> saveSettings(DriverSettings s) async {
    // Independent keys: write them concurrently instead of 16 sequential round-trips.
    await Future.wait([
      prefs.setDouble(keyFuelConsumption, s.fuelConsumptionLPer100Km),
      prefs.setDouble(keyFuelPrice, s.fuelPricePerLiter),
      prefs.setDouble(keyCommission, s.inDriveCommissionPercent),
      prefs.setDouble(keyWearPerKm, s.wearAndTearPerKm),
      prefs.setDouble(keyDailyTarget, s.targetDailyEarnings),
      prefs.setDouble(keyMinProfitKm, s.minProfitPerKm),
      prefs.setDouble(keyAssumedTripKm, s.assumedTripKm),
      prefs.setString(keyCurrency, s.currency),
      prefs.setStringList(keyFavoriteZones, s.favoriteZones),
      prefs.setStringList(keyBlacklistedZones, s.blacklistedZones),
      prefs.setBool(keyAutoAcceptEnabled, s.autoAcceptEnabled),
      prefs.setDouble(keyAutoAcceptMinProfitKm, s.autoAcceptMinProfitPerKm),
      prefs.setDouble(keyAutoAcceptMinNetProfit, s.autoAcceptMinNetProfit),
      prefs.setDouble(keyAutoAcceptMaxPickupKm, s.autoAcceptMaxPickupKm),
      prefs.setBool(keyAutoAcceptOnlyFav, s.autoAcceptOnlyFavoriteZones),
      prefs.setBool(keyAutoAcceptRejectBlack, s.autoAcceptRejectBlacklisted),
      prefs.setDouble(keyAutoAcceptDelay, s.autoAcceptDelaySeconds),
      prefs.setBool(keyPickupPricingEnabled, s.pickupPricingEnabled),
      prefs.setDouble(keyPickupProfitKm, s.pickupProfitPerKm),
      prefs.setDouble(keyPickupCommissionShare, s.pickupCommissionSharePercent),
      prefs.setBool(keyPickupRuleLonger, s.pickupRuleLongerThanTrip),
      prefs.setBool(keyPickupRuleOverKm, s.pickupRuleOverKm),
      prefs.setDouble(keyPickupRuleThreshold, s.pickupRuleThresholdKm),
      prefs.setBool(keyPickupRuleMatchAll, s.pickupRuleMatchAll),
      prefs.setString(keyPickupShortMode, s.pickupShortMode),
      prefs.setBool(keyAutoAcceptPickBest, s.autoAcceptPickBest),
      prefs.setString(keyAutoAcceptRankBy, s.autoAcceptRankBy),
      prefs.setBool(keyAutoDismissLoss, s.autoDismissLoss),
      prefs.setBool(keyAutoDismissBlocked, s.autoDismissBlocked),
      prefs.setString(keyAutoDismissDirection, s.autoDismissDirection),
      prefs.setDouble(keyAutoDismissDelay, s.autoDismissDelaySeconds),
      prefs.setStringList(keyFavoritePassengers, s.favoritePassengers),
      prefs.setStringList(keyBlacklistedPassengers, s.blacklistedPassengers),
    ]);
  }

  // Floating HUD settings (falls back to the pre-1.11 style/size keys)
  HudSettings loadHudSettings() {
    final legacy = HudSettings(
      style: prefs.getString(keyOverlayStyle) ?? "COMPACT_CIRCLE",
      size: prefs.getString(keyOverlaySize) ?? "MEDIUM",
    );
    final raw = prefs.getString(keyHudSettings);
    if (raw == null || raw.isEmpty) return legacy;
    try {
      return HudSettings.fromJson(Map<String, dynamic>.from(jsonDecode(raw) as Map), fallback: legacy);
    } catch (_) {
      return legacy;
    }
  }

  Future<void> saveHudSettings(HudSettings hud) async =>
      await prefs.setString(keyHudSettings, jsonEncode(hud.toJson()));

  // Calculator: remembered last inputs
  Map<String, double> loadCalculatorInputs() {
    final raw = prefs.getString(keyCalculatorInputs);
    if (raw == null) return const {};
    try {
      return Map<String, dynamic>.from(jsonDecode(raw) as Map)
          .map((k, v) => MapEntry(k, (v as num).toDouble()));
    } catch (_) {
      return const {};
    }
  }

  Future<void> saveCalculatorInputs(Map<String, double> values) async =>
      await prefs.setString(keyCalculatorInputs, jsonEncode(values));

  // Auto-accept consent (shown once, before the first enable)
  bool get autoAcceptConsentGiven => prefs.getBool(keyAutoAcceptConsentGiven) ?? false;
  Future<void> setAutoAcceptConsentGiven() async => await prefs.setBool(keyAutoAcceptConsentGiven, true);

  /// The walkthrough was finished or skipped once (shown automatically only on first launch).
  bool get tutorialSeen => prefs.getBool(keyTutorialSeen) ?? false;
  Future<void> setTutorialSeen() async => await prefs.setBool(keyTutorialSeen, true);

  // Shift persistence (Android kills the app while the driver is in inDrive)
  bool get isShiftActive => prefs.getBool(keyShiftActive) ?? false;
  int get shiftStartTime => prefs.getInt(keyShiftStartTime) ?? 0;

  Future<void> saveShift({required bool active, required int startTime}) async {
    await Future.wait([
      prefs.setBool(keyShiftActive, active),
      prefs.setInt(keyShiftStartTime, startTime),
    ]);
  }

  // Multi-Vehicle Profiles
  static List<VehicleProfile> defaultVehicleProfiles() => [
        VehicleProfile(
          id: "def_car_1",
          name: "سيارة اقتصادية (بنزين 92)",
          modelYear: "2021",
          fuelType: "بنزين 92",
          fuelConsumptionLPer100Km: 8.5,
          fuelPricePerLiter: 15.25,
          wearAndTearPerKm: 1.2,
        ),
        VehicleProfile(
          id: "def_car_2",
          name: "سيارة موفرة (غاز طبيعي)",
          modelYear: "2019",
          fuelType: "غاز طبيعي / CNG",
          fuelConsumptionLPer100Km: 7.0,
          fuelPricePerLiter: 11.50,
          wearAndTearPerKm: 1.1,
        ),
      ];

  List<VehicleProfile> loadVehicleProfiles() {
    final raw = prefs.getString(keyVehicleProfiles);
    if (raw == null || raw.isEmpty) {
      final defaults = defaultVehicleProfiles();
      saveVehicleProfiles(defaults);
      if (prefs.getString(keyActiveVehicleId) == null) {
        prefs.setString(keyActiveVehicleId, defaults.first.id);
      }
      return defaults;
    }

    final decoded = decodeJsonListSafely(
      prefs: prefs,
      key: keyVehicleProfiles,
      raw: raw,
      fromJson: VehicleProfile.fromJson,
    );
    return decoded.isNotEmpty ? decoded : defaultVehicleProfiles();
  }

  Future<void> saveVehicleProfiles(List<VehicleProfile> profiles) async {
    final raw = jsonEncode(profiles.map((p) => p.toJson()).toList());
    await prefs.setString(keyVehicleProfiles, raw);
  }

  String getActiveVehicleId() {
    final id = prefs.getString(keyActiveVehicleId);
    if (id != null && id.isNotEmpty) return id;
    final profiles = loadVehicleProfiles();
    return profiles.isNotEmpty ? profiles.first.id : "";
  }

  Future<void> setActiveVehicleId(String id) async {
    await prefs.setString(keyActiveVehicleId, id);
  }
}

/// Decodes a JSON list stored under [key] without ever losing data:
/// - items that fail to parse are skipped individually (the rest still load);
/// - if the whole payload is unreadable (or any item was skipped), the raw string is copied to a
///   `<key>_corrupt_<timestamp>` backup key before anything can overwrite it.
List<T> decodeJsonListSafely<T>({
  required SharedPreferences prefs,
  required String key,
  required String raw,
  required T Function(Map<String, dynamic>) fromJson,
}) {
  List<dynamic> list;
  try {
    list = jsonDecode(raw) as List<dynamic>;
  } catch (e) {
    _backupCorrupt(prefs, key, raw, e);
    return [];
  }

  final result = <T>[];
  var skipped = 0;
  for (final item in list) {
    try {
      result.add(fromJson(Map<String, dynamic>.from(item as Map)));
    } catch (_) {
      skipped++;
    }
  }
  if (skipped > 0) {
    _backupCorrupt(prefs, key, raw, "$skipped unreadable item(s)");
  }
  return result;
}

void _backupCorrupt(SharedPreferences prefs, String key, String raw, Object reason) {
  final backupKey = "${key}_corrupt_${DateTime.now().millisecondsSinceEpoch}";
  prefs.setString(backupKey, raw);
  debugPrint("Storage: could not fully read '$key' ($reason). Raw data backed up to '$backupKey'.");
}
