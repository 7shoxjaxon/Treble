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

@Composable
fun RegistrationScreen(
    onNavigateBack: () -> Unit,
    onNavigateToHome: () -> Unit,
    onNavigateToLogin: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var login by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // 0 = ma'lumotlar, 1 = SMS kod
    var step by remember { mutableStateOf(0) }
    var code by remember { mutableStateOf("") }
    var codeError by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var secondsLeft by remember { mutableStateOf(0) }
    var sendCount by remember { mutableStateOf(0) }
    var botUrl by remember { mutableStateOf<String?>(null) }
    val ctx = LocalContext.current

    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val formattedPhone = AuthRepository.normalizePhone(phone) ?: ""

    // Qayta yuborish uchun orqaga sanash
    LaunchedEffect(sendCount) {
        while (secondsLeft > 0) {
            delay(1000L)
            secondsLeft -= 1
        }
    }

    BackHandler(enabled = step == 1) {
        step = 0
        code = ""
        codeError = null
    }

    suspend fun finishRegistration(otpToken: String?) {
        val result = AuthRepository.register(name, phone, login, password, confirm, otpToken)
        loading = false
        when (result) {
            is AuthResult.Success -> { onNavigateToHome() }
            is AuthResult.Error -> {
                step = 0
                errorMessage = result.message
            }
        }
    }

    fun requestCode() {
        if (loading) return
        focusManager.clearFocus()
        val check = AuthRepository.validateRegistration(name, phone, login, password, confirm)
        if (check is AuthResult.Error) {
            errorMessage = check.message
            return
        }
        loading = true
        scope.launch {
            val result = OtpRepository.sendCode(formattedPhone, "register")
            if (result.error != null) {
                loading = false
                errorMessage = result.error
            } else if (result.skipped) {
                // Server SMS tasdiqlashni talab qilmaydi
                finishRegistration(null)
            } else {
                loading = false
                step = 1
                code = ""
                codeError = null
                secondsLeft = result.retryAfter
                botUrl = result.botUrl
                sendCount += 1
            }
        }
    }

    fun resendCode() {
        if (loading || secondsLeft > 0) return
        loading = true
        scope.launch {
            val result = OtpRepository.sendCode(formattedPhone, "register")
            loading = false
            code = ""
            if (result.error == null) {
                codeError = null
                secondsLeft = result.retryAfter
                botUrl = result.botUrl
                sendCount += 1
            } else {
                codeError = result.error
            }
        }
    }

    fun verifyCode() {
        if (loading || code.length != 6) return
        loading = true
        scope.launch {
            val result = OtpRepository.verifyCode(formattedPhone, code, "register")
            if (result.error == null && result.token != null) {
                finishRegistration(result.token)
            } else {
                loading = false
                codeError = result.error
                code = ""
            }
        }
    }

    // 6 ta raqam kiritilishi bilan o'zi tekshiradi
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
                            codeError = null
                        } else {
                            onNavigateBack()
                        }
                    },
                    modifier = Modifier.padding(top = 8.dp, start = 16.dp)
                )
                AestheticLogo(
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    size = if (step == 0) 150.dp else 190.dp
                )
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

                if (step == 0) {
                    // ---------------- 1-bosqich: ma'lumotlar ----------------
                    Text(
                        text = "Xush kelibsiz",
                        color = Color.White,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Hisob yarating: logon va parol o'ylab toping.",
                        color = Color.White.copy(alpha = 0.70f),
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    TrebleTextField(
                        label = "Ism va familiya",
                        value = name,
                        onValueChange = { name = it; errorMessage = null },
                        placeholder = "Masalan: Jasur Rahimov"
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    TrebleTextField(
                        label = "Telefon raqami",
                        value = phone,
                        onValueChange = { raw ->
                            val digits = raw.filter { it.isDigit() }
                            val cleaned = if (digits.length == 12 && digits.startsWith("998")) digits.drop(3) else digits
                            phone = cleaned.take(9)
                            errorMessage = null
                        },
                        placeholder = "90 123 45 67",
                        keyboardType = KeyboardType.Phone,
                        prefix = "+998"
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    TrebleTextField(
                        label = "Logon (kirish uchun)",
                        value = login,
                        onValueChange = { login = it.filter { c -> !c.isWhitespace() }.take(20); errorMessage = null },
                        placeholder = "kamida 4 ta belgi"
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    TrebleTextField(
                        label = "Parol",
                        value = password,
                        onValueChange = { password = it; errorMessage = null },
                        placeholder = "kamida 6 ta belgi",
                        isPassword = true
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    TrebleTextField(
                        label = "Parolni takrorlang",
                        value = confirm,
                        onValueChange = { confirm = it; errorMessage = null },
                        isPassword = true,
                        imeAction = ImeAction.Done,
                        onDone = { requestCode() }
                    )

                    if (errorMessage != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        ErrorText(errorMessage)
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    GlassButton(
                        text = if (loading) "Kuting..." else "Davom etish",
                        onClick = { requestCode() }
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Telefon raqamingiz bilan bog'langan Telegram'ga tasdiqlash kodi yuboriladi.",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Hisobingiz bormi? ",
                            color = Color.White.copy(alpha = 0.60f),
                            fontSize = 13.sp
                        )
                        Text(
                            text = "Kirish",
                            color = LimeGreen,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable(onClick = onNavigateToLogin)
                        )
                    }
                } else {
                    // ---------------- 2-bosqich: SMS kod ----------------
                    Text(
                        text = "Telefonni tasdiqlang",
                        color = Color.White,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (botUrl != null) "Kodni olish uchun $formattedPhone raqami ulangan Telegram akkauntingizda botimizni oching va \"Raqamni ulashish\" tugmasini bosing. Kod shu bot orqali keladi. Bir nechta akkauntingiz bo'lsa, to'g'risini tanlang." else "$formattedPhone raqami bilan bog'langan Telegram'ga 6 xonali kod yubordik. Telegram'ni ochib, kodni quyiga kiriting.",
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
                        onValueChange = { code = it; codeError = null },
                        hasError = codeError != null,
                        enabled = !loading
                    )

                    if (codeError != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        ErrorText(codeError)
                    }

                    Spacer(modifier = Modifier.height(18.dp))
                    GlassButton(
                        text = if (loading) "Tekshirilmoqda..." else "Tasdiqlash",
                        onClick = { verifyCode() }
                    )

                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
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
                                    .clickable { resendCode() }
                                    .padding(6.dp)
                            )
                        }
                    }
                    Text(
                        text = "Raqamni o'zgartirish",
                        color = Color.White.copy(alpha = 0.60f),
                        fontSize = 13.sp,
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                step = 0
                                code = ""
                                codeError = null
                            }
                            .padding(6.dp)
                    )
                }

                Spacer(modifier = Modifier.navigationBarsPadding().height(4.dp))
            }
        }
    }
}
