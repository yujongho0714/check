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
import com.jungdo.attend.data.EventType
import com.jungdo.attend.data.Messages
import com.jungdo.attend.data.Prefs
import com.jungdo.attend.data.Repo
import com.jungdo.attend.data.Status
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

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        classTimes = ClassTimes(this)
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "문자 발송 서버", NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel(CALL_CHANNEL, "통화 후 결석 사유 입력", NotificationManager.IMPORTANCE_HIGH))
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
        studentsReg = Repo.students().addSnapshotListener { snap, _ -> if (snap != null) students = snap.documents.map(Repo::toStudent) }
        // 통화 끝 감지 (전화 상태 권한이 있을 때 동작, 나중에 허용해도 됨)
        ContextCompat.registerReceiver(this, phoneReceiver, IntentFilter(TelephonyManager.ACTION_PHONE_STATE_CHANGED), ContextCompat.RECEIVER_EXPORTED)

        // 실시간 감시
        listener = Repo.events().whereEqualTo("status", Status.PENDING).addSnapshotListener { snap, e ->
            if (e != null) { info("DB 연결 오류: ${e.message}"); return@addSnapshotListener }
            snap?.documents?.forEach { enqueue(it.id) }
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

    private fun enqueue(id: String) {
        if (inFlight.add(id)) queue.trySend(id)
    }

    private suspend fun sweep() {
        try {
            Repo.events().whereEqualTo("status", Status.PENDING).get().await().documents.forEach { enqueue(it.id) }
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
        val text = Messages.build(ev, Messages.loadConfig(), Messages.loadNotice(Repo.dayOf(ev.clientTime)), classTimes.label(part))
        // 번호는 최신 명단 기준 (명단이 바뀌었을 수 있으니), 없으면 신호에 담긴 번호
        val latest = students.firstOrNull { it.id == ev.studentId }?.phones?.ifEmpty { null } ?: ev.phones
        val all = latest.map(Repo::normalizePhone).filter { it.length >= 9 }.distinct()
        val phones = if (prefs.smsAllPhones) all else all.take(1)
        if (phones.isEmpty()) {
            ref.update(mapOf("status" to Status.FAILED, "error" to "학부모 연락처 없음", "message" to text))
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
        studentsReg?.remove()
        runCatching { unregisterReceiver(phoneReceiver) }
        scope.cancel()
        runCatching { Repo.heartbeat("server", mapOf("running" to false)) }
        runCatching { wakeLock?.release() }
        super.onDestroy()
    }
}
