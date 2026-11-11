package com.personaldevelopment.commuterreminder.alarm

import android.app.Activity
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.personaldevelopment.commuterreminder.R
import com.personaldevelopment.commuterreminder.data.AlarmSoundStore
import com.personaldevelopment.commuterreminder.tracking.TrackingService
import kotlinx.coroutines.delay

/**
 * Layar alarm full-screen yang tampil di atas lockscreen (seperti alarm
 * bawaan device): layar menyala sendiri, suara + getar berulang sampai
 * user menekan tombol. Dibuka lewat full-screen intent dari [Notifier].
 */
class AlarmActivity : ComponentActivity() {

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null

    private var titleText by mutableStateOf("")
    private var bodyText by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        // tampil di atas lockscreen + nyalakan layar (fallback API 26)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        super.onCreate(savedInstanceState)

        titleText = intent.getStringExtra(EXTRA_TITLE)
            ?: getString(R.string.notif_alarm_arriving)
        bodyText = intent.getStringExtra(EXTRA_TEXT).orEmpty()

        startAlarmSoundAndVibration()

        setContent {
            AlarmScreen(
                title = titleText,
                body = bodyText,
                onSnooze = { snoozeAlarm() },
                onFinish = { finishAndStopAlarm() }
            )
        }
    }

    private fun startAlarmSoundAndVibration() {
        val sound: Uri = AlarmSoundStore.soundUri(this)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        mediaPlayer = runCatching {
            MediaPlayer().apply {
                setDataSource(this@AlarmActivity, sound)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                isLooping = true
                prepare()
                start()
            }
        }.getOrNull()

        vibrator = runCatching {
            val v = ContextCompat.getSystemService(this@AlarmActivity, Vibrator::class.java)
            v?.vibrate(
                VibrationEffect.createWaveform(longArrayOf(0, 800, 400), 0)
            )
            v
        }.getOrNull()
    }

    private fun stopAlarmSoundAndVibration() {
        runCatching {
            mediaPlayer?.takeIf { it.isPlaying }?.stop()
        }
        mediaPlayer?.release()
        mediaPlayer = null
        vibrator?.cancel()
        vibrator = null
    }

    private fun finishAndStopAlarm() {
        stopAlarmSoundAndVibration()
        // perjalanan selesai: hentikan tracking sepenuhnya
        TrackingService.stop(this)
        finish()
    }

    /** Alarm dipatikan tapi perjalanan berlanjut: tracking tetap jalan. */
    private fun snoozeAlarm() {
        stopAlarmSoundAndVibration()
        TrackingService.snooze(this)
        finish()
    }

    override fun onDestroy() {
        stopAlarmSoundAndVibration()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_TITLE = "alarm_title"
        const val EXTRA_TEXT = "alarm_text"
    }
}

@Composable
private fun AlarmScreen(
    title: String,
    body: String,
    onSnooze: () -> Unit,
    onFinish: () -> Unit
) {
    // denyut latar agar mata user tertarik saat layar menyala
    var pulse by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(600)
            pulse = !pulse
        }
    }

    val bg = if (pulse) Color(0xFFB71C1C) else Color(0xFFC62828)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bg),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Notifications,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(96.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = title,
                color = Color.White,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            if (body.isNotBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = body,
                    color = Color.White.copy(alpha = 0.9f),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )
            }
            Spacer(modifier = Modifier.height(48.dp))
            Button(
                onClick = onSnooze,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color(0xFFB71C1C)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text(
                    text = stringResource(R.string.alarm_screen_snooze),
                    style = MaterialTheme.typography.titleMedium
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = onFinish,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text(text = stringResource(R.string.alarm_screen_stop_tracking))
            }
        }
    }
}
