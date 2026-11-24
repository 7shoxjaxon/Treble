package com.shohjahon.treble.ui.components

import coil.compose.SubcomposeAsyncImage
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.shohjahon.treble.data.models.Game
import com.shohjahon.treble.data.models.GameStatus
import com.shohjahon.treble.data.util.DateUtils
import com.shohjahon.treble.data.util.ImageStore
import com.shohjahon.treble.data.util.formatPrice
import com.shohjahon.treble.ui.theme.*

// ------------------------------------------------------------------ Tashqi amallar

/** Havolani ochadi. Noto'g'ri bo'lsa xabar ko'rsatadi, ilova qulamaydi. */
fun openLink(context: Context, url: String) {
    try {
        val fixed = if (url.startsWith("http://") || url.startsWith("https://")) url else "https://$url"
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(fixed)))
    } catch (e: Exception) {
        Toast.makeText(context, "Havolani ochib bo'lmadi", Toast.LENGTH_SHORT).show()
    }
}

fun dialNumber(context: Context, phone: String) {
    try {
        val clean = phone.filter { it.isDigit() || it == '+' }
        context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$clean")))
    } catch (e: Exception) {
        Toast.makeText(context, "Qo'ng'iroq qilib bo'lmadi", Toast.LENGTH_SHORT).show()
    }
}

/** Maydonni xaritada ochadi: havola bo'lsa shuni, bo'lmasa nom/manzil bo'yicha qidiradi. */
fun openMap(context: Context, game: Game) {
    if (game.mapLink.isNotBlank()) {
        openLink(context, game.mapLink)
    } else {
        val query = Uri.encode("${game.venueName} ${game.address}".trim())
        openLink(context, "https://www.google.com/maps/search/?api=1&query=$query")
    }
}

fun openMapFor(context: Context, name: String, address: String, mapLink: String) {
    if (mapLink.isNotBlank()) {
        openLink(context, mapLink)
    } else {
        openLink(context, "https://www.google.com/maps/search/?api=1&query=" + Uri.encode("$name $address".trim()))
    }
}

fun shareGame(context: Context, game: Game) {
    try {
        val text = buildString {
            append("⚽ ${game.venueName}\n")
            append("${DateUtils.dateWithWeekday(game.dateMillis)}, ${game.timeRange()}\n")
            append("${game.format} · ${formatPrice(game.price)} so'm\n")
            append("Joy qoldi: ${game.spotsLeft}/${game.maxPlayers}")
            if (game.mapLink.isNotBlank()) append("\n${game.mapLink}")
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(intent, "Ulashish"))
    } catch (e: Exception) {
        Toast.makeText(context, "Ulashib bo'lmadi", Toast.LENGTH_SHORT).show()
    }
}

/** Telefonning taqvimiga o'yinni qo'shish oynasini ochadi (ruxsat talab qilinmaydi). */
fun addToCalendar(context: Context, game: Game) {
    try {
        val intent = Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, game.startMillis)
            putExtra(CalendarContract.EXTRA_EVENT_END_TIME, game.endMillis)
            putExtra(CalendarContract.Events.TITLE, "⚽ ${game.venueName} (${game.format})")
            putExtra(CalendarContract.Events.EVENT_LOCATION, game.address.ifBlank { game.venueName })
            putExtra(
                CalendarContract.Events.DESCRIPTION,
                "Narx: ${if (game.price == 0) "bepul" else formatPrice(game.price) + " so'm"}"
            )
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "Taqvim ilovasi topilmadi", Toast.LENGTH_SHORT).show()
    }
}

private fun shareText(context: Context, text: String) {
    try {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(intent, "Ulashish"))
    } catch (e: Exception) {
        Toast.makeText(context, "Ulashib bo'lmadi", Toast.LENGTH_SHORT).show()
    }
}

/** Band qilganlar ro'yxatini matn qilib ulashadi (Telegram guruhiga tashlash uchun). Telefonsiz. */
fun sharePlayersList(context: Context, game: Game) {
    val text = buildString {
        append("⚽ ${game.venueName}\n")
        append("${DateUtils.dateWithWeekday(game.dateMillis)}, ${game.timeRange()} · ${game.format}\n")
        append("Band: ${game.players.size}/${game.maxPlayers}\n\n")
        if (game.players.isEmpty()) {
            append("Hozircha hech kim yo'q")
        } else {
            game.players.forEachIndexed { i, p -> append("${i + 1}. ${p.name}\n") }
        }
    }
    shareText(context, text.trimEnd())
}

