import 'dart:convert';
import 'package:shared_preferences/shared_preferences.dart';
import '../models/expense.dart';
import '../models/trip.dart';
import 'preferences_manager.dart';

class DatabaseService {
  static const String keyTrips = "trips_data_list";
  static const String keyExpenses = "expenses_data_list";

  final SharedPreferences prefs;

  List<Trip>? _cachedTrips;
  List<Expense>? _cachedExpenses;

  DatabaseService(this.prefs);

  // ==========================================
  // TRIPS OPERATIONS
  // ==========================================
  List<Trip> getAllTrips() {
    if (_cachedTrips != null) {
      return List<Trip>.from(_cachedTrips!);
    }
    final raw = prefs.getString(keyTrips);
    final trips = (raw == null || raw.isEmpty)
        ? <Trip>[]
        : decodeJsonListSafely(prefs: prefs, key: keyTrips, raw: raw, fromJson: Trip.fromJson);
    trips.sort((a, b) => b.timestamp.compareTo(a.timestamp));
    _cachedTrips = trips;
    return List<Trip>.from(trips);
  }

  List<Trip> getTripsSince(int timestamp) {
    return getAllTrips().where((t) => t.timestamp >= timestamp).toList();
  }

  Future<void> recordTrip(Trip trip) async {
    final current = getAllTrips()
      ..insert(0, trip)
      ..sort((a, b) => b.timestamp.compareTo(a.timestamp));
    _cachedTrips = current;
    await prefs.setString(keyTrips, jsonEncode(current.map((t) => t.toJson()).toList()));
  }

  Future<void> deleteTrip(int tripId) async {
    final current = getAllTrips().where((t) => t.id != tripId).toList();
    _cachedTrips = current;
    await prefs.setString(keyTrips, jsonEncode(current.map((t) => t.toJson()).toList()));
  }

  Future<void> clearAllTrips() async {
    _cachedTrips = [];
    await prefs.remove(keyTrips);
  }

  // ==========================================
  // EXPENSES OPERATIONS
  // ==========================================
  List<Expense> getAllExpenses() {
    if (_cachedExpenses != null) {
      return List<Expense>.from(_cachedExpenses!);
    }
    final raw = prefs.getString(keyExpenses);
    final expenses = (raw == null || raw.isEmpty)
        ? <Expense>[]
        : decodeJsonListSafely(prefs: prefs, key: keyExpenses, raw: raw, fromJson: Expense.fromJson);
    expenses.sort((a, b) => b.timestamp.compareTo(a.timestamp));
    _cachedExpenses = expenses;
    return List<Expense>.from(expenses);
  }

  List<Expense> getExpensesSince(int timestamp) {
    return getAllExpenses().where((e) => e.timestamp >= timestamp).toList();
  }

  double getTotalExpensesSince(int timestamp) {
    return getExpensesSince(timestamp).fold(0.0, (sum, e) => sum + e.amount);
  }

  List<Expense> getFuelRefills() {
    return getAllExpenses().where((e) => e.category == "FUEL").toList();
  }

  /// Average consumption over the most recent [window] refills that recorded distance.
  double? getRecentAverageFuelConsumption({int window = 3}) {
    final refills = getFuelRefills()
        .where((e) => e.consumptionLPer100Km != null && e.consumptionLPer100Km! > 0)
        .take(window)
        .toList();
    if (refills.isEmpty) return null;
    return refills.fold(0.0, (sum, e) => sum + e.consumptionLPer100Km!) / refills.length;
  }

  List<Expense> getOilChanges() {
    return getAllExpenses().where((e) => e.category == "OIL_CHANGE").toList();
  }

  Future<void> recordExpense(Expense expense) async {
    final current = getAllExpenses()
      ..insert(0, expense)
      ..sort((a, b) => b.timestamp.compareTo(a.timestamp));
    _cachedExpenses = current;
    await prefs.setString(keyExpenses, jsonEncode(current.map((e) => e.toJson()).toList()));
  }

  Future<void> deleteExpense(int expenseId) async {
    final current = getAllExpenses().where((e) => e.id != expenseId).toList();
    _cachedExpenses = current;
    await prefs.setString(keyExpenses, jsonEncode(current.map((e) => e.toJson()).toList()));
  }
}
