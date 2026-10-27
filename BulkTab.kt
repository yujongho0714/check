package com.jungdo.attend.server

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.jungdo.attend.data.Bulk
import com.jungdo.attend.data.BulkJob
import com.jungdo.attend.data.BulkStatus
import com.jungdo.attend.data.Messages
import com.jungdo.attend.data.Prefs
import com.jungdo.attend.data.Repo
import com.jungdo.attend.data.Student
import com.jungdo.attend.data.StudentStatus
import com.jungdo.attend.data.gradeRank
import com.jungdo.attend.data.rememberBulkJobs
import com.jungdo.attend.data.rememberGroups
import com.jungdo.attend.ui.BadRed
import com.jungdo.attend.ui.Muted
import com.jungdo.attend.ui.Navy
import com.jungdo.attend.ui.OkGreen
import com.jungdo.attend.ui.OutOrange
import com.jungdo.attend.ui.Pill
import com.jungdo.attend.ui.SectionTitle
import com.jungdo.attend.ui.dayLabel

/** 단체 문자: 받는 사람(그룹·학년·직접 선택) 고르기 → 문자 쓰기 → 발송 기록 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BulkTab(students: List<Student>) {
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }
    val groups by rememberGroups()
    val jobs by rememberBulkJobs()
    val running by SmsService.running.collectAsState()
    val active = remember(students) { students.filter { it.status != StudentStatus.LEFT } }

    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var groupId by remember { mutableStateOf<String?>(null) }
    var pick by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var groupName by remember { mutableStateOf("") }
    var text by remember { mutableStateOf("") }
    var allPhones by remember { mutableStateOf(prefs.smsAllPhones) }
    var confirm by remember { mutableStateOf(false) }
    var delGroup by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }

    val kids = active.filter { it.id in selected }
    val (items, noPhone) = remember(text, selected, allPhones, active) { Bulk.buildItems(text.ifBlank { " " }, kids, allPhones) }
    val grades = remember(active) { active.map { it.grade }.filter { it.isNotBlank() }.distinct().sortedBy { gradeRank(it) } }
    val shownPick = active.filter { query.isBlank() || it.name.contains(query.trim()) || it.grade.contains(query.trim()) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle("① 받는 사람")
        Text("그룹을 누르면 그 그룹 사람들이 선택돼요. 학년 버튼은 그 학년 전체를 더하고, 아래 \"직접 고르기\"로 한 명씩 더하고 뺄 수 있어요.", color = Muted, fontSize = 13.sp)
        if (groups.isNotEmpty()) {
            Text("내 그룹", fontWeight = FontWeight.Bold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                groups.forEach { g ->
                    Pill("${g.name} ${g.studentIds.count { id -> active.any { it.id == id } }}명", groupId == g.id, 15.sp) {
                        groupId = g.id; groupName = g.name
                        selected = g.studentIds.filter { id -> active.any { it.id == id } }.toSet()
                    }
                }
            }
        }
        Text("빠르게 선택", fontWeight = FontWeight.Bold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Pill("전체 원생", false, 15.sp) { selected = active.map { it.id }.toSet(); groupId = null }
            grades.forEach { g -> Pill("+$g", false, 15.sp) { selected = selected + active.filter { it.grade == g }.map { it.id } } }
            Pill("선택 해제", false, 15.sp) { selected = emptySet(); groupId = null }
        }
        Text("선택됨: ${kids.size}명" + if (kids.isNotEmpty() && kids.size <= 8) " (${kids.joinToString(", ") { it.name }})" else "", fontWeight = FontWeight.Bold, color = Navy)

        OutlinedButton(onClick = { pick = !pick }) { Text(if (pick) "직접 고르기 접기 ▲" else "직접 고르기 ▼") }
        if (pick) {
            OutlinedTextField(query, { query = it }, label = { Text("이름·학년 검색") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { selected = selected + shownPick.map { it.id } }) { Text("보이는 사람 모두 선택") }
                OutlinedButton(onClick = { selected = selected - shownPick.map { it.id }.toSet() }) { Text("모두 해제") }
            }
            shownPick.forEach { s ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                        .background(if (s.id in selected) Color(0xFFE0E7FF) else Color(0xFFF3F4F6))
                        .clickable { selected = if (s.id in selected) selected - s.id else selected + s.id }
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = s.id in selected, onCheckedChange = { selected = if (it) selected + s.id else selected - s.id })
                    Text(s.name, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    Text(s.grade, color = Muted)
                }
            }
        }

        // 그룹 저장
        Text("이 선택을 그룹으로 저장 (국기원 심사, 줄넘기, 현장체험학습, 특강 …)", fontWeight = FontWeight.Bold)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(groupName, { groupName = it }, label = { Text("그룹 이름") }, singleLine = true, modifier = Modifier.weight(1f))
            Button(enabled = groupName.isNotBlank() && selected.isNotEmpty(), onClick = {
                val name = groupName.trim()
                val same = groups.firstOrNull { it.name == name }
                groupId = Bulk.saveGroup(same?.id, name, active.filter { it.id in selected }.map { it.id })
                msg = if (same != null) "'$name' 그룹을 ${selected.size}명으로 바꿔 저장했어요 ✔" else "'$name' 그룹을 만들었어요 ✔ (${selected.size}명)"
            }) { Text("저장") }
        }
        Text("같은 이름이 있으면 그 그룹을 덮어써요. 명단이 바뀌어도 퇴관한 아이는 자동으로 빠져요.", color = Muted, fontSize = 12.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            msg?.let { Text(it, color = OkGreen, modifier = Modifier.weight(1f)) } ?: Spacer(Modifier.weight(1f))
            if (groupId != null && groups.any { it.id == groupId }) TextButton(onClick = { delGroup = true }) { Text("이 그룹 삭제", color = BadRed) }
        }

        HorizontalDivider()
        SectionTitle("② 문자 쓰기")
        Text("{이름} 아이 이름(성 뺀 이름, 형제면 '윤우, 서연')  {성명} 전체 이름  {호칭} 어머님/아버님  {날짜}", color = Muted, fontSize = 13.sp)
        OutlinedTextField(text, { text = it }, label = { Text("문자 내용 (예: {호칭}, {이름} 국기원 심사는 12월 7일 오전 9시입니다)") },
            modifier = Modifier.fillMaxWidth().heightIn(min = 130.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("등록된 번호 모두에게", fontWeight = FontWeight.Bold)
                Text(if (allPhones) "어머님·아버님 등 번호마다 1통씩" else "대표 번호(첫 번째) 1개에만", color = Muted, fontSize = 13.sp)
            }
            Switch(allPhones, { allPhones = it })
        }
        if (text.isNotBlank() && items.isNotEmpty()) {
            val sample = items.first()
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFFFEF9C3)).padding(12.dp)) {
                Text("미리보기 (${sample.names}) · ${sample.text.length}자 · 문자 ${Messages.smsCount(sample.text)}통", color = Muted, fontSize = 13.sp)
                Text(sample.text, fontSize = 16.sp)
            }
            Text("받는 번호 ${items.size}곳 (아이 ${kids.size}명). 같은 번호(형제)는 1통만 가요.", fontWeight = FontWeight.Bold)
        }
        if (noPhone.isNotEmpty()) Text("⚠ 번호가 없어 못 보내는 아이: ${noPhone.joinToString(", ")}", color = OutOrange, fontSize = 13.sp)
        if (!running) Text("⚠ 문자 서버가 꺼져 있어요. [상태·기록]에서 서버를 켜면 그때부터 보내져요.", color = OutOrange, fontSize = 13.sp)
        Button(enabled = text.isNotBlank() && items.isNotEmpty(), onClick = { confirm = true }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Text("보내기 (${items.size}곳)", fontSize = 17.sp)
        }
        Text("출석 문자(등원·하원)가 먼저 나가고 단체 문자는 그 사이사이에 한 통씩 천천히 나가요. 폰이 30건 넘게 보낼 때 뜨는 허용 창이 나오면 \"항상 허용\"을 누르세요.", color = Muted, fontSize = 12.sp)

        HorizontalDivider()
        SectionTitle("③ 보낸 기록")
        if (jobs.isEmpty()) Text("아직 보낸 단체 문자가 없어요.", color = Muted)
        jobs.forEach { j -> JobCard(j) }
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("${items.size}곳에 보낼까요?") },
            text = { Text("아이 ${kids.size}명 · 한 곳당 문자 ${Messages.smsCount(items.firstOrNull()?.text ?: "")}통\n\n" + (items.firstOrNull()?.text ?: "")) },
            confirmButton = {
                Button(onClick = {
                    val gName = groups.firstOrNull { it.id == groupId }?.name ?: "직접 선택 ${kids.size}명"
                    Bulk.create("$gName · ${text.trim().take(15)}", items)
                    confirm = false; text = ""; msg = "보내기 목록에 올렸어요 ✔ 아래 [보낸 기록]에서 진행 상황을 볼 수 있어요"
                }) { Text("보내기") }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("취소") } },
        )
    }
    if (delGroup) {
        val g = groups.firstOrNull { it.id == groupId }
        AlertDialog(
            onDismissRequest = { delGroup = false },
            title = { Text("'${g?.name}' 그룹을 삭제할까요?") },
            text = { Text("그룹 묶음만 지워져요. 원생 명단은 그대로예요.") },
            confirmButton = {
                Button(colors = ButtonDefaults.buttonColors(containerColor = BadRed), onClick = {
                    g?.let { Bulk.deleteGroup(it.id) }; groupId = null; groupName = ""; delGroup = false
                }) { Text("삭제") }
            },
            dismissButton = { TextButton(onClick = { delGroup = false }) { Text("취소") } },
        )
    }
}

@Composable
private fun JobCard(j: BulkJob) {
    var open by remember { mutableStateOf(false) }
    val total = j.items.size
    val done = j.results.size
    val failed = j.failedIdx.size
    val state = when { j.status == BulkStatus.DONE -> "완료"; j.status == BulkStatus.SENDING -> "보내는 중"; else -> "대기" }
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(12.dp), modifier = Modifier.clickable { open = !open }) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(j.title, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))
                Text(state, color = if (j.status == BulkStatus.DONE) OkGreen else OutOrange, fontWeight = FontWeight.Bold)
            }
            Text("${dayLabel(Repo.dayOf(j.createdMs))} ${Repo.timeText(j.createdMs)} · 성공 ${j.sent} / ${total}" + if (failed > 0) " · 실패 $failed" else "", color = if (failed > 0) BadRed else Muted, fontSize = 13.sp)
            Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFFE5E7EB))) {
                Box(Modifier.fillMaxWidth(if (total == 0) 0f else done.toFloat() / total).height(8.dp).background(if (failed > 0) OutOrange else Navy))
            }
            if (failed > 0 && j.status == BulkStatus.DONE) {
                OutlinedButton(onClick = { Bulk.retryFailed(j) }) { Text("실패 ${failed}건만 다시 보내기") }
            }
            if (open) {
                HorizontalDivider()
                j.items.firstOrNull()?.let { Text(it.text, fontSize = 14.sp, color = Muted) }
                j.items.forEachIndexed { i, it ->
                    val r = j.results[i]
                    Row {
                        Text(when { r == null -> "…"; r == "OK" -> "✔"; else -> "✖" }, modifier = Modifier.width(24.dp), color = if (r != null && r != "OK") BadRed else Muted)
                        Text("${it.names} · ${Repo.prettyPhone(it.phone)}" + if (r != null && r != "OK") "\n   $r" else "", fontSize = 13.sp)
                    }
                }
                if (j.status == BulkStatus.DONE) TextButton(onClick = { Bulk.delete(j.id) }) { Text("이 기록 삭제", color = BadRed) }
            }
        }
    }
}
