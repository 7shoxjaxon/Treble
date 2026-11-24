package com.shohjahon.treble.ui.screens

import com.shohjahon.treble.ui.components.auroraBackground
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shohjahon.treble.data.models.Game
import com.shohjahon.treble.data.models.GameStatus
import com.shohjahon.treble.data.models.Player
import com.shohjahon.treble.data.models.User
import com.shohjahon.treble.data.models.UserRole
import com.shohjahon.treble.data.repository.AuthRepository
import com.shohjahon.treble.data.repository.CouponRepository
import com.shohjahon.treble.data.repository.GameRepository
import com.shohjahon.treble.data.util.DateUtils
import com.shohjahon.treble.data.util.formatPrice
import com.shohjahon.treble.ui.components.*
import com.shohjahon.treble.ui.theme.*

private const val DEFAULT_RULES =
    "• O'yin boshlanishidan 10 daqiqa oldin keling.\n" +
            "• Sport poyabzali kiying (shipovkasiz bo'lsa yaxshi).\n" +
            "• Hurmat va o'yin odobiga rioya qiling.\n" +
            "• Kela olmasangiz, bandni oldindan bekor qiling."

private fun payMethodLabel(method: String): String = when (method) {
    "CARD" -> "Kartaga o'tkazma"
    "COUPON" -> "Kupon"
    else -> "Maydonda to'laydi"
}

