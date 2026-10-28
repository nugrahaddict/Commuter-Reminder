package com.personaldevelopment.commuterreminder.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface StationDao {

    @Query("SELECT * FROM stations ORDER BY line, orderIndex")
    suspend fun getAll(): List<StationEntity>

    @Query("SELECT * FROM stations WHERE line = :line ORDER BY orderIndex")
    suspend fun getByLine(line: String): List<StationEntity>

    @Query("SELECT COUNT(*) FROM stations")
    suspend fun count(): Int

    @Query("SELECT * FROM stations WHERE id = :id")
    suspend fun getById(id: String): StationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(stations: List<StationEntity>)

    @Query("UPDATE stations SET latitude = :latitude, longitude = :longitude WHERE id = :id")
    suspend fun updateCoordinates(id: String, latitude: Double, longitude: Double)
}
