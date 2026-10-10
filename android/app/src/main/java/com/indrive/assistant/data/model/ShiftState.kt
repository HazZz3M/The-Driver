package com.indrive.assistant.data.model

data class ShiftState(
    val isShiftActive: Boolean = false,
    val shiftStartTime: Long = 0L,
    val shiftEndTime: Long = 0L,
    val targetEarnings: Double = 1500.0,
    val completedTripsCount: Int = 0,
    val totalGrossFare: Double = 0.0,
    val totalFuelSpent: Double = 0.0,
    val totalNetProfit: Double = 0.0,
    val totalKilometers: Double = 0.0
) {
    val durationMinutes: Long
        get() {
            if (!isShiftActive || shiftStartTime == 0L) return 0L
            return (System.currentTimeMillis() - shiftStartTime) / (1000 * 60)
        }

    val progressPercent: Float
        get() {
            if (targetEarnings <= 0) return 0f
            return ((totalGrossFare / targetEarnings) * 100f).toFloat().coerceIn(0f, 100f)
        }
}
