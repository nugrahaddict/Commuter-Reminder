package com.personaldevelopment.commuterreminder.data

import android.content.Context
import com.personaldevelopment.commuterreminder.domain.model.Station
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

/**
 * Persistensi riwayat rute commuter reminder: pasangan tujuan + radius yang
 * pernah dipakai memulai reminder. Terbaru dulu, maks [MAX_HISTORY] entri.
 * Entri dengan tujuan & radius sama digantikan, bukan diduplikasi.
 */
object RouteHistoryStore {

    data class RouteEntry(
        val id: String,
        val stationId: String,
        val stationName: String,
        val stationLine: String,
        val stationLat: Double,
        val stationLon: Double,
        val radiusKm: Double,
        val savedAtMillis: Long
    ) {
        fun toStation(): Station = Station(
            id = stationId,
            name = stationName,
            line = stationLine,
            orderIndex = 0,
            latitude = stationLat,
            longitude = stationLon
        )
    }

    private const val PREFS_NAME = "route_history_prefs"
    private const val KEY_HISTORY = "route_history"

    private const val MAX_HISTORY = 10

    /** Riwayat rute, terbaru dulu. */
    fun history(context: Context): List<RouteEntry> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return decode(prefs.getString(KEY_HISTORY, null))
    }

    /** Simpan pasangan tujuan + radius ke riwayat (terdepan). */
    suspend fun save(
        context: Context,
        destination: Station,
        radiusKm: Double
    ) = withContext(Dispatchers.IO) {
        val entry = RouteEntry(
            id = "route-${System.currentTimeMillis()}",
            stationId = destination.id,
            stationName = destination.name,
            stationLine = destination.line,
            stationLat = destination.latitude,
            stationLon = destination.longitude,
            radiusKm = radiusKm,
            savedAtMillis = System.currentTimeMillis()
        )
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existing = decode(prefs.getString(KEY_HISTORY, null))
        val deduped = existing.filterNot {
            it.stationId == entry.stationId && it.radiusKm == entry.radiusKm
        }
        val updated = (listOf(entry) + deduped).take(MAX_HISTORY)
        prefs.edit().putString(KEY_HISTORY, encode(updated)).apply()
    }

    /** Hapus satu entri riwayat. */
    suspend fun remove(context: Context, id: String) = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val remaining = decode(prefs.getString(KEY_HISTORY, null)).filterNot { it.id == id }
        prefs.edit().putString(KEY_HISTORY, encode(remaining)).apply()
    }

    private fun encode(list: List<RouteEntry>): String {
        val array = JSONArray()
        list.forEach { entry ->
            array.put(
                org.json.JSONObject()
                    .put("id", entry.id)
                    .put("stationId", entry.stationId)
                    .put("stationName", entry.stationName)
                    .put("stationLine", entry.stationLine)
                    .put("stationLat", entry.stationLat)
                    .put("stationLon", entry.stationLon)
                    .put("radiusKm", entry.radiusKm)
                    .put("savedAt", entry.savedAtMillis)
            )
        }
        return array.toString()
    }

    private fun decode(raw: String?): List<RouteEntry> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { i ->
                val obj = array.optJSONObject(i) ?: return@mapNotNull null
                val lat = obj.optDouble("stationLat", Double.NaN)
                val lon = obj.optDouble("stationLon", Double.NaN)
                val radius = obj.optDouble("radiusKm", Double.NaN)
                if (lat.isNaN() || lon.isNaN() || radius.isNaN() || radius <= 0.0) {
                    return@mapNotNull null
                }
                RouteEntry(
                    id = obj.optString("id"),
                    stationId = obj.optString("stationId"),
                    stationName = obj.optString("stationName"),
                    stationLine = obj.optString("stationLine"),
                    stationLat = lat,
                    stationLon = lon,
                    radiusKm = radius,
                    savedAtMillis = obj.optLong("savedAt", 0L)
                )
            }
        }.getOrDefault(emptyList())
    }
}
