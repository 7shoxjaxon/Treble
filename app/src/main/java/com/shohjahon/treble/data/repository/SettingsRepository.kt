package com.shohjahon.treble.data.repository

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.shohjahon.treble.data.remote.ApiClient
import org.json.JSONObject

/** Admin kiritadigan "Bog'lanish" ma'lumotlari (serverda saqlanadi, hamma ko'radi). */
object SettingsRepository {
    var supportPhone by mutableStateOf("")
        private set
    var supportTelegram by mutableStateOf("")
        private set

    @Suppress("UNUSED_PARAMETER")
    fun init(context: Context) {
        // Sozlamalar serverdan olinadi
    }

    suspend fun refresh() {
        val r = ApiClient.request("GET", "/settings")
        if (!r.ok) return
        supportPhone = r.json?.optString("supportPhone", "") ?: ""
        supportTelegram = r.json?.optString("supportTelegram", "") ?: ""
    }

    /** Xato bo'lsa matn, muvaffaqiyatda null qaytaradi. */
    suspend fun saveSupport(phone: String, telegram: String): String? {
        val body = JSONObject().put("supportPhone", phone.trim()).put("supportTelegram", telegram.trim())
        val r = ApiClient.request("PUT", "/settings", body)
        if (!r.ok) return r.message
        supportPhone = r.json?.optString("supportPhone", "") ?: ""
        supportTelegram = r.json?.optString("supportTelegram", "") ?: ""
        return null
    }
}