/** Tuzilgan jamoalarni matn qilib ulashadi. */
fun shareTeams(context: Context, game: Game, teamA: List<String>, teamB: List<String>) {
    val text = buildString {
        append("⚽ ${game.venueName} · ${DateUtils.dateWithWeekday(game.dateMillis)}, ${game.startTime}\n\n")
        append("🟢 A jamoa\n")
        teamA.forEachIndexed { i, n -> append("${i + 1}. $n\n") }
        append("\n🔵 B jamoa\n")
        teamB.forEachIndexed { i, n -> append("${i + 1}. $n\n") }
    }
    shareText(context, text.trimEnd())
}

// ------------------------------------------------------------------ Holat belgisi

fun statusLabel(status: GameStatus): String = when (status) {
    GameStatus.OPEN -> "Joy bor"
    GameStatus.FULL -> "To'lgan"
    GameStatus.FINISHED -> "Yakunlangan"
    GameStatus.CANCELLED -> "Bekor qilingan"
}

fun statusBg(status: GameStatus): Color = when (status) {
    GameStatus.OPEN -> StatusOpenBg
    GameStatus.CANCELLED -> StatusCancelBg
    else -> StatusFullBg
}

fun statusFg(status: GameStatus): Color = when (status) {
    GameStatus.OPEN -> StatusOpenFg
    GameStatus.CANCELLED -> StatusCancelFg
    else -> StatusFullFg
}

@Composable
fun GameStatusPill(status: GameStatus, modifier: Modifier = Modifier, label: String? = null) {
    StatusPill(
        text = label ?: statusLabel(status),
        background = statusBg(status),
        foreground = statusFg(status),
        modifier = modifier
    )
}

// ------------------------------------------------------------------ Rasm

@Composable
fun GameImage(path: String?, modifier: Modifier = Modifier) {
    if (path == null) {
        Box(modifier = modifier.background(Surface2), contentAlignment = Alignment.Center) {
            BallIcon(tint = TextSecondary.copy(alpha = 0.5f), size = 28.dp)
        }
    } else {
        AsyncImage(
            model = ImageStore.model(path),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.background(Surface2)
        )
    }
}

// ------------------------------------------------------------------ O'yin kartasi

/**
 * Ro'yxatdagi o'yin kartasi.
 * [joined] = joriy foydalanuvchi band qilgan; [statusLabelOverride] holat yozuvini almashtiradi.
 */
@Composable
fun GameCard(
    game: Game,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    joined: Boolean = false,
    statusLabelOverride: String? = null,
    statusOverride: GameStatus? = null
) {
    val status = statusOverride ?: game.status()
    val left = game.spotsLeft

    SurfaceCard(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
        contentPadding = PaddingValues(14.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            GameImage(
                path = game.images.firstOrNull(),
                modifier = Modifier
                    .size(68.dp)
                    .clip(RoundedCornerShape(14.dp))
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = game.venueName,
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    GameStatusPill(status, label = statusLabelOverride)
                }
                if (game.address.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = game.address,
                        color = TextSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TrebleIcon(IconPaths.Clock, TextSecondary, size = 15.dp)
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "${DateUtils.dayLabel(game.dateMillis)} · ${game.startTime}",
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = game.format,
                        color = TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Surface2)
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Outline)
        )
        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${game.players.size}/${game.maxPlayers} band",
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = when {
                        joined -> "✓ Siz band qilgansiz"
                        left == 0 -> "Joy qolmadi"
                        else -> "$left ta joy qoldi"
                    },
                    color = if (joined) LimeGreen else TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = if (joined) FontWeight.SemiBold else FontWeight.Normal
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = if (game.price == 0) "Bepul" else "${formatPrice(game.price)} so'm",
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(text = "har bir o'yinchi", color = TextSecondary, fontSize = 11.sp)
            }
        }
    }
}

// ------------------------------------------------------------------ Yangi dizayn: katta va qator kartalar

private fun spotsText(game: Game, joined: Boolean, status: GameStatus): Pair<String, Color> = when {
    status == GameStatus.CANCELLED -> "Bekor qilingan" to StatusCancelFg
    joined -> "✓ Siz band qilgansiz" to LimeGreen
    game.spotsLeft == 0 -> "Joy qolmadi" to TextSecondary
    else -> "${game.spotsLeft} ta joy qoldi" to TextSecondary
}

private fun priceText(game: Game): String = if (game.price == 0) "Bepul" else "${formatPrice(game.price)} so'm"

