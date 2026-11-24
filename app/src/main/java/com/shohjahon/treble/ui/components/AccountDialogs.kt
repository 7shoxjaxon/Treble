package com.shohjahon.treble.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shohjahon.treble.data.models.AuthResult
import com.shohjahon.treble.data.repository.AuthRepository
import com.shohjahon.treble.ui.theme.TextSecondary

/** Parolni almashtirish. */
@Composable
fun ChangePasswordDialog(onDismiss: () -> Unit, onChanged: () -> Unit) {
    var oldPass by remember { mutableStateOf("") }
    var newPass by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    TrebleDialog(title = "Parolni o'zgartirish", onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
        ) {
            TrebleTextField(
                label = "Joriy parol",
                value = oldPass,
                onValueChange = { oldPass = it; error = null },
                isPassword = true
            )
            Spacer(modifier = Modifier.height(10.dp))
            TrebleTextField(
                label = "Yangi parol",
                value = newPass,
                onValueChange = { newPass = it; error = null },
                placeholder = "kamida 6 ta belgi",
                isPassword = true
            )
            Spacer(modifier = Modifier.height(10.dp))
            TrebleTextField(
                label = "Yangi parolni takrorlang",
                value = confirm,
                onValueChange = { confirm = it; error = null },
                isPassword = true,
                imeAction = ImeAction.Done
            )
            if (error != null) {
                Spacer(modifier = Modifier.height(8.dp))
                ErrorText(error)
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            DialogButton("Bekor qilish", onDismiss, Modifier.weight(1f))
            DialogButton(
                text = if (loading) "Kuting..." else "O'zgartirish",
                onClick = {
                    if (!loading) {
                        loading = true
                        scope.launch {
                            val r = AuthRepository.changePassword(oldPass, newPass, confirm)
                            loading = false
                            when (r) {
                                is AuthResult.Success -> { onChanged() }
                                is AuthResult.Error -> { error = r.message }
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

/** Akkauntni o'chirish: parolni so'raydi. */
@Composable
fun DeleteAccountDialog(onDismiss: () -> Unit, onDeleted: () -> Unit) {
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    TrebleDialog(title = "Akkauntni o'chirish", onDismiss = onDismiss) {
        Text(
            text = "Hisobingiz butunlay o'chiriladi va o'tmagan o'yinlardan chiqarilasiz. Tasdiqlash uchun parolingizni kiriting.",
            color = TextSecondary,
            fontSize = 13.sp,
            lineHeight = 19.sp
        )
        Spacer(modifier = Modifier.height(12.dp))
        TrebleTextField(
            label = "Parol",
            value = password,
            onValueChange = { password = it; error = null },
            isPassword = true,
            imeAction = ImeAction.Done
        )
        if (error != null) {
            Spacer(modifier = Modifier.height(8.dp))
            ErrorText(error)
        }
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            DialogButton("Bekor qilish", onDismiss, Modifier.weight(1f))
            DialogButton(
                text = if (loading) "Kuting..." else "O'chirish",
                onClick = {
                    if (!loading) {
                        loading = true
                        scope.launch {
                            val r = AuthRepository.deleteAccount(password)
                            loading = false
                            when (r) {
                                is AuthResult.Success -> { onDeleted() }
                                is AuthResult.Error -> { error = r.message }
                            }
                        }
                    }
                },
                modifier = Modifier.weight(1f),
                danger = true
            )
        }
    }
}
