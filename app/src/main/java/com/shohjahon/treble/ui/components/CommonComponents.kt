package com.shohjahon.treble.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import coil.compose.SubcomposeAsyncImage
import com.shohjahon.treble.data.util.ImageStore
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.shohjahon.treble.ui.theme.*

val ErrorRed = Color(0xFFFF6B6B)
val DialogBg = Surface1
val FieldBg = Surface2

// ------------------------------------------------------------------ Matn maydoni

/**
 * Umumiy matn maydoni (login, ro'yxatdan o'tish, profil).
 * [prefix] berilsa (masalan "+998") maydon boshida ko'rsatiladi.
 */
@Composable
fun TrebleTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    isPassword: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    prefix: String? = null,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    minHeight: Dp = 52.dp,
    onDone: (() -> Unit)? = null
) {
    var passwordVisible by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            color = TextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = minHeight)
                .clip(RoundedCornerShape(14.dp))
                .background(if (enabled) Surface2 else Surface1)
                .border(
                    1.dp,
                    if (focused) LimeGreen.copy(alpha = 0.7f) else Outline,
                    RoundedCornerShape(14.dp)
                )
                .padding(horizontal = 14.dp, vertical = if (singleLine) 0.dp else 12.dp),
            contentAlignment = if (singleLine) Alignment.CenterStart else Alignment.TopStart
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top
            ) {
                if (prefix != null) {
                    Text(
                        text = prefix,
                        color = TextSecondary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                }

                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    enabled = enabled,
                    singleLine = singleLine,
                    visualTransformation = if (isPassword && !passwordVisible) {
                        PasswordVisualTransformation()
                    } else {
                        VisualTransformation.None
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (isPassword) KeyboardType.Password else keyboardType,
                        imeAction = if (singleLine) imeAction else ImeAction.Default
                    ),
                    keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }),
                    cursorBrush = SolidColor(LimeGreen),
                    textStyle = TextStyle(
                        color = if (enabled) TextPrimary else TextSecondary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    decorationBox = { innerTextField ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (value.isEmpty() && placeholder.isNotEmpty()) {
                                Text(
                                    text = placeholder,
                                    color = TextSecondary.copy(alpha = 0.6f),
                                    fontSize = 15.sp
                                )
                            }
                            innerTextField()
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .onFocusChanged { focused = it.isFocused }
                )

                if (isPassword) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (passwordVisible) "Yashir" else "Ko'rsat",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { passwordVisible = !passwordVisible }
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    )
                }
                if (!enabled) {
                    Spacer(modifier = Modifier.width(8.dp))
                    TrebleIcon(IconPaths.Lock, TextSecondary, size = 16.dp)
                }
            }
        }
    }
}

@Composable
fun ErrorText(message: String?, modifier: Modifier = Modifier) {
    if (message != null) {
        Text(
            text = message,
            color = ErrorRed,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 16.sp,
            modifier = modifier
        )
    }
}

// ------------------------------------------------------------------ Dialoglar

@Composable
fun TrebleDialog(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 640.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Surface1)
                .border(1.dp, OutlineStrong, RoundedCornerShape(24.dp))
                .padding(20.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconCircleButton(IconPaths.Close, onDismiss, size = 32.dp)
                }
                Spacer(modifier = Modifier.height(14.dp))
                content()
            }
        }
    }
}

/** Dialog va sahifalardagi tugma. [primary] = yashil, [danger] = qizil, aks holda kulrang. */
@Composable
fun DialogButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    danger: Boolean = false,
    enabled: Boolean = true
) {
    val base = modifier
        .height(48.dp)
        .clip(RoundedCornerShape(14.dp))
    val styled = when {
        !enabled -> base.background(Surface2)
        primary -> base.background(LimeGreen)
        danger -> base
            .background(ErrorRed.copy(alpha = 0.12f))
            .border(1.dp, ErrorRed.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
        else -> base
            .background(Surface2)
            .border(1.dp, Outline, RoundedCornerShape(14.dp))
    }
    Box(
        modifier = if (enabled) styled.clickable(onClick = onClick) else styled,
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = when {
                !enabled -> TextSecondary
                primary -> DarkText
                danger -> ErrorRed
                else -> TextPrimary
            },
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 12.dp)
        )
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    danger: Boolean = false
) {
    TrebleDialog(title = title, onDismiss = onDismiss) {
        Text(
            text = message,
            color = TextSecondary,
            fontSize = 14.sp,
            lineHeight = 20.sp
        )
        Spacer(modifier = Modifier.height(18.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            DialogButton(text = "Bekor qilish", onClick = onDismiss, modifier = Modifier.weight(1f))
            DialogButton(
                text = confirmText,
                onClick = onConfirm,
                modifier = Modifier.weight(1f),
                primary = !danger,
                danger = danger
            )
        }
    }
}

// ------------------------------------------------------------------ Kichik elementlar

@Composable
fun EmptyState(
    iconPath: String,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 36.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(Surface2),
            contentAlignment = Alignment.Center
        ) {
            TrebleIcon(iconPath, TextSecondary, size = 28.dp)
        }
        Spacer(modifier = Modifier.height(14.dp))
        Text(text = title, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = subtitle,
            color = TextSecondary,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        if (actionText != null && onAction != null) {
            Spacer(modifier = Modifier.height(16.dp))
            DialogButton(text = actionText, onClick = onAction, primary = true, modifier = Modifier.widthIn(min = 180.dp))
        }
    }
}

