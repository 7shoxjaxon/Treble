package com.shohjahon.treble.data.remote

import org.json.JSONObject

data class OtpSendResult(val error: String? = null, val retryAfter: Int = 60, val skipped: Boolean = false, val botUrl: String? = null)
data class OtpVerifyResult(val error: String? = null, val token: String? = null)

/**
 * SMS kod yuborish va tekshirish (server orqali).
 * [purpose]: "register" (ro'yxatdan o'tish) yoki "reset" (parolni tiklash).
 */
object OtpRepository {
    private fun digits(phone: String): String = phone.filter { it.isDigit() }

    suspend fun sendCode(phone: String, purpose: String = "register"): OtpSendResult {
        val body = JSONObject().put("phone", digits(phone)).put("purpose", purpose)
        val r = ApiClient.request("POST", "/otp/send", body, auth = false)
        if (!r.ok) return OtpSendResult(error = r.message)
        val skipped = r.json?.optBoolean("skipped", false) ?: false
        val botUrl = if (r.json?.optBoolean("needBot", false) == true) r.json?.optString("botUrl")?.takeIf { it.isNotBlank() } else null
        return OtpSendResult(error = null, retryAfter = r.json?.optInt("retryAfter", 60) ?: 60, skipped = skipped, botUrl = botUrl)
    }

    suspend fun verifyCode(phone: String, code: String, purpose: String = "register"): OtpVerifyResult {
        val body = JSONObject().put("phone", digits(phone)).put("code", code).put("purpose", purpose)
        val r = ApiClient.request("POST", "/otp/verify", body, auth = false)
        if (!r.ok) return OtpVerifyResult(error = r.message)
        val token = r.json?.optString("token")
        return if (token.isNullOrBlank()) OtpVerifyResult(error = "Serverdan noto'g'ri javob keldi.") else OtpVerifyResult(token = token)
    }
}
