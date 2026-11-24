package com.shohjahon.treble.ui.screens

import com.shohjahon.treble.ui.components.auroraBackground
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shohjahon.treble.data.models.Game
import com.shohjahon.treble.data.models.StaffRef
import com.shohjahon.treble.data.repository.GameRepository
import com.shohjahon.treble.data.repository.PeopleRepository
import com.shohjahon.treble.data.util.DateUtils
import com.shohjahon.treble.data.util.ImageStore
import com.shohjahon.treble.ui.components.*
import com.shohjahon.treble.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

private fun looksLikeUrl(s: String): Boolean =
    s.startsWith("http://") || s.startsWith("https://") || s.startsWith("www.") || s.startsWith("maps.")

@Composable
private fun FormLabel(text: String) {
    Text(
        text = text,
        color = TextSecondary,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 18.dp, bottom = 8.dp)
    )
}

/** Yangi o'yin qo'shish yoki mavjudini tahrirlash sahifasi (faqat admin). */
@Composable
fun GameFormScreen(gameId: String?, copyFromId: String? = null, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val initial = remember(gameId) { gameId?.let { GameRepository.gameById(it) } }
    val isEdit = initial != null
    // Nusxa olinayotgan bo'lsa, shu o'yin ma'lumotlari shablon bo'ladi
    val template = remember(gameId, copyFromId) {
        initial ?: copyFromId?.let { GameRepository.gameById(it) }
    }
    val startDate = remember(template) {
        val t = template
        if (t == null) {
            DateUtils.today()
        } else if (initial != null) {
            t.dateMillis
        } else {
            var d = DateUtils.addDays(t.dateMillis, 7)
            while (d < DateUtils.today()) d = DateUtils.addDays(d, 7)
            d
        }
    }

    var venueName by remember { mutableStateOf(template?.venueName ?: "") }
    var address by remember { mutableStateOf(template?.address ?: "") }
    var mapLink by remember { mutableStateOf(template?.mapLink ?: "") }
    val images = remember { mutableStateListOf<String>().apply { addAll(template?.images ?: emptyList()) } }
    var imageUrl by remember { mutableStateOf("") }
    var dateMillis by remember { mutableStateOf(startDate) }
    var startTime by remember { mutableStateOf(template?.startTime ?: "20:00") }
    var durationMin by remember { mutableStateOf(template?.durationMin ?: 120) }
    var format by remember { mutableStateOf(template?.format ?: "5v5") }
    var maxPlayers by remember { mutableStateOf((template?.maxPlayers ?: 10).toString()) }
    var price by remember { mutableStateOf(template?.price?.toString() ?: "") }
    var freeCancelHours by remember { mutableStateOf(template?.freeCancelHours ?: 24) }
    var payInfo by remember { mutableStateOf(template?.payInfo ?: "") }
    var description by remember { mutableStateOf(template?.description ?: "") }
    var rules by remember { mutableStateOf(template?.rules ?: "") }
    var staffIds by remember { mutableStateOf(template?.staff?.map { it.id } ?: emptyList<String>()) }
    var error by remember { mutableStateOf<String?>(null) }
    var uploading by remember { mutableStateOf(false) }

    // Biriktirish uchun ishchi adminlar ro'yxatini serverdan olamiz
    LaunchedEffect(Unit) { PeopleRepository.refreshStaff() }
    val knownStaff = (PeopleRepository.staff.map { StaffRef(it.id, it.name) } + (template?.staff ?: emptyList()))
        .distinctBy { it.id }
    var saving by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(6)
    ) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                uploading = true
                var lastError: String? = null
                var failed = 0
                for (uri in uris) {
                    if (images.size >= 8) break
                    val (url, err) = ImageStore.uploadFromUri(context, uri)
                    if (url != null) {
                        images.add(url)
                    } else {
                        failed += 1
                        lastError = err
                    }
                }
                uploading = false
                if (failed > 0) {
                    Toast.makeText(context, lastError ?: "Ba'zi rasmlarni yuklab bo'lmadi", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val dayOptions = remember {
        val list = (0..13).map { DateUtils.addDays(DateUtils.today(), it) }.toMutableList()
        val current: Long? = startDate
        if (current != null && current !in list) list.add(0, current)
        list.toList()
    }
    val venues = remember { if (isEdit) emptyList() else GameRepository.venues() }

    fun toast(message: String) = Toast.makeText(context, message, Toast.LENGTH_SHORT).show()

    val save: () -> Unit = {
        val max = maxPlayers.toIntOrNull()
        val problem = when {
            venueName.isBlank() -> "Maydon nomini kiriting."
            DateUtils.parseMinutes(startTime) == null -> "Boshlanish vaqtini SS:DD ko'rinishida kiriting, masalan 20:00."
            max == null || max < 2 || max > 40 -> "Maksimal o'yinchi soni 2 dan 40 gacha bo'lsin."
            price.isBlank() -> "Narxni kiriting (bepul bo'lsa 0 yozing)."
            mapLink.isNotBlank() && !looksLikeUrl(mapLink.trim()) -> "Xarita havolasi noto'g'ri."
            !isEdit && DateUtils.timeWithDate(dateMillis, startTime.trim()) < System.currentTimeMillis() ->
                "Boshlanish vaqti o'tib ketgan. Sana yoki vaqtni o'zgartiring."
            else -> null
        }
        if (problem != null || max == null) {
            error = problem
        } else {
            val base = initial ?: Game(
                id = UUID.randomUUID().toString(),
                venueName = "",
                dateMillis = dateMillis,
                startTime = startTime
            )
            val normalizedTime = DateUtils.formatMinutes(DateUtils.parseMinutes(startTime) ?: 0)
            val game = base.copy(
                venueName = venueName.trim(),
                address = address.trim(),
                mapLink = mapLink.trim(),
                images = images.toList(),
                dateMillis = dateMillis,
                startTime = normalizedTime,
                durationMin = durationMin,
                format = format.trim().ifBlank { "5v5" },
                maxPlayers = max,
                price = price.toIntOrNull() ?: 0,
                description = description.trim(),
                rules = rules.trim(),
                payInfo = payInfo.trim(),
                freeCancelHours = freeCancelHours,
                staff = knownStaff.filter { it.id in staffIds }
            )
            saving = true
            scope.launch {
                val result = if (initial == null) GameRepository.addGame(game) else GameRepository.editGame(game)
                saving = false
                if (result == null) {
                    toast(if (isEdit) "Saqlandi" else "O'yin e'lon qilindi")
                    onBack()
                } else {
                    error = result
                }
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
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            IconCircleButton(IconPaths.ArrowBack, onBack, modifier = Modifier.align(Alignment.CenterStart), size = 42.dp)
            Text(
                text = if (isEdit) "O'yinni tahrirlash" else if (copyFromId != null) "Yangi o'yin (nusxa)" else "Yangi o'yin",
                color = TextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            if (venues.isNotEmpty()) {
                FormLabel("Avval ishlatilgan maydonlar (bosing, ma'lumotlar to'ldiriladi)")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(venues) { v ->
                        SelectChip(text = v.name, selected = false, onClick = {
                            venueName = v.name
                            address = v.address
                            mapLink = v.mapLink
                            images.clear()
                            images.addAll(v.images)
                            error = null
                        })
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
            }

            TrebleTextField(
                label = "Maydon (stadion) nomi",
                value = venueName,
                onValueChange = { venueName = it; error = null },
                placeholder = "Masalan: Yoshlar Sport City",
                modifier = Modifier.padding(top = if (venues.isEmpty()) 8.dp else 0.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            TrebleTextField(
                label = "Manzil",
                value = address,
                onValueChange = { address = it; error = null },
                placeholder = "Tuman, ko'cha, mo'ljal"
            )
            Spacer(modifier = Modifier.height(12.dp))
            TrebleTextField(
                label = "Xarita havolasi (ixtiyoriy)",
                value = mapLink,
                onValueChange = { mapLink = it; error = null },
                placeholder = "https://maps.google.com/..."
            )

            // ---- Rasmlar ----
            FormLabel("Maydon rasmlari (${images.size}/8)")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    Column(
                        modifier = Modifier
                            .size(92.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Surface2)
                            .border(1.dp, LimeGreen.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                            .clickable {
                                if (!uploading) {
                                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                }
                            },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        TrebleIcon(IconPaths.Add, LimeGreen, size = 26.dp)
                        Text(if (uploading) "Yuklanmoqda..." else "Galereya", color = LimeGreen, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                items(images.toList()) { path ->
                    Box(modifier = Modifier.size(92.dp)) {
                        GameImage(
                            path = path,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(16.dp))
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(4.dp)
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.65f))
                                .clickable {
                                    images.remove(path)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            TrebleIcon(IconPaths.Close, Color.White, size = 13.dp)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                TrebleTextField(
                    label = "Yoki rasm havolasi",
                    value = imageUrl,
                    onValueChange = { imageUrl = it; error = null },
                    placeholder = "https://...",
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                DialogButton(
                    text = "Qo'shish",
                    onClick = {
                        val url = imageUrl.trim()
                        if (!looksLikeUrl(url)) {
                            error = "Rasm havolasi noto'g'ri."
                        } else if (images.size >= 8) {
                            error = "Ko'pi bilan 8 ta rasm qo'shish mumkin."
                        } else {
                            images.add(if (url.startsWith("www.")) "https://$url" else url)
                            imageUrl = ""
                            error = null
                        }
                    },
                    modifier = Modifier.width(96.dp)
                )
            }

            // ---- Sana va vaqt ----
            FormLabel("Sana")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(dayOptions) { day ->
                    SelectChip(
                        text = DateUtils.chipLabel(day),
                        selected = day == dateMillis,
                        onClick = { dateMillis = day; error = null }
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TrebleTextField(
                    label = "Boshlanish (SS:DD)",
                    value = startTime,
                    onValueChange = { startTime = it.take(5); error = null },
                    placeholder = "20:00",
                    modifier = Modifier.weight(1f)
                )
                TrebleTextField(
                    label = "Narx (so'm)",
                    value = price,
                    onValueChange = { price = it.filter { c -> c.isDigit() }.take(7); error = null },
                    placeholder = "30000",
                    keyboardType = KeyboardType.Number,
                    modifier = Modifier.weight(1f)
                )
            }

            FormLabel("Davomiyligi")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(60, 90, 120, 150).forEach { m ->
                    SelectChip("$m daq", durationMin == m) { durationMin = m }
                }
            }

            // ---- Format ----
            FormLabel("Format")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("5v5" to 10, "6v6" to 12, "7v7" to 14, "11v11" to 22).forEach { (f, max) ->
                    SelectChip(f, format == f) {
                        format = f
                        maxPlayers = max.toString()
                        error = null
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TrebleTextField(
                    label = "Format (o'zingiz yozing)",
                    value = format,
                    onValueChange = { format = it.take(10); error = null },
                    placeholder = "5v5",
                    modifier = Modifier.weight(1f)
                )
                TrebleTextField(
                    label = "Maks. o'yinchi",
                    value = maxPlayers,
                    onValueChange = { maxPlayers = it.filter { c -> c.isDigit() }.take(2); error = null },
                    placeholder = "10",
                    keyboardType = KeyboardType.Number,
                    modifier = Modifier.weight(1f)
                )
            }

            // ---- Bekor qilish va to'lov ----
            FormLabel("Bandni bekor qilish muddati (o'yinga qadar)")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0, 6, 12, 24, 48).forEach { h ->
                    SelectChip(if (h == 0) "Yo'q" else "$h soat", freeCancelHours == h) { freeCancelHours = h }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            TrebleTextField(
                label = "Kartaga o'tkazma ma'lumoti (ixtiyoriy)",
                value = payInfo,
                onValueChange = { payInfo = it.take(120); error = null },
                placeholder = "Karta raqami va ism"
            )

            // ---- Ishchi admin ----
            FormLabel("Ishchi admin (yoqlama qiladi)")
            if (knownStaff.isEmpty()) {
                Text(
                    text = "Ishchi admin yo'q. Avval Profil → Ishchi adminlar orqali qo'shing.",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(knownStaff, key = { it.id }) { st ->
                        SelectChip(st.name, st.id in staffIds) {
                            staffIds = if (st.id in staffIds) staffIds - st.id else staffIds + st.id
                        }
                    }
                }
                Text(
                    text = "Tanlangan ishchi admin faqat shu o'yinni ko'radi va faqat shu o'yinning yoqlamasini qila oladi.",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            // ---- Matnlar ----
            Spacer(modifier = Modifier.height(12.dp))
            TrebleTextField(
                label = "O'yin haqida (ixtiyoriy)",
                value = description,
                onValueChange = { description = it.take(600) },
                placeholder = "Format, jamoalar, formalar haqida...",
                singleLine = false,
                minHeight = 96.dp
            )
            Spacer(modifier = Modifier.height(12.dp))
            TrebleTextField(
                label = "Qoidalar (bo'sh qolsa standart qoidalar ko'rsatiladi)",
                value = rules,
                onValueChange = { rules = it.take(600) },
                placeholder = "• Vaqtida keling...",
                singleLine = false,
                minHeight = 96.dp
            )

            if (error != null) {
                Spacer(modifier = Modifier.height(12.dp))
                ErrorText(error)
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(BackgroundDark)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            DialogButton(
                text = if (saving) "Saqlanmoqda..." else if (uploading) "Rasm yuklanmoqda..." else if (isEdit) "Saqlash" else "E'lon qilish",
                onClick = { if (!saving && !uploading) save() },
                modifier = Modifier.fillMaxWidth(),
                primary = true
            )
        }
    }
}
