package com.shohjahon.treble.data.util

import java.util.Calendar

/** Sana/vaqt yordamchilari. java.time ishlatilmaydi (minSdk 24 da ishlamaydi). */
object DateUtils {
    private val MONTHS = arrayOf("Yan", "Fev", "Mar", "Apr", "May", "Iyn", "Iyl", "Avg", "Sen", "Okt", "Noy", "Dek")
    private val MONTHS_FULL = arrayOf(
        "Yanvar", "Fevral", "Mart", "Aprel", "May", "Iyun",
        "Iyul", "Avgust", "Sentabr", "Oktabr", "Noyabr", "Dekabr"
    )
    private val DAYS = arrayOf("Yak", "Dush", "Sesh", "Chor", "Pay", "Jum", "Shan")

    private fun cal(millis: Long): Calendar {
        val c = Calendar.getInstance()
        c.timeInMillis = millis
        return c
    }

    fun startOfDay(millis: Long): Long {
        val c = cal(millis)
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    fun today(): Long = startOfDay(System.currentTimeMillis())

    fun addDays(dayMillis: Long, days: Int): Long {
        val c = cal(dayMillis)
        c.add(Calendar.DAY_OF_YEAR, days)
        return startOfDay(c.timeInMillis)
    }

    private fun weekday(dayMillis: Long): String = DAYS[cal(dayMillis).get(Calendar.DAY_OF_WEEK) - 1]
    private fun dayOfMonth(dayMillis: Long): Int = cal(dayMillis).get(Calendar.DAY_OF_MONTH)
    private fun monthShort(dayMillis: Long): String = MONTHS[cal(dayMillis).get(Calendar.MONTH)]

    /** "Bugun, 17 Okt" / "Ertaga, 18 Okt" / "Chor, 21 Okt" */
    fun dayLabel(dayMillis: Long): String {
        val d = startOfDay(dayMillis)
        val t = today()
        val prefix = when (d) {
            t -> "Bugun"
            addDays(t, 1) -> "Ertaga"
            else -> weekday(d)
        }
        return "$prefix, ${dayOfMonth(d)} ${monthShort(d)}"
    }

    /** Sana lentasi uchun: "Bugun, 17 Okt" / "Yak, 18 Okt" */
    fun chipLabel(dayMillis: Long): String {
        val d = startOfDay(dayMillis)
        val prefix = if (d == today()) "Bugun" else weekday(d)
        return "$prefix, ${dayOfMonth(d)} ${monthShort(d)}"
    }

    /** Doim hafta kuni bilan: "Chor, 21 Okt" */
    fun dateWithWeekday(dayMillis: Long): String {
        val d = startOfDay(dayMillis)
        return "${weekday(d)}, ${dayOfMonth(d)} ${monthShort(d)}"
    }

    /** "17 Okt, 19:05" */
    fun dateTime(millis: Long): String {
        val c = cal(millis)
        return "%d %s, %02d:%02d".format(
            c.get(Calendar.DAY_OF_MONTH), MONTHS[c.get(Calendar.MONTH)],
            c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE)
        )
    }

    /** "19:00" ni daqiqalarga (0..1439) aylantiradi. Noto'g'ri bo'lsa null. */
    fun parseMinutes(hhmm: String): Int? {
        val m = Regex("^([01]?\\d|2[0-3]):([0-5]\\d)$").matchEntire(hhmm.trim()) ?: return null
        return m.groupValues[1].toInt() * 60 + m.groupValues[2].toInt()
    }

    fun formatMinutes(total: Int): String {
        val t = ((total % 1440) + 1440) % 1440
        return "%02d:%02d".format(t / 60, t % 60)
    }

    /** Berilgan kun + "HH:mm" => millisekund. */
    fun timeWithDate(dayMillis: Long, hhmm: String): Long {
        val minutes = parseMinutes(hhmm) ?: 0
        return startOfDay(dayMillis) + minutes * 60_000L
    }

    /** "2026-10" */
    fun monthKey(millis: Long): String {
        val c = cal(millis)
        return "%04d-%02d".format(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1)
    }

    fun currentMonthKey(): String = monthKey(System.currentTimeMillis())

    /** "2026-09" => "Sentabr 2026" */
    fun monthLabel(key: String): String {
        val parts = key.split("-")
        val year = parts.getOrNull(0) ?: return key
        val month = parts.getOrNull(1)?.toIntOrNull() ?: return key
        return "${MONTHS_FULL.getOrElse(month - 1) { "" }} $year"
    }

    /** "Oktabr 2026" */
    fun monthYear(millis: Long): String = monthLabel(monthKey(millis))
}

/** 30000 => "30 000" */
fun formatPrice(value: Int): String {
    val s = value.toString()
    val sb = StringBuilder()
    for (i in s.indices) {
        if (i > 0 && (s.length - i) % 3 == 0) sb.append(' ')
        sb.append(s[i])
    }
    return sb.toString()
}