@Composable
fun GameDetailScreen(
    gameId: String,
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    onCopy: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val game = GameRepository.gameById(gameId)
    val me = AuthRepository.currentUser
    if (game == null || me == null) {
        Box(modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark))
        return
    }

    val context = LocalContext.current
    val isAdmin = me.isBoss          // bosh admin: hamma huquq
    val isStaff = me.isStaff         // ishchi admin: faqat davomat va "o'yin yakunlandi"
    val isManager = me.isManager     // ikkalasi
    val status = game.status()
    val joined = game.hasJoined(me.id)

    var showBooking by remember { mutableStateOf(false) }
    var showPlayers by remember { mutableStateOf(false) }
    var showCancelBooking by remember { mutableStateOf(false) }
    var showCancelGame by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var showAllComments by remember { mutableStateOf(false) }
    var commentText by remember { mutableStateOf("") }
    var showTeams by remember { mutableStateOf(false) }
    var showAddGuest by remember { mutableStateOf(false) }
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    fun toast(message: String) = Toast.makeText(context, message, Toast.LENGTH_SHORT).show()

    /** Serverga so'rov yuboradi: xato bo'lsa xabar ko'rsatadi, muvaffaqiyatda [success] xabarini va [onDone] ni bajaradi. */
    fun act(success: String? = null, onDone: () -> Unit = {}, block: suspend () -> String?) {
        scope.launch {
            val error = block()
            if (error != null) {
                toast(error)
            } else {
                if (success != null) toast(success)
                onDone()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .auroraBackground()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            HeroGallery(
                game = game,
                onBack = onBack,
                onShare = { shareGame(context, game) }
            )

            Column(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 110.dp)
            ) {
                Text(
                    text = game.venueName,
                    color = TextPrimary,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = game.format,
                        color = LimeGreen,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(LimeDeep)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                    GameStatusPill(status)
                    if (joined && status != GameStatus.CANCELLED) {
                        StatusPill("Siz band qilgansiz", LimeDeep, LimeGreen)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TrebleIcon(IconPaths.Calendar, TextSecondary, size = 18.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${DateUtils.dateWithWeekday(game.dateMillis)}, ${game.timeRange()} · ${game.durationMin} daqiqa",
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                if (!game.isOver() && !game.isCancelled) {
                    Text(
                        text = "+ Taqvimga qo'shish",
                        color = LimeGreen,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .padding(top = 6.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { addToCalendar(context, game) }
                            .padding(vertical = 4.dp, horizontal = 2.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Joylashuv
                SurfaceCard(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { openMap(context, game) },
                    contentPadding = PaddingValues(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Surface2),
                            contentAlignment = Alignment.Center
                        ) {
                            TrebleIcon(IconPaths.Pin, LimeGreen, size = 22.dp)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = game.address.ifBlank { game.venueName },
                                color = TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Xaritada ochish",
                                color = LimeGreen,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        TrebleIcon(IconPaths.ChevronRight, TextSecondary, size = 22.dp)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // O'yinchilar
                SurfaceCard(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { showPlayers = true },
                    contentPadding = PaddingValues(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "O'yinchilar · ${game.players.size}",
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        TrebleIcon(IconPaths.ChevronRight, TextSecondary, size = 22.dp)
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    if (game.players.isEmpty()) {
                        Text(
                            text = "Hali hech kim band qilmagan. Birinchi bo'ling!",
                            color = TextSecondary,
                            fontSize = 13.sp
                        )
                    } else {
                        AvatarStack(people = game.players.map { it.name to it.avatar })
                        Spacer(modifier = Modifier.height(6.dp))
                        val first = game.players.take(3).joinToString(", ") { it.name.substringBefore(' ') }
                        val rest = game.players.size - 3
                        Text(
                            text = if (rest > 0) "$first va yana $rest o'yinchi" else first,
                            color = TextSecondary,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Narx / format / band
                SurfaceCard(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 14.dp, horizontal = 8.dp)
                ) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        StatCell(
                            value = if (game.price == 0) "Bepul" else formatPrice(game.price),
                            label = "so'm · o'yinchi",
                            modifier = Modifier.weight(1f)
                        )
                        StatCell(value = game.format, label = "format", modifier = Modifier.weight(1f))
                        StatCell(
                            value = "${game.players.size}/${game.maxPlayers}",
                            label = "band",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Bekor qilish qoidasi
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(LimeDeep)
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(LimeGreen.copy(alpha = 0.13f)),
                        contentAlignment = Alignment.Center
                    ) {
                        TrebleIcon(IconPaths.Shield, LimeGreen, size = 20.dp)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Bandni bekor qilish",
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (game.freeCancelHours > 0)
                                "O'yin boshlanishidan ${game.freeCancelHours} soat oldin bekor qilish mumkin."
                            else
                                "Band qilingandan keyin bekor qilib bo'lmaydi.",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            lineHeight = 17.sp
                        )
                    }
                }

                // Izohlar
                Spacer(modifier = Modifier.height(22.dp))
                val sortedComments = game.comments.sortedByDescending { it.createdAt }
                SectionTitle(
                    title = "Izohlar${if (sortedComments.isNotEmpty()) " · ${sortedComments.size}" else ""}",
                    trailing = if (sortedComments.size > 2) (if (showAllComments) "Yig'ish" else "Barchasi") else null,
                    onTrailing = { showAllComments = !showAllComments }
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (sortedComments.isEmpty()) {
                    Text(
                        text = "Hali izoh yo'q — birinchi bo'ling",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Surface1)
                            .padding(14.dp)
                    )
                } else {
                    val shown = if (showAllComments) sortedComments else sortedComments.take(2)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        shown.forEach { c ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Surface1)
                                    .padding(12.dp)
                            ) {
                                UserAvatar(name = c.userName, avatar = c.avatar, size = 32.dp)
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = c.userName,
                                            color = TextPrimary,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = DateUtils.dateTime(c.createdAt),
                                            color = TextSecondary,
                                            fontSize = 11.sp
                                        )
                                    }
                                    Text(
                                        text = c.text,
                                        color = TextPrimary.copy(alpha = 0.9f),
                                        fontSize = 13.sp,
                                        lineHeight = 18.sp
                                    )
                                    if (isAdmin || c.userId == me.id) {
                                        Text(
                                            text = "O'chirish",
                                            color = ErrorRed.copy(alpha = 0.85f),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier
                                                .padding(top = 4.dp)
                                                .clickable { act { GameRepository.deleteComment(game.id, c.id) } }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                val sendComment: () -> Unit = {
                    if (commentText.isNotBlank()) {
                        val text = commentText
                        act(onDone = { commentText = "" }) { GameRepository.addComment(game.id, text) }
                    }
                }
                TrebleTextField(
                    label = "Izoh qoldirish",
                    value = commentText,
                    onValueChange = { commentText = it.take(300) },
                    placeholder = "Izoh yozing...",
                    imeAction = ImeAction.Send,
                    onDone = { sendComment() }
                )
                Spacer(modifier = Modifier.height(8.dp))
                DialogButton(
                    text = "Yuborish",
                    onClick = { sendComment() },
                    modifier = Modifier.align(Alignment.End).width(130.dp),
                    enabled = commentText.isNotBlank()
                )

                // O'yin haqida
                if (game.description.isNotBlank()) {
                    Spacer(modifier = Modifier.height(22.dp))
                    SectionTitle("O'yin haqida")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = game.description,
                        color = TextPrimary.copy(alpha = 0.9f),
                        fontSize = 14.sp,
                        lineHeight = 21.sp
                    )
                }

                Spacer(modifier = Modifier.height(22.dp))
                SectionTitle("Qoidalar")
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = game.rules.ifBlank { DEFAULT_RULES },
                    color = TextPrimary.copy(alpha = 0.9f),
                    fontSize = 14.sp,
                    lineHeight = 21.sp
                )

                // Admin boshqaruvi
                if (isManager) {
                  if (isAdmin) {
                    Spacer(modifier = Modifier.height(26.dp))
                    SectionTitle("Boshqaruv")
                    Text(
                        text = if (game.staff.isEmpty()) {
                            "Ishchi admin biriktirilmagan"
                        } else {
                            "Ishchi admin: " + game.staff.joinToString(", ") { it.name }
                        },
                        color = TextSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        DialogButton(
                            text = if (game.isFinished) "Tugallashni qaytarish" else "O'yinni tugallash",
                            onClick = { act { GameRepository.setFinished(game.id, !game.isFinished) } },
                            modifier = Modifier.weight(1f)
                        )
                        DialogButton(
                            text = "O'chirish",
                            onClick = { showDelete = true },
                            modifier = Modifier.weight(1f),
                            danger = true
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        DialogButton(
                            text = "Keyingi haftaga nusxa",
                            onClick = { onCopy(game.id) },
                            modifier = Modifier.weight(1f)
                        )
                        DialogButton(
                            text = "Jamoalar tuzish",
                            onClick = { showTeams = true },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        DialogButton(
                            text = "Ro'yxatni ulashish",
                            onClick = { sharePlayersList(context, game) },
                            modifier = Modifier.weight(1f)
                        )
                        DialogButton(
                            text = "+ O'yinchi qo'shish",
                            onClick = { showAddGuest = true },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))
                  } else {
                    Spacer(modifier = Modifier.height(26.dp))
                  }
                    val came = game.players.count { it.isAttended == true }
                    val missed = game.players.count { it.isAttended == false }
                    val paid = game.players.count { it.paid }
                    SectionTitle("Band qilganlar va davomat")
                    Text(
                        text = if (isAdmin) {
                            "Keldi: $came · Kelmadi: $missed · To'lagan: $paid/${game.players.size}"
                        } else {
                            "Keldi: $came · Kelmadi: $missed"
                        },
                        color = TextSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
                    )
                    if (game.players.isEmpty()) {
                        Text("Hozircha hech kim band qilmagan.", color = TextSecondary, fontSize = 13.sp)
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            game.players.forEach { p ->
                                AdminPlayerRow(
                                    player = p,
                                    showPayment = isManager && game.price > 0,
                                    canRemove = isAdmin,
                                    onAttendance = { value -> act { GameRepository.markAttendance(game.id, p.id, value) } },
                                    onPaid = { value -> act { GameRepository.setPaid(game.id, p.id, value) } },
                                    onCall = { dialNumber(context, p.phone) },
                                    onRemove = {
                                        act("${p.name} ro'yxatdan chiqarildi") { GameRepository.removePlayer(game.id, p.id) }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Pastki tugma paneli
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(BackgroundDark)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            when {
                isAdmin -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DialogButton(
                        text = "Tahrirlash",
                        onClick = { onEdit(game.id) },
                        modifier = Modifier.weight(1f),
                        primary = true
                    )
                    DialogButton(
                        text = if (game.isCancelled) "Tiklash" else "Bekor qilish",
                        onClick = {
                            if (game.isCancelled) {
                                act("O'yin tiklandi") { GameRepository.setCancelled(game.id, false) }
                            } else {
                                showCancelGame = true
                            }
                        },
                        modifier = Modifier.weight(1f),
                        danger = !game.isCancelled
                    )
                }
                isStaff -> DialogButton(
                    text = if (game.isFinished) "Tugallashni qaytarish" else "O'yinni tugallash",
                    onClick = { act { GameRepository.setFinished(game.id, !game.isFinished) } },
                    modifier = Modifier.fillMaxWidth(),
                    primary = !game.isFinished
                )
                joined && status != GameStatus.FINISHED -> DialogButton(
                    text = "Bandni bekor qilish",
                    onClick = { showCancelBooking = true },
                    modifier = Modifier.fillMaxWidth(),
                    danger = true
                )
                status == GameStatus.OPEN -> DialogButton(
                    text = if (game.price == 0) "Band qilish · Bepul" else "Band qilish · ${formatPrice(game.price)} so'm",
                    onClick = { showBooking = true },
                    modifier = Modifier.fillMaxWidth(),
                    primary = true
                )
                else -> DialogButton(
                    text = when (status) {
                        GameStatus.FULL -> "Joy qolmadi"
                        GameStatus.CANCELLED -> "O'yin bekor qilingan"
                        else -> "O'yin yakunlangan"
                    },
                    onClick = {},
                    modifier = Modifier.fillMaxWidth(),
                    enabled = false
                )
            }
        }
    }

    // ---- Dialoglar ----
    if (showBooking) {
        BookingDialog(
            game = game,
            me = me,
            onDismiss = { showBooking = false },
            onResult = { error ->
                if (error == null) {
                    showBooking = false
                    toast("O'yin band qilindi ✓. Boshlanishidan 2 soat, 1 soat va 30 daqiqa oldin eslatamiz")
                    if (Build.VERSION.SDK_INT >= 33 &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED
                    ) {
                        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                } else {
                    toast(error)
                }
            }
        )
    }
    if (showPlayers) {
        PlayersDialog(game = game, me = me, isAdmin = isManager, onDismiss = { showPlayers = false })
    }
    if (showTeams) {
        TeamsDialog(game = game, onDismiss = { showTeams = false })
    }
    if (showAddGuest) {
        AddGuestDialog(
            game = game,
            onDismiss = { showAddGuest = false },
            onAdded = {
                showAddGuest = false
                toast("O'yinchi qo'shildi")
            }
        )
    }
    if (showCancelBooking) {
        ConfirmDialog(
            title = "Bandni bekor qilish",
            message = "${DateUtils.dayLabel(game.dateMillis)}, ${game.startTime} dagi o'yinga bandingiz bekor qilinadi. Ishlatilgan kupon bo'lsa qaytariladi.",
            confirmText = "Bekor qilish",
            danger = true,
            onConfirm = {
                showCancelBooking = false
                act("Band bekor qilindi") { GameRepository.cancelBooking(game.id) }
            },
            onDismiss = { showCancelBooking = false }
        )
    }
    if (showCancelGame) {
        ConfirmDialog(
            title = "O'yinni bekor qilish",
            message = "O'yin bekor qilinadi va band qilganlar buni ko'radi. Keyin uni qayta tiklash mumkin.",
            confirmText = "Bekor qilish",
            danger = true,
            onConfirm = {
                showCancelGame = false
                act("O'yin bekor qilindi") { GameRepository.setCancelled(game.id, true) }
            },
            onDismiss = { showCancelGame = false }
        )
    }
    if (showDelete) {
        ConfirmDialog(
            title = "O'yinni o'chirish",
            message = "\"${game.venueName}\" o'yini, rasmlari, izohlari va ${game.players.size} ta o'yinchi ro'yxati butunlay o'chiriladi.",
            confirmText = "O'chirish",
            danger = true,
            onConfirm = {
                showDelete = false
                act("O'yin o'chirildi", onDone = { onBack() }) { GameRepository.deleteGame(game.id) }
            },
            onDismiss = { showDelete = false }
        )
    }
}

// ------------------------------------------------------------------ Qismlar

@Composable
private fun HeroGallery(game: Game, onBack: () -> Unit, onShare: () -> Unit) {
    val count = game.images.size
    val pagerState = rememberPagerState { maxOf(count, 1) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp)
    ) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            GameImage(path = game.images.getOrNull(page), modifier = Modifier.fillMaxSize())
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp)
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent)))
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconCircleButton(IconPaths.ArrowBack, onBack, background = Color.Black.copy(alpha = 0.45f), tint = Color.White)
            IconCircleButton(IconPaths.Share, onShare, background = Color.Black.copy(alpha = 0.45f), tint = Color.White)
        }
        if (count > 1) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 36.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                for (i in 0 until count) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(if (i == pagerState.currentPage) Color.White else Color.White.copy(alpha = 0.4f))
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(24.dp)
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(BackgroundDark)
        )
    }
}

@Composable
private fun SectionTitle(title: String, trailing: String? = null, onTrailing: () -> Unit = {}) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = title, color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        if (trailing != null) {
            Text(
                text = trailing,
                color = LimeGreen,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onTrailing)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
private fun StatCell(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(text = label, color = TextSecondary, fontSize = 11.sp, maxLines = 1)
    }
}

@Composable
private fun SmallToggle(text: String, selected: Boolean, selectedBg: Color, selectedFg: Color, onClick: () -> Unit) {
    Text(
        text = text,
        color = if (selected) selectedFg else TextPrimary,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) selectedBg else Surface2)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    )
}

@Composable
private fun AdminPlayerRow(
    player: Player,
    showPayment: Boolean,
    canRemove: Boolean,
    onAttendance: (Boolean?) -> Unit,
    onPaid: (Boolean) -> Unit,
    onCall: () -> Unit,
    onRemove: () -> Unit
) {
    SurfaceCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            UserAvatar(name = player.name, avatar = player.avatar, size = 38.dp)
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (player.isGuest) "${player.name} · qo'lda qo'shilgan" else player.name,
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (player.phone.isNotBlank()) {
                    Text(
                        text = player.phone,
                        color = LimeGreen,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.clickable(onClick = onCall)
                    )
                }
            }
            if (canRemove) {
                Text(
                    text = "Chiqarish",
                    color = ErrorRed.copy(alpha = 0.9f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onRemove)
                        .padding(6.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            SmallToggle("✓ Keldi", player.isAttended == true, StatusOpenBg, StatusOpenFg) {
                onAttendance(if (player.isAttended == true) null else true)
            }
            SmallToggle("✕ Kelmadi", player.isAttended == false, StatusCancelBg, StatusCancelFg) {
                onAttendance(if (player.isAttended == false) null else false)
            }
            if (showPayment) {
                SmallToggle(if (player.paid) "To'langan" else "To'lanmagan", player.paid, StatusOpenBg, StatusOpenFg) {
                    onPaid(!player.paid)
                }
            }
        }
        if (showPayment) {
            Text(
                text = "To'lov usuli: ${payMethodLabel(player.payMethod)}",
                color = TextSecondary,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}

@Composable
private fun PlayersDialog(game: Game, me: User, isAdmin: Boolean, onDismiss: () -> Unit) {
    val context = LocalContext.current
    TrebleDialog(title = "Band qilganlar · ${game.players.size}", onDismiss = onDismiss) {
        if (game.players.isEmpty()) {
            Text("Hali hech kim band qilmagan.", color = TextSecondary, fontSize = 14.sp)
        } else {
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                game.players.forEachIndexed { index, p ->
                    val isMe = p.id == me.id
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isMe) LimeDeep else Surface2)
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${index + 1}",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            modifier = Modifier.width(24.dp)
                        )
                        UserAvatar(name = p.name, avatar = p.avatar, size = 34.dp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isMe) "${p.name} (Siz)" else p.name,
                                color = if (isMe) LimeGreen else TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (isAdmin && p.phone.isNotBlank()) {
                                Text(
                                    text = p.phone,
                                    color = LimeGreen,
                                    fontSize = 12.sp,
                                    modifier = Modifier.clickable { dialNumber(context, p.phone) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BookingDialog(
    game: Game,
    me: User,
    onDismiss: () -> Unit,
    onResult: (String?) -> Unit
) {
    val coupons = CouponRepository.available(me.id)
    var method by remember { mutableStateOf("CASH") }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    data class Option(val key: String, val title: String, val desc: String)

    val options = ArrayList<Option>()
    if (game.price == 0) {
        options.add(Option("CASH", "Bepul o'yin", "To'lov talab qilinmaydi"))
    } else {
        options.add(Option("CASH", "Maydonda to'lash", "O'yin kuni tashkilotchiga to'laysiz"))
        if (game.payInfo.isNotBlank()) {
            options.add(Option("CARD", "Kartaga o'tkazma", game.payInfo))
        }
        if (coupons.isNotEmpty()) {
            options.add(Option("COUPON", "Bepul o'yin kuponi", "Mavjud kuponlar: ${coupons.size}"))
        }
    }

    TrebleDialog(title = "Band qilish", onDismiss = onDismiss) {
        Text(
            text = game.venueName,
            color = TextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "${DateUtils.dateWithWeekday(game.dateMillis)}, ${game.timeRange()}",
            color = TextSecondary,
            fontSize = 13.sp
        )
        Spacer(modifier = Modifier.height(14.dp))
        Text("To'lov usuli", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(8.dp))

        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.forEach { option ->
                val selected = method == option.key
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (selected) LimeDeep else Surface2)
                        .border(
                            1.dp,
                            if (selected) LimeGreen.copy(alpha = 0.6f) else Outline,
                            RoundedCornerShape(14.dp)
                        )
                        .clickable { method = option.key }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .border(2.dp, if (selected) LimeGreen else TextSecondary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (selected) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(LimeGreen)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(option.title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text(option.desc, color = TextSecondary, fontSize = 12.sp, lineHeight = 16.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Jami", color = TextSecondary, fontSize = 14.sp)
            Text(
                text = if (game.price == 0 || method == "COUPON") "Bepul" else "${formatPrice(game.price)} so'm",
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DialogButton("Yopish", onDismiss, Modifier.weight(1f))
            DialogButton(
                text = if (loading) "Kuting..." else "Tasdiqlash",
                onClick = {
                    if (!loading) {
                        loading = true
                        scope.launch {
                            val error = GameRepository.book(game.id, method)
                            loading = false
                            onResult(error)
                        }
                    }
                },
                modifier = Modifier.weight(1f),
                primary = true
            )
        }
    }
}

@Composable
private fun TeamColumn(title: String, names: List<String>, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Surface2)
            .padding(12.dp)
    ) {
        Text(title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        names.forEachIndexed { i, n ->
            Text(
                text = "${i + 1}. $n",
                color = TextPrimary.copy(alpha = 0.9f),
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(vertical = 2.dp)
            )
        }
    }
}

/** Band qilganlarni tasodifiy ikki jamoaga bo'ladi ("Kelmadi" deb belgilanganlar kirmaydi). */
@Composable
private fun TeamsDialog(game: Game, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val names = game.players.filter { it.isAttended != false }.map { it.name }
    var seed by remember { mutableStateOf(System.nanoTime()) }
    val shuffled = remember(seed, names) { names.shuffled(kotlin.random.Random(seed)) }
    val half = (shuffled.size + 1) / 2
    val teamA = shuffled.take(half)
    val teamB = shuffled.drop(half)

    TrebleDialog(title = "Jamoalar", onDismiss = onDismiss) {
        if (names.size < 2) {
            Text(
                text = "Jamoa tuzish uchun kamida 2 ta o'yinchi kerak.",
                color = TextSecondary,
                fontSize = 14.sp
            )
        } else {
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TeamColumn("🟢 A jamoa", teamA, Modifier.weight(1f))
                    TeamColumn("🔵 B jamoa", teamB, Modifier.weight(1f))
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DialogButton(
                    text = "Qayta aralashtirish",
                    onClick = { seed = System.nanoTime() },
                    modifier = Modifier.weight(1f)
                )
                DialogButton(
                    text = "Ulashish",
                    onClick = { shareTeams(context, game, teamA, teamB) },
                    modifier = Modifier.weight(1f),
                    primary = true
                )
            }
        }
    }
}

/** Ilovada akkaunti yo'q o'yinchini (telefon/Telegram orqali yozilgan) qo'lda qo'shish. */
@Composable
private fun AddGuestDialog(game: Game, onDismiss: () -> Unit, onAdded: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    TrebleDialog(title = "O'yinchi qo'shish", onDismiss = onDismiss) {
        Text(
            text = "Ilovadan foydalanmaydigan, telefon yoki Telegram orqali yozilgan o'yinchini qo'lda qo'shing.",
            color = TextSecondary,
            fontSize = 13.sp,
            lineHeight = 18.sp
        )
        Spacer(modifier = Modifier.height(12.dp))
        TrebleTextField(
            label = "Ism",
            value = name,
            onValueChange = { name = it.take(40); error = null },
            placeholder = "Masalan: Sardor"
        )
        Spacer(modifier = Modifier.height(10.dp))
        TrebleTextField(
            label = "Telefon (ixtiyoriy)",
            value = phone,
            onValueChange = { phone = it.take(20); error = null },
            placeholder = "+998 90 123 45 67",
            keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone,
            imeAction = ImeAction.Done
        )
        if (error != null) {
            Spacer(modifier = Modifier.height(8.dp))
            ErrorText(error)
        }
        Spacer(modifier = Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DialogButton("Bekor qilish", onDismiss, Modifier.weight(1f))
            DialogButton(
                text = if (loading) "Kuting..." else "Qo'shish",
                onClick = {
                    if (!loading) {
                        loading = true
                        scope.launch {
                            val result = GameRepository.addGuest(game.id, name, phone)
                            loading = false
                            if (result == null) {
                                onAdded()
                            } else {
                                error = result
                            }
                        }
                    }
                },
                modifier = Modifier.weight(1f),
                primary = true
            )
        }
    }
}
