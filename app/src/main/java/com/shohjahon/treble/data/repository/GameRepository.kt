package com.shohjahon.treble.data.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateListOf
import com.shohjahon.treble.data.models.Comment
import com.shohjahon.treble.data.models.Game
import com.shohjahon.treble.data.models.Player
import com.shohjahon.treble.data.models.PlayerStat
import com.shohjahon.treble.data.models.StaffRef
import com.shohjahon.treble.data.models.Venue
import com.shohjahon.treble.data.remote.ApiClient
import com.shohjahon.treble.data.util.DateUtils
import com.shohjahon.treble.notify.ReminderScheduler
import org.json.JSONArray
import org.json.JSONObject

/**
 * O'yinlar ombori. Haqiqat manbai: server. Bu yerda uning nusxasi turadi (internet bo'lmasa ham oxirgi holat ko'rinadi).
 * Har qanday o'zgartirish avval serverga yuboriladi, javob kelgach ro'yxat yangilanadi.
 */
object GameRepository {
    private const val PREFS = "treble_games_cache"
    private const val KEY_GAMES = "games"

    private lateinit var prefs: SharedPreferences
    private var initialized = false

    val games = mutableStateListOf<Game>()

    fun init(context: Context) {
        if (initialized) return
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        loadCache()
        initialized = true
    }

    /** Chiqishda nusxani tozalaydi (telefon raqamlari qolib ketmasligi uchun). */
    fun clear() {
        games.clear()
        if (initialized) prefs.edit().clear().apply()
    }

    fun gameById(id: String): Game? = games.find { it.id == id }

    // ---------------------------------------------------------------- Serverdan olish

    /** Barcha o'yinlarni yangilaydi. Xato bo'lsa matn qaytaradi. */
    suspend fun refresh(): String? {
        val r = ApiClient.request("GET", "/games")
        val arr = r.json?.optJSONArray("games")
        if (!r.ok || arr == null) return r.message
        return try {
            val list = ArrayList<Game>()
            for (i in 0 until arr.length()) list.add(gameFromJson(arr.getJSONObject(i)))
            if (games.toList() != list) {
                games.clear()
                games.addAll(list)
            }
            prefs.edit().putString(KEY_GAMES, arr.toString()).apply()
            syncReminders()
            null
        } catch (e: Exception) {
            "Serverdan noto'g'ri javob keldi."
        }
    }

    // ---------------------------------------------------------------- Admin amallari

    suspend fun addGame(game: Game): String? = mutate("POST", "/games", requestJson(game))

    suspend fun editGame(game: Game): String? = mutate("PUT", "/games/${game.id}", requestJson(game))

    suspend fun deleteGame(gameId: String): String? {
        val r = ApiClient.request("DELETE", "/games/$gameId")
        if (!r.ok) return r.message
        games.removeAll { it.id == gameId }
        saveCache()
        ReminderScheduler.cancel(gameId)
        CouponRepository.refresh()
        return null
    }

    suspend fun setCancelled(gameId: String, cancelled: Boolean): String? =
        mutate("POST", "/games/$gameId/cancel", JSONObject().put("cancelled", cancelled))

    suspend fun setFinished(gameId: String, finished: Boolean): String? =
        mutate("POST", "/games/$gameId/finish", JSONObject().put("finished", finished))

    suspend fun removePlayer(gameId: String, playerId: String): String? =
        mutate("DELETE", "/games/$gameId/players/$playerId")

    suspend fun markAttendance(gameId: String, playerId: String, attended: Boolean?): String? {
        val body = JSONObject().put("attended", attended ?: JSONObject.NULL)
        return mutate("POST", "/games/$gameId/players/$playerId/attendance", body)
    }

    suspend fun setPaid(gameId: String, playerId: String, paid: Boolean): String? =
        mutate("POST", "/games/$gameId/players/$playerId/paid", JSONObject().put("paid", paid))

    /** Ilovada akkaunti yo'q o'yinchini admin qo'lda qo'shadi. */
    suspend fun addGuest(gameId: String, name: String, phone: String): String? =
        mutate("POST", "/games/$gameId/guests", JSONObject().put("name", name.trim()).put("phone", phone.trim()))

