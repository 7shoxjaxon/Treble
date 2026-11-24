package com.shohjahon.treble.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shohjahon.treble.data.models.ChatConversation
import com.shohjahon.treble.data.models.ChatMessage
import com.shohjahon.treble.data.models.ChatPeer
import com.shohjahon.treble.data.models.User
import com.shohjahon.treble.data.repository.ChatRepository
import com.shohjahon.treble.data.util.DateUtils
import com.shohjahon.treble.ui.components.*
import com.shohjahon.treble.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ------------------------------------------------------------------ Umumiy yordamchilar (DmScreen ham ishlatadi)

internal fun chatTime(ms: Long): String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ms))

internal fun chatDayLabel(ms: Long): String {
    val day = DateUtils.startOfDay(ms)
    val today = DateUtils.today()
    return when (day) {
        today -> "Bugun"
        DateUtils.addDays(today, -1) -> "Kecha"
        else -> SimpleDateFormat("d.MM.yyyy", Locale.getDefault()).format(Date(ms))
    }
}

internal fun roleLabel(role: String): String? = when (role) {
    "ADMIN" -> "Admin"
    "STAFF" -> "Ishchi"
    else -> null
}

/** Kun o'zgargan joyda chiqadigan kichik sana yorlig'i. */
@Composable
internal fun ChatDaySeparator(ms: Long) {
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        Text(
            text = chatDayLabel(ms),
            color = TextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .clip(CircleShape)
                .background(Color(0x26FFFFFF))
                .padding(horizontal = 12.dp, vertical = 4.dp)
        )
    }
}

/**
 * Bitta xabar pufagi. O'zingiznikilar o'ngda (havorang), boshqalarniki chapda (shisha).
 * [showAvatar] = chap tomonda avatar ko'rsatiladimi (guruhda ha, shaxsiy chatda yo'q).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ChatBubbleRow(
    message: ChatMessage,
    mine: Boolean,
    showAuthor: Boolean,
    showAvatar: Boolean,
    onAvatarClick: () -> Unit = {},
    onLongPress: () -> Unit = {}
) {
    val bubbleShape = RoundedCornerShape(
        topStart = 20.dp,
        topEnd = 20.dp,
        bottomStart = if (mine) 20.dp else 6.dp,
        bottomEnd = if (mine) 6.dp else 20.dp
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (!mine && showAvatar) {
            if (showAuthor) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onAvatarClick)
                ) {
                    UserAvatar(name = message.senderName, avatar = message.senderAvatar, size = 34.dp)
                }
            } else {
                Spacer(modifier = Modifier.width(34.dp))
            }
            Spacer(modifier = Modifier.width(8.dp))
        }
        Column(
            modifier = Modifier
                .widthIn(max = 290.dp)
                .clip(bubbleShape)
                .then(
                    if (mine) Modifier.background(Brush.verticalGradient(listOf(LimeButtonTop, LimeButtonBottom)))
                    else Modifier
                        .background(Brush.linearGradient(listOf(Color(0x33FFFFFF), Color(0x14FFFFFF))))
                        .border(1.dp, Color(0x26FFFFFF), bubbleShape)
                )
                .combinedClickable(onClick = {}, onLongClick = onLongPress)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            if (!mine && showAuthor) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = message.senderName,
                        color = LimeGreen,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    roleLabel(message.senderRole)?.let {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = it,
                            color = DarkText,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(LimeGreen)
                                .padding(horizontal = 6.dp, vertical = 1.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
            }
            Text(
                text = message.text,
                color = if (mine) DarkText else TextPrimary,
                fontSize = 15.sp,
                lineHeight = 20.sp
            )
            Text(
                text = chatTime(message.createdAt),
                color = if (mine) DarkText.copy(alpha = 0.6f) else TextSecondary,
                fontSize = 10.sp,
                textAlign = TextAlign.End,
                modifier = Modifier.align(Alignment.End)
            )
        }
    }
}

/** Xabar yozish paneli: shisha maydon + yuborish tugmasi. */
@Composable
internal fun ChatInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    enabled: Boolean,
    bottomPadding: androidx.compose.ui.unit.Dp,
    placeholder: String = "Xabar yozing..."
) {
    val canSend = enabled && value.isNotBlank()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottomPadding),
        verticalAlignment = Alignment.Bottom
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 50.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(Brush.horizontalGradient(listOf(Color(0x33FFFFFF), Color(0x14FFFFFF))))
                .border(1.dp, Color(0x40FFFFFF), RoundedCornerShape(26.dp))
                .padding(horizontal = 18.dp, vertical = 13.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            BasicTextField(
                value = value,
                onValueChange = { if (it.length <= 1000) onValueChange(it) },
                enabled = enabled,
                maxLines = 4,
                cursorBrush = SolidColor(LimeGreen),
                textStyle = TextStyle(color = TextPrimary, fontSize = 16.sp),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (value.isEmpty()) Text(placeholder, color = TextSecondary, fontSize = 16.sp)
                        inner()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .size(50.dp)
                .clip(CircleShape)
                .background(
                    if (canSend) Brush.verticalGradient(listOf(LimeButtonTop, LimeButtonBottom))
                    else Brush.verticalGradient(listOf(Color(0x33FFFFFF), Color(0x1AFFFFFF)))
                )
                .clickable(enabled = canSend, onClick = onSend),
            contentAlignment = Alignment.Center
        ) {
            TrebleIcon(IconPaths.Send, if (canSend) DarkText else TextSecondary, size = 22.dp)
        }
    }
}

