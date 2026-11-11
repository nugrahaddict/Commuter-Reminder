package com.personaldevelopment.commuterreminder.data

import android.content.Context
import com.personaldevelopment.commuterreminder.domain.model.Station
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

/**
 * Persistensi lokasi custom (lat, lon, radius km) ke SharedPreferences.
 *
 * Menyimpan satu lokasi aktif + riwayat lokasi (maks [MAX_HISTORY], terbaru
 * dulu) supaya bisa dipakai lagi di lain waktu.
 */
object CustomLocationStore {

    data class CustomLocation(
        val id: String,
        val latitude: Double,
        val longitude: Double,
        val radiusKm: Double,
        val label: String?,
        val savedAtMillis: Long
    ) {
        /** Representasi sebagai Station agar bisa dipakai TrackingService/detektor. */
        fun toStation(): Station = Station(
            id = "custom_$id",
            name = label ?: DEFAULT_NAME,
            line = "",
            orderIndex = 0,
            latitude = latitude,
            longitude = longitude
        )

        companion object {
            const val DEFAULT_NAME = "Lokasi Custom"
        }
    }

    private const val PREFS_NAME = "custom_location_prefs"
    private const val KEY_LAT = "custom_lat"
    private const val KEY_LON = "custom_lon"
    private const val KEY_RADIUS = "custom_radius_km"
    private const val KEY_LABEL = "custom_label"
    private const val KEY_ID = "custom_id"
    private const val KEY_SAVED_AT = "custom_saved_at"
    private const val KEY_HISTORY = "custom_history"

    private const val MAX_HISTORY = 10

    /** Epsilon koordinat untuk dedupe (≈ 55 m). */
    private const val DEDUPE_EPSILON = 0.0005

