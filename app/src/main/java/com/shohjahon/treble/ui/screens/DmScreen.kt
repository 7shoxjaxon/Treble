package com.shohjahon.treble.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shohjahon.treble.data.models.ChatMessage
import com.shohjahon.treble.data.models.ChatPeer
import com.shohjahon.treble.data.repository.AuthRepository
import com.shohjahon.treble.data.repository.ChatRepository
import com.shohjahon.treble.data.util.DateUtils
import com.shohjahon.treble.ui.components.*
import com.shohjahon.treble.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Bitta odam bilan shaxsiy chat. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DmScreen(
    peerId: String,
    peerName: String,
    onBack: () -> Unit
) {
    val me = AuthRepository.currentUser ?: return
    val scope = rememberCoroutineScope()
    val messages = remember { mutableStateListOf<ChatMessage>() }
    var peer by remember { mutableStateOf(ChatPeer(id = peerId, name = peerName)) }
    var peerInChat by remember { mutableStateOf(true) }
    var loaded by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var deleteTarget by remember { mutableStateOf<ChatMessage?>(null) }
    var reportTarget by remember { mutableStateOf<ChatMessage?>(null) }
    var blockTarget by remember { mutableStateOf<ChatMessage?>(null) }
    var scrolledOnce by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val imeVisible = WindowInsets.isImeVisible

    LaunchedEffect(peerId) {
        var firstLoad = true
        while (true) {
            val after = messages.lastOrNull()?.createdAt ?: 0L
            val r = ChatRepository.loadDm(peerId, after)
            if (r.ok) {
                val d = r.data!!
                peer = d.peer
                peerInChat = d.peerInChat
                d.messages.forEach { m -> if (messages.none { it.id == m.id }) messages.add(m) }
                loaded = true
                error = null
                if (firstLoad) {
                    firstLoad = false
                    ChatRepository.refreshStatus() // o'qilmagan sonlari yangilansin
                }
            } else if (!loaded) {
                error = r.error
            }
            delay(2500)
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
            val r = ChatRepository.sendDm(peerId, text)
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .auroraBackground()
            .statusBarsPadding()
            .imePadding()
    ) {
        // Tepa: orqaga + avatar + ism
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(Color(0x40FFFFFF), Color(0x10FFFFFF))))
                    .border(1.dp, GlassBorder, CircleShape)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                TrebleIcon(IconPaths.ArrowBack, TextPrimary, size = 20.dp)
            }
            Spacer(modifier = Modifier.width(12.dp))
            UserAvatar(name = peer.name, avatar = peer.avatar, size = 42.dp)
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = peer.name,
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                roleLabel(peer.role)?.let { Text(it, color = LimeGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
            }
        }

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
                    "Hali xabar yo'q.\n${peer.name}ga birinchi bo'lib yozing!",
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
                        ChatBubbleRow(
                            message = m,
                            mine = mine,
                            showAuthor = false,
                            showAvatar = false,
                            onLongPress = { if (mine) deleteTarget = m else reportTarget = m }
                        )
                    }
                }
            }
        }

        if (!peerInChat) {
            Text(
                text = "Bu foydalanuvchi chatdan chiqqan. Hozircha unga xabar yubora olmaysiz.",
                color = TextSecondary,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)
            )
        }
        if (error != null && loaded) {
            Text(error ?: "", color = StatusCancelFg, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 20.dp))
        }
        ChatInputBar(
            value = input,
            onValueChange = { input = it },
            onSend = { send() },
            enabled = !sending && peerInChat,
            bottomPadding = 12.dp + 0.dp,
            placeholder = if (peerInChat) "Xabar yozing..." else "Xabar yuborib bo'lmaydi"
        )
        Spacer(modifier = Modifier.navigationBarsPadding())
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
}