// ------------------------------------------------------------------ Chat tabi

/**
 * Pastki menyudagi "Chat" bo'limi. Birinchi ochilganda "Chatga qo'shilasizmi?" deb so'raydi (Ha / Yoq).
 * Qo'shilganlar uchun: "Umumiy" guruh va "Shaxsiy" xabarlar.
 */
@Composable
fun ChatTab(
    me: User,
    onOpenDm: (String, String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var section by rememberSaveable { mutableStateOf(0) } // 0 = Umumiy, 1 = Shaxsiy
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmLeave by remember { mutableStateOf(false) }
    var loadTry by remember { mutableStateOf(0) }

    LaunchedEffect(loadTry) { ChatRepository.refreshStatus() }

    fun setJoined(join: Boolean) {
        if (busy) return
        scope.launch {
            busy = true
            error = ChatRepository.setJoined(join)
            busy = false
        }
    }

    Box(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        when (ChatRepository.status) {
            "joined" -> ChatJoined(
                me = me,
                section = section,
                onSection = { section = it },
                onOpenDm = onOpenDm,
                onLeave = { confirmLeave = true }
            )
            "unknown" -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Chat yuklanmoqda...", color = TextSecondary, fontSize = 15.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Qayta urinish",
                        color = LimeGreen,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { loadTry++ }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
            else -> ChatNotJoined(
                declined = ChatRepository.status == "declined",
                busy = busy,
                error = error,
                onJoin = { setJoined(true) }
            )
        }

        // Birinchi ochilganda: javob berilmaguncha yopilmaydi
        if (ChatRepository.status == "none") {
            GlassDialog(
                title = "Chatga qo'shilasizmi?",
                text = "Chatda boshqa futbolchilar bilan umumiy guruhda yozishingiz va ularga shaxsiy xabar yuborishingiz mumkin. Qo'shilsangiz, ismingiz va rasmingiz chat a'zolariga ko'rinadi.",
                confirmText = "Ha",
                dismissText = "Yoq",
                onConfirm = { setJoined(true) },
                onDismiss = { setJoined(false) },
                dismissable = false
            )
        }
        if (confirmLeave) {
            GlassDialog(
                title = "Chatdan chiqasizmi?",
                text = "Chiqsangiz, guruhga yoza olmaysiz va shaxsiy xabarlar ko'rinmaydi. Xohlagan payt qayta qo'shilishingiz mumkin.",
                confirmText = "Ha",
                dismissText = "Yoq",
                onConfirm = { confirmLeave = false; setJoined(false) },
                onDismiss = { confirmLeave = false }
            )
        }
    }
}

@Composable
private fun ChatNotJoined(declined: Boolean, busy: Boolean, error: String?, onJoin: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(Color(0x40FFFFFF), Color(0x10FFFFFF))))
                .border(1.dp, GlassBorder, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            TrebleIcon(IconPaths.Chat, LimeGreen, size = 40.dp)
        }
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "Chat",
            color = TextPrimary,
            fontSize = 28.sp,
            fontWeight = FontWeight.ExtraBold,
            fontStyle = FontStyle.Italic
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (declined) "Siz chatga qo'shilmagansiz. Futbolchilar bilan gaplashish uchun qo'shiling."
            else "Futbolchilar bilan umumiy guruhda gaplashing va shaxsiy xabar yuboring.",
            color = TextSecondary,
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
            lineHeight = 21.sp
        )
        if (declined) {
            Spacer(modifier = Modifier.height(24.dp))
            GlassButton(text = if (busy) "Kuting..." else "Chatga qo'shilish", onClick = onJoin, arrow = !busy)
        }
        if (error != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(error, color = StatusCancelFg, fontSize = 13.sp, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun ChatJoined(
    me: User,
    section: Int,
    onSection: (Int) -> Unit,
    onOpenDm: (String, String) -> Unit,
    onLeave: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().imePadding()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 12.dp, top = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Chat",
                color = TextPrimary,
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                fontStyle = FontStyle.Italic,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "Chiqish",
                color = TextSecondary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onLeave)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SelectChip("Umumiy", section == 0) { onSection(0) }
            SelectChip(
                text = if (ChatRepository.dmUnread > 0) "Shaxsiy · ${ChatRepository.dmUnread}" else "Shaxsiy",
                selected = section == 1
            ) { onSection(1) }
        }
        if (section == 0) GroupChat(me = me, onOpenDm = onOpenDm) else DmList(me = me, onOpenDm = onOpenDm)
    }
}

