package com.shohjahon.treble.notify

import android.content.Context
import android.content.SharedPreferences
import com.google.firebase.messaging.FirebaseMessaging
import com.shohjahon.treble.data.remote.ApiClient
import com.shohjahon.treble.data.repository.AuthRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import kotlin.coroutines.resume

/** Telefonning Firebase tokenini serverga yuboradi, shunda server ilova yopiq bo'lsa ham xabar yubora oladi. */
object PushRepository {
    private const val PREFS = "treble_push"
    private const val KEY_TOKEN = "sent_token"
    private const val KEY_USER = "sent_user"

    private var prefs: SharedPreferences? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var registeredFor: String? = null   // shu jarayonda qaysi foydalanuvchi uchun yuborilgan

    fun init(context: Context) {
        if (prefs == null) prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    /** Kirgan foydalanuvchi uchun token serverda bo'lishini ta'minlaydi (kerak bo'lsa yuboradi). */
    suspend fun ensureRegistered() {
        val me = AuthRepository.currentUser ?: return
        if (registeredFor == me.id) return
        val token = fetchToken() ?: return
        if (send(token)) registeredFor = me.id
    }

    /** Firebase yangi token bersa (masalan, ilova qayta o'rnatilganda). */
    suspend fun onNewToken(token: String) {
        registeredFor = null
        if (AuthRepository.currentUser != null && send(token)) registeredFor = AuthRepository.currentUser?.id
    }

    /** Hisobdan chiqilganda: bu telefon endi xabar olmasligi uchun tokenni serverdan o'chiradi. */
    fun onLogout(authToken: String) {
        val p = prefs ?: return
        val token = p.getString(KEY_TOKEN, null)
        p.edit().clear().apply()
        registeredFor = null
        if (token != null) {
            scope.launch {
                ApiClient.request(
                    "POST", "/push/unregister", JSONObject().put("token", token),
                    tokenOverride = authToken
                )
            }
        }
    }

    private suspend fun send(token: String): Boolean {
        val me = AuthRepository.currentUser ?: return false
        val r = ApiClient.request("POST", "/push/token", JSONObject().put("token", token))
        if (!r.ok) return false
        prefs?.edit()?.putString(KEY_TOKEN, token)?.putString(KEY_USER, me.id)?.apply()
        return true
    }

    private suspend fun fetchToken(): String? = suspendCancellableCoroutine { cont ->
        try {
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                if (cont.isActive) cont.resume(if (task.isSuccessful) task.result else null)
            }
        } catch (e: Exception) {
            // Firebase sozlanmagan bo'lsa ilova ishlashda davom etadi
            if (cont.isActive) cont.resume(null)
        }
    }
}
