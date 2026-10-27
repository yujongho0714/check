package com.jungdo.attend.ui

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import com.jungdo.attend.ui.game.GamePanel
import com.jungdo.attend.ui.game.GamePill
import com.jungdo.attend.ui.game.GameSlot
import com.jungdo.attend.ui.game.GameTextButton
import com.jungdo.attend.ui.game.gameMuted
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.jungdo.attend.data.AssetPack
import com.jungdo.attend.data.AwardResult
import com.jungdo.attend.data.Loot
import com.jungdo.attend.data.Repo
import com.jungdo.attend.data.Student
import kotlinx.coroutines.delay

private val assetCache = LruCache<String, ImageBitmap>(160)

/** 올린 그림이 바뀌었을 때 기억해 둔 그림을 비워요 */
internal fun clearAssetCache() = assetCache.evictAll()

/** assets 안의 그림 한 장 (한 번 읽은 건 기억해 둠) */
@Composable
internal fun rememberAsset(path: String): ImageBitmap? {
    val ctx = LocalContext.current
    val ver = AssetPack.version
    return remember(path, ver) {
        assetCache.get(path) ?: runCatching {
            AssetPack.open(ctx, path).use { BitmapFactory.decodeStream(it) }?.asImageBitmap()
        }.getOrNull()?.also { assetCache.put(path, it) }
    }
}

/** 보석 그림. locked 면 아직 못 얻은 것 (까만 그림자) */
@Composable
fun GemImage(id: String, size: Dp, locked: Boolean = false) {
    val bmp = rememberAsset("gem/$id.webp")
    if (bmp == null) { Spacer(Modifier.size(size)); return }
    Image(
        bitmap = bmp, contentDescription = null, filterQuality = FilterQuality.Medium,
        colorFilter = if (locked) ColorFilter.tint(Color.Black, BlendMode.SrcIn) else null,
        modifier = Modifier.size(size).alpha(if (locked) 0.22f else 1f),
    )
}

/** 네임플레이트 위에 이름을 얹어서 보여줌. 그림 왼쪽 네모칸은 비우고 오른쪽 칸에 이름 */
@Composable
fun NamePlate(plateId: String, name: String, width: Dp, fontSize: TextUnit, locked: Boolean = false) {
    val bmp = rememberAsset("plate/$plateId.webp")
    if (bmp == null) { Text(name, fontSize = fontSize, fontWeight = FontWeight.Bold, color = Color.White); return }
    val aspect = bmp.width.toFloat() / bmp.height.toFloat()
    Box(Modifier.width(width).aspectRatio(aspect)) {
        Image(
            bitmap = bmp, contentDescription = null, contentScale = ContentScale.FillBounds,
            colorFilter = if (locked) ColorFilter.tint(Color.Black, BlendMode.SrcIn) else null,
            modifier = Modifier.fillMaxSize().alpha(if (locked) 0.22f else 1f),
        )
        if (!locked) Box(Modifier.fillMaxSize().padding(start = width * 0.28f, end = width * 0.04f), contentAlignment = Alignment.Center) {
            Text(
                name, fontSize = fontSize, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1,
                softWrap = false, overflow = TextOverflow.Clip, textAlign = TextAlign.Center,
                style = TextStyle(shadow = Shadow(Color.Black, Offset(2f, 2f), 6f)),
            )
        }
    }
}

/** 키오스크 완료 화면: 이번에 얻은 보석 / 네임플레이트 */
@Composable
fun LootRow(a: AwardResult) {
    if (a.gemId == null && a.plateId == null) return
    val pop = remember(a) { Animatable(0.3f) }
    LaunchedEffect(a) {
        delay(900)
        pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
    }
    Row(
        Modifier.graphicsLayer { scaleX = pop.value; scaleY = pop.value; alpha = ((pop.value - 0.3f) / 0.7f).coerceIn(0f, 1f) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        a.gemId?.let { id ->
            GemImage(id, 84.dp)
            Column {
                Text("보석 획득!", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFDE68A))
                Text("보석 ${id.toIntOrNull() ?: 0}번", fontSize = 15.sp, color = Color.White)
            }
        }
        a.plateId?.let { id ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("네임플레이트 획득!", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF86EFAC))
                NamePlate(id, a.name, 220.dp, 22.sp)
            }
        }
    }
}

/**
 * 내 보관함: 모은 보석 보기 + 네임플레이트 고르기 (본인이 출결번호를 누른 뒤에 열림)
 * 네임플레이트를 누르면 바로 바뀌고, 쓰고 있는 걸 다시 누르면 벗음.
 */
