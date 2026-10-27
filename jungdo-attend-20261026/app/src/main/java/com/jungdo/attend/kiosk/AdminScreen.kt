package com.jungdo.attend.kiosk

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.jungdo.attend.data.AttendEvent
import com.jungdo.attend.data.EventType
import com.jungdo.attend.data.Prefs
import com.jungdo.attend.data.Repo
import com.jungdo.attend.data.Status
import com.jungdo.attend.data.Student
import com.jungdo.attend.ui.AssetPackSettings
import com.jungdo.attend.ui.game.GameFxSettings
import com.jungdo.attend.ui.game.LocalGameTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.CompositionLocalProvider
import com.jungdo.attend.ui.BadRed
import com.jungdo.attend.ui.LootSettings
import com.jungdo.attend.ui.PrizeSettings
import com.jungdo.attend.ui.MessageSettings
import com.jungdo.attend.ui.Muted
import com.jungdo.attend.ui.Navy
import com.jungdo.attend.ui.OkGreen
import com.jungdo.attend.ui.Pill
import com.jungdo.attend.ui.SectionTitle
import com.jungdo.attend.ui.StatusChip

/** 키오스크 관리자 화면: 원생 관리 / 일괄 등록 / 오늘 기록 / 문자·안내 / 설정 */
@Composable
fun AdminScreen(students: List<Student>, todayEvents: List<AttendEvent>, onClose: () -> Unit, onReset: () -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    val tabs = listOf("원생 관리", "일괄 등록", "오늘 기록", "문자·안내", "설정")
    BackHandler { onClose() }

    val gt = LocalGameTheme.current
    val themed = gt.skin != null
    val barColor = if (themed) gt.bar else Navy
    val bodyBg = if (themed) gt.card else Color.Unspecified   // 기본 테마는 예전 그대로(바탕 없음)

    Column(Modifier.fillMaxSize().then(if (themed) Modifier.background(bodyBg) else Modifier)) {
        Row(Modifier.fillMaxWidth().background(barColor).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("관리자", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onClose) { Text("출석 화면으로 ✕", color = Color.White, fontSize = 18.sp) }
        }
        ScrollableTabRow(
            selectedTabIndex = tab, edgePadding = 8.dp,
            containerColor = if (themed) gt.card else androidx.compose.material3.MaterialTheme.colorScheme.surface,
            contentColor = if (themed) gt.text else androidx.compose.material3.MaterialTheme.colorScheme.primary,
        ) {
            tabs.forEachIndexed { i, t -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t, fontSize = 16.sp) }) }
        }
        CompositionLocalProvider(LocalContentColor provides (if (themed) gt.text else LocalContentColor.current)) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (tab) {
                    0 -> StudentsTab(students)
                    1 -> BulkTab(students)
                    2 -> TodayTab(todayEvents)
                    3 -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                        MessageSettings()
                        Spacer(Modifier.height(24.dp))
                        LootSettings()
                        Spacer(Modifier.height(24.dp))
                        PrizeSettings()
                    }
                    else -> SettingsTab(onReset)
                }
            }
        }
    }
}

// ───────────────────────── 원생 관리 ─────────────────────────
@Composable
private fun StudentsTab(students: List<Student>) {
    var editing by remember { mutableStateOf<Student?>(null) }
    var query by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(query, { query = it }, label = { Text("이름 검색") }, singleLine = true, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(12.dp))
            Button(onClick = { editing = Student() }) { Text("+ 원생 추가") }
        }
        Text("재원 ${students.count { it.isActive }}명 · 이름을 누르면 수정 (명단 정리는 관장님 폰 [명단·형제]에서 현황 엑셀로)", color = Muted, modifier = Modifier.padding(vertical = 8.dp))
        LazyColumn {
            items(students.filter { query.isBlank() || it.name.contains(query.trim()) }, key = { it.id }) { s ->
                Row(Modifier.fillMaxWidth().clickable { editing = s }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(s.name, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(110.dp))
                    Text(s.grade + if (!s.isActive) " · ${com.jungdo.attend.data.StudentStatus.label(s.status)}" else "", color = Muted, modifier = Modifier.width(110.dp))
                    Text(s.phones.joinToString(", ") { Repo.prettyPhone(it) }.ifBlank { "연락처 없음" },
                        color = if (s.phones.isEmpty()) BadRed else Color.Unspecified)
                }
                HorizontalDivider()
            }
        }
    }
    editing?.let { com.jungdo.attend.ui.StudentEditDialog(it, students) { editing = null } }
}

// ───────────────────────── 일괄 등록 ─────────────────────────
fun parseBulk(text: String): List<Student> = text.lines().mapNotNull { line ->
    val c = line.split(',', '\t').map { it.trim() }
    if (c.isEmpty() || c[0].isBlank() || c[0] == "이름") null
    else Student(
        name = c[0],
        grade = c.getOrElse(1) { "" },
        phones = c.drop(2).map(Repo::normalizePhone).filter { it.length >= 9 },
    )
}

