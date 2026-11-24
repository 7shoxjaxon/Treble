package com.shohjahon.treble.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.shohjahon.treble.data.models.AuthResult
import com.shohjahon.treble.data.models.User
import com.shohjahon.treble.data.models.UserRole
import com.shohjahon.treble.data.remote.ApiClient
import com.shohjahon.treble.data.remote.ApiResponse
import com.shohjahon.treble.data.util.ImageStore
import com.shohjahon.treble.notify.PushRepository
import com.shohjahon.treble.notify.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Hisob: kirish, ro'yxatdan o'tish, profil. Hisoblar va parollar serverda saqlanadi.
 * Telefonda faqat sessiya tokeni va oxirgi ma'lum profil nusxasi turadi (ilova tez ochilishi uchun).
 */
object AuthRepository {
    private const val PREFS = "treble_session"
    private const val KEY_TOKEN = "token"
    private const val KEY_USER = "user"
    private const val KEY_DEFAULT_PW = "default_pw"
    private const val AVATAR_MAX_SIDE = 720

    private lateinit var prefs: SharedPreferences
    private var initialized = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    var currentUser by mutableStateOf<User?>(null)
        private set

    /** Admin hali standart paroldan foydalanmoqda (ogohlantirish uchun). */
    var usingDefaultPassword by mutableStateOf(false)
        private set

    val isAdmin: Boolean get() = currentUser?.role == UserRole.ADMIN || currentUser?.role == UserRole.STAFF

    fun init(context: Context) {
        if (initialized) return
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val token = prefs.getString(KEY_TOKEN, null)
        val cached = prefs.getString(KEY_USER, null)
        if (token != null && cached != null) {
            try {
                currentUser = userFromJson(JSONObject(cached))
                usingDefaultPassword = prefs.getBoolean(KEY_DEFAULT_PW, false)
                ApiClient.token = token
            } catch (e: Exception) {
                prefs.edit().clear().apply()
            }
        }
        // Server "sessiya tugagan" desa, avtomatik chiqamiz
        ApiClient.onUnauthorized = { clearSession() }
        initialized = true
    }

    // ---------------------------------------------------------------- Kirish va ro'yxatdan o'tish

    suspend fun login(loginInput: String, password: String): AuthResult {
        if (loginInput.isBlank() || password.isBlank()) return AuthResult.Error("Logon va parolni kiriting.")
        val body = JSONObject().put("login", loginInput.trim()).put("password", password)
        return handleAuth(ApiClient.request("POST", "/auth/login", body, auth = false))
    }

    /** Ma'lumotlarni telefonning o'zida tekshiradi (SMS yuborishdan oldin). Band logon/telefonni server tekshiradi. */
    fun validateRegistration(
        name: String,
        phoneInput: String,
        loginInput: String,
        password: String,
        confirmPassword: String
    ): AuthResult {
        val cleanName = name.trim().replace(Regex("\\s+"), " ")
        val login = loginInput.trim()
        if (cleanName.length < 2) return AuthResult.Error("Ismingizni kiriting (kamida 2 harf).")
        if (normalizePhone(phoneInput) == null) {
            return AuthResult.Error("Telefon raqami noto'g'ri. 9 ta raqam kiriting, masalan: 90 123 45 67")
        }
        validateLogin(login)?.let { return AuthResult.Error(it) }
        validatePassword(password, login)?.let { return AuthResult.Error(it) }
        if (password != confirmPassword) return AuthResult.Error("Parollar bir xil emas.")
        return AuthResult.Success
    }

    /** [otpToken] = SMS kod tasdiqlangach server bergan token (server SMS talab qilmasa null). */
    suspend fun register(
        name: String,
        phoneInput: String,
        loginInput: String,
        password: String,
        confirmPassword: String,
        otpToken: String?
    ): AuthResult {
        val check = validateRegistration(name, phoneInput, loginInput, password, confirmPassword)
        if (check is AuthResult.Error) return check
        val phone = normalizePhone(phoneInput) ?: return AuthResult.Error("Telefon raqami noto'g'ri.")
        val body = JSONObject()
            .put("name", name.trim())
            .put("phone", phone.filter { it.isDigit() })
            .put("login", loginInput.trim())
            .put("password", password)
        if (otpToken != null) body.put("otpToken", otpToken)
        return handleAuth(ApiClient.request("POST", "/auth/register", body, auth = false))
    }

