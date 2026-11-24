package com.shohjahon.treble.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.shape.CircleShape
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shohjahon.treble.R
import com.shohjahon.treble.ui.theme.*

/** Logotip: qora fon shaffoflikka aylantiriladi. Hech qanday yorug'lik effektisiz. */
@Composable
fun AestheticLogo(
    modifier: Modifier = Modifier,
    size: Dp = 340.dp
) {
    val context = LocalContext.current
    val transparentBitmap = remember {
        val options = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
        val original = BitmapFactory.decodeResource(context.resources, R.drawable.treble_logo, options)
        val w = original.width
        val h = original.height
        val pixels = IntArray(w * h)
        original.getPixels(pixels, 0, w, 0, 0, w, h)
        for (i in pixels.indices) {
            val p = pixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            val brightness = maxOf(r, maxOf(g, b))
            pixels[i] = (brightness shl 24) or 0x00FFFFFF
        }
        Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888).asImageBitmap()
    }

    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Image(
            bitmap = transparentBitmap,
            contentDescription = "Treble Logo",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
        )
    }
}

/** Shishasimon karta: yarim shaffof to'q fon, yengil yorug'lik va nozik chegara. */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(30.dp),
    contentPadding: PaddingValues = PaddingValues(20.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(Color(0x990A1017))
            .background(Brush.linearGradient(listOf(Color(0x2EFFFFFF), Color(0x08FFFFFF))))
            .border(1.dp, Brush.linearGradient(listOf(Color(0x66FFFFFF), Color(0x14FFFFFF))), shape)
            .padding(contentPadding)
    ) {
        Column(content = content)
    }
}

/** Asosiy tugma: laym gradient, to'liq yumaloq, yumshoq yorug'lik. [arrow] = o'ng tomonda strelka. */
@Composable
fun GlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    arrow: Boolean = false
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(58.dp)
            .shadow(
                elevation = 16.dp,
                shape = CircleShape,
                ambientColor = LimeGreen.copy(alpha = 0.5f),
                spotColor = LimeGreen.copy(alpha = 0.7f)
            )
            .clip(CircleShape)
            .background(Brush.verticalGradient(listOf(LimeButtonTop, LimeButtonBottom)))
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            color = DarkText,
            fontSize = 17.sp,
            fontWeight = FontWeight.ExtraBold
        )
        if (arrow) {
            Spacer(modifier = Modifier.width(10.dp))
            TrebleIcon(IconPaths.ArrowForward, DarkText, size = 22.dp)
        }
    }
}

/** Ikkinchi darajali tugma: shishasimon yumaloq. */
@Composable
fun GlassSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(CircleShape)
            .background(Brush.verticalGradient(listOf(Color(0x33FFFFFF), Color(0x14FFFFFF))))
            .border(1.dp, Color(0x40FFFFFF), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = TextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/** Shishasimon kichik yorliq (masalan, "8/10 o'yinchi qo'shildi"). */
@Composable
fun GlassChip(
    text: String,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(Color(0x59FFFFFF), Color(0x1AFFFFFF))))
            .border(1.dp, Color(0x40FFFFFF), CircleShape)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leading != null) {
            leading()
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(text = text, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

/** Yashil nuqta (jonli holat belgisi). */
@Composable
fun LimeDot(size: Dp = 10.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(LimeGreen)
    )
}

/** Shishasimon savol oynasi ("Ha" / "Yoq"). [dismissable] = false bo'lsa, tashqariga bosib yoki "orqaga" bilan yopilmaydi. */
@Composable
fun GlassDialog(
    title: String,
    text: String,
    confirmText: String,
    dismissText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismissable: Boolean = true
) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = { if (dismissable) onDismiss() },
        properties = androidx.compose.ui.window.DialogProperties(
            dismissOnBackPress = dismissable,
            dismissOnClickOutside = dismissable
        )
    ) {
        val shape = RoundedCornerShape(30.dp)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(Color(0xF2101C2A))
                .border(1.dp, GlassBorder, shape)
                .padding(24.dp)
        ) {
            Text(
                text = title,
                color = TextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(text = text, color = TextSecondary, fontSize = 15.sp, lineHeight = 21.sp)
            Spacer(modifier = Modifier.height(22.dp))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                GlassSecondaryButton(text = dismissText, onClick = onDismiss, modifier = Modifier.weight(1f))
                Spacer(modifier = Modifier.width(12.dp))
                GlassButton(text = confirmText, onClick = onConfirm, modifier = Modifier.weight(1f))
            }
        }
    }
}
