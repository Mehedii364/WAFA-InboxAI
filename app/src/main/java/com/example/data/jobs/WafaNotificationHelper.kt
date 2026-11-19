package com.example.data.jobs

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.R

object WafaNotificationHelper {

    const val CHANNEL_FOLLOWUPS = "wafa_followups"
    const val CHANNEL_SECURITY = "wafa_security"

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val followUpChannel = NotificationChannel(
                CHANNEL_FOLLOWUPS,
                context.getString(R.string.notification_channel_followups_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.notification_channel_followups_desc)
            }
            val securityChannel = NotificationChannel(
                CHANNEL_SECURITY,
                context.getString(R.string.notification_channel_security_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.notification_channel_security_desc)
            }
            nm.createNotificationChannel(followUpChannel)
            nm.createNotificationChannel(securityChannel)
        }
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else true
    }

    fun postNotification(
        context: Context,
        id: Int,
        channelId: String,
        title: String,
        message: String
    ) {
        if (!hasNotificationPermission(context)) return
        ensureChannels(context)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setAutoCancel(true)
            .build()
        nm.notify(id, notification)
    }
}