// ------------------------------------------------------------------ Umumiy guruh

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GroupChat(me: User, onOpenDm: (String, String) -> Unit) {
    val scope = rememberCoroutineScope()
    val messages = remember { mutableStateListOf<ChatMessage>() }
    var loaded by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var deleteTarget by remember { mutableStateOf<ChatMessage?>(null) }
    var dmTarget by remember { mutableStateOf<ChatMessage?>(null) }
    var reportTarget by remember { mutableStateOf<ChatMessage?>(null) }
    var blockTarget by remember { mutableStateOf<ChatMessage?>(null) }
    var scrolledOnce by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val imeVisible = WindowInsets.isImeVisible

    // Yangi xabarlarni har 3 soniyada olib turadi
    LaunchedEffect(Unit) {
        while (true) {
            val after = messages.lastOrNull()?.createdAt ?: 0L
            val r = ChatRepository.loadGroup(after)
            if (r.ok) {
                r.data!!.forEach { m -> if (messages.none { it.id == m.id }) messages.add(m) }
                loaded = true
                error = null
            } else if (!loaded) {
                error = r.error
            }
            delay(3000)
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isEmpty()) return@LaunchedEffect
        val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
        if (!scrolledOnce || lastVisible >= messages.size - 3 || messages.last().senderId == me.id) {
            scrolledOnce = true
            listState.scrollToItem(messages.size - 1)
        }
    }
    LaunchedEffect(imeVisible) {
        if (imeVisible && messages.isNotEmpty()) listState.scrollToItem(messages.size - 1)
    }

    fun send() {
        val text = input.trim()
        if (text.isEmpty() || sending) return
        scope.launch {
            sending = true
            val r = ChatRepository.sendGroup(text)
            sending = false
            if (r.ok) {
                input = ""
                error = null
                if (messages.none { it.id == r.data!!.id }) messages.add(r.data!!)
            } else {
                error = r.error
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                !loaded && error == null -> Text(
                    "Yuklanmoqda...",
                    color = TextSecondary,
                    fontSize = 15.sp,
                    modifier = Modifier.align(Alignment.Center)
                )
                !loaded -> Text(
                    error ?: "",
                    color = StatusCancelFg,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).padding(32.dp)
                )
                messages.isEmpty() -> Text(
                    "Hali xabar yo'q.\nBirinchi bo'lib yozing!",
                    color = TextSecondary,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).padding(32.dp)
                )
                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    itemsIndexed(messages, key = { _, m -> m.id }) { index, m ->
                        val prev = messages.getOrNull(index - 1)
                        if (prev == null || DateUtils.startOfDay(prev.createdAt) != DateUtils.startOfDay(m.createdAt)) {
                            ChatDaySeparator(m.createdAt)
                        }
                        val mine = m.senderId == me.id
                        val sameAuthor = prev != null && prev.senderId == m.senderId &&
                                DateUtils.startOfDay(prev.createdAt) == DateUtils.startOfDay(m.createdAt)
                        ChatBubbleRow(
                            message = m,
                            mine = mine,
                            showAuthor = !sameAuthor,
                            showAvatar = true,
                            onAvatarClick = { if (!mine) dmTarget = m },
                            onLongPress = { if (mine || me.isBoss) deleteTarget = m else reportTarget = m }
                        )
                    }
                }
            }
        }
        if (error != null && loaded) {
            Text(error ?: "", color = StatusCancelFg, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 20.dp))
        }
        ChatInputBar(
            value = input,
            onValueChange = { input = it },
            onSend = { send() },
            enabled = !sending,
            bottomPadding = if (imeVisible) 8.dp else 104.dp
        )
    }

    deleteTarget?.let { m ->
        GlassDialog(
            title = "Xabarni o'chirasizmi?",
            text = m.text.take(120),
            confirmText = "Ha",
            dismissText = "Yoq",
            onConfirm = {
                deleteTarget = null
                scope.launch {
                    val err = ChatRepository.deleteMessage(m.id)
                    if (err == null) messages.removeAll { it.id == m.id } else error = err
                }
            },
            onDismiss = { deleteTarget = null }
        )
    }

    reportTarget?.let { m ->
        GlassDialog(
            title = "Shikoyat qilish",
            text = "${m.senderName}: «${m.text.take(100)}»\n\nBu xabar qoidalarga zid bo'lsa, adminlarga shikoyat yuboring.",
            confirmText = "Shikoyat",
            dismissText = "Bekor",
            onConfirm = {
                reportTarget = null
                scope.launch {
                    val err = ChatRepository.reportMessage(m.id)
                    if (err == null) blockTarget = m else error = err
                }
            },
            onDismiss = { reportTarget = null }
        )
    }
    blockTarget?.let { m ->
        GlassDialog(
            title = "Bloklaymizmi?",
            text = "Shikoyatingiz adminlarga yuborildi. ${m.senderName}ni bloklasangiz, uning xabarlarini ko'rmaysiz va u sizga shaxsiy xabar yoza olmaydi.",
            confirmText = "Bloklash",
            dismissText = "Yo'q",
            onConfirm = {
                blockTarget = null
                scope.launch {
                    val err = ChatRepository.blockUser(m.senderId)
                    if (err == null) messages.removeAll { it.senderId == m.senderId } else error = err
                }
            },
            onDismiss = { blockTarget = null }
        )
    }
    dmTarget?.let { m ->
        GlassDialog(
            title = m.senderName,
            text = "Shu foydalanuvchiga shaxsiy xabar yozasizmi?",
            confirmText = "Yozish",
            dismissText = "Bekor",
            onConfirm = {
                dmTarget = null
                onOpenDm(m.senderId, m.senderName)
            },
            onDismiss = { dmTarget = null }
        )
    }
}

