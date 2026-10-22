import 'package:flutter/foundation.dart';
import '../data/models/driver_settings.dart';
import 'dart:async';
import '../data/models/expense.dart';
import '../data/models/hud_settings.dart';
import '../data/models/trip.dart';
import '../data/models/vehicle_profile.dart';
import '../data/repository/assistant_repository.dart';
import '../engine/fare_calculator_engine.dart';
import '../services/native_bridge_service.dart';

class AssistantProvider extends ChangeNotifier {
  final AssistantRepository repository;

  // Settings
  late DriverSettings driverSettings;
  late List<VehicleProfile> vehicleProfiles;
  late String activeVehicleId;
  late HudSettings hud;

  // Calculator inputs
  double pickupDistanceKm = 2.0;
  double tripDistanceKm = 9.5;
  int estimatedMinutes = FareCalculatorEngine.estimateMinutes(11.5);
  double offeredFare = 85.0;

  // Shift tracking (persisted)
  bool isShiftActive = false;
  int shiftStartTime = 0;

  // Analysis result
  late FareAnalysisResult fareAnalysis;

  // Memoized "today" statistics (recomputed only when trips change or the day rolls over)
  int _tripsVersion = 0;
  String? _statsKey;
  List<Trip> _todayTrips = const [];

  AssistantProvider(this.repository) {
    _loadInitialData();
  }

  void _loadInitialData() {
    driverSettings = repository.getDriverSettings();
    vehicleProfiles = repository.getVehicleProfiles();
    activeVehicleId = repository.getActiveVehicleId();
    hud = repository.getHudSettings();
    _restoreCalculatorInputs();
    isShiftActive = repository.isShiftActive;
    shiftStartTime = repository.shiftStartTime;

    // Before 1.14 expenses were not tied to a vehicle: give them to the vehicle that was active.
    final owner = activeProfile?.id;
    if (owner != null) {
      if (activeVehicleId != owner) activeVehicleId = owner;
      repository.assignUntaggedExpenses(owner);
    }

    // Pre-1.10 builds updated settings without updating the vehicle; adopt the settings
    // (what the engine actually used) into the active vehicle so both agree from now on.
    final active = activeProfile;
    if (active != null && !_vehicleMatchesSettings(active)) {
      _replaceProfile(active.copyWith(
        fuelConsumptionLPer100Km: driverSettings.fuelConsumptionLPer100Km,
        fuelPricePerLiter: driverSettings.fuelPricePerLiter,
        wearAndTearPerKm: driverSettings.wearAndTearPerKm,
      ));
      repository.saveVehicleProfiles(vehicleProfiles);
    }

    _recalculateAnalysis();
  }

  /// Called once at startup and whenever the app returns to the foreground:
  /// 1. adopts zone edits the driver made from the floating HUD, then
  /// 2. pushes the full settings snapshot to the native HUD / accessibility layer.
  Future<void> syncWithNative() async {
    final native = await NativeBridgeService.getNativeZones();
    if (native != null && native.mode != "none") {
      final fav = native.mode == "replace"
          ? native.favoriteZones
          : _union(driverSettings.favoriteZones, native.favoriteZones);
      final black = native.mode == "replace"
          ? native.blacklistedZones
          : _union(driverSettings.blacklistedZones, native.blacklistedZones);
      if (!listEquals(fav, driverSettings.favoriteZones) || !listEquals(black, driverSettings.blacklistedZones)) {
        driverSettings = driverSettings.copyWith(favoriteZones: fav, blacklistedZones: black);
        _recalculateAnalysis();
        notifyListeners();
        await repository.updateDriverSettings(driverSettings);
      }
    }
    await _pushToNative();
  }

  static List<String> _union(List<String> a, List<String> b) {
    final result = List<String>.from(a);
    for (final z in b) {
      if (!result.any((e) => e.toLowerCase() == z.toLowerCase())) result.add(z);
    }
    return result;
  }

  Future<void> _pushToNative() => NativeBridgeService.syncSettings({
        ...driverSettings.toJson(),
        'hud': hud.toJson(),
      });

  void _recalculateAnalysis() {
    fareAnalysis = FareCalculatorEngine.analyze(
      pickupDistanceKm: pickupDistanceKm,
      tripDistanceKm: tripDistanceKm,
      estimatedMinutes: estimatedMinutes,
      offeredFare: offeredFare,
      settings: driverSettings,
    );
  }

