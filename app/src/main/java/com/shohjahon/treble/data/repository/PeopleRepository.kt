package com.shohjahon.treble.data.repository

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.shohjahon.treble.data.models.User
import com.shohjahon.treble.data.models.UserRole
import com.shohjahon.treble.data.remote.ApiClient
import org.json.JSONArray
import org.json.JSONObject

/**
 * Faqat bosh admin uchun: ishchi adminlar ro'yxati va ro'yxatdan o'tgan barcha o'yinchilar.
 * Ma'lumotlar telefonda saqlanmaydi (telefon raqamlari qolib ketmasligi uchun), har safar serverdan olinadi.
 */
object PeopleRepository {
    val staff = mutableStateListOf<User>()
    val players = mutableStateListOf<User>()

    /** Ro'yxat birinchi marta yuklanayotgan paytda true. */
    var loadingStaff by mutableStateOf(false)
        private set
    var loadingPlayers by mutableStateOf(false)
        private set

    fun clear() {
        staff.clear()
        players.clear()
    }

    /** Ishchi adminlarni yangilaydi. Xato bo'lsa matn qaytaradi. */
    suspend fun refreshStaff(): String? {
        loadingStaff = true
        val r = ApiClient.request("GET", "/staff")
        loadingStaff = false
        val arr = r.json?.optJSONArray("staff")
        if (!r.ok || arr == null) return r.message
        return try {
            val list = parse(arr, UserRole.STAFF)
            staff.clear()
            staff.addAll(list)
            null
        } catch (e: Exception) {
            "Serverdan noto'g'ri javob keldi."
        }
    }

    /** Barcha ro'yxatdan o'tgan o'yinchilarni (nomeri bilan) yangilaydi. */
    suspend fun refreshPlayers(): String? {
        loadingPlayers = true
        val r = ApiClient.request("GET", "/players")
        loadingPlayers = false
        val arr = r.json?.optJSONArray("players")
        if (!r.ok || arr == null) return r.message
        return try {
            val list = parse(arr, UserRole.USER)
            players.clear()
            players.addAll(list)
            null
        } catch (e: Exception) {
            "Serverdan noto'g'ri javob keldi."
        }
    }

    suspend fun addStaff(name: String, login: String, password: String): String? {
        val cleanName = name.trim().replace(Regex("\\s+"), " ")
        val cleanLogin = login.trim()
        if (cleanName.length < 2) return "Ismni kiriting (kamida 2 harf)."
        if (cleanLogin.length < 4) return "Logon kamida 4 ta belgidan iborat bo'lsin."
        if (cleanLogin.length > 20) return "Logon 20 ta belgidan oshmasin."
        if (!cleanLogin.matches(Regex("^[A-Za-z0-9_.]+$"))) {
            return "Logonda faqat lotin harflari, raqam, _ va . bo'lishi mumkin."
        }
        if (password.length < 6) return "Parol kamida 6 ta belgidan iborat bo'lsin."
        if (password.equals(cleanLogin, ignoreCase = true)) return "Parol logon bilan bir xil bo'lmasin."
        val body = JSONObject()
            .put("name", cleanName)
            .put("login", cleanLogin)
            .put("password", password)
        val r = ApiClient.request("POST", "/staff", body)
        if (!r.ok) return r.message
        return refreshStaff()
    }

    suspend fun deleteStaff(id: String): String? {
        val r = ApiClient.request("DELETE", "/staff/$id")
        if (!r.ok) return r.message
        staff.removeAll { it.id == id }
        return null
    }

    private fun parse(arr: JSONArray, role: UserRole): List<User> {
        val out = ArrayList<User>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out.add(
                User(
                    id = o.getString("id"),
                    name = o.optString("name", ""),
                    phone = o.optString("phone", ""),
                    login = o.optString("login", ""),
                    role = role,
                    joinedAt = o.optLong("joinedAt", 0L),
                    avatar = o.optString("avatar", "")
                )
            )
        }
        return out
    }
}
