import '../local/database_service.dart';
import '../local/preferences_manager.dart';
import '../models/driver_settings.dart';
import '../models/expense.dart';
import '../models/trip.dart';
import '../models/vehicle_profile.dart';

class AssistantRepository {
  final DatabaseService database;
  final PreferencesManager preferencesManager;

  AssistantRepository({
    required this.database,
    required this.preferencesManager,
  });

  // Settings
  DriverSettings getDriverSettings() => preferencesManager.loadSettings();

  Future<void> updateDriverSettings(DriverSettings settings) async {
    await preferencesManager.saveSettings(settings);
  }

  // Vehicles
  List<VehicleProfile> getVehicleProfiles() => preferencesManager.loadVehicleProfiles();

  Future<void> saveVehicleProfiles(List<VehicleProfile> profiles) async {
    await preferencesManager.saveVehicleProfiles(profiles);
  }

  String getActiveVehicleId() => preferencesManager.getActiveVehicleId();

  Future<void> switchActiveVehicle(VehicleProfile profile) async {
    await preferencesManager.setActiveVehicleId(profile.id);
    final current = getDriverSettings();
    final updated = current.copyWith(
      fuelConsumptionLPer100Km: profile.fuelConsumptionLPer100Km,
      fuelPricePerLiter: profile.fuelPricePerLiter,
      inDriveCommissionPercent: profile.inDriveCommissionPercent,
      wearAndTearPerKm: profile.wearAndTearPerKm,
      minProfitPerKm: profile.minProfitPerKm,
    );
    await updateDriverSettings(updated);
  }

  Future<void> updateWearAndTearRate(double rate) async {
    final current = getDriverSettings();
    await updateDriverSettings(current.copyWith(wearAndTearPerKm: rate));
  }

  // Theme & Overlay
  String getThemeMode() => preferencesManager.getThemeMode();
  Future<void> setThemeMode(String mode) async => await preferencesManager.setThemeMode(mode);

  String getOverlayStyle() => preferencesManager.getOverlayStyle();
  Future<void> setOverlayStyle(String style) async => await preferencesManager.setOverlayStyle(style);

  String getOverlaySize() => preferencesManager.getOverlaySize();
  Future<void> setOverlaySize(String size) async => await preferencesManager.setOverlaySize(size);

  // Zones
  Future<bool> addFavoriteZone(String zone) => preferencesManager.addFavoriteZone(zone);
  Future<bool> removeFavoriteZone(String zone) => preferencesManager.removeFavoriteZone(zone);
  Future<bool> addBlacklistZone(String zone) => preferencesManager.addBlacklistZone(zone);
  Future<bool> removeBlacklistZone(String zone) => preferencesManager.removeBlacklistZone(zone);

  bool isFavoriteZone(String zone) => getDriverSettings().isFavorite(zone);
  bool isBlacklistedZone(String zone) => getDriverSettings().isBlacklisted(zone);

  // Trips
  List<Trip> getAllTrips() => database.getAllTrips();
  List<Trip> getTripsSince(int timestamp) => database.getTripsSince(timestamp);
  double getTotalFareSince(int timestamp) => database.getTotalFareSince(timestamp);
  double getTotalProfitSince(int timestamp) => database.getTotalProfitSince(timestamp);
  int getCompletedTripsCountSince(int timestamp) => database.getCompletedTripsCountSince(timestamp);
  Future<void> recordTrip(Trip trip) => database.recordTrip(trip);
  Future<void> deleteTrip(int tripId) => database.deleteTrip(tripId);
  Future<void> clearTrips() => database.clearAllTrips();

  // Expenses
  List<Expense> getAllExpenses() => database.getAllExpenses();
  List<Expense> getExpensesSince(int timestamp) => database.getExpensesSince(timestamp);
  double getTotalExpensesSince(int timestamp) => database.getTotalExpensesSince(timestamp);
  List<Expense> getFuelRefills() => database.getFuelRefills();
  double? getAverageFuelConsumption() => database.getAverageFuelConsumption();
  double? getAverageFuelCostPerKm() => database.getAverageFuelCostPerKm();
  List<Expense> getOilChanges() => database.getOilChanges();
  Future<void> recordExpense(Expense expense) => database.recordExpense(expense);
  Future<void> deleteExpense(int expenseId) => database.deleteExpense(expenseId);
}
