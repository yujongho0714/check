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
import com.jungdo.attend.ui.BadRed
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

private fun granted(ctx: Context, p: String) =
    androidx.core.content.ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED

private fun callPermsOk(ctx: Context) = listOf(Manifest.permission.CALL_PHONE, Manifest.permission.READ_PHONE_STATE, Manifest.permission.READ_CALL_LOG).all { granted(ctx, it) }

private fun batteryExempt(ctx: Context) =
    ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName)

/** 기기 B: 관장님 폰 화면 — 한 화면에 탭으로: 오늘 출결 / 월간 출석부 / 퇴관 위험 / 명단·형제 / 문자·안내 / 상태·기록 / 설정 */
@Composable
fun ServerScreen(onReset: () -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    val students by rememberStudents()
    rememberCustomHolidays() // 관장님이 추가한 휴일을 앱 전체에 반영
    val recent by com.jungdo.attend.data.rememberInboxRange(Repo.shiftDay(Repo.today(), -30), Repo.today())
    val openMsgs = recent.count { !it.handled }
    val tabs = listOf("오늘 출결", "월간 출석부", if (openMsgs > 0) "받은 문자 ($openMsgs)" else "받은 문자", "단체 문자", "퇴관 위험", "결석 통계", "명단·형제", "문자·안내", "상태·기록", "설정")
    Column(Modifier.fillMaxSize().background(PageBg)) {
        Row(Modifier.fillMaxWidth().background(Navy).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("정도 출결 · 관장님 폰", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            val running by SmsService.running.collectAsState()
            Text(if (running) "● 문자 서버 ON" else "● 문자 서버 OFF", color = if (running) Color(0xFF86EFAC) else Color(0xFFFCA5A5), fontSize = 13.sp)
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
            7 -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) { MessageSettings() }
            8 -> StatusAndLog()
            else -> ServerSettings(onReset)
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
    var overlayOk by remember { mutableStateOf(Settings.canDrawOverlays(ctx)) }
    val refresh = {
        callOk = callPermsOk(ctx)
        recvOk = granted(ctx, Manifest.permission.RECEIVE_SMS)
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
                    Text(if (running) "🟢 문자 서버 작동 중" else "🔴 문자 서버 꺼짐", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text(info, color = Muted)
                    Spacer(Modifier.height(8.dp))
                    if (running) OutlinedButton(onClick = { SmsService.stop(ctx) }) { Text("서버 끄기") }
                    else Button(enabled = smsOk, onClick = { SmsService.start(ctx) }) { Text("서버 켜기", fontSize = 18.sp) }
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
                        Text(if (kOk) "✅" else "⚠️", fontSize = 18.sp)
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
                OutlinedButton(onClick = { day = Repo.shiftDay(day, -1) }) { Text("◀") }
                Text(dayLabel(day), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                OutlinedButton(onClick = { day = Repo.shiftDay(day, 1) }) { Text("▶") }
            }
            Text(
                "등원 ${events.count { it.type == EventType.IN }} · 하원 ${events.count { it.type == EventType.OUT }} · " +
                    "성공 ${events.count { it.status == Status.SENT }} · 실패 ${events.count { it.status == Status.FAILED }} · " +
                    "대기 ${events.count { it.status == Status.PENDING || it.status == Status.SENDING }}",
                color = Muted,
            )
        } }
        // ── 발송 기록 ──
        if (events.isEmpty()) item { Text("기록이 없습니다.", color = Muted) }
        items(events, key = { it.id }) { e -> LogRow(e) }
    }
}

@Composable
private fun CheckRow(label: String, ok: Boolean, onFix: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(if (ok) "✅" else "⚠️", fontSize = 18.sp)
        Spacer(Modifier.width(8.dp))
        Text(label, modifier = Modifier.weight(1f), fontSize = 16.sp)
        if (!ok) TextButton(onClick = onFix) { Text("허용하기") }
    }
}

@Composable
private fun LogRow(e: AttendEvent) {
    var open by remember { mutableStateOf(false) }
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(12.dp),
        modifier = Modifier.clickable { open = !open }) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(Repo.timeText(e.clientTime), modifier = Modifier.width(80.dp), color = Muted)
                Text(e.name, fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.width(80.dp))
                Text(EventType.label(e.type), modifier = Modifier.width(44.dp))
                StatusChip(e.status)
                Spacer(Modifier.weight(1f))
                if (e.status == Status.FAILED || e.status == Status.EXPIRED)
                    TextButton(onClick = { Repo.retry(e.id) }) { Text("재발송") }
            }
            e.error?.let { Text(it, color = BadRed, fontSize = 13.sp) }
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
        if (testResult.isNotBlank()) Text(testResult, color = if (testResult.startsWith("실패")) BadRed else OkGreen)

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
        ExtraSettings()

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
