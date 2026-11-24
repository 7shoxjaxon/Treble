package com.shohjahon.treble.ui.screens

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.shohjahon.treble.R
import kotlinx.coroutines.launch

/** Animatsiya davomiyligi. Dizayndagi 6 soniyalik versiya ilova ochilishi uchun qisqartirilgan. */
private const val SPLASH_DURATION_MS = 4200

private val SplashEasing = CubicBezierEasing(0.22f, 0.8f, 0.3f, 1f)

// ---- Kalit kadrlar: [vaqt0, qiymat0, vaqt1, qiymat1, ...] (vaqt 0..1 oralig'ida) ----
private val GLOW_ALPHA = floatArrayOf(0f, 0f, .10f, 0f, .35f, .9f, .60f, .5f, .88f, .7f, 1f, 0f)
private val GLOW_SCALE = floatArrayOf(0f, .6f, .10f, .6f, .35f, 1f, .60f, 1.15f, .88f, 1.05f, 1f, 1.2f)
private val RING_ALPHA = floatArrayOf(0f, 0f, .28f, 0f, .30f, .7f, .55f, 0f, 1f, 0f)
private val RING_SCALE = floatArrayOf(0f, .5f, .28f, .5f, .55f, 1.9f, 1f, 1.9f)

private val PLAYER_ALPHA = floatArrayOf(0f, 0f, .12f, 0f, .18f, 1f, .88f, 1f, 1f, 0f)
private val PLAYER_X = floatArrayOf(0f, -.45f, .12f, -.45f, .28f, 0f, 1f, 0f)
private val PLAYER_SCALE = floatArrayOf(0f, 1.05f, .12f, 1.05f, .28f, 1f, .88f, 1f, 1f, 1.08f)
private val PLAYER_BLUR = floatArrayOf(0f, 14f, .12f, 14f, .18f, 8f, .28f, 0f, 1f, 0f)

private val BALL_ALPHA = floatArrayOf(0f, 0f, .03f, 0f, .10f, 1f, .88f, 1f, 1f, 0f)
private val BALL_SCALE = floatArrayOf(0f, .3f, .03f, .3f, .10f, 1.12f, .14f, 1f, .88f, 1f, 1f, 1.08f)
private val BALL_FLASH = floatArrayOf(0f, 0f, .27f, 0f, .285f, .9f, .37f, 0f, 1f, 0f)

private val WORD_ALPHA = floatArrayOf(0f, 0f, .31f, 0f, .32f, 1f, .88f, 1f, 1f, 0f)
private val WORD_CLIP = floatArrayOf(0f, 1f, .32f, 1f, .41f, 0f, 1f, 0f)

private val T1_ALPHA = floatArrayOf(0f, 0f, .45f, 0f, .50f, 1f, .88f, 1f, 1f, 0f)
private val T1_Y = floatArrayOf(0f, 8f, .45f, 8f, .50f, 0f, 1f, 0f)
private val T2_ALPHA = floatArrayOf(0f, 0f, .51f, 0f, .56f, 1f, .88f, 1f, 1f, 0f)
private val T2_Y = floatArrayOf(0f, 8f, .51f, 8f, .56f, 0f, 1f, 0f)
private val T3_ALPHA = floatArrayOf(0f, 0f, .57f, 0f, .62f, 1f, .88f, 1f, 1f, 0f)
private val T3_Y = floatArrayOf(0f, 8f, .57f, 8f, .62f, 0f, 1f, 0f)

private val BAR_BOX_ALPHA = floatArrayOf(0f, 0f, .28f, 0f, .34f, 1f, .90f, 1f, 1f, 0f)
private val BAR_FILL = floatArrayOf(0f, 0f, .30f, 0f, .85f, 1f, 1f, 1f)

/** Kalit kadrlar bo'yicha qiymat hisoblaydi (har oraliqda CSS dagi kabi yumshoq tezlik egri chizig'i). */
private fun kf(p: Float, s: FloatArray): Float {
    if (p <= s[0]) return s[1]
    var i = 0
    while (i + 3 < s.size) {
        val t0 = s[i]
        val v0 = s[i + 1]
        val t1 = s[i + 2]
        val v1 = s[i + 3]
        if (p <= t1) {
            val f = if (t1 == t0) 1f else (p - t0) / (t1 - t0)
            return v0 + (v1 - v0) * SplashEasing.transform(f)
        }
        i += 2
    }
    return s[s.size - 1]
}

/** Logotip rasmining (sheet) bir bo'lagini, rasmdagi o'zining joyiga chizadi. Qiymatlar 0..1 ulushlarda. */
private fun DrawScope.drawPart(sheet: ImageBitmap, l: Float, t: Float, r: Float, b: Float) {
    val srcOffset = IntOffset((l * sheet.width).toInt(), (t * sheet.height).toInt())
    val srcSize = IntSize(((r - l) * sheet.width).toInt(), ((b - t) * sheet.height).toInt())
    val dstOffset = IntOffset((l * size.width).toInt(), (t * size.height).toInt())
    val dstSize = IntSize(((r - l) * size.width).toInt(), ((b - t) * size.height).toInt())
    drawImage(
        image = sheet,
        srcOffset = srcOffset,
        srcSize = srcSize,
        dstOffset = dstOffset,
        dstSize = dstSize,
        filterQuality = FilterQuality.High
    )
}

