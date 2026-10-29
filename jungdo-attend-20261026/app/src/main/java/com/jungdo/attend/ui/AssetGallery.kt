package com.jungdo.attend.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.jungdo.attend.data.AssetPack
import com.jungdo.attend.data.Dress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/*
 * 올린 그림 전체 보기 (계급 · 보석 · 네임플레이트 · 테마 · 옷)
 *  - 칸을 누르면 크게 보고, 모서리 체크 칸으로 여러 장을 골라서 한 번에 지울 수 있어요.
 *  - 지우면 계정에도 반영돼서 다른 기기도 같이 지워져요. (앱 기본 그림은 그대로라서, 지운 자리는 앱 기본 그림으로 보여요)
 */

private val ROOT_LABELS = listOf(
    "rank" to "계급", "gem" to "보석", "plate" to "네임플레이트", "dressup" to "옷·장비", "theme" to "UI 테마",
)

private fun groupOf(path: String): String {
    val p = path.split('/')
    return if (p.size > 2) p[1] else ""
}

private fun groupLabel(g: String) = when {
    g.isEmpty() -> "기본"
    g in Dress.CATS -> Dress.catLabel(g)
    else -> g
}

@Composable
private fun Thumb(path: String, modifier: Modifier = Modifier, maxPx: Int = 256) {
    val ctx = LocalContext.current
    val ver = AssetPack.version
    val bmp by produceState<ImageBitmap?>(null, path, ver) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                AssetPack.open(ctx, path).use { BitmapFactory.decodeStream(it, null, o) }
                var s = 1
                while (o.outWidth / (s * 2) >= maxPx && o.outHeight / (s * 2) >= maxPx) s *= 2
                val o2 = BitmapFactory.Options().apply { inSampleSize = s }
                AssetPack.open(ctx, path).use { BitmapFactory.decodeStream(it, null, o2) }?.asImageBitmap()
            }.getOrNull()
        }
    }
    Box(modifier.background(Color(0xFFE5E7EB)), contentAlignment = Alignment.Center) {
        bmp?.let { Image(it, path, Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
            ?: Text("…", color = Muted)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AssetGalleryDialog(onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val ver = AssetPack.version

    var root by remember { mutableStateOf("gem") }
    var group by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var preview by remember { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf<List<String>?>(null) }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }

    val counts = remember(ver) { ROOT_LABELS.associate { (r, _) -> r to AssetPack.imageFiles(ctx, r).size } }
    val files = remember(ver, root) { AssetPack.imageFiles(ctx, root) }
    val groups = remember(files) { files.map { groupOf(it) }.distinct().sorted() }
    val shown = files.filter { group == null || groupOf(it) == group }
    val sel = selected.filter { it in files }

    fun del(list: List<String>) {
        busy = true; msg = "지우는 중…"
        scope.launch {
            msg = AssetPack.deleteFiles(ctx, list)
            selected = selected - list.toSet()
            busy = false
        }
    }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)) {
        Column(Modifier.fillMaxSize().background(PageBg).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("올린 그림 전체 보기", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = onClose) { Text("닫기") }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ROOT_LABELS.forEach { (r, label) ->
                    Pill("$label ${counts[r] ?: 0}", root == r, 14.sp) { root = r; group = null; selected = emptySet() }
                }
            }
            if (groups.size > 1) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Pill("전체 ${files.size}", group == null, 13.sp) { group = null }
                    groups.forEach { g -> Pill("${groupLabel(g)} ${files.count { groupOf(it) == g }}", group == g, 13.sp) { group = g } }
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("선택 ${sel.size}장", fontWeight = FontWeight.Bold)
                OutlinedButton(enabled = !busy && shown.isNotEmpty(), onClick = { selected = selected + shown }) { Text("보이는 것 전체선택") }
                OutlinedButton(enabled = !busy && sel.isNotEmpty(), onClick = { selected = emptySet() }) { Text("선택 해제") }
                Button(enabled = !busy && sel.isNotEmpty(), onClick = { confirm = sel }) { Text("선택한 ${sel.size}장 지우기") }
            }
            if (msg.isNotBlank()) Text(msg, color = if (msg.startsWith("실패") || msg.contains("⚠️")) BadRed else OkGreen, fontSize = 13.sp)

            if (shown.isEmpty()) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { Text("올린 그림이 없어요", color = Muted) }
            } else {
                LazyVerticalGrid(
                    GridCells.Adaptive(96.dp), Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(shown, key = { it }) { path ->
                        val on = path in selected
                        Column {
                            Box(
                                Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(8.dp))
                                    .border(if (on) 3.dp else 1.dp, if (on) BadRed else Color(0xFFD1D5DB), RoundedCornerShape(8.dp))
                                    .clickable { preview = path },
                            ) {
                                Thumb(path, Modifier.fillMaxSize())
                                Box(
                                    Modifier.align(Alignment.TopEnd).padding(4.dp).size(28.dp).clip(RoundedCornerShape(6.dp))
                                        .background(if (on) BadRed else Color(0xCCFFFFFF))
                                        .clickable { selected = if (on) selected - path else selected + path },
                                    contentAlignment = Alignment.Center,
                                ) { Text(if (on) "✓" else "", color = Color.White, fontWeight = FontWeight.Bold) }
                            }
                            Text(path.substringAfterLast('/'), fontSize = 11.sp, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }

    preview?.let { p ->
        AlertDialog(
            onDismissRequest = { preview = null },
            title = { Text(p.substringAfterLast('/'), fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            text = { Thumb(p, Modifier.fillMaxWidth().aspectRatio(1f), maxPx = 1024) },
            confirmButton = { Button(enabled = !busy, onClick = { preview = null; confirm = listOf(p) }) { Text("이 그림 지우기") } },
            dismissButton = { TextButton(onClick = { preview = null }) { Text("닫기") } },
        )
    }

    confirm?.let { list ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text("${list.size}장을 지울까요?") },
            text = { Text("이 기기와 계정에서 지워지고, 다른 기기도 같이 지워져요. 지운 자리는 앱 기본 그림으로 보여요.") },
            confirmButton = { Button(onClick = { confirm = null; del(list) }) { Text("지우기") } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("아니요") } },
        )
    }
}