/** Rasm yo'q yoki yuklanmaganda: yashil maydon ko'rinishidagi zaxira fon. */
@Composable
private fun FeaturedFallback() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(Color(0xFF226B47), Color(0xFF0A2A1E))))
            .drawBehind { drawPitchLines(Color(0x33FFFFFF), 0.34f, size.width * 0.26f) }
    )
}

/**
 * Ochiq (katta) o'yin kartasi. Fon: o'yin qo'shilganda yuklangan maydon rasmi.
 * Bosilganda o'yin sahifasi ochiladi.
 */
@Composable
fun FeaturedGameCard(
    game: Game,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    joined: Boolean = false
) {
    val status = game.status()
    val shape = RoundedCornerShape(30.dp)
    val (sub, subColor) = spotsText(game, joined, status)
    val fraction = if (game.maxPlayers > 0) (game.players.size.toFloat() / game.maxPlayers).coerceIn(0f, 1f) else 0f

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, GlassBorder, shape)
            .clickable(onClick = onClick)
    ) {
        if (game.images.isNotEmpty()) {
            SubcomposeAsyncImage(
                model = ImageStore.model(game.images.first()),
                contentDescription = game.venueName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
                loading = { FeaturedFallback() },
                error = { FeaturedFallback() }
            )
        } else {
            FeaturedFallback()
        }
        // Yozuvlar yaxshi o'qilishi uchun tepa va past qorayadi
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(Brush.verticalGradient(listOf(Color(0x59000000), Color.Transparent, Color(0xB3000000))))
        )

        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                GlassChip(
                    text = "${DateUtils.dayLabel(game.dateMillis)} · ${game.startTime}",
                    leading = { LimeDot() }
                )
                Spacer(modifier = Modifier.weight(1f))
                GlassChip(text = game.format)
            }
            Spacer(modifier = Modifier.height(78.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0x59000000))
                    .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(24.dp))
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = game.venueName,
                            color = TextPrimary,
                            fontSize = 21.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontStyle = FontStyle.Italic,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(priceText(game), color = LimeGreen, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (game.address.isNotBlank()) game.address else sub,
                        color = TextSecondary,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(7.dp)
                                .clip(CircleShape)
                                .background(Color(0x33FFFFFF))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(fraction)
                                    .fillMaxHeight()
                                    .clip(CircleShape)
                                    .background(Brush.horizontalGradient(listOf(LimeGreen, TealAccent)))
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            "${game.players.size}/${game.maxPlayers}",
                            color = TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (game.address.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(sub, color = subColor, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(Brush.verticalGradient(listOf(LimeButtonTop, LimeButtonBottom))),
                    contentAlignment = Alignment.Center
                ) {
                    TrebleIcon(IconPaths.ArrowForward, DarkText, size = 24.dp)
                }
            }
        }
    }
}

/** Ro'yxatdagi ixcham qator: chapda sana/vaqt bloki, o'rtada nom va tafsilot. */
@Composable
fun GameRowCard(
    game: Game,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    joined: Boolean = false
) {
    val status = game.status()
    val shape = RoundedCornerShape(26.dp)
    val today = DateUtils.dayLabel(game.dateMillis).equals("Bugun", ignoreCase = true)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.linearGradient(listOf(Color(0x2EFFFFFF), Color(0x0AFFFFFF))))
            .border(1.dp, Color(0x33FFFFFF), shape)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .width(72.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(if (today) LimeGreen.copy(alpha = 0.18f) else Color(0x1FFFFFFF))
                .border(1.dp, if (today) LimeGreen.copy(alpha = 0.55f) else Color(0x33FFFFFF), RoundedCornerShape(20.dp))
                .padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = DateUtils.dayLabel(game.dateMillis).uppercase(),
                color = if (today) LimeGreen else TextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1
            )
            Text(game.startTime, color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = game.venueName,
                color = TextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "${game.format} · ${game.players.size}/${game.maxPlayers} o'yinchi · ${priceText(game)}",
                color = TextSecondary,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        when {
            status == GameStatus.CANCELLED -> StatusPill("Bekor", StatusCancelBg, StatusCancelFg)
            joined -> TrebleIcon(IconPaths.Check, LimeGreen, size = 22.dp)
            game.spotsLeft == 0 -> StatusPill("To'lgan", StatusFullBg, StatusFullFg)
            else -> TrebleIcon(IconPaths.ChevronRight, TextSecondary, size = 22.dp)
        }
    }
}
