package com.jungdo.attend.data

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * 공휴일·대체공휴일 (2026~2027 기본 내장) + 도장에서 직접 추가한 휴관일
 * 출처: 관공서의 공휴일에 관한 규정 (2026년 제헌절 공휴일 복원, 6/3 지방선거 포함)
 * 2028년 이후는 앱의 [설정 → 휴일 추가]에서 넣거나, 아래 표에 줄을 추가하면 됩니다.
 */
object Holidays {
    private val builtIn = mapOf(
        // 2026
        "2026-01-01" to "신정",
        "2026-02-16" to "설날 연휴", "2026-02-17" to "설날", "2026-02-18" to "설날 연휴",
        "2026-03-01" to "삼일절", "2026-03-02" to "대체공휴일",
        "2026-05-01" to "노동절",
        "2026-05-05" to "어린이날",
        "2026-05-24" to "부처님오신날", "2026-05-25" to "대체공휴일",
        "2026-06-03" to "지방선거",
        "2026-06-06" to "현충일",
        "2026-07-17" to "제헌절",
        "2026-08-15" to "광복절", "2026-08-17" to "대체공휴일",
        "2026-09-24" to "추석 연휴", "2026-09-25" to "추석", "2026-09-26" to "추석 연휴",
        "2026-10-03" to "개천절", "2026-10-05" to "대체공휴일",
        "2026-10-09" to "한글날",
        "2026-12-25" to "성탄절",
        // 2027
        "2027-01-01" to "신정",
        "2027-02-06" to "설날 연휴", "2027-02-07" to "설날", "2027-02-08" to "설날 연휴", "2027-02-09" to "대체공휴일",
        "2027-03-01" to "삼일절",
        "2027-05-01" to "노동절", "2027-05-03" to "대체공휴일",
        "2027-05-05" to "어린이날",
        "2027-05-13" to "부처님오신날",
        "2027-06-06" to "현충일",
        "2027-07-17" to "제헌절", "2027-07-19" to "대체공휴일",
        "2027-08-15" to "광복절", "2027-08-16" to "대체공휴일",
        "2027-09-14" to "추석 연휴", "2027-09-15" to "추석", "2027-09-16" to "추석 연휴",
        "2027-10-03" to "개천절", "2027-10-04" to "대체공휴일",
        "2027-10-09" to "한글날", "2027-10-11" to "대체공휴일",
        "2027-12-25" to "성탄절", "2027-12-27" to "대체공휴일",
    )

    /** 관장님이 추가한 날 (Firestore config/holidays). 값이 "수업" 이면 공휴일이어도 수업하는 날 */
    @Volatile var custom: Map<String, String> = emptyMap()
    const val OPEN = "수업"

    fun builtInName(day: String): String? = builtIn[day]

    /** 쉬는 날 이름 (주말은 null) */
    fun name(day: String): String? {
        custom[day]?.let { return if (it == OPEN) null else it }
        return builtIn[day]
    }

    fun dayOfWeek(day: String): Int { // 1=일 … 7=토
        val c = Calendar.getInstance()
        c.time = SimpleDateFormat("yyyy-MM-dd", Locale.KOREA).parse(day)!!
        return c.get(Calendar.DAY_OF_WEEK)
    }
    fun isWeekend(day: String) = dayOfWeek(day).let { it == Calendar.SATURDAY || it == Calendar.SUNDAY }

    /** 주말·공휴일·휴관일 → 출석부에 색칠, 미등원/위험도 계산에서 빠짐 */
    fun isOff(day: String): Boolean {
        if (custom[day] == OPEN) return false
        return isWeekend(day) || name(day) != null
    }

    fun weekdayKo(day: String) = "일월화수목금토"[dayOfWeek(day) - 1].toString()
}

