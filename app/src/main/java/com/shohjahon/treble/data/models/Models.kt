package com.shohjahon.treble.data.models

import com.shohjahon.treble.data.util.DateUtils

enum class UserRole {
    USER,   // Futbolchi
    ADMIN,  // Bosh admin (hamma huquq)
    STAFF   // Ishchi admin (faqat davomat va "o'yin yakunlandi")
}

/** Ro'yxatdan o'tgan foydalanuvchi. Parol faqat serverda (hash ko'rinishida) saqlanadi. */
data class User(
    val id: String,
    val name: String,
    val phone: String,
    val login: String,
    val role: UserRole = UserRole.USER,
    val joinedAt: Long = 0L,
    /** Profil rasmi: serverdagi "/uploads/..." havolasi (bo'sh bo'lsa rasm qo'yilmagan). */
    val avatar: String = ""
) {
    /** Bosh admin. */
    val isBoss: Boolean get() = role == UserRole.ADMIN

    /** Ishchi admin. */
    val isStaff: Boolean get() = role == UserRole.STAFF

    /** Bosh admin yoki ishchi admin. */
    val isManager: Boolean get() = role != UserRole.USER
}

/** O'yinga band qilgan futbolchi. [id] = foydalanuvchi id si. */
data class Player(
    val id: String,
    val name: String,
    val phone: String,
    val isAttended: Boolean? = null,
    val paid: Boolean = false,
    val payMethod: String = "CASH",   // CASH | CARD | COUPON
    val bookedAt: Long = 0L,
    val avatar: String = ""
) {
    /** Ilovada akkaunti yo'q, admin qo'lda qo'shgan o'yinchi. */
    val isGuest: Boolean get() = id.startsWith("guest-")
}

data class Comment(
    val id: String,
    val userId: String,
    val userName: String,
    val text: String,
    val createdAt: Long,
    val avatar: String = ""
)

/** O'yinga biriktirilgan ishchi admin. */
data class StaffRef(val id: String, val name: String)

enum class GameStatus { OPEN, FULL, FINISHED, CANCELLED }

data class Game(
    val id: String,
    val venueName: String,
    val address: String = "",
    val mapLink: String = "",
    /** Havola (https://...) yoki telefon ichidagi fayl yo'li (/data/...). */
    val images: List<String> = emptyList(),
    val dateMillis: Long,              // o'yin kuni (kun boshi)
    val startTime: String,             // "19:00"
    val durationMin: Int = 120,
    val format: String = "5v5",
    val maxPlayers: Int = 10,
    val price: Int = 0,                // bir o'yinchi uchun, so'm
    val description: String = "",
    val rules: String = "",
    val payInfo: String = "",          // kartaga o'tkazma uchun ma'lumot (ixtiyoriy)
    val freeCancelHours: Int = 24,     // shu soatgacha bandni bekor qilish mumkin
    val players: List<Player> = emptyList(),
    val comments: List<Comment> = emptyList(),
    val cancelledBy: List<String> = emptyList(),
    /** Shu o'yinda yoqlama qiladigan ishchi adminlar (faqat adminlarga keladi). */
    val staff: List<StaffRef> = emptyList(),
    val isFinished: Boolean = false,
    val isCancelled: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
) {
    val startMillis: Long get() = DateUtils.timeWithDate(dateMillis, startTime)
    val endMillis: Long get() = startMillis + durationMin * 60_000L
    val spotsLeft: Int get() = (maxPlayers - players.size).coerceAtLeast(0)

    fun isOver(now: Long = System.currentTimeMillis()): Boolean = isFinished || now > endMillis

    fun status(now: Long = System.currentTimeMillis()): GameStatus = when {
        isCancelled -> GameStatus.CANCELLED
        isOver(now) -> GameStatus.FINISHED
        players.size >= maxPlayers -> GameStatus.FULL
        else -> GameStatus.OPEN
    }

    /** "19:00–21:00" */
    fun timeRange(): String {
        val start = DateUtils.parseMinutes(startTime) ?: 0
        return "$startTime–${DateUtils.formatMinutes(start + durationMin)}"
    }

    fun hasJoined(userId: String): Boolean = players.any { it.id == userId }
}

/** Reyting uchun statistika. */
data class PlayerStat(
    val id: String,
    val name: String,
    val phone: String,
    val signedUp: Int,   // nechta o'yinga band qilgan
    val played: Int,     // nechta o'yin o'tgan va kelmagan deb belgilanmagan
    val missed: Int      // nechta o'yinga kelmagan
)

/** O'yinlar asosida hosil qilingan "joy" (stadion). */
data class Venue(
    val name: String,
    val address: String,
    val mapLink: String,
    val images: List<String>,
    val activeGames: Int
)

data class Coupon(
    val id: String,
    val userId: String,
    val monthKey: String,
    val usedGameId: String? = null
)

sealed class AuthResult {
    object Success : AuthResult()
    data class Error(val message: String) : AuthResult()
}

/** Ilova ichidagi bildirishnoma (eslatma yoki "sizga o'yin biriktirildi" xabari). */
data class AppNotification(
    val id: String,
    val kind: String,          // REMINDER | ASSIGNED
    val gameId: String = "",
    val title: String,
    val body: String,
    val createdAt: Long,
    val read: Boolean = false
)

// ---- Chat ----

/** Chatdagi bitta xabar. [senderRole]: USER | STAFF | ADMIN. */
data class ChatMessage(
    val id: String,
    val senderId: String,
    val senderName: String,
    val senderAvatar: String = "",
    val senderRole: String = "USER",
    val text: String,
    val createdAt: Long
)

/** Chat a'zosi (shaxsiy xabar yoziladigan odam). */
data class ChatPeer(
    val id: String,
    val name: String,
    val avatar: String = "",
    val role: String = "USER"
)

/** Shaxsiy chatlar ro'yxatidagi bitta suhbat. */
data class ChatConversation(
    val peer: ChatPeer,
    val last: ChatMessage?,
    val unread: Int
)
