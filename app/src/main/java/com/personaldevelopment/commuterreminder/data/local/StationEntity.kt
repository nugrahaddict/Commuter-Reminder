package com.personaldevelopment.commuterreminder.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "stations")
data class StationEntity(
    @PrimaryKey val id: String,
    val name: String,
    val line: String,
    val orderIndex: Int,
    val latitude: Double,
    val longitude: Double
)