@Composable
fun SelectChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val shape = androidx.compose.foundation.shape.CircleShape
    Box(
        modifier = Modifier
            .clip(shape)
            .background(
                if (selected) androidx.compose.ui.graphics.Brush.verticalGradient(listOf(LimeButtonTop, LimeButtonBottom))
                else androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0x2EFFFFFF), Color(0x14FFFFFF)))
            )
            .border(1.dp, if (selected) Color.Transparent else Color(0x33FFFFFF), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 11.dp)
    ) {
        Text(
            text = text,
            color = if (selected) DarkText else TextPrimary,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

@Composable
fun StatusPill(text: String, background: Color, foreground: Color, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(background)
            .padding(horizontal = 9.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(5.dp)
                .clip(CircleShape)
                .background(foreground)
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(text = text, color = foreground, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
fun IconCircleButton(
    iconPath: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    background: Color = Surface2,
    tint: Color = TextPrimary
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(background)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        TrebleIcon(iconPath, tint, size = size * 0.5f)
    }
}

@Composable
fun AuthBackButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    IconCircleButton(IconPaths.ArrowBack, onClick, modifier = modifier, size = 44.dp)
}

@Composable
fun InitialAvatar(
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    background: Color = LimeDeep,
    foreground: Color = LimeGreen
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(background),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = name.trim().take(1).uppercase().ifEmpty { "?" },
            color = foreground,
            fontSize = (size.value * 0.42f).sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/** Profil rasmi. Rasm bo'lmasa yoki yuklanmasa, ismning bosh harfi ko'rsatiladi. */
@Composable
fun UserAvatar(
    name: String,
    avatar: String?,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp
) {
    if (avatar.isNullOrBlank()) {
        InitialAvatar(name = name, size = size, modifier = modifier)
        return
    }
    SubcomposeAsyncImage(
        model = ImageStore.model(avatar),
        contentDescription = name,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(LimeDeep),
        loading = { InitialAvatar(name = name, size = size) },
        error = { InitialAvatar(name = name, size = size) }
    )
}

/** Ustma-ust joylashgan avatarlar: "A B C +3". [people] = (ism, rasm havolasi) juftliklari. */
@Composable
fun AvatarStack(people: List<Pair<String, String>>, modifier: Modifier = Modifier, max: Int = 5, size: Dp = 34.dp) {
    val names = people
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        names.take(max).forEachIndexed { index, (name, avatar) ->
            UserAvatar(
                name = name,
                avatar = avatar,
                size = size,
                modifier = Modifier
                    .offset(x = (-8 * index).dp)
                    .border(2.dp, Surface1, CircleShape)
            )
        }
        if (names.size > max) {
            Box(
                modifier = Modifier
                    .offset(x = (-8 * max).dp)
                    .size(size)
                    .clip(CircleShape)
                    .background(Surface2)
                    .border(2.dp, Surface1, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "+${names.size - max}",
                    color = TextPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/** Ichki sahifalar uchun sarlavha + orqaga tugmasi. */
@Composable
fun SubPageScaffold(
    title: String,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .auroraBackground()
            .statusBarsPadding()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            IconCircleButton(IconPaths.ArrowBack, onBack, modifier = Modifier.align(Alignment.CenterStart), size = 42.dp)
            Text(
                text = title,
                color = TextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Center)
            )
        }
        content()
    }
}

/** Tekis (yassi) karta. */
@Composable
fun SurfaceCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(24.dp)
    val base = modifier
        .clip(shape)
        .background(Color(0x990A1017))
        .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0x2EFFFFFF), Color(0x08FFFFFF))))
        .border(
            1.dp,
            androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0x59FFFFFF), Color(0x14FFFFFF))),
            shape
        )
    Column(
        modifier = (if (onClick != null) base.clickable(onClick = onClick) else base).padding(contentPadding),
        content = content
    )
}

/** Ikki-uch bo'limli tanlagich ("Faol | Tarix"). */
@Composable
fun SegmentedTabs(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Surface1)
            .border(1.dp, Outline, RoundedCornerShape(14.dp))
            .padding(4.dp)
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (selected) LimeDeep else Color.Transparent)
                    .clickable { onSelect(index) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    color = if (selected) LimeGreen else TextSecondary,
                    fontSize = 14.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                )
            }
        }
    }
}

/**
 * 6 xonali SMS kod kiritish maydoni. Ko'rinadigan katakchalar ostida ko'rinmas matn maydoni turadi:
 * raqamlar kiritilganda katakchalar to'ladi.
 */
@Composable
fun OtpCodeField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    length: Int = 6,
    hasError: Boolean = false,
    enabled: Boolean = true
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        try {
            focusRequester.requestFocus()
        } catch (e: Exception) {
            // fokus keyin ham berilishi mumkin
        }
    }

    Box(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            for (i in 0 until length) {
                val ch = value.getOrNull(i)?.toString() ?: ""
                val active = enabled && i == value.length.coerceAtMost(length - 1)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(58.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Surface2)
                        .border(
                            1.dp,
                            when {
                                hasError -> ErrorRed
                                active -> LimeGreen
                                else -> Outline
                            },
                            RoundedCornerShape(14.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = ch,
                        color = TextPrimary,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        BasicTextField(
            value = value,
            onValueChange = { raw -> onValueChange(raw.filter { it.isDigit() }.take(length)) },
            enabled = enabled,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            textStyle = TextStyle(color = Color.Transparent),
            cursorBrush = SolidColor(Color.Transparent),
            modifier = Modifier
                .matchParentSize()
                .alpha(0f)
                .focusRequester(focusRequester)
        )
    }
}
