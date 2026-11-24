package com.shohjahon.treble.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shohjahon.treble.data.models.User
import com.shohjahon.treble.data.repository.AuthRepository
import com.shohjahon.treble.data.repository.CouponRepository
import com.shohjahon.treble.ui.components.*
import com.shohjahon.treble.ui.theme.*

@Composable
private fun MenuRow(
    iconPath: String,
    title: String,
    onClick: () -> Unit,
    trailing: String? = null,
    showDivider: Boolean = true
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TrebleIcon(iconPath, TextSecondary, size = 21.dp)
            Spacer(modifier = Modifier.width(14.dp))
            Text(
                text = title,
                color = TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            if (trailing != null) {
                Text(text = trailing, color = TextSecondary, fontSize = 13.sp)
                Spacer(modifier = Modifier.width(6.dp))
            }
            TrebleIcon(IconPaths.ChevronRight, TextSecondary, size = 20.dp)
        }
        if (showDivider) {
            Box(
                modifier = Modifier
                    .padding(start = 51.dp)
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Outline)
            )
        }
    }
}

@Composable
fun ProfileTab(
    me: User,
    isAdmin: Boolean,
    isBoss: Boolean,
    onOpenPage: (String) -> Unit,
    onLoggedOut: () -> Unit
) {
    val context = LocalContext.current
    var showPassword by remember { mutableStateOf(false) }
    var showLogout by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }

    val usingDefaultPassword = AuthRepository.usingDefaultPassword
    val couponCount = CouponRepository.available(me.id).size

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 12.dp, bottom = 112.dp)
    ) {
        Text(text = "Profil", color = TextPrimary, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(14.dp))

        SurfaceCard(
            modifier = Modifier.fillMaxWidth(),
            onClick = { onOpenPage("public_profile") },
            contentPadding = PaddingValues(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                UserAvatar(name = me.name, avatar = me.avatar, size = 56.dp)
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = me.name,
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = when {
                            isBoss -> "@${me.login} · Bosh admin"
                            isAdmin -> "@${me.login} · Ishchi admin"
                            else -> "@${me.login}"
                        },
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                }
                TrebleIcon(IconPaths.ChevronRight, TextSecondary, size = 22.dp)
            }
        }

        if (usingDefaultPassword) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Admin paroli hali standart holatda. Xavfsizlik uchun uni hoziroq almashtiring.",
                color = Color(0xFFFFB84D),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 17.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0x1FFFB84D))
                    .clickable { showPassword = true }
                    .padding(14.dp)
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        SurfaceCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
            MenuRow(IconPaths.Edit, "Shaxsiy ma'lumotlar", { onOpenPage("personal") })
            MenuRow(IconPaths.Trophy, "Reyting", { onOpenPage("rating") })
            if (!isAdmin) {
                MenuRow(
                    IconPaths.Ticket, "Kuponlarim", { onOpenPage("coupons") },
                    trailing = if (couponCount > 0) "$couponCount ta" else null
                )
            }
            MenuRow(IconPaths.Person, "Profilim", { onOpenPage("public_profile") })
            MenuRow(IconPaths.Lock, "Parolni o'zgartirish", { showPassword = true }, showDivider = false)
        }

        if (isBoss) {
            Spacer(modifier = Modifier.height(14.dp))
            SurfaceCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
                MenuRow(IconPaths.People, "O'yinchilar", { onOpenPage("players") })
                MenuRow(IconPaths.Shield, "Ishchi adminlar", { onOpenPage("staff") }, showDivider = false)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        SurfaceCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
            MenuRow(IconPaths.Help, "Savol-javob", { onOpenPage("faq") })
            MenuRow(IconPaths.Phone, "Bog'lanish", { onOpenPage("contact") })
            MenuRow(IconPaths.Shield, "Maxfiylik siyosati", { onOpenPage("privacy") }, showDivider = false)
        }

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Surface1)
                .border(1.dp, Outline, RoundedCornerShape(16.dp))
                .clickable { showLogout = true }
                .padding(vertical = 15.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TrebleIcon(IconPaths.Logout, LimeGreen, size = 20.dp)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Chiqish", color = LimeGreen, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }

        if (!isAdmin) {
            Text(
                text = "Akkauntni o'chirish",
                color = ErrorRed.copy(alpha = 0.85f),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 18.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { showDelete = true }
                    .padding(8.dp)
            )
        }
    }

    if (showPassword) {
        ChangePasswordDialog(
            onDismiss = { showPassword = false },
            onChanged = {
                showPassword = false
                Toast.makeText(context, "Parol o'zgartirildi", Toast.LENGTH_SHORT).show()
            }
        )
    }
    if (showLogout) {
        ConfirmDialog(
            title = "Hisobdan chiqish",
            message = "Hisobingizdan chiqmoqchimisiz? Qayta kirish uchun logon va parol kerak bo'ladi.",
            confirmText = "Chiqish",
            onConfirm = {
                showLogout = false
                // Chiqqach, ilova o'zi kirish sahifasiga o'tadi
                AuthRepository.logout()
            },
            onDismiss = { showLogout = false }
        )
    }
    if (showDelete) {
        DeleteAccountDialog(
            onDismiss = { showDelete = false },
            onDeleted = {
                showDelete = false
            }
        )
    }
}