@Composable
fun CollectionDialog(s: Student, onClose: () -> Unit) {
    var tab by remember { mutableStateOf(0) }
    // 아무도 안 건드리면 90초 뒤 닫힘 (누를 때마다 다시 셈)
    LaunchedEffect(tab, s.plate) { delay(90_000); onClose() }
    val gemSet = remember(s.gems) { s.gems.toSet() }
    val plateSet = remember(s.plates) { s.plates.toSet() }
    val gemList = remember(gemSet) { Loot.allGems.sortedBy { it !in gemSet } }
    val plateList = remember(plateSet) { Loot.allPlates.sortedBy { it !in plateSet } }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        GamePanel(Modifier.fillMaxWidth(0.94f).fillMaxHeight(0.9f), padding = 20.dp, horizontalAlignment = Alignment.Start) {
            run {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${s.name}의 보관함", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    GameTextButton("닫기", 20.sp) { onClose() }
                }
                Spacer(Modifier.size(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GamePill("보석 ${s.gems.size}/${Loot.GEM_COUNT}", tab == 0, 20.sp) { tab = 0 }
                    GamePill("네임플레이트 ${s.plates.size}/${Loot.PLATE_COUNT}", tab == 1, 20.sp) { tab = 1 }
                }
                Spacer(Modifier.size(8.dp))
                if (tab == 0) {
                    Text("등원·하원을 누르면 보석을 얻을 수 있어요 (운이 좋으면!)", color = gameMuted(), fontSize = 14.sp)
                    LazyVerticalGrid(GridCells.Adaptive(92.dp), Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(gemList) { id -> GameSlot(Modifier.padding(2.dp)) { GemImage(id, 60.dp, locked = id !in gemSet) } }
                    }
                } else {
                    Text("가진 네임플레이트를 누르면 바뀌어요 (쓰는 걸 다시 누르면 벗어요)", color = gameMuted(), fontSize = 14.sp)
                    LazyVerticalGrid(GridCells.Adaptive(190.dp), Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(plateList) { id ->
                            val own = id in plateSet
                            val on = s.plate == id
                            Box(
                                Modifier.clip(RoundedCornerShape(12.dp))
                                    .then(if (on) Modifier.background(Color(0x3322C55E)) else Modifier)
                                    .then(if (own) Modifier.clickable { Repo.setPlate(s.id, if (on) "" else id) } else Modifier)
                                    .padding(4.dp),
                                contentAlignment = Alignment.TopEnd,
                            ) {
                                NamePlate(id, s.name, 180.dp, 22.sp, locked = !own)
                                if (on) Text("✔ 사용 중", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White,
                                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(OkGreen).padding(horizontal = 6.dp, vertical = 2.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun pctText(v: Double): String = if (v % 1.0 == 0.0) v.toInt().toString() else v.toString()

/**
 * 관리자 설정: 보석 · 네임플레이트 얻을 확률 (%)
 * 저장하면 Firestore(config/loot)에 올라가서 출석 단말기에도 바로 똑같이 적용돼요.
 */
@Composable
fun LootSettings() {
    var gem by remember { mutableStateOf(pctText(Loot.gemChance)) }
    var plate by remember { mutableStateOf(pctText(Loot.plateChance)) }
    var msg by remember { mutableStateOf("") }
    // 서버 값이 늦게 도착하거나 저장 후 바뀌면 입력칸도 맞춰 줌
    LaunchedEffect(Loot.gemChance, Loot.plateChance) { gem = pctText(Loot.gemChance); plate = pctText(Loot.plateChance) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle("보석 · 네임플레이트 얻을 확률")
        Text("등원·하원을 누를 때마다 이 확률로 뽑아요 (0 ~ 100%, 소수점 가능). 하루에 등원 한 번·하원 한 번만 뽑아요.", color = Muted, fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(gem, { gem = it.filter { c -> c.isDigit() || c == '.' }; msg = "" }, label = { Text("보석 확률 %") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
            OutlinedTextField(plate, { plate = it.filter { c -> c.isDigit() || c == '.' }; msg = "" }, label = { Text("네임플레이트 확률 %") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("보석 빠른 선택", color = Muted, fontSize = 13.sp)
            listOf("100", "50", "30", "10").forEach { v -> TextButton(onClick = { gem = v; msg = "" }) { Text("$v%") } }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = {
                val g = gem.toDoubleOrNull()?.coerceIn(0.0, 100.0) ?: Loot.gemChance
                val p = plate.toDoubleOrNull()?.coerceIn(0.0, 100.0) ?: Loot.plateChance
                Repo.saveLootConfig(g, p)
                gem = pctText(g); plate = pctText(p)
                msg = "저장됐어요 ✔ (지금 적용: 보석 ${pctText(g)}%, 네임플레이트 ${pctText(p)}%)"
            }) { Text("확률 저장") }
            if (msg.isNotBlank()) Text(msg, color = OkGreen, fontSize = 13.sp)
        }
    }
}
