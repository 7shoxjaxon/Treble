package com.shohjahon.treble.ui.screens

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shohjahon.treble.data.models.AuthResult
import com.shohjahon.treble.data.repository.AuthRepository
import com.shohjahon.treble.ui.components.*
import com.shohjahon.treble.ui.theme.LimeGreen
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    onNavigateBack: () -> Unit,
    onNavigateToHome: () -> Unit,
    onNavigateToRegister: () -> Unit,
    onNavigateToForgot: () -> Unit
) {
    var loginInput by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()

    val submit: () -> Unit = {
        if (!loading) {
            focusManager.clearFocus()
            loading = true
            scope.launch {
                val result = AuthRepository.login(loginInput, passwordInput)
                loading = false
                when (result) {
                    is AuthResult.Success -> { onNavigateToHome() }
                    is AuthResult.Error -> { errorMessage = result.message }
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        BackgroundAmbientGlow(glowRight = true)

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
                    onClick = onNavigateBack,
                    modifier = Modifier.padding(top = 8.dp, start = 16.dp)
                )
                AestheticLogo(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(top = 4.dp),
                    size = 220.dp
                )
            }

            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(topStart = 40.dp, topEnd = 40.dp),
                contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 20.dp, bottom = 20.dp)
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .width(36.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White.copy(alpha = 0.40f))
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Hisobga kirish",
                    color = Color.White,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Logon va parolingizni kiriting.",
                    color = Color.White.copy(alpha = 0.70f),
                    fontSize = 14.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                TrebleTextField(
                    label = "Logon",
                    value = loginInput,
                    onValueChange = { loginInput = it; errorMessage = null }
                )
                Spacer(modifier = Modifier.height(12.dp))
                TrebleTextField(
                    label = "Parol",
                    value = passwordInput,
                    onValueChange = { passwordInput = it; errorMessage = null },
                    isPassword = true,
                    imeAction = ImeAction.Done,
                    onDone = { submit() }
                )

                Text(
                    text = "Parolni unutdingizmi?",
                    color = LimeGreen,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(top = 8.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onNavigateToForgot)
                        .padding(vertical = 4.dp, horizontal = 4.dp)
                )

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    ErrorText(errorMessage)
                }

                Spacer(modifier = Modifier.height(14.dp))

                GlassButton(text = if (loading) "Kuting..." else "Kirish", onClick = { submit() }, arrow = !loading)

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Hisobingiz yo'qmi? ",
                        color = Color.White.copy(alpha = 0.60f),
                        fontSize = 13.sp
                    )
                    Text(
                        text = "Ro'yxatdan o'tish",
                        color = LimeGreen,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable(onClick = onNavigateToRegister)
                    )
                }

                Spacer(modifier = Modifier.navigationBarsPadding().height(8.dp))
            }
        }
    }
}
