package com.jungdo.attend.server

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.provider.Settings
import android.provider.Telephony
import android.telephony.TelephonyManager
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ListenerRegistration
import com.jungdo.attend.MainActivity
import com.jungdo.attend.call.CallTracker
import com.jungdo.attend.call.Phone
import com.jungdo.attend.call.ReasonActivity
import com.jungdo.attend.data.ClassTimes
import com.jungdo.attend.data.Student
import com.jungdo.attend.data.StudentStatus
import com.jungdo.attend.data.Bulk
import com.jungdo.attend.data.BulkStatus
import com.jungdo.attend.data.DayNotice
import com.jungdo.attend.data.EventType
import com.jungdo.attend.data.MessageConfig
import com.jungdo.attend.data.Inbox
import com.jungdo.attend.data.InboxKind
import com.jungdo.attend.data.Messages
import com.jungdo.attend.data.Prefs
import com.jungdo.attend.data.Repo
import com.jungdo.attend.data.Status
import com.jungdo.attend.data.Wins
import com.google.firebase.firestore.DocumentChange
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * 기기 B: 문자 발송 서버 (포그라운드 서비스 — 상단 알림이 떠 있는 동안 화면이 꺼져도 계속 동작)
 *
 *  1) Firestore events 에서 status == PENDING 인 것을 실시간 감시
 *  2) 트랜잭션으로 PENDING → SENDING 으로 '찜' (같은 신호를 두 번 보내지 않게)
 *  3) 문구(config/messages) + 그날 안내(notices/{날짜}) 로 문자 내용 만들기
 *  4) SmsSender 로 학부모 번호에 발송 → SENT / FAILED 기록
 *  5) 통화가 끝나면 (앱에서 건 전화 / 학부모 번호와의 일반 전화) 결석 사유 입력 창 띄우기
 */
class SmsService : Service() {

    companion object {
        val running = MutableStateFlow(false)
        val lastInfo = MutableStateFlow("꺼져 있음")
        private const val CHANNEL = "sms_server"
        private const val CALL_CHANNEL = "call_reason"
        private const val CALL_NOTI_ID = 2
        private const val NOTI_ID = 1
        private const val TAG = "SmsService"

        fun start(ctx: Context) {
            Prefs(ctx).serverEnabled = true
            ContextCompat.startForegroundService(ctx, Intent(ctx, SmsService::class.java))
        }

        fun stop(ctx: Context) {
            Prefs(ctx).serverEnabled = false
            ctx.stopService(Intent(ctx, SmsService::class.java))
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val queue = Channel<String>(Channel.UNLIMITED)
    private val inFlight = HashSet<String>()
    private var listener: ListenerRegistration? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var started = false
    private lateinit var prefs: Prefs
    private lateinit var classTimes: ClassTimes
    @Volatile private var students: List<Student> = emptyList()
    private var studentsReg: ListenerRegistration? = null
    private var inCall = false

    // 단체 문자: 출석 문자(등원·하원)가 우선, 단체 문자는 그 사이사이에 한 통씩
    private val bulkQueue = Channel<String>(Channel.UNLIMITED)
    private val bulkInFlight = HashSet<String>()
    private var bulkListener: ListenerRegistration? = null

    // 상품 당첨 알림: 지급 대기(paid=false) 당첨이 새로 생기면 알림
    private var winListener: ListenerRegistration? = null
    private val notifiedWins = HashSet<String>()
    private var winFirst = true
    private var failCount = 0

    // 문구·안내는 20초 동안 기억해서 매번 인터넷에서 읽지 않음 (연달아 누를 때 속도 ↑)
    private var cfgCache: Pair<Long, MessageConfig>? = null
    private val noticeCache = HashMap<String, Pair<Long, DayNotice>>()
    private suspend fun cfg(): MessageConfig {
        val c = cfgCache; val now = System.currentTimeMillis()
        if (c != null && now - c.first < 20_000) return c.second
        return Messages.loadConfig().also { cfgCache = now to it }
    }
    private suspend fun notice(day: String): DayNotice {
        val c = noticeCache[day]; val now = System.currentTimeMillis()
        if (c != null && now - c.first < 20_000) return c.second
        return Messages.loadNotice(day).also { noticeCache[day] = now to it }
    }

    /** 전화 상태: 울림/통화중 → 대기(IDLE) 로 바뀌면 통화 끝 */
    private val phoneReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            when (i.getStringExtra(TelephonyManager.EXTRA_STATE)) {
                TelephonyManager.EXTRA_STATE_RINGING, TelephonyManager.EXTRA_STATE_OFFHOOK -> inCall = true
                TelephonyManager.EXTRA_STATE_IDLE -> if (inCall) {
                    inCall = false
                    scope.launch { delay(1_500); onCallEnded() } // 통화 기록이 저장될 시간
                }
            }
        }
    }

