package com.jungdo.attend.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
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
import com.jungdo.attend.data.AttendEvent
import com.jungdo.attend.data.ClassTimes
import com.jungdo.attend.data.DayNotice
import com.jungdo.attend.data.EventType
import com.jungdo.attend.data.MessageConfig
import com.jungdo.attend.data.Messages
import com.jungdo.attend.data.Repo
import com.jungdo.attend.data.Status
import kotlinx.coroutines.delay

/**
 * 문자 문구 + 날짜별 안내 설정 화면 (관장님 폰, 키오스크 관리자 화면 둘 다에서 사용)
 * 부별 안내: 등원 시각으로 판정한 1부~7부 기준
 */
@Composable
fun MessageSettings() {
    val classes = ClassTimes.PARTS
    // ───────── 1) 등원/하원 기본 문구 ─────────
    var tplIn by remember { mutableStateOf("") }
    var tplOut by remember { mutableStateOf("") }
    var cfgLoaded by remember { mutableStateOf(false) }
    var cfgSaved by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val c = Messages.loadConfig()
        tplIn = c.tplIn; tplOut = c.tplOut; cfgLoaded = true
    }
    LaunchedEffect(cfgSaved) { if (cfgSaved) { delay(2000); cfgSaved = false } }

    // ───────── 2) 날짜별 안내 ─────────
    var day by remember { mutableStateOf(Repo.today()) }
    var common by remember { mutableStateOf("") }
    val byClass = remember { mutableStateMapOf<String, String>() }
    var noticeSaved by remember { mutableStateOf(false) }
    LaunchedEffect(day) {
        val n = Messages.loadNotice(day)
        common = n.common
        byClass.clear(); byClass.putAll(n.byClass)
    }
    LaunchedEffect(noticeSaved) { if (noticeSaved) { delay(2000); noticeSaved = false } }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle("① 기본 문구")
        Text(
            "아래 칸 글자가 자동으로 바뀝니다:  {이름}  {시각}  {구분}  {부}  {학년}  {날짜}  {안내}\n" +
                "{부}는 아이가 누른 시각으로 자동 판정돼요 (예: 15:30 등원 → 3부).\n" +
                "{안내} 자리에 ②에서 적은 그날 안내가 들어가요. 안내가 없는 날은 그 줄이 빠집니다.",
            color = Muted, fontSize = 14.sp,
        )
        OutlinedTextField(
            tplIn, { tplIn = it }, label = { Text("등원 문자") }, enabled = cfgLoaded,
            modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
        )
        OutlinedTextField(
            tplOut, { tplOut = it }, label = { Text("하원 문자") }, enabled = cfgLoaded,
            modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(enabled = cfgLoaded, onClick = { Messages.saveConfig(MessageConfig(tplIn, tplOut)); cfgSaved = true }) { Text("문구 저장") }
            Spacer(Modifier.padding(6.dp))
            if (cfgSaved) Text("저장됐어요 ✔", color = OkGreen)
        }

        Spacer(Modifier.height(12.dp))
        SectionTitle("② 날짜별 안내 (매일 바뀌는 수업 안내)")
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { day = Repo.shiftDay(day, -1) }) { Text("◀") }
            Text(dayLabel(day), fontSize = 18.sp, fontWeight = FontWeight.Bold)
            OutlinedButton(onClick = { day = Repo.shiftDay(day, 1) }) { Text("▶") }
        }
        Text("내일·모레 안내도 미리 적어둘 수 있어요. 그 날짜에 자동으로 나갑니다.", color = Muted, fontSize = 14.sp)
        OutlinedTextField(
            common, { common = it }, label = { Text("전체 공통 안내 (예: 내일은 품새 심사 연습이 있습니다)") },
            modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp),
        )
        if (classes.isNotEmpty()) {
            Text("부별 안내 (등원 시각 기준 · 비워두면 공통 안내가 나가요)", fontWeight = FontWeight.Bold)
            classes.forEach { c ->
                OutlinedTextField(
                    byClass[c] ?: "", { byClass[c] = it }, label = { Text("$c 안내") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { Messages.saveNotice(day, DayNotice(common.trim(), byClass.mapValues { it.value.trim() })); noticeSaved = true }) {
                Text("${dayLabel(day)} 안내 저장")
            }
            Spacer(Modifier.padding(6.dp))
            if (noticeSaved) Text("저장됐어요 ✔", color = OkGreen)
        }

        // ───────── 3) 미리보기 ─────────
        Spacer(Modifier.height(12.dp))
        SectionTitle("③ 미리보기")
        val cfg = MessageConfig(tplIn.ifBlank { " " }, tplOut.ifBlank { " " })
        val notice = DayNotice(common, byClass.toMap())
        val sampleClass = "3부"
        listOf(EventType.IN, EventType.OUT).forEach { type ->
            val sample = AttendEvent(
                id = "", studentId = "", name = "홍길동", grade = "2학년", phones = emptyList(),
                type = type, status = Status.PENDING, clientTime = sampleTime(day, type), day = day, error = null, message = null,
            )
            val text = Messages.build(sample, cfg, notice, sampleClass)
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFFFEF9C3)).padding(12.dp),
            ) {
                Text("${EventType.label(type)} ($sampleClass 예시) · ${text.length}자 · 문자 ${Messages.smsCount(text)}통", color = Muted, fontSize = 13.sp)
                Text(text, fontSize = 16.sp)
            }
        }
        Text("한글 70자가 넘으면 문자가 여러 통으로 나뉘어 가요(폰 기종에 따라 장문 1통). 문자 무제한 요금제인지 확인하세요.", color = Muted, fontSize = 13.sp)
    }
}

@Composable
fun SectionTitle(t: String) {
    Text(t, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Navy)
}

fun dayLabel(day: String): String {
    val today = Repo.today()
    val tag = when (day) {
        today -> " (오늘)"
        Repo.shiftDay(today, 1) -> " (내일)"
        Repo.shiftDay(today, -1) -> " (어제)"
        else -> ""
    }
    return "${day.substring(5, 7).toInt()}월 ${day.substring(8, 10).toInt()}일$tag"
}

private fun sampleTime(day: String, type: String): Long {
    val f = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.KOREA)
    return f.parse("$day ${if (type == EventType.IN) "15:25" else "16:20"}")?.time ?: System.currentTimeMillis()
}
