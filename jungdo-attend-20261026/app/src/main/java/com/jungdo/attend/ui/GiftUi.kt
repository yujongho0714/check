package com.jungdo.attend.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jungdo.attend.data.AwardResult
import com.jungdo.attend.data.Dress
import com.jungdo.attend.data.Gifts
import com.jungdo.attend.data.Prizes
import com.jungdo.attend.data.Rarity
import com.jungdo.attend.data.Repo
import com.jungdo.attend.data.Student
import kotlinx.coroutines.delay

/*
 * 관장님 선물 (전체 지급 / 개별 지급)
 *  - GiftTab : 관리자 탭. 종류(장비·보석·네임플레이트·상품) + 옵션 + 개수 → 전체 재원생 또는 고른 원생에게 보내기
 *  - GiftRow : 키오스크 완료 화면. 선물을 받은 순간 무엇을 받았는지 보여줌
 *  선물은 원생이 다음 "진짜 첫" 등원/하원을 누를 때 한 번만 받음 (Gifts 설명 참고)
 */

@Composable
private fun GiftChip(text: String, on: Boolean, color: Color = Navy, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(50))
            .background(if (on) color else Color(0x1A808080))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(text, fontSize = 14.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal, color = if (on) Color.White else Color.Unspecified)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GiftTab(students: List<Student>) {
    var kind by remember { mutableStateOf(Gifts.DRESS) }
    var dressCat by remember { mutableStateOf("any") }
    var gemRar by remember { mutableStateOf("any") }
    var prizeId by remember { mutableStateOf("") }
    var countText by remember { mutableStateOf("1") }
    var toAll by remember { mutableStateOf(true) }
    var picked by remember { mutableStateOf<Set<String>>(emptySet()) }
    var query by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }

    val active = remember(students) { students.filter { it.isActive }.sortedBy { it.name } }
    val n = countText.toIntOrNull() ?: 0
    val arg = when (kind) { Gifts.DRESS -> dressCat; Gifts.GEM -> gemRar; Gifts.PRIZE -> prizeId; else -> "any" }
    val targets = if (toAll) active else active.filter { it.id in picked }
    val ready = n in 1..Gifts.MAX_N && targets.isNotEmpty() && (kind != Gifts.PRIZE || prizeId.isNotBlank())
    val preview = if (n in 1..Gifts.MAX_N) Gifts.label(Gifts.make(kind, arg, n)) else ""

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SectionTitle("선물 보내기 (전체 / 개별)")
        Text(
            "보낸 선물은 그 원생이 다음에 등원이나 하원을 누를 때 딱 한 번만 받아요. 같은 날 같은 버튼을 또 눌러도 다시 안 받아요. 장비는 고른 부위에서 랜덤으로 나오고(없는 옷 먼저), 보석·네임플레이트는 아직 없는 것 중에서 랜덤이에요.",
            color = Muted, fontSize = 13.sp,
        )

        Text("1. 무엇을 보낼까요", fontWeight = FontWeight.Bold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            GiftChip("장비", kind == Gifts.DRESS) { kind = Gifts.DRESS }
            GiftChip("보석", kind == Gifts.GEM) { kind = Gifts.GEM }
            GiftChip("네임플레이트", kind == Gifts.PLATE) { kind = Gifts.PLATE }
            GiftChip("상품", kind == Gifts.PRIZE) { kind = Gifts.PRIZE }
        }
        when (kind) {
            Gifts.DRESS -> {
                Text("부위 (그 부위 옷 중 랜덤)", fontSize = 13.sp, color = Muted)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    GiftChip("아무 부위", dressCat == "any") { dressCat = "any" }
                    Dress.CATS.forEach { c -> GiftChip(Dress.catLabel(c), dressCat == c) { dressCat = c } }
                }
            }
            Gifts.GEM -> {
                Text("등급 (사용중인 보석 중 아직 없는 것 랜덤)", fontSize = 13.sp, color = Muted)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    GiftChip("아무 등급", gemRar == "any") { gemRar = "any" }
                    Rarity.values().forEach { r -> GiftChip(r.label, gemRar == r.code, Color(r.argb)) { gemRar = r.code } }
                }
            }
            Gifts.PRIZE -> {
                Text("상품 (등록된 상품에서 고르기)", fontSize = 13.sp, color = Muted)
                if (Prizes.list.isEmpty()) Text("등록된 상품이 없어요. [문자·안내]의 상품 설정에서 먼저 등록해 주세요.", color = BadRed, fontSize = 13.sp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Prizes.list.forEach { p -> GiftChip(p.name + (p.stock?.let { " (재고 $it)" } ?: ""), prizeId == p.id) { prizeId = p.id } }
                }
            }
        }
        OutlinedTextField(
            countText, { countText = it.filter(Char::isDigit).take(2) }, label = { Text("개수 (한 명당, 1~${Gifts.MAX_N})") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(),
        )

        HorizontalDivider()
        Text("2. 누구에게 보낼까요", fontWeight = FontWeight.Bold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            GiftChip("전체 (재원 ${active.size}명)", toAll) { toAll = true }
            GiftChip("개별 고르기", !toAll) { toAll = false }
        }
        if (!toAll) {
            OutlinedTextField(query, { query = it }, label = { Text("이름 검색") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            val shown = active.filter { query.isBlank() || it.name.contains(query.trim()) }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("선택 ${targets.size}명", fontWeight = FontWeight.Bold)
                TextButton(onClick = { picked = picked + shown.map { it.id } }) { Text("보이는 사람 전체선택") }
                TextButton(onClick = { picked = emptySet() }) { Text("선택 해제") }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                shown.forEach { s -> GiftChip(s.name, s.id in picked, Color(0xFF16A34A)) { picked = if (s.id in picked) picked - s.id else picked + s.id } }
            }
        }

        HorizontalDivider()
        Text(
            if (ready) "「$preview」를 ${targets.size}명에게 보내요" else "종류·개수·받을 사람을 정해 주세요",
            fontWeight = FontWeight.Bold, color = if (ready) OkGreen else BadRed,
        )
        Button(enabled = ready, onClick = { confirm = true }) { Text("선물 보내기") }
        if (msg.isNotBlank()) Text(msg, color = OkGreen, fontWeight = FontWeight.Bold)

        // ── 아직 안 받은 선물 ──
        HorizontalDivider()
        val waiting = active.filter { it.gifts.isNotEmpty() }
        Text("아직 안 받은 선물 (${waiting.size}명)", fontWeight = FontWeight.Bold)
        if (waiting.isEmpty()) Text("모두 받았어요", color = Muted, fontSize = 13.sp)
        waiting.forEach { s ->
            Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text(s.name, fontWeight = FontWeight.Bold)
                s.gifts.forEach { raw ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("· " + Gifts.label(raw), modifier = Modifier.weight(1f), fontSize = 14.sp)
                        TextButton(onClick = { Repo.cancelGift(s.id, raw); msg = "${s.name}의 선물을 취소했어요" }) { Text("취소", color = BadRed) }
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("선물을 보낼까요?") },
            text = { Text("「$preview」를 ${targets.size}명에게 보내요.\n각자 다음 등원/하원 때 한 번만 받아요. 보내기 전에는 취소할 수 있어요.") },
            confirmButton = {
                Button(onClick = {
                    Repo.sendGifts(targets, Gifts.make(kind, arg, n))
                    msg = "${targets.size}명에게 「$preview」를 보냈어요 ✔"
                    confirm = false
                }) { Text("보내기") }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("취소") } },
        )
    }
}

