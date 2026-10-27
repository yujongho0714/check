package com.jungdo.attend.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import org.json.JSONObject
import com.jungdo.attend.data.AssetPack

/*
 * ───────────────────────────────────────────────────────────────
 *  계급 영역 "테마" 시스템 (v17)
 *   - 테마 = assets/theme/<테마이름>/ 폴더 (theme.json + 그림 파일들)
 *   - 어떤 테마를 쓸지는 assets/theme/active.txt 한 줄에 적은 이름
 *   - 코드는 건드릴 필요 없이 폴더·글자·그림만 바꾸면 돼요. (설명서 참고)
 * ───────────────────────────────────────────────────────────────
 */

/** 네모 틀 하나. image 가 있으면 그림(9조각)으로, 없으면 색으로 그림 */
data class FrameSpec(
    val image: String? = null,      // 테마 폴더 안 그림 파일 이름 (예: "slot.png")
    val slice: Int = 0,             // 그림 모서리 자르는 크기(그림 픽셀). 0 이면 통째로 늘림
    val border: Int = 0,            // 화면에서 모서리 크기(dp)
    val bgColor: Color? = null,     // 그림이 없을 때 배경색
    val strokeColor: Color? = null, // 그림이 없을 때 테두리색
    val strokeWidth: Int = 0,
    val radius: Int = 12,
    val padH: Int = 0,
    val padV: Int = 0,
    val width: Int = 0,             // 0 이면 안에 든 내용 크기
    val height: Int = 0,
)

data class RankTheme(
    val id: String,
    val name: String,
    val pixel: Boolean,             // true 면 픽셀 그림이라 확대해도 또렷하게(흐리게 안 함)
    val panel: FrameSpec,           // 계급 영역 전체를 감싸는 틀
    val slot: FrameSpec,            // 계급 그림 뒤 틀
    val slotImageSize: Int,         // 틀 안 계급 그림 크기(dp)
    val rankSide: String,           // "left" 계급 그림이 레벨 왼쪽 / "right" 오른쪽
    val levelFormat: String,        // 예: "Lv.{lv}"
    val levelColor: Color,
    val levelSize: Int,
    val badge: FrameSpec,           // 레벨 글자 뒤 틀 (없으면 글자만)
    val levelUpText: String,
    val levelUpColor: Color,
    val levelUpSize: Int,
    val levelUpShowFromTo: Boolean, // "(Lv.9→11)" 붙일지
    val levelUpBox: FrameSpec,      // 레벨 업 글자 뒤 틀
    val blink: Int,                 // 레벨 업 때 깜빡이는 횟수
    val barWidth: Int,
    val barHeight: Int,
    val barTrack: Color,
    val barFill: Color,
    val barStroke: Color?,
    val barStrokeWidth: Int,
    val barRadius: Int,
    val barIcon: String?,           // 막대 왼쪽 작은 그림 (없으면 null)
    val barIconSize: Int,
    val expColor: Color,
    val expSize: Int,
    val streakColor: Color,
    val streakSize: Int,
) {
    companion object {
        /** 지금(v16)과 똑같은 모양. theme.json 이 없거나 깨졌을 때도 이걸로 돌아가요 */
        fun classic() = RankTheme(
            id = "classic", name = "기본", pixel = true,
            panel = FrameSpec(), slot = FrameSpec(), slotImageSize = 96, rankSide = "right",
            levelFormat = "Lv.{lv}", levelColor = Color(0xFFFDE68A), levelSize = 34,
            badge = FrameSpec(), levelUpText = "레벨 업!", levelUpColor = Color(0xFFFDE68A), levelUpSize = 18,
            levelUpShowFromTo = true, levelUpBox = FrameSpec(), blink = 3,
            barWidth = 300, barHeight = 10, barTrack = Color(0x55FFFFFF), barFill = Color(0xFFFDE68A),
            barStroke = null, barStrokeWidth = 0, barRadius = 5, barIcon = null, barIconSize = 0,
            expColor = Color.White, expSize = 18, streakColor = Color(0xFF86EFAC), streakSize = 16,
        )
    }
}

