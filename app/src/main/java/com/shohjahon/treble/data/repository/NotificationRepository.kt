package com.shohjahon.treble.data.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateListOf
import com.shohjahon.treble.data.models.AppNotification
import com.shohjahon.treble.data.remote.ApiClient
import com.shohjahon.treble.notify.NotificationHelper
import org.json.JSONArray
import org.json.JSONObject

/** Ilova ichidagi bildirishnomalar ro'yxati (telefonda saqlanadi, hisobga bog'langan). */
object NotificationRepository {
    private const val PREFS = "treble_notifications"
    private const val KEY_ITEMS = "items"
    private const val KEY_OWNER = "owner"
    private const val MAX_ITEMS = 100

    val items = mutableStateListOf<AppNotification>()
    val unreadCount: Int get() = items.count { !it.read }

    private lateinit var prefs: SharedPreferences
    private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        initialized = true
        val me = AuthRepository.currentUser?.id
        if (me == null || prefs.getString(KEY_OWNER, null) != me) {
            prefs.edit().clear().apply()
            return
        }
        try {
            val arr = JSONArray(prefs.getString(KEY_ITEMS, "[]"))
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                items.add(
                    AppNotification(
                        id = o.getString("id"),
                        kind = o.optString("kind", "REMINDER"),
                        gameId = o.optString("gameId", ""),
                        title = o.optString("title", ""),
                        body = o.optString("body", ""),
                        createdAt = o.optLong("createdAt", 0L),
                        read = o.optBoolean("read", false)
                    )
                )
            }
        } catch (e: Exception) {
            items.clear()
        }
    }

    /** Yangi bildirishnoma qo'shadi. Allaqachon bo'lsa false qaytaradi. */
    @Synchronized
    fun add(n: AppNotification): Boolean {
        if (!initialized) return false
        if (items.any { it.id == n.id }) return false
        items.add(n)
        items.sortByDescending { it.createdAt }
        while (items.size > MAX_ITEMS) items.removeAt(items.size - 1)
        save()
        return true
    }

    @Synchronized
    fun markAllRead() {
        if (!initialized || items.none { !it.read }) return
        for (i in items.indices) items[i] = items[i].copy(read = true)
        save()
    }

    @Synchronized
    fun clearAll() {
        items.clear()
        if (initialized) save()
    }

    /** Chiqishda ro'yxatni tozalaydi. */
    fun clear() {
        items.clear()
        if (initialized) prefs.edit().clear().apply()
    }

    private fun save() {
        val arr = JSONArray()
        items.forEach {
            arr.put(
                JSONObject()
                    .put("id", it.id).put("kind", it.kind).put("gameId", it.gameId)
                    .put("title", it.title).put("body", it.body)
                    .put("createdAt", it.createdAt).put("read", it.read)
            )
        }
        prefs.edit()
            .putString(KEY_OWNER, AuthRepository.currentUser?.id)
            .putString(KEY_ITEMS, arr.toString())
            .apply()
    }

    /**
     * Serverdagi xabarlarni (masalan, "sizga o'yin biriktirildi") oladi.
     * [showSystem] = true bo'lsa (ilova yopiq paytda), yangilari telefon panelida ham ko'rsatiladi.
     */
    suspend fun sync(context: Context?, showSystem: Boolean) {
        if (!initialized || AuthRepository.currentUser == null || ApiClient.token == null) return
        val r = ApiClient.request("GET", "/notifications")
        val arr = r.json?.optJSONArray("notifications") ?: return
        if (!r.ok) return
        val fresh = ArrayList<AppNotification>()
        for (i in arr.length() - 1 downTo 0) {
            val o = arr.optJSONObject(i) ?: continue
            val n = AppNotification(
                id = "srv:" + o.optString("id"),
                kind = o.optString("kind", "ASSIGNED"),
                gameId = o.optString("gameId", ""),
                title = o.optString("title", ""),
                body = o.optString("body", ""),
                createdAt = o.optLong("createdAt", 0L)
            )
            if (add(n)) fresh.add(n)
        }
        if (showSystem && context != null) {
            val recent = System.currentTimeMillis() - 6 * 3_600_000L
            fresh.filter { it.createdAt >= recent }.forEach { NotificationHelper.show(context, it) }
        }
    }
}
