package com.jungdo.attend.server

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.ContextCompat
import com.jungdo.attend.data.AttendEvent

/**
 * 이 폰의 문자함(보낸 문자)에 그 문자가 실제로 있는지 확인.
 * 앱 기록은 실패/시간초과인데 문자함에는 보내진 것으로 남아 있는 경우를 '전송됨'으로 맞추기 위한 용도.
 * (문자함의 '보냄' = 폰이 통신사로 내보냈다는 뜻이에요. 상대 폰 도착까지 보장하는 건 아니에요.)
 * 문자 읽기 권한이 없으면 항상 false.
 */
object SentBox {
    private const val TYPE_SENT = 2   // Telephony.Sms.MESSAGE_TYPE_SENT

    fun has(ctx: Context, e: AttendEvent): Boolean {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) return false
        val body = e.message?.trim().orEmpty()
        if (body.isEmpty() || e.phones.isEmpty()) return false
        val since = e.clientTime - 60_000L
        val tails = e.phones.map { it.filter(Char::isDigit).takeLast(8) }.filter { it.length >= 7 }
        if (tails.isEmpty()) return false
        return runCatching {
            ctx.contentResolver.query(
                Uri.parse("content://sms/sent"), arrayOf("address", "body", "type"),
                "date >= ?", arrayOf(since.toString()), "date DESC",
            )?.use { c ->
                var n = 0
                while (c.moveToNext() && n++ < 300) {
                    if (c.getInt(2) != TYPE_SENT) continue
                    val addr = (c.getString(0) ?: "").filter(Char::isDigit)
                    if (tails.none { addr.endsWith(it) }) continue
                    val b = (c.getString(1) ?: "").trim()
                    if (b == body || b.startsWith(body.take(30))) return@runCatching true
                }
                false
            } ?: false
        }.getOrDefault(false)
    }
}
