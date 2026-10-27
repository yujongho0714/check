package com.jungdo.attend.ui

import android.graphics.BitmapFactory
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jungdo.attend.data.AssetPack
import com.jungdo.attend.data.AwardResult
import com.jungdo.attend.data.Rank
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import java.util.Locale

/** 계급 이미지 (assets/rank/001.png … 866.png). 작은 픽셀 그림이라 흐려지지 않게 확대 */
@Composable
fun RankImage(level: Int, size: Dp) {
    val ctx = LocalContext.current
    val ver = AssetPack.version
    val bmp = remember(level, ver) {
        runCatching {
            AssetPack.open(ctx, String.format(Locale.US, "rank/%03d.png", level.coerceIn(1, 866))).use { BitmapFactory.decodeStream(it) }?.asImageBitmap()
        }.getOrNull()
    }
    if (bmp != null) Image(bitmap = bmp, contentDescription = null, filterQuality = FilterQuality.None, modifier = Modifier.size(size))
    else Spacer(Modifier.size(size))
}

/** "Lv.{lv}" 같은 글자 틀에 숫자를 넣어요 */
private fun fmtLv(format: String, lv: Int) = format.replace("{lv}", lv.toString())

/** 레벨 글자 (테마에 badge 틀이 있으면 그 안에, 없으면 글자만) */
@Composable
private fun LevelLabel(th: RankTheme, lv: Int, modifier: Modifier = Modifier) {
    val txt = fmtLv(th.levelFormat, lv)
    if (th.badge.image != null || th.badge.bgColor != null) {
        ThemedBox(th, th.badge, modifier) {
            Text(txt, fontSize = th.levelSize.sp, fontWeight = FontWeight.Bold, color = th.levelColor, maxLines = 1, softWrap = false)
        }
    } else {
        Text(txt, fontSize = th.levelSize.sp, fontWeight = FontWeight.Bold, color = th.levelColor, modifier = modifier)
    }
}

/** 계급 그림 (테마에 slot 틀이 있으면 틀 안에) */
@Composable
private fun RankSlot(th: RankTheme, lv: Int, modifier: Modifier = Modifier) {
    if (th.slot.image != null || th.slot.bgColor != null) {
        ThemedBox(th, th.slot, modifier) { RankImage(lv, th.slotImageSize.dp) }
    } else {
        Box(modifier) { RankImage(lv, th.slotImageSize.dp) }
    }
}

/** "레벨 업!" 표시 (테마에 levelUpBox 틀이 있으면 그 안에) */
@Composable
private fun LevelUpLabel(th: RankTheme, a: AwardResult) {
    val txt = th.levelUpText + if (th.levelUpShowFromTo) "  (Lv.${a.levelBefore}→${a.level})" else ""
    if (th.levelUpBox.image != null || th.levelUpBox.bgColor != null) {
        ThemedBox(th, th.levelUpBox) {
            Text(txt, fontSize = th.levelUpSize.sp, fontWeight = FontWeight.Bold, color = th.levelUpColor, maxLines = 1, softWrap = false)
        }
    } else {
        Text(txt, fontSize = th.levelUpSize.sp, fontWeight = FontWeight.Bold, color = th.levelUpColor)
    }
}

/** 경험치 막대 (progress 0~1) */
@Composable
private fun ExpBar(th: RankTheme, progress: Float) {
    val shape = RoundedCornerShape(th.barRadius.dp)
    val icon = th.barIcon?.let { rememberAsset(th.asset(it)) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (icon != null && th.barIconSize > 0) {
            Image(bitmap = icon, contentDescription = null, filterQuality = if (th.pixel) FilterQuality.None else FilterQuality.Medium, modifier = Modifier.size(th.barIconSize.dp))
            Spacer(Modifier.width(8.dp))
        }
        var m = Modifier.width(th.barWidth.dp).height(th.barHeight.dp).clip(shape).background(th.barTrack)
        if (th.barStroke != null && th.barStrokeWidth > 0) m = m.border(th.barStrokeWidth.dp, th.barStroke, shape)
        Box(m) {
            Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(th.barHeight.dp).background(th.barFill))
        }
    }
}