private fun JSONObject.color(key: String, def: Color?): Color? {
    val s = optString(key, "")
    if (s.isBlank()) return def
    return runCatching { Color(android.graphics.Color.parseColor(s)) }.getOrDefault(def)
}

private fun JSONObject.frame(key: String): FrameSpec {
    val o = optJSONObject(key) ?: return FrameSpec()
    return FrameSpec(
        image = o.optString("image", "").ifBlank { null },
        slice = o.optInt("slice", 0),
        border = o.optInt("border", 0),
        bgColor = o.color("bgColor", null),
        strokeColor = o.color("strokeColor", null),
        strokeWidth = o.optInt("strokeWidth", 0),
        radius = o.optInt("radius", 12),
        padH = o.optInt("padH", 0),
        padV = o.optInt("padV", 0),
        width = o.optInt("width", 0),
        height = o.optInt("height", 0),
    )
}

object RankThemes {
    private val cache = HashMap<String, RankTheme>()

    /** assets/theme/active.txt 에 적힌 테마를 읽어요. 실패하면 기본(classic) */
    fun load(ctx: Context, preferId: String? = null): RankTheme {
        // 관리자가 직접 고른 테마 > 화면 테마에 맞는 테마(preferId) > active.txt
        val id = AssetPack.manualThemeId(ctx)
            ?: preferId?.takeIf { it in AssetPack.themes(ctx) }
            ?: AssetPack.activeThemeId(ctx)
        return cache.getOrPut(id) { parse(ctx, id) ?: RankTheme.classic() }
    }

    /** 올린 그림이 바뀌었을 때 기억해 둔 테마를 비워요 */
    fun clear() = cache.clear()

    private fun parse(ctx: Context, id: String): RankTheme? = runCatching {
        val j = JSONObject(AssetPack.open(ctx, "theme/$id/theme.json").bufferedReader().use { it.readText() })
        val d = RankTheme.classic()
        val lv = j.optJSONObject("level")
        val up = j.optJSONObject("levelUp")
        val bar = j.optJSONObject("bar")
        val tx = j.optJSONObject("text")
        val slot = j.optJSONObject("slot")
        RankTheme(
            id = id,
            name = j.optString("name", id),
            pixel = j.optBoolean("pixel", true),
            panel = j.frame("panel"),
            slot = j.frame("slot"),
            slotImageSize = slot?.optInt("imageSize", d.slotImageSize) ?: d.slotImageSize,
            rankSide = j.optString("rankSide", d.rankSide),
            levelFormat = lv?.optString("format", d.levelFormat) ?: d.levelFormat,
            levelColor = lv?.color("color", d.levelColor) ?: d.levelColor,
            levelSize = lv?.optInt("size", d.levelSize) ?: d.levelSize,
            badge = j.frame("badge"),
            levelUpText = up?.optString("text", d.levelUpText) ?: d.levelUpText,
            levelUpColor = up?.color("color", d.levelUpColor) ?: d.levelUpColor,
            levelUpSize = up?.optInt("size", d.levelUpSize) ?: d.levelUpSize,
            levelUpShowFromTo = up?.optBoolean("showFromTo", d.levelUpShowFromTo) ?: d.levelUpShowFromTo,
            levelUpBox = j.frame("levelUpBox"),
            blink = up?.optInt("blink", d.blink) ?: d.blink,
            barWidth = bar?.optInt("width", d.barWidth) ?: d.barWidth,
            barHeight = bar?.optInt("height", d.barHeight) ?: d.barHeight,
            barTrack = bar?.color("track", d.barTrack) ?: d.barTrack,
            barFill = bar?.color("fill", d.barFill) ?: d.barFill,
            barStroke = bar?.color("stroke", null),
            barStrokeWidth = bar?.optInt("strokeWidth", 0) ?: 0,
            barRadius = bar?.optInt("radius", d.barRadius) ?: d.barRadius,
            barIcon = bar?.optString("icon", "")?.ifBlank { null },
            barIconSize = bar?.optInt("iconSize", 0) ?: 0,
            expColor = tx?.color("exp", d.expColor) ?: d.expColor,
            expSize = tx?.optInt("expSize", d.expSize) ?: d.expSize,
            streakColor = tx?.color("streak", d.streakColor) ?: d.streakColor,
            streakSize = tx?.optInt("streakSize", d.streakSize) ?: d.streakSize,
        )
    }.getOrNull()
}

