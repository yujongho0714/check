package com.jungdo.attend.server

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jungdo.attend.data.Payout
import com.jungdo.attend.data.Repo
import com.jungdo.attend.data.Wins
import com.jungdo.attend.data.rememberPayouts
import com.jungdo.attend.ui.BadRed
import com.jungdo.attend.ui.Muted
import com.jungdo.attend.ui.Pill
import com.jungdo.attend.ui.dayLabel

/** 지급내역 — 날짜별 / 원생별로 보기, 이름 검색, 실수로 누른 지급은 [되돌리기] */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PayoutsTab() {
    val today = Repo.today()
    var weeks by remember { mutableIntStateOf(4) }            // 4주씩 더 보기
    val all by rememberPayouts(Repo.shiftDay(today, -(7 * weeks - 1)), today)
    var byStudent by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var undo by remember { mutableStateOf<Payout?>(null) }

    val shown = all.filter { query.isBlank() || it.name.contains(query.trim()) || it.prizeName.contains(query.trim()) }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("지급내역 · ${shown.size}건", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("상품을 지급한 기록이에요. 잘못 누른 건 [되돌리기] 로 지급 대기로 돌릴 수 있어요.", color = Muted, fontSize = 13.sp)
                OutlinedTextField(query, { query = it }, label = { Text("원생 이름 또는 상품 검색") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Pill("날짜별", !byStudent, 15.sp) { byStudent = false }
                    Pill("원생별", byStudent, 15.sp) { byStudent = true }
                }
            }
        }
        if (shown.isEmpty()) item { Text("지급내역이 없어요.", color = Muted) }

        if (!byStudent) {
            shown.groupBy { it.payDay }.toSortedMap(compareByDescending<String> { it }).forEach { (day, list) ->
                item(key = "d$day") { Text("${dayLabel(day)} · ${list.size}건", fontWeight = FontWeight.Bold, color = Muted, modifier = Modifier.padding(top = 6.dp)) }
                items(list, key = { it.id }) { p -> PayoutCard(p, showName = true, showDay = false) { undo = p } }
            }
        } else {
            shown.groupBy { it.studentId.ifBlank { it.name } }.values.sortedBy { it.first().name }.forEach { list ->
                val first = list.first()
                item(key = "s${first.studentId}${first.name}") {
                    Text("${first.name} · ${list.size}건", fontWeight = FontWeight.Bold, color = Muted, modifier = Modifier.padding(top = 6.dp))
                }
                items(list, key = { it.id }) { p -> PayoutCard(p, showName = false, showDay = true) { undo = p } }
            }
        }
        item { OutlinedButton(onClick = { weeks += 4 }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) { Text("이전 4주 더 보기") } }
    }

    undo?.let { p ->
        AlertDialog(
            onDismissRequest = { undo = null },
            title = { Text("지급을 되돌릴까요?") },
            text = { Text("${p.name} · ${p.prizeName}\n지급내역에서 지워지고 다시 지급 대기 목록으로 돌아가요.") },
            confirmButton = {
                Button(colors = ButtonDefaults.buttonColors(containerColor = BadRed), onClick = { Wins.undo(p); undo = null }) { Text("되돌리기") }
            },
            dismissButton = { TextButton(onClick = { undo = null }) { Text("아니요") } },
        )
    }
}

@Composable
private fun PayoutCard(p: Payout, showName: Boolean, showDay: Boolean, onUndo: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                if (showName) Text(p.name + if (p.grade.isNotBlank()) " (${p.grade})" else "", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(p.prizeName, fontSize = 15.sp, fontWeight = if (showName) FontWeight.Normal else FontWeight.Bold)
                Text((if (showDay) "${dayLabel(p.payDay)} " else "") + "${Repo.timeText(p.paidAt)} 지급 · ${dayLabel(Repo.dayOf(p.winTime))} 당첨", color = Muted, fontSize = 12.sp)
            }
            TextButton(onClick = onUndo) { Text("되돌리기", color = BadRed) }
        }
    }
}
