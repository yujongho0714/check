package com.jungdo.attend.server

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.jungdo.attend.call.Phone
import com.jungdo.attend.data.Inbox
import com.jungdo.attend.data.InboxKind
import com.jungdo.attend.data.InboxMsg
import com.jungdo.attend.data.Repo
import com.jungdo.attend.data.rememberInboxRange
import com.jungdo.attend.ui.AbsenceForm
import com.jungdo.attend.ui.BadRed
import com.jungdo.attend.ui.InBlue
import com.jungdo.attend.ui.Muted
import com.jungdo.attend.ui.OkGreen
import com.jungdo.attend.ui.OutOrange
import com.jungdo.attend.ui.Pill
import com.jungdo.attend.ui.dayLabel

private fun kindColor(k: String) = when (k) {
    InboxKind.ABSENT -> OutOrange
    InboxKind.UNKNOWN -> BadRed
    InboxKind.SCHEDULE -> InBlue
    else -> Muted
}

/** 학부모에게서 받은 문자 전부를 날짜별로. 키워드에 안 걸려서 놓친 문자도 여기서 확인 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InboxTab() {
    val ctx = LocalContext.current
    val today = Repo.today()
    var weeks by remember { mutableIntStateOf(2) }          // 2주씩 더 보기
    val from = Repo.shiftDay(today, -(7 * weeks - 1))
    val all by rememberInboxRange(from, today)
    var onlyOpen by remember { mutableStateOf(true) }
    var form by remember { mutableStateOf<InboxMsg?>(null) }

    val shown = all.filter { !onlyOpen || !it.handled }
    val openCount = all.count { !it.handled }
    val grouped = shown.groupBy { it.day }.toSortedMap(compareByDescending<String> { it })

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Column(Modifier.padding(top = 12.dp)) {
                Text("학부모 문자함", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("등록된 학부모 번호에서 온 문자가 모두 여기 남아요. 결석 키워드에 안 걸려서 놓친 문자도 여기서 확인하세요.", color = Muted, fontSize = 13.sp)
                Spacer(Modifier.padding(4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Pill("확인 안 한 문자 $openCount", onlyOpen, 15.sp) { onlyOpen = true }
                    Pill("전체 ${all.size}", !onlyOpen, 15.sp) { onlyOpen = false }
                }
            }
        }
        if (grouped.isEmpty()) item { Text(if (onlyOpen) "확인 안 한 문자가 없어요 ✔" else "받은 문자가 없어요.", color = Muted) }
        grouped.forEach { (day, list) ->
            item(key = "h$day") {
                Text("${dayLabel(day)} · ${list.size}건", fontWeight = FontWeight.Bold, color = Muted, modifier = Modifier.padding(top = 6.dp))
            }
            items(list, key = { it.id }) { m -> MsgCard(m, onForm = { form = m }, onCall = {
                Phone.call(ctx, m.from, m.studentIds)
            }) }
        }
        item {
            OutlinedButton(onClick = { weeks += 2 }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) { Text("이전 2주 더 보기") }
        }
    }

    form?.let { m ->
        Dialog(onDismissRequest = { form = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Card(Modifier.fillMaxWidth(0.95f)) {
                val unknown = m.studentIds.isEmpty()
                AbsenceForm(
                    preselected = m.studentIds,
                    header = if (unknown) "등록 안 된 번호 ${Repo.prettyPhone(m.from)} 문자" else "${m.names} ${m.label} 문자",
                    callNumber = m.from,
                    smsBody = m.body,
                    inboxId = m.id,
                    addNumber = if (unknown) m.from else null,
                    replyNumber = m.from,
                    replyLabel = m.label,
                    initialDay = m.day,
                    onDone = { form = null },
                )
            }
        }
    }
}

@Composable
private fun MsgCard(m: InboxMsg, onForm: () -> Unit, onCall: () -> Unit) {
    val unknown = m.studentIds.isEmpty()
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(Repo.timeText(m.at), color = Muted, modifier = Modifier.width(80.dp), fontSize = 14.sp)
                Text(if (unknown) Repo.prettyPhone(m.from) else "${m.names} ${m.label}", fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.weight(1f))
                Text(
                    InboxKind.label(m.kind), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(kindColor(m.kind)).padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
            Text(m.body, fontSize = 16.sp)
            m.reply?.let {
                if (m.replyError == null) Text("↩ 답장 보냄: $it", color = OkGreen, fontSize = 13.sp)
                else Text("↩ 답장 실패: ${m.replyError}", color = BadRed, fontSize = 13.sp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = onForm) { Text(if (unknown) "아이 고르기·사유" else "사유 입력") }
                OutlinedButton(onClick = onCall) { Text("📞") }
                Spacer(Modifier.weight(1f))
                if (m.handled) TextButton(onClick = { Inbox.markHandled(m.id, false) }) { Text("되돌리기") }
                else TextButton(onClick = { Inbox.markHandled(m.id) }) { Text("확인 완료") }
            }
        }
    }
}
