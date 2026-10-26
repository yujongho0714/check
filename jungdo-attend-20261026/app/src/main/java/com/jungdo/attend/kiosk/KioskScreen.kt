package com.jungdo.attend.kiosk

import android.app.Activity
import android.content.Context
import android.speech.tts.TextToSpeech
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.jungdo.attend.data.AttendEvent
import com.jungdo.attend.data.EventType
import com.jungdo.attend.data.Prefs
import com.jungdo.attend.data.Repo
import com.jungdo.attend.data.Diag
import com.jungdo.attend.data.Student
import com.jungdo.attend.data.rememberDeviceStatus
import com.jungdo.attend.data.rememberEventsOfDay
import com.jungdo.attend.data.rememberNow
import com.jungdo.attend.data.rememberStudents
import com.jungdo.attend.ui.BadRed
import com.jungdo.attend.ui.InBlue
import com.jungdo.attend.ui.Muted
import com.jungdo.attend.ui.Navy
import com.jungdo.attend.ui.OkGreen
import com.jungdo.attend.ui.OutOrange
import com.jungdo.attend.ui.PageBg
import com.jungdo.attend.ui.Pill
import com.jungdo.attend.ui.isOnline
import kotlinx.coroutines.delay
import java.util.Locale

/** 이름 첫 글자의 초성 (쌍자음은 기본 자음으로) */
private val CHO = "ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ"
fun choOf(c: Char): Char {
    if (c !in '가'..'힣') return c
    return when (val ch = CHO[(c - '가') / 588]) {
        'ㄲ' -> 'ㄱ'; 'ㄸ' -> 'ㄷ'; 'ㅃ' -> 'ㅂ'; 'ㅆ' -> 'ㅅ'; 'ㅉ' -> 'ㅈ'
        else -> ch
    }
}
private val CHO_FILTERS = "ㄱㄴㄷㄹㅁㅂㅅㅇㅈㅊㅋㅌㅍㅎ".toList()

/**
 * 기기 A: 출석 단말기 화면
 *  - 이름 터치 → [등원]/[하원] → Firestore 에 신호 저장 → 기기 B 가 문자 발송
 *  - 앱 제목을 길게 누르면 관리자 비밀번호 → 원생 관리
 */
