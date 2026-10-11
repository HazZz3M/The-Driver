import 'dart:convert';
import 'package:shared_preferences/shared_preferences.dart';
import '../models/driver_settings.dart';
import '../models/vehicle_profile.dart';

class PreferencesManager {
  static const String keyFuelConsumption = "fuel_consumption";
  static const String keyFuelPrice = "fuel_price";
  static const String keyCommission = "in_drive_commission";
  static const String keyWearPerKm = "wear_per_km";
  static const String keyDailyTarget = "daily_target";
  static const String keyMinProfitKm = "min_profit_km";
  static const String keyCurrency = "currency";
  static const String keyFavoriteZones = "favorite_zones";
  static const String keyBlacklistedZones = "blacklisted_zones";

  static const String keyThemeMode = "app_theme_mode";
  static const String keyOverlayStyle = "overlay_style";
  static const String keyOverlaySize = "overlay_size";
  static const String keyVehicleProfiles = "vehicle_profiles";
  static const String keyActiveVehicleId = "active_vehicle_id";

  static const String keyAutoAcceptEnabled = "auto_accept_enabled";
  static const String keyAutoAcceptMinProfitKm = "auto_accept_min_profit_km";
  static const String keyAutoAcceptMinNetProfit = "auto_accept_min_net_profit";
  static const String keyAutoAcceptMaxPickupKm = "auto_accept_max_pickup_km";
  static const String keyAutoAcceptOnlyFav = "auto_accept_only_fav";
  static const String keyAutoAcceptIgnoreBlack = "auto_accept_ignore_black";
  static const String keyAutoBidMode = "auto_bid_mode";
  static const String keyAutoAcceptDelay = "auto_accept_delay";

  final SharedPreferences prefs;

  PreferencesManager(this.prefs);

  static Future<PreferencesManager> init() async {
    final sp = await SharedPreferences.getInstance();
    return PreferencesManager(sp);
  }

  DriverSettings loadSettings() {
    return DriverSettings(
      fuelConsumptionLPer100Km: prefs.getDouble(keyFuelConsumption) ?? 8.5,
      fuelPricePerLiter: prefs.getDouble(keyFuelPrice) ?? 15.0,
      inDriveCommissionPercent: prefs.getDouble(keyCommission) ?? 10.0,
      wearAndTearPerKm: prefs.getDouble(keyWearPerKm) ?? 1.2,
      targetDailyEarnings: prefs.getDouble(keyDailyTarget) ?? 1500.0,
      minProfitPerKm: prefs.getDouble(keyMinProfitKm) ?? 8.0,
      currency: prefs.getString(keyCurrency) ?? "EGP",
      favoriteZones: prefs.getStringList(keyFavoriteZones) ??
          const ["المعادي", "التجمع", "مصر الجديدة", "الدقي", "الشيخ زايد"],
      blacklistedZones: prefs.getStringList(keyBlacklistedZones) ??
          const ["شبرا الخيمة", "حلوان", "المرج"],
      autoAcceptEnabled: prefs.getBool(keyAutoAcceptEnabled) ?? false,
      autoAcceptMinProfitPerKm: prefs.getDouble(keyAutoAcceptMinProfitKm) ?? 10.0,
      autoAcceptMinNetProfit: prefs.getDouble(keyAutoAcceptMinNetProfit) ?? 25.0,
      autoAcceptMaxPickupKm: prefs.getDouble(keyAutoAcceptMaxPickupKm) ?? 3.5,
      autoAcceptOnlyFavoriteZones: prefs.getBool(keyAutoAcceptOnlyFav) ?? false,
      autoAcceptIgnoreBlacklist: prefs.getBool(keyAutoAcceptIgnoreBlack) ?? true,
      autoBidMode: prefs.getString(keyAutoBidMode) ?? "RECOMMENDED",
      autoAcceptDelaySeconds: prefs.getDouble(keyAutoAcceptDelay) ?? 1.5,
    );
  }