    /** Baca lokasi custom aktif; null kalau belum pernah disimpan atau rusak. */
    fun load(context: Context): CustomLocation? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.contains(KEY_LAT) || !prefs.contains(KEY_LON)) return null
        val lat = java.lang.Double.longBitsToDouble(prefs.getLong(KEY_LAT, 0L))
        val lon = java.lang.Double.longBitsToDouble(prefs.getLong(KEY_LON, 0L))
        val radius = if (prefs.contains(KEY_RADIUS)) {
            java.lang.Double.longBitsToDouble(prefs.getLong(KEY_RADIUS, 0L))
        } else {
            com.personaldevelopment.commuterreminder.domain.ReminderSettings
                .DEFAULT_ACTIVATION_RADIUS_KM
        }
        val valid = isValidLat(lat) && isValidLon(lon) &&
            !radius.isNaN() && radius > 0.0
        return if (valid) {
            CustomLocation(
                id = prefs.getString(KEY_ID, null) ?: "legacy",
                latitude = lat,
                longitude = lon,
                radiusKm = radius,
                label = prefs.getString(KEY_LABEL, null),
                savedAtMillis = prefs.getLong(KEY_SAVED_AT, 0L)
            )
        } else {
            null
        }
    }

    /** Riwayat lokasi tersimpan, terbaru dulu. */
    fun history(context: Context): List<CustomLocation> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return decodeHistory(prefs.getString(KEY_HISTORY, null))
    }

    /**
     * Simpan lokasi sebagai lokasi aktif dan masukkan ke riwayat (terdepan).
     * Entri riwayat dengan koordinat & label sama digantikan, bukan diduplikasi.
     */
    suspend fun save(
        context: Context,
        latitude: Double,
        longitude: Double,
        radiusKm: Double,
        label: String?
    ) = withContext(Dispatchers.IO) {
        if (!isValidLat(latitude) || !isValidLon(longitude)) return@withContext
        val entry = CustomLocation(
            id = "loc-${System.currentTimeMillis()}",
            latitude = latitude,
            longitude = longitude,
            radiusKm = radiusKm,
            label = label?.takeIf { it.isNotBlank() },
            savedAtMillis = System.currentTimeMillis()
        )
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existing = decodeHistory(prefs.getString(KEY_HISTORY, null))
        val deduped = existing.filter {
            !(samePoint(it, entry) && it.label == entry.label)
        }
        val updated = (listOf(entry) + deduped).take(MAX_HISTORY)
        prefs.edit()
            .putLong(KEY_LAT, java.lang.Double.doubleToRawLongBits(latitude))
            .putLong(KEY_LON, java.lang.Double.doubleToRawLongBits(longitude))
            .putLong(KEY_RADIUS, java.lang.Double.doubleToRawLongBits(radiusKm))
            .putString(KEY_LABEL, entry.label)
            .putString(KEY_ID, entry.id)
            .putLong(KEY_SAVED_AT, entry.savedAtMillis)
            .putString(KEY_HISTORY, encodeHistory(updated))
            .apply()
    }

    /** Ganti nama alias lokasi di riwayat; ikut perbarui label lokasi aktif bila sama id. */
    suspend fun renameAlias(context: Context, id: String, alias: String) =
        withContext(Dispatchers.IO) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val clean = alias.trim().takeIf { it.isNotEmpty() }
            val updated = decodeHistory(prefs.getString(KEY_HISTORY, null)).map {
                if (it.id == id) it.copy(label = clean) else it
            }
            val editor = prefs.edit()
                .putString(KEY_HISTORY, encodeHistory(updated))
            if (prefs.getString(KEY_ID, null) == id) {
                editor.putString(KEY_LABEL, clean)
            }
            editor.apply()
        }

    /** Hapus satu entri dari riwayat; kalau itu lokasi aktif, lokasi aktif ikut dikosongkan. */
    suspend fun removeFromHistory(context: Context, id: String) =
        withContext(Dispatchers.IO) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val remaining = decodeHistory(prefs.getString(KEY_HISTORY, null))
                .filterNot { it.id == id }
            val editor = prefs.edit()
                .putString(KEY_HISTORY, encodeHistory(remaining))
            if (prefs.getString(KEY_ID, null) == id) {
                editor.remove(KEY_LAT).remove(KEY_LON).remove(KEY_RADIUS)
                    .remove(KEY_LABEL).remove(KEY_ID).remove(KEY_SAVED_AT)
            }
            editor.apply()
        }

    /** Hapus lokasi aktif saja (riwayat tetap). */
    suspend fun clearActive(context: Context) = withContext(Dispatchers.IO) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_LAT).remove(KEY_LON).remove(KEY_RADIUS)
            .remove(KEY_LABEL).remove(KEY_ID).remove(KEY_SAVED_AT)
            .apply()
    }

    private fun samePoint(a: CustomLocation, b: CustomLocation): Boolean =
        kotlin.math.abs(a.latitude - b.latitude) <= DEDUPE_EPSILON &&
            kotlin.math.abs(a.longitude - b.longitude) <= DEDUPE_EPSILON

    private fun encodeHistory(list: List<CustomLocation>): String {
        val array = JSONArray()
        list.forEach { entry ->
            array.put(
                org.json.JSONObject()
                    .put("id", entry.id)
                    .put("lat", entry.latitude)
                    .put("lon", entry.longitude)
                    .put("radius", entry.radiusKm)
                    .put("label", entry.label ?: "")
                    .put("savedAt", entry.savedAtMillis)
            )
        }
        return array.toString()
    }

    private fun decodeHistory(raw: String?): List<CustomLocation> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { i ->
                val obj = array.optJSONObject(i) ?: return@mapNotNull null
                val lat = obj.optDouble("lat", Double.NaN)
                val lon = obj.optDouble("lon", Double.NaN)
                val radius = obj.optDouble("radius", Double.NaN)
                if (!isValidLat(lat) || !isValidLon(lon) || radius.isNaN() || radius <= 0.0) {
                    return@mapNotNull null
                }
                CustomLocation(
                    id = obj.optString("id"),
                    latitude = lat,
                    longitude = lon,
                    radiusKm = radius,
                    label = obj.optString("label").takeIf { it.isNotEmpty() },
                    savedAtMillis = obj.optLong("savedAt", 0L)
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun isValidLat(value: Double): Boolean = value in -90.0..90.0
    private fun isValidLon(value: Double): Boolean = value in -180.0..180.0
}