@Composable
fun KioskScreen(onReset: () -> Unit) {
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }
    val now by rememberNow(10_000)
    val day = Repo.dayOf(now)                    // 자정이 지나면 자동으로 다음 날로
    val allStudents by rememberStudents()
    // 재원생만 (휴관·퇴관은 출석체크 화면에 안 나옴)
    val students = remember(allStudents) { allStudents.filter { it.isActive } }
    val events by rememberEventsOfDay(day)
    val server by rememberDeviceStatus("server")
    val latest = remember(events) { events.sortedBy { it.clientTime }.associateBy { it.studentId } }

    var classFilter by remember { mutableStateOf("전체") }
    var choFilter by remember { mutableStateOf<Char?>(null) }
    var selected by remember { mutableStateOf<Student?>(null) }
    var dup by remember { mutableStateOf<Pair<Student, String>?>(null) }
    var done by remember { mutableStateOf<Pair<String, String>?>(null) }
    var showPin by remember { mutableStateOf(false) }
    var showAdmin by remember { mutableStateOf(false) }
    var showPad by remember { mutableStateOf(false) }
    var padChoices by remember { mutableStateOf<List<Student>?>(null) }
    val tts by rememberTts(ctx)

    KeepScreenOnFullScreen(ctx as? Activity)
    BackHandler(enabled = !showAdmin) { /* 아이들이 뒤로가기로 나가지 못하게 */ }

    // 1분마다 "키오스크 살아있음" 신호 → 관장님 폰에서 연결 상태 확인
    LaunchedEffect(Unit) {
        while (true) {
            runCatching { Repo.heartbeat("kiosk") }
            delay(60_000)
        }
    }
    LaunchedEffect(done) { if (done != null) { delay(2_200); done = null } }

    if (showAdmin) {
        AdminScreen(students = allStudents, todayEvents = events, onClose = { showAdmin = false }, onReset = onReset)
        return
    }

    var withSibs by remember { mutableStateOf(setOf<String>()) }

    fun attend(s: Student, type: String, force: Boolean = false) {
        val last = latest[s.id]
        // 10분 안에 같은 버튼을 또 누르면 한 번 더 확인 (문자 중복 방지)
        if (!force && last != null && last.type == type && System.currentTimeMillis() - last.clientTime < 10 * 60_000) {
            selected = null; dup = s to type; return
        }
        // 같이 온 형제도 함께 체크
        val sibs = students.filter { it.id in withSibs && it.id != s.id }
        (listOf(s) + sibs).forEach { Repo.addEvent(it, type) }
        val names = (listOf(s) + sibs).joinToString(", ") { it.name }
        selected = null; dup = null; withSibs = emptySet()
        done = names to type
        if (prefs.ttsOn) tts?.speak("$names ${EventType.label(type)}", TextToSpeech.QUEUE_FLUSH, null, "att")
    }

    val classes = remember(students) { listOf("전체") + students.map { it.grade }.filter { it.isNotBlank() }.distinct() }
    val shown = students.filter {
        (classFilter == "전체" || it.grade == classFilter) &&
            (choFilter == null || it.name.firstOrNull()?.let(::choOf) == choFilter)
    }

    Box(Modifier.fillMaxSize().background(PageBg)) {
        Column(Modifier.fillMaxSize()) {
            // ── 상단 바 ──
            Row(
                Modifier.fillMaxWidth().background(Navy).padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "용인대 정도 태권도 2관",
                    color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.pointerInput(Unit) { detectTapGestures(onLongPress = { showPin = true }) },
                )
                Spacer(Modifier.weight(1f))
                val ok = server?.running == true && isOnline(server?.lastSeen, now)
                Text(if (ok) "● 문자 연결" else "● 문자폰 꺼짐", color = if (ok) Color(0xFF86EFAC) else Color(0xFFFCA5A5), fontSize = 16.sp)
                Spacer(Modifier.width(20.dp))
                Text(Repo.timeText(now), color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
            // ── 부 선택 ──
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) { items(classes) { c -> Pill(c, classFilter == c) { classFilter = c } } }
            // ── 초성 선택 ──
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                item { Pill("전체", choFilter == null, 16.sp) { choFilter = null } }
                items(CHO_FILTERS) { ch -> Pill(ch.toString(), choFilter == ch, 16.sp) { choFilter = ch } }
            }
            // ── 이름 카드 ──
            if (shown.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        if (students.isEmpty()) "등록된 원생이 없습니다.\n위의 도장 이름을 길게 눌러 관리자 화면에서 등록하세요.\n\n" +
                            "[연결 진단] 계정: ${com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.email ?: "로그인 안 됨"}\n" +
                            "프로젝트: ${com.google.firebase.FirebaseApp.getInstance().options.projectId}\n" +
                            "오류: ${Diag.error.ifBlank { "없음(받은 원생 0명)" }}\n" +
                            "앱 빌드: ${com.jungdo.attend.BuildConfig.VERSION_CODE}"
                        else "해당하는 이름이 없어요",
                        fontSize = 20.sp, color = Muted, textAlign = TextAlign.Center,
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(150.dp),
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(shown, key = { it.id }) { s -> StudentCard(s, latest[s.id]) { selected = s } }
                }
            }
        }

        // ── 출결번호로 출석 버튼 ──
        Button(
            onClick = { showPad = true },
            colors = ButtonDefaults.buttonColors(containerColor = Navy),
            shape = RoundedCornerShape(40.dp),
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp).height(72.dp),
        ) { Text("🔢 출결번호로 출석", fontSize = 24.sp, fontWeight = FontWeight.Bold) }

        // ── 완료 화면 (2초) ──
        done?.let { (name, type) ->
            Box(Modifier.fillMaxSize().background(Color(0xDD000000)), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("✅", fontSize = 80.sp)
                    Text(name, fontSize = 56.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text(
                        "${EventType.label(type)} 완료!", fontSize = 40.sp, fontWeight = FontWeight.Bold,
                        color = if (type == EventType.IN) Color(0xFF93C5FD) else Color(0xFFFDBA74),
                    )
                }
            }
        }
    }

    // ── 등원/하원 선택 팝업 ──
    selected?.let { s ->
        LaunchedEffect(s, withSibs) { delay(15_000); selected = null; withSibs = emptySet() } // 15초 동안 안 누르면 자동 닫힘
        Dialog(onDismissRequest = { selected = null; withSibs = emptySet() }) {
            Card(shape = RoundedCornerShape(28.dp)) {
                Column(Modifier.widthIn(min = 340.dp).padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(s.name, fontSize = 44.sp, fontWeight = FontWeight.Bold)
                    if (s.grade.isNotBlank()) Text(s.grade, fontSize = 18.sp, color = Muted)
                    latest[s.id]?.let { Text("오늘 마지막: ${EventType.label(it.type)} ${Repo.timeText(it.clientTime)}", color = Muted) }
                    // 형제가 있으면 "같이 왔어요" 선택
                    val sibs = students.filter {
                        it.id != s.id && ((s.siblingKey.isNotBlank() && it.siblingKey == s.siblingKey) || (s.attendNo.isNotBlank() && it.attendNo == s.attendNo))
                    }
                    if (sibs.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Text("형제도 같이 왔나요? (누르면 선택)", color = Muted)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            sibs.forEach { sb ->
                                Pill((if (sb.id in withSibs) "✔ " else "") + sb.name, sb.id in withSibs, 22.sp) {
                                    withSibs = if (sb.id in withSibs) withSibs - sb.id else withSibs + sb.id
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        BigButton("등원", InBlue, Modifier.weight(1f)) { attend(s, EventType.IN) }
                        BigButton("하원", OutOrange, Modifier.weight(1f)) { attend(s, EventType.OUT) }
                    }
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { selected = null; withSibs = emptySet() }) { Text("취소", fontSize = 20.sp) }
                }
            }
        }
    }

    // ── 출결번호 입력 ──
    if (showPad) NumberPad(
        onDismiss = { showPad = false },
        onSubmit = { no ->
            val found = students.filter { it.attendNo == no }
            when {
                found.isEmpty() -> false
                found.size == 1 -> { showPad = false; withSibs = emptySet(); selected = found.first(); true }
                else -> { showPad = false; padChoices = found; true }
            }
        },
    )
    // 같은 번호를 쓰는 형제 → 누구인지 고르기
    padChoices?.let { list ->
        LaunchedEffect(list) { delay(15_000); padChoices = null }
        Dialog(onDismissRequest = { padChoices = null }) {
            Card(shape = RoundedCornerShape(28.dp)) {
                Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("누구예요?", fontSize = 32.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(16.dp))
                    list.forEach { st ->
                        Button(
                            onClick = { padChoices = null; withSibs = emptySet(); selected = st },
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier.widthIn(min = 260.dp).height(80.dp).padding(vertical = 4.dp),
                        ) { Text(st.name, fontSize = 32.sp, fontWeight = FontWeight.Bold) }
                    }
                    TextButton(onClick = { padChoices = null }) { Text("취소", fontSize = 20.sp) }
                }
            }
        }
    }

    // ── 중복 확인 ──
    dup?.let { (s, type) ->
        AlertDialog(
            onDismissRequest = { dup = null },
            title = { Text("${s.name} 이미 ${EventType.label(type)}했어요", fontSize = 24.sp) },
            text = { Text("조금 전에 ${EventType.label(type)} 버튼을 눌렀어요.\n한 번 더 문자를 보낼까요?", fontSize = 18.sp) },
            confirmButton = { Button(onClick = { attend(s, type, force = true) }) { Text("다시 보내기", fontSize = 18.sp) } },
            dismissButton = { TextButton(onClick = { dup = null }) { Text("아니요", fontSize = 18.sp) } },
        )
    }

    // ── 관리자 비밀번호 ──
    if (showPin) {
        var pin by remember { mutableStateOf("") }
        var wrong by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { showPin = false },
            title = { Text("관리자 비밀번호") },
            text = {
                Column {
                    OutlinedTextField(
                        pin, { pin = it; wrong = false }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    )
                    if (wrong) Text("비밀번호가 틀렸어요", color = BadRed)
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (pin == prefs.adminPin) { showPin = false; showAdmin = true } else wrong = true
                }) { Text("확인") }
            },
            dismissButton = { TextButton(onClick = { showPin = false }) { Text("취소") } },
        )
    }
}

