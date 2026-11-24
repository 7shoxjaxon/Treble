package com.shohjahon.treble.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.shohjahon.treble.data.models.AuthResult
import com.shohjahon.treble.data.models.User
import com.shohjahon.treble.data.models.UserRole
import com.shohjahon.treble.data.repository.AuthRepository
import com.shohjahon.treble.data.repository.CouponRepository
import com.shohjahon.treble.data.repository.GameRepository
import com.shohjahon.treble.data.repository.PeopleRepository
import com.shohjahon.treble.data.repository.SettingsRepository
import com.shohjahon.treble.data.util.DateUtils
import com.shohjahon.treble.ui.components.*
import com.shohjahon.treble.ui.theme.*

/** Profildan ochiladigan ichki sahifalar. */
@Composable
fun ProfilePageScreen(
    name: String,
    onBack: () -> Unit,
    onOpenPage: (String) -> Unit
) {
    val me = AuthRepository.currentUser
    if (me == null) {
        Box(modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark))
        return
    }
    when (name) {
        "rating" -> RatingPage(me, onBack, onOpenPage)
        "coupons" -> CouponsPage(me, onBack, onOpenPage)
        "personal" -> PersonalInfoPage(me, onBack)
        "public_profile" -> PublicProfilePage(me, onBack, onOpenPage)
        "faq" -> FaqPage(onBack)
        "privacy" -> PrivacyPage(onBack)
        "contact" -> ContactPage(me, onBack)
        "players" -> if (me.isBoss) PlayersPage(onBack) else NoAccessPage(onBack)
        "staff" -> if (me.isBoss) StaffPage(onBack) else NoAccessPage(onBack)
        else -> SubPageScaffold(title = "Sahifa", onBack = onBack) {
            EmptyState(IconPaths.Info, "Sahifa topilmadi", "Orqaga qayting.")
        }
    }
}

// ------------------------------------------------------------------ Reyting

@Composable
private fun RatingPage(me: User, onBack: () -> Unit, onOpenPage: (String) -> Unit) {
    var tab by remember { mutableStateOf(0) }
    val monthKey = if (tab == 0) DateUtils.currentMonthKey() else null
    val ranking = GameRepository.ranking(monthKey)
    val myIndex = ranking.indexOfFirst { it.id == me.id }

    SubPageScaffold(title = "Reyting", onBack = onBack) {
        SegmentedTabs(
            options = listOf("Bu oy", "Butun davr"),
            selectedIndex = tab,
            onSelect = { tab = it },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Surface1)
                .border(1.dp, Outline, RoundedCornerShape(16.dp))
                .padding(14.dp)
        ) {
            TrebleIcon(IconPaths.Info, TextSecondary, size = 18.dp)
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = if (tab == 0)
                        "Reyting shu oyda o'ynalgan o'yinlar soni bo'yicha tuziladi. Oy oxirida eng faol 3 o'yinchi bittadan bepul o'yin kuponini yutib oladi."
                    else
                        "Butun davr bo'yicha eng ko'p o'ynagan futbolchilar. Sovg'alar har oylik reytingda beriladi.",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )
                if (tab == 0) {
                    Text(
                        text = "Kuponlar",
                        color = LimeGreen,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .clickable { onOpenPage("coupons") }
                    )
                }
            }
        }

        if (ranking.isEmpty()) {
            EmptyState(
                iconPath = IconPaths.Trophy,
                title = "Reyting hali bo'sh",
                subtitle = "O'yinlar o'tgach, eng faol futbolchilar shu yerda ko'rinadi."
            )
            Spacer(modifier = Modifier.weight(1f))
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(ranking, key = { _, s -> s.id }) { index, stat ->
                    val rank = index + 1
                    val top = rank == 1
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (top) LimeDeep else Surface1)
                            .border(
                                1.dp,
                                if (top) LimeGreen.copy(alpha = 0.5f) else Outline,
                                RoundedCornerShape(16.dp)
                            )
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "$rank",
                            color = if (rank <= 3) LimeGreen else TextSecondary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(32.dp)
                        )
                        UserAvatar(name = stat.name, avatar = GameRepository.avatarOf(stat.id), size = 38.dp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = if (stat.id == me.id) "${stat.name} (Siz)" else stat.name,
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Column(horizontalAlignment = Alignment.End) {
                            Text("${stat.played}", color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                            Text("o'yin", color = TextSecondary, fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        // Mening o'rnim
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Surface1)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (myIndex >= 0) "#${myIndex + 1}" else "—",
                color = LimeGreen,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(48.dp)
            )
            UserAvatar(name = me.name, avatar = me.avatar, size = 38.dp)
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(me.name, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(
                    text = if (myIndex >= 0) "Sizning o'rningiz" else "Hali o'ynamagansiz",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }
        }
    }
}

