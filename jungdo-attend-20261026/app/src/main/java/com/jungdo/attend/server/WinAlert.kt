package com.jungdo.attend.server

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.jungdo.attend.MainActivity
import com.jungdo.attend.data.Win
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * 상품 당첨 알림 (문자 폰).
 *  - 원생이 상품에 당첨되면 소리·진동 알림이 뜸 → 누르면 앱의 [당첨·지급] 탭이 열리고 그 건의 "지급했나요?" 확인창이 바로 뜸
 *  - 알림에서 바로 지급되지는 않아요 (잘못 눌러 지급 처리되는 것 방지). 확인창에서 [지급 완료] 를 눌러야 지급.
 *  - 지급이 끝나면(어느 기기에서든) 그 알림은 자동으로 사라짐
 */
object WinAlert {
    const val CHANNEL = "prize_win"
    const val EXTRA_WIN = "openWinId"

    /** 알림을 눌러 들어온 당첨 id — 당첨·지급 탭이 읽고 확인창을 띄운 뒤 비움 */
    val openWin = MutableStateFlow<String?>(null)

    private fun notiId(winId: String) = 20_000 + (winId.hashCode() and 0xffff)

    fun ensureChannel(ctx: Context) {
        val ch = NotificationChannel(CHANNEL, "상품 당첨 알림", NotificationManager.IMPORTANCE_HIGH)
        ch.enableVibration(true)
        ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    fun notify(ctx: Context, w: Win) {
        ensureChannel(ctx)
        val open = Intent(ctx, MainActivity::class.java)
            .putExtra(EXTRA_WIN, w.id)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pi = PendingIntent.getActivity(ctx, notiId(w.id), open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val who = w.name + if (w.grade.isNotBlank()) " (${w.grade})" else ""
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(android.R.drawable.star_on)
            .setContentTitle("🎁 $who 상품 당첨!")
            .setContentText("${w.prizeName} · 눌러서 확인하고 지급하기")
            .setStyle(NotificationCompat.BigTextStyle().bigText("${w.prizeName}\n눌러서 확인하고 지급하기"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        ctx.getSystemService(NotificationManager::class.java).notify(notiId(w.id), n)
    }

    fun cancel(ctx: Context, winId: String) {
        ctx.getSystemService(NotificationManager::class.java).cancel(notiId(winId))
    }
}
