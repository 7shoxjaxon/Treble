package com.shohjahon.treble.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

/**
 * Telegram havolasini ochadi. Har safar tanlov oynasini ko'rsatadi, shunda foydalanuvchi
 * bir nechta Telegram ilovasi (Telegram, Telegram X, brauzer...) orasidan o'zi tanlay oladi.
 */
fun openTelegramChooser(context: Context, url: String) {
    try {
        val view = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        val chooser = Intent.createChooser(view, "Qaysi Telegram'da ochilsin?")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    } catch (_: Exception) {
        Toast.makeText(context, "Telegram topilmadi", Toast.LENGTH_SHORT).show()
    }
}

/** Havolani nusxalaydi: boshqa Telegram akkauntida qo'lda ochish uchun. */
fun copyLink(context: Context, text: String) {
    try {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("Treble bot", text))
        Toast.makeText(context, "Havola nusxalandi", Toast.LENGTH_SHORT).show()
    } catch (_: Exception) {
    }
}