@Composable
fun rememberRankTheme(): RankTheme {
    val ctx = LocalContext.current
    val ver = AssetPack.version // 그림·테마가 바뀌면 다시 읽어요
    val skin = com.jungdo.attend.ui.game.LocalGameTheme.current.skin // 새싹 농장 화면이면 새싹 계급 틀
    return remember(ver, skin) { RankThemes.load(ctx, skin) }
}

/** 테마 폴더 안 그림 경로 */
fun RankTheme.asset(file: String) = "theme/$id/$file"

/**
 * 9조각 그림 그리기: 네 모서리는 그대로, 변은 늘려서 어떤 크기에도 맞춤.
 * slice <= 0 이면 그림 한 장을 통째로 늘려요.
 */
fun Modifier.nineSlice(img: ImageBitmap, slice: Int, border: Dp, pixel: Boolean): Modifier = drawBehind {
    val q = if (pixel) FilterQuality.None else FilterQuality.Medium
    val w = size.width.roundToInt()
    val h = size.height.roundToInt()
    if (slice <= 0 || img.width <= slice * 2 || img.height <= slice * 2) {
        drawImage(img, IntOffset.Zero, IntSize(img.width, img.height), IntOffset.Zero, IntSize(w, h), filterQuality = q)
        return@drawBehind
    }
    val b = minOf(border.toPx().roundToInt(), w / 2, h / 2)
    val sx = intArrayOf(0, slice, img.width - slice, img.width)
    val sy = intArrayOf(0, slice, img.height - slice, img.height)
    val dx = intArrayOf(0, b, w - b, w)
    val dy = intArrayOf(0, b, h - b, h)
    for (r in 0..2) for (c in 0..2) {
        val dw = dx[c + 1] - dx[c]
        val dh = dy[r + 1] - dy[r]
        if (dw <= 0 || dh <= 0) continue
        drawImage(
            img,
            IntOffset(sx[c], sy[r]), IntSize(sx[c + 1] - sx[c], sy[r + 1] - sy[r]),
            IntOffset(dx[c], dy[r]), IntSize(dw, dh),
            filterQuality = q,
        )
    }
}

/** 테마 틀로 감싼 상자. 그림이 있으면 그림, 없으면 색(배경·테두리) */
@Composable
fun ThemedBox(th: RankTheme, f: FrameSpec, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val bmp = f.image?.let { rememberAsset(th.asset(it)) }
    var m = modifier
    if (f.width > 0) m = m.width(f.width.dp)
    if (f.height > 0) m = m.height(f.height.dp)
    m = if (bmp != null) {
        m.nineSlice(bmp, f.slice, f.border.dp, th.pixel)
    } else {
        val shape = RoundedCornerShape(f.radius.dp)
        var mm = m
        if (f.bgColor != null) mm = mm.clip(shape).background(f.bgColor)
        if (f.strokeColor != null && f.strokeWidth > 0) mm = mm.border(f.strokeWidth.dp, f.strokeColor, shape)
        mm
    }
    Box(m.padding(horizontal = f.padH.dp, vertical = f.padV.dp), contentAlignment = Alignment.Center, content = content)
}
