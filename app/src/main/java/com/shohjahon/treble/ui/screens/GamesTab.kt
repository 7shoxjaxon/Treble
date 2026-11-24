package com.shohjahon.treble.ui.screens

import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.animation.animateContentSize
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import com.shohjahon.treble.data.models.Game
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shohjahon.treble.data.models.User
import com.shohjahon.treble.data.repository.GameRepository
import com.shohjahon.treble.data.repository.NotificationRepository
import com.shohjahon.treble.data.util.DateUtils
import com.shohjahon.treble.ui.components.*
import com.shohjahon.treble.ui.theme.*

/** Futbolchining eng yaqin o'yini va unga qancha vaqt qolgani. Har 30 soniyada yangilanadi. */
@Composable
private fun NextGameBanner(game: Game, onClick: () -> Unit) {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000L)
            now = System.currentTimeMillis()
        }
    }
    val left = game.startMillis - now
    val countdown = when {
        left <= 0L -> "O'yin boshlandi"
        left < 3_600_000L -> "${left / 60_000L + 1} daqiqadan keyin"
        left < 86_400_000L -> "${left / 3_600_000L} soat ${(left % 3_600_000L) / 60_000L} daq. dan keyin"
        else -> "${left / 86_400_000L} kundan keyin"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(LimeDeep)
            .border(1.dp, LimeGreen.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Keyingi o'yiningiz", color = LimeGreen, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text(
                text = game.venueName,
                color = TextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            Text(
                text = "${DateUtils.dayLabel(game.dateMillis)} · ${game.timeRange()}",
                color = TextSecondary,
                fontSize = 13.sp
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = countdown,
            color = LimeGreen,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/** Bosh sahifa tepasidagi qo'ng'iroqcha. O'qilmagan xabarlar soni ko'rsatiladi. */
@Composable
private fun NotificationBell(unread: Int, onClick: () -> Unit) {
    Box(modifier = Modifier.size(54.dp), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(50.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(Color(0x40FFFFFF), Color(0x10FFFFFF))))
                .border(1.dp, GlassBorder, CircleShape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            TrebleIcon(IconPaths.Bell, TextPrimary, size = 24.dp)
        }
        if (unread > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .defaultMinSize(minWidth = 18.dp, minHeight = 18.dp)
                    .clip(CircleShape)
                    .background(LimeGreen)
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (unread > 9) "9+" else unread.toString(),
                    color = DarkText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/** Asosiy tab: qidiruv, sana lentasi va o'yinlar ro'yxati. */
@Composable
fun GamesTab(
    me: User,
    isAdmin: Boolean,
    canCreate: Boolean,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    selectedDate: Long,
    onDateSelect: (Long) -> Unit,
    onOpenGame: (String) -> Unit,
    onAddGame: () -> Unit,
    onOpenNotifications: () -> Unit = {}
) {
    val now = System.currentTimeMillis()
    val q = searchQuery.trim()
    val days = (0..13).map { DateUtils.addDays(DateUtils.today(), it) }

    val visible = GameRepository.games
        .filter { game ->
            !game.isOver(now) &&
                    (isAdmin || !game.isCancelled) &&
                    (selectedDate == -1L || game.dateMillis == selectedDate) &&
                    (q.isEmpty() ||
                            game.venueName.contains(q, ignoreCase = true) ||
                            game.address.contains(q, ignoreCase = true))
        }
        .sortedBy { it.startMillis }

    // Ochiq (katta) karta: foydalanuvchi bosgani, aks holda eng yaqin o'yin
    var expandedId by rememberSaveable { mutableStateOf<String?>(null) }
    val activeId = visible.firstOrNull { it.id == expandedId }?.id ?: visible.firstOrNull()?.id

    val nextGame = if (isAdmin) null else GameRepository.games
        .filter { it.hasJoined(me.id) && !it.isCancelled && !it.isOver(now) }
        .minByOrNull { it.startMillis }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Salom, ${me.name.trim().substringBefore(' ').ifBlank { "futbolchi" }}",
                    color = TextSecondary,
                    fontSize = 15.sp
                )
                Text(
                    text = "Bugun o'ynaymizmi?",
                    color = TextPrimary,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontStyle = FontStyle.Italic,
                    maxLines = 1
                )
            }
            NotificationBell(
                unread = NotificationRepository.unreadCount,
                onClick = onOpenNotifications
            )
            if (canCreate) {
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .clip(CircleShape)
                        .background(Brush.verticalGradient(listOf(LimeButtonTop, LimeButtonBottom)))
                        .clickable(onClick = onAddGame),
                    contentAlignment = Alignment.Center
                ) {
                    TrebleIcon(IconPaths.Add, DarkText, size = 24.dp)
                }
            }
        }

        // Qidiruv
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .height(56.dp)
                .clip(CircleShape)
                .background(Brush.horizontalGradient(listOf(Color(0x2EFFFFFF), Color(0x12FFFFFF))))
                .border(1.dp, Color(0x40FFFFFF), CircleShape)
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TrebleIcon(IconPaths.Search, TextSecondary, size = 22.dp)
            Spacer(modifier = Modifier.width(12.dp))
            BasicTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                singleLine = true,
                cursorBrush = SolidColor(LimeGreen),
                textStyle = TextStyle(color = TextPrimary, fontSize = 16.sp),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (searchQuery.isEmpty()) {
                            Text("O'yin yoki maydon qidirish", color = TextSecondary, fontSize = 16.sp)
                        }
                        inner()
                    }
                },
                modifier = Modifier.weight(1f)
            )
            if (searchQuery.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .clickable { onSearchChange("") },
                    contentAlignment = Alignment.Center
                ) {
                    TrebleIcon(IconPaths.Close, TextSecondary, size = 16.dp)
                }
            }
        }

        // Sana lentasi
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item { SelectChip("Hammasi", selectedDate == -1L) { onDateSelect(-1L) } }
            items(days) { day ->
                SelectChip(DateUtils.chipLabel(day), selectedDate == day) { onDateSelect(day) }
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (nextGame != null) {
                item(key = "next_game") {
                    NextGameBanner(game = nextGame, onClick = { onOpenGame(nextGame.id) })
                }
            }
            if (visible.isEmpty()) {
                item {
                    val anyActive = GameRepository.games.any { !it.isOver(now) && (isAdmin || !it.isCancelled) }
                    EmptyState(
                        iconPath = if (anyActive) IconPaths.Search else IconPaths.Calendar,
                        title = if (anyActive) "Hech narsa topilmadi" else "Hozircha o'yin yo'q",
                        subtitle = when {
                            anyActive -> "Qidiruv yoki sanani o'zgartirib ko'ring."
                            canCreate -> "Yuqoridagi «Yangi» tugmasi orqali birinchi o'yinni e'lon qiling."
                            isAdmin -> "Sizga hali o'yin biriktirilmagan. Bosh admin biriktirgach shu yerda ko'rinadi."
                            else -> "Tashkilotchi yangi o'yin e'lon qilganda shu yerda ko'rinadi."
                        },
                        actionText = if (canCreate && !anyActive) "Yangi o'yin" else null,
                        onAction = if (canCreate && !anyActive) onAddGame else null
                    )
                }
            } else {
                item(key = "section_header") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Yaqin o'yinlar",
                            color = TextPrimary,
                            fontSize = 21.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontStyle = FontStyle.Italic,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "Barchasi",
                            color = LimeGreen,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onDateSelect(-1L); onSearchChange("") }
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                        )
                    }
                }
                itemsIndexed(visible, key = { _, g -> g.id }) { _, game ->
                    val joined = game.hasJoined(me.id)
                    // 1-bosish: karta ochiladi (rasm bilan). Ochiq kartani yana bossa: o'yin sahifasi.
                    Box(modifier = Modifier.animateContentSize()) {
                        if (game.id == activeId) {
                            FeaturedGameCard(game = game, joined = joined, onClick = { onOpenGame(game.id) })
                        } else {
                            GameRowCard(game = game, joined = joined, onClick = { expandedId = game.id })
                        }
                    }
                }
            }
        }
    }
}