/** 날짜 계산 도우미 */
object Days {
    private fun fmt() = SimpleDateFormat("yyyy-MM-dd", Locale.KOREA)
    fun month(day: String) = day.substring(0, 7)
    fun thisMonth() = month(Repo.today())
    fun daysInMonth(month: String): Int {
        val c = Calendar.getInstance()
        c.time = SimpleDateFormat("yyyy-MM", Locale.KOREA).parse(month)!!
        return c.getActualMaximum(Calendar.DAY_OF_MONTH)
    }
    fun dayOf(month: String, d: Int) = "%s-%02d".format(month, d)
    fun shiftMonth(month: String, delta: Int): String {
        val c = Calendar.getInstance()
        c.time = SimpleDateFormat("yyyy-MM", Locale.KOREA).parse(month)!!
        c.add(Calendar.MONTH, delta)
        return SimpleDateFormat("yyyy-MM", Locale.KOREA).format(c.time)
    }
    fun monthLabel(month: String) = "${month.substring(0, 4)}년 ${month.substring(5, 7).toInt()}월"
    /** from ~ to (포함) 날짜 목록 */
    fun range(from: String, to: String): List<String> {
        val out = mutableListOf<String>()
        var d = from
        var guard = 0
        while (d <= to && guard++ < 400) { out += d; d = Repo.shiftDay(d, 1) }
        return out
    }
    fun between(a: String, b: String): Int { // b - a (일)
        val f = fmt()
        return (((f.parse(b)!!.time - f.parse(a)!!.time) + 43_200_000L) / 86_400_000L).toInt()
    }
}

/**
 * 출석 시간 → 부 자동 판정 (관장님 폰에 저장, [설정]에서 수정)
 * 각 부의 '시작 경계' 시각. 경계는 앞 부가 끝나는 시각이라 쉬는 시간에 온 아이는 다음 부로 잡힙니다.
 * 기본값: 1부 12:00~ / 2부 14:20~ / 3부 15:20~ / 4부 16:20~(수업 16:30) / 5부 17:20~(수업 17:30) / 6부 18:20~ / 7부 19:30~
 */
class ClassTimes(ctx: Context) {
    private val sp = ctx.getSharedPreferences("jungdo_attend", Context.MODE_PRIVATE)

    var starts: List<String>
        get() = (sp.getString("classStarts", null) ?: DEFAULT).split(",").map { it.trim() }.filter { it.isNotBlank() }
        set(v) = sp.edit().putString("classStarts", v.joinToString(",")).apply()

    private fun minutes(hm: String): Int = hm.split(":").let { (it[0].toIntOrNull() ?: 0) * 60 + (it.getOrNull(1)?.toIntOrNull() ?: 0) }

    /** 1부부터 시작. 1부 시작 전에 온 아이도 1부 */
    fun partOf(ms: Long): Int {
        val c = Calendar.getInstance().apply { timeInMillis = ms }
        val m = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
        var part = 1
        starts.forEachIndexed { i, s -> if (m >= minutes(s)) part = i + 1 }
        return part
    }
    fun label(part: Int) = "${part}부"
    fun rangeText(part: Int): String {
        val s = starts
        val a = s.getOrNull(part - 1) ?: return ""
        val b = s.getOrNull(part) ?: ""
        return "$a~$b"
    }
    /** 해당 부가 시작하고 몇 분 지났는지 판단할 때 쓰는 시작 분 */
    fun startMinutes(part: Int) = starts.getOrNull(part - 1)?.let(::minutes) ?: 0

    companion object {
        const val DEFAULT = "12:00,14:20,15:20,16:20,17:20,18:20,19:30"
        val PARTS = (1..7).map { "${it}부" }
    }
}

/** 월별 계절 색 (진한색, 연한색) — 출석부 제목/머리글/줄무늬에 사용 */
object Season {
    data class Colors(val name: String, val dark: String, val light: String, val emoji: String)
    fun of(month: String): Colors = when (month.substring(5, 7).toInt()) {
        1 -> Colors("한겨울", "3949AB", "E8EAF6", "❄️")
        2 -> Colors("늦겨울", "5E35B1", "EDE7F6", "⛄")
        3 -> Colors("초봄 벚꽃", "D81B60", "FCE4EC", "🌸")
        4 -> Colors("봄 개나리", "F9A825", "FFF8E1", "🌼")
        5 -> Colors("늦봄 신록", "7CB342", "F1F8E9", "🌱")
        6 -> Colors("초여름", "2E7D32", "E8F5E9", "🌿")
        7 -> Colors("한여름 바다", "0288D1", "E1F5FE", "🌊")
        8 -> Colors("늦여름", "00838F", "E0F7FA", "🍉")
        9 -> Colors("초가을", "EF6C00", "FFF3E0", "🌾")
        10 -> Colors("가을 단풍", "D84315", "FBE9E7", "🍁")
        11 -> Colors("늦가을 낙엽", "6D4C41", "EFEBE9", "🍂")
        else -> Colors("초겨울 성탄", "C62828", "FFEBEE", "🎄")
    }
    const val OFF_FILL = "CCFFFF"  // 주말·공휴일 (기존 정도 출석부와 같은 하늘색)
}
