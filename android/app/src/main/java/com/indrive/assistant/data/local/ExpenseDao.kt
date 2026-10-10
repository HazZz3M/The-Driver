package com.indrive.assistant.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.indrive.assistant.data.model.Expense
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpense(expense: Expense): Long

    @Query("SELECT * FROM expenses ORDER BY timestamp DESC")
    fun getAllExpenses(): Flow<List<Expense>>

    @Query("SELECT * FROM expenses WHERE timestamp >= :sinceTimestamp ORDER BY timestamp DESC")
    fun getExpensesSince(sinceTimestamp: Long): Flow<List<Expense>>

    @Query("SELECT SUM(amount) FROM expenses WHERE timestamp >= :sinceTimestamp")
    fun getTotalExpensesSince(sinceTimestamp: Long): Flow<Double?>

    @Query("SELECT * FROM expenses WHERE category = 'FUEL' ORDER BY timestamp DESC")
    fun getFuelRefills(): Flow<List<Expense>>

    @Query("SELECT AVG(consumptionLPer100Km) FROM expenses WHERE category = 'FUEL' AND consumptionLPer100Km IS NOT NULL AND consumptionLPer100Km > 0")
    fun getAverageFuelConsumption(): Flow<Double?>

    @Query("SELECT AVG(fuelCostPerKm) FROM expenses WHERE category = 'FUEL' AND fuelCostPerKm IS NOT NULL AND fuelCostPerKm > 0")
    fun getAverageFuelCostPerKm(): Flow<Double?>

    @Query("SELECT * FROM expenses WHERE category = 'OIL_CHANGE' ORDER BY timestamp DESC")
    fun getOilChanges(): Flow<List<Expense>>

    @Query("DELETE FROM expenses WHERE id = :expenseId")
    suspend fun deleteExpense(expenseId: Long)
}
