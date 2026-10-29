package com.jungdo.attend.server

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.firebase.auth.FirebaseAuth
import com.jungdo.attend.data.AttendEvent
import com.jungdo.attend.data.EventType
import com.jungdo.attend.data.Prefs
import com.jungdo.attend.data.Repo
import com.jungdo.attend.data.Status
import com.jungdo.attend.data.rememberDeviceStatus
import com.jungdo.attend.data.rememberEventsOfDay
import com.jungdo.attend.data.rememberNow
import com.jungdo.attend.data.rememberStudents
import com.jungdo.attend.data.rememberCustomHolidays
import com.jungdo.attend.call.Phone
import com.jungdo.attend.ui.AssetPackSettings
import com.jungdo.attend.ui.UiSizeSettings
import com.jungdo.attend.kiosk.KioskScreen
import com.jungdo.attend.ui.BadRed
import com.jungdo.attend.ui.DogamTab
import com.jungdo.attend.ui.GiftTab
import com.jungdo.attend.ui.GemRarityTab
import com.jungdo.attend.ui.LootSettings
import com.jungdo.attend.ui.PrizeSettings
import com.jungdo.attend.ui.MessageSettings
import com.jungdo.attend.ui.Muted
import com.jungdo.attend.ui.Navy
import com.jungdo.attend.ui.OkGreen
import com.jungdo.attend.ui.PageBg
import com.jungdo.attend.ui.SectionTitle
import com.jungdo.attend.ui.StatusChip
import com.jungdo.attend.ui.agoText
import com.jungdo.attend.ui.dayLabel
import com.jungdo.attend.ui.isOnline
import kotlinx.coroutines.launch
import com.jungdo.attend.ui.game.PixelText
import com.jungdo.attend.ui.game.PixelIcon
import androidx.compose.runtime.produceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private fun granted(ctx: Context, p: String) =
    androidx.core.content.ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED

private fun callPermsOk(ctx: Context) = listOf(Manifest.permission.CALL_PHONE, Manifest.permission.READ_PHONE_STATE, Manifest.permission.READ_CALL_LOG).all { granted(ctx, it) }

private fun batteryExempt(ctx: Context) =
    ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName)

/** 기기 B: 관장님 폰 화면 — 한 화면에 탭으로: 오늘 출결 / 월간 출석부 / 퇴관 위험 / 명단·형제 / 문자·안내 / 상태·기록 / 설정 */
@Composable
fun ServerScreen(onReset: () -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    val openWin by WinAlert.openWin.collectAsState()
    LaunchedEffect(openWin) { if (openWin != null) tab = 7 } // 당첨 알림을 눌러 들어오면 당첨·지급 탭으로
    val students by rememberStudents()
    rememberCustomHolidays() // 관장님이 추가한 휴일을 앱 전체에 반영
    val recent by com.jungdo.attend.data.rememberInboxRange(Repo.shiftDay(Repo.today(), -30), Repo.today())
    val openMsgs = recent.count { !it.handled }
    val pendingWins by com.jungdo.attend.data.rememberPendingWins()
    val tabs = listOf("오늘 출결", "월간 출석부", if (openMsgs > 0) "받은 문자 ($openMsgs)" else "받은 문자", "단체 문자", "퇴관 위험", "결석 통계", "명단·형제", if (pendingWins.isNotEmpty()) "당첨·지급 (${pendingWins.size})" else "당첨·지급", "지급내역", "내 정보 보내기", "문자·안내", "상태·기록", "보석 등급", "도감", "선물 보내기", "키오스크 화면", "설정")
    Column(Modifier.fillMaxSize().background(PageBg)) {
        Row(Modifier.fillMaxWidth().background(Navy).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("정도 출결 · 관장님 폰", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            val running by SmsService.running.collectAsState()
            PixelText(if (running) "{dot_on} 문자 서버 ON" else "{dot_off} 문자 서버 OFF", color = if (running) Color(0xFF86EFAC) else Color(0xFFFCA5A5), fontSize = 13.sp)
        }
        ScrollableTabRow(selectedTabIndex = tab, edgePadding = 4.dp) {
            tabs.forEachIndexed { i, t -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t, fontSize = 15.sp) }) }
        }
        when (tab) {
            0 -> TodayTab(students)
            1 -> MonthTab(students)
            2 -> InboxTab()
            3 -> BulkTab(students)
            4 -> RiskTab(students)
            5 -> StatsTab()
            6 -> RosterTab(students)
            7 -> WinsTab()
            8 -> PayoutsTab()
            9 -> MyInfoTab(students)
            10 -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) { MessageSettings() }
            11 -> StatusAndLog()
            12 -> GemRarityTab()
            13 -> DogamTab()
            14 -> GiftTab(students)
            15 -> KioskPreviewTab()
            else -> ServerSettings(onReset)
        }
    }
}