@Composable
private fun StudentCard(s: Student, last: AttendEvent?, onClick: () -> Unit) {
    val (bg, line) = when (last?.type) {
        EventType.IN -> Color(0xFFDCFCE7) to "등원 ${Repo.timeText(last.clientTime)}"
        EventType.OUT -> Color(0xFFE5E7EB) to "하원 ${Repo.timeText(last.clientTime)}"
        else -> Color.White to " "
    }
    Column(
        Modifier.clip(RoundedCornerShape(16.dp)).background(bg).clickable(onClick = onClick)
            .padding(vertical = 18.dp, horizontal = 8.dp).fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(s.name, fontSize = 28.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(line, fontSize = 14.sp, color = if (last?.type == EventType.IN) OkGreen else Muted)
    }
}

@Composable
private fun BigButton(text: String, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = color),
        shape = RoundedCornerShape(20.dp),
        modifier = modifier.height(110.dp),
    ) { Text(text, fontSize = 34.sp, fontWeight = FontWeight.Bold) }
}

/** "홍길동 등원" 음성 안내 */
@Composable
private fun rememberTts(ctx: Context): State<TextToSpeech?> {
    val state = remember { mutableStateOf<TextToSpeech?>(null) }
    DisposableEffect(Unit) {
        var engine: TextToSpeech? = null
        engine = TextToSpeech(ctx) { status ->
            if (status == TextToSpeech.SUCCESS) {
                engine?.language = Locale.KOREAN
                state.value = engine
            }
        }
        onDispose { engine?.shutdown() }
    }
    return state
}

