package com.jungdo.attend.ui

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalConfiguration
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
import androidx.compose.material3.OutlinedButton
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
import com.jungdo.attend.data.UiSize
import kotlinx.coroutines.delay
import com.jungdo.attend.ui.game.PixelText
import com.jungdo.attend.ui.game.PixelIcon

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

/** 키오스크 완료 화면: 이번에 받은 골드 (장착한 보석 능력) */
@Composable
fun GoldRow(a: AwardResult) {
    val total = a.gold + a.dogamGold
    if (total <= 0) return
    val pop = remember(a) { Animatable(0.3f) }
    LaunchedEffect(a) {
        delay(600)
        pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
    }
    Row(
        Modifier.graphicsLayer { scaleX = pop.value; scaleY = pop.value; alpha = ((pop.value - 0.3f) / 0.7f).coerceIn(0f, 1f) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        PixelIcon("coin", 44.dp)
        Column {
            Text("+$total 골드", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFDE68A))
            if (a.dogamGold > 0) PixelText("📖 도감 보너스 +${a.dogamGold}", fontSize = 15.sp, color = Color(0xFF86EFAC))
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

/** 키오스크 완료 화면: 출석 때 나온 옷 (새 옷이면 "옷 획득!", 이미 가진 옷이면 환급 골드) */
@Composable
fun DressRow(a: AwardResult) {
    val key = a.dressKey ?: return
    val pack = rememberDressPack()
    val pop = remember(a) { Animatable(0.3f) }
    LaunchedEffect(a) {
        delay(1200)
        pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
    }
    val name = pack?.item(key)?.name?.takeIf { it.isNotBlank() } ?: "${com.jungdo.attend.data.Dress.noOf(key)}번"
    Row(
        Modifier.graphicsLayer { scaleX = pop.value; scaleY = pop.value; alpha = ((pop.value - 0.3f) / 0.7f).coerceIn(0f, 1f) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 그 옷만 입은 캐릭터
        DressCharacter(mapOf(com.jungdo.attend.data.Dress.catOf(key) to key), 96.dp)
        Column {
            Text(if (a.dressDup) "이미 있는 옷!" else "새 옷 획득!", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = if (a.dressDup) Color(0xFFE5E7EB) else Color(0xFF86EFAC))
            Text("${com.jungdo.attend.data.Dress.catLabel(com.jungdo.attend.data.Dress.catOf(key))} · $name", fontSize = 16.sp, color = Color.White)
            if (a.dressDup && a.dressRefund > 0) PixelText("🪙 +${a.dressRefund} 골드로 돌려받았어요", fontSize = 15.sp, color = Color(0xFFFDE68A))
        }
    }
}

/**
 * 내 보관함: 모은 보석 보기 + 네임플레이트 고르기 (본인이 출결번호를 누른 뒤에 열림)
 * 네임플레이트를 누르면 바로 바뀌고, 쓰고 있는 걸 다시 누르면 벗음.
 * (9단계) 맨 위에 내 캐릭터 + 골드, 보석은 등급 색 테두리 · 누르면 장착/해제 (장착한 보석 1개만 골드를 줌)
 */
@Composable
fun CollectionDialog(s: Student, startTab: Int = 0, onClose: () -> Unit) {
    var tab by remember { mutableStateOf(startTab) }
    var charScale by rememberCharScale()
    // 아무도 안 건드리면 90초 뒤 닫힘 (누를 때마다 다시 셈)
    LaunchedEffect(tab, s.plate, s.gem, s.gold, s.dress.size, s.dressEquip) { delay(90_000); onClose() }
    val gemSet = remember(s.gems) { s.gems.toSet() }
    val plateSet = remember(s.plates) { s.plates.toSet() }
    // 보석 목록: 가진 것 + 사용중인 미보유(까만 그림자). 장착 → 가진 것(높은 등급 먼저) → 못 가진 것
    val gemEnabled = Loot.gemEnabled
    val gemRarity = Loot.gemRarity
    val gemList = remember(gemSet, s.gem, gemEnabled, gemRarity) {
        Loot.allGems.filter { it in gemSet || Loot.isGemOn(it) }
            .sortedWith(compareBy<String>({ it != s.gem }, { it !in gemSet }, { -Loot.rarityOf(it).ordinal }, { it }))
    }
    val plateList = remember(plateSet) { Loot.allPlates.sortedBy { it !in plateSet } }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        GamePanel(Modifier.fillMaxWidth(0.94f).fillMaxHeight(0.9f), padding = 20.dp, horizontalAlignment = Alignment.Start) {
            run {
                val cfg = LocalConfiguration.current
                // 기본은 이 기기 화면에 딱 맞게 자동 계산: 창 가로의 약 30% / 캐릭터 높이는 창 높이에서 이름표·계급 자리를 뺀 만큼. 막대는 그 배율
                val scrW = cfg.screenWidthDp.toFloat()
                val scrH = cfg.screenHeightDp.toFloat()
                val leftW = minOf(scrW * 0.94f * 0.30f * UiSize.box(scrW > scrH) * charScale, scrW * 0.5f).dp
                val charH = (minOf(leftW.value * 0.9f, maxOf(scrH * 0.9f - 250f, 100f) * charScale) * UiSize.char(scrW > scrH)).dp
                Row(Modifier.fillMaxSize()) {
                // ── 왼쪽 고정: 내 캐릭터(크게) → 네임플레이트 → 레벨·계급 (크기 조절 가능) ──
                Column(
                    Modifier.width(leftW).fillMaxHeight().verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    MyCharacter(s, charH)
                    Spacer(Modifier.size(8.dp))
                    if (s.plate.isNotBlank()) NamePlate(s.plate, s.name, leftW, 24.sp)
                    else Text(s.name, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.size(6.dp))
                    RankHeader(s.level)
                    if (s.grade.isNotBlank()) Text(s.grade, fontSize = 16.sp, color = gameMuted())
                    Spacer(Modifier.size(10.dp))
                    CharSizeBar(charScale, { charScale = it })
                }
                Spacer(Modifier.width(16.dp))
                // ── 오른쪽: 제목 · 탭 · 내용 ──
                Column(Modifier.weight(1f).fillMaxHeight()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("${s.name}의 보관함", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                        PixelText("💰 ${s.gold} 골드", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        DogamBadge(s)
                    }
                    Spacer(Modifier.weight(1f))
                    GameTextButton("닫기", 20.sp) { onClose() }
                }
                Spacer(Modifier.size(8.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GamePill("보석 ${s.gems.size}/${maxOf(Loot.enabledCount, s.gems.size)}", tab == 0, 20.sp) { tab = 0 }
                    GamePill("네임플레이트 ${s.plates.size}/${Loot.PLATE_COUNT}", tab == 1, 20.sp) { tab = 1 }
                    GamePill("🎁 뽑기", tab == 2, 20.sp) { tab = 2 }
                    GamePill("👗 드레스룸", tab == 3, 20.sp) { tab = 3 }
                    GamePill("📖 도감", tab == 4, 20.sp) { tab = 4 }
                }
                Spacer(Modifier.size(8.dp))
                if (tab == 0) {
                    // 지금 장착한 보석과 그 능력
                    val eq = s.gem
                    if (eq.isNotBlank()) {
                        val r = Loot.rarityOf(eq)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            GemImage(eq, 36.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("장착 중 · ${r.label} · 등원/하원마다 ${r.goldMin}~${r.goldMax}골드", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(r.argb))
                        }
                    } else {
                        Text("보석을 장착하면 등원·하원 때 골드를 받아요. 가진 보석을 눌러 보세요!", color = gameMuted(), fontSize = 14.sp)
                    }
                    Text("장착한 보석 1개만 힘을 써요 (누르면 장착, 다시 누르면 해제)", color = gameMuted(), fontSize = 13.sp)
                    LazyVerticalGrid(GridCells.Adaptive(92.dp), Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(gemList) { id ->
                            val own = id in gemSet
                            val on = s.gem == id
                            val r = Loot.rarityOf(id)
                            val rc = if (own) Color(r.argb) else Color(0x33808080)
                            val shape = RoundedCornerShape(12.dp)
                            Column(
                                Modifier.clip(shape)
                                    .border(if (on) 4.dp else 2.dp, rc, shape)
                                    .then(if (on) Modifier.background(rc.copy(alpha = 0.22f)) else Modifier)
                                    .then(if (own) Modifier.clickable { Repo.setGem(s.id, if (on) "" else id) } else Modifier)
                                    .padding(vertical = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                GameSlot(Modifier.padding(2.dp)) { GemImage(id, 60.dp, locked = !own) }
                                if (own) Text(
                                    if (on) "장착중" else r.label, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                    color = if (on) Color.White else rc,
                                    modifier = if (on) Modifier.clip(RoundedCornerShape(6.dp)).background(OkGreen).padding(horizontal = 6.dp, vertical = 1.dp) else Modifier,
                                ) else Text("?", fontSize = 12.sp, color = gameMuted())
                            }
                        }
                    }
                } else if (tab == 1) {
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
                                if (on) PixelText("✔ 사용 중", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White,
                                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(OkGreen).padding(horizontal = 6.dp, vertical = 2.dp))
                            }
                        }
                    }
                } else if (tab == 2) {
                    GachaPanel(s, Modifier.weight(1f).fillMaxWidth())   // 10단계: 뽑기
                } else if (tab == 3) {
                    DressRoomPanel(s, Modifier.weight(1f).fillMaxWidth())   // 11단계: 드레스룸
                } else {
                    DogamPanel(s, Modifier.weight(1f).fillMaxWidth())   // 도감
                }
                }   // 오른쪽 Column 끝
                }   // 왼쪽+오른쪽 Row 끝
            }
        }
    }
}

private fun pctText(v: Double): String = if (v % 1.0 == 0.0) v.toInt().toString() else v.toString()

/**
 * 관리자 설정: 네임플레이트 얻을 확률 (%) + 드레스업 설정(보석 등급별 확률 등)
 * 저장하면 Firestore(config/loot)에 올라가서 출석 단말기에도 바로 똑같이 적용돼요.
 */
@Composable
fun LootSettings() {
    var plate by remember { mutableStateOf(pctText(Loot.plateChance)) }
    var msg by remember { mutableStateOf("") }
    // 서버 값이 늦게 도착하거나 저장 후 바뀌면 입력칸도 맞춰 줌
    LaunchedEffect(Loot.plateChance) { plate = pctText(Loot.plateChance) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle("네임플레이트 얻을 확률")
        Text("등원·하원을 누를 때마다 이 확률로 뽑아요 (0 ~ 100%, 소수점 가능). 하루에 등원 한 번·하원 한 번만 뽑아요.\n보석은 아래 [보석 등급별 획득 확률]에서 등급마다 정해요.\n" +
            "※ 같은 아이로 같은 날 다시 눌러 보면 이미 뽑았기 때문에 아무것도 안 나와요. 확인할 땐 다른 아이로 눌러 보세요. 이미 가진 보석·네임플레이트는 다시 안 나와요.", color = Muted, fontSize = 13.sp)
        Text("지금 이 기기에 적용된 값: 보석(등급 합계) ${pctText(Loot.gemChance)}% · 네임플레이트 ${pctText(Loot.plateChance)}%", fontWeight = FontWeight.Bold, color = Navy, fontSize = 14.sp)
        PixelText(Loot.syncNote, color = if (Loot.syncNote.startsWith("⚠")) BadRed else Muted, fontSize = 12.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(plate, { plate = it.filter { c -> c.isDigit() || c == '.' }; msg = "" }, label = { Text("네임플레이트 확률 %") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("빠른 선택", color = Muted, fontSize = 13.sp)
            listOf("100", "50", "30", "10").forEach { v -> TextButton(onClick = { plate = v; msg = "" }) { Text("$v%") } }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = {
                val p = plate.toDoubleOrNull()?.coerceIn(0.0, 100.0) ?: Loot.plateChance
                Repo.saveLootConfig(p)
                plate = pctText(p)
                msg = "저장됐어요 ✔ (지금 적용: 네임플레이트 ${pctText(p)}%)"
            }) { Text("확률 저장") }
            OutlinedButton(onClick = { Repo.refreshLootNow(); msg = "서버에서 다시 받는 중…" }) { Text("서버에서 다시 받기") }
            if (msg.isNotBlank()) PixelText(msg, color = OkGreen, fontSize = 13.sp)
        }
        DressSettings()   // 드레스업: 등급 비율 · 뽑기/옷 드롭 · 레전드리 보너스 상품
    }
}