  VehicleProfile? get activeProfile {
    for (final p in vehicleProfiles) {
      if (p.id == activeVehicleId) return p;
    }
    return vehicleProfiles.isNotEmpty ? vehicleProfiles.first : null;
  }

  bool _vehicleMatchesSettings(VehicleProfile p) =>
      p.fuelConsumptionLPer100Km == driverSettings.fuelConsumptionLPer100Km &&
      p.fuelPricePerLiter == driverSettings.fuelPricePerLiter &&
      p.wearAndTearPerKm == driverSettings.wearAndTearPerKm;

  void _replaceProfile(VehicleProfile updated) {
    vehicleProfiles = [for (final p in vehicleProfiles) p.id == updated.id ? updated : p];
  }

  // Calculator Input Mutators
  void setPickupDistance(double value) {
    pickupDistanceKm = value;
    estimatedMinutes = FareCalculatorEngine.estimateMinutes(pickupDistanceKm + tripDistanceKm);
    _recalculateAnalysis();
    _scheduleSaveInputs();
    notifyListeners();
  }

  void setTripDistance(double value) {
    tripDistanceKm = value;
    estimatedMinutes = FareCalculatorEngine.estimateMinutes(pickupDistanceKm + tripDistanceKm);
    _recalculateAnalysis();
    _scheduleSaveInputs();
    notifyListeners();
  }

  void setOfferedFare(double value) {
    offeredFare = value;
    _recalculateAnalysis();
    _scheduleSaveInputs();
    notifyListeners();
  }

  // Shift Management
  Future<void> toggleShift() async {
    isShiftActive = !isShiftActive;
    shiftStartTime = isShiftActive ? DateTime.now().millisecondsSinceEpoch : 0;
    notifyListeners();
    await repository.saveShift(active: isShiftActive, startTime: shiftStartTime);
  }

  /// Undo for an accidental "end shift": restarts the shift with its original start time.
  Future<void> resumeShift(int startTime) async {
    isShiftActive = true;
    shiftStartTime = startTime;
    notifyListeners();
    await repository.saveShift(active: true, startTime: startTime);
  }

  Duration get shiftDuration => isShiftActive && shiftStartTime > 0
      ? DateTime.now().difference(DateTime.fromMillisecondsSinceEpoch(shiftStartTime))
      : Duration.zero;

  int get todayMidnightTimestamp {
    final now = DateTime.now();
    return DateTime(now.year, now.month, now.day).millisecondsSinceEpoch;
  }

  List<Trip> get todayTrips {
    final key = "$todayMidnightTimestamp:$_tripsVersion";
    if (_statsKey != key) {
      _todayTrips = repository.getTripsSince(todayMidnightTimestamp);
      _statsKey = key;
    }
    return _todayTrips;
  }

  double get todayTotalGross => todayTrips.fold(0.0, (sum, t) => sum + t.offeredFare);
  double get todayTotalNetProfit => todayTrips.fold(0.0, (sum, t) => sum + t.netProfit);
  int get todayTripsCount => todayTrips.where((t) => t.status == "COMPLETED").length;

  double get shiftProgress {
    if (driverSettings.targetDailyEarnings <= 0) return 0.0;
    return (todayTotalGross / driverSettings.targetDailyEarnings).clamp(0.0, 1.0);
  }

  // Expenses
  List<Expense> get allExpenses => repository.getAllExpenses();

  /// Fuel, oil and parts of the active vehicle only (newest first).
  List<Expense> get vehicleExpenses => allExpenses.where((e) => e.vehicleId == activeVehicleId).toList();

  Future<void> saveCurrentRide([double? fareToSave]) async {
    final actualFare = fareToSave ?? offeredFare;
    final recalculated = FareCalculatorEngine.analyze(
      pickupDistanceKm: fareAnalysis.pickupDistanceKm,
      tripDistanceKm: fareAnalysis.tripDistanceKm,
      estimatedMinutes: fareAnalysis.estimatedMinutes,
      offeredFare: actualFare,
      settings: driverSettings,
    );

    await repository.recordTrip(Trip(
      pickupDistanceKm: recalculated.pickupDistanceKm,
      tripDistanceKm: recalculated.tripDistanceKm,
      estimatedMinutes: recalculated.estimatedMinutes,
      offeredFare: actualFare,
      fuelCost: recalculated.fuelCost,
      commissionCost: recalculated.commissionCost,
      wearAndTearCost: recalculated.wearAndTearCost,
      netProfit: recalculated.netProfit,
      profitPerKm: recalculated.profitPerKm,
    ));
    _tripsVersion++;
    notifyListeners();
  }

