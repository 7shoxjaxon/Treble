package com.shohjahon.treble.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.shohjahon.treble.data.models.Game
import com.shohjahon.treble.data.models.User

/**
 * O'yin boshlanishidan 2 soat, 1 soat va 30 daqiqa oldin eslatma.
 * - Futbolchiga: o'zi band qilgan o'yinlar uchun.
 * - Ishchi adminga: faqat o'ziga biriktirilgan o'yinlar uchun.
 * Eslatmalar telefonning o'zida rejalashtiriladi (internet shart emas).
 */
object ReminderScheduler {
    /** Daqiqalarda: o'yin boshlanishidan qancha oldin eslatish. */
    val LEADS_MIN = intArrayOf(120, 60, 30)

    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun leadLabel(lead: Int): String = when (lead) {
        120 -> "2 soat"
        60 -> "1 soat"
        else -> "$lead daqiqa"
    }

    private fun pendingIntent(context: Context, gameId: String, lead: Int, info: Intent? = null): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = "com.shohjahon.treble.REMIND"
            data = Uri.parse("treble://remind/$gameId/$lead")
            info?.extras?.let { putExtras(it) }
        }
        return PendingIntent.getBroadcast(
            context,
            "$gameId:$lead".hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** Foydalanuvchiga tegishli barcha o'yinlar uchun eslatmalarni qayta o'rnatadi. */
    fun syncAll(games: List<Game>, me: User) {
        games.forEach { schedule(it, me) }
    }

    fun schedule(game: Game, me: User) {
        val context = appContext ?: return
        val asPlayer = game.hasJoined(me.id)
        val asStaff = game.staff.any { it.id == me.id }
        if ((!asPlayer && !asStaff) || game.isCancelled || game.isOver()) {
            cancel(game.id)
            return
        }

        val alarm = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val now = System.currentTimeMillis()
        for (lead in LEADS_MIN) {
            val triggerAt = game.startMillis - lead * 60_000L
            if (triggerAt <= now) {
                cancelOne(context, alarm, game.id, lead)
                continue
            }
            val title = if (asPlayer) "⚽ O'yin yaqinlashmoqda" else "🛠 Biriktirilgan o'yin yaqinlashmoqda"
            val text = "${game.venueName} · ${game.startTime} da boshlanadi (${leadLabel(lead)}dan keyin)"
            val info = Intent().apply {
                putExtra("gameId", game.id)
                putExtra("lead", lead)
                putExtra("userId", me.id)
                putExtra("title", title)
                putExtra("text", text)
            }
            val pi = pendingIntent(context, game.id, lead, info)
            try {
                // Aniq budilnik ruxsati (SCHEDULE_EXACT_ALARM) Google Play'da cheklangan, shuning uchun
                // eslatma o'sha vaqt atrofida (bir necha daqiqa aniqlikda) keladi.
                alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            } catch (e: Exception) {
                // Eslatma o'rnatilmasa ham ilova ishlashda davom etadi
            }
        }
    }

    private fun cancelOne(context: Context, alarm: AlarmManager, gameId: String, lead: Int) {
        try {
            alarm.cancel(pendingIntent(context, gameId, lead))
        } catch (e: Exception) {
            // e'tibor bermaymiz
        }
    }

    fun cancel(gameId: String) {
        val context = appContext ?: return
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        LEADS_MIN.forEach { cancelOne(context, alarm, gameId, it) }
    }
}
