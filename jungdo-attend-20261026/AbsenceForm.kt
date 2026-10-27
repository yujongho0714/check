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
import android.Manifest
import com.jungdo.attend.call.Phone
import com.jungdo.attend.data.Inbox
import com.jungdo.attend.data.Messages
import com.jungdo.attend.data.Prefs
import com.jungdo.attend.server.SmsSender
import kotlinx.coroutines.launch
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
    smsBody: String? = null,
    inboxId: String? = null,        // 받은 문자 기록 (저장하면 '처리 완료'로 표시)
    addNumber: String? = null,      // 등록 안 된 번호 → 선택한 아이 학부모 번호로 추가
    replyNumber: String? = null,    // 저장할 때 이 번호로 답장 문자
    replyLabel: String = "",        // 어머님/아버님…
    initialDay: String = Repo.today(),
    onDone: () -> Unit,
) {
    val ctx = LocalContext.current
    val all by rememberStudents()
    val todayEvents by rememberEventsOfDay(Repo.today())
    val selected = remember { mutableStateListOf<String>().apply { addAll(preselected) } }
    var from by remember { mutableStateOf(initialDay) }
    var to by remember { mutableStateOf(initialDay) }
    var reason by remember { mutableStateOf(smsBody ?: "") }
    var query by remember { mutableStateOf("") }
    var saved by remember { mutableStateOf(false) }
    val prefs = remember { Prefs(ctx) }
    var replyOn by remember { mutableStateOf(prefs.replyOn) }
    var replyText by remember { mutableStateOf("") }
    var replyEdited by remember { mutableStateOf(false) }
    var tpl by remember { mutableStateOf<String?>(null) }
    var addPhoneOn by remember { mutableStateOf(true) }
    var addLabel by remember { mutableStateOf("보호자") }
    LaunchedEffect(Unit) { if (replyNumber != null) tpl = Messages.loadReplyTpl() }

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

    val selNames = active.filter { it.id in selected }.map { it.name }
    LaunchedEffect(tpl, selNames, addLabel) {
        val t = tpl
        if (t != null && !replyEdited) replyText = Messages.buildReply(t, selNames, if (addNumber != null) addLabel else replyLabel)
    }
    LaunchedEffect(saved) { if (saved) { kotlinx.coroutines.delay(900); onDone() } }

    Column(
        Modifier.fillMaxWidth().background(Color.White).verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(header, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Navy)
        if (callNumber != null) Text("📞 ${Repo.prettyPhone(callNumber)}" + if (missedCall) "  · 통화 연결 안 됨" else "", color = Muted)
        if (smsBody != null) Text("💬 받은 문자 내용이 아래 사유 칸에 들어갔어요. 고쳐서 저장하세요.", color = Muted, fontSize = 13.sp)
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
        // 긴 결석(독감·여행 등): 시작일 기준으로 끝 날짜를 한 번에 정하기
        Text("기간 빠르게 (시작일부터)", color = Muted, fontSize = 13.sp)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("3일" to 2, "1주" to 6, "2주" to 13, "3주" to 20, "한 달" to 29).forEach { (label, plus) ->
                val target = Repo.shiftDay(from, plus)
                Pill(label, to == target, 15.sp) { to = target }
            }
        }
        val days = Days.range(from, to).filter { !Holidays.isOff(it) }
        if (from != to) Text("${dayLabel(from)} ~ ${dayLabel(to)} · 수업일 ${days.size}일 결석 처리 (주말·공휴일 제외)", color = Muted, fontSize = 13.sp)

        Text("사유", fontWeight = FontWeight.Bold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            QUICK.forEach { q -> Pill(q, reason == q, 15.sp) { reason = q } }
        }
        OutlinedTextField(reason, { reason = it }, label = { Text("부모님께 들은 내용 (예: 감기로 병원 다녀옴)") }, modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp))

        if (addNumber != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = addPhoneOn, onCheckedChange = { addPhoneOn = it })
                Text("이 번호(${Repo.prettyPhone(addNumber)})를 선택한 아이의 학부모 번호로 추가", modifier = Modifier.weight(1f))
            }
            if (addPhoneOn) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("어머님", "아버님", "할머님", "보호자").forEach { l -> Pill(l, addLabel == l, 15.sp) { addLabel = l } }
            }
        }
        if (replyNumber != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = replyOn, onCheckedChange = { replyOn = it })
                Text("학부모님께 답장 문자 보내기 (${Repo.prettyPhone(replyNumber)})", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
            }
            if (replyOn) {
                OutlinedTextField(replyText, { replyText = it; replyEdited = true }, label = { Text("답장 내용 (여기서 고쳐서 보낼 수 있어요)") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 90.dp))
                Text("${replyText.length}자 · 문자 ${Messages.smsCount(replyText)}통", color = Muted, fontSize = 13.sp)
                if (selNames.isNotEmpty() && selNames.none { replyText.contains(Messages.givenName(it)) })
                    Text("⚠ 아이 이름이 문구에 없어요", color = BadRed, fontSize = 13.sp)
            }
        }

        Spacer(Modifier.height(4.dp))
        if (saved) Text("저장됐어요 ✔ 출석부에 '결'로 표시됩니다", color = OkGreen, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                enabled = selected.isNotEmpty() && days.isNotEmpty() && !saved,
                onClick = {
                    val kids = active.filter { it.id in selected }
                    Repo.saveAbsences(kids, days, reason.ifBlank { "부모님 연락" })
                    if (addNumber != null && addPhoneOn) kids.forEach { Repo.addPhone(it, addNumber, addLabel) }
                    if (inboxId != null) Inbox.markHandled(inboxId)
                    if (replyNumber != null) {
                        prefs.replyOn = replyOn
                        if (replyOn && replyText.isNotBlank()) {
                            // 창이 닫혀도 끝까지 보내도록 앱 전체 범위에서 실행
                            val app = ctx.applicationContext; val num = Repo.normalizePhone(replyNumber); val text = replyText.trim()
                            Inbox.appScope.launch {
                                val err = if (!Phone.has(app, Manifest.permission.SEND_SMS)) "문자 보내기 권한 없음" else SmsSender.send(app, num, text)
                                if (inboxId != null) Inbox.markReplied(inboxId, text, err)
                            }
                        }
                    }
                    saved = true
                },
                modifier = Modifier.weight(1f).height(52.dp),
            ) { Text("결석으로 저장", fontSize = 17.sp) }
            OutlinedButton(onClick = onDone, modifier = Modifier.weight(1f).height(52.dp)) { Text("결석 아님 · 닫기") }
        }
    }
}