  /// Records a refill. Returns the new averaged consumption applied to pricing, or null if the
  /// refill had no distance (then pricing only takes the new fuel price).
  Future<double?> addFuelRefill({
    required double liters,
    required double pricePerLiter,
    required double kmSinceLastRefill,
    String description = "",
  }) async {
    final totalAmount = liters * pricePerLiter;
    final consumption = kmSinceLastRefill > 0 ? (liters / kmSinceLastRefill) * 100.0 : 0.0;
    final costPerKm = kmSinceLastRefill > 0 ? totalAmount / kmSinceLastRefill : 0.0;

    await repository.recordExpense(Expense(
      category: "FUEL",
      amount: totalAmount,
      liters: liters,
      pricePerLiter: pricePerLiter,
      kmSinceLastRefill: kmSinceLastRefill,
      consumptionLPer100Km: consumption,
      fuelCostPerKm: costPerKm,
      description: description.isNotEmpty
          ? description
          : "تفويلة (${liters.toStringAsFixed(1)} لتر × ${pricePerLiter.toStringAsFixed(2)} ج.م)",
      vehicleId: activeVehicleId,
    ));

    // Average of this car's last few refills smooths out partial fills.
    final averaged = consumption > 0 ? repository.getRecentAverageFuelConsumption(vehicleId: activeVehicleId) : null;
    await updateSettings(driverSettings.copyWith(
      fuelConsumptionLPer100Km: averaged != null ? (averaged * 10).round() / 10.0 : null,
      fuelPricePerLiter: pricePerLiter,
    ));
    return averaged;
  }

  Future<void> addOilChange({
    required double oilPricePerLiter,
    required int oilVolumeMl,
    required double oilLifespanKm,
    String description = "",
  }) async {
    final liters = oilVolumeMl / 1000.0;
    final totalCost = liters * oilPricePerLiter;
    final costPerKm = oilLifespanKm > 0 ? totalCost / oilLifespanKm : 0.0;

    await repository.recordExpense(Expense(
      category: "OIL_CHANGE",
      amount: totalCost,
      oilPricePerLiter: oilPricePerLiter,
      oilVolumeMl: oilVolumeMl,
      oilLifespanKm: oilLifespanKm,
      oilCostPerKm: costPerKm,
      description: description.isNotEmpty
          ? description
          : "غيار زيت موتور ($oilVolumeMl ملل، عمر ${oilLifespanKm.toInt()} كم)",
      vehicleId: activeVehicleId,
    ));
    notifyListeners();
  }

  Future<void> addSparePart({
    required String partName,
    required double amount,
    double? lifespanKm,
    required bool addToWearRate,
    String description = "",
  }) async {
    final costPerKm = (lifespanKm != null && lifespanKm > 0) ? amount / lifespanKm : null;
    final applied = (addToWearRate && costPerKm != null && costPerKm > 0) ? costPerKm : null;

    await repository.recordExpense(Expense(
      category: "SPARE_PART",
      amount: amount,
      partName: partName,
      partLifespanKm: lifespanKm,
      partCostPerKm: costPerKm,
      appliedWearPerKm: applied,
      description: description.isNotEmpty
          ? description
          : (costPerKm != null
              ? "قطعة غيار: $partName (عمر ${lifespanKm!.toInt()} كم • ${costPerKm.toStringAsFixed(2)} ج.م/كم)"
              : "قطعة غيار / صيانة: $partName"),
      vehicleId: activeVehicleId,
    ));

    if (applied != null) {
      await updateSettings(driverSettings.copyWith(
        wearAndTearPerKm: ((driverSettings.wearAndTearPerKm + applied) * 100).round() / 100.0,
      ));
    } else {
      notifyListeners();
    }
  }

  Future<void> deleteTrip(int tripId) async {
    await repository.deleteTrip(tripId);
    _tripsVersion++;
    notifyListeners();
  }

  Future<void> deleteExpense(Expense expense) async {
    await repository.deleteExpense(expense.id);
    final applied = expense.appliedWearPerKm;
    if (applied != null && applied > 0) {
      // Undo this part's contribution to its vehicle's wear rate.
      await _addToWearRate(expense.vehicleId, -applied);
    } else {
      notifyListeners();
    }
  }

