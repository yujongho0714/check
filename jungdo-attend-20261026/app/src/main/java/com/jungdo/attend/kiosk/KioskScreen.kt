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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.window.DialogProperties
import com.jungdo.attend.ui.CharSizeBar
import com.jungdo.attend.ui.rememberCharScale
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.jungdo.attend.data.AttendEvent
import com.jungdo.attend.data.AwardResult
import com.jungdo.attend.data.Rank
import com.jungdo.attend.data.UiSize
import com.jungdo.attend.data.rememberCustomHolidays
import com.jungdo.attend.ui.AwardRow
import com.jungdo.attend.ui.CollectionDialog
import com.jungdo.attend.ui.DressRow
import com.jungdo.attend.ui.GoldRow
import com.jungdo.attend.ui.LootRow
import com.jungdo.attend.ui.PrizeRow
import com.jungdo.attend.ui.MyCharacter
import com.jungdo.attend.ui.MyCharacterStage
import com.jungdo.attend.ui.NamePlate
import com.jungdo.attend.ui.RankHeader
import com.jungdo.attend.ui.RankImage
import com.jungdo.attend.data.DojangInfo
import com.jungdo.attend.data.EventType
import com.jungdo.attend.data.Prefs
import com.jungdo.attend.data.Repo
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
import com.jungdo.attend.ui.game.GameButton
import com.jungdo.attend.ui.game.GamePanel
import com.jungdo.attend.ui.game.LocalGameTheme
import com.jungdo.attend.ui.game.gameNumFont
import com.jungdo.attend.ui.game.GamePill
import com.jungdo.attend.ui.game.GameTextButton
import com.jungdo.attend.ui.game.gameMuted
import com.jungdo.attend.ui.game.gamePageBackground
import androidx.compose.ui.platform.LocalConfiguration
import kotlinx.coroutines.delay
import java.util.Locale
import com.jungdo.attend.ui.game.PixelText
import com.jungdo.attend.ui.game.PixelIcon

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
    val holidaysNow by rememberCustomHolidays()   // 수업 없는 날 계산(연속 출석)에 쓰는 휴관일 목록
    // 재원생만 (휴관·퇴관은 출석체크 화면에 안 나옴)
    val students = remember(allStudents) { allStudents.filter { it.isActive } }
    val events by rememberEventsOfDay(day)
    val server by rememberDeviceStatus("server")
    val latest = remember(events) { events.sortedBy { it.clientTime }.associateBy { it.studentId } }

    var selected by remember { mutableStateOf<Student?>(null) }
    var dup by remember { mutableStateOf<Pair<Student, String>?>(null) }
    var done by remember { mutableStateOf<Pair<String, String>?>(null) }
    var doneAwards by remember { mutableStateOf<List<AwardResult>>(emptyList()) }
    var doneStudents by remember { mutableStateOf<List<Student>>(emptyList()) }   // 완료 화면에 캐릭터를 보여 줄 원생들
    var collectionId by remember { mutableStateOf<String?>(null) }   // 보관함을 열어 본 원생
    var collectionTab by remember { mutableStateOf(0) }              // 보관함을 열 때 처음 보여줄 탭 (0 보석 · 1 플레이트 · 2 뽑기 · 3 드레스룸)
    var touchTick by remember { mutableStateOf(0) }                  // 캐릭터를 조작할 때마다 올라감 (자동 닫힘 시간 다시 시작)
    var showPin by remember { mutableStateOf(false) }
    var showAdmin by remember { mutableStateOf(false) }
    var padChoices by remember { mutableStateOf<List<Student>?>(null) }
    var typedNo by remember { mutableStateOf("") }   // 마지막으로 입력한 번호 (어느 학부모 번호로 눌렀는지 알아내려고)
    val tts by rememberTts(ctx)
    val theme = LocalGameTheme.current

    KeepScreenOnFullScreen(ctx as? Activity)
    BackHandler(enabled = !showAdmin) { /* 아이들이 뒤로가기로 나가지 못하게 */ }

    // 1분마다 "키오스크 살아있음" 신호 → 관장님 폰에서 연결 상태 확인
    LaunchedEffect(Unit) {
        while (true) {
            runCatching { Repo.heartbeat("kiosk") }
            delay(60_000)
        }
    }
    LaunchedEffect(done) { if (done != null) { delay(if (doneAwards.any { it.prize != null }) 10_000 else if (doneAwards.any { it.gemId != null || it.plateId != null || it.dressKey != null }) 6_000 else 3_500); done = null } }

    if (showAdmin) {
        AdminScreen(students = allStudents, todayEvents = events, onClose = { showAdmin = false }, onReset = onReset)
        return
    }

    var withSibs by remember { mutableStateOf(setOf<String>()) }

    fun attend(s: Student, type: String, force: Boolean = false) {
        val last = latest[s.id]
        // 10분 안에 같은 버튼을 또 누르면 한 번 더 확인 (문자 중복 방지)
        if (!force && !Repo.retryEligible(s, type) && last != null && last.type == type && System.currentTimeMillis() - last.clientTime < 10 * 60_000) {
            selected = null; dup = s to type; return
        }
        // 같이 온 형제도 함께 체크
        val sibs = students.filter { it.id in withSibs && it.id != s.id }
        val group = listOf(s) + sibs
        // (임시) 보상 다시 받기인 친구는 문자가 또 나가지 않게 기록을 새로 만들지 않음
        group.forEach { if (!Repo.retryEligible(it, type)) Repo.addEvent(it, type, typedNo) }
        // 경험치: 등원 +10, 하원 +10 (하루 한 번씩) + 연속 출석 보너스
        val awards = group.map { runCatching { Repo.awardExp(it, type) }.getOrNull() }.filterNotNull()
        val names = group.joinToString(", ") { it.name }
        selected = null; dup = null; withSibs = emptySet()
        doneAwards = awards
        doneStudents = group
        done = names to type
        if (prefs.ttsOn) tts?.speak("$names ${EventType.label(type)}", TextToSpeech.QUEUE_FLUSH, null, "att")
    }

    // 출결번호 확인: 한 명이면 바로 등원/하원 창, 형제(같은 번호)면 "누구예요?"에서 자기 이름 고르기
    fun submitNo(no: String): Boolean {
        val found = students.filter { it.matchesNo(no) }
        typedNo = no.trim()
        if (found.isNotEmpty()) {
            // 확률·상품 설정을 서버에서 바로 다시 받아 둠 (등원/하원 누르기 전에 최신 값으로)
            Repo.refreshLootNow()
        }
        return when {
            found.isEmpty() -> false
            found.size == 1 -> { withSibs = emptySet(); selected = found.first(); true }
            else -> { padChoices = found; true }
        }
    }

    Box(Modifier.fillMaxSize().gamePageBackground()) {
        Column(Modifier.fillMaxSize()) {
            // ── 상단 바 ──
            Row(
                Modifier.fillMaxWidth().background(theme.bar).padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    DojangInfo.NAME,
                    color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.pointerInput(Unit) { detectTapGestures(onLongPress = { showPin = true }) },
                )
                Spacer(Modifier.weight(1f))
                val ok = server?.running == true && isOnline(server?.lastSeen, now)
                PixelText(if (ok) "{dot_on} 문자 연결" else "{dot_off} 문자폰 꺼짐", color = if (ok) Color(0xFF86EFAC) else Color(0xFFFCA5A5), fontSize = 16.sp)
                Spacer(Modifier.width(20.dp))
                Text(Repo.timeText(now), color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold, fontFamily = gameNumFont())
            }
            // ── 출결번호 입력 (이름 목록은 보여주지 않음 → 다른 아이 이름을 눌러 장난치는 것 방지) ──
            PadPanel(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                emptyNotice = students.isEmpty(),
                onSubmit = { submitNo(it) },
            )
        }

        // ── 완료 화면 (3.5초) ──
        done?.let { (name, type) ->
            Box(Modifier.fillMaxSize().background(if (theme.skin != null) Color(0xDD2B4A1E) else Color(0xDD000000)).clickable { done = null }, contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(theme.doneEmoji, fontSize = 48.sp)
                    Text(
                        "${EventType.label(type)} 완료!", fontSize = 34.sp, fontWeight = FontWeight.Bold,
                        color = if (theme.skin != null) (if (type == EventType.IN) Color(0xFFD9F99D) else Color(0xFFFFD9A0))
                                else if (type == EventType.IN) Color(0xFF93C5FD) else Color(0xFFFDBA74),
                    )
                    if (doneAwards.isEmpty()) Text(name, fontSize = 48.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    // 계급 · 이름 (계급 그림은 항상 이름 왼쪽) · 경험치
                    doneAwards.forEach { a ->
                        // 내 캐릭터 (이름이 같은 원생을 찾아서 지금 입은 옷 그대로)
                        doneStudents.firstOrNull { it.name == a.name }?.let { st ->
                            Box(Modifier.padding(top = 12.dp)) { MyCharacter(allStudents.firstOrNull { it.id == st.id } ?: st, 110.dp) }
                        }
                        Box(Modifier.padding(top = 8.dp)) { AwardRow(a) }
                        Box(Modifier.padding(top = 10.dp)) { GoldRow(a) }
                        Box(Modifier.padding(top = 10.dp)) { LootRow(a) }
                        Box(Modifier.padding(top = 10.dp)) { DressRow(a) }
                        Box(Modifier.padding(top = 10.dp)) { PrizeRow(a) }
                    }
                }
            }
        }
    }

    // ── 등원/하원 선택 팝업 ──
    selected?.let { s0 ->
        val s = allStudents.firstOrNull { it.id == s0.id } ?: s0   // 보관함에서 바꾼 네임플레이트가 바로 보이게
        LaunchedEffect(s0, withSibs, collectionId, touchTick) { if (collectionId == null) { delay(15_000); selected = null; withSibs = emptySet() } } // 15초 동안 안 누르면 자동 닫힘
        var charScale by rememberCharScale()
        val cfgDlg = LocalConfiguration.current
        // 캐릭터 무대 크기: 기본은 이 기기 화면에 딱 맞게 자동 계산 (팝업의 나머지 부분 약 520dp 를 뺀 높이), 아래 막대는 그 기본값의 배율
        val scrW = cfgDlg.screenWidthDp.toFloat()
        val scrH = cfgDlg.screenHeightDp.toFloat()
        val fitH = minOf(maxOf(scrH - 520f, 150f), scrH * 0.36f)
        val stageH = minOf(fitH * UiSize.box * charScale, scrH * 0.5f).dp
        val stageW = maxOf(340f, minOf(fitH * UiSize.box * charScale * 1.5f, scrW - 80f)).dp
        val stageCharH = minOf((stageH - 40.dp) * UiSize.char, stageH)   // 관리자가 정한 캐릭터 크기 (박스 안에서)
        Dialog(onDismissRequest = { selected = null; withSibs = emptySet() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            GamePanel(Modifier.widthIn(min = 340.dp)) {
                run {
                    // 내 캐릭터 (걷기·공격 조작 가능, 조작하는 동안은 창이 안 닫힘)
                    MyCharacterStage(s, stageWidth = stageW, stageHeight = stageH, charHeight = stageCharH, onTouch = { touchTick++ })
                    CharSizeBar(charScale, { charScale = it; touchTick++ }, Modifier.width(stageW))
                    Spacer(Modifier.height(12.dp))
                    // 네임플레이트(없으면 이름) → Lv + 계급 그림 (완료 화면과 같은 배치)
                    if (s.plate.isNotBlank()) NamePlate(s.plate, s.name, 300.dp, 32.sp)
                    else Text(s.name, fontSize = 44.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    RankHeader(s.level)
                    if (s.grade.isNotBlank()) Text(s.grade, fontSize = 18.sp, color = gameMuted())
                    latest[s.id]?.let { Text("오늘 마지막: ${EventType.label(it.type)} ${Repo.timeText(it.clientTime)}", color = gameMuted()) }
                    // 형제가 있으면 "같이 왔어요" 선택
                    val sibs = students.filter {
                        it.id != s.id && ((s.siblingKey.isNotBlank() && it.siblingKey == s.siblingKey) || (s.attendNo.isNotBlank() && it.attendNo == s.attendNo) || s.sharesPhone(it))
                    }
                    if (sibs.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Text("형제도 같이 왔나요? (누르면 선택)", color = gameMuted())
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            sibs.forEach { sb ->
                                GamePill((if (sb.id in withSibs) "✔ " else "") + sb.name, sb.id in withSibs, 22.sp) {
                                    withSibs = if (sb.id in withSibs) withSibs - sb.id else withSibs + sb.id
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                    // 오늘 마지막 기록으로 맞는 버튼을 또렷하게 (흐린 버튼도 누를 수 있음) → 등원/하원 잘못 누름 방지
                    val lastType = latest[s.id]?.type
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        // 등원·하원은 효과음 없이 이름 음성(TTS)만 나와요 (sound = null)
                        BigButton("등원", if (lastType == EventType.IN) theme.primary.copy(alpha = 0.4f) else theme.primary, Modifier.weight(1f)) { attend(s, EventType.IN) }
                        BigButton("하원", if (lastType == EventType.IN) theme.secondary else theme.secondary.copy(alpha = 0.4f), Modifier.weight(1f)) { attend(s, EventType.OUT) }
                    }
                    Spacer(Modifier.height(8.dp))
                    // 내 보석 · 플레이트 · 가방(드레스룸) · 뽑기 → 보관함이 그 탭으로 열려요
                    Row(Modifier.width(maxOf(stageW, 340.dp)), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(Triple("내 보석", null, 0), Triple("플레이트", null, 1), Triple("가방", "chest", 3), Triple("뽑기", "gift", 2))
                            .forEach { (label, icon, tab) ->
                                val open = { collectionTab = tab; collectionId = s.id }
                                val m = Modifier.weight(1f).height(56.dp)
                                if (icon != null) GameButton(label, onClick = open, modifier = m, fontSize = 16.sp, leading = { PixelIcon(icon, 24.dp) })
                                else GameButton(label, onClick = open, modifier = m, fontSize = 16.sp)
                            }
                    }
                    GameTextButton("취소", 20.sp) { selected = null; withSibs = emptySet() }
                }
            }
        }
    }

    // 내 보관함 (보석 구경 + 네임플레이트 고르기)
    collectionId?.let { id ->
        val st = allStudents.firstOrNull { it.id == id }
        if (st == null) collectionId = null
        else CollectionDialog(st, startTab = collectionTab) { collectionId = null }
    }

    // 같은 번호를 쓰는 형제 → 누구인지 고르기
    padChoices?.let { list ->
        LaunchedEffect(list) { delay(15_000); padChoices = null }
        Dialog(onDismissRequest = { padChoices = null }) {
            GamePanel {
                run {
                    Text("누구예요?", fontSize = 32.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(16.dp))
                    list.forEach { st ->
                        GameButton(
                            st.name,
                            onClick = { padChoices = null; withSibs = emptySet(); selected = st },
                            modifier = Modifier.widthIn(min = 260.dp).height(84.dp).padding(vertical = 4.dp),
                            fontSize = 32.sp, sound = null,
                            leading = { RankImage(st.level, 52.dp) },
                        )
                    }
                    GameTextButton("취소", 20.sp) { padChoices = null }
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
    GameButton(text, onClick, modifier.height(110.dp), color = color, fontSize = 34.sp, sound = null)
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


/** 메인 화면의 출결번호 키패드 — 4자리를 누르면 자동 확인, 연속 5번 틀리면 30초 잠금 */
@Composable
private fun PadPanel(modifier: Modifier, emptyNotice: Boolean, onSubmit: (String) -> Boolean) {
    var input by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    var fails by remember { mutableStateOf(0) }
    var locked by remember { mutableStateOf(false) }
    LaunchedEffect(input) { if (input.isNotEmpty()) { delay(15_000); input = "" } } // 15초 동안 입력 없으면 지워짐
    LaunchedEffect(locked) { if (locked) { delay(30_000); locked = false; wrong = false; fails = 0 } }

    fun check() {
        val ok = onSubmit(input)
        input = ""
        if (ok) { wrong = false; fails = 0 } else {
            wrong = true; fails += 1
            if (fails >= 5) locked = true
        }
    }
    fun press(k: String) {
        if (locked) return
        wrong = false
        when (k) {
            "지움" -> input = input.dropLast(1)
            "확인" -> if (input.isNotEmpty()) check()
            else -> if (input.length < 6) {
                input += k
                if (input.length == 4) check()
            }
        }
    }

    val theme = LocalGameTheme.current
    val land = LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val numFont = gameNumFont()

    // 제목 + 안내 + 입력한 숫자
    val header: @Composable () -> Unit = {
        Text("출결번호를 눌러 주세요", fontSize = if (land) 40.sp else 34.sp, fontWeight = FontWeight.Bold, color = theme.title, textAlign = TextAlign.Center)
        Text(
            when {
                locked -> "여러 번 틀렸어요. 30초 뒤에 다시 눌러 주세요"
                wrong -> "없는 번호예요. 다시 눌러 주세요"
                else -> "학부모님 휴대폰 번호 끝 4자리"
            },
            color = if (locked || wrong) BadRed else Muted, fontSize = 20.sp, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            input.padEnd(4, '_').chunked(1).joinToString(" "),
            fontSize = if (land) 72.sp else 52.sp, fontWeight = FontWeight.Bold, color = theme.title, fontFamily = numFont,
        )
    }
    // 번호 키패드 (효과음 없음, 진동만)
    val keys: @Composable () -> Unit = {
        listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("지움", "0", "확인")).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(vertical = 5.dp)) {
                row.forEach { k ->
                    GameButton(
                        k, onClick = { press(k) }, enabled = !locked,
                        color = if (k.length > 1) theme.keyAltColor else theme.keyColor,
                        fontSize = if (k.length > 1) 22.sp else 36.sp,
                        sound = null,
                        fontFamily = if (k.length > 1) null else numFont,
                        modifier = Modifier.width(if (land) 130.dp else 110.dp).height(if (land) 84.dp else 76.dp),
                    )
                }
            }
        }
    }
    val notice: @Composable () -> Unit = {
        if (emptyNotice) {
            Spacer(Modifier.height(12.dp))
            Text("등록된 원생이 없습니다.\n위의 도장 이름을 길게 눌러 관리자 화면에서 등록하세요.", fontSize = 16.sp, color = Muted, textAlign = TextAlign.Center)
        }
    }

    if (land) {
        // 태블릿 가로: 왼쪽 제목·숫자, 오른쪽 키패드
        Row(modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) { header(); notice() }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { keys() }
        }
    } else {
        Column(
            modifier.verticalScroll(rememberScrollState()).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) { header(); Spacer(Modifier.height(8.dp)); keys(); notice() }
    }
}