/** 등원/하원 선택 팝업용: 레벨 + 계급 그림 한 줄 (밝은 배경 위) */
@Composable
fun RankHeader(level: Int) {
    val th = rememberRankTheme()
    val framed = th.badge.image != null || th.badge.bgColor != null
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (th.rankSide == "left") {
            RankSlot(th, level)
            Spacer(Modifier.width(14.dp))
        }
        // 틀이 없으면 밝은 배경에서도 보이게 예전 주황색 글자
        if (framed) LevelLabel(th, level)
        else Text(fmtLv(th.levelFormat, level), fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Color(0xFFB45309))
        if (th.rankSide != "left") {
            Spacer(Modifier.width(14.dp))
            RankSlot(th, level)
        }
    }
}

/**
 * 키오스크 완료 화면의 "계급 · 경험치" 영역. (이름/네임플레이트는 그 위에)
 * 레벨이 오르면: 막대가 끝까지 참 → 새 계급 그림이 튀어나옴 → "레벨 업!" 글자가 깜빡 → 막대가 새 레벨 위치까지 참
 * 모양(틀·색·글자)은 테마(assets/theme)가 정해요.
 */
@Composable
fun AwardRow(a: AwardResult) {
    val th = rememberRankTheme()
    var shown by remember(a) { mutableStateOf(a.levelBefore) }
    val bar = remember(a) { Animatable(Rank.progress(a.expBefore)) }
    val pop = remember(a) { Animatable(1f) }
    val pulse = remember(a) { Animatable(1f) }
    LaunchedEffect(a) {
        delay(500)
        if (a.leveledUp) {
            bar.animateTo(1f, tween(500))
            shown = a.level
            bar.snapTo(0f)
            pop.snapTo(0.4f)
            launch { pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)) }
            launch { repeat(th.blink) { pulse.animateTo(1.25f, tween(160)); pulse.animateTo(1f, tween(160)) } }
        }
        bar.animateTo(Rank.progress(a.expAfter), tween(600))
    }
    val up = a.leveledUp && shown == a.level
    // 네임플레이트(없으면 이름) → [Lv + 계급 그림] → 경험치 막대 → 받은 경험치 글자 (세로로 가운데 정렬)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (a.equipped.isNotBlank()) NamePlate(a.equipped, a.name, 340.dp, 34.sp)
        else Text(a.name, fontSize = 44.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(Modifier.height(10.dp))
        ThemedBox(th, th.panel) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val lvBlock: @Composable () -> Unit = {
                    Column(horizontalAlignment = if (th.rankSide == "left") Alignment.Start else Alignment.End) {
                        LevelLabel(th, shown, Modifier.graphicsLayer { scaleX = pulse.value; scaleY = pulse.value })
                        if (up) { Spacer(Modifier.height(4.dp)); LevelUpLabel(th, a) }
                    }
                }
                val rankBlock: @Composable () -> Unit = {
                    Box(Modifier.graphicsLayer { scaleX = pop.value; scaleY = pop.value }) { RankSlot(th, shown) }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (th.rankSide == "left") { rankBlock(); Spacer(Modifier.width(16.dp)); lvBlock() }
                    else { lvBlock(); Spacer(Modifier.width(16.dp)); rankBlock() }
                }
                Spacer(Modifier.height(10.dp))
                ExpBar(th, bar.value)
                Spacer(Modifier.height(6.dp))
                Text(
                    if (a.gained > 0) "경험치 +${a.gained}" + (if (a.bonus > 0) "  (연속 ${a.streak}일 보너스 +${a.bonus})" else "")
                    else "오늘은 이미 경험치를 받았어요",
                    fontSize = th.expSize.sp, color = th.expColor,
                )
                if (a.streak >= 2) Text("연속 출석 ${a.streak}일", fontSize = th.streakSize.sp, color = th.streakColor)
            }
        }
    }
}
