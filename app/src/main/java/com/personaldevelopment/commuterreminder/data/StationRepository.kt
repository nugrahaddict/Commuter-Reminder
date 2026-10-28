package com.personaldevelopment.commuterreminder.data

import android.content.Context
import com.personaldevelopment.commuterreminder.data.local.StationDao
import com.personaldevelopment.commuterreminder.data.local.StationEntity
import com.personaldevelopment.commuterreminder.domain.model.Station
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class StationRepository(
    private val dao: StationDao,
    private val seedJsonProvider: suspend () -> String
) {

    suspend fun ensureSeeded() {
        if (dao.count() == 0) {
            dao.insertAll(StationJsonParser.parse(seedJsonProvider()))
        }
    }

    suspend fun getAll(): List<Station> = dao.getAll().map { it.toDomain() }

    suspend fun getByLine(line: String): List<Station> =
        dao.getByLine(line).map { it.toDomain() }

    suspend fun getLines(): List<String> =
        getAll().map { it.line }.distinct().sorted()

    /** Perbarui koordinat satu stasiun di database lokal. */
    suspend fun updateCoordinates(id: String, latitude: Double, longitude: Double) {
        dao.updateCoordinates(id = id, latitude = latitude, longitude = longitude)
    }

    private fun StationEntity.toDomain() = Station(
        id = id,
        name = name,
        line = line,
        orderIndex = orderIndex,
        latitude = latitude,
        longitude = longitude
    )

    companion object {
        fun from(context: Context): StationRepository {
            val db = com.personaldevelopment.commuterreminder.data.local.CommuterDatabase.get(context)
            return StationRepository(db.stationDao()) {
                withContext(Dispatchers.IO) {
                    context.assets.open("stations.json").bufferedReader().use { it.readText() }
                }
            }
        }
    }
}
