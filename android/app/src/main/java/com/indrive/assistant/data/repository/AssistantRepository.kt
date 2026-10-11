package com.indrive.assistant.data.repository

import com.indrive.assistant.data.local.AppDatabase
import com.indrive.assistant.data.local.PreferencesManager
import com.indrive.assistant.data.model.DriverSettings
import com.indrive.assistant.data.model.Expense
import com.indrive.assistant.data.model.Trip
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

class AssistantRepository(
    private val database: AppDatabase,
    private val preferencesManager: PreferencesManager
) {
    val driverSettingsFlow: StateFlow<DriverSettings> = preferencesManager.settingsFlow
    val vehicleProfilesFlow = preferencesManager.vehicleProfilesFlow
    val activeVehicleIdFlow = preferencesManager.activeVehicleIdFlow
    val themeModeFlow = preferencesManager.themeModeFlow
    val overlayStyleFlow = preferencesManager.overlayStyleFlow
    val overlaySizeFlow = preferencesManager.overlaySizeFlow

    fun getDriverSettings(): DriverSettings = preferencesManager.getSettings()

    fun updateDriverSettings(settings: DriverSettings) {
        preferencesManager.saveSettings(settings)
    }

    fun switchActiveVehicle(profile: com.indrive.assistant.data.model.VehicleProfile) {
        preferencesManager.switchActiveVehicle(profile)
    }

    fun saveVehicleProfiles(profiles: List<com.indrive.assistant.data.model.VehicleProfile>) {
        preferencesManager.saveVehicleProfiles(profiles)
    }

    fun setThemeMode(mode: String) {
        preferencesManager.setThemeMode(mode)
    }

    fun setOverlayStyle(style: String) {
        preferencesManager.setOverlayStyle(style)
    }

    fun setOverlaySize(size: String) {
        preferencesManager.setOverlaySize(size)
    }

    fun updateWearAndTearRate(rate: Double) {
        preferencesManager.updateWearAndTearRate(rate)
    }

    fun addFavoriteZone(zone: String): Boolean = preferencesManager.addFavoriteZone(zone)
    fun addBlacklistZone(zone: String): Boolean = preferencesManager.addBlacklistZone(zone)
    fun getPreferencesManager(): PreferencesManager = preferencesManager

    // Trips
    fun getAllTrips(): Flow<List<Trip>> = database.tripDao().getAllTrips()

    fun getTripsSince(timestamp: Long): Flow<List<Trip>> = database.tripDao().getTripsSince(timestamp)

    fun getTotalFareSince(timestamp: Long): Flow<Double?> = database.tripDao().getTotalFareSince(timestamp)

    fun getTotalProfitSince(timestamp: Long): Flow<Double?> = database.tripDao().getTotalProfitSince(timestamp)

    fun getCompletedTripsCountSince(timestamp: Long): Flow<Int> = database.tripDao().getCompletedTripsCountSince(timestamp)

    suspend fun recordTrip(trip: Trip): Long = database.tripDao().insertTrip(trip)

    suspend fun deleteTrip(tripId: Long) = database.tripDao().deleteTrip(tripId)

    suspend fun clearTrips() = database.tripDao().clearAllTrips()

    // Expenses
    fun getAllExpenses(): Flow<List<Expense>> = database.expenseDao().getAllExpenses()

    fun getExpensesSince(timestamp: Long): Flow<List<Expense>> = database.expenseDao().getExpensesSince(timestamp)

    fun getTotalExpensesSince(timestamp: Long): Flow<Double?> = database.expenseDao().getTotalExpensesSince(timestamp)

    fun getFuelRefills(): Flow<List<Expense>> = database.expenseDao().getFuelRefills()

    fun getAverageFuelConsumption(): Flow<Double?> = database.expenseDao().getAverageFuelConsumption()

    fun getAverageFuelCostPerKm(): Flow<Double?> = database.expenseDao().getAverageFuelCostPerKm()

    fun getOilChanges(): Flow<List<Expense>> = database.expenseDao().getOilChanges()

    suspend fun recordExpense(expense: Expense): Long = database.expenseDao().insertExpense(expense)

    suspend fun deleteExpense(expenseId: Long) = database.expenseDao().deleteExpense(expenseId)
}
