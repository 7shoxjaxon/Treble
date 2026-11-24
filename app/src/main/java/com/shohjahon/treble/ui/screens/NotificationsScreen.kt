package com.shohjahon.treble.ui.screens

import com.shohjahon.treble.ui.components.auroraBackground
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shohjahon.treble.data.repository.GameRepository
import com.shohjahon.treble.data.repository.NotificationRepository
import com.shohjahon.treble.data.util.DateUtils
import com.shohjahon.treble.ui.components.*
import com.shohjahon.treble.ui.theme.*

/** Bildirishnomalar ro'yxati: o'yin eslatmalari va "sizga o'yin biriktirildi" xabarlari. */
@Composable
fun NotificationsScreen(
    onBack: () -> Unit,
    onOpenGame: (String) -> Unit
) {
    // Ochilganda yangi (o'qilmagan) xabarlar belgilab ko'rsatiladi, so'ng hammasi o'qilgan bo'ladi
    var unreadIds by remember { mutableStateOf(NotificationRepository.items.filter { !it.read }.map { it.id }.toSet()) }
    LaunchedEffect(Unit) {
        unreadIds = NotificationRepository.items.filter { !it.read }.map { it.id }.toSet()
        NotificationRepository.markAllRead()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .auroraBackground()
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Surface1)
                    .border(1.dp, Outline, CircleShape)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                TrebleIcon(IconPaths.ArrowBack, TextPrimary, size = 20.dp)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "Bildirishnomalar",
                color = TextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            if (NotificationRepository.items.isNotEmpty()) {
                Text(
                    text = "Tozalash",
                    color = TextSecondary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { NotificationRepository.clearAll() }
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                )
            }
        }

        if (NotificationRepository.items.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                    TrebleIcon(IconPaths.Bell, TextSecondary, size = 40.dp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Hozircha xabar yo'q", color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "O'yin boshlanishidan 2 soat, 1 soat va 30 daqiqa oldin eslatmalar shu yerda ham ko'rinadi.",
                        color = TextSecondary,
                        fontSize = 14.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(NotificationRepository.items.toList(), key = { it.id }) { n ->
                    val isNew = n.id in unreadIds
                    val canOpen = n.gameId.isNotBlank() && GameRepository.gameById(n.gameId) != null
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(if (isNew) LimeDeep else Surface1)
                            .border(1.dp, if (isNew) LimeGreen.copy(alpha = 0.4f) else Outline, RoundedCornerShape(18.dp))
                            .clickable(enabled = canOpen) { onOpenGame(n.gameId) }
                            .padding(14.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(Surface2),
                            contentAlignment = Alignment.Center
                        ) {
                            TrebleIcon(
                                if (n.kind == "ASSIGNED") IconPaths.Shield else IconPaths.Clock,
                                LimeGreen,
                                size = 20.dp
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(n.title, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(n.body, color = TextSecondary, fontSize = 13.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(DateUtils.dateTime(n.createdAt), color = TextSecondary.copy(alpha = 0.7f), fontSize = 11.sp)
                        }
                        if (isNew) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .padding(top = 6.dp)
                                    .size(9.dp)
                                    .clip(CircleShape)
                                    .background(LimeGreen)
                            )
                        }
                    }
                }
            }
        }
    }
}
