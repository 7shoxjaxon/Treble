package com.shohjahon.treble.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shohjahon.treble.ui.components.*
import com.shohjahon.treble.ui.theme.LimeGreen
import com.shohjahon.treble.ui.theme.TextPrimary
import com.shohjahon.treble.ui.theme.TextSecondary

@Composable
fun IntroScreen(
    onNavigateToRegister: () -> Unit,
    onNavigateToLogin: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        BackgroundAmbientGlow(pitch = true)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            GlassChip(text = "3 ta o'yin yaqin atrofda", leading = { LimeDot() })

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                AestheticLogo(size = 290.dp)
            }

            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                GlassChip(
                    text = "8/10 o'yinchi qo'shildi",
                    leading = { TrebleIcon(IconPaths.People, LimeGreen, size = 20.dp) }
                )
            }
            Spacer(modifier = Modifier.height(16.dp))

            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(34.dp),
                contentPadding = PaddingValues(24.dp)
            ) {
                Text(
                    text = "Maydonga chiq.\nJamoangni top.",
                    color = TextPrimary,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontStyle = FontStyle.Italic,
                    lineHeight = 35.sp
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Yaqin atrofdagi o'yinlarni toping, joyingizni band qiling va futbolchilar bilan tanishing.",
                    color = TextSecondary,
                    fontSize = 15.sp,
                    lineHeight = 22.sp
                )
                Spacer(modifier = Modifier.height(22.dp))
                GlassButton(text = "Boshlash", onClick = onNavigateToRegister, arrow = true)
                Spacer(modifier = Modifier.height(10.dp))
                GlassSecondaryButton(text = "Menda hisob bor", onClick = onNavigateToLogin)
            }
        }
    }
}