/** 화면 꺼짐 방지 + 상단/하단 바 숨기기 */
@Composable
private fun KeepScreenOnFullScreen(activity: Activity?) {
    DisposableEffect(activity) {
        val w = activity?.window
        w?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val c = w?.let { WindowCompat.getInsetsController(it, it.decorView) }
        c?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        c?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            w?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            c?.show(WindowInsetsCompat.Type.systemBars())
        }
    }
}


/** 출결번호 4자리 입력 키패드 */
@Composable
private fun NumberPad(onDismiss: () -> Unit, onSubmit: (String) -> Boolean) {
    var input by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    LaunchedEffect(input) { delay(20_000); onDismiss() } // 20초 동안 입력 없으면 닫힘
    fun press(k: String) {
        wrong = false
        when (k) {
            "지움" -> input = input.dropLast(1)
            "확인" -> if (input.isNotEmpty() && !onSubmit(input)) { wrong = true; input = "" }
            else -> if (input.length < 6) {
                input += k
                if (input.length == 4 && onSubmit(input).not()) { wrong = true; input = "" }
            }
        }
    }
    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(28.dp)) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("출결번호 입력", fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Text(if (wrong) "없는 번호예요. 다시 눌러 주세요" else "학부모님 번호 끝 4자리", color = if (wrong) BadRed else Muted, fontSize = 16.sp)
                Spacer(Modifier.height(8.dp))
                Text(input.padEnd(4, '_').chunked(1).joinToString(" "), fontSize = 44.sp, fontWeight = FontWeight.Bold, color = Navy)
                Spacer(Modifier.height(12.dp))
                listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("지움", "0", "확인")).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(vertical = 5.dp)) {
                        row.forEach { k ->
                            Button(
                                onClick = { press(k) },
                                shape = RoundedCornerShape(18.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = if (k.length > 1) Muted else InBlue),
                                modifier = Modifier.width(96.dp).height(80.dp),
                            ) { Text(k, fontSize = if (k.length > 1) 20.sp else 34.sp, fontWeight = FontWeight.Bold) }
                        }
                    }
                }
                TextButton(onClick = onDismiss) { Text("닫기", fontSize = 20.sp) }
            }
        }
    }
}