// ------------------------------------------------------------------ Shaxsiy chatlar ro'yxati

@Composable
private fun DmList(me: User, onOpenDm: (String, String) -> Unit) {
    var conversations by remember { mutableStateOf<List<ChatConversation>?>(null) }
    var showPicker by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            val r = ChatRepository.conversations()
            if (r.ok) conversations = r.data
            ChatRepository.refreshStatus()
            delay(5000)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
            GlassSecondaryButton(text = "+ Yangi shaxsiy xabar", onClick = { showPicker = true })
        }
        val list = conversations
        when {
            list == null -> Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("Yuklanmoqda...", color = TextSecondary, fontSize = 15.sp)
            }
            list.isEmpty() -> Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    "Hali shaxsiy xabarlar yo'q.\nA'zoni tanlab birinchi bo'lib yozing.",
                    color = TextSecondary,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(32.dp)
                )
            }
            else -> LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(list, key = { _, c -> c.peer.id }) { _, c ->
                    ConversationRow(me = me, conversation = c) { onOpenDm(c.peer.id, c.peer.name) }
                }
            }
        }
    }

    if (showPicker) {
        MemberPicker(
            onPick = { peer ->
                showPicker = false
                onOpenDm(peer.id, peer.name)
            },
            onClose = { showPicker = false }
        )
    }
}