/**
 * 관장님 폰에서 출석 단말기(기기 A) 화면을 그대로 보는 미리보기 + 바로 크기 조절.
 * 미리보기라서 이름·등원·하원을 눌러도 출결 기록·보상·문자·"키오스크 연결됨" 신호가 전혀 안 생김. 크기는 저장하면 단말기에도 바로 적용.
 */
@Composable
private fun KioskPreviewTab() {
    var showSize by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        Text(
            "미리보기예요 · 눌러 봐도 출결은 기록 안 돼요 · 이름을 누르면 팝업이 뜨고 배치를 조절할 수 있어요",
            color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth().background(Color(0xFF7C3AED)).padding(horizontal = 16.dp, vertical = 6.dp),
        )
        Box(Modifier.weight(1f).fillMaxWidth()) {
            KioskScreen(onReset = {}, preview = true)
            Button(onClick = { showSize = !showSize }, modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp)) {
                Text(if (showSize) "크기 조절 닫기" else "크기 조절")
            }
            if (showSize) {
                Card(
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(0.5f).padding(bottom = 64.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                ) {
                    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) { UiSizeSettings() }
                }
            }
        }
    }
}

@SuppressLint("BatteryLife")
@Composable
private fun StatusAndLog() {
    val ctx = LocalContext.current
    val running by SmsService.running.collectAsState()
    val info by SmsService.lastInfo.collectAsState()
    val now by rememberNow(5_000)
    val kiosk by rememberDeviceStatus("kiosk")
    var day by remember { mutableStateOf(Repo.today()) }
    val events by rememberEventsOfDay(day)

    // 권한 상태 (설정 화면에서 돌아올 때마다 다시 확인)
    var smsOk by remember { mutableStateOf(granted(ctx, Manifest.permission.SEND_SMS)) }
    var notiOk by remember { mutableStateOf(Build.VERSION.SDK_INT < 33 || granted(ctx, Manifest.permission.POST_NOTIFICATIONS)) }
    var batteryOk by remember { mutableStateOf(batteryExempt(ctx)) }
    var callOk by remember { mutableStateOf(callPermsOk(ctx)) }
    var recvOk by remember { mutableStateOf(granted(ctx, Manifest.permission.RECEIVE_SMS)) }
    var readOk by remember { mutableStateOf(granted(ctx, Manifest.permission.READ_SMS)) }
    var overlayOk by remember { mutableStateOf(Settings.canDrawOverlays(ctx)) }
    val refresh = {
        callOk = callPermsOk(ctx)
        recvOk = granted(ctx, Manifest.permission.RECEIVE_SMS)
        readOk = granted(ctx, Manifest.permission.READ_SMS)
        overlayOk = Settings.canDrawOverlays(ctx)
        smsOk = granted(ctx, Manifest.permission.SEND_SMS)
        notiOk = Build.VERSION.SDK_INT < 33 || granted(ctx, Manifest.permission.POST_NOTIFICATIONS)
        batteryOk = batteryExempt(ctx)
    }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) refresh() }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { refresh() }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // ── 서버 켜기/끄기 ──
        item {
            Card(colors = CardDefaults.cardColors(containerColor = if (running) Color(0xFFDCFCE7) else Color(0xFFFEE2E2))) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    PixelText(if (running) "🟢 문자 서버 작동 중" else "🔴 문자 서버 꺼짐", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text(info, color = Muted)
                    Spacer(Modifier.height(8.dp))
                    if (running) OutlinedButton(onClick = { SmsService.stop(ctx) }) { Text("서버 끄기") }
                    else Button(enabled = smsOk, onClick = {
                        SmsService.start(ctx)
                        if (!batteryExempt(ctx)) runCatching { ctx.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}"))) }
                    }) { Text("서버 켜기", fontSize = 18.sp) }
                    if (running && !batteryOk) PixelText("⚠ 배터리 최적화 제외가 꺼져 있어요. 홈버튼만 눌러 두면 폰이 앱을 재워서 문자가 안 나갈 수 있어요. 아래 [준비 상태]에서 허용하세요.", color = BadRed, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    if (!smsOk) Text("먼저 아래에서 문자 권한을 허용하세요", color = BadRed)
                }
            }
        }
        // ── 준비 체크리스트 ──
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SectionTitle("준비 상태")
                    CheckRow("문자 보내기 권한", smsOk) {
                        val list = mutableListOf(Manifest.permission.SEND_SMS)
                        if (Build.VERSION.SDK_INT >= 33) list += Manifest.permission.POST_NOTIFICATIONS
                        permLauncher.launch(list.toTypedArray())
                    }
                    CheckRow("문자 받기 권한 (학부모 문자 오면 사유 입력 창)", recvOk) {
                        permLauncher.launch(arrayOf(Manifest.permission.RECEIVE_SMS))
                    }
                    CheckRow("문자함 확인 권한 (보낸 문자가 문자함에 있으면 '전송됨'으로 표시)", readOk) {
                        permLauncher.launch(arrayOf(Manifest.permission.READ_SMS))
                    }
                    CheckRow("알림 표시 권한", notiOk) {
                        if (Build.VERSION.SDK_INT >= 33) permLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
                    }
                    CheckRow("배터리 최적화 제외", batteryOk) {
                        ctx.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}")))
                    }
                    CheckRow("전화 걸기·통화 감지 (통화 끝나면 사유 입력 창)", callOk) {
                        permLauncher.launch(arrayOf(Manifest.permission.CALL_PHONE, Manifest.permission.READ_PHONE_STATE, Manifest.permission.READ_CALL_LOG))
                    }
                    CheckRow("다른 앱 위에 표시 (다른 앱 쓰는 중에도 창 띄우기)", overlayOk) {
                        ctx.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}")))
                    }
                    val k = kiosk
                    val kOk = isOnline(k?.lastSeen, now)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PixelIcon(if (kOk) "check" else "warn", 24.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("출석 단말기(기기 A) 연결: " + if (k == null) "아직 연결된 적 없음" else "${if (kOk) "연결됨" else "끊김"} · 마지막 신호 ${agoText(k.lastSeen, now)}" + (k.model?.let { " ($it)" } ?: ""),
                            color = if (kOk) Color.Unspecified else BadRed)
                    }
                }
            }
        }
        // ── 날짜 + 통계 ──
        item { Column {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { day = Repo.shiftDay(day, -1) }) { PixelIcon("arrow_l", 22.dp) }
                Text(dayLabel(day), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                OutlinedButton(onClick = { day = Repo.shiftDay(day, 1) }) { PixelIcon("arrow_r", 22.dp) }
            }
            Text(
                "등원 ${events.count { it.type == EventType.IN }} · 하원 ${events.count { it.type == EventType.OUT }} · " +
                    "전송됨 ${events.count { it.status == Status.SENT && it.dlvFail == 0 }} · 실패 ${events.count { it.status == Status.FAILED || (it.status == Status.SENT && it.dlvFail > 0) }} · " +
                    "대기 ${events.count { it.status == Status.PENDING || it.status == Status.SENDING }}",
                color = Muted,
            )
            val expired = events.count { it.status == Status.EXPIRED }
            if (expired > 0) PixelText("⚠ 시간초과 ${expired}건은 문자가 나가지 않았어요. 아래에서 [재전송]을 눌러 주세요.", color = BadRed, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        } }
        // ── 발송 기록 ──
        if (events.isEmpty()) item { Text("기록이 없습니다.", color = Muted) }
        items(events, key = { it.id }) { e -> LogRow(e, now) }
    }
}

