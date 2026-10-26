import '../local/database_service.dart';
import '../local/preferences_manager.dart';
import '../models/analysis_prefs.dart';
import '../models/driver_settings.dart';
import '../models/expense.dart';
import '../models/hud_settings.dart';
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
  Future<void> updateDriverSettings(DriverSettings settings) => preferencesManager.saveSettings(settings);

  // Vehicles
  List<VehicleProfile> getVehicleProfiles() => preferencesManager.loadVehicleProfiles();
  Future<void> saveVehicleProfiles(List<VehicleProfile> profiles) => preferencesManager.saveVehicleProfiles(profiles);
  String getActiveVehicleId() => preferencesManager.getActiveVehicleId();
  Future<void> setActiveVehicleId(String id) => preferencesManager.setActiveVehicleId(id);

  // Floating HUD
  HudSettings getHudSettings() => preferencesManager.loadHudSettings();
  Future<void> saveHudSettings(HudSettings hud) => preferencesManager.saveHudSettings(hud);

  AnalysisPrefs getAnalysisPrefs() => preferencesManager.loadAnalysisPrefs();
  Future<void> saveAnalysisPrefs(AnalysisPrefs a) => preferencesManager.saveAnalysisPrefs(a);

  // Calculator inputs
  Map<String, double> getCalculatorInputs() => preferencesManager.loadCalculatorInputs();
  Future<void> saveCalculatorInputs(Map<String, double> values) => preferencesManager.saveCalculatorInputs(values);

  // Auto-accept consent
  bool get autoAcceptConsentGiven => preferencesManager.autoAcceptConsentGiven;
  Future<void> setAutoAcceptConsentGiven() => preferencesManager.setAutoAcceptConsentGiven();

  // Tutorial
  bool get tutorialSeen => preferencesManager.tutorialSeen;
  Future<void> setTutorialSeen() => preferencesManager.setTutorialSeen();

  // Shift
  bool get isShiftActive => preferencesManager.isShiftActive;
  int get shiftStartTime => preferencesManager.shiftStartTime;
  Future<void> saveShift({required bool active, required int startTime}) =>
      preferencesManager.saveShift(active: active, startTime: startTime);

  // Trips
  List<Trip> getAllTrips() => database.getAllTrips();
  List<Trip> getTripsSince(int timestamp) => database.getTripsSince(timestamp);
  Future<void> recordTrip(Trip trip) => database.recordTrip(trip);
  Future<void> deleteTrip(int tripId) => database.deleteTrip(tripId);

  // Expenses
  List<Expense> getAllExpenses() => database.getAllExpenses();
  double getTotalExpensesSince(int timestamp) => database.getTotalExpensesSince(timestamp);
  List<Expense> getFuelRefills({String? vehicleId}) => database.getFuelRefills(vehicleId: vehicleId);
  double? getRecentAverageFuelConsumption({String? vehicleId}) =>
      database.getRecentAverageFuelConsumption(vehicleId: vehicleId);
  List<Expense> getOilChanges() => database.getOilChanges();
  Future<void> recordExpense(Expense expense) => database.recordExpense(expense);
  Future<void> deleteExpense(int expenseId) => database.deleteExpense(expenseId);
  Future<void> assignUntaggedExpenses(String vehicleId) => database.assignUntaggedExpenses(vehicleId);
  Future<void> deleteExpensesOfVehicle(String vehicleId) => database.deleteExpensesOfVehicle(vehicleId);
}
