package com.indrive.assistant.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.indrive.assistant.data.model.DriverSettings
import com.indrive.assistant.data.model.Expense
import com.indrive.assistant.data.model.ShiftState
import com.indrive.assistant.data.model.Trip
import com.indrive.assistant.data.repository.AssistantRepository
import com.indrive.assistant.engine.FareAnalysisResult
import com.indrive.assistant.engine.FareCalculatorEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AssistantViewModel(
    private val repository: AssistantRepository
) : ViewModel() {

    val driverSettings: StateFlow<DriverSettings> = repository.driverSettingsFlow
    val vehicleProfiles: StateFlow<List<com.indrive.assistant.data.model.VehicleProfile>> = repository.vehicleProfilesFlow
    val activeVehicleId: StateFlow<String> = repository.activeVehicleIdFlow
    val themeMode: StateFlow<String> = repository.themeModeFlow
    val overlayStyle: StateFlow<String> = repository.overlayStyleFlow
    val overlaySize: StateFlow<String> = repository.overlaySizeFlow

    fun switchActiveVehicle(profile: com.indrive.assistant.data.model.VehicleProfile) {
        repository.switchActiveVehicle(profile)
    }

    fun saveVehicleProfiles(profiles: List<com.indrive.assistant.data.model.VehicleProfile>) {
        repository.saveVehicleProfiles(profiles)
    }

    fun setThemeMode(mode: String) {
        repository.setThemeMode(mode)
    }

    fun setOverlayStyle(style: String) {
        repository.setOverlayStyle(style)
    }

    fun setOverlaySize(size: String) {
        repository.setOverlaySize(size)
    }

    // Calculator inputs
    val pickupDistanceKm = MutableStateFlow(2.0)
    val tripDistanceKm = MutableStateFlow(9.5)
    val estimatedMinutes = MutableStateFlow(24)
    val offeredFare = MutableStateFlow(85.0)

    // Reactive Fare Analysis (auto-recalculates on any parameter or settings change)
    val fareAnalysis: StateFlow<FareAnalysisResult> = combine(
        pickupDistanceKm,
        tripDistanceKm,
        estimatedMinutes,
        offeredFare,
        driverSettings
    ) { pickup, trip, minutes, fare, settings ->
        FareCalculatorEngine.analyze(
            pickupDistanceKm = pickup,
            tripDistanceKm = trip,
            estimatedMinutes = minutes,
            offeredFare = fare,
            settings = settings
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        FareCalculatorEngine.analyze(2.0, 9.5, 24, 85.0, driverSettings.value)
    )

    // Shift Tracking
    private val _isShiftActive = MutableStateFlow(false)
    val isShiftActive: StateFlow<Boolean> = _isShiftActive.asStateFlow()

    private val _shiftStartTime = MutableStateFlow(0L)
    val shiftStartTime: StateFlow<Long> = _shiftStartTime.asStateFlow()

    // Start of current day timestamp
    private val todayMidnightTimestamp: Long
        get() {
            val cal = java.util.Calendar.getInstance()
            cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
            cal.set(java.util.Calendar.MINUTE, 0)
            cal.set(java.util.Calendar.SECOND, 0)
            cal.set(java.util.Calendar.MILLISECOND, 0)
            return cal.timeInMillis
        }

    val todayTrips: StateFlow<List<Trip>> = repository.getTripsSince(todayMidnightTimestamp)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val todayTotalGross: StateFlow<Double> = repository.getTotalFareSince(todayMidnightTimestamp)
        .map { it ?: 0.0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val todayTotalNetProfit: StateFlow<Double> = repository.getTotalProfitSince(todayMidnightTimestamp)
        .map { it ?: 0.0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val todayTripsCount: StateFlow<Int> = repository.getCompletedTripsCountSince(todayMidnightTimestamp)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // Expenses
    val allExpenses: StateFlow<List<Expense>> = repository.getAllExpenses()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalExpensesAmount: StateFlow<Double> = repository.getTotalExpensesSince(todayMidnightTimestamp)
        .map { it ?: 0.0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val fuelRefills: StateFlow<List<Expense>> = repository.getFuelRefills()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val averageFuelConsumption: StateFlow<Double?> = repository.getAverageFuelConsumption()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val averageFuelCostPerKm: StateFlow<Double?> = repository.getAverageFuelCostPerKm()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val oilChanges: StateFlow<List<Expense>> = repository.getOilChanges()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setPickupDistance(value: Double) {
        pickupDistanceKm.value = value
    }

    fun setTripDistance(value: Double) {
        tripDistanceKm.value = value
        // Auto estimate duration ~ 2.5 min per km in city
        estimatedMinutes.value = ((pickupDistanceKm.value + value) * 2.5).toInt().coerceAtLeast(5)
    }

    fun setEstimatedMinutes(value: Int) {
        estimatedMinutes.value = value
    }

    fun setOfferedFare(value: Double) {
        offeredFare.value = value
    }

    fun toggleShift() {
        if (_isShiftActive.value) {
            _isShiftActive.value = false
            _shiftStartTime.value = 0L
        } else {
            _isShiftActive.value = true
            _shiftStartTime.value = System.currentTimeMillis()
        }
    }

    fun saveCurrentRide(fareToSave: Double? = null) {
        val analysis = fareAnalysis.value
        val actualFare = fareToSave ?: offeredFare.value
        val recalculated = FareCalculatorEngine.analyze(
            pickupDistanceKm = analysis.pickupDistanceKm,
            tripDistanceKm = analysis.tripDistanceKm,
            estimatedMinutes = analysis.estimatedMinutes,
            offeredFare = actualFare,
            settings = driverSettings.value
        )

        viewModelScope.launch {
            repository.recordTrip(
                Trip(
                    pickupDistanceKm = recalculated.pickupDistanceKm,
                    tripDistanceKm = recalculated.tripDistanceKm,
                    estimatedMinutes = recalculated.estimatedMinutes,
                    offeredFare = actualFare,
                    fuelCost = recalculated.fuelCost,
                    commissionCost = recalculated.commissionCost,
                    wearAndTearCost = recalculated.wearAndTearCost,
                    netProfit = recalculated.netProfit,
                    profitPerKm = recalculated.profitPerKm
                )
            )
        }
    }

    fun addFuelRefill(
        liters: Double,
        pricePerLiter: Double,
        kmSinceLastRefill: Double,
        description: String = ""
    ) {
        val totalAmount = liters * pricePerLiter
        val consumption = if (kmSinceLastRefill > 0) (liters / kmSinceLastRefill) * 100.0 else 0.0
        val costPerKm = if (kmSinceLastRefill > 0) totalAmount / kmSinceLastRefill else 0.0

        viewModelScope.launch {
            repository.recordExpense(
                Expense(
                    category = "FUEL",
                    amount = totalAmount,
                    liters = liters,
                    pricePerLiter = pricePerLiter,
                    kmSinceLastRefill = kmSinceLastRefill,
                    consumptionLPer100Km = consumption,
                    fuelCostPerKm = costPerKm,
                    description = description.ifBlank { "تفويلة بنزين (${String.format("%.1f", liters)} لتر @ ${String.format("%.2f", pricePerLiter)})" }
                )
            )

            // Auto-update driver settings with measured consumption & current fuel price
            if (consumption > 0) {
                val current = driverSettings.value
                repository.updateDriverSettings(
                    current.copy(
                        fuelConsumptionLPer100Km = Math.round(consumption * 10.0) / 10.0,
                        fuelPricePerLiter = pricePerLiter
                    )
                )
            }
        }
    }

    fun addOilChange(
        oilPricePerLiter: Double,
        oilVolumeMl: Int,
        oilLifespanKm: Double,
        description: String = ""
    ) {
        val liters = oilVolumeMl / 1000.0
        val totalCost = liters * oilPricePerLiter
        val costPerKm = if (oilLifespanKm > 0) totalCost / oilLifespanKm else 0.0

        viewModelScope.launch {
            repository.recordExpense(
                Expense(
                    category = "OIL_CHANGE",
                    amount = totalCost,
                    oilPricePerLiter = oilPricePerLiter,
                    oilVolumeMl = oilVolumeMl,
                    oilLifespanKm = oilLifespanKm,
                    oilCostPerKm = costPerKm,
                    description = description.ifBlank { "غيار زيت موتور (${oilVolumeMl} ملل، نزل بعد ${oilLifespanKm.toInt()} كم)" }
                )
            )
        }
    }

    fun addSparePart(
        partName: String,
        amount: Double,
        lifespanKm: Double?,
        updateWearTear: Boolean,
        description: String = ""
    ) {
        val costPerKm = if (lifespanKm != null && lifespanKm > 0) amount / lifespanKm else null
        viewModelScope.launch {
            repository.recordExpense(
                Expense(
                    category = "SPARE_PART",
                    amount = amount,
                    partName = partName,
                    partLifespanKm = lifespanKm,
                    partCostPerKm = costPerKm,
                    description = description.ifBlank {
                        if (lifespanKm != null && lifespanKm > 0) {
                            "قطعة غيار: $partName (عمر افتراضي: ${lifespanKm.toInt()} كم • ${String.format("%.2f", costPerKm)} ج/كم)"
                        } else {
                            "قطعة غيار / صيانة: $partName"
                        }
                    }
                )
            )

            if (updateWearTear && costPerKm != null && costPerKm > 0) {
                val current = driverSettings.value
                val newRate = Math.round((current.wearAndTearPerKm + costPerKm) * 100.0) / 100.0
                repository.updateWearAndTearRate(newRate)
            }
        }
    }

    fun addExpense(category: String, amount: Double, liters: Double? = null, description: String = "") {
        viewModelScope.launch {
            repository.recordExpense(
                Expense(
                    category = category,
                    amount = amount,
                    liters = liters,
                    description = description
                )
            )
        }
    }

    fun deleteTrip(tripId: Long) {
        viewModelScope.launch {
            repository.deleteTrip(tripId)
        }
    }

    fun deleteExpense(expenseId: Long) {
        viewModelScope.launch {
            repository.deleteExpense(expenseId)
        }
    }

    fun updateSettings(newSettings: DriverSettings) {
        repository.updateDriverSettings(newSettings)
    }
}

class AssistantViewModelFactory(
    private val repository: AssistantRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return AssistantViewModel(repository) as T
    }
}