    suspend fun deleteComment(gameId: String, commentId: String): String? =
        mutate("DELETE", "/games/$gameId/comments/$commentId")

    // ---------------------------------------------------------------- Futbolchi amallari

    /** Band qilish. [method]: CASH, CARD yoki COUPON (kupon serverda avtomatik tanlanadi). */
    suspend fun book(gameId: String, method: String): String? {
        val error = mutate("POST", "/games/$gameId/book", JSONObject().put("method", method))
        if (error == null) CouponRepository.refresh()
        return error
    }

    suspend fun cancelBooking(gameId: String): String? {
        val error = mutate("DELETE", "/games/$gameId/book")
        if (error == null) {
            ReminderScheduler.cancel(gameId)
            CouponRepository.refresh()
        }
        return error
    }

    suspend fun addComment(gameId: String, text: String): String? {
        val clean = text.trim()
        if (clean.isEmpty()) return "Izoh bo'sh bo'lmasin."
        if (clean.length > 300) return "Izoh 300 belgidan oshmasin."
        return mutate("POST", "/games/$gameId/comments", JSONObject().put("text", clean))
    }

    // ---------------------------------------------------------------- So'rovlar va statistika

    /** Foydalanuvchining barcha band qilgan yoki bekor qilgan o'yinlari (yangisi birinchi). */
    fun gamesOf(userId: String): List<Game> =
        games.filter { it.hasJoined(userId) || it.cancelledBy.contains(userId) }
            .sortedByDescending { it.startMillis }

    /** O'yinlardan hosil qilingan joylar (stadionlar) ro'yxati. */
    fun venues(): List<Venue> {
        val now = System.currentTimeMillis()
        val grouped = games.groupBy { it.venueName.trim().lowercase() }
        return grouped.values.map { list ->
            val latest = list.maxByOrNull { it.createdAt } ?: list.first()
            val withImages = list.firstOrNull { it.images.isNotEmpty() }
            Venue(
                name = latest.venueName,
                address = latest.address,
                mapLink = latest.mapLink,
                images = withImages?.images ?: emptyList(),
                activeGames = list.count { !it.isOver(now) && !it.isCancelled }
            )
        }.sortedBy { it.name.lowercase() }
    }

    /** [monthKey] berilsa faqat shu oydagi o'yinlar hisobga olinadi. */
    fun playerStats(monthKey: String? = null): List<PlayerStat> {
        val now = System.currentTimeMillis()
        val map = LinkedHashMap<String, PlayerStat>()
        games.forEach { game ->
            if (game.isCancelled) return@forEach
            if (monthKey != null && DateUtils.monthKey(game.dateMillis) != monthKey) return@forEach
            val over = game.isOver(now)
            game.players.forEach { p ->
                if (p.isGuest) return@forEach
                val played = if (over && p.isAttended != false) 1 else 0
                val missed = if (p.isAttended == false) 1 else 0
                val old = map[p.id]
                map[p.id] = if (old == null) {
                    PlayerStat(p.id, p.name, p.phone, 1, played, missed)
                } else {
                    old.copy(
                        name = p.name,
                        phone = p.phone,
                        signedUp = old.signedUp + 1,
                        played = old.played + played,
                        missed = old.missed + missed
                    )
                }
            }
        }
        return map.values.sortedWith(
            compareByDescending<PlayerStat> { it.played }
                .thenByDescending { it.signedUp }
                .thenBy { it.name.lowercase() }
        )
    }

    /** Foydalanuvchining profil rasmi (o'yinchilar va izohlardan olinadi). Rasm yo'q bo'lsa bo'sh matn. */
    fun avatarOf(userId: String): String {
        AuthRepository.currentUser?.let { if (it.id == userId) return it.avatar }
        for (g in games) {
            g.players.firstOrNull { it.id == userId && it.avatar.isNotBlank() }?.let { return it.avatar }
        }
        for (g in games) {
            g.comments.firstOrNull { it.userId == userId && it.avatar.isNotBlank() }?.let { return it.avatar }
        }
        return ""
    }

