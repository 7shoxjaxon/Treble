package com.shohjahon.treble.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shohjahon.treble.data.models.Game
import com.shohjahon.treble.data.models.GameStatus
import com.shohjahon.treble.data.models.User
import com.shohjahon.treble.data.repository.GameRepository
import com.shohjahon.treble.data.util.formatPrice
import com.shohjahon.treble.ui.components.*
import com.shohjahon.treble.ui.theme.*

@Composable
private fun AdminStat(value: String, label: String, modifier: Modifier = Modifier) {
    SurfaceCard(modifier = modifier, contentPadding = PaddingValues(vertical = 14.dp, horizontal = 10.dp)) {
        Text(text = value, color = LimeGreen, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(text = label, color = TextSecondary, fontSize = 11.sp, maxLines = 2, lineHeight = 14.sp)
    }
}

/**
 * Futbolchi uchun: "O'yinlarim" (faol va tarix).
 * Admin uchun: "Boshqaruv" (statistika va barcha o'yinlar).
 */
@Composable
fun MyGamesTab(
    me: User,
    isAdmin: Boolean,
    isBoss: Boolean,
    onOpenGame: (String) -> Unit,
    onAddGame: () -> Unit,
    onGoToGames: () -> Unit
) {
    var segment by rememberSaveable { mutableStateOf(0) }
    val now = System.currentTimeMillis()

    val activeList: List<Game>
    val historyList: List<Game>
    if (isAdmin) {
        val all = GameRepository.games
        activeList = all.filter { !it.isOver(now) && !it.isCancelled }.sortedBy { it.startMillis }
        historyList = all.filter { it.isOver(now) || it.isCancelled }.sortedByDescending { it.startMillis }
    } else {
        val mine = GameRepository.gamesOf(me.id)
        activeList = mine
            .filter { it.hasJoined(me.id) && !it.isOver(now) && !it.isCancelled }
            .sortedBy { it.startMillis }
        historyList = mine.filter { g -> activeList.none { it.id == g.id } }
    }
    val shown = if (segment == 0) activeList else historyList

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        Text(
            text = if (isAdmin) "Boshqaruv" else "O'yinlarim",
            color = TextPrimary,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp)
        )

        if (isAdmin) {
            val activeBookings = activeList.sumOf { it.players.size }
            val paidTotal = GameRepository.games.sumOf { g -> g.players.count { it.paid && it.payMethod != "COUPON" } * g.price }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AdminStat("${activeList.size}", "Faol o'yinlar", Modifier.weight(1f))
                AdminStat("$activeBookings", "Faol bandlar", Modifier.weight(1f))
                // To'langan pullarni faqat bosh admin ko'radi
                if (isBoss) {
                    AdminStat(formatPrice(paidTotal), "To'langan, so'm", Modifier.weight(1f))
                }
            }
        } else {
            Spacer(modifier = Modifier.height(12.dp))
        }

        SegmentedTabs(
            options = listOf("Faol", "Tarix"),
            selectedIndex = segment,
            onSelect = { segment = it },
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 112.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (shown.isEmpty()) {
                item {
                    if (segment == 0) {
                        EmptyState(
                            iconPath = IconPaths.Ticket,
                            title = if (isAdmin) "Faol o'yin yo'q" else "Faol bandlar yo'q",
                            subtitle = when {
                                isBoss -> "Yangi o'yin e'lon qiling."
                                isAdmin -> "Sizga hali o'yin biriktirilmagan. Bosh admin biriktirgach shu yerda ko'rinadi."
                                else -> "O'yinni tanlab, joyingizni band qiling."
                            },
                            actionText = when {
                                isBoss -> "Yangi o'yin"
                                isAdmin -> null
                                else -> "O'yinlarni ko'rish"
                            },
                            onAction = when {
                                isBoss -> onAddGame
                                isAdmin -> null
                                else -> onGoToGames
                            }
                        )
                    } else {
                        EmptyState(
                            iconPath = IconPaths.Clock,
                            title = "Tarix bo'sh",
                            subtitle = "Yakunlangan va bekor qilingan o'yinlar shu yerda ko'rinadi."
                        )
                    }
                }
            } else {
                items(shown, key = { it.id }) { game ->
                    val joined = game.hasJoined(me.id)
                    val cancelledByMe = !isAdmin && !joined && game.cancelledBy.contains(me.id)
                    GameCard(
                        game = game,
                        joined = joined && !game.isOver(now) && !game.isCancelled,
                        statusOverride = if (cancelledByMe) GameStatus.CANCELLED else null,
                        onClick = { onOpenGame(game.id) }
                    )
                }
            }
        }
    }
}
