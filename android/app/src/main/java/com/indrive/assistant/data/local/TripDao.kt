package com.indrive.assistant.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.indrive.assistant.data.model.Trip
import kotlinx.coroutines.flow.Flow

@Dao
interface TripDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrip(trip: Trip): Long

    @Query("SELECT * FROM trips ORDER BY timestamp DESC")
    fun getAllTrips(): Flow<List<Trip>>

    @Query("SELECT * FROM trips WHERE timestamp >= :sinceTimestamp ORDER BY timestamp DESC")
    fun getTripsSince(sinceTimestamp: Long): Flow<List<Trip>>

    @Query("SELECT SUM(offeredFare) FROM trips WHERE timestamp >= :sinceTimestamp")
    fun getTotalFareSince(sinceTimestamp: Long): Flow<Double?>

    @Query("SELECT SUM(netProfit) FROM trips WHERE timestamp >= :sinceTimestamp")
    fun getTotalProfitSince(sinceTimestamp: Long): Flow<Double?>

    @Query("SELECT COUNT(*) FROM trips WHERE timestamp >= :sinceTimestamp")
    fun getCompletedTripsCountSince(sinceTimestamp: Long): Flow<Int>

    @Query("DELETE FROM trips WHERE id = :tripId")
    suspend fun deleteTrip(tripId: Long)

    @Query("DELETE FROM trips")
    suspend fun clearAllTrips()
}
