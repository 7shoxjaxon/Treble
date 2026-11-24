package com.shohjahon.treble.data.repository

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import com.shohjahon.treble.data.models.Coupon
import com.shohjahon.treble.data.remote.ApiClient

/**
 * Bepul o'yin kuponlari. Har oy tugagach server shu oyda eng ko'p o'ynagan 3 futbolchiga bittadan kupon beradi.
 * Bu yerda faqat o'zingizning kuponlaringiz ko'rinadi.
 */
object CouponRepository {
    val coupons = mutableStateListOf<Coupon>()

    @Suppress("UNUSED_PARAMETER")
    fun init(context: Context) {
        // Kuponlar serverdan olinadi
    }

    suspend fun refresh() {
        val r = ApiClient.request("GET", "/coupons")
        if (!r.ok) return
        val arr = r.json?.optJSONArray("coupons") ?: return
        val list = ArrayList<Coupon>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            list.add(
                Coupon(
                    id = o.getString("id"),
                    userId = o.getString("userId"),
                    monthKey = o.optString("monthKey", ""),
                    usedGameId = if (o.isNull("usedGameId")) null else o.optString("usedGameId")
                )
            )
        }
        if (coupons.toList() != list) {
            coupons.clear()
            coupons.addAll(list)
        }
    }

    fun clear() {
        coupons.clear()
    }

    fun forUser(userId: String): List<Coupon> = coupons.filter { it.userId == userId }

    fun available(userId: String): List<Coupon> = forUser(userId).filter { it.usedGameId == null }
}
