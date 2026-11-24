package com.shohjahon.treble.data.repository

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.shohjahon.treble.data.models.ChatConversation
import com.shohjahon.treble.data.models.ChatMessage
import com.shohjahon.treble.data.models.ChatPeer
import com.shohjahon.treble.data.remote.ApiClient
import org.json.JSONArray
import org.json.JSONObject

/** Chat bilan ishlash: qo'shilish holati, umumiy guruh va shaxsiy xabarlar. */
object ChatRepository {
    /** unknown (yuklanmoqda) | none (hali javob bermagan) | joined | declined */
    var status by mutableStateOf("unknown")
        private set
    var groupUnread by mutableStateOf(0)
        private set
    var dmUnread by mutableStateOf(0)
        private set
    val totalUnread: Int get() = groupUnread + dmUnread

    data class Result<T>(val data: T? = null, val error: String? = null) {
        val ok: Boolean get() = error == null && data != null
    }

    data class DmData(val peer: ChatPeer, val peerInChat: Boolean, val messages: List<ChatMessage>)

    suspend fun refreshStatus() {
        if (ApiClient.token == null) return
        val r = ApiClient.request("GET", "/chat/status")
        val j = r.json
        if (!r.ok || j == null) return
        status = j.optString("status", "none")
        groupUnread = j.optInt("groupUnread", 0)
        dmUnread = j.optInt("dmUnread", 0)
    }

    /** Chatga qo'shilish (true) yoki rad etish/chiqish (false). Xato bo'lsa matnini qaytaradi. */
    suspend fun setJoined(join: Boolean): String? {
        val r = ApiClient.request("POST", if (join) "/chat/join" else "/chat/decline", JSONObject())
        if (!r.ok) return r.message
        status = if (join) "joined" else "declined"
        if (!join) {
            groupUnread = 0
            dmUnread = 0
        }
        return null
    }

    suspend fun loadGroup(after: Long): Result<List<ChatMessage>> {
        val r = ApiClient.request("GET", "/chat/group" + if (after > 0) "?after=$after" else "")
        val arr = r.json?.optJSONArray("messages")
        if (!r.ok || arr == null) return Result(error = r.message)
        groupUnread = 0
        return Result(parseMessages(arr))
    }

    suspend fun sendGroup(text: String): Result<ChatMessage> = send("/chat/group", text)

    suspend fun conversations(): Result<List<ChatConversation>> {
        val r = ApiClient.request("GET", "/chat/dms")
        val arr = r.json?.optJSONArray("conversations")
        if (!r.ok || arr == null) return Result(error = r.message)
        val list = ArrayList<ChatConversation>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val peer = parsePeer(o.optJSONObject("peer") ?: continue)
            list.add(
                ChatConversation(
                    peer = peer,
                    last = o.optJSONObject("last")?.let { parseMessage(it) },
                    unread = o.optInt("unread", 0)
                )
            )
        }
        return Result(list)
    }

    suspend fun members(): Result<List<ChatPeer>> {
        val r = ApiClient.request("GET", "/chat/members")
        val arr = r.json?.optJSONArray("members")
        if (!r.ok || arr == null) return Result(error = r.message)
        val list = ArrayList<ChatPeer>()
        for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { list.add(parsePeer(it)) }
        return Result(list)
    }

    suspend fun loadDm(peerId: String, after: Long): Result<DmData> {
        val r = ApiClient.request("GET", "/chat/dm/$peerId" + if (after > 0) "?after=$after" else "")
        val j = r.json
        val arr = j?.optJSONArray("messages")
        val peer = j?.optJSONObject("peer")
        if (!r.ok || j == null || arr == null || peer == null) return Result(error = r.message)
        return Result(DmData(parsePeer(peer), j.optBoolean("peerInChat", true), parseMessages(arr)))
    }

    suspend fun sendDm(peerId: String, text: String): Result<ChatMessage> = send("/chat/dm/$peerId", text)

    /** Xabarni o'chiradi. Xato bo'lsa matnini qaytaradi. */
    suspend fun deleteMessage(id: String): String? {
        val r = ApiClient.request("DELETE", "/chat/messages/$id")
        return if (r.ok) null else r.message
    }

    /** Xabar ustidan shikoyat yuboradi. Xato bo'lsa matnini qaytaradi. */
    suspend fun reportMessage(id: String, reason: String = ""): String? {
        val r = ApiClient.request("POST", "/chat/messages/$id/report", JSONObject().put("reason", reason))
        return if (r.ok) null else r.message
    }

    /** Foydalanuvchini bloklaydi: uning xabarlari ko'rinmaydi, u ham sizga yoza olmaydi. */
    suspend fun blockUser(userId: String): String? {
        val r = ApiClient.request("POST", "/chat/block/$userId", JSONObject())
        return if (r.ok) null else r.message
    }

    suspend fun unblockUser(userId: String): String? {
        val r = ApiClient.request("DELETE", "/chat/block/$userId")
        return if (r.ok) null else r.message
    }

    /** Men bloklagan foydalanuvchilar. */
    suspend fun blockedUsers(): Result<List<ChatPeer>> {
        val r = ApiClient.request("GET", "/chat/blocks")
        val arr = r.json?.optJSONArray("blocked")
        if (!r.ok || arr == null) return Result(error = r.message)
        val list = ArrayList<ChatPeer>()
        for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { list.add(parsePeer(it)) }
        return Result(list)
    }

    fun clear() {
        status = "unknown"
        groupUnread = 0
        dmUnread = 0
    }

    private suspend fun send(path: String, text: String): Result<ChatMessage> {
        val r = ApiClient.request("POST", path, JSONObject().put("text", text))
        val m = r.json?.optJSONObject("message")
        if (!r.ok || m == null) return Result(error = r.message)
        return Result(parseMessage(m))
    }

    private fun parseMessages(arr: JSONArray): List<ChatMessage> {
        val list = ArrayList<ChatMessage>()
        for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { list.add(parseMessage(it)) }
        return list
    }

    private fun parseMessage(o: JSONObject) = ChatMessage(
        id = o.optString("id"),
        senderId = o.optString("senderId"),
        senderName = o.optString("senderName", ""),
        senderAvatar = o.optString("senderAvatar", ""),
        senderRole = o.optString("senderRole", "USER"),
        text = o.optString("text", ""),
        createdAt = o.optLong("createdAt", 0L)
    )

    private fun parsePeer(o: JSONObject) = ChatPeer(
        id = o.optString("id"),
        name = o.optString("name", ""),
        avatar = o.optString("avatar", ""),
        role = o.optString("role", "USER")
    )
}
