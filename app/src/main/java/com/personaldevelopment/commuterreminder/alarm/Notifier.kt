package com.personaldevelopment.commuterreminder.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import androidx.core.app.NotificationCompat
import com.personaldevelopment.commuterreminder.MainActivity
import com.personaldevelopment.commuterreminder.R

object Notifier {

    const val CHANNEL_TRACKING = "tracking"
    const val CHANNEL_ALARM = "alarm"
    const val NOTIFICATION_ID_TRACKING = 1
    const val NOTIFICATION_ID_ALARM = 2

    fun createChannels(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val trackingChannel = NotificationChannel(
            CHANNEL_TRACKING,
            context.getString(R.string.channel_tracking_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.channel_tracking_desc)
            setSound(null, null)
        }

        val alarmSound: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val alarmChannel = NotificationChannel(
            CHANNEL_ALARM,
            context.getString(R.string.channel_alarm_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.channel_alarm_desc)
            setSound(
                alarmSound,
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 800, 400, 800, 400, 800)
            setBypassDnd(true)
        }

        manager.createNotificationChannel(trackingChannel)
        manager.createNotificationChannel(alarmChannel)
    }

    fun showTrackingNotification(context: Context, text: String) {
        notifySafely(context, NOTIFICATION_ID_TRACKING, buildTrackingNotification(context, text))
    }

    fun buildTrackingNotification(context: Context, text: String): android.app.Notification =
        NotificationCompat.Builder(context, CHANNEL_TRACKING)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notif_tracking_title))
            .setContentText(text)
            .setOngoing(true)
            .setSilent(true)
            .build()

    fun showAlarmNotification(context: Context, title: String, text: String) {
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ALARM)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(contentIntent, true)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()
        notifySafely(context, NOTIFICATION_ID_ALARM, notification)
    }

    fun cancelAlarm(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(NOTIFICATION_ID_ALARM)
    }

    private fun notifySafely(context: Context, id: Int, notification: android.app.Notification) {
        try {
            val manager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(id, notification)
        } catch (_: SecurityException) {
        }
    }
}
