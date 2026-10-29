package com.jungdo.attend.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jungdo.attend.data.Loot
import com.jungdo.attend.data.Prizes
import com.jungdo.attend.data.Rarity
import com.jungdo.attend.data.Repo
import com.jungdo.attend.ui.game.PixelText
import com.jungdo.attend.ui.game.PixelCheck

private fun pct(v: Double): String = if (v % 1.0 == 0.0) v.toInt().toString() else v.toString()

private fun cleanNum(s: String) = s.filter { it.isDigit() || it == '.' }

/**
 * 관리자 설정 (드레스업): 보석 등급별 획득 비율 · 뽑기 가격/환급/옷 드롭 확률 · 레전드리 보너스 상품
 * LootSettings 맨 아래에 붙어서 관리자 화면·서버 화면 양쪽에 나옴. 저장은 Firestore config/loot, 모든 기기에 바로 반영.
 */
@Composable
fun DressSettings() {
    // ── 등급별 획득 비율 ──
    val wText = remember { mutableStateMapOf<Rarity, String>() }
    LaunchedEffect(Loot.rarityWeights) {
        Rarity.values().forEach { wText[it] = pct(Loot.rarityWeights[it] ?: it.defaultWeight) }
    }
    var wMsg by remember { mutableStateOf("") }
    val wSum = Rarity.values().sumOf { wText[it]?.toDoubleOrNull() ?: 0.0 }

    // ── 뽑기 · 옷 드롭 ──
    var price by remember { mutableStateOf(Loot.gachaPrice.toString()) }
    var refund by remember { mutableStateOf(Loot.dupRefund.toString()) }
    var dress by remember { mutableStateOf(pct(Loot.dressChance)) }
    LaunchedEffect(Loot.gachaPrice, Loot.dupRefund, Loot.dressChance) {
        price = Loot.gachaPrice.toString(); refund = Loot.dupRefund.toString(); dress = pct(Loot.dressChance)
    }
    var gMsg by remember { mutableStateOf("") }

    // ── 레전드리 보너스 상품 ──
    var lChance by remember { mutableStateOf(pct(Prizes.legendaryChance)) }
    var lIds by remember { mutableStateOf(Prizes.legendaryIds) }
    LaunchedEffect(Prizes.legendaryChance, Prizes.legendaryIds) { lChance = pct(Prizes.legendaryChance); lIds = Prizes.legendaryIds }
    var lMsg by remember { mutableStateOf("") }

    Column(Modifier.padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle("보석 등급별 획득 비율")
        Text(
            "보석이 나올 때 등급을 먼저 뽑고, 그 등급의 안 가진 보석 중 하나가 나와요. 숫자는 비율이라 합이 100이 아니어도 돼요. 0 이면 그 등급은 안 나와요. 그 등급 보석을 다 모았으면 다른 등급으로 넘어가요.",
            color = Muted, fontSize = 13.sp,
        )
        Rarity.values().forEach { r ->
            OutlinedTextField(
                wText[r] ?: "", { wText[r] = cleanNum(it); wMsg = "" },
                label = { Text("${r.label} 비율 (골드 ${r.goldMin}~${r.goldMax})") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(),
            )
        }
        Text(
            "합계 ${pct(wSum)} → " + Rarity.values().joinToString(" · ") { r ->
                val v = wText[r]?.toDoubleOrNull() ?: 0.0
                "${r.label} ${if (wSum > 0) String.format(java.util.Locale.US, "%.1f", v / wSum * 100.0) else "0"}%"
            },
            color = Navy, fontSize = 13.sp, fontWeight = FontWeight.Bold,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = {
                if (wSum <= 0.0) { wMsg = "⚠ 비율이 전부 0 이면 보석이 안 나와요. 하나는 0보다 크게 적어 주세요." }
                else {
                    Repo.saveRarityWeights(Rarity.values().associateWith { wText[it]?.toDoubleOrNull() ?: 0.0 })
                    wMsg = "저장됐어요 ✔"
                }
            }) { Text("비율 저장") }
            OutlinedButton(onClick = { Rarity.values().forEach { wText[it] = pct(it.defaultWeight) }; wMsg = "기본값을 채웠어요. [비율 저장]을 눌러야 적용돼요." }) { Text("기본값") }
        }
        if (wMsg.isNotBlank()) PixelText(wMsg, color = if (wMsg.startsWith("⚠")) BadRed else OkGreen, fontSize = 13.sp)

        SectionTitle("뽑기 · 옷 드롭")
        Text("옷 뽑기 화면과 출석 옷 드롭은 이후 단계에서 붙어요. 지금은 값만 저장해 둬요.", color = Muted, fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(price, { price = it.filter(Char::isDigit); gMsg = "" }, label = { Text("1회 뽑기 골드") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
            OutlinedTextField(refund, { refund = it.filter(Char::isDigit); gMsg = "" }, label = { Text("중복 환급 골드") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
        }
        OutlinedTextField(dress, { dress = cleanNum(it); gMsg = "" }, label = { Text("출석 때 옷 얻을 확률 %") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = {
                val p = price.toIntOrNull() ?: Loot.gachaPrice
                val rf = refund.toIntOrNull() ?: Loot.dupRefund
                val d = (dress.toDoubleOrNull() ?: Loot.dressChance).coerceIn(0.0, 100.0)
                Repo.saveGachaConfig(p, rf, d)
                price = p.toString(); refund = rf.toString(); dress = pct(d)
                gMsg = "저장됐어요 ✔"
            }) { Text("뽑기 설정 저장") }
            if (gMsg.isNotBlank()) PixelText(gMsg, color = OkGreen, fontSize = 13.sp)
        }

        SectionTitle("레전드리 보너스 상품")
        Text(
            "레전드리 보석을 장착한 아이가 등원·하원할 때, 일반 상품에 안 걸렸으면 이 확률로 아래 체크한 상품 중 하나(재고 있는 것)가 한 번 더 당첨돼요. 확률 0 이거나 상품을 하나도 안 고르면 꺼져 있어요.",
            color = Muted, fontSize = 13.sp,
        )
        OutlinedTextField(lChance, { lChance = cleanNum(it); lMsg = "" }, label = { Text("보너스 당첨 확률 %") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
        if (Prizes.list.isEmpty()) Text("등록된 상품이 없어요. 아래 \"상품\" 설정에서 먼저 추가해 주세요.", color = Muted, fontSize = 13.sp)
        Prizes.list.forEach { p ->
            val on = p.id in lIds
            Row(
                Modifier.fillMaxWidth().clickable { lIds = if (on) lIds - p.id else lIds + p.id; lMsg = "" }.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                PixelCheck(on)
                PrizeImage(p.photo, 40.dp)
                Text(p.name, fontSize = 16.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = {
                val c = (lChance.toDoubleOrNull() ?: Prizes.legendaryChance).coerceIn(0.0, 100.0)
                Repo.saveLegendaryPrize(c, lIds)
                lChance = pct(c)
                lMsg = if (c > 0.0 && lIds.isNotEmpty()) "저장됐어요 ✔" else "저장됐어요 ✔ (지금은 꺼져 있어요: 확률 0 이거나 상품이 없어요)"
            }) { Text("보너스 저장") }
        }
        if (lMsg.isNotBlank()) PixelText(lMsg, color = OkGreen, fontSize = 13.sp)

        DressPreviewPanel()   // 7단계: 캐릭터 그리기 확인용 미리보기
    }
}