    /** "Parolni unutdim": SMS bilan tasdiqlangan telefon uchun yangi parol o'rnatadi va kiritadi. */
    suspend fun resetPassword(phoneInput: String, otpToken: String, newPassword: String, confirm: String): AuthResult {
        val phone = normalizePhone(phoneInput) ?: return AuthResult.Error("Telefon raqami noto'g'ri.")
        if (newPassword.length < 6) return AuthResult.Error("Parol kamida 6 ta belgidan iborat bo'lsin.")
        if (newPassword != confirm) return AuthResult.Error("Parollar bir xil emas.")
        val body = JSONObject()
            .put("phone", phone.filter { it.isDigit() })
            .put("otpToken", otpToken)
            .put("newPassword", newPassword)
        return handleAuth(ApiClient.request("POST", "/auth/reset", body, auth = false))
    }

    private fun handleAuth(r: ApiResponse): AuthResult {
        val json = r.json
        if (!r.ok || json == null) return AuthResult.Error(r.message)
        return try {
            val token = json.getString("token")
            val user = userFromJson(json.getJSONObject("user"))
            val defaultPw = json.optBoolean("usingDefaultPassword", false)
            prefs.edit()
                .putString(KEY_TOKEN, token)
                .putString(KEY_USER, userToJson(user).toString())
                .putBoolean(KEY_DEFAULT_PW, defaultPw)
                .apply()
            ApiClient.token = token
            usingDefaultPassword = defaultPw
            currentUser = user
            AuthResult.Success
        } catch (e: Exception) {
            AuthResult.Error("Serverdan noto'g'ri javob keldi.")
        }
    }

    fun logout() {
        val token = ApiClient.token
        if (token != null) {
            // Serverdagi sessiyani ham o'chiramiz (javobini kutmaymiz)
            scope.launch { ApiClient.request("POST", "/auth/logout", auth = false, tokenOverride = token) }
        }
        clearSession()
    }

    private fun clearSession() {
        ApiClient.token?.let { PushRepository.onLogout(it) } // bu telefon endi push xabar olmaydi
        if (initialized) prefs.edit().clear().apply()
        ApiClient.token = null
        currentUser = null
        usingDefaultPassword = false
        GameRepository.games.forEach { ReminderScheduler.cancel(it.id) }
        GameRepository.clear()
        NotificationRepository.clear()
        ChatRepository.clear()
        CouponRepository.clear()
        PeopleRepository.clear()
    }

    // ---------------------------------------------------------------- Profil

    /** Serverdan joriy profilni yangilaydi (ilova ochilganda). */
    suspend fun refreshMe() {
        if (ApiClient.token == null) return
        val r = ApiClient.request("GET", "/me")
        val json = r.json
        if (!r.ok || json == null) return
        try {
            val user = userFromJson(json.getJSONObject("user"))
            val defaultPw = json.optBoolean("usingDefaultPassword", false)
            prefs.edit().putString(KEY_USER, userToJson(user).toString()).putBoolean(KEY_DEFAULT_PW, defaultPw).apply()
            usingDefaultPassword = defaultPw
            if (currentUser != user) currentUser = user
        } catch (e: Exception) {
            // eski nusxa qoladi
        }
    }

    suspend fun updateProfile(name: String): AuthResult {
        val clean = name.trim().replace(Regex("\\s+"), " ")
        if (clean.length < 2) return AuthResult.Error("Ismingizni kiriting (kamida 2 harf).")
        val r = ApiClient.request("PUT", "/me", JSONObject().put("name", clean))
        val json = r.json
        if (!r.ok || json == null) return AuthResult.Error(r.message)
        return try {
            val user = userFromJson(json.getJSONObject("user"))
            prefs.edit().putString(KEY_USER, userToJson(user).toString()).apply()
            currentUser = user
            GameRepository.refresh()
            AuthResult.Success
        } catch (e: Exception) {
            AuthResult.Error("Serverdan noto'g'ri javob keldi.")
        }
    }

