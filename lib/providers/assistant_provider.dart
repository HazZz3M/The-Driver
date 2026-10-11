import 'package:flutter/foundation.dart';
import '../data/models/driver_settings.dart';
import '../data/models/expense.dart';
import '../data/models/trip.dart';
import '../data/models/vehicle_profile.dart';
import '../data/repository/assistant_repository.dart';
import '../engine/fare_calculator_engine.dart';

class AssistantProvider extends ChangeNotifier {
  final AssistantRepository repository;

  // Settings & Theme
  late DriverSettings driverSettings;
  late List<VehicleProfile> vehicleProfiles;
  late String activeVehicleId;
  late String themeMode;
  late String overlayStyle;
  late String overlaySize;

  // Calculator inputs
  double pickupDistanceKm = 2.0;
  double tripDistanceKm = 9.5;
  int estimatedMinutes = 24;
  double offeredFare = 85.0;

  // Shift tracking
  bool isShiftActive = false;
  int shiftStartTime = 0;

  // Analysis result
  late FareAnalysisResult fareAnalysis;

  AssistantProvider(this.repository) {
    _loadInitialData();
  }

  void _loadInitialData() {
    driverSettings = repository.getDriverSettings();
    vehicleProfiles = repository.getVehicleProfiles();
    activeVehicleId = repository.getActiveVehicleId();
    themeMode = repository.getThemeMode();
    overlayStyle = repository.getOverlayStyle();
    overlaySize = repository.getOverlaySize();

    _recalculateAnalysis();
  }

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
    try {
      return vehicleProfiles.firstWhere((p) => p.id == activeVehicleId);
    } catch (_) {
      return vehicleProfiles.isNotEmpty ? vehicleProfiles.first : null;
    }
  }

  // Calculator Input Mutators
  void setPickupDistance(double value) {
    pickupDistanceKm = value;
    _recalculateAnalysis();
    notifyListeners();
  }

  void setTripDistance(double value) {
    tripDistanceKm = value;
    estimatedMinutes = ((pickupDistanceKm + value) * 2.5).toInt().clamp(5, 300);
    _recalculateAnalysis();
    notifyListeners();
  }

  void setEstimatedMinutes(int value) {
    estimatedMinutes = value;
    _recalculateAnalysis();
    notifyListeners();
  }

  void setOfferedFare(double value) {
    offeredFare = value;
    _recalculateAnalysis();
    notifyListeners();
  }

  // Shift Management
  void toggleShift() {
    isShiftActive = !isShiftActive;
    shiftStartTime = isShiftActive ? DateTime.now().millisecondsSinceEpoch : 0;
    notifyListeners();
  }

  int get todayMidnightTimestamp {
    final now = DateTime.now();
    return DateTime(now.year, now.month, now.day).millisecondsSinceEpoch;
  }

  List<Trip> get todayTrips => repository.getTripsSince(todayMidnightTimestamp);
  double get todayTotalGross => repository.getTotalFareSince(todayMidnightTimestamp);
  double get todayTotalNetProfit => repository.getTotalProfitSince(todayMidnightTimestamp);
  int get todayTripsCount => repository.getCompletedTripsCountSince(todayMidnightTimestamp);

  double get shiftProgress {
    if (driverSettings.targetDailyEarnings <= 0) return 0.0;
    return (todayTotalGross / driverSettings.targetDailyEarnings).clamp(0.0, 1.0);
  }

  // Expenses & Fuel Analytics
  List<Expense> get allExpenses => repository.getAllExpenses();
  double get totalExpensesAmount => repository.getTotalExpensesSince(todayMidnightTimestamp);
  List<Expense> get fuelRefills => repository.getFuelRefills();
  double? get averageFuelConsumption => repository.getAverageFuelConsumption();
  double? get averageFuelCostPerKm => repository.getAverageFuelCostPerKm();
  List<Expense> get oilChanges => repository.getOilChanges();

  Future<void> saveCurrentRide([double? fareToSave]) async {
    final actualFare = fareToSave ?? offeredFare;
    final recalculated = FareCalculatorEngine.analyze(
      pickupDistanceKm: fareAnalysis.pickupDistanceKm,
      tripDistanceKm: fareAnalysis.tripDistanceKm,
      estimatedMinutes: fareAnalysis.estimatedMinutes,
      offeredFare: actualFare,
      settings: driverSettings,
    );

    final trip = Trip(
      pickupDistanceKm: recalculated.pickupDistanceKm,
      tripDistanceKm: recalculated.tripDistanceKm,
      estimatedMinutes: recalculated.estimatedMinutes,
      offeredFare: actualFare,
      fuelCost: recalculated.fuelCost,
      commissionCost: recalculated.commissionCost,
      wearAndTearCost: recalculated.wearAndTearCost,
      netProfit: recalculated.netProfit,
      profitPerKm: recalculated.profitPerKm,
    );

    await repository.recordTrip(trip);
    notifyListeners();
  }

  Future<void> addFuelRefill({
    required double liters,
    required double pricePerLiter,
    required double kmSinceLastRefill,
    String description = "",
  }) async {
    final totalAmount = liters * pricePerLiter;
    final consumption = kmSinceLastRefill > 0 ? (liters / kmSinceLastRefill) * 100.0 : 0.0;
    final costPerKm = kmSinceLastRefill > 0 ? totalAmount / kmSinceLastRefill : 0.0;

    final expense = Expense(
      category: "FUEL",
      amount: totalAmount,
      liters: liters,
      pricePerLiter: pricePerLiter,
      kmSinceLastRefill: kmSinceLastRefill,
      consumptionLPer100Km: consumption,
      fuelCostPerKm: costPerKm,
      description: description.isNotEmpty
          ? description
          : "تفويلة بنزين (${liters.toStringAsFixed(1)} لتر @ ${pricePerLiter.toStringAsFixed(2)})",
    );

    await repository.recordExpense(expense);

    // Auto-update driver settings with measured consumption & current fuel price
    if (consumption > 0) {
      final updated = driverSettings.copyWith(
        fuelConsumptionLPer100Km: (consumption * 10).round() / 10.0,
        fuelPricePerLiter: pricePerLiter,
      );
      await updateSettings(updated);
    }
    notifyListeners();
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

    final expense = Expense(
      category: "OIL_CHANGE",
      amount: totalCost,
      oilPricePerLiter: oilPricePerLiter,
      oilVolumeMl: oilVolumeMl,
      oilLifespanKm: oilLifespanKm,
      oilCostPerKm: costPerKm,
      description: description.isNotEmpty
          ? description
          : "غيار زيت موتور ($oilVolumeMl ملل، نزل بعد ${oilLifespanKm.toInt()} كم)",
    );

    await repository.recordExpense(expense);
    notifyListeners();
  }

  Future<void> addSparePart({
    required String partName,
    required double amount,
    double? lifespanKm,
    required bool updateWearTear,
    String description = "",
  }) async {
    final costPerKm = (lifespanKm != null && lifespanKm > 0) ? amount / lifespanKm : null;
    final expense = Expense(
      category: "SPARE_PART",
      amount: amount,
      partName: partName,
      partLifespanKm: lifespanKm,
      partCostPerKm: costPerKm,
      description: description.isNotEmpty
          ? description
          : (lifespanKm != null && lifespanKm > 0
              ? "قطعة غيار: $partName (عمر افتراضي: ${lifespanKm.toInt()} كم • ${costPerKm!.toStringAsFixed(2)} ج/كم)"
              : "قطعة غيار / صيانة: $partName"),
    );

    await repository.recordExpense(expense);

    if (updateWearTear && costPerKm != null && costPerKm > 0) {
      final newRate = ((driverSettings.wearAndTearPerKm + costPerKm) * 100).round() / 100.0;
      await repository.updateWearAndTearRate(newRate);
      driverSettings = repository.getDriverSettings();
      _recalculateAnalysis();
    }
    notifyListeners();
  }

  Future<void> deleteTrip(int tripId) async {
    await repository.deleteTrip(tripId);
    notifyListeners();
  }

  Future<void> deleteExpense(int expenseId) async {
    await repository.deleteExpense(expenseId);
    notifyListeners();
  }

  // Vehicle Profile Switching
  Future<void> switchActiveVehicle(VehicleProfile profile) async {
    await repository.switchActiveVehicle(profile);
    activeVehicleId = profile.id;
    driverSettings = repository.getDriverSettings();
    _recalculateAnalysis();
    notifyListeners();
  }

  Future<void> addVehicleProfile(VehicleProfile profile) async {
    final current = List<VehicleProfile>.from(vehicleProfiles)..add(profile);
    await repository.saveVehicleProfiles(current);
    vehicleProfiles = current;
    await switchActiveVehicle(profile);
  }

  // Settings & Themes
  Future<void> updateSettings(DriverSettings newSettings) async {
    await repository.updateDriverSettings(newSettings);
    driverSettings = newSettings;
    _recalculateAnalysis();
    notifyListeners();
  }

  Future<void> setThemeMode(String mode) async {
    await repository.setThemeMode(mode);
    themeMode = mode;
    notifyListeners();
  }

  Future<void> setOverlayStyle(String style) async {
    await repository.setOverlayStyle(style);
    overlayStyle = style;
    notifyListeners();
  }

  Future<void> setOverlaySize(String size) async {
    await repository.setOverlaySize(size);
    overlaySize = size;
    notifyListeners();
  }

  // Zone Actions
  Future<bool> addFavoriteZone(String zone) async {
    final success = await repository.addFavoriteZone(zone);
    if (success) {
      driverSettings = repository.getDriverSettings();
      _recalculateAnalysis();
      notifyListeners();
    }
    return success;
  }

  Future<bool> removeFavoriteZone(String zone) async {
    final success = await repository.removeFavoriteZone(zone);
    if (success) {
      driverSettings = repository.getDriverSettings();
      _recalculateAnalysis();
      notifyListeners();
    }
    return success;
  }

  Future<bool> addBlacklistZone(String zone) async {
    final success = await repository.addBlacklistZone(zone);
    if (success) {
      driverSettings = repository.getDriverSettings();
      _recalculateAnalysis();
      notifyListeners();
    }
    return success;
  }

  Future<bool> removeBlacklistZone(String zone) async {
    final success = await repository.removeBlacklistZone(zone);
    if (success) {
      driverSettings = repository.getDriverSettings();
      _recalculateAnalysis();
      notifyListeners();
    }
    return success;
  }
}