@Composable
private fun BulkTab(students: List<Student>) {
    var text by remember { mutableStateOf("") }
    var result by remember { mutableStateOf("") }
    val parsed = remember(text) { parseBulk(text) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle("여러 명 한꺼번에 등록")
        Text("한 줄에 한 명:  이름, 학년, 연락처1, 연락처2(선택)\n예)  홍길동, 2학년, 010-1234-5678\n엑셀에서 이름·학년·연락처 열을 복사해 붙여넣어도 됩니다.\n같은 이름+학년이 이미 있으면 연락처만 새로 덮어씁니다.\n(수련생 현황 엑셀·연락처 vcf로 한 번에 정리하려면 관장님 폰 [명단·형제] 탭)", color = Muted)
        OutlinedTextField(text, { text = it; result = "" }, modifier = Modifier.fillMaxWidth().height(280.dp), label = { Text("여기에 붙여넣기") })
        Text("인식된 원생: ${parsed.size}명 (연락처 없는 원생 ${parsed.count { it.phones.isEmpty() }}명)")
        Button(enabled = parsed.isNotEmpty(), onClick = {
            val n = Repo.bulkUpsert(parsed, students)
            result = "${n}명 저장했습니다."; text = ""
        }) { Text("${parsed.size}명 등록하기") }
        if (result.isNotBlank()) Text(result, color = OkGreen, fontWeight = FontWeight.Bold)
    }
}

// ───────────────────────── 오늘 기록 ─────────────────────────
@Composable
private fun TodayTab(events: List<AttendEvent>) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("오늘 등원 ${events.count { it.type == EventType.IN }} · 하원 ${events.count { it.type == EventType.OUT }} · 실패 ${events.count { it.status == Status.FAILED }}", fontWeight = FontWeight.Bold)
        Text("아이가 이름을 잘못 눌렀다면, 문자가 나가기 전(대기)에만 [취소]할 수 있어요.", color = Muted, fontSize = 14.sp)
        LazyColumn {
            items(events, key = { it.id }) { e ->
                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(Repo.timeText(e.clientTime), modifier = Modifier.width(90.dp))
                    Text(e.name, fontWeight = FontWeight.Bold, modifier = Modifier.width(90.dp))
                    Text(EventType.label(e.type), modifier = Modifier.width(50.dp))
                    StatusChip(e.status)
                    Spacer(Modifier.weight(1f))
                    if (e.status == Status.PENDING) OutlinedButton(onClick = { Repo.cancel(e.id) }) { Text("취소") }
                    if (e.status == Status.FAILED || e.status == Status.EXPIRED) OutlinedButton(onClick = { Repo.retry(e.id) }) { Text("재발송") }
                }
                e.error?.let { Text(it, color = BadRed, fontSize = 13.sp) }
                HorizontalDivider()
            }
        }
    }
}

// ───────────────────────── 설정 ─────────────────────────
@Composable
private fun SettingsTab(onReset: () -> Unit) {
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }
    var newPin by remember { mutableStateOf("") }
    var pinMsg by remember { mutableStateOf("") }
    var tts by remember { mutableStateOf(prefs.ttsOn) }
    var confirm by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle("관리자 비밀번호 변경")
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(newPin, { newPin = it.filter(Char::isDigit) }, label = { Text("새 비밀번호(숫자 4자리 이상)") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
            Spacer(Modifier.width(12.dp))
            Button(enabled = newPin.length >= 4, onClick = { prefs.adminPin = newPin; newPin = ""; pinMsg = "변경됐어요" }) { Text("변경") }
        }
        if (pinMsg.isNotBlank()) Text(pinMsg, color = OkGreen)

        HorizontalDivider()
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("버튼 누르면 음성 안내 (\"홍길동 등원\")", fontSize = 18.sp, modifier = Modifier.weight(1f))
            Switch(checked = tts, onCheckedChange = { tts = it; prefs.ttsOn = it })
        }

        HorizontalDivider()
        GameFxSettings()

        HorizontalDivider()
        AssetPackSettings()

        HorizontalDivider()
        SectionTitle("기기")
        Text("벽에 붙여 쓸 때는 안드로이드 '앱 고정' 기능을 켜면 아이들이 다른 앱으로 못 나갑니다. (설명서 참고)", color = Muted)
        OutlinedButton(onClick = { confirm = "mode" }) { Text("이 기기 모드 바꾸기 (출석 단말기 ↔ 문자 폰)") }
        OutlinedButton(onClick = { confirm = "logout" }) { Text("로그아웃", color = BadRed) }
    }

    confirm?.let { what ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(if (what == "mode") "모드를 바꿀까요?" else "로그아웃할까요?") },
            confirmButton = {
                Button(onClick = {
                    if (what == "logout") FirebaseAuth.getInstance().signOut()
                    confirm = null; onReset()
                }) { Text("예") }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("아니요") } },
        )
    }
}
