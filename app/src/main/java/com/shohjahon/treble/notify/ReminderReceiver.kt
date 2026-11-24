package com.shohjahon.treble.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.shohjahon.treble.data.models.AppNotification
import com.shohjahon.treble.data.repository.AuthRepository
import com.shohjahon.treble.data.repository.GameRepository
import com.shohjahon.treble.data.repository.NotificationRepository

/** Rejalashtirilgan vaqt kelganda eslatmani ko'rsatadi va ilova ichidagi ro'yxatga yozadi. */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val gameId = intent.getStringExtra("gameId") ?: return
        val userId = intent.getStringExtra("userId") ?: return
        val title = intent.getStringExtra("title") ?: return
        val text = intent.getStringExtra("text") ?: return
        val lead = intent.getIntExtra("lead", 0)

        // Ilova yopiq bo'lsa ham ma'lumotlar telefondagi nusxadan o'qiladi
        AuthRepository.init(context)
        GameRepository.init(context)
        NotificationRepository.init(context)

        val me = AuthRepository.currentUser ?: return
        if (me.id != userId) return                       // boshqa hisob uchun rejalashtirilgan
        val game = GameRepository.gameById(gameId) ?: return
        if (game.isCancelled || game.isOver()) return
        if (!game.hasJoined(me.id) && game.staff.none { it.id == me.id }) return

        val now = System.currentTimeMillis()
        if (now > game.startMillis) return                // o'yin boshlanib bo'lgan
        if (now < game.startMillis - lead * 60_000L - 2 * 60_000L) return // o'yin vaqti keyinga surilgan

        val n = AppNotification(
            id = "rem:$gameId:$lead",
            kind = "REMINDER",
            gameId = gameId,
            title = title,
            body = text,
            createdAt = now
        )
        if (NotificationRepository.add(n)) NotificationHelper.show(context, n)
    }
}
