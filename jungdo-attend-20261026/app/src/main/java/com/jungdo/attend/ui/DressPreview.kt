package com.jungdo.attend.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jungdo.attend.data.Dress
import com.jungdo.attend.ui.game.PixelIcon

@Composable
private fun PreviewChip(text: String, on: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(50))
            .background(if (on) Color(0xFF1F3A68) else Color(0x1A808080))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) { Text(text, fontSize = 15.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal, color = if (on) Color.White else Color.Unspecified) }
}

/**
 * 캐릭터 미리보기 (테스트용): 방향·동작을 바꿔 보고, 부위별로 옷을 한 벌씩 넘겨 가며 입혀 봄.
 * 저장은 안 함 — 7단계 모션 확인용. (실제 착용 저장은 드레스룸 11단계)
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DressPreviewPanel() {
    val pack = rememberDressPack()
    var equip by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var dir by remember { mutableStateOf(DressDir.FRONT) }
    var action by remember { mutableStateOf(DressAction.STAND) }

    Column(Modifier.padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle("캐릭터 미리보기 (테스트)")
        Text("옷 800종이 제대로 겹쳐 그려지는지, 서기·걷기·공격 모션이 맞는지 확인하는 화면이에요. 여기서 입혀 본 건 저장되지 않아요.", color = Muted, fontSize = 13.sp)
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            DressCharacter(equip, 240.dp, dir = dir, action = action, onAttackDone = { action = DressAction.STAND })
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(DressDir.FRONT to "앞", DressDir.BACK to "뒤", DressDir.RIGHT to "오른쪽", DressDir.LEFT to "왼쪽").forEach { (d, label) ->
                PreviewChip(label, dir == d) { dir = d }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            PreviewChip("서기", action == DressAction.STAND) { action = DressAction.STAND }
            PreviewChip("걷기", action == DressAction.WALK) { action = DressAction.WALK }
            PreviewChip("공격", action == DressAction.ATTACK) { action = DressAction.ATTACK }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = pack != null, onClick = {
                val p = pack ?: return@Button
                equip = Dress.CATS.mapNotNull { c -> p.itemsByCat[c]?.randomOrNull()?.let { c to it.key } }.toMap()
            }) { Text("랜덤 코디") }
            OutlinedButton(onClick = { equip = emptyMap() }) { Text("모두 벗기") }
        }
        Dress.CATS.forEach { cat ->
            val items = pack?.itemsByCat?.get(cat).orEmpty()
            val idx = items.indexOfFirst { it.key == equip[cat] }   // -1 = 안 입음
            fun go(delta: Int) {
                if (items.isEmpty()) return
                val n = items.size + 1                       // 안 입음 포함
                val next = ((idx + 1 + delta) % n + n) % n - 1
                equip = if (next < 0) equip - cat else equip + (cat to items[next].key)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(Dress.catLabel(cat), Modifier.width(48.dp), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                TextButton(onClick = { go(-1) }) { PixelIcon("arrow_l", 22.dp) }
                Text(
                    if (idx < 0) "안 입음" else "${idx + 1}. ${items[idx].name}",
                    Modifier.weight(1f), fontSize = 14.sp, maxLines = 1,
                )
                TextButton(onClick = { go(1) }) { PixelIcon("arrow_r", 22.dp) }
            }
        }
    }
}
