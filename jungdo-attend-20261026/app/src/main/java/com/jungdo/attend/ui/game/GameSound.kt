package com.jungdo.attend.ui.game

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.jungdo.attend.R
import com.jungdo.attend.data.Prefs
import java.util.Collections

/**
 * 게임 효과음. res/raw 의 짧은 wav 를 SoundPool 로 바로 재생해요 (지연 거의 없음).
 *  - 앱을 켤 때 GameFx.init(context) 한 번만 부르면 돼요.
 *  - 소리 파일이 아직 안 읽혔거나 꺼져 있으면 조용히 넘어가요 (앱이 멈추지 않음).
 */
object GameSound {
    enum class Fx(val res: Int) {
        TAP(R.raw.fx_tap),         // 일반 버튼
        OK(R.raw.fx_ok),           // 확인/저장
        IN(R.raw.fx_in),           // 등원
        OUT(R.raw.fx_out),         // 하원
        WARN(R.raw.fx_warn),       // 중복/주의
        FAIL(R.raw.fx_fail),       // 실패
        LEVELUP(R.raw.fx_levelup), // 레벨 업
    }

    @Volatile var enabled = true
    @Volatile var volume = 0.8f // 0.0 ~ 1.0

    private var pool: SoundPool? = null
    private val ids = HashMap<Fx, Int>()
    private val loaded: MutableSet<Int> = Collections.synchronizedSet(HashSet())

    fun init(ctx: Context) {
        if (pool != null) return
        val app = ctx.applicationContext
        val sp = SoundPool.Builder()
            .setMaxStreams(4)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .build()
        sp.setOnLoadCompleteListener { _, id, status -> if (status == 0) loaded.add(id) }
        Fx.values().forEach { fx -> ids[fx] = sp.load(app, fx.res, 1) }
        pool = sp
        reload(app)
    }

    /** 설정(켜기/크기)을 다시 읽어요 */
    fun reload(ctx: Context) {
        val p = Prefs(ctx.applicationContext)
        enabled = p.fxSound
        volume = p.fxVolume / 100f
    }

    fun play(fx: Fx) {
        if (!enabled || volume <= 0f) return
        val id = ids[fx] ?: return
        if (id !in loaded) return
        pool?.play(id, volume, volume, 1, 0, 1f)
    }
}