@Composable
private fun ConversationRow(me: User, conversation: ChatConversation, onClick: () -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    val last = conversation.last
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.linearGradient(listOf(Color(0x2EFFFFFF), Color(0x0AFFFFFF))))
            .border(1.dp, Color(0x33FFFFFF), shape)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        UserAvatar(name = conversation.peer.name, avatar = conversation.peer.avatar, size = 48.dp)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = conversation.peer.name,
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (last != null) {
                Text(
                    text = (if (last.senderId == me.id) "Siz: " else "") + last.text,
                    color = if (conversation.unread > 0) TextPrimary else TextSecondary,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            if (last != null) Text(chatTime(last.createdAt), color = TextSecondary, fontSize = 11.sp)
            if (conversation.unread > 0) {
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .defaultMinSize(minWidth = 20.dp, minHeight = 20.dp)
                        .clip(CircleShape)
                        .background(LimeGreen)
                        .padding(horizontal = 5.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (conversation.unread > 9) "9+" else conversation.unread.toString(),
                        color = DarkText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }
    }
}

/** Chatdagi a'zolardan birini tanlash oynasi (yangi shaxsiy xabar uchun). */
@Composable
private fun MemberPicker(onPick: (ChatPeer) -> Unit, onClose: () -> Unit) {
    var members by remember { mutableStateOf<List<ChatPeer>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val r = ChatRepository.members()
        if (r.ok) members = r.data else error = r.error
    }

    androidx.compose.ui.window.Dialog(onDismissRequest = onClose) {
        val shape = RoundedCornerShape(30.dp)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 520.dp)
                .clip(shape)
                .background(Color(0xF2101C2A))
                .border(1.dp, GlassBorder, shape)
                .padding(vertical = 20.dp)
        ) {
            Text(
                text = "Kimga yozasiz?",
                color = TextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                fontStyle = FontStyle.Italic,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            val list = members
            when {
                error != null -> Text(error ?: "", color = StatusCancelFg, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 24.dp))
                list == null -> Text("Yuklanmoqda...", color = TextSecondary, fontSize = 15.sp, modifier = Modifier.padding(horizontal = 24.dp))
                list.isEmpty() -> Text(
                    "Hozircha chatda boshqa a'zo yo'q.",
                    color = TextSecondary,
                    fontSize = 15.sp,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
                else -> LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                    itemsIndexed(list, key = { _, p -> p.id }) { _, peer ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(peer) }
                                .padding(horizontal = 24.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            UserAvatar(name = peer.name, avatar = peer.avatar, size = 40.dp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = peer.name,
                                color = TextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            roleLabel(peer.role)?.let {
                                Text(it, color = LimeGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Box(modifier = Modifier.padding(horizontal = 24.dp)) {
                GlassSecondaryButton(text = "Yopish", onClick = onClose)
            }
        }
    }
}
