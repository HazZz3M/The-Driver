import 'dart:convert';
import 'package:shared_preferences/shared_preferences.dart';
import '../models/expense.dart';
import '../models/trip.dart';

class DatabaseService {
  static const String keyTrips = "trips_data_list";
  static const String keyExpenses = "expenses_data_list";

  final SharedPreferences prefs;

  DatabaseService(this.prefs);

  // ==========================================
  // TRIPS OPERATIONS
  // ==========================================
  List<Trip> getAllTrips() {
    final raw = prefs.getString(keyTrips);
    if (raw == null || raw.isEmpty) return [];
    try {
      final list = jsonDecode(raw) as List<dynamic>;
      final trips = list.map((e) => Trip.fromJson(e as Map<String, dynamic>)).toList();
      trips.sort((a, b) => b.timestamp.compareTo(a.timestamp));
      return trips;
    } catch (_) {
      return [];
    }
  }

  List<Trip> getTripsSince(int timestamp) {
    return getAllTrips().where((t) => t.timestamp >= timestamp).toList();
  }

  double getTotalFareSince(int timestamp) {
    final trips = getTripsSince(timestamp);
    return trips.fold(0.0, (sum, t) => sum + t.offeredFare);
  }

  double getTotalProfitSince(int timestamp) {
    final trips = getTripsSince(timestamp);
    return trips.fold(0.0, (sum, t) => sum + t.netProfit);
  }

  int getCompletedTripsCountSince(int timestamp) {
    return getTripsSince(timestamp).where((t) => t.status == "COMPLETED").length;
  }

  Future<void> recordTrip(Trip trip) async {
    final current = getAllTrips();
    current.insert(0, trip);
    final raw = jsonEncode(current.map((t) => t.toJson()).toList());
    await prefs.setString(keyTrips, raw);
  }

  Future<void> deleteTrip(int tripId) async {
    final current = getAllTrips().where((t) => t.id != tripId).toList();
    final raw = jsonEncode(current.map((t) => t.toJson()).toList());
    await prefs.setString(keyTrips, raw);
  }

  Future<void> clearAllTrips() async {
    await prefs.remove(keyTrips);
  }

  // ==========================================
  // EXPENSES OPERATIONS
  // ==========================================
  List<Expense> getAllExpenses() {
    final raw = prefs.getString(keyExpenses);
    if (raw == null || raw.isEmpty) return [];
    try {
      final list = jsonDecode(raw) as List<dynamic>;
      final expenses = list.map((e) => Expense.fromJson(e as Map<String, dynamic>)).toList();
      expenses.sort((a, b) => b.timestamp.compareTo(a.timestamp));
      return expenses;
    } catch (_) {
      return [];
    }
  }

  List<Expense> getExpensesSince(int timestamp) {
    return getAllExpenses().where((e) => e.timestamp >= timestamp).toList();
  }

  double getTotalExpensesSince(int timestamp) {
    final expenses = getExpensesSince(timestamp);
    return expenses.fold(0.0, (sum, e) => sum + e.amount);
  }

  List<Expense> getFuelRefills() {
    return getAllExpenses().where((e) => e.category == "FUEL").toList();
  }

  double? getAverageFuelConsumption() {
    final refills = getFuelRefills().where((e) => e.consumptionLPer100Km != null && e.consumptionLPer100Km! > 0).toList();
    if (refills.isEmpty) return null;
    final total = refills.fold(0.0, (sum, e) => sum + e.consumptionLPer100Km!);
    return total / refills.length;
  }

  double? getAverageFuelCostPerKm() {
    final refills = getFuelRefills().where((e) => e.fuelCostPerKm != null && e.fuelCostPerKm! > 0).toList();
    if (refills.isEmpty) return null;
    final total = refills.fold(0.0, (sum, e) => sum + e.fuelCostPerKm!);
    return total / refills.length;
  }

  List<Expense> getOilChanges() {
    return getAllExpenses().where((e) => e.category == "OIL_CHANGE").toList();
  }

  Future<void> recordExpense(Expense expense) async {
    final current = getAllExpenses();
    current.insert(0, expense);
    final raw = jsonEncode(current.map((e) => e.toJson()).toList());
    await prefs.setString(keyExpenses, raw);
  }

  Future<void> deleteExpense(int expenseId) async {
    final current = getAllExpenses().where((e) => e.id != expenseId).toList();
    final raw = jsonEncode(current.map((e) => e.toJson()).toList());
    await prefs.setString(keyExpenses, raw);
  }
}
