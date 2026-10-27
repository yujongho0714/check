package com.jungdo.attend.call

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CallLog
import androidx.core.content.ContextCompat
import com.jungdo.attend.data.Repo

/**
 * 앱에서 건 전화 기억하기 → 통화가 끝나면 그 아이(형제) 사유 입력 창을 띄움
 * (앱이 잠깐 종료돼도 남도록 SharedPreferences 에 저장)
 */
object CallTracker {
    private fun sp(ctx: Context) = ctx.getSharedPreferences("call_tracker", Context.MODE_PRIVATE)

    data class Pending(val studentIds: List<String>, val number: String, val at: Long)

    fun remember(ctx: Context, studentIds: List<String>, number: String) {
        sp(ctx).edit().putString("ids", studentIds.joinToString(",")).putString("num", number).putLong("at", System.currentTimeMillis()).apply()
    }

    /** 2시간 안에 앱에서 건 전화가 있으면 꺼내고 지움 */
    fun take(ctx: Context): Pending? {
        val p = sp(ctx)
        val at = p.getLong("at", 0L)
        val ids = p.getString("ids", "") ?: ""
        val num = p.getString("num", "") ?: ""
        p.edit().clear().apply()
        if (ids.isBlank() || System.currentTimeMillis() - at > 2 * 3_600_000L) return null
        return Pending(ids.split(","), num, at)
    }

    fun peek(ctx: Context): Boolean = (sp(ctx).getString("ids", "") ?: "").isNotBlank()
}

/** 마지막 통화 (번호, 통화 시간 초, 걸었는지/받았는지) — 통화 기록 권한 필요 */
data class LastCall(val number: String, val durationSec: Long, val outgoing: Boolean, val missed: Boolean)

object Phone {
    fun has(ctx: Context, perm: String) = ContextCompat.checkSelfPermission(ctx, perm) == PackageManager.PERMISSION_GRANTED

    /** 바로 전화 걸기 (권한 없으면 다이얼 화면). 끝나면 사유 입력 창이 뜨도록 기억 */
    fun call(ctx: Context, number: String, studentIds: List<String>) {
        CallTracker.remember(ctx, studentIds, number)
        val uri = Uri.parse("tel:$number")
        val i = if (has(ctx, Manifest.permission.CALL_PHONE)) Intent(Intent.ACTION_CALL, uri) else Intent(Intent.ACTION_DIAL, uri)
        ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun sms(ctx: Context, number: String, body: String = "") {
        val i = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$number")).putExtra("sms_body", body)
        ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun lastCall(ctx: Context): LastCall? {
        if (!has(ctx, Manifest.permission.READ_CALL_LOG)) return null
        return try {
            ctx.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.DURATION, CallLog.Calls.TYPE),
                null, null, CallLog.Calls.DATE + " DESC",
            )?.use { c ->
                if (!c.moveToFirst()) null
                else {
                    val type = c.getInt(2)
                    LastCall(
                        number = Repo.normalizePhone(c.getString(0) ?: ""),
                        durationSec = c.getLong(1),
                        outgoing = type == CallLog.Calls.OUTGOING_TYPE,
                        missed = type == CallLog.Calls.MISSED_TYPE || type == CallLog.Calls.REJECTED_TYPE,
                    )
                }
            }
        } catch (e: Exception) { null }
    }
}
