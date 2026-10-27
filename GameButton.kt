package com.jungdo.attend.ui.game

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val DisabledFace = Color(0xFF9CA3AF)

/**
 * 게임 버튼: 누르면 쏙 들어가고(두께가 눌림), 떼면 통! 튕겨 올라와요.
 * 누르는 순간 소리·진동이 나고, 그다음 onClick 이 실행돼요.
 */
@Composable
fun GameButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = LocalGameTheme.current.primary,
    fontSize: TextUnit = 20.sp,
    enabled: Boolean = true,
    sound: GameSound.Fx? = GameSound.Fx.TAP,
    haptic: GameHaptic.Kind? = GameHaptic.Kind.TICK,
    leading: (@Composable () -> Unit)? = null,   // 글자 왼쪽에 놓을 그림 (예: 계급 그림)
    fontFamily: FontFamily? = null,
) {
    val theme = LocalGameTheme.current
    if (theme.skin != null) {
        SkinButton(text, onClick, modifier, color, fontSize, enabled, sound, haptic, leading, fontFamily)
        return
    }
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val down = pressed && enabled

    val scale by animateFloatAsState(
        targetValue = if (down) theme.pressScale else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "gameBtnScale",
    )
    val depth = theme.buttonDepth.dp
    // 눌리면 얼굴이 아래 "두께" 쪽으로 내려가 거의 납작해져요
    val faceShift by animateDpAsState(
        targetValue = if (down && theme.buttonDepth > 1) (theme.buttonDepth - 1).dp else 0.dp,
        animationSpec = spring(stiffness = Spring.StiffnessHigh),
        label = "gameBtnShift",
    )

    val face = if (enabled) color else DisabledFace
    val edge = lerp(face, Color.Black, 0.35f)
    val shape = RoundedCornerShape(theme.buttonRadius.dp)

    Box(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(
                interactionSource = src,
                indication = null,
                enabled = enabled,
            ) {
                GameFx.fire(sound, haptic)
                onClick()
            },
        propagateMinConstraints = true, // 바깥에서 정한 너비·높이가 눌리는 얼굴에 그대로 전달돼요
    ) {
        // 아래 "두께"
        Box(
            Modifier
                .matchParentSize()
                .padding(top = depth)
                .clip(shape)
                .background(edge),
        )
        // 눌리는 얼굴
        Box(
            Modifier
                .padding(bottom = depth)
                .offset(y = faceShift)
                .clip(shape)
                .background(face)
                .padding(horizontal = 18.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                leading?.let { it(); Spacer(Modifier.width(10.dp)) }
                Text(
                    text,
                    color = theme.textOnColor,
                    fontSize = fontSize,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    fontFamily = fontFamily,
                )
            }
        }
    }
}

/** 그림 테마용 버튼: 나무 버튼 그림을 색으로 물들여 쓰고, 누르면 눌린 그림으로 바뀌어요 */
@Composable
private fun SkinButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier,
    color: Color,
    fontSize: TextUnit,
    enabled: Boolean,
    sound: GameSound.Fx?,
    haptic: GameHaptic.Kind?,
    leading: (@Composable () -> Unit)?,
    fontFamily: FontFamily?,
) {
    val theme = LocalGameTheme.current
    val up = rememberSkinBitmap(theme.skin, "btn.png")
    val dn = rememberSkinBitmap(theme.skin, "btn_down.png")
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val down = pressed && enabled
    val scale by animateFloatAsState(
        targetValue = if (down) theme.pressScale else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "skinBtnScale",
    )
    val px = LocalContext.current.resources.displayMetrics.density * 3f
    val tint = if (enabled) color else Color(0xFFBDBDBD)
    val img = if (down) dn ?: up else up

    Box(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .drawBehind {
                if (img != null) drawNineSlice(img, 6, px, tint)
                else drawRect(tint)
            }
            .clickable(interactionSource = src, indication = null, enabled = enabled) {
                GameFx.fire(sound, haptic)
                onClick()
            },
        contentAlignment = Alignment.Center,
        propagateMinConstraints = true,
    ) {
        Row(
            Modifier.padding(horizontal = 18.dp, vertical = 12.dp).offset(y = if (down) 3.dp else 0.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            leading?.let { it(); Spacer(Modifier.width(10.dp)) }
            Text(
                text,
                color = theme.textOnColor,
                fontSize = fontSize,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                fontFamily = fontFamily,
            )
        }
    }
}

/**
 * 카드·이름칸 같은 "아무 모양"에 눌림(작아졌다 튕김) + 소리 + 진동을 붙이는 modifier.
 * 예)  Modifier.gameClickable { ... }
 */
@Composable
fun Modifier.gameClickable(
    enabled: Boolean = true,
    sound: GameSound.Fx? = GameSound.Fx.TAP,
    haptic: GameHaptic.Kind? = GameHaptic.Kind.TICK,
    onClick: () -> Unit,
): Modifier {
    val theme = LocalGameTheme.current
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) theme.pressScale else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "gameClickScale",
    )
    return this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(interactionSource = src, indication = null, enabled = enabled) {
            GameFx.fire(sound, haptic)
            onClick()
        }
}
