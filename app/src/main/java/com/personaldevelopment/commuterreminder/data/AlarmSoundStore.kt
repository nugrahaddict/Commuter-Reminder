package com.personaldevelopment.commuterreminder.data

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Penyimpanan pilihan ringtone alarm user (dipilih lewat "choose audio").
 *
 * Audio pilihan user disalin ke koleksi ALAMM MediaStore (API 29+) supaya sistem
 * bisa memutarnya sebagai sound notifikasi; di bawah API 29 URI dokumen asli
 * dipakai langsung dengan persistable permission.
 *
 * Setiap perubahan suara menaikkan versi channel karena NotificationChannel
 * tidak bisa mengubah sound setelah dibuat.
 */
object AlarmSoundStore {

    private const val PREFS_NAME = "alarm_sound_prefs"
    private const val KEY_SOUND_URI = "alarm_sound_uri"
    private const val KEY_CHANNEL_VERSION = "alarm_channel_version"

    fun soundUri(context: Context): Uri? {
        val raw = prefs(context).getString(KEY_SOUND_URI, null) ?: return null
        return runCatching { Uri.parse(raw) }.getOrNull()
    }

    suspend fun saveSound(context: Context, pickedUri: Uri) = withContext(Dispatchers.IO) {
        // amankan akses baca jangka panjang ke dokumen pilihan user
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                pickedUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        val previous = soundUri(context)
        val stored = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            copyToMediaStore(context, pickedUri) ?: pickedUri
        } else {
            pickedUri
        }
        prefs(context).edit()
            .putString(KEY_SOUND_URI, stored.toString())
            .putInt(KEY_CHANNEL_VERSION, channelVersion(context) + 1)
            .apply()
        deleteOwnedMedia(context, previous)
    }

    suspend fun clearSound(context: Context) = withContext(Dispatchers.IO) {
        val previous = soundUri(context)
        prefs(context).edit()
            .remove(KEY_SOUND_URI)
            .putInt(KEY_CHANNEL_VERSION, channelVersion(context) + 1)
            .apply()
        deleteOwnedMedia(context, previous)
    }

    /** Versi channel alarm aktif; naik tiap ringtone diganti. */
    fun channelVersion(context: Context): Int = prefs(context).getInt(KEY_CHANNEL_VERSION, 0)

    /** Salin audio pilihan user ke koleksi ALARM agar sistem bisa memutarnya sebagai alarm. */
    private fun copyToMediaStore(context: Context, source: Uri): Uri? = runCatching {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, buildMediaFileName(resolver, source))
            put(MediaStore.Audio.Media.IS_ALARM, 1)
            put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_ALARMS)
        }
        val target = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
            ?: return null
        val output = resolver.openOutputStream(target) ?: return null
        output.use { out ->
            resolver.openInputStream(source)?.use { input -> input.copyTo(out) } ?: return null
        }
        target
    }.getOrNull()

    private fun buildMediaFileName(resolver: ContentResolver, source: Uri): String {
        val original = queryDisplayName(resolver, source)?.takeIf { it.isNotBlank() }
            ?: "alarm_audio"
        val safe = original.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        return if (safe.contains('.')) safe else "$safe${guessExtension(resolver.getType(source))}"
    }

    private fun queryDisplayName(resolver: ContentResolver, uri: Uri): String? =
        runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        }.getOrNull()

    private fun guessExtension(mime: String?): String = when (mime) {
        "audio/ogg" -> ".ogg"
        "audio/wav", "audio/x-wav" -> ".wav"
        "audio/aac" -> ".aac"
        "audio/flac" -> ".flac"
        "audio/mp4" -> ".m4a"
        else -> ".mp3"
    }

    /** Hapus salinan lama di MediaStore yang dibuat aplikasi ini sendiri. */
    private fun deleteOwnedMedia(context: Context, uri: Uri?) {
        val target = uri ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            target.toString().startsWith("content://media/")
        ) {
            runCatching { context.contentResolver.delete(target, null, null) }
        }
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
