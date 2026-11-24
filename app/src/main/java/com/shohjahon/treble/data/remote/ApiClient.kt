package com.shohjahon.treble.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Server javobi. [networkError] = internet yo'q yoki server javob bermadi. */
data class ApiResponse(val status: Int, val json: JSONObject?, val networkError: Boolean = false) {
    val ok: Boolean get() = !networkError && status in 200..299

    /** Foydalanuvchiga ko'rsatiladigan xato matni (server o'zbekcha yuboradi). */
    val message: String
        get() {
            if (networkError) return ApiClient.NETWORK_ERROR
            val m = json?.optString("message")
            return if (m.isNullOrBlank()) "Xatolik yuz berdi. Qayta urinib ko'ring." else m
        }
}

object ApiClient {
    const val NETWORK_ERROR = "Serverga ulanib bo'lmadi. Internetni tekshirib, qayta urinib ko'ring."

    /** Joriy sessiya tokeni. */
    @Volatile
    var token: String? = null

    /** Server "sessiya tugagan" (401) desa chaqiriladi. Asosiy oqimda ishlaydi. */
    @Volatile
    var onUnauthorized: (() -> Unit)? = null

    /**
     * JSON so'rov. [auth] = true bo'lsa token yuboriladi va 401 kelsa sessiya tugagan deb hisoblanadi.
     * DELETE so'rovlarga tana qo'shilmaydi (Android buni qo'llab-quvvatlamaydi).
     */
    suspend fun request(
        method: String,
        path: String,
        body: JSONObject? = null,
        auth: Boolean = true,
        tokenOverride: String? = null
    ): ApiResponse {
        val bytes = body?.toString()?.toByteArray(Charsets.UTF_8)
        val bearer = tokenOverride ?: token
        val response = withContext(Dispatchers.IO) {
            execute(method, path, bytes, "application/json; charset=utf-8", bearer)
        }
        if (auth && response.status == 401 && bearer != null && tokenOverride == null) {
            withContext(Dispatchers.Main) { onUnauthorized?.invoke() }
        }
        return response
    }

    /** Rasm yuklash (admin). */
    suspend fun upload(path: String, bytes: ByteArray, contentType: String): ApiResponse {
        val bearer = token
        return withContext(Dispatchers.IO) { execute("POST", path, bytes, contentType, bearer) }
    }

    private fun execute(method: String, path: String, body: ByteArray?, contentType: String, bearer: String?): ApiResponse {
        var conn: HttpURLConnection? = null
        try {
            conn = URL(ApiConfig.BASE_URL + path).openConnection() as HttpURLConnection
            conn.requestMethod = method
            conn.connectTimeout = 10_000
            conn.readTimeout = 20_000
            conn.setRequestProperty("Accept", "application/json")
            if (bearer != null) conn.setRequestProperty("Authorization", "Bearer $bearer")
            if (body != null && method != "GET" && method != "DELETE") {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", contentType)
                conn.outputStream.use { it.write(body) }
            }
            val status = conn.responseCode
            val stream = if (status in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            val json = try {
                JSONObject(text)
            } catch (e: Exception) {
                null
            }
            return ApiResponse(status, json)
        } catch (e: IOException) {
            return ApiResponse(0, null, networkError = true)
        } catch (e: Exception) {
            return ApiResponse(0, null, networkError = true)
        } finally {
            conn?.disconnect()
        }
    }
}
