package com.jungdo.attend.data

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.pow

/** 등원/하원 한 번 누른 결과 (키오스크 완료 화면에 보여줌) */
data class AwardResult(
    val name: String,
    val type: String,
    val gained: Int,        // 이번에 받은 경험치 (이미 오늘 받았으면 0)
    val bonus: Int,         // 그중 연속 출석 보너스
    val streak: Int,        // 연속 출석 일수
    val expBefore: Int,
    val expAfter: Int,
    val gemId: String? = null,     // 이번에 얻은 보석
    val plateId: String? = null,   // 이번에 얻은 네임플레이트
    val equipped: String = "",     // 지금 쓰고 있는 네임플레이트 (이름 뒤에 깔아 줌)
    val prize: PrizeWin? = null,   // 이번에 당첨된 상품
) {
    val levelBefore: Int get() = Rank.levelOf(expBefore)
    val level: Int get() = Rank.levelOf(expAfter)
    val leveledUp: Boolean get() = level > levelBefore
}

class RankPlan(val result: AwardResult, val fields: Map<String, Any>)

/**
 * 계급(레벨) · 경험치
 *  - 레벨 1 ~ 866 (계급 이미지 866장). 이미지는 assets/rank/001.png … 866.png
 *  - 등원 +10, 하원 +10 (각각 하루 한 번만). 그날 첫 출석에는 연속 출석 보너스 (연속 2일째부터 +2씩, 최대 +20)
 *  - 연속 출석 = 수업일(주말·공휴일·휴관일 제외)을 하루도 안 빠지고 나온 날 수
 *  - 총 18,000 경험치면 최고 레벨. 성실하게(수업일의 약 85%) 다니면 약 3년, 개근이면 약 1년 9개월
 *  - 레벨이 낮을수록 빨리 오르고(처음엔 며칠마다 여러 단계), 높을수록 천천히 오릅니다
 */
object Rank {
    const val MAX_LEVEL = 866
    const val MAX_EXP = 18_000
    const val EXP_IN = 10
    const val EXP_OUT = 10
    const val STREAK_STEP = 2
    const val STREAK_BONUS_DAYS = 10
    private const val CURVE = 1.3

    /** 그 레벨이 되는 데 필요한 누적 경험치 */
    fun expFor(level: Int): Int = when {
        level <= 1 -> 0
        level >= MAX_LEVEL -> MAX_EXP
        else -> ceil(MAX_EXP * ((level - 1).toDouble() / (MAX_LEVEL - 1)).pow(CURVE)).toInt()
    }

    fun levelOf(exp: Int): Int {
        if (exp <= 0) return 1
        if (exp >= MAX_EXP) return MAX_LEVEL
        var l = (1 + floor((MAX_LEVEL - 1) * (exp.toDouble() / MAX_EXP).pow(1.0 / CURVE))).toInt().coerceIn(1, MAX_LEVEL)
        while (l < MAX_LEVEL && expFor(l + 1) <= exp) l++
        while (l > 1 && expFor(l) > exp) l--
        return l
    }

    /** 다음 레벨까지 얼마나 찼는지 0.0 ~ 1.0 */
    fun progress(exp: Int): Float {
        val l = levelOf(exp)
        if (l >= MAX_LEVEL) return 1f
        val a = expFor(l); val b = expFor(l + 1)
        return if (b <= a) 1f else ((exp - a).toFloat() / (b - a)).coerceIn(0f, 1f)
    }

    /** 직전 수업일 (주말·공휴일·휴관일은 건너뜀) */
    fun prevClassDay(day: String): String {
        var d = Repo.shiftDay(day, -1)
        var n = 0
        while (Holidays.isOff(d) && n < 20) { d = Repo.shiftDay(d, -1); n++ }
        return d
    }

    fun plan(s: Student, type: String, today: String): RankPlan {
        val off = Holidays.isOff(today)
        val already = if (type == EventType.IN) s.inDay == today else s.outDay == today
        if (already) return RankPlan(AwardResult(s.name, type, 0, 0, s.streak, s.exp, s.exp), emptyMap())

        val firstToday = s.streakDay != today
        var streak = s.streak
        if (firstToday && !off) {
            streak = if (s.streakDay.isNotBlank() && s.streakDay == prevClassDay(today)) s.streak + 1 else 1
        }
        val base = if (type == EventType.IN) EXP_IN else EXP_OUT
        val bonus = if (firstToday && !off) minOf(maxOf(streak - 1, 0), STREAK_BONUS_DAYS) * STREAK_STEP else 0
        val after = (s.exp + base + bonus).coerceAtMost(MAX_EXP)

        val f = HashMap<String, Any>()
        f["exp"] = after
        f["streak"] = streak
        if (!off) f["streakDay"] = today
        f[if (type == EventType.IN) "inDay" else "outDay"] = today
        return RankPlan(AwardResult(s.name, type, after - s.exp, bonus, streak, s.exp, after), f)
    }
}