    /** Reyting: kamida bitta o'yin o'ynaganlar. */
    fun ranking(monthKey: String? = null): List<PlayerStat> =
        playerStats(monthKey).filter { it.played > 0 }

    fun statFor(userId: String, monthKey: String? = null): PlayerStat? =
        playerStats(monthKey).find { it.id == userId }

    /** Foydalanuvchining reytingdagi o'rni (1 dan boshlab) yoki null. */
    fun rankOf(userId: String, monthKey: String? = null): Int? {
        val index = ranking(monthKey).indexOfFirst { it.id == userId }
        return if (index == -1) null else index + 1
    }

    // ---------------------------------------------------------------- Ichki

    /** So'rov yuboradi. Server o'zgargan o'yinni qaytaradi, ro'yxatga qo'yamiz. */
    private suspend fun mutate(method: String, path: String, body: JSONObject? = null): String? {
        val r = ApiClient.request(method, path, body)
        if (!r.ok) return r.message
        val g = r.json?.optJSONObject("game") ?: return null
        return try {
            applyGame(gameFromJson(g))
            null
        } catch (e: Exception) {
            "Serverdan noto'g'ri javob keldi."
        }
    }

    private fun applyGame(game: Game) {
        val index = games.indexOfFirst { it.id == game.id }
        if (index == -1) games.add(0, game) else games[index] = game
        saveCache()
        syncReminders()
    }

    /** Joriy foydalanuvchi band qilgan kelgusi o'yinlarga eslatma o'rnatadi, qolganlarini o'chiradi. */
    private fun syncReminders() {
        val me = AuthRepository.currentUser ?: return
        // Futbolchiga: band qilgan o'yinlari. Ishchi adminga: o'ziga biriktirilgan o'yinlar. (2 soat, 1 soat, 30 daqiqa oldin)
        ReminderScheduler.syncAll(games.toList(), me)
    }

    private fun requestJson(g: Game): JSONObject {
        val images = JSONArray()
        g.images.forEach { images.put(it) }
        val staffIds = JSONArray()
        g.staff.forEach { staffIds.put(it.id) }
        return JSONObject()
            .put("staffIds", staffIds)
            .put("venueName", g.venueName)
            .put("address", g.address)
            .put("mapLink", g.mapLink)
            .put("images", images)
            .put("dateMillis", g.dateMillis)
            .put("startTime", g.startTime)
            .put("durationMin", g.durationMin)
            .put("format", g.format)
            .put("maxPlayers", g.maxPlayers)
            .put("price", g.price)
            .put("description", g.description)
            .put("rules", g.rules)
            .put("payInfo", g.payInfo)
            .put("freeCancelHours", g.freeCancelHours)
    }

