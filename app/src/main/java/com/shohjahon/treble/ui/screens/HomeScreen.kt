package com.shohjahon.treble.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shohjahon.treble.data.models.UserRole
import com.shohjahon.treble.data.repository.AuthRepository
import com.shohjahon.treble.data.repository.ChatRepository
import com.shohjahon.treble.notify.NavIntentState
import com.shohjahon.treble.ui.components.*
import com.shohjahon.treble.ui.theme.*

@OptIn(ExperimentalLayoutApi::class)
/** Kirgandan keyingi asosiy ekran: 5 ta tab (O'yinlar, Joylar, Chat, O'yinlarim/Boshqaruv, Profil). Orqaga tugmasi login sahifasiga qaytarmaydi. */
@Composable
fun HomeScreen(
    onOpenGame: (String) -> Unit,
    onAddGame: () -> Unit,
    onOpenPage: (String) -> Unit,
    onLoggedOut: () -> Unit,
    onOpenNotifications: () -> Unit = {},
    onOpenDm: (String, String) -> Unit = { _, _ -> }
) {
    val me = AuthRepository.currentUser ?: return
    val isAdmin = me.isManager   // bosh admin yoki ishchi admin (boshqaruv ko'rinishi)
    val isBoss = me.isBoss       // faqat bosh admin: o'yin qo'shish, to'lovlar, adminlar

    var tab by rememberSaveable { mutableStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var selectedDate by rememberSaveable { mutableStateOf(-1L) }

    // Bildirishnomaga bosib kirilsa (masalan, chat xabari), kerakli bo'lim ochiladi
    LaunchedEffect(NavIntentState.tab) {
        NavIntentState.tab?.let {
            tab = it.coerceIn(0, 4)
            NavIntentState.tab = null
        }
    }

    // Boshqa tabda "orqaga" bosilsa, avval birinchi tabga qaytadi
    BackHandler(enabled = tab != 0) { tab = 0 }

    Box(modifier = Modifier.fillMaxSize()) {
        BackgroundAmbientGlow()

        // Tablar orasida silliq (iOS kabi) o'tish: yon tomonga siljish + xiralashish
        AnimatedContent(
            targetState = tab,
            transitionSpec = {
                val forward = targetState > initialState
                val slideSpec = tween<androidx.compose.ui.unit.IntOffset>(durationMillis = 340, easing = FastOutSlowInEasing)
                (slideInHorizontally(animationSpec = slideSpec) { w -> if (forward) w / 4 else -w / 4 } +
                        fadeIn(animationSpec = tween(280))) togetherWith
                        (slideOutHorizontally(animationSpec = slideSpec) { w -> if (forward) -w / 4 else w / 4 } +
                                fadeOut(animationSpec = tween(180)))
            },
            label = "tabs",
            modifier = Modifier.fillMaxSize()
        ) { target ->
            when (target) {
                0 -> GamesTab(
                    me = me,
                    isAdmin = isAdmin,
                    canCreate = isBoss,
                    searchQuery = query,
                    onSearchChange = { query = it },
                    selectedDate = selectedDate,
                    onDateSelect = { selectedDate = it },
                    onOpenGame = onOpenGame,
                    onAddGame = onAddGame,
                    onOpenNotifications = onOpenNotifications
                )
                1 -> VenuesTab(
                    onShowVenueGames = { venueName ->
                        query = venueName
                        selectedDate = -1L
                        tab = 0
                    }
                )
                2 -> ChatTab(
                    me = me,
                    onOpenDm = onOpenDm
                )
                3 -> MyGamesTab(
                    me = me,
                    isAdmin = isAdmin,
                    isBoss = isBoss,
                    onOpenGame = onOpenGame,
                    onAddGame = onAddGame,
                    onGoToGames = { tab = 0 }
                )
                else -> ProfileTab(
                    me = me,
                    isAdmin = isAdmin,
                    isBoss = isBoss,
                    onOpenPage = onOpenPage,
                    onLoggedOut = onLoggedOut
                )
            }
        }

        val imeVisible = WindowInsets.isImeVisible
        if (!(tab == 2 && imeVisible)) FloatingBottomBar(
            selectedTab = tab,
            fourthLabel = if (isAdmin) "Boshqaruv" else "O'yinlarim",
            onSelect = { tab = it },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 14.dp)
        )
    }
}

/** Suzuvchi yumaloq pastki menyu. Tanlangan bo'lim ustidagi yashil kapsula silliq siljiydi. */
@Composable
private fun FloatingBottomBar(
    selectedTab: Int,
    fourthLabel: String,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val labels = listOf("O'yinlar", "Joylar", "Chat", fourthLabel, "Profil")
    val shape = RoundedCornerShape(36.dp)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(74.dp)
            .shadow(18.dp, shape, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(shape)
            .background(
                Brush.linearGradient(listOf(Color(0xE61C2C3A), Color(0xE60B1620)))
            )
            .border(1.dp, Color.White.copy(alpha = 0.14f), shape)
            .padding(horizontal = 8.dp, vertical = 8.dp)
    ) {
        val itemWidth = maxWidth / 5
        val indicatorX by animateDpAsState(
            targetValue = itemWidth * selectedTab,
            animationSpec = spring(dampingRatio = 0.78f, stiffness = 380f),
            label = "indicator"
        )

        Box(
            modifier = Modifier
                .offset(x = indicatorX)
                .width(itemWidth)
                .fillMaxHeight()
                .clip(RoundedCornerShape(28.dp))
                .background(LimeDeep)
                .border(1.dp, LimeGreen.copy(alpha = 0.45f), RoundedCornerShape(28.dp))
        )

        Row(modifier = Modifier.fillMaxSize()) {
            for (index in 0 until 5) {
                val selected = selectedTab == index
                val tint by animateColorAsState(
                    targetValue = if (selected) LimeGreen else TextSecondary,
                    animationSpec = tween(220),
                    label = "tint"
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onSelect(index) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    when (index) {
                        0 -> BallIcon(tint = tint, size = 24.dp)
                        1 -> TrebleIcon(IconPaths.Pin, tint, size = 24.dp)
                        2 -> Box {
                            TrebleIcon(IconPaths.Chat, tint, size = 24.dp)
                            if (ChatRepository.totalUnread > 0 && !selected) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .size(9.dp)
                                        .clip(androidx.compose.foundation.shape.CircleShape)
                                        .background(LimeGreen)
                                )
                            }
                        }
                        3 -> TrebleIcon(IconPaths.Ticket, tint, size = 24.dp)
                        else -> TrebleIcon(IconPaths.Person, tint, size = 24.dp)
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = labels[index],
                        color = tint,
                        fontSize = 11.sp,
                        fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
