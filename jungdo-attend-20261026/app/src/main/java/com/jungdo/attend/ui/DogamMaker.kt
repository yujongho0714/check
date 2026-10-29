package com.jungdo.attend.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jungdo.attend.data.Dogam
import com.jungdo.attend.data.Loot
import com.jungdo.attend.data.Rarity
import com.jungdo.attend.data.Repo

/**
 * 도감 제작기 (관리자 · 도감 탭 맨 위)
 *  - 지금 "사용중"인 보석을 등급별로 세서, 2개씩 무작위로 짝지어 도감을 한 번에 만들어 줌
 *  - 보석이 홀수면 남는 1개는 도감 1개로 만들고 효과는 절반
 *  - 노말·레어·레전드리 = 등원/하원 추가 골드, 에픽 = 보석 획득 확률 +%, 유니크 = 장비 획득 확률 +%
 *  - 확률 보너스는 활성 도감끼리 "합적용"(더하기) — 곱적용보다 항상 같거나 덜 올라서 당첨 확률이 더 낮음
 *  - [도감 만들기]를 누르면 지금 있는 도감은 모두 지워지고 새로 만들어짐
 */
@Composable
fun DogamMaker(onDone: (String) -> Unit) {
    val rarityMap = Loot.gemRarity
    val enabled = Loot.gemEnabled
    val groups = remember(rarityMap, enabled) { Loot.allGems.filter { Loot.isGemOn(it) }.groupBy { Loot.rarityOf(it) } }
    val total = groups.values.sumOf { it.size }

    var goldTxt by remember { mutableStateOf(Dogam.makerGold.mapValues { it.value.toString() }) }
    var pctTxt by remember { mutableStateOf(Dogam.makerPct.mapValues { Dogam.fmt(it.value) }) }
    var confirm by remember { mutableStateOf(false) }

    // 만들었을 때 생길 도감 수 / 최대 확률 보너스(전부 모았을 때, 합적용)
    var dogamCount = 0
    var gemMax = 0.0
    var dressMax = 0.0
    Rarity.values().forEach { r ->
        val n = groups[r]?.size ?: 0
        val pairs = n / 2
        val single = n % 2
        dogamCount += pairs + single
        val p = (Dogam.makerPct[r] ?: 0.0)
        val sum = pairs * p + single * p / 2
        if (r == Rarity.EPIC) gemMax += sum
        if (r == Rarity.UNIQUE) dressMax += sum
    }

    val shape = RoundedCornerShape(12.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).border(2.dp, Navy, shape).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("도감 제작기 (등급별 자동 만들기)", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Navy)
        Text(
            "지금 사용중인 보석을 등급별로 2개씩 무작위로 짝지어 도감을 한 번에 만들어요. 홀수면 남는 1개는 도감 1개로 만들고 효과는 절반이에요. (보석 개수는 [보석 등급] 탭에서 정해요)",
            color = Muted, fontSize = 13.sp,
        )
        Text("사용중 보석 ${total}개 → 도감 ${dogamCount}개가 만들어져요", fontWeight = FontWeight.Bold, fontSize = 15.sp)

        Rarity.values().forEach { r ->
            val n = groups[r]?.size ?: 0
            val pairs = n / 2
            val single = n % 2
            val isGold = r == Rarity.NORMAL || r == Rarity.RARE || r == Rarity.LEGENDARY
            val what = when (r) {
                Rarity.EPIC -> "보석 획득 확률 +%"
                Rarity.UNIQUE -> "장비(옷) 획득 확률 +%"
                else -> "등원·하원 추가 골드"
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(r.label, color = Color(r.argb), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(
                        "보석 ${n}개 → 짝 ${pairs}개" + (if (single == 1) " + 남는 1개(절반)" else "") + " = 도감 ${pairs + single}개",
                        fontSize = 12.sp, color = Muted,
                    )
                    Text(what + " (짝 기준)", fontSize = 12.sp, color = Muted)
                }
                if (isGold) {
                    OutlinedTextField(
                        goldTxt[r] ?: "0",
                        { v ->
                            val t = v.filter(Char::isDigit).take(4)
                            goldTxt = goldTxt + (r to t)
                            Dogam.makerGold = Dogam.makerGold + (r to (t.toIntOrNull() ?: 0))
                        },
                        label = { Text("골드") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.width(100.dp),
                    )
                } else if (r == Rarity.EPIC || r == Rarity.UNIQUE) {
                    OutlinedTextField(
                        pctTxt[r] ?: "0",
                        { v ->
                            val t = v.filter { it.isDigit() || it == '.' }.take(7)
                            pctTxt = pctTxt + (r to t)
                            Dogam.makerPct = Dogam.makerPct + (r to (t.toDoubleOrNull() ?: 0.0))
                        },
                        label = { Text("%") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.width(100.dp),
                    )
                }
            }
        }

        Text(
            "전부 모았을 때 최대: 보석 획득 +${Dogam.fmt(gemMax)}% · 장비 획득 +${Dogam.fmt(dressMax)}% (합적용)",
            fontWeight = FontWeight.Bold, fontSize = 14.sp, color = OkGreen,
        )
        Text(
            "활성 도감이 여러 개면 확률은 전부 더하기(합적용)로 계산해요. 곱하기(곱적용)보다 항상 같거나 덜 올라서 당첨 확률이 더 낮아요.",
            color = Muted, fontSize = 12.sp,
        )
        Button(enabled = total > 0, onClick = { confirm = true }) { Text("도감 만들기") }
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("도감을 새로 만들까요?") },
            text = { Text("지금 있는 도감은 모두 지워지고, 사용중인 보석 ${total}개로 도감 ${dogamCount}개가 새로 만들어져요. 아이들이 가진 보석은 그대로예요.") },
            confirmButton = {
                Button(onClick = {
                    Repo.saveDogam(Dogam.build(groups))
                    onDone("도감 ${dogamCount}개를 만들었어요 ✔ (모든 기기에 바로 적용돼요)")
                    confirm = false
                }) { Text("만들기") }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("취소") } },
        )
    }
}
