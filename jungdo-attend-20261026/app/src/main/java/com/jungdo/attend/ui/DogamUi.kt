package com.jungdo.attend.ui

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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.jungdo.attend.data.Dogam
import com.jungdo.attend.data.DogamSet
import com.jungdo.attend.data.GemData
import com.jungdo.attend.data.Loot
import com.jungdo.attend.data.Repo
import com.jungdo.attend.data.Student
import com.jungdo.attend.ui.game.gameMuted
import com.jungdo.attend.ui.game.PixelText

/*
 * 도감 (보석 세트 효과)
 *  - DogamTab    : 관리자 화면 탭. 도감 만들기/고치기/지우기 (이름, 보석 1~5개, 등원 추가골드, 하원 추가골드)
 *  - DogamPanel  : 아이 보관함의 "도감" 탭. 어떤 도감이 활성화됐는지, 등원/하원 때 추가로 받는 골드
 *  - DogamBadge  : 보관함 맨 위 한 줄 요약
 */

// ───────────────────────────── 관리자 ─────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DogamTab() {
    val sets = Dogam.sets
    var editing by remember { mutableStateOf<DogamSet?>(null) }
    var delTarget by remember { mutableStateOf<DogamSet?>(null) }
    var msg by remember { mutableStateOf("") }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SectionTitle("도감")
        Text(
            "도감 하나에 보석을 1~5개 고르고, 등원할 때 / 하원할 때 추가로 받을 골드를 정해요. 아이가 그 보석을 전부 모으면 도감이 활성화돼서 등원·하원마다 추가 골드를 받아요. (장착하지 않아도 가지고만 있으면 돼요 · 활성 도감이 여러 개면 전부 더해져요)",
            color = Muted, fontSize = 13.sp,
        )
        Button(onClick = { editing = DogamSet(Dogam.newId(), "", emptyList(), 0, 0) }) { Text("＋ 도감 추가") }
        if (msg.isNotBlank()) PixelText(msg, color = OkGreen, fontSize = 13.sp)

        if (sets.isEmpty()) Text("아직 만든 도감이 없어요", color = Muted)
        sets.forEach { d ->
            val shape = RoundedCornerShape(12.dp)
            Column(
                Modifier.fillMaxWidth().clip(shape).border(1.5.dp, Color(0x55808080), shape).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(d.name.ifBlank { "(이름 없음)" }, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Navy)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    d.gems.forEach { id ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            GemImage(id, 48.dp)
                            Text(GemData.name(id), fontSize = 10.sp, color = Muted, maxLines = 1)
                        }
                    }
                }
                Text("등원 +${d.goldIn}골드 · 하원 +${d.goldOut}골드", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { editing = d }) { Text("수정") }
                    OutlinedButton(onClick = { delTarget = d }) { Text("삭제", color = BadRed) }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    editing?.let { cur ->
        DogamEditor(
            init = cur,
            onCancel = { editing = null },
            onSave = { saved ->
                val list = if (Dogam.sets.any { it.id == saved.id }) Dogam.sets.map { if (it.id == saved.id) saved else it } else Dogam.sets + saved
                Repo.saveDogam(list)
                msg = "「${saved.name}」 저장했어요 ✔ (모든 기기에 바로 적용돼요)"
                editing = null
            },
        )
    }

    delTarget?.let { d ->
        AlertDialog(
            onDismissRequest = { delTarget = null },
            title = { Text("「${d.name}」 도감을 지울까요?") },
            text = { Text("지우면 이 도감으로 받던 추가 골드가 사라져요. 아이들이 가진 보석은 그대로예요.") },
            confirmButton = { Button(onClick = { Repo.saveDogam(Dogam.sets.filter { it.id != d.id }); msg = "지웠어요 ✔"; delTarget = null }) { Text("지우기") } },
            dismissButton = { TextButton(onClick = { delTarget = null }) { Text("취소") } },
        )
    }
}

