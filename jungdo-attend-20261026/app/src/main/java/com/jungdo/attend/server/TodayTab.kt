package com.jungdo.attend.server

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.jungdo.attend.call.Phone
import com.jungdo.attend.data.ClassTimes
import com.jungdo.attend.data.EventType
import com.jungdo.attend.data.Holidays
import com.jungdo.attend.data.Repo
import com.jungdo.attend.data.Student
import com.jungdo.attend.data.rememberAbsencesRange
import com.jungdo.attend.data.rememberEventsOfDay
import com.jungdo.attend.data.rememberEventsRange
import com.jungdo.attend.data.rememberNow
import com.jungdo.attend.ui.AbsenceForm
import com.jungdo.attend.ui.BadRed
import com.jungdo.attend.ui.InBlue
import com.jungdo.attend.ui.Muted
import com.jungdo.attend.ui.OkGreen
import com.jungdo.attend.ui.SectionTitle
import com.jungdo.attend.ui.dayLabel
import java.util.Calendar

/** 오늘 출결: 미등원 아이 바로 전화 · 부모님 연락 → 사유 결석 · 등원 목록 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TodayTab(students: List<Student>) {
    val ctx = LocalContext.current
    val ct = remember { ClassTimes(ctx) }
    var day by remember { mutableStateOf(Repo.today()) }
    val now by rememberNow(30_000)
    val events by rememberEventsOfDay(day)
    val absences by rememberAbsencesRange(day, day)
    val history by rememberEventsRange(Repo.shiftDay(day, -35), Repo.shiftDay(day, -1))
    var form by remember { mutableStateOf<List<String>?>(null) }

    val active = students.filter { it.isActive }
    val attendedIds = events.map { it.studentId }.toSet()
    val absMap = absences.associateBy { it.studentId }
    // 평소 오는 부 (최근 5주 가장 많이 온 부)
    val usual = remember(history) {
        history.filter { it.type == EventType.IN }.groupBy { it.studentId }
            .mapValues { (_, l) -> l.groupingBy { ct.partOf(it.clientTime) }.eachCount().maxByOrNull { it.value }!!.key }
    }
    val missing = active.filter { it.id !in attendedIds && it.id !in absMap }
        .sortedWith(compareBy({ usual[it.id] ?: 99 }, { it.name }))
    val nowMin = Calendar.getInstance().apply { timeInMillis = now }.let { it.get(Calendar.HOUR_OF_DAY) * 60 + it.get(Calendar.MINUTE) }
    val isToday = day == Repo.today()
    val sibOf = active.filter { it.siblingKey.isNotBlank() }.groupBy { it.siblingKey }
    val ins = events.filter { it.type == EventType.IN }.sortedBy { it.clientTime }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { day = Repo.shiftDay(day, -1) }) { Text("◀") }
                    Text("  ${dayLabel(day)} (${Holidays.weekdayKo(day)})  ", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    OutlinedButton(onClick = { day = Repo.shiftDay(day, 1) }) { Text("▶") }
                }
                val holidayName = Holidays.name(day)
                if (holidayName != null) Text("🎌 $holidayName — 쉬는 날", color = BadRed)
                else if (Holidays.isWeekend(day)) Text("주말", color = BadRed)
                Text("등원 ${ins.map { it.studentId }.distinct().size} · 미등원 ${missing.size} · 사유 결석 ${absences.size} · 휴관 ${students.count { it.status == "LEAVE" }}", color = Muted)
                Spacer(Modifier.height(6.dp))
                Button(onClick = { form = emptyList() }, modifier = Modifier.fillMaxWidth().height(50.dp)) { Text("📝 부모님 연락 입력 (결석 처리 + 사유)", fontSize = 16.sp) }
            }
        }

        item { SectionTitle("미등원 ${missing.size}명 — 바로 전화") }
        if (missing.isEmpty()) item { Text("모두 등원했거나 사유가 있어요 👍", color = OkGreen) }
        items(missing, key = { "m" + it.id }) { s ->
            val part = usual[s.id]
            val late = isToday && part != null && nowMin > ct.startMinutes(part) + 15
            val sibs = sibOf[s.siblingKey].orEmpty().filter { it.id != s.id }
            val sibMissing = sibs.filter { it in missing }
            Card(colors = CardDefaults.cardColors(containerColor = if (late) Color(0xFFFEE2E2) else Color.White)) {
                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(s.name, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                        Text("  ${s.grade}${if (s.demo) " · 시범단" else ""}", color = Muted)
                        Spacer(Modifier.weight(1f))
                        Text(
                            when { part == null -> "평소 기록 없음"; late -> "${part}부 시작 지남!"; else -> "보통 ${part}부 (${ct.rangeText(part)})" },
                            color = if (late) BadRed else Muted, fontWeight = if (late) FontWeight.Bold else FontWeight.Normal, fontSize = 13.sp,
                        )
                    }
                    if (sibs.isNotEmpty()) Text("형제: " + sibs.joinToString(", ") { it.name + if (it in sibMissing) "(미등원)" else if (it.id in attendedIds) "(등원)" else "" }, color = InBlue, fontSize = 13.sp)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        s.phones.forEachIndexed { i, p ->
                            Button(onClick = { Phone.call(ctx, p, listOf(s.id) + sibMissing.map { it.id }) }) { Text("📞 ${s.phoneLabel(i)}") }
                        }
                        s.phones.firstOrNull()?.let { p -> OutlinedButton(onClick = { Phone.sms(ctx, p) }) { Text("💬 문자") } }
                        TextButton(onClick = { form = listOf(s.id) + sibMissing.map { it.id } }) { Text("결석 처리") }
                    }
                    if (s.phones.isEmpty()) Text("학부모 번호 없음 — [명단] 탭에서 입력", color = BadRed, fontSize = 13.sp)
                }
            }
        }

        if (absences.isNotEmpty()) {
            item { SectionTitle("사유 결석 ${absences.size}명") }
            items(absences, key = { "a" + it.id }) { a ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(a.name, fontWeight = FontWeight.Bold, modifier = Modifier.widthIn(min = 70.dp))
                    Text(a.reason, modifier = Modifier.weight(1f))
                    TextButton(onClick = { Repo.deleteAbsence(a.id) }) { Text("취소", color = BadRed) }
                }
            }
        }

        item { SectionTitle("등원 ${ins.size}건") }
        items(ins, key = { "i" + it.id }) { e ->
            Row(Modifier.fillMaxWidth()) {
                Text(Repo.timeText(e.clientTime), color = Muted, modifier = Modifier.width(80.dp))
                Text(e.name, fontWeight = FontWeight.Bold, modifier = Modifier.width(80.dp))
                Text("${ct.partOf(e.clientTime)}부", color = InBlue)
                Text("  ${e.grade}", color = Muted)
            }
        }
    }

    form?.let { pre ->
        Dialog(onDismissRequest = { form = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Card(Modifier.fillMaxWidth(0.95f)) {
                AbsenceForm(preselected = pre, header = "부모님 연락 → 결석 처리", initialDay = day, onDone = { form = null })
            }
        }
    }
}