// ------------------------------------------------------------------ Kuponlar

@Composable
private fun CouponsPage(me: User, onBack: () -> Unit, onOpenPage: (String) -> Unit) {
    val coupons = CouponRepository.forUser(me.id).sortedBy { it.monthKey }.reversed()
    SubPageScaffold(title = "Kuponlarim", onBack = onBack) {
        if (coupons.isEmpty()) {
            EmptyState(
                iconPath = IconPaths.Ticket,
                title = "Hozircha kuponlar yo'q",
                subtitle = "Oylik reyting top-3 sovg'alari shu yerda ko'rinadi.",
                actionText = "Reytingga o'tish",
                onAction = { onOpenPage("rating") }
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(coupons, key = { _, c -> c.id }) { _, c ->
                    SurfaceCard(modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(LimeDeep),
                                contentAlignment = Alignment.Center
                            ) {
                                TrebleIcon(IconPaths.Ticket, LimeGreen, size = 24.dp)
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Bepul o'yin kuponi", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    "${DateUtils.monthLabel(c.monthKey)} reytingi uchun",
                                    color = TextSecondary,
                                    fontSize = 12.sp
                                )
                            }
                            if (c.usedGameId == null) {
                                StatusPill("Faol", StatusOpenBg, StatusOpenFg)
                            } else {
                                StatusPill("Ishlatilgan", StatusFullBg, StatusFullFg)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ Profil rasmi

/** Profil rasmi + qalamcha belgisi + "Rasm qo'shish / O'chirish". Bosilsa galereya ochiladi. */
@Composable
private fun AvatarEditor(
    name: String,
    avatar: String,
    size: Dp,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null && !busy) {
            scope.launch {
                busy = true
                val r = AuthRepository.uploadAvatar(context, uri)
                busy = false
                val msg = if (r is AuthResult.Error) r.message else "Rasm yangilandi"
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            }
        }
    }
    val pick = {
        if (!busy) picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        // Tashqi quti kesilmaydi, shuning uchun qalamcha belgisi to'liq ko'rinadi
        Box(modifier = Modifier.size(size)) {
            Box(
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape)
                    .clickable { pick() },
                contentAlignment = Alignment.Center
            ) {
                UserAvatar(name = name, avatar = avatar, size = size)
                if (busy) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0x99000000)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("...", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(32.dp)
                    .border(2.dp, BackgroundDark, CircleShape)
                    .clip(CircleShape)
                    .background(LimeGreen)
                    .clickable { pick() },
                contentAlignment = Alignment.Center
            ) {
                TrebleIcon(IconPaths.Edit, DarkText, size = 16.dp)
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (avatar.isBlank()) "Rasm qo'shish" else "Rasmni almashtirish",
                color = LimeGreen,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { pick() }
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            )
            if (avatar.isNotBlank()) {
                Text(
                    text = "O'chirish",
                    color = ErrorRed,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            if (!busy) {
                                scope.launch {
                                    busy = true
                                    val r = AuthRepository.removeAvatar()
                                    busy = false
                                    val msg = if (r is AuthResult.Error) r.message else "Rasm o'chirildi"
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                )
            }
        }
    }
}

// ------------------------------------------------------------------ Shaxsiy ma'lumotlar

@Composable
private fun PersonalInfoPage(me: User, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(me.name) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    SubPageScaffold(title = "Shaxsiy ma'lumotlar", onBack = onBack) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            AvatarEditor(
                name = name,
                avatar = me.avatar,
                size = 96.dp,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            Spacer(modifier = Modifier.height(14.dp))
            TrebleTextField(
                label = "To'liq ism",
                value = name,
                onValueChange = { name = it; error = null },
                imeAction = ImeAction.Done
            )
            Spacer(modifier = Modifier.height(14.dp))
            TrebleTextField(
                label = "Telefon raqami",
                value = me.phone.ifBlank { "Kiritilmagan" },
                onValueChange = {},
                enabled = false,
                keyboardType = KeyboardType.Phone
            )
            Text(
                text = "Telefon raqamini bu yerda o'zgartirib bo'lmaydi.",
                color = TextSecondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 6.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))
            TrebleTextField(
                label = "Logon",
                value = me.login,
                onValueChange = {},
                enabled = false
            )
            if (error != null) {
                Spacer(modifier = Modifier.height(10.dp))
                ErrorText(error)
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(16.dp)
        ) {
            DialogButton(
                text = if (saving) "Saqlanmoqda..." else "Saqlash",
                onClick = {
                    if (!saving) {
                        saving = true
                        scope.launch {
                            val r = AuthRepository.updateProfile(name)
                            saving = false
                            when (r) {
                                is AuthResult.Success -> {
                                    Toast.makeText(context, "Saqlandi", Toast.LENGTH_SHORT).show()
                                    onBack()
                                }
                                is AuthResult.Error -> { error = r.message }
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                primary = true,
                enabled = name.trim() != me.name
            )
        }
    }
}

// ------------------------------------------------------------------ Ochiq profil

@Composable
private fun PublicProfilePage(me: User, onBack: () -> Unit, onOpenPage: (String) -> Unit) {
    val stat = GameRepository.statFor(me.id)
    val rank = GameRepository.rankOf(me.id)
    val now = System.currentTimeMillis()
    // Admin o'ynamaydi, o'yin tashkil qiladi: bosh admin uchun hamma, ishchi admin uchun o'ziga biriktirilgan o'yinlar
    val isOrganizer = me.isManager
    val organized = if (isOrganizer) {
        GameRepository.games
            .filter { g -> g.isOver(now) && !g.isCancelled && (me.isBoss || g.staff.any { it.id == me.id }) }
            .sortedByDescending { it.dateMillis }
    } else emptyList()
    val recent = if (isOrganizer) organized else GameRepository.gamesOf(me.id)
        .filter { it.hasJoined(me.id) && it.isOver(now) && !it.isCancelled }
        .take(3)

    SubPageScaffold(title = "O'yinchi", onBack = onBack) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AvatarEditor(name = me.name, avatar = me.avatar, size = 96.dp)
            Spacer(modifier = Modifier.height(12.dp))
            Text(me.name, color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            if (me.joinedAt > 0L) {
                Text(
                    text = "${DateUtils.monthYear(me.joinedAt)} dan Treble bilan birga!",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            Spacer(modifier = Modifier.height(20.dp))

            SurfaceCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(if (isOrganizer) "Tashkil qilgan o'yinlari" else "O'ynagan o'yinlari", color = TextPrimary, fontSize = 15.sp)
                    Text("${if (isOrganizer) organized.size else (stat?.played ?: 0)}", color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                }
            }
            val played = stat?.played ?: 0
            val missed = stat?.missed ?: 0
            if (!isOrganizer && played + missed > 0) {
                Spacer(modifier = Modifier.height(10.dp))
                SurfaceCard(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Davomat", color = TextPrimary, fontSize = 15.sp)
                        Text(
                            text = "${played * 100 / (played + missed)}%",
                            color = LimeGreen,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(LimeDeep)
                    .border(1.dp, LimeGreen.copy(alpha = 0.4f), RoundedCornerShape(18.dp))
                    .clickable { onOpenPage("rating") }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TrebleIcon(IconPaths.Trophy, LimeGreen, size = 28.dp)
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("REYTINGDAGI O'RNINGIZ", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = if (rank != null) "#$rank" else "—",
                        color = LimeGreen,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                TrebleIcon(IconPaths.ChevronRight, LimeGreen, size = 22.dp)
            }

            Spacer(modifier = Modifier.height(22.dp))
            Text(
                text = if (isOrganizer) "Tashkil qilgan o'yinlar tarixi" else "Oxirgi o'yinlar",
                color = TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.Start)
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (recent.isEmpty()) {
                Text(
                    text = if (isOrganizer) "Hali o'yin o'tkazilmagan." else "Hali o'yin o'ynalmagan.",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.align(Alignment.Start)
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    recent.forEach { g ->
                        SurfaceCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(g.venueName, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        if (isOrganizer) "${g.format} · ${g.players.size} o'yinchi" else g.format,
                                        color = TextSecondary, fontSize = 12.sp
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(DateUtils.dateWithWeekday(g.dateMillis), color = TextPrimary, fontSize = 13.sp)
                                    Text(g.startTime, color = TextSecondary, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ Savol-javob va maxfiylik

@Composable
private fun FaqPage(onBack: () -> Unit) {
    val items = listOf(
        "O'yinga qanday yozilaman?" to "«O'yinlar» bo'limidan o'yinni tanlang, sahifasidagi «Band qilish» tugmasini bosing va to'lov usulini tasdiqlang.",
        "Bandni bekor qila olamanmi?" to "Ha. Har bir o'yin sahifasida bekor qilish muddati ko'rsatilgan (odatda o'yin boshlanishidan 24 soat oldin). Undan keyin bekor qilish uchun tashkilotchi bilan bog'laning.",
        "To'lov qanday amalga oshiriladi?" to "Odatda o'yin kuni maydonda to'lanadi. Tashkilotchi karta ma'lumotini kiritgan bo'lsa, kartaga o'tkazma ham tanlash mumkin. To'lovni tashkilotchi belgilab boradi.",
        "Kupon nima va qanday olinadi?" to "Har oy oxirida shu oyda eng ko'p o'ynagan 3 futbolchiga bittadan bepul o'yin kuponi beriladi. Band qilishda uni to'lov usuli sifatida tanlaysiz.",
        "Reyting qanday hisoblanadi?" to "Reyting o'tgan o'yinlar soniga qarab tuziladi. Tashkilotchi «Kelmadi» deb belgilagan o'yinlar hisobga olinmaydi.",
        "O'yin bekor qilinsa nima bo'ladi?" to "Tashkilotchi o'yinni bekor qilsa, u «Bekor qilingan» holatida ko'rinadi, kupon ishlatgan bo'lsangiz, u qaytariladi."
    )
    var openIndex by remember { mutableStateOf(-1) }

    SubPageScaffold(title = "Savol-javob", onBack = onBack) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items.forEachIndexed { index, (q, a) ->
                SurfaceCard(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { openIndex = if (openIndex == index) -1 else index },
                    contentPadding = PaddingValues(16.dp)
                ) {
                    Text(q, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    if (openIndex == index) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(a, color = TextSecondary, fontSize = 13.sp, lineHeight = 19.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun PrivacyPage(onBack: () -> Unit) {
    val context = LocalContext.current
    SubPageScaffold(title = "Maxfiylik siyosati", onBack = onBack) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            listOf(
                "Qanday ma'lumotlar saqlanadi" to "Ismingiz, telefon raqamingiz, logoningiz va parolingiz (faqat shifrlangan ko'rinishda), profil rasmingiz, band qilgan o'yinlaringiz, izohlaringiz va chat xabarlaringiz.",
                "Ma'lumotlar qayerda saqlanadi" to "Ma'lumotlar Treble serverida saqlanadi va faqat ilova ishlashi uchun ishlatiladi. Tasdiqlash kodi Telegram bot orqali, bildirishnomalar Google Firebase orqali yuboriladi. Reklama yo'q, ma'lumotlaringiz sotilmaydi.",
                "Telefon raqamingizni kim ko'radi" to "Faqat o'yinlarni tashkil qiluvchi admin. Boshqa futbolchilar faqat ismingizni ko'radi.",
                "Ma'lumotlarni o'chirish" to "Profil bo'limidagi «Akkauntni o'chirish» orqali hisobingizni istalgan vaqtda o'chirishingiz mumkin."
            ).forEach { (title, text) ->
                Column {
                    Text(title, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text, color = TextSecondary, fontSize = 13.sp, lineHeight = 19.sp)
                }
            }
            SurfaceCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    try {
                        context.startActivity(
                            android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(com.shohjahon.treble.data.remote.ApiConfig.BASE_URL + "/privacy"))
                        )
                    } catch (e: Exception) {
                        Toast.makeText(context, "Brauzer topilmadi", Toast.LENGTH_SHORT).show()
                    }
                },
                contentPadding = PaddingValues(16.dp)
            ) {
                Text("To'liq maxfiylik siyosatini ochish", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(com.shohjahon.treble.data.remote.ApiConfig.BASE_URL + "/privacy", color = TextSecondary, fontSize = 13.sp)
            }
        }
    }
}

// ------------------------------------------------------------------ Bog'lanish

@Composable
private fun ContactPage(me: User, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isAdmin = me.role == UserRole.ADMIN
    var phone by remember { mutableStateOf(SettingsRepository.supportPhone) }
    var telegram by remember { mutableStateOf(SettingsRepository.supportTelegram) }

    SubPageScaffold(title = "Bog'lanish", onBack = onBack) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (isAdmin) {
                Text(
                    text = "Futbolchilar bu ma'lumotlarni «Bog'lanish» sahifasida ko'radi.",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
                TrebleTextField(
                    label = "Telefon raqami",
                    value = phone,
                    onValueChange = { phone = it.take(20) },
                    placeholder = "+998 90 123 45 67",
                    keyboardType = KeyboardType.Phone
                )
                TrebleTextField(
                    label = "Telegram (username)",
                    value = telegram,
                    onValueChange = { telegram = it.take(40) },
                    placeholder = "@username",
                    imeAction = ImeAction.Done
                )
                DialogButton(
                    text = "Saqlash",
                    onClick = {
                        scope.launch {
                            val error = SettingsRepository.saveSupport(phone, telegram)
                            Toast.makeText(context, error ?: "Saqlandi", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    primary = true
                )
            } else {
                val savedPhone = SettingsRepository.supportPhone
                val savedTelegram = SettingsRepository.supportTelegram
                if (savedPhone.isBlank() && savedTelegram.isBlank()) {
                    EmptyState(
                        iconPath = IconPaths.Phone,
                        title = "Ma'lumot kiritilmagan",
                        subtitle = "Tashkilotchi hali bog'lanish ma'lumotlarini qo'shmagan."
                    )
                } else {
                    if (savedPhone.isNotBlank()) {
                        SurfaceCard(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { dialNumber(context, savedPhone) }
                        ) {
                            Text("Qo'ng'iroq qilish", color = TextSecondary, fontSize = 12.sp)
                            Text(savedPhone, color = LimeGreen, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    if (savedTelegram.isNotBlank()) {
                        SurfaceCard(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { openLink(context, "https://t.me/$savedTelegram") }
                        ) {
                            Text("Telegram", color = TextSecondary, fontSize = 12.sp)
                            Text("@$savedTelegram", color = LimeGreen, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}


// ------------------------------------------------------------------ Bosh admin sahifalari

@Composable
private fun NoAccessPage(onBack: () -> Unit) {
    SubPageScaffold(title = "Sahifa", onBack = onBack) {
        EmptyState(IconPaths.Lock, "Ruxsat yo'q", "Bu sahifa faqat bosh admin uchun.")
    }
}

/** Ro'yxatdan o'tgan barcha o'yinchilar, telefon raqami bilan. */
@Composable
private fun PlayersPage(onBack: () -> Unit) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { error = PeopleRepository.refreshPlayers() }

    val q = query.trim()
    val digits = q.filter { it.isDigit() }
    val list = PeopleRepository.players.filter { u ->
        q.isEmpty() ||
                u.name.contains(q, ignoreCase = true) ||
                u.login.contains(q, ignoreCase = true) ||
                (digits.isNotEmpty() && u.phone.filter { c -> c.isDigit() }.contains(digits))
    }

    SubPageScaffold(title = "O'yinchilar", onBack = onBack) {
        TrebleTextField(
            label = "Qidirish",
            value = query,
            onValueChange = { query = it.take(40) },
            placeholder = "Ism, logon yoki telefon",
            imeAction = ImeAction.Done,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        Text(
            text = "Ro'yxatdan o'tganlar: ${PeopleRepository.players.size}",
            color = TextSecondary,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )
        if (error != null) {
            ErrorText(error, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        }

        if (list.isEmpty()) {
            EmptyState(
                iconPath = IconPaths.People,
                title = if (PeopleRepository.loadingPlayers) "Yuklanmoqda..." else "O'yinchi topilmadi",
                subtitle = "Ro'yxatdan o'tgan futbolchilar shu yerda ko'rinadi."
            )
            Spacer(modifier = Modifier.weight(1f))
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(list, key = { it.id }) { u ->
                    SurfaceCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            UserAvatar(name = u.name, avatar = u.avatar, size = 40.dp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = u.name,
                                    color = TextPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(text = "@${u.login}", color = TextSecondary, fontSize = 12.sp)
                            }
                            if (u.phone.isNotBlank()) {
                                Text(
                                    text = u.phone,
                                    color = LimeGreen,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { dialNumber(context, u.phone) }
                                        .padding(6.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Ishchi adminlarni ko'rish, qo'shish va o'chirish. */
@Composable
private fun StaffPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf<String?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<User?>(null) }

    LaunchedEffect(Unit) { error = PeopleRepository.refreshStaff() }

    SubPageScaffold(title = "Ishchi adminlar", onBack = onBack) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(
                text = "Ishchi admin faqat o'ziga biriktirilgan o'yinni ko'radi: o'yinchilarning «keldi / kelmadi» holatini belgilaydi va o'yinni yakunlaydi. " +
                        "To'lovlarni ko'ra olmaydi, o'yin qo'sha yoki o'chira olmaydi. O'yinga o'yin yaratish yoki tahrirlash oynasida biriktirasiz.",
                color = TextSecondary,
                fontSize = 12.sp,
                lineHeight = 17.sp
            )
            Spacer(modifier = Modifier.height(12.dp))
            DialogButton(
                text = "+ Ishchi admin qo'shish",
                onClick = { showAdd = true },
                modifier = Modifier.fillMaxWidth(),
                primary = true
            )
            if (error != null) {
                Spacer(modifier = Modifier.height(8.dp))
                ErrorText(error)
            }
        }

        if (PeopleRepository.staff.isEmpty()) {
            EmptyState(
                iconPath = IconPaths.Shield,
                title = if (PeopleRepository.loadingStaff) "Yuklanmoqda..." else "Ishchi adminlar yo'q",
                subtitle = "Yuqoridagi tugma orqali birinchi ishchi adminni qo'shing."
            )
            Spacer(modifier = Modifier.weight(1f))
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(PeopleRepository.staff, key = { it.id }) { s ->
                    SurfaceCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            UserAvatar(name = s.name, avatar = s.avatar, size = 40.dp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = s.name,
                                    color = TextPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(text = "@${s.login} · Ishchi admin", color = TextSecondary, fontSize = 12.sp)
                            }
                            Text(
                                text = "O'chirish",
                                color = ErrorRed.copy(alpha = 0.9f),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { toDelete = s }
                                    .padding(6.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        AddStaffDialog(
            onDismiss = { showAdd = false },
            onAdded = {
                showAdd = false
                Toast.makeText(context, "Ishchi admin qo'shildi", Toast.LENGTH_SHORT).show()
            }
        )
    }

    toDelete?.let { s ->
        ConfirmDialog(
            title = "Ishchi adminni o'chirish",
            message = "${s.name} (@${s.login}) hisobi o'chiriladi va u ilovadan chiqib ketadi. Davom etasizmi?",
            confirmText = "O'chirish",
            danger = true,
            onConfirm = {
                toDelete = null
                scope.launch {
                    val result = PeopleRepository.deleteStaff(s.id)
                    Toast.makeText(context, result ?: "O'chirildi", Toast.LENGTH_SHORT).show()
                }
            },
            onDismiss = { toDelete = null }
        )
    }
}

@Composable
private fun AddStaffDialog(onDismiss: () -> Unit, onAdded: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var login by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    TrebleDialog(title = "Ishchi admin qo'shish", onDismiss = onDismiss) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            TrebleTextField(
                label = "Ism",
                value = name,
                onValueChange = { name = it.take(40); error = null },
                placeholder = "Masalan: Aziz"
            )
            Spacer(modifier = Modifier.height(10.dp))
            TrebleTextField(
                label = "Logon",
                value = login,
                onValueChange = { login = it.take(20); error = null },
                placeholder = "Masalan: Aziz_ishchi"
            )
            Spacer(modifier = Modifier.height(10.dp))
            TrebleTextField(
                label = "Parol",
                value = password,
                onValueChange = { password = it.take(64); error = null },
                placeholder = "Kamida 6 ta belgi",
                isPassword = true,
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
                                val result = PeopleRepository.addStaff(name, login, password)
                                loading = false
                                if (result == null) onAdded() else error = result
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    primary = true
                )
            }
        }
    }
}