  /// Adds [delta] to the wear rate of [vehicleId]: through the settings (what pricing uses) for
  /// the active vehicle, directly on the profile for any other one.
  Future<void> _addToWearRate(String? vehicleId, double delta) async {
    double next(double rate) {
      final r = ((rate + delta) * 100).round() / 100.0;
      return r < 0 ? 0.0 : r;
    }

    if (vehicleId == null || vehicleId == activeVehicleId) {
      await updateSettings(driverSettings.copyWith(wearAndTearPerKm: next(driverSettings.wearAndTearPerKm)));
      return;
    }
    final owner = vehicleProfiles.where((p) => p.id == vehicleId).firstOrNull;
    if (owner != null) {
      _replaceProfile(owner.copyWith(wearAndTearPerKm: next(owner.wearAndTearPerKm)));
      await repository.saveVehicleProfiles(vehicleProfiles);
    }
    notifyListeners();
  }

  // Vehicle Profiles (the active vehicle owns consumption, fuel price and wear rate)
  Future<void> switchActiveVehicle(VehicleProfile profile) async {
    activeVehicleId = profile.id;
    await repository.setActiveVehicleId(profile.id);
    await updateSettings(driverSettings.copyWith(
      fuelConsumptionLPer100Km: profile.fuelConsumptionLPer100Km,
      fuelPricePerLiter: profile.fuelPricePerLiter,
      wearAndTearPerKm: profile.wearAndTearPerKm,
    ));
  }

  Future<void> addVehicleProfile(VehicleProfile profile) async {
    vehicleProfiles = List<VehicleProfile>.from(vehicleProfiles)..add(profile);
    await repository.saveVehicleProfiles(vehicleProfiles);
    await switchActiveVehicle(profile);
  }

  Future<void> updateVehicleProfile(VehicleProfile profile) async {
    _replaceProfile(profile);
    await repository.saveVehicleProfiles(vehicleProfiles);
    if (profile.id == activeVehicleId) {
      await switchActiveVehicle(profile);
    } else {
      notifyListeners();
    }
  }

  /// Deletes a vehicle. The last remaining vehicle cannot be deleted.
  Future<bool> deleteVehicleProfile(VehicleProfile profile) async {
    if (vehicleProfiles.length <= 1) return false;
    vehicleProfiles = vehicleProfiles.where((p) => p.id != profile.id).toList();
    await repository.saveVehicleProfiles(vehicleProfiles);
    await repository.deleteExpensesOfVehicle(profile.id);
    if (profile.id == activeVehicleId) {
      await switchActiveVehicle(vehicleProfiles.first);
    } else {
      notifyListeners();
    }
    return true;
  }

  // Settings
  /// Applies settings immediately (so rapid taps build on the latest value), then persists them,
  /// mirrors vehicle cost fields into the active vehicle and pushes everything to the native HUD.
  Future<void> updateSettings(DriverSettings newSettings) async {
    driverSettings = newSettings;
    _recalculateAnalysis();

    final active = activeProfile;
    final vehicleChanged = active != null && !_vehicleMatchesSettings(active);
    if (vehicleChanged) {
      _replaceProfile(active!.copyWith(
        fuelConsumptionLPer100Km: newSettings.fuelConsumptionLPer100Km,
        fuelPricePerLiter: newSettings.fuelPricePerLiter,
        wearAndTearPerKm: newSettings.wearAndTearPerKm,
      ));
    }
    notifyListeners();

    await repository.updateDriverSettings(newSettings);
    if (vehicleChanged) await repository.saveVehicleProfiles(vehicleProfiles);
    await _pushToNative();
  }

  bool get autoAcceptConsentGiven => repository.autoAcceptConsentGiven;
  Future<void> recordAutoAcceptConsent() => repository.setAutoAcceptConsentGiven();

  /// Updates what the floating HUD shows; applied live to the running overlay.
  Future<void> updateHud(HudSettings newHud) async {
    hud = newHud;
    notifyListeners();
    await repository.saveHudSettings(newHud);
    await _pushToNative();
  }

  // ---------------------------------------------------------------------------
  // Calculator input memory
  // ---------------------------------------------------------------------------
  Timer? _saveInputsDebounce;

  void _restoreCalculatorInputs() {
    final saved = repository.getCalculatorInputs();
    pickupDistanceKm = saved['pickup'] ?? pickupDistanceKm;
    tripDistanceKm = saved['trip'] ?? tripDistanceKm;
    offeredFare = saved['fare'] ?? offeredFare;
    estimatedMinutes = FareCalculatorEngine.estimateMinutes(pickupDistanceKm + tripDistanceKm);
  }