/** 키오스크 완료 화면: 관장님 선물로 이번에 받은 것들 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GiftRow(a: AwardResult) {
    if (!a.hasGift) return
    val pop = remember(a) { Animatable(0.3f) }
    LaunchedEffect(a) {
        delay(1000)
        pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
    }
    Column(
        Modifier.graphicsLayer { scaleX = pop.value; scaleY = pop.value; alpha = ((pop.value - 0.3f) / 0.7f).coerceIn(0f, 1f) },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("🎁 관장님 선물이 도착했어요!", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFDE68A))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            a.giftGems.forEach { id -> GemImage(id, 64.dp) }
            a.giftPlates.forEach { id -> NamePlate(id, a.name, 180.dp, 18.sp) }
            a.giftDress.forEach { key -> DressCharacter(mapOf(Dress.catOf(key) to key), 84.dp) }
            a.giftPrizes.groupBy { it.prizeId }.values.forEach { list ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    PrizeImage(list.first().photo, 90.dp, fit = true)
                    Text(list.first().name + if (list.size > 1) " ×${list.size}" else "", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.Center)
                }
            }
        }
        if (a.giftGold > 0) PixelTextSafe("🪙 이미 있는 옷은 +${a.giftGold} 골드로 돌려받았어요")
    }
}

@Composable
private fun PixelTextSafe(t: String) {
    Text(t, fontSize = 15.sp, color = Color(0xFFFDE68A))
}
