package com.jungdo.attend.ui.game

import android.content.Context

/** 소리 + 진동을 한 번에 켜고, 한 번에 울리는 입구 */
object GameFx {
    fun init(ctx: Context) {
        GameSound.init(ctx)
        GameHaptic.init(ctx)
    }

    fun reload(ctx: Context) {
        GameSound.reload(ctx)
        GameHaptic.reload(ctx)
    }

    /** 소리와 진동을 같이 */
    fun fire(sound: GameSound.Fx?, haptic: GameHaptic.Kind?) {
        if (sound != null) GameSound.play(sound)
        if (haptic != null) GameHaptic.pulse(haptic)
    }
}
