package com.jungdo.attend.server

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import com.google.firebase.firestore.FieldValue
import com.jungdo.attend.data.Repo
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume

/**
 * 실제 문자 발송 (관장님 폰의 통신사 요금으로 발송)
 * 원본 출결 앱과 같은 방식: SmsManager.sendTextMessage / sendMultipartTextMessage + 'SMS_SENT' 결과 확인
 *
 * @return null 이면 성공, 아니면 실패 이유
 */
object SmsSender {
    private const val ACTION_SENT = "com.jungdo.attend.SMS_SENT"
    /** 통신사가 '상대 폰에 도착했어요' 라고 알려줄 때 오는 신호 (SmsService 가 받아서 기록) */
    const val ACTION_DELIVERED = "com.jungdo.attend.SMS_DELIVERED"
    private val requestCode = AtomicInteger(1000)

    private fun smsManager(ctx: Context): SmsManager =
        if (Build.VERSION.SDK_INT >= 31) ctx.getSystemService(SmsManager::class.java)
        else @Suppress("DEPRECATION") SmsManager.getDefault()

    suspend fun send(ctx: Context, phone: String, text: String, eventId: String? = null): String? {
        if (text.isBlank()) return "문자 내용이 비어 있음"
        // 성공은 "" , 실패는 이유 문자열, 시간초과는 null
        val r: String = withTimeoutOrNull(30_000L) {
        suspendCancellableCoroutine<String> { cont ->
            val app = ctx.applicationContext
            val sms = smsManager(app)
            val parts = sms.divideMessage(text)
            val token = "t" + requestCode.incrementAndGet()
            var remaining = parts.size
            var firstError: String? = null

            val receiver = object : BroadcastReceiver() {
                override fun onReceive(c: Context, i: Intent) {
                    if (i.getStringExtra("token") != token) return
                    if (resultCode != Activity.RESULT_OK && firstError == null) firstError = errorText(resultCode)
                    remaining--
                    if (remaining <= 0) {
                        runCatching { app.unregisterReceiver(this) }
                        if (cont.isActive) cont.resume(firstError ?: "")
                    }
                }
            }
            ContextCompat.registerReceiver(app, receiver, IntentFilter(ACTION_SENT), ContextCompat.RECEIVER_NOT_EXPORTED)
            cont.invokeOnCancellation { runCatching { app.unregisterReceiver(receiver) } }

            val sentIntents = ArrayList<PendingIntent>()
            for (k in parts.indices) {
                val intent = Intent(ACTION_SENT).setPackage(app.packageName).putExtra("token", token)
                sentIntents += PendingIntent.getBroadcast(
                    app, requestCode.incrementAndGet(), intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            }
            // 출석 문자면 '수신 확인'도 요청 (도착하면 해당 기록에 표시)
            val deliveryIntents: ArrayList<PendingIntent>? = if (eventId == null) null else ArrayList()
            if (eventId != null && deliveryIntents != null) {
                for (k in parts.indices) {
                    val di = Intent(ACTION_DELIVERED).setPackage(app.packageName).putExtra("eventId", eventId)
                    deliveryIntents.add(PendingIntent.getBroadcast(
                        app, requestCode.incrementAndGet(), di,
                        PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    ))
                }
                runCatching { Repo.events().document(eventId).update("dlvNeed", FieldValue.increment(parts.size.toLong())) }
            }
            try {
                if (parts.size <= 1) sms.sendTextMessage(phone, null, text, sentIntents[0], deliveryIntents?.get(0))
                else sms.sendMultipartTextMessage(phone, null, parts, sentIntents, deliveryIntents)
            } catch (e: Exception) {
                runCatching { app.unregisterReceiver(receiver) }
                if (cont.isActive) cont.resume("발송 오류: ${e.message}")
            }
        }
        } ?: return "30초 안에 발송 결과가 오지 않음 (문자함에 갔는지 확인 후 재발송)"
        return r.ifEmpty { null }
    }

    private fun errorText(code: Int) = when (code) {
        SmsManager.RESULT_ERROR_GENERIC_FAILURE -> "일반 실패(통신사 거부/한도 초과 가능)"
        SmsManager.RESULT_ERROR_NO_SERVICE -> "통신 신호 없음"
        SmsManager.RESULT_ERROR_NULL_PDU -> "문자 형식 오류"
        SmsManager.RESULT_ERROR_RADIO_OFF -> "비행기 모드/전화 꺼짐"
        else -> "실패 코드 $code"
    }
}
