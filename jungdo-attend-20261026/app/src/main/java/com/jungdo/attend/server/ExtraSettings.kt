package com.jungdo.attend.server

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jungdo.attend.data.ClassTimes
import com.jungdo.attend.data.Holidays
import com.jungdo.attend.data.Prefs
import com.jungdo.attend.data.Repo
import com.jungdo.attend.data.rememberCustomHolidays
import com.jungdo.attend.ui.BadRed
import com.jungdo.attend.ui.Muted
import com.jungdo.attend.ui.OkGreen
import com.jungdo.attend.ui.SectionTitle
import com.jungdo.attend.ui.dayLabel

/** 부 시간표 · 문자 받는 번호 · 통화 후 창 · 휴일 추가 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExtraSettings() {
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }
    val ct = remember { ClassTimes(ctx) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // ── 부 시간표 ──
        SectionTitle("부 시간표 (등원 시각 → 몇 부)")
        Text("각 부가 '시작되는 경계 시각'이에요. 앞 부가 끝나는 시각으로 적으면 쉬는 시간에 온 아이도 다음 부로 잡혀요.\n예) 4부 수업이 16:30이어도 3부가 16:20에 끝나면 16:20", color = Muted, fontSize = 13.sp)
        val times = remember { ct.starts.let { s -> (0 until 7).map { mutableStateOf(s.getOrElse(it) { "" }) } } }
        var saved by remember { mutableStateOf(false) }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            times.forEachIndexed { i, st ->
                OutlinedTextField(st.value, { st.value = it; saved = false }, label = { Text("${i + 1}부") }, singleLine = true, modifier = Modifier.width(96.dp))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = {
                val ok = times.map { it.value.trim() }.filter { Regex("^\\d{1,2}:\\d{2}$").matches(it) }
                ct.starts = ok; saved = true
            }) { Text("시간표 저장") }
            TextButton(onClick = { ClassTimes.DEFAULT.split(",").forEachIndexed { i, t -> times[i].value = t } }) { Text("기본값") }
            if (saved) Text("저장됐어요 ✔", color = OkGreen)
        }

        HorizontalDivider()
        // ── 문자 받는 번호 ──
        var smsAll by remember { mutableStateOf(prefs.smsAllPhones) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("등하원 문자를 학부모 번호 전부에 보내기", fontWeight = FontWeight.Bold)
                Text(if (smsAll) "어머님·아버님 등 등록된 번호 모두에게" else "대표 번호(첫 번째, 보통 어머님) 1개에만", color = Muted, fontSize = 13.sp)
            }
            Switch(smsAll, { smsAll = it; prefs.smsAllPhones = it })
        }
        var popAny by remember { mutableStateOf(prefs.callPopupAny) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("일반 전화 후에도 사유 입력 창 띄우기", fontWeight = FontWeight.Bold)
                Text("앱 밖에서 걸거나 받은 전화도, 학부모 번호면 통화가 끝날 때 그 아이(형제) 창이 떠요", color = Muted, fontSize = 13.sp)
            }
            Switch(popAny, { popAny = it; prefs.callPopupAny = it })
        }

        HorizontalDivider()
        // ── 휴일 ──
        SectionTitle("휴일·휴관일")
        Text("공휴일·대체공휴일(2026~2027)은 자동이에요. 도장 휴관일(여름휴가 등)을 추가하거나, 공휴일인데 수업하는 날을 지정하세요. 출석부에 색칠되고 미등원·위험도 계산에서 빠져요.", color = Muted, fontSize = 13.sp)
        val custom by rememberCustomHolidays()
        var day by remember { mutableStateOf(Repo.today()) }
        var name by remember { mutableStateOf("도장 휴관") }
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { day = Repo.shiftDay(day, -1) }) { Text("◀") }
            Text("${dayLabel(day)} (${Holidays.weekdayKo(day)})", fontWeight = FontWeight.Bold)
            TextButton(onClick = { day = Repo.shiftDay(day, 1) }) { Text("▶") }
            Holidays.builtInName(day)?.let { Text(" 공휴일: $it", color = BadRed, fontSize = 13.sp) }
        }
        OutlinedTextField(name, { name = it }, label = { Text("이름 (예: 여름 휴가)") }, singleLine = true)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(onClick = { Repo.saveCustomHoliday(day, name.ifBlank { "도장 휴관" }) }) { Text("이 날 쉬는 날로 추가") }
            OutlinedButton(onClick = { Repo.saveCustomHoliday(day, Holidays.OPEN) }) { Text("이 날은 수업함") }
        }
        custom.toSortedMap().forEach { (d, n) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("$d  ${if (n == Holidays.OPEN) "수업함(공휴일 무시)" else n}", modifier = Modifier.weight(1f))
                TextButton(onClick = { Repo.saveCustomHoliday(d, null) }) { Text("삭제", color = BadRed) }
            }
        }
    }
}
