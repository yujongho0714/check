package com.jungdo.attend.ui.game

import android.graphics.BitmapFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import com.jungdo.attend.R

/** 도트 숫자 글꼴 (숫자·영문만 있어요. 한글은 기본 글꼴로 대신 나와요) */
val SproutFont = FontFamily(Font(R.font.sprout_pixel))

/** 이 테마가 숫자에 쓸 글꼴 (그림 테마가 아니면 null = 기본 글꼴) */
@Composable
fun gameNumFont(): FontFamily? = if (LocalGameTheme.current.skin == "sprout") SproutFont else null

/** assets/game/<skin>/<file> 그림을 읽어요. 못 읽으면 null (그럴 땐 단색 모양으로 대신 그려요) */
@Composable
fun rememberSkinBitmap(skin: String?, file: String): ImageBitmap? {
    val ctx = LocalContext.current
    return remember(skin, file) {
        if (skin == null) null
        else runCatching {
            ctx.assets.open("game/$skin/$file").use { BitmapFactory.decodeStream(it)?.asImageBitmap() }
        }.getOrNull()
    }
}

/** 도트 그림을 모서리는 그대로, 가운데만 늘려서 그려요 (흐릿해지지 않게 픽셀 그대로) */
fun DrawScope.drawNineSlice(img: ImageBitmap, slice: Int, pixelPx: Float, tint: Color) {
    val w = size.width.toInt()
    val h = size.height.toInt()
    if (w <= 0 || h <= 0) return
    val s = (slice * pixelPx).toInt().coerceAtMost(minOf(w, h) / 2)
    val sx = intArrayOf(0, slice, img.width - slice, img.width)
    val sy = intArrayOf(0, slice, img.height - slice, img.height)
    val dx = intArrayOf(0, s, w - s, w)
    val dy = intArrayOf(0, s, h - s, h)
    val filter = if (tint == Color.White) null else ColorFilter.tint(tint, BlendMode.Modulate)
    for (r in 0..2) for (c in 0..2) {
        drawImage(
            image = img,
            srcOffset = IntOffset(sx[c], sy[r]),
            srcSize = IntSize(sx[c + 1] - sx[c], sy[r + 1] - sy[r]),
            dstOffset = IntOffset(dx[c], dy[r]),
            dstSize = IntSize(dx[c + 1] - dx[c], dy[r + 1] - dy[r]),
            colorFilter = filter,
            filterQuality = FilterQuality.None,
        )
    }
}

/** 창·카드 바탕: 그림 테마면 크림색 나무 틀, 아니면 기본 흰 카드 */
@Composable
fun GamePanel(
    modifier: Modifier = Modifier,
    padding: Dp = 28.dp,
    horizontalAlignment: Alignment.Horizontal = Alignment.CenterHorizontally,
    content: @Composable ColumnScope.() -> Unit,
) {
    val theme = LocalGameTheme.current
    val img = rememberSkinBitmap(theme.skin, "btn.png")
    if (theme.skin == null) {
        Card(shape = RoundedCornerShape(28.dp)) {
            Column(modifier.padding(padding), horizontalAlignment = horizontalAlignment, content = content)
        }
    } else {
        val px = LocalContext.current.resources.displayMetrics.density * 3f
        CompositionLocalProvider(LocalContentColor provides theme.text) {
            Column(
                modifier
                    .drawBehind {
                        if (img != null) drawNineSlice(img, 6, px, Color.White)
                        else drawRect(theme.card)
                    }
                    .padding(padding),
                horizontalAlignment = horizontalAlignment,
                verticalArrangement = Arrangement.Top,
                content = content,
            )
        }
    }
}

/** 보조 글자색: 새싹 테마면 연한 갈색, 아니면 예전 회색 */
@Composable
fun gameMuted(): Color {
    val t = LocalGameTheme.current
    return if (t.skin != null) t.text.copy(alpha = 0.7f) else com.jungdo.attend.ui.Muted
}

/** 선택 알약: 새싹 테마면 나무 버튼(선택되면 초록), 아니면 기존 알약 */
@Composable
fun GamePill(text: String, selected: Boolean, fontSize: TextUnit = 18.sp, onClick: () -> Unit) {
    val t = LocalGameTheme.current
    if (t.skin == null) { com.jungdo.attend.ui.Pill(text, selected, fontSize, onClick); return }
    GameButton(text, onClick, color = if (selected) t.good else Color.White, fontSize = fontSize, sound = null)
}

/** 글자 버튼(취소·닫기 등): 새싹 테마면 작은 나무 버튼, 아니면 기존 TextButton */
@Composable
fun GameTextButton(text: String, fontSize: TextUnit = 18.sp, onClick: () -> Unit) {
    val t = LocalGameTheme.current
    if (t.skin == null) { TextButton(onClick = onClick) { Text(text, fontSize = fontSize) }; return }
    GameButton(text, onClick, color = t.keyAltColor, fontSize = fontSize, sound = null, modifier = Modifier.padding(vertical = 3.dp))
}

/** 보석 같은 걸 담는 칸: 새싹 테마면 나무 칸 틀, 아니면 틀 없이 그대로 */
@Composable
fun GameSlot(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val t = LocalGameTheme.current
    val img = rememberSkinBitmap(t.skin, "slot.png")
    if (t.skin == null || img == null) { Box(modifier, contentAlignment = Alignment.Center, content = content); return }
    val px = LocalContext.current.resources.displayMetrics.density * 2f
    Box(modifier.drawBehind { drawNineSlice(img, 6, px, Color.White) }.padding(10.dp), contentAlignment = Alignment.Center, content = content)
}

/** 화면 배경: 새싹 테마면 초록 들판 + 은은한 풀·꽃 무늬(바둑판처럼 이어 붙임) */
@Composable
fun Modifier.gamePageBackground(): Modifier {
    val t = LocalGameTheme.current
    val img = rememberSkinBitmap(t.skin, "bg_grass.png")
    val px = LocalContext.current.resources.displayMetrics.density * 3f
    val base = this.background(t.page)
    if (img == null) return base
    return base.drawBehind {
        val tile = (img.width * px).toInt().coerceAtLeast(1)
        var y = 0
        while (y < size.height) {
            var x = 0
            while (x < size.width) {
                drawImage(img, IntOffset.Zero, IntSize(img.width, img.height), IntOffset(x, y), IntSize(tile, tile), filterQuality = FilterQuality.None)
                x += tile
            }
            y += tile
        }
    }
}