    /** Profil rasmini galereyadan tanlab serverga yuklaydi. */
    suspend fun uploadAvatar(context: Context, uri: Uri): AuthResult {
        val bytes = ImageStore.jpegBytes(context, uri, AVATAR_MAX_SIDE)
            ?: return AuthResult.Error("Rasmni o'qib bo'lmadi.")
        return applyUserResponse(ApiClient.upload("/me/avatar", bytes, "image/jpeg"))
    }

    suspend fun removeAvatar(): AuthResult = applyUserResponse(ApiClient.request("DELETE", "/me/avatar"))

    private suspend fun applyUserResponse(r: ApiResponse): AuthResult {
        val json = r.json
        if (!r.ok || json == null) return AuthResult.Error(r.message)
        return try {
            val user = userFromJson(json.getJSONObject("user"))
            prefs.edit().putString(KEY_USER, userToJson(user).toString()).apply()
            currentUser = user
            GameRepository.refresh() // o'yinchilar ro'yxatida ham yangi rasm ko'rinsin
            AuthResult.Success
        } catch (e: Exception) {
            AuthResult.Error("Serverdan noto'g'ri javob keldi.")
        }
    }

    suspend fun changePassword(oldPassword: String, newPassword: String, confirm: String): AuthResult {
        if (newPassword.length < 6) return AuthResult.Error("Parol kamida 6 ta belgidan iborat bo'lsin.")
        if (newPassword != confirm) return AuthResult.Error("Yangi parollar bir xil emas.")
        if (newPassword == oldPassword) return AuthResult.Error("Yangi parol eskisidan farq qilishi kerak.")
        val body = JSONObject().put("oldPassword", oldPassword).put("newPassword", newPassword)
        val r = ApiClient.request("POST", "/me/password", body)
        if (!r.ok) return AuthResult.Error(r.message)
        usingDefaultPassword = false
        prefs.edit().putBoolean(KEY_DEFAULT_PW, false).apply()
        return AuthResult.Success
    }

    suspend fun deleteAccount(password: String): AuthResult {
        val r = ApiClient.request("POST", "/me/delete", JSONObject().put("password", password))
        if (!r.ok) return AuthResult.Error(r.message)
        clearSession()
        return AuthResult.Success
    }

    // ---------------------------------------------------------------- Yordamchi funksiyalar

    /** Telefonni tekshiradi va "+998 90 123 45 67" ko'rinishiga keltiradi. Noto'g'ri bo'lsa null. */
    fun normalizePhone(input: String): String? {
        var digits = input.filter { it.isDigit() }
        if (digits.length == 12 && digits.startsWith("998")) digits = digits.substring(3)
        if (digits.length != 9) return null
        return "+998 ${digits.substring(0, 2)} ${digits.substring(2, 5)} ${digits.substring(5, 7)} ${digits.substring(7, 9)}"
    }

    private fun validateLogin(login: String): String? {
        if (login.length < 4) return "Logon kamida 4 ta belgidan iborat bo'lsin."
        if (login.length > 20) return "Logon 20 ta belgidan oshmasin."
        if (!login.matches(Regex("^[A-Za-z0-9_.]+$"))) {
            return "Logonda faqat lotin harflari, raqam, _ va . bo'lishi mumkin."
        }
        return null
    }

    private fun validatePassword(password: String, login: String): String? {
        if (password.length < 6) return "Parol kamida 6 ta belgidan iborat bo'lsin."
        if (password.length > 64) return "Parol juda uzun."
        if (password.equals(login, ignoreCase = true)) return "Parol logon bilan bir xil bo'lmasin."
        return null
    }

    private fun userFromJson(o: JSONObject): User = User(
        id = o.getString("id"),
        name = o.getString("name"),
        phone = o.optString("phone", ""),
        login = o.getString("login"),
        role = when (o.optString("role")) {
            "ADMIN" -> UserRole.ADMIN
            "STAFF" -> UserRole.STAFF
            else -> UserRole.USER
        },
        joinedAt = o.optLong("joinedAt", 0L),
        avatar = o.optString("avatar", "")
    )

    private fun userToJson(u: User): JSONObject = JSONObject()
        .put("id", u.id)
        .put("name", u.name)
        .put("phone", u.phone)
        .put("login", u.login)
        .put("role", u.role.name)
        .put("joinedAt", u.joinedAt)
        .put("avatar", u.avatar)
}
