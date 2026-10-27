package com.jungdo.attend.server

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.jungdo.attend.data.Prefs

/** 관장님 폰이 재부팅되면 문자 서버를 자동으로 다시 켬 (켜둔 상태였을 때만) */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val p = Prefs(context)
        if (p.mode == Prefs.MODE_SERVER && p.serverEnabled) {
            runCatching { ContextCompat.startForegroundService(context, Intent(context, SmsService::class.java)) }
        }
    }
}
