package com.jungdo.attend.ui.game

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import java.util.concurrent.ConcurrentHashMap

/**
 * 도트 아이콘 (assets/ui/icons/<이름>.png).
 * 이모지(🎁 💰 ✔ ⚠ 등) 대신 UI 팩 그림을 쓰기 위한 도구.
 *
 *  - PixelIcon("coin", 24.dp)                  : 아이콘 한 개
 *  - PixelText("{coin} 100 골드")               : 글자 사이에 아이콘 끼우기 ({이름} 자리에 그림)
 *      · 옛날 이모지(🪙 💰 🎁 👗 📖 🎒 ✔ ✕ ⚠ ✅ 🟢 🔴)는 쓰던 그대로 써도 자동으로 그림으로 바뀜
 *  - PixelCheck(true)                          : 체크박스 (☑ ☐ 대체)
 *
 * 이름: chest gift dress book gold coin check x warn dot_on dot_off arrow_l arrow_r arrow_u arrow_d
 */
object PixelIcons {
    private class Slot(val bmp: ImageBitmap?)
    private val cache = ConcurrentHashMap<String, Slot>()

    fun load(ctx: Context, name: String): ImageBitmap? =
        cache.getOrPut(name) {
            Slot(runCatching {
                ctx.assets.open("ui/icons/$name.png").use { BitmapFactory.decodeStream(it)?.asImageBitmap() }
            }.getOrNull())
        }.bmp

    /** 옛날 이모지 → {아이콘이름} */
    fun swap(s: String): String = s
        .replace("✅", "{check}").replace("✔", "{check}").replace("✕", "{x}")
        .replace("⚠️", "{warn}").replace("⚠", "{warn}")
        .replace("🪙", "{coin}").replace("💰", "{gold}")
        .replace("🎁", "{gift}").replace("👗", "{dress}").replace("📖", "{book}").replace("🎒", "{chest}")
        .replace("🟢", "{dot_on}").replace("🔴", "{dot_off}")
}

/** 그림 한 장 (도트가 뭉개지지 않게 확대) */
@Composable
fun PixelIcon(name: String, size: Dp, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val bmp = remember(name) { PixelIcons.load(ctx, name) } ?: return
    Image(bmp, contentDescription = null, filterQuality = FilterQuality.None, modifier = modifier.size(size))
}

@Composable
private fun PixelFill(name: String) {
    val ctx = LocalContext.current
    val bmp = remember(name) { PixelIcons.load(ctx, name) } ?: return
    Image(bmp, contentDescription = null, filterQuality = FilterQuality.None, modifier = Modifier.fillMaxSize())
}

/** ☑ / ☐ 대신: 켜짐 = 체크 그림, 꺼짐 = 빈 나무색 칸 */
@Composable
fun PixelCheck(on: Boolean, size: Dp = 26.dp, modifier: Modifier = Modifier) {
    if (on) PixelIcon("check", size, modifier)
    else Box(modifier.size(size).border(3.dp, Color(0xFF8A4B2A), RoundedCornerShape(5.dp)))
}

/** Text 와 똑같이 쓰되, {아이콘이름} 자리(와 옛 이모지)를 도트 그림으로 바꿔서 보여줌 */
@Composable
fun PixelText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontWeight: FontWeight? = null,
    textAlign: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
) {
    val rx = remember { Regex("\\{([a-z_]+)\\}") }
    val s = remember(text) { PixelIcons.swap(text) }
    val names = remember(s) { rx.findAll(s).map { it.groupValues[1] }.toSet() }
    val ann = remember(s) {
        buildAnnotatedString {
            var last = 0
            for (m in rx.findAll(s)) {
                append(s.substring(last, m.range.first))
                appendInlineContent(m.groupValues[1], "·")
                last = m.range.last + 1
            }
            append(s.substring(last))
        }
    }
    val fs = if (fontSize == TextUnit.Unspecified) 16.sp else fontSize
    val box = fs * 1.25f
    val inline = remember(names, box) {
        names.associateWith { n ->
            InlineTextContent(Placeholder(box, box, PlaceholderVerticalAlign.TextCenter)) { PixelFill(n) }
        }
    }
    Text(
        ann, modifier = modifier, color = color, fontSize = fontSize, fontWeight = fontWeight,
        textAlign = textAlign, maxLines = maxLines, inlineContent = inline,
    )
}
