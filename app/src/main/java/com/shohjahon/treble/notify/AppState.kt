package com.shohjahon.treble.notify

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Ilova hozir ekranda ochiqmi (ochiq bo'lsa, push o'rniga ilovaning o'zida yangilanadi). */
object AppState {
    @Volatile
    var foreground: Boolean = false
}

/** Bildirishnomaga bosilganda qaysi bo'limni ochish kerakligi (HomeScreen o'qib, tozalaydi). */
object NavIntentState {
    var tab by mutableStateOf<Int?>(null)
}
