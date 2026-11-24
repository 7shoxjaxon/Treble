package com.shohjahon.treble.notify

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.shohjahon.treble.data.models.AppNotification
import com.shohjahon.treble.data.repository.AuthRepository
import com.shohjahon.treble.data.repository.NotificationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Serverdan kelgan push xabarlarni qabul qiladi (ilova yopiq bo'lsa ham ishlaydi).
 * - type = chat: yangi chat xabari
 * - boshqa: o'yin biriktirildi / vaqti o'zgardi / bekor qilindi (ilova ichidagi ro'yxatga ham yoziladi)
 */
class TrebleMessagingService : FirebaseMessagingService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        AuthRepository.init(applicationContext)
        PushRepository.init(applicationContext)
        scope.launch { PushRepository.onNewToken(token) }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        if (data.isEmpty()) return

        AuthRepository.init(applicationContext)
        NotificationRepository.init(applicationContext)
        if (AuthRepository.currentUser == null) return

        val title = data["title"] ?: return
        val body = data["body"] ?: ""
        val tab = data["tab"]?.toIntOrNull()
        val now = System.currentTimeMillis()

        if (data["type"] == "chat") {
            // Ilova ochiq bo'lsa chat o'zi yangilanadi, panelga chiqarish shart emas
            if (AppState.foreground) return
            NotificationHelper.show(
                applicationContext,
                AppNotification(
                    id = "chat:" + (data["room"] ?: "group"), // bitta suhbatdan kelganlar bir-birini almashtiradi
                    kind = "CHAT",
                    title = title,
                    body = body,
                    createdAt = now
                ),
                tab
            )
            return
        }

        val n = AppNotification(
            id = data["id"] ?: "push:$now",
            kind = data["kind"] ?: "INFO",
            gameId = data["gameId"] ?: "",
            title = title,
            body = body,
            createdAt = now
        )
        // Ro'yxatga yoziladi; ilova ochiq bo'lmasa panelga ham chiqadi (takrorlanmaydi)
        if (NotificationRepository.add(n) && !AppState.foreground) {
            NotificationHelper.show(applicationContext, n, tab)
        }
    }
}
