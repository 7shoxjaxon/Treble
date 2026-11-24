package com.shohjahon.treble.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shohjahon.treble.data.models.AuthResult
import com.shohjahon.treble.data.remote.OtpRepository
import com.shohjahon.treble.data.repository.AuthRepository
import com.shohjahon.treble.ui.components.*
import com.shohjahon.treble.ui.theme.LimeGreen
import com.shohjahon.treble.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Parolni tiklash: telefon -> SMS kod -> yangi parol. Tugagach hisobga o'zi kiradi. */
@Composable
fun ForgotPasswordScreen(
    onNavigateBack: () -> Unit,
    onNavigateToHome: () -> Unit
) {
    // 0 = telefon, 1 = SMS kod, 2 = yangi parol
    var step by remember { mutableStateOf(0) }
    var phone by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var otpToken by remember { mutableStateOf<String?>(null) }
    var newPassword by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var secondsLeft by remember { mutableStateOf(0) }
    var sendCount by remember { mutableStateOf(0) }
    var botUrl by remember { mutableStateOf<String?>(null) }
    val ctx = LocalContext.current

    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val formattedPhone = AuthRepository.normalizePhone(phone) ?: ""

    LaunchedEffect(sendCount) {
        while (secondsLeft > 0) {
            delay(1000L)
            secondsLeft -= 1
        }
    }

    BackHandler(enabled = step == 1) {
        step = 0
        code = ""
        error = null
    }

    fun sendCode() {
        if (loading) return
        focusManager.clearFocus()
        if (formattedPhone.isEmpty()) {
            error = "Telefon raqami noto'g'ri. 9 ta raqam kiriting, masalan: 90 123 45 67"
            return
        }
        loading = true
        scope.launch {
            val result = OtpRepository.sendCode(formattedPhone, "reset")
            loading = false
            if (result.error == null) {
                step = 1
                code = ""
                error = null
                secondsLeft = result.retryAfter
                botUrl = result.botUrl
                sendCount += 1
            } else {
                error = result.error
            }
        }
    }

    fun verifyCode() {
        if (loading || code.length != 6) return
        loading = true
        scope.launch {
            val result = OtpRepository.verifyCode(formattedPhone, code, "reset")
            loading = false
            if (result.error == null && result.token != null) {
                otpToken = result.token
                error = null
                step = 2
            } else {
                error = result.error
                code = ""
            }
        }
    }

    fun savePassword() {
        val token = otpToken
        if (loading || token == null) return
        focusManager.clearFocus()
        loading = true
        scope.launch {
            val result = AuthRepository.resetPassword(phone, token, newPassword, confirm)
            loading = false
            when (result) {
                is AuthResult.Success -> { onNavigateToHome() }
                is AuthResult.Error -> { error = result.message }
            }
        }
    }

    LaunchedEffect(code) {
        if (step == 1 && code.length == 6) verifyCode()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        BackgroundAmbientGlow()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                AuthBackButton(
                    onClick = {
                        if (step == 1) {
                            step = 0
                            code = ""
                            error = null
                        } else {
                            onNavigateBack()
                        }
                    },
                    modifier = Modifier.padding(top = 8.dp, start = 16.dp)
                )
                AestheticLogo(modifier = Modifier.align(Alignment.CenterHorizontally), size = 180.dp)
            }

            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(topStart = 38.dp, topEnd = 38.dp),
                contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 18.dp, bottom = 18.dp)
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .width(36.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White.copy(alpha = 0.40f))
                )
                Spacer(modifier = Modifier.height(12.dp))

                when (step) {
                    0 -> {
                        Text("Parolni tiklash", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Ro'yxatdan o'tgan telefon raqamingizni kiriting, Telegram orqali kod yuboramiz.",
                            color = Color.White.copy(alpha = 0.70f),
                            fontSize = 14.sp,
                            lineHeight = 20.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        TrebleTextField(
                            label = "Telefon raqami",
                            value = phone,
                            onValueChange = { raw ->
                                val digits = raw.filter { it.isDigit() }
                                val cleaned = if (digits.length == 12 && digits.startsWith("998")) digits.drop(3) else digits
                                phone = cleaned.take(9)
                                error = null
                            },
                            placeholder = "90 123 45 67",
                            keyboardType = KeyboardType.Phone,
                            prefix = "+998",
                            imeAction = ImeAction.Done,
                            onDone = { sendCode() }
                        )
                        if (error != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            ErrorText(error)
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        GlassButton(text = if (loading) "Yuborilmoqda..." else "Kod yuborish", onClick = { sendCode() })
                    }

                    1 -> {
                        Text("Kodni kiriting", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (botUrl != null) "Kodni olish uchun $formattedPhone raqami ulangan Telegram akkauntingizda botimizni oching va \"Raqamni ulashish\" tugmasini bosing. Kod shu bot orqali keladi. Bir nechta akkauntingiz bo'lsa, to'g'risini tanlang." else "$formattedPhone raqami bilan bog'langan Telegram'ga 6 xonali kod yubordik.",
                            color = Color.White.copy(alpha = 0.70f),
                            fontSize = 14.sp,
                            lineHeight = 20.sp
                        )
                        if (botUrl != null) {
                            Spacer(modifier = Modifier.height(14.dp))
                            GlassButton(text = "Telegram botni ochish", onClick = { openTelegramChooser(ctx, botUrl!!) })
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Havolani nusxalash",
                                color = LimeGreen,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.clickable { copyLink(ctx, botUrl!!) }
                            )
                        }
                        Spacer(modifier = Modifier.height(20.dp))
                        OtpCodeField(
                            value = code,
                            onValueChange = { code = it; error = null },
                            hasError = error != null,
                            enabled = !loading
                        )
                        if (error != null) {
                            Spacer(modifier = Modifier.height(10.dp))
                            ErrorText(error)
                        }
                        Spacer(modifier = Modifier.height(18.dp))
                        GlassButton(text = if (loading) "Tekshirilmoqda..." else "Tasdiqlash", onClick = { verifyCode() })
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                            if (secondsLeft > 0) {
                                Text(
                                    text = "Kodni qayta yuborish: %d:%02d".format(secondsLeft / 60, secondsLeft % 60),
                                    color = TextSecondary,
                                    fontSize = 13.sp
                                )
                            } else {
                                Text(
                                    text = "Kodni qayta yuborish",
                                    color = LimeGreen,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { sendCode() }
                                        .padding(6.dp)
                                )
                            }
                        }
                    }

                    else -> {
                        Text("Yangi parol", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Telefon tasdiqlandi. Yangi parol o'ylab toping.",
                            color = Color.White.copy(alpha = 0.70f),
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        TrebleTextField(
                            label = "Yangi parol",
                            value = newPassword,
                            onValueChange = { newPassword = it; error = null },
                            placeholder = "kamida 6 ta belgi",
                            isPassword = true
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        TrebleTextField(
                            label = "Parolni takrorlang",
                            value = confirm,
                            onValueChange = { confirm = it; error = null },
                            isPassword = true,
                            imeAction = ImeAction.Done,
                            onDone = { savePassword() }
                        )
                        if (error != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            ErrorText(error)
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        GlassButton(text = if (loading) "Saqlanmoqda..." else "Saqlash va kirish", onClick = { savePassword() })
                    }
                }

                Spacer(modifier = Modifier.navigationBarsPadding().height(4.dp))
            }
        }
    }
}