    /** 학부모에게서 문자가 오면: 번호로 아이(형제) 찾기 → 문자 내용을 담아 사유 입력 창 */
    private val smsReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            val parts = Telephony.Sms.Intents.getMessagesFromIntent(i) ?: return
            if (parts.isEmpty()) return
            val from = Repo.normalizePhone(parts.first().originatingAddress ?: "")
            if (from.length < 9) return
            val body = parts.joinToString("") { it.messageBody ?: "" }.trim()
            if (body.isBlank()) return
            onSmsReceived(from, body)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        classTimes = ClassTimes(this)
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "문자 발송 서버", NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel(CALL_CHANNEL, "통화 후 결석 사유 입력", NotificationManager.IMPORTANCE_HIGH))
        WinAlert.ensureChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val n = buildNotification("시작하는 중…")
        if (Build.VERSION.SDK_INT >= 34) startForeground(NOTI_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(NOTI_ID, n)
        if (!started) { started = true; begin() }
        return START_STICKY // 시스템이 죽여도 다시 살림
    }

    private fun begin() {
        if (FirebaseAuth.getInstance().currentUser == null) { info("로그인이 필요합니다. 앱을 열어 로그인하세요."); stopSelf(); return }
        if (checkSelfPermission(Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            info("문자 보내기 권한이 없습니다. 앱에서 권한을 허용하세요."); stopSelf(); return
        }
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "jungdo:sms").apply { setReferenceCounted(false); acquire() }
        running.value = true

        // 원생 명단 (최신 번호 · 통화 상대 찾기용)
        studentsReg = Repo.students().addSnapshotListener { snap, _ -> if (snap != null) { Repo.migrateAges(snap.documents); students = snap.documents.map(Repo::toStudent) } }
        // 통화 끝 감지 (전화 상태 권한이 있을 때 동작, 나중에 허용해도 됨)
        ContextCompat.registerReceiver(this, phoneReceiver, IntentFilter(TelephonyManager.ACTION_PHONE_STATE_CHANGED), ContextCompat.RECEIVER_EXPORTED)

        // 학부모 문자 수신 감지 (문자 받기 권한이 있을 때 동작)
        if (checkSelfPermission(Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED) {
            ContextCompat.registerReceiver(this, smsReceiver, IntentFilter(Telephony.Sms.Intents.SMS_RECEIVED_ACTION), ContextCompat.RECEIVER_EXPORTED)
        }

        // 실시간 감시
        listener = Repo.events().whereEqualTo("status", Status.PENDING).addSnapshotListener { snap, e ->
            if (e != null) { info("DB 연결 오류: ${e.message}"); return@addSnapshotListener }
            // 누른 순서(시간순)로 보내기
            snap?.documents?.sortedBy { it.getLong("clientTime") ?: 0L }?.forEach { enqueue(it.id) }
        }
        // 상품 당첨 감시: 새 당첨이 생기면 알림 (서비스를 막 켰을 때 이미 쌓여 있던 오래된 건은 알리지 않음 — 탭에서 보임)
        winListener = Repo.wins().whereEqualTo("paid", false).addSnapshotListener { snap, _ ->
            if (snap == null) return@addSnapshotListener
            val first = winFirst; winFirst = false
            for (c in snap.documentChanges) {
                when (c.type) {
                    DocumentChange.Type.ADDED -> {
                        val w = Wins.toWin(c.document)
                        if (!notifiedWins.add(w.id)) continue
                        if (first && System.currentTimeMillis() - w.time > 10 * 60_000L) continue
                        WinAlert.notify(this, w)
                    }
                    DocumentChange.Type.REMOVED -> { notifiedWins.remove(c.document.id); WinAlert.cancel(this, c.document.id) }
                    else -> {}
                }
            }
        }
        // 단체 문자 감시 + 한 통씩 보내는 일꾼
        bulkListener = Bulk.col().whereEqualTo("status", BulkStatus.PENDING).addSnapshotListener { snap, _ ->
            snap?.documents?.sortedBy { it.getLong("createdMs") ?: 0L }?.forEach { enqueueBulk(it.id) }
        }
        scope.launch {
            for (id in bulkQueue) {
                try { processBulk(id) } catch (t: Throwable) { Log.e(TAG, "bulk $id", t); info("단체 문자 오류: ${t.message}") }
                finally { bulkInFlight.remove(id) }
            }
        }
        // 한 건씩 차례대로 발송 (통신사 스팸 차단 피하려고 1.2초 간격)
        scope.launch {
            for (id in queue) {
                try { process(id) } catch (t: Throwable) { Log.e(TAG, "process $id", t); info("오류: ${t.message}") }
                finally { inFlight.remove(id) }
                delay(1_200)
            }
        }
        // 1분마다: 살아있음 신호 + 놓친 신호 다시 확인
        scope.launch {
            while (isActive) {
                runCatching { Repo.heartbeat("server", mapOf("running" to true)) }
                sweep()
                delay(60_000)
            }
        }
        info("대기 중 · 출석 신호를 기다리는 중")
    }

    private fun enqueueBulk(id: String) {
        if (bulkInFlight.add(id)) bulkQueue.trySend(id)
    }

    /** 단체 문자: 받는 번호마다 한 통씩. 출석 문자가 밀려 있으면 먼저 보내고 기다림 */
    private suspend fun processBulk(id: String) {
        val ref = Bulk.col().document(id)
        val snap = try {
            Repo.db.runTransaction<com.google.firebase.firestore.DocumentSnapshot?> { tx ->
                val s = tx.get(ref)
                if (s.getString("status") != BulkStatus.PENDING) null
                else { tx.update(ref, mapOf("status" to BulkStatus.SENDING, "lastMs" to System.currentTimeMillis())); s }
            }.await()
        } catch (e: Exception) { info("인터넷 확인 필요: ${e.message}"); return } ?: return
        val job = Bulk.toJob(snap)
        var fail = 0
        for ((i, item) in job.items.withIndex()) {
            if (job.results.containsKey(i)) continue // 이미 보낸 것은 건너뜀 (이어 보내기)
            while (inFlight.isNotEmpty()) { delay(500) } // 등원·하원 문자가 우선
            info("단체 문자 ${i + 1}/${job.items.size} · ${item.names}")
            val err = SmsSender.send(this, item.phone, item.text)
            if (err != null) fail++
            ref.update(mapOf("results.$i" to (err ?: "OK"), "lastMs" to System.currentTimeMillis()))
            delay(1_200)
        }
        ref.update(mapOf("status" to BulkStatus.DONE, "lastMs" to System.currentTimeMillis()))
        info("단체 문자 보내기 끝 · ${job.title}" + if (fail > 0) " (실패 ${fail}건)" else "")
        if (fail > 0) notifyFail("단체 문자 '${job.title}'", "${fail}건 실패 — [단체 문자] 탭에서 실패만 다시 보낼 수 있어요")
    }

    /** 발송 실패는 알림으로도 알려줌 (화면을 안 보고 있어도 놓치지 않게) */
    private fun notifyFail(what: String, why: String) {
        failCount++
        val open = PendingIntent.getActivity(this, 10, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(this, CALL_CHANNEL)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("문자 발송 실패 (누적 ${failCount}건)")
            .setContentText("$what: $why")
            .setStyle(NotificationCompat.BigTextStyle().bigText("$what: $why\n[상태·기록] 탭에서 재발송할 수 있어요"))
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        getSystemService(NotificationManager::class.java).notify(CALL_NOTI_ID + 400, n)
    }

    private fun enqueue(id: String) {
        if (inFlight.add(id)) queue.trySend(id)
    }

    private suspend fun sweep() {
        try {
            Repo.events().whereEqualTo("status", Status.PENDING).get().await().documents.sortedBy { it.getLong("clientTime") ?: 0L }.forEach { enqueue(it.id) }
            Bulk.col().whereEqualTo("status", BulkStatus.PENDING).get().await().documents.forEach { enqueueBulk(it.id) }
            // 단체 문자 도중 앱이 꺼졌으면(3분 넘게 소식 없음) 이어서 보내도록 되돌림
            Bulk.col().whereEqualTo("status", BulkStatus.SENDING).get().await().documents
                .filter { (it.getLong("lastMs") ?: 0L) < System.currentTimeMillis() - 3 * 60_000 && it.id !in bulkInFlight }
                .forEach { it.reference.update("status", BulkStatus.PENDING) }
            // 발송 도중 앱이 강제 종료돼 'SENDING'에 멈춘 건 → 실패 처리 (중복 발송 위험 때문에 자동 재발송은 안 함)
            val stale = System.currentTimeMillis() - 5 * 60_000
            Repo.events().whereEqualTo("status", Status.SENDING).get().await().documents
                .filter { (it.getTimestamp("claimedAt")?.toDate()?.time ?: 0L) < stale }
                .forEach { it.reference.update(mapOf("status" to Status.FAILED, "error" to "발송 중 중단됨 — 재발송 버튼으로 다시 보내세요")) }
        } catch (e: Exception) {
            Log.w(TAG, "sweep", e)
        }
    }

    private suspend fun process(id: String) {
        val ref = Repo.events().document(id)
        // ① 찜하기 (다른 기기/중복 감지로 두 번 보내는 것 방지)
        val data: Map<String, Any?> = try {
            Repo.db.runTransaction<Map<String, Any?>?> { tx ->
                val s = tx.get(ref)
                if (s.getString("status") != Status.PENDING) null
                else {
                    tx.update(ref, mapOf("status" to Status.SENDING, "claimedAt" to FieldValue.serverTimestamp(), "serverModel" to Build.MODEL))
                    s.data
                }
            }.await()
        } catch (e: Exception) {
            info("인터넷 확인 필요: ${e.message}"); return
        } ?: return

        val ev = Repo.toEvent(id, data)

        // ② 너무 오래된 신호는 보내지 않음
        val base = maxOf(ev.clientTime, (data["retryAt"] as? Number)?.toLong() ?: 0L)
        if (System.currentTimeMillis() - base > prefs.expireMinutes * 60_000L) {
            ref.update(mapOf("status" to Status.EXPIRED, "error" to "${prefs.expireMinutes}분 넘게 지난 신호라 보내지 않음"))
            info("${ev.name} ${EventType.label(ev.type)}: 시간 초과로 건너뜀")
            return
        }

        // ③ 문자 내용 만들기 (설정한 문구 + 그날 안내). {부}는 등원 시각으로 자동 판정
        val part = classTimes.partOf(ev.clientTime)
        val text = Messages.build(ev, cfg(), notice(Repo.dayOf(ev.clientTime)), classTimes.label(part))
        // 번호는 최신 명단 기준 (명단이 바뀌었을 수 있으니), 없으면 신호에 담긴 번호
        val latest = students.firstOrNull { it.id == ev.studentId }?.phones?.ifEmpty { null } ?: ev.phones
        val all = latest.map(Repo::normalizePhone).filter { it.length >= 9 }.distinct()
        val phones = if (prefs.smsAllPhones) all else all.take(1)
        if (phones.isEmpty()) {
            ref.update(mapOf("status" to Status.FAILED, "error" to "학부모 연락처 없음", "message" to text))
            notifyFail("${ev.name} ${EventType.label(ev.type)}", "학부모 연락처 없음")
            return
        }

        // ④ 발송
        info("${ev.name} ${EventType.label(ev.type)} 문자 보내는 중…")
        val errors = mutableListOf<String>()
        for (p in phones) {
            SmsSender.send(this, p, text)?.let { errors += "${Repo.prettyPhone(p)}: $it" }
            if (phones.size > 1) delay(800)
        }
        ref.update(mapOf(
            "status" to if (errors.isEmpty()) Status.SENT else Status.FAILED,
            "error" to errors.joinToString(" / ").ifBlank { null },
            "message" to text,
            "part" to part,
            "sentAt" to FieldValue.serverTimestamp(),
        ))
        if (errors.isNotEmpty()) notifyFail("${ev.name} ${EventType.label(ev.type)}", errors.first())
        info(if (errors.isEmpty()) "${ev.name} ${EventType.label(ev.type)} 발송 완료 (${Repo.timeText(System.currentTimeMillis())})" else "${ev.name} 발송 실패: ${errors.first()}")
    }

    /** 통화가 끝났을 때: 앱에서 건 전화면 그 아이, 일반 전화면 번호로 아이 찾기 → 사유 입력 창 */
    private fun onCallEnded() {
        val last = Phone.lastCall(this)
        val pending = CallTracker.take(this)
        val ids: List<String>
        val number: String
        val header: String
        if (pending != null) {
            ids = pending.studentIds; number = pending.number
            val names = students.filter { it.id in ids }.joinToString(", ") { it.name }
            header = "$names 학부모님과 통화했어요"
        } else {
            if (!prefs.callPopupAny || last == null || last.number.length < 9) return
            val matched = students.filter { s -> s.status != StudentStatus.LEFT && s.phones.any { Repo.normalizePhone(it) == last.number } }
            if (matched.isEmpty()) return
            ids = matched.map { it.id }; number = last.number
            val lb = matched.first().let { s -> s.phoneLabel(s.phones.indexOfFirst { Repo.normalizePhone(it) == last.number }) }
            header = "${matched.joinToString(", ") { it.name }} $lb 통화" + if (last.missed) " (부재중)" else ""
        }
        val missed = last != null && last.number == Repo.normalizePhone(number) && last.durationSec == 0L
        val intent = ReasonActivity.intent(this, ids, number, header, missed)
        if (Settings.canDrawOverlays(this)) {
            runCatching { startActivity(intent) }.onFailure { notifyReason(intent, header) }
        } else notifyReason(intent, header)
    }

    private fun hasWord(body: String, words: String) =
        words.split(",", "\n").map { it.trim() }.filter { it.isNotEmpty() }.any { body.contains(it) }

    private fun onSmsReceived(from: String, body: String) {
        val matched = students.filter { s -> s.status != StudentStatus.LEFT && s.phones.any { Repo.normalizePhone(it) == from } }
        val absent = hasWord(body, prefs.absentWords)
        val schedule = !absent && prefs.scheduleToKakao && hasWord(body, prefs.scheduleWords)

        // ── 등록 안 된 번호: 결석 키워드가 있을 때만 (광고 문자는 제외) → 어느 아이인지 고르는 창 ──
        if (matched.isEmpty()) {
            if (!absent || body.contains("(광고)") || body.contains("무료수신거부")) return
            val id = Inbox.add(from, body, emptyList(), "", InboxKind.UNKNOWN)
            if (!prefs.smsPopup) return
            val header = "등록 안 된 번호 ${Repo.prettyPhone(from)} 문자"
            openReason(ReasonActivity.intent(this, emptyList(), from, header, false)
                .putExtra("smsBody", body.take(300)).putExtra("inboxId", id).putExtra("addNumber", from).putExtra("replyNumber", from), header)
            return
        }

        val lb = matched.first().let { s -> s.phoneLabel(s.phones.indexOfFirst { Repo.normalizePhone(it) == from }) }
        val names = matched.joinToString(", ") { it.name }
        val kind = if (absent) InboxKind.ABSENT else if (schedule) InboxKind.SCHEDULE else InboxKind.OTHER
        val id = Inbox.add(from, body, matched, lb, kind) // 키워드에 안 걸려도 [받은 문자]에 남겨서 놓치지 않게
        when (kind) {
            InboxKind.ABSENT -> if (prefs.smsPopup) {
                val header = "$names $lb 문자"
                openReason(ReasonActivity.intent(this, matched.map { it.id }, from, header, false)
                    .putExtra("smsBody", body.take(300)).putExtra("inboxId", id).putExtra("replyNumber", from).putExtra("replyLabel", lb), header)
            }
            InboxKind.SCHEDULE -> notifyKakao(names, body)
            else -> notifyMissed(names, lb, body)
        }
    }

    private fun openReason(intent: Intent, header: String) {
        if (Settings.canDrawOverlays(this)) {
            runCatching { startActivity(intent) }.onFailure { notifyReason(intent, header) }
        } else notifyReason(intent, header)
    }

    /** 키워드에 안 걸린 학부모 문자: 조용한 알림만 (누르면 앱) — [받은 문자] 탭에 남아 있음 */
    private fun notifyMissed(names: String, label: String, body: String) {
        val open = PendingIntent.getActivity(this, 9, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle("$names $label 문자")
            .setContentText(body.take(60))
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        getSystemService(NotificationManager::class.java).notify(CALL_NOTI_ID + 2 + (body.hashCode() and 0xff), n)
    }

    /** 스케줄 문자: 누르면 카카오톡 공유 창(받을 사람 고르고 전송만 누르면 됨) */
    private fun notifyKakao(names: String, body: String) {
        val text = "[$names 학부모 문자]\n$body"
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        val kakao = Intent(send).setPackage("com.kakao.talk")
        val target = if (kakao.resolveActivity(packageManager) != null) kakao else Intent.createChooser(send, "보내기")
        val pi = PendingIntent.getActivity(this, 8, target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(this, CALL_CHANNEL)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle("$names 학부모 스케줄 문자")
            .setContentText(body.take(60) + " · 눌러서 카톡으로 보내기")
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        getSystemService(NotificationManager::class.java).notify(CALL_NOTI_ID + 1, n)
    }

    /** '다른 앱 위에 표시' 권한이 없으면 알림으로 (누르면 사유 입력 창) */
    private fun notifyReason(intent: Intent, header: String) {
        val pi = PendingIntent.getActivity(this, 7, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(this, CALL_CHANNEL)
            .setSmallIcon(android.R.drawable.sym_action_call)
            .setContentTitle(header)
            .setContentText("눌러서 결석 사유 입력")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        getSystemService(NotificationManager::class.java).notify(CALL_NOTI_ID, n)
    }

    private fun info(t: String) {
        lastInfo.value = t
        Log.i(TAG, t)
        getSystemService(NotificationManager::class.java).notify(NOTI_ID, buildNotification(t))
    }

    private fun buildNotification(text: String): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle("정도 출결 · 문자 서버 작동 중")
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(open)
            .build()
    }

    override fun onDestroy() {
        running.value = false
        lastInfo.value = "꺼져 있음"
        listener?.remove()
        bulkListener?.remove()
        winListener?.remove()
        studentsReg?.remove()
        runCatching { unregisterReceiver(phoneReceiver) }
        runCatching { unregisterReceiver(smsReceiver) }
        scope.cancel()
        runCatching { Repo.heartbeat("server", mapOf("running" to false)) }
        runCatching { wakeLock?.release() }
        super.onDestroy()
    }
}