    private fun loadCache() {
        games.clear()
        val raw = prefs.getString(KEY_GAMES, null) ?: return
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) games.add(gameFromJson(arr.getJSONObject(i)))
        } catch (e: Exception) {
            games.clear()
        }
    }

    private fun saveCache() {
        val arr = JSONArray()
        games.forEach { arr.put(gameToJsonForCache(it)) }
        prefs.edit().putString(KEY_GAMES, arr.toString()).apply()
    }

    private fun strings(arr: JSONArray?): List<String> {
        if (arr == null) return emptyList()
        val out = ArrayList<String>()
        for (i in 0 until arr.length()) out.add(arr.getString(i))
        return out
    }

    private fun staffRefs(arr: JSONArray?): List<StaffRef> {
        if (arr == null) return emptyList()
        val out = ArrayList<StaffRef>()
        for (i in 0 until arr.length()) {
            val so = arr.getJSONObject(i)
            out.add(StaffRef(so.optString("id", ""), so.optString("name", "")))
        }
        return out
    }

    private fun gameFromJson(o: JSONObject): Game {
        val playersArr = o.optJSONArray("players") ?: JSONArray()
        val players = ArrayList<Player>()
        for (j in 0 until playersArr.length()) {
            val po = playersArr.getJSONObject(j)
            players.add(
                Player(
                    id = po.getString("id"),
                    name = po.optString("name", ""),
                    phone = po.optString("phone", ""),
                    isAttended = if (po.has("attended") && !po.isNull("attended")) po.getBoolean("attended") else null,
                    paid = po.optBoolean("paid", false),
                    payMethod = po.optString("payMethod", "CASH"),
                    bookedAt = po.optLong("bookedAt", 0L),
                    avatar = po.optString("avatar", "")
                )
            )
        }
        val commentsArr = o.optJSONArray("comments") ?: JSONArray()
        val comments = ArrayList<Comment>()
        for (j in 0 until commentsArr.length()) {
            val co = commentsArr.getJSONObject(j)
            comments.add(
                Comment(
                    id = co.getString("id"),
                    userId = co.optString("userId", ""),
                    userName = co.optString("userName", ""),
                    text = co.optString("text", ""),
                    createdAt = co.optLong("createdAt", 0L),
                    avatar = co.optString("avatar", "")
                )
            )
        }
        return Game(
            id = o.getString("id"),
            venueName = o.optString("venueName", ""),
            address = o.optString("address", ""),
            mapLink = o.optString("mapLink", ""),
            images = strings(o.optJSONArray("images")),
            dateMillis = o.optLong("dateMillis", DateUtils.today()),
            startTime = o.optString("startTime", "20:00"),
            durationMin = o.optInt("durationMin", 120),
            format = o.optString("format", "5v5"),
            maxPlayers = o.optInt("maxPlayers", 10),
            price = o.optInt("price", 0),
            description = o.optString("description", ""),
            rules = o.optString("rules", ""),
            payInfo = o.optString("payInfo", ""),
            freeCancelHours = o.optInt("freeCancelHours", 24),
            players = players,
            comments = comments,
            cancelledBy = strings(o.optJSONArray("cancelledBy")),
            staff = staffRefs(o.optJSONArray("staff")),
            isFinished = o.optBoolean("isFinished", false),
            isCancelled = o.optBoolean("isCancelled", false),
            createdAt = o.optLong("createdAt", 0L)
        )
    }

    private fun gameToJsonForCache(g: Game): JSONObject {
        val playersArr = JSONArray()
        g.players.forEach { p ->
            val po = JSONObject()
                .put("id", p.id)
                .put("name", p.name)
                .put("phone", p.phone)
                .put("paid", p.paid)
                .put("payMethod", p.payMethod)
                .put("bookedAt", p.bookedAt)
                .put("avatar", p.avatar)
            if (p.isAttended != null) po.put("attended", p.isAttended)
            playersArr.put(po)
        }
        val commentsArr = JSONArray()
        g.comments.forEach { c ->
            commentsArr.put(
                JSONObject()
                    .put("id", c.id)
                    .put("userId", c.userId)
                    .put("userName", c.userName)
                    .put("text", c.text)
                    .put("createdAt", c.createdAt)
                    .put("avatar", c.avatar)
            )
        }
        val imagesArr = JSONArray()
        g.images.forEach { imagesArr.put(it) }
        val cancelledArr = JSONArray()
        g.cancelledBy.forEach { cancelledArr.put(it) }
        val staffArr = JSONArray()
        g.staff.forEach { staffArr.put(JSONObject().put("id", it.id).put("name", it.name)) }

        return JSONObject()
            .put("id", g.id)
            .put("venueName", g.venueName)
            .put("address", g.address)
            .put("mapLink", g.mapLink)
            .put("images", imagesArr)
            .put("dateMillis", g.dateMillis)
            .put("startTime", g.startTime)
            .put("durationMin", g.durationMin)
            .put("format", g.format)
            .put("maxPlayers", g.maxPlayers)
            .put("price", g.price)
            .put("description", g.description)
            .put("rules", g.rules)
            .put("payInfo", g.payInfo)
            .put("freeCancelHours", g.freeCancelHours)
            .put("players", playersArr)
            .put("comments", commentsArr)
            .put("cancelledBy", cancelledArr)
            .put("staff", staffArr)
            .put("isFinished", g.isFinished)
            .put("isCancelled", g.isCancelled)
            .put("createdAt", g.createdAt)
    }
}
