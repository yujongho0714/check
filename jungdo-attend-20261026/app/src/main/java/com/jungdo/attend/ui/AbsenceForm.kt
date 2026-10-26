package com.jungdo.attend.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jungdo.attend.call.Phone
import com.jungdo.attend.data.Days
import com.jungdo.attend.data.EventType
import com.jungdo.attend.data.Holidays
import com.jungdo.attend.data.Repo
import com.jungdo.attend.data.Student
import com.jungdo.attend.data.StudentStatus
import com.jungdo.attend.data.rememberEventsOfDay
import com.jungdo.attend.data.rememberStudents

private val QUICK = listOf("아파서", "병원", "가족 일정", "여행", "학교 행사", "학원 일정", "방학 휴식", "개인 사정")

/**
 * 부모님께 받은 연락 → 결석 처리 + 사유 저장
 * 통화 후 팝업(ReasonActivity)과 [오늘 출결] 탭의 '부모님 연락 입력' 둘 다 이 화면을 씀
 *
 * @param preselected 미리 선택할 원생 id (형제는 자동으로 목록에 같이 나옴)
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AbsenceForm(
    preselected: List<String>,
    header: String,
    callNumber: String? = null,
    missedCall: Boolean = false,
    initialDay: String = Repo.today(),
    onDone: () -> Unit,
) {
    val ctx = LocalContext.current
    val all by rememberStudents()
    val todayEvents by rememberEventsOfDay(Repo.today())
    val selected = remember { mutableStateListOf<String>().apply { addAll(preselected) } }
    var from by remember { mutableStateOf(initialDay) }
    var to by remember { mutableStateOf(initialDay) }
    var reason by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var saved by remember { mutableStateOf(false) }

    val active = all.filter { it.status != StudentStatus.LEFT }
    val pre = active.filter { it.id in preselected }
    // 미리 선택된 아이 + 그 형제들
    val family = remember(all, preselected) {
        val keys = pre.map { it.siblingKey }.filter { it.isNotBlank() }.toSet()
        (pre + active.filter { it.siblingKey in keys }).distinctBy { it.id }
    }
    val searched = if (query.isBlank()) emptyList() else active.filter { it.name.contains(query.trim()) && it !in family }.take(8)
    val shown = (family + active.filter { it.id in selected && it !in family } + searched).distinctBy { it.id }
    val inToday = todayEvents.filter { it.type == EventType.IN }.map { it.studentId }.toSet()

    LaunchedEffect(saved) { if (saved) { kotlinx.coroutines.delay(900); onDone() } }

    Column(
        Modifier.fillMaxWidth().background(Color.White).verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(header, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Navy)
        if (callNumber != null) Text("📞 ${Repo.prettyPhone(callNumber)}" + if (missedCall) "  · 통화 연결 안 됨" else "", color = Muted)
        if (missedCall && callNumber != null) {
            OutlinedButton(onClick = { Phone.call(ctx, callNumber, selected.toList()); onDone() }) { Text("📞 다시 걸기") }
        }

        Text("결석할 아이", fontWeight = FontWeight.Bold)
        if (shown.isEmpty()) Text("아래에서 이름을 검색해 선택하세요", color = Muted)
        shown.forEach { s ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                    .background(if (s.id in selected) Color(0xFFE0E7FF) else Color(0xFFF3F4F6))
                    .clickable { if (s.id in selected) selected.remove(s.id) else selected.add(s.id) }
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = s.id in selected, onCheckedChange = { if (it) selected.add(s.id) else selected.remove(s.id) })
                Text(s.name, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                Text(s.grade, color = Muted)
                if (s in family && s.id !in preselected) Text("  형제", color = InBlue, fontSize = 13.sp)
                if (s.id in inToday) Text("  오늘 등원함", color = OkGreen, fontSize = 13.sp)
            }
        }
        OutlinedTextField(query, { query = it }, label = { Text("다른 아이 이름 검색해서 추가") }, singleLine = true, modifier = Modifier.fillMaxWidth())

        Text("날짜", fontWeight = FontWeight.Bold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val t = Repo.today(); val tm = Repo.shiftDay(t, 1)
            Pill("오늘", from == t && to == t, 15.sp) { from = t; to = t }
            Pill("내일", from == tm && to == tm, 15.sp) { from = tm; to = tm }
            Pill("오늘~내일", from == t && to == tm, 15.sp) { from = t; to = tm }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("시작 ", color = Muted)
            TextButton(onClick = { from = Repo.shiftDay(from, -1) }) { Text("◀") }
            Text(dayLabel(from), fontWeight = FontWeight.Bold)
            TextButton(onClick = { from = Repo.shiftDay(from, 1); if (to < from) to = from }) { Text("▶") }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("끝    ", color = Muted)
            TextButton(onClick = { to = Repo.shiftDay(to, -1); if (to < from) from = to }) { Text("◀") }
            Text(dayLabel(to), fontWeight = FontWeight.Bold)
            TextButton(onClick = { to = Repo.shiftDay(to, 1) }) { Text("▶") }
        }
        val days = Days.range(from, to).filter { !Holidays.isOff(it) }
        if (from != to) Text("수업일 ${days.size}일 결석 처리 (주말·공휴일 제외)", color = Muted, fontSize = 13.sp)

        Text("사유", fontWeight = FontWeight.Bold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            QUICK.forEach { q -> Pill(q, reason == q, 15.sp) { reason = q } }
        }
        OutlinedTextField(reason, { reason = it }, label = { Text("부모님께 들은 내용 (예: 감기로 병원 다녀옴)") }, modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp))

        Spacer(Modifier.height(4.dp))
        if (saved) Text("저장됐어요 ✔ 출석부에 '결'로 표시됩니다", color = OkGreen, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                enabled = selected.isNotEmpty() && days.isNotEmpty() && !saved,
                onClick = {
                    val kids = active.filter { it.id in selected }
                    Repo.saveAbsences(kids, days, reason.ifBlank { "부모님 연락" })
                    saved = true
                },
                modifier = Modifier.weight(1f).height(52.dp),
            ) { Text("결석으로 저장", fontSize = 17.sp) }
            OutlinedButton(onClick = onDone, modifier = Modifier.weight(1f).height(52.dp)) { Text("결석 아님 · 닫기") }
        }
    }
}
