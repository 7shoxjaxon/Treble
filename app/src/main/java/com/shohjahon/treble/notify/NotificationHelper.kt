package com.shohjahon.treble.notify

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.shohjahon.treble.MainActivity
import com.shohjahon.treble.R
import com.shohjahon.treble.data.models.AppNotification

/** Telefonning bildirishnoma panelida xabar ko'rsatadi. */
object NotificationHelper {
    const val CHANNEL_ID = "game_reminders"

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "O'yin eslatmalari",
            NotificationManager.IMPORTANCE_HIGH
        ).apply { description = "O'yin boshlanishidan oldingi eslatmalar va yangi biriktirilgan o'yinlar" }
        val system = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        system.createNotificationChannel(channel)
    }

    @SuppressLint("MissingPermission")
    fun show(context: Context, n: AppNotification, tab: Int? = null) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        ensureChannel(context)

        val open = PendingIntent.getActivity(
            context,
            n.id.hashCode(),
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                if (tab != null) putExtra("tab", tab) // bosilganda shu bo'lim ochiladi
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(n.title)
            .setContentText(n.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(n.body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()

        try {
            manager.notify(n.id.hashCode(), notification)
        } catch (e: SecurityException) {
            // Ruxsat berilmagan bo'lsa, ilova ichidagi ro'yxatda baribir ko'rinadi
        }
    }
}