  void _scheduleSaveInputs() {
    _saveInputsDebounce?.cancel();
    _saveInputsDebounce = Timer(const Duration(milliseconds: 700), () {
      repository.saveCalculatorInputs({'pickup': pickupDistanceKm, 'trip': tripDistanceKm, 'fare': offeredFare});
    });
  }

  void resetCalculator() {
    pickupDistanceKm = 2.0;
    tripDistanceKm = 9.5;
    offeredFare = 85.0;
    estimatedMinutes = FareCalculatorEngine.estimateMinutes(pickupDistanceKm + tripDistanceKm);
    _recalculateAnalysis();
    _scheduleSaveInputs();
    notifyListeners();
  }

  @override
  void dispose() {
    _saveInputsDebounce?.cancel();
    super.dispose();
  }

  // ---------------------------------------------------------------------------
  // Extra statistics
  // ---------------------------------------------------------------------------
  double get todayAverageProfitPerTrip => todayTrips.isEmpty ? 0.0 : todayTotalNetProfit / todayTrips.length;

  double get todayAverageProfitPerKm {
    final km = todayTrips.fold(0.0, (sum, t) => sum + t.totalDistanceKm);
    return km <= 0 ? 0.0 : todayTotalNetProfit / km;
  }

  /// Net profit per hour of the running shift (null when no shift is active or it just started).
  double? get shiftProfitPerHour {
    if (!isShiftActive || shiftStartTime <= 0) return null;
    final hours = shiftDuration.inMinutes / 60.0;
    if (hours < 0.25) return null;
    final shiftTrips = repository.getTripsSince(shiftStartTime);
    return shiftTrips.fold(0.0, (sum, t) => sum + t.netProfit) / hours;
  }

  /// Last [days] days (oldest first): date, trip count, gross and net per day.
  List<DaySummary> lastDaysSummary({int days = 7}) {
    final now = DateTime.now();
    final start = DateTime(now.year, now.month, now.day).subtract(Duration(days: days - 1));
    final trips = repository.getTripsSince(start.millisecondsSinceEpoch);
    return List.generate(days, (i) {
      final day = start.add(Duration(days: i));
      final next = day.add(const Duration(days: 1));
      final dayTrips = trips.where((t) =>
          t.timestamp >= day.millisecondsSinceEpoch && t.timestamp < next.millisecondsSinceEpoch);
      return DaySummary(
        day: day,
        trips: dayTrips.length,
        gross: dayTrips.fold(0.0, (s, t) => s + t.offeredFare),
        net: dayTrips.fold(0.0, (s, t) => s + t.netProfit),
      );
    });
  }

  // Undo support
  Future<void> restoreTrip(Trip trip) async {
    await repository.recordTrip(trip);
    _tripsVersion++;
    notifyListeners();
  }

  Future<void> restoreExpense(Expense expense) async {
    await repository.recordExpense(expense);
    final applied = expense.appliedWearPerKm;
    if (applied != null && applied > 0) {
      await _addToWearRate(expense.vehicleId, applied);
    } else {
      notifyListeners();
    }
  }

  // Zone Actions
  Future<bool> addFavoriteZone(String zone) async {
    final clean = zone.trim();
    if (clean.isEmpty || driverSettings.favoriteZones.any((z) => z.toLowerCase() == clean.toLowerCase())) return false;
    await updateSettings(driverSettings.copyWith(favoriteZones: [...driverSettings.favoriteZones, clean]));
    return true;
  }

  Future<bool> removeFavoriteZone(String zone) async {
    final clean = zone.trim().toLowerCase();
    await updateSettings(driverSettings.copyWith(
      favoriteZones: driverSettings.favoriteZones.where((z) => z.toLowerCase() != clean).toList(),
    ));
    return true;
  }

  Future<bool> addBlacklistZone(String zone) async {
    final clean = zone.trim();
    if (clean.isEmpty || driverSettings.blacklistedZones.any((z) => z.toLowerCase() == clean.toLowerCase())) return false;
    await updateSettings(driverSettings.copyWith(blacklistedZones: [...driverSettings.blacklistedZones, clean]));
    return true;
  }

  Future<bool> removeBlacklistZone(String zone) async {
    final clean = zone.trim().toLowerCase();
    await updateSettings(driverSettings.copyWith(
      blacklistedZones: driverSettings.blacklistedZones.where((z) => z.toLowerCase() != clean).toList(),
    ));
    return true;
  }
}

/// One day of trips for the weekly summary.
class DaySummary {
  final DateTime day;
  final int trips;
  final double gross;
  final double net;

  const DaySummary({required this.day, required this.trips, required this.gross, required this.net});
}