/** 도감 만들기/고치기 창: 이름 · 보석 1~5개 고르기 · 등원/하원 추가골드 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DogamEditor(init: DogamSet, onCancel: () -> Unit, onSave: (DogamSet) -> Unit) {
    var name by remember { mutableStateOf(init.name) }
    var picked by remember { mutableStateOf(init.gems) }
    var goldIn by remember { mutableStateOf(init.goldIn.toString()) }
    var goldOut by remember { mutableStateOf(init.goldOut.toString()) }
    var query by remember { mutableStateOf("") }
    var err by remember { mutableStateOf("") }

    val shown = remember(query) {
        val q = query.trim()
        Loot.allGems.filter { id -> q.isEmpty() || q.toIntOrNull() == id.toInt() || id.contains(q) || GemData.name(id).contains(q) }
    }

    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            Modifier.fillMaxWidth(0.96f).fillMaxHeight(0.94f),
            shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface,
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (Dogam.sets.any { it.id == init.id }) "도감 수정" else "도감 추가", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Navy)
                OutlinedTextField(name, { name = it.take(20) }, label = { Text("도감 이름") }, singleLine = true, modifier = Modifier.fillMaxWidth())

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        goldIn, { goldIn = it.filter(Char::isDigit).take(4) }, label = { Text("등원 추가골드") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        goldOut, { goldOut = it.filter(Char::isDigit).take(4) }, label = { Text("하원 추가골드") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f),
                    )
                }

                Text("고른 보석 ${picked.size}/${Dogam.MAX_GEMS} (누르면 빼요)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                if (picked.isEmpty()) Text("아래에서 보석을 1~5개 골라 주세요", color = Muted, fontSize = 13.sp)
                else FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    picked.forEach { id ->
                        Column(
                            Modifier.clip(RoundedCornerShape(8.dp)).background(Color(0x3322C55E)).clickable { picked = picked - id; err = "" }.padding(4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            GemImage(id, 44.dp)
                            PixelText("${id.toInt()}번 ✕", fontSize = 11.sp)
                        }
                    }
                }

                OutlinedTextField(query, { query = it }, label = { Text("번호 또는 이름으로 찾기") }, singleLine = true, modifier = Modifier.fillMaxWidth())

                LazyVerticalGrid(
                    GridCells.Adaptive(68.dp), Modifier.weight(1f).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(shown, key = { it }) { id ->
                        val sel = id in picked
                        val r = Loot.rarityOf(id)
                        val shape = RoundedCornerShape(8.dp)
                        Column(
                            Modifier.clip(shape)
                                .background(if (sel) Color(0x5522C55E) else Color.Transparent)
                                .border(if (sel) 3.dp else 1.5.dp, Color(r.argb), shape)
                                .clickable {
                                    if (sel) { picked = picked - id; err = "" }
                                    else if (picked.size >= Dogam.MAX_GEMS) err = "보석은 최대 ${Dogam.MAX_GEMS}개까지예요"
                                    else { picked = picked + id; err = "" }
                                }
                                .padding(3.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            GemImage(id, 46.dp)
                            Text("${id.toInt()}", fontSize = 11.sp, textAlign = TextAlign.Center)
                        }
                    }
                }

                if (err.isNotBlank()) Text(err, color = BadRed, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("취소") }
                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            val n = name.trim()
                            when {
                                n.isEmpty() -> err = "도감 이름을 적어 주세요"
                                picked.isEmpty() -> err = "보석을 1개 이상 골라 주세요"
                                else -> onSave(DogamSet(init.id, n, picked, goldIn.toIntOrNull() ?: 0, goldOut.toIntOrNull() ?: 0))
                            }
                        },
                    ) { Text("저장") }
                }
            }
        }
    }
}

// ───────────────────────────── 아이 보관함 ─────────────────────────────

/** 보관함 맨 위 한 줄 요약. 활성 도감이 없으면 아무것도 안 그림 */
@Composable
fun DogamBadge(s: Student) {
    val own = remember(s.gems) { s.gems.toSet() }
    val act = Dogam.sets.filter { Dogam.isActive(it, own) }
    if (act.isEmpty()) return
    PixelText(
        "📖 도감 ${act.size}개 활성 · 등원 +${Dogam.totalIn(own)} · 하원 +${Dogam.totalOut(own)} 골드",
        fontSize = 14.sp, fontWeight = FontWeight.Bold, color = OkGreen,
    )
}

/** 내 보관함의 "도감" 탭 */
@Composable
fun DogamPanel(s: Student, modifier: Modifier = Modifier) {
    val own = remember(s.gems) { s.gems.toSet() }
    val sets = Dogam.sets
    val sorted = remember(sets, own) {
        sets.sortedWith(compareBy<DogamSet>({ !Dogam.isActive(it, own) }, { -(it.gems.count { g -> g in own }.toFloat() / it.gems.size.coerceAtLeast(1)) }, { it.name }))
    }
    val actCount = sorted.count { Dogam.isActive(it, own) }
    Column(modifier) {
        if (sets.isEmpty()) {
            Text("아직 도감이 없어요", color = gameMuted(), fontSize = 16.sp)
            return@Column
        }
        Text("도감의 보석을 전부 모으면 활성화돼요. 활성화된 도감은 등원·하원 때마다 골드를 더 줘요!", color = gameMuted(), fontSize = 14.sp)
        Text(
            "활성 $actCount/${sets.size} · 지금 등원할 때 +${Dogam.totalIn(own)}골드 · 하원할 때 +${Dogam.totalOut(own)}골드 더 받아요",
            fontSize = 16.sp, fontWeight = FontWeight.Bold, color = if (actCount > 0) OkGreen else gameMuted(),
            modifier = Modifier.padding(vertical = 6.dp),
        )
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(sorted, key = { it.id }) { d ->
                val have = d.gems.count { it in own }
                val active = Dogam.isActive(d, own)
                val shape = RoundedCornerShape(12.dp)
                val line = if (active) OkGreen else Color(0x55808080)
                Row(
                    Modifier.fillMaxWidth().clip(shape)
                        .then(if (active) Modifier.background(Color(0x2222C55E)) else Modifier)
                        .border(if (active) 3.dp else 1.5.dp, line, shape).padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    d.gems.forEach { id -> GemImage(id, 52.dp, locked = id !in own); Spacer(Modifier.width(4.dp)) }
                    Spacer(Modifier.width(6.dp))
                    Column(Modifier.weight(1f)) {
                        Text(d.name, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        PixelText(
                            if (active) "✔ 활성화! 등원 +${d.goldIn} · 하원 +${d.goldOut} 골드"
                            else "$have/${d.gems.size} 모음 · 다 모으면 등원 +${d.goldIn} · 하원 +${d.goldOut} 골드",
                            fontSize = 14.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                            color = if (active) OkGreen else gameMuted(),
                        )
                    }
                }
            }
        }
    }
}
