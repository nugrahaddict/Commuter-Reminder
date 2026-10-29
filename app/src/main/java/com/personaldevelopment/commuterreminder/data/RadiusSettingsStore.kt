package com.personaldevelopment.commuterreminder.data

import android.content.Context
import com.personaldevelopment.commuterreminder.domain.ReminderSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

/**
 * Persistensi radius aktivasi reminder (km) ke SharedPreferences.
 *
 * [ReminderSettings] tetap satu-satunya sumber nilai saat runtime (object murni
 * tanpa Android agar mudah di-unit-test); store ini yang mengisi nilainya saat
 * aplikasi dimulai dan menyimpannya tiap user mengubah radius.
 */
object RadiusSettingsStore {

    private const val PREFS_NAME = "reminder_settings_prefs"
    private const val KEY_RADIUS_KM = "activation_radius_km"

    /** Baca radius tersimpan; null kalau belum pernah disimpan atau nilainya rusak. */
    fun loadRadiusKm(context: Context): Double? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.contains(KEY_RADIUS_KM)) return null
        val value = java.lang.Double.longBitsToDouble(prefs.getLong(KEY_RADIUS_KM, 0L))
        return if (isValid(value)) value else null
    }

    /** Terapkan radius tersimpan ke [ReminderSettings] (fallback ke default). */
    fun applySaved(context: Context) {
        ReminderSettings.ACTIVATION_RADIUS_KM = loadRadiusKm(context)
            ?: ReminderSettings.DEFAULT_ACTIVATION_RADIUS_KM
    }

    suspend fun save(context: Context, radiusKm: Double) = withContext(Dispatchers.IO) {
        val clamped = clamp(radiusKm)
        if (!isValid(clamped)) return@withContext
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_RADIUS_KM, java.lang.Double.doubleToRawLongBits(clamped))
            .apply()
        ReminderSettings.ACTIVATION_RADIUS_KM = clamped
    }

    private fun clamp(radiusKm: Double): Double = min(
        ReminderSettings.MAX_ACTIVATION_RADIUS_KM,
        max(ReminderSettings.MIN_ACTIVATION_RADIUS_KM, radiusKm)
    )

    private fun isValid(value: Double): Boolean =
        !value.isNaN() && value in ReminderSettings.MIN_ACTIVATION_RADIUS_KM..
                ReminderSettings.MAX_ACTIVATION_RADIUS_KM
}