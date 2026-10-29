package com.jungdo.attend.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jungdo.attend.data.GemData
import com.jungdo.attend.data.Loot
import com.jungdo.attend.data.Rarity
import com.jungdo.attend.data.Repo
import com.jungdo.attend.ui.game.PixelText
import com.jungdo.attend.ui.game.PixelIcon

private enum class OnFilter(val label: String) { ALL("전체"), ON("사용중"), OFF("꺼짐") }

@Composable
private fun PickChip(text: String, on: Boolean, color: Color = Color(0xFF1F3A68), onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(50))
            .background(if (on) color else Color(0x1A808080))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(text, fontSize = 14.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal, color = if (on) Color.White else Color.Unspecified)
    }
}

/**
 * 관리자 화면: 보석 등급 · 사용 지정
 *  - 보석 396종을 격자로 보여주고(등급 색 테두리), 체크해서 한꺼번에 등급 변경 / 사용 켜기·끄기
 *  - 저장은 Firestore config/gemRarity (r = 보석별 등급, on = 사용중 보석 목록). 모든 기기에 바로 반영
 *  - 사용중 보석을 한 번도 지정한 적이 없으면 396종 전부 사용중
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GemRarityTab() {
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var onFilter by remember { mutableStateOf(OnFilter.ALL) }
    var rarityFilter by remember { mutableStateOf<Rarity?>(null) }
    var query by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf("") }
    var confirmClear by remember { mutableStateOf(false) }

    val rarityMap = Loot.gemRarity
    val enabledSet = Loot.gemEnabled
    val shown = remember(rarityMap, enabledSet, onFilter, rarityFilter, query) {
        val q = query.trim()
        Loot.allGems.filter { id ->
            val on = enabledSet?.contains(id) ?: true
            val r = rarityMap[id] ?: Rarity.NORMAL
            val okOn = when (onFilter) { OnFilter.ALL -> true; OnFilter.ON -> on; OnFilter.OFF -> !on }
            val okRarity = rarityFilter == null || r == rarityFilter
            val okQuery = q.isEmpty() || id.contains(q) || q.toIntOrNull() == id.toInt() || GemData.name(id).contains(q)
            okOn && okRarity && okQuery
        }
    }
    val counts = Rarity.values().associateWith { r -> Loot.allGems.count { (rarityMap[it] ?: Rarity.NORMAL) == r } }

    LazyVerticalGrid(
        GridCells.Adaptive(72.dp),
        Modifier.fillMaxSize().padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(Modifier.fillMaxWidth().padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionTitle("보석 등급 · 사용 지정")
                Text(
                    "보석을 눌러 체크한 뒤, 등급을 바꾸거나 출석 때 나올지(사용) 정해요. 사용을 끈 보석은 새로 안 나오고, 이미 가진 아이는 그대로 가지고 있어요. 한 번도 지정하지 않으면 전부 사용중이에요.",
                    color = Muted, fontSize = 13.sp,
                )
                Text(
                    "사용중 ${Loot.enabledCount}개 / ${Loot.GEM_COUNT}" + if (enabledSet == null) " (지정 안 함 → 전부 사용)" else "",
                    fontWeight = FontWeight.Bold, color = Navy,
                )
                Text(Rarity.values().joinToString(" · ") { "${it.label} ${counts[it] ?: 0}" }, color = Muted, fontSize = 13.sp)

                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OnFilter.values().forEach { f -> PickChip(f.label, onFilter == f) { onFilter = f } }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    PickChip("모든 등급", rarityFilter == null) { rarityFilter = null }
                    Rarity.values().forEach { r -> PickChip(r.label, rarityFilter == r, Color(r.argb)) { rarityFilter = if (rarityFilter == r) null else r } }
                }
                OutlinedTextField(
                    query, { query = it }, label = { Text("번호 또는 이름 검색") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    Text("선택 ${selected.size}개 · 보이는 ${shown.size}개", fontWeight = FontWeight.Bold)
                    TextButton(onClick = { selected = selected + shown }) { Text("보이는 것 전체선택") }
                    TextButton(onClick = { selected = emptySet() }) { Text("선택 해제") }
                }

                Text("선택한 보석의 등급을 바꾸기", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Rarity.values().forEach { r ->
                        Button(
                            enabled = selected.isNotEmpty(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(r.argb),
                                contentColor = if (r == Rarity.NORMAL || r == Rarity.UNIQUE) Color.Black else Color.White,
                            ),
                            onClick = { Repo.saveGemRarity(selected, r); msg = "${selected.size}개를 ${r.label}(으)로 바꿨어요 ✔" },
                        ) { Text(r.label) }
                    }
                }

                Text("선택한 보석의 사용", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(enabled = selected.isNotEmpty(), onClick = { Repo.saveGemEnabled(selected, true); msg = "${selected.size}개 사용 켰어요 ✔" }) { Text("사용 켜기") }
                    OutlinedButton(enabled = selected.isNotEmpty(), onClick = { Repo.saveGemEnabled(selected, false); msg = "${selected.size}개 사용 껐어요 ✔" }) { Text("사용 끄기") }
                    OutlinedButton(enabled = selected.isNotEmpty(), onClick = { Repo.saveGemEnabledOnly(selected); msg = "선택한 ${selected.size}개만 사용해요 ✔ (나머지는 껐어요)" }) { Text("선택한 것만 사용") }
                    TextButton(onClick = { confirmClear = true }) { Text("지정 풀기 (전부 사용)", color = BadRed) }
                }
                if (msg.isNotBlank()) PixelText(msg, color = OkGreen, fontSize = 13.sp)
                Box(Modifier.padding(bottom = 4.dp))
            }
        }

        items(shown, key = { it }) { id ->
            val r = rarityMap[id] ?: Rarity.NORMAL
            val on = enabledSet?.contains(id) ?: true
            val sel = id in selected
            Box(
                Modifier.clip(RoundedCornerShape(10.dp))
                    .background(if (sel) Color(0x5522C55E) else Color.Transparent)
                    .border(if (sel) 3.dp else 2.dp, Color(r.argb), RoundedCornerShape(10.dp))
                    .clickable { selected = if (sel) selected - id else selected + id }
                    .padding(4.dp),
                contentAlignment = Alignment.TopCenter,
            ) {
                Column(Modifier.alpha(if (on) 1f else 0.35f), horizontalAlignment = Alignment.CenterHorizontally) {
                    GemImage(id, 52.dp)
                    Text("${id.toIntOrNull() ?: 0}", fontSize = 12.sp, textAlign = TextAlign.Center)
                    Text(if (on) r.label else "꺼짐", fontSize = 10.sp, color = Color(r.argb))
                }
                if (sel) PixelIcon("check", 20.dp, Modifier.align(Alignment.TopEnd))
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) { Box(Modifier.padding(bottom = 24.dp)) }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("사용 지정을 풀까요?") },
            text = { Text("지금 정해 둔 사용중 목록이 사라지고, 396종 전부 다시 출석 때 나와요. (등급은 그대로예요)") },
            confirmButton = { Button(onClick = { Repo.clearGemEnabled(); selected = emptySet(); msg = "지정을 풀었어요 → 전부 사용중 ✔"; confirmClear = false }) { Text("풀기") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("취소") } },
        )
    }
}
