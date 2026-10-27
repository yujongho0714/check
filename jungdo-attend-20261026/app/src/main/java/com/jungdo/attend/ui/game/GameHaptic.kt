package com.jungdo.attend.ui.game

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.jungdo.attend.data.Prefs

/** 버튼 진동. 진동 모터가 없는 태블릿/공기계에서는 아무 일도 안 일어나요. */
object GameHaptic {
    enum class Kind { TICK, SUCCESS, FAIL }

    @Volatile var enabled = true
    private var vib: Vibrator? = null

    fun init(ctx: Context) {
        val app = ctx.applicationContext
        vib = runCatching {
            if (Build.VERSION.SDK_INT >= 31) {
                app.getSystemService(VibratorManager::class.java)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                app.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        }.getOrNull()
        reload(app)
    }

    fun reload(ctx: Context) {
        enabled = Prefs(ctx.applicationContext).fxHaptic
    }

    fun pulse(kind: Kind) {
        if (!enabled) return
        val v = vib ?: return
        if (!v.hasVibrator()) return
        runCatching {
            val effect = when (kind) {
                Kind.TICK -> VibrationEffect.createOneShot(14, 90)
                Kind.SUCCESS -> VibrationEffect.createWaveform(longArrayOf(0, 22, 45, 34), -1)
                Kind.FAIL -> VibrationEffect.createWaveform(longArrayOf(0, 70, 55, 70), -1)
            }
            v.vibrate(effect)
        }
    }
}