@Composable
private fun CheckRow(label: String, ok: Boolean, onFix: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        PixelIcon(if (ok) "check" else "warn", 24.dp)
        Spacer(Modifier.width(8.dp))
        Text(label, modifier = Modifier.weight(1f), fontSize = 16.sp)
        if (!ok) TextButton(onClick = onFix) { Text("허용하기") }
    }
}

@Composable
private fun LogRow(e: AttendEvent, now: Long) {
    var open by remember { mutableStateOf(false) }
    var askRetry by remember { mutableStateOf(false) }
    // 앱 기록은 실패/시간초과/발송중인데 이 폰 문자함에 보낸 문자로 남아 있으면 '전송됨'으로 보여줌
    val lctx = LocalContext.current
    val inSentBox by produceState(false, e.id, e.status) {
        value = if (e.status == Status.FAILED || e.status == Status.EXPIRED || e.status == Status.SENDING)
            withContext(Dispatchers.IO) { SentBox.has(lctx, e) } else false
    }
    if (askRetry) AlertDialog(
        onDismissRequest = { askRetry = false },
        title = { Text("${e.name} 문자를 다시 보낼까요?") },
        text = { Text(if (e.status == Status.SENT) "이미 받은 분께는 같은 문자가 한 번 더 가요." else "문자를 다시 보내요.") },
        confirmButton = { Button(onClick = { Repo.retry(e.id); askRetry = false }) { Text("다시 보내기") } },
        dismissButton = { TextButton(onClick = { askRetry = false }) { Text("취소") } },
    )
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(12.dp),
        modifier = Modifier.clickable { open = !open }) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            val confirmed = e.dlvNeed > 0 && e.dlvOk >= e.dlvNeed
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(Repo.timeText(e.clientTime), modifier = Modifier.width(80.dp), color = Muted)
                Text(e.name, fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.width(80.dp))
                Text(EventType.label(e.type), modifier = Modifier.width(44.dp))
                if (e.status == Status.SENT) {
                    // 초록 '발송완료'는 상대 폰 도착이 확인된 뒤에만. 그 전엔 '접수·확인중', 3분 넘게 확인 안 되면 '도착 미확인'
                    // 통신사 도착 신호가 안 오는 폰이 많아서, 문자를 내보냈으면 '전송됨'. 통신사가 '실패'라고 알려준 것만 실패로 표시
                    val (label, bg) = when {
                        e.dlvFail > 0 -> "도착 실패" to BadRed
                        else -> "전송됨" to OkGreen
                    }
                    Box(Modifier.background(bg, RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 5.dp)) {
                        Text(label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                } else if (inSentBox) {
                    Box(Modifier.background(OkGreen, RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 5.dp)) {
                        Text("전송됨", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                } else StatusChip(e.status)
                Spacer(Modifier.weight(1f))
                if (e.status == Status.SENT || e.status == Status.FAILED || e.status == Status.EXPIRED)
                    OutlinedButton(onClick = { askRetry = true }) { Text("재전송", maxLines = 1, softWrap = false, fontSize = 14.sp) }
            }
            if (inSentBox) Text("이 폰 문자함에 보낸 문자로 남아 있어요 · 상대 폰 도착은 확인할 수 없어요", color = Muted, fontSize = 12.sp)
            else e.error?.let { Text(it, color = BadRed, fontSize = 13.sp) }
            if (e.status == Status.SENT) {
                val failNote = e.dlvError ?: "통신사 보고"
                if (e.dlvFail > 0) PixelText("⚠ 상대 폰에 도착 실패 ($failNote)", color = BadRed, fontSize = 13.sp)
                else if (confirmed) PixelText("✔ 상대 폰 도착 확인됨", color = OkGreen, fontSize = 13.sp)
                else Text("폰에서 내보냈어요 · 상대 폰 도착은 확인할 수 없어요", color = Muted, fontSize = 12.sp)
            }
            if (open) {
                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                Text("받는 번호: " + e.phones.joinToString(", ") { Repo.prettyPhone(it) }, fontSize = 13.sp, color = Muted)
                e.message?.let { Text(it, fontSize = 14.sp) }
            }
        }
    }
}