/**
 * Ilova ochilishidagi kirish animatsiyasi. Ekranga tegilsa yoki "orqaga" bosilsa o'tkazib yuboriladi.
 * Tugagach qora parda yumshoq so'nib, ilovaning o'zi ko'rinadi.
 */
@Composable
fun SplashScreen(onFinished: () -> Unit) {
    val sheet = ImageBitmap.imageResource(R.drawable.splash_sheet)
    val progress = remember { Animatable(0f) }
    val overlayAlpha = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    var finishing by remember { mutableStateOf(false) }

    fun finish() {
        if (finishing) return
        finishing = true
        scope.launch {
            overlayAlpha.animateTo(0f, tween(durationMillis = 320))
            onFinished()
        }
    }

    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(durationMillis = SPLASH_DURATION_MS, easing = LinearEasing))
        finish()
    }

    BackHandler(enabled = true) { finish() }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = overlayAlpha.value }
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { finish() },
        contentAlignment = Alignment.Center
    ) {
        val boxWidth = minOf(330.dp, maxWidth * 0.92f)
        val k = boxWidth / 330.dp

        // Yashil yorug'lik
        Box(
            modifier = Modifier
                .size(360.dp * k)
                .graphicsLayer {
                    val p = progress.value
                    alpha = kf(p, GLOW_ALPHA)
                    val s = kf(p, GLOW_SCALE)
                    scaleX = s
                    scaleY = s
                }
                .drawBehind {
                    val r = size.minDimension / 2f
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0xF22B562D), Color(0x002B562D)),
                            center = center,
                            radius = r * 0.96f
                        ),
                        radius = r
                    )
                }
        )

        // Kengayuvchi halqa
        Box(
            modifier = Modifier
                .size(200.dp * k)
                .graphicsLayer {
                    val p = progress.value
                    alpha = kf(p, RING_ALPHA)
                    val s = kf(p, RING_SCALE)
                    scaleX = s
                    scaleY = s
                }
                .border(2.dp, Color(0xFF4C8F50), CircleShape)
        )

        // Logotip qatlamlari
        Box(
            modifier = Modifier
                .width(boxWidth)
                .aspectRatio(1252f / 1254f)
        ) {
            // Futbolchi: chapdan xiralashib kiradi
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val p = progress.value
                        alpha = kf(p, PLAYER_ALPHA)
                        translationX = kf(p, PLAYER_X) * size.width
                        val s = kf(p, PLAYER_SCALE)
                        scaleX = s
                        scaleY = s
                        val blur = kf(p, PLAYER_BLUR) * density * k
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && blur > 0.5f) {
                            renderEffect = BlurEffect(blur, blur, TileMode.Decal)
                        }
                    }
            ) {
                drawPart(sheet, 0f, 0f, 0.775f, 0.616f)
            }

            // To'p: paydo bo'ladi, keyin yarqiraydi
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val p = progress.value
                        alpha = kf(p, BALL_ALPHA)
                        val s = kf(p, BALL_SCALE)
                        scaleX = s
                        scaleY = s
                    }
            ) {
                val cx = 0.8243f * size.width
                val cy = 0.3042f * size.height
                val radius = 0.0503f * size.width
                val flash = kf(progress.value, BALL_FLASH)
                if (flash > 0.01f) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color.White.copy(alpha = flash * 0.55f), Color.Transparent),
                            center = Offset(cx, cy),
                            radius = radius * 2.6f
                        ),
                        radius = radius * 2.6f,
                        center = Offset(cx, cy)
                    )
                }
                val path = Path().apply { addOval(Rect(Offset(cx, cy), radius)) }
                clipPath(path) {
                    drawPart(
                        sheet,
                        0.8243f - 0.0503f, 0.3042f - 0.0503f * size.width / size.height,
                        0.8243f + 0.0503f, 0.3042f + 0.0503f * size.width / size.height
                    )
                }
            }

            // "TREBLE" yozuvi: chapdan o'ngga ochiladi
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = kf(progress.value, WORD_ALPHA) }
            ) {
                val visible = 1f - kf(progress.value, WORD_CLIP)
                clipRect(left = 0f, top = 0f, right = size.width * visible, bottom = size.height) {
                    drawPart(sheet, 0f, 0.618f, 1f, 0.778f)
                }
            }

            // PLAY · CONNECT · ENJOY: bittalab paydo bo'ladi
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val p = progress.value
                        alpha = kf(p, T1_ALPHA)
                        translationY = kf(p, T1_Y) * density * k
                    }
            ) { drawPart(sheet, 0.17f, 0.785f, 0.31f, 0.821f) }

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val p = progress.value
                        alpha = kf(p, T2_ALPHA)
                        translationY = kf(p, T2_Y) * density * k
                    }
            ) { drawPart(sheet, 0.31f, 0.785f, 0.665f, 0.821f) }

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val p = progress.value
                        alpha = kf(p, T3_ALPHA)
                        translationY = kf(p, T3_Y) * density * k
                    }
            ) { drawPart(sheet, 0.665f, 0.785f, 0.84f, 0.821f) }
        }

        // Pastdagi yuklanish chizig'i
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 96.dp)
                .fillMaxWidth(0.615f)
                .height(4.dp)
                .graphicsLayer { alpha = kf(progress.value, BAR_BOX_ALPHA) }
                .clip(RoundedCornerShape(2.dp))
                .background(Color(0xFF1E1E1E))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = kf(progress.value, BAR_FILL)
                        transformOrigin = TransformOrigin(0f, 0.5f)
                    }
                    .background(Color.White)
            )
        }
    }
}