  Future<void> saveSettings(DriverSettings s) async {
    await prefs.setDouble(keyFuelConsumption, s.fuelConsumptionLPer100Km);
    await prefs.setDouble(keyFuelPrice, s.fuelPricePerLiter);
    await prefs.setDouble(keyCommission, s.inDriveCommissionPercent);
    await prefs.setDouble(keyWearPerKm, s.wearAndTearPerKm);
    await prefs.setDouble(keyDailyTarget, s.targetDailyEarnings);
    await prefs.setDouble(keyMinProfitKm, s.minProfitPerKm);
    await prefs.setString(keyCurrency, s.currency);
    await prefs.setStringList(keyFavoriteZones, s.favoriteZones);
    await prefs.setStringList(keyBlacklistedZones, s.blacklistedZones);

    await prefs.setBool(keyAutoAcceptEnabled, s.autoAcceptEnabled);
    await prefs.setDouble(keyAutoAcceptMinProfitKm, s.autoAcceptMinProfitPerKm);
    await prefs.setDouble(keyAutoAcceptMinNetProfit, s.autoAcceptMinNetProfit);
    await prefs.setDouble(keyAutoAcceptMaxPickupKm, s.autoAcceptMaxPickupKm);
    await prefs.setBool(keyAutoAcceptOnlyFav, s.autoAcceptOnlyFavoriteZones);
    await prefs.setBool(keyAutoAcceptIgnoreBlack, s.autoAcceptIgnoreBlacklist);
    await prefs.setString(keyAutoBidMode, s.autoBidMode);
    await prefs.setDouble(keyAutoAcceptDelay, s.autoAcceptDelaySeconds);
  }

  // Theme & Overlay Style
  String getThemeMode() => prefs.getString(keyThemeMode) ?? "DARK";
  Future<void> setThemeMode(String mode) async => await prefs.setString(keyThemeMode, mode);

  String getOverlayStyle() => prefs.getString(keyOverlayStyle) ?? "COMPACT_CIRCLE";
  Future<void> setOverlayStyle(String style) async => await prefs.setString(keyOverlayStyle, style);

  String getOverlaySize() => prefs.getString(keyOverlaySize) ?? "MEDIUM";
  Future<void> setOverlaySize(String size) async => await prefs.setString(keyOverlaySize, size);

  // Quick Zone Operations
  Future<bool> addFavoriteZone(String zone) async {
    final clean = zone.trim();
    if (clean.isEmpty) return false;
    final current = loadSettings();
    if (current.favoriteZones.any((z) => z.toLowerCase() == clean.toLowerCase())) return false;
    final updated = List<String>.from(current.favoriteZones)..add(clean);
    await saveSettings(current.copyWith(favoriteZones: updated));
    return true;
  }

  Future<bool> removeFavoriteZone(String zone) async {
    final clean = zone.trim();
    final current = loadSettings();
    final updated = current.favoriteZones.where((z) => z.toLowerCase() != clean.toLowerCase()).toList();
    await saveSettings(current.copyWith(favoriteZones: updated));
    return true;
  }

  Future<bool> addBlacklistZone(String zone) async {
    final clean = zone.trim();
    if (clean.isEmpty) return false;
    final current = loadSettings();
    if (current.blacklistedZones.any((z) => z.toLowerCase() == clean.toLowerCase())) return false;
    final updated = List<String>.from(current.blacklistedZones)..add(clean);
    await saveSettings(current.copyWith(blacklistedZones: updated));
    return true;
  }

  Future<bool> removeBlacklistZone(String zone) async {
    final clean = zone.trim();
    final current = loadSettings();
    final updated = current.blacklistedZones.where((z) => z.toLowerCase() != clean.toLowerCase()).toList();
    await saveSettings(current.copyWith(blacklistedZones: updated));
    return true;
  }

  // Multi-Vehicle Profiles
  List<VehicleProfile> loadVehicleProfiles() {
    final raw = prefs.getString(keyVehicleProfiles);
    if (raw == null || raw.isEmpty) {
      final defaults = [
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
          name: "سيارة موفرة (غاز طبيعي / سولار)",
          modelYear: "2019",
          fuelType: "غاز طبيعي / CNG",
          fuelConsumptionLPer100Km: 7.0,
          fuelPricePerLiter: 11.50,
          wearAndTearPerKm: 1.1,
        ),
      ];
      saveVehicleProfiles(defaults);
      if (prefs.getString(keyActiveVehicleId) == null) {
        prefs.setString(keyActiveVehicleId, defaults.first.id);
      }
      return defaults;
    }

    try {
      final list = jsonDecode(raw) as List<dynamic>;
      return list.map((e) => VehicleProfile.fromJson(e as Map<String, dynamic>)).toList();
    } catch (_) {
      return [VehicleProfile(id: "default", name: "مركبة افتراضية")];
    }
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