@Composable
private fun ServerSettings(onReset: () -> Unit) {
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }
    val scope = rememberCoroutineScope()
    var testNo by remember { mutableStateOf("") }
    var testResult by remember { mutableStateOf("") }
    var expire by remember { mutableStateOf(prefs.expireMinutes.toString()) }
    var confirm by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle("테스트 문자 보내기")
        Text("관장님 번호를 넣고 보내보세요. 문자가 오면 정상입니다.", color = Muted)
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(testNo, { testNo = it }, label = { Text("받을 번호") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Button(enabled = testNo.length >= 9 && granted(ctx, Manifest.permission.SEND_SMS), onClick = {
                testResult = "보내는 중…"
                scope.launch {
                    val r = SmsSender.send(ctx, Repo.normalizePhone(testNo), "[정도 출결] 테스트 문자입니다. 이 문자가 보이면 정상 작동 중입니다.")
                    testResult = r?.let { "실패: $it" } ?: "보냈어요 ✔ (받는 폰 확인)"
                }
            }) { Text("보내기") }
        }
        if (testResult.isNotBlank()) PixelText(testResult, color = if (testResult.startsWith("실패")) BadRed else OkGreen)

        HorizontalDivider()
        SectionTitle("늦게 온 신호 처리")
        Text("문자폰이 꺼져 있다가 나중에 켜졌을 때, 이 시간(분)보다 오래된 등원·하원은 문자를 보내지 않습니다.", color = Muted)
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(expire, { expire = it.filter(Char::isDigit) }, label = { Text("분") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.width(120.dp))
            Spacer(Modifier.width(8.dp))
            Button(onClick = { prefs.expireMinutes = expire.toIntOrNull()?.coerceIn(1, 1440) ?: 30; expire = prefs.expireMinutes.toString() }) { Text("저장") }
        }

        HorizontalDivider()
        LootSettings()

        HorizontalDivider()
        PrizeSettings()

        HorizontalDivider()
        ExtraSettings()

        HorizontalDivider()
        UiSizeSettings()

        HorizontalDivider()
        AssetPackSettings()

        HorizontalDivider()
        SectionTitle("기기")
        OutlinedButton(onClick = { confirm = "mode" }) { Text("이 기기 모드 바꾸기 (출석 단말기 ↔ 문자 폰)") }
        OutlinedButton(onClick = { confirm = "logout" }) { Text("로그아웃", color = BadRed) }
    }

    confirm?.let { what ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(if (what == "mode") "모드를 바꿀까요?" else "로그아웃할까요?") },
            text = { Text("문자 서버가 꺼집니다.") },
            confirmButton = {
                Button(colors = ButtonDefaults.buttonColors(containerColor = BadRed), onClick = {
                    SmsService.stop(ctx)
                    if (what == "logout") FirebaseAuth.getInstance().signOut()
                    confirm = null; onReset()
                }) { Text("예") }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("아니요") } },
        )
    }
}
