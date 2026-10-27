package com.jungdo.attend.report

import com.jungdo.attend.data.Absence
import com.jungdo.attend.data.AttendEvent
import com.jungdo.attend.data.Holidays
import com.jungdo.attend.data.Repo
import com.jungdo.attend.data.Student
import kotlin.math.roundToInt

/** 퇴관 위험도 결과 */
data class Risk(
    val student: Student,
    val score: Int,           // 0~100
    val level: String,        // 위험 / 주의 / 안전 / 기록 부족
    val reasons: List<String>,
    val lastDay: String?,
    val recent: Int,          // 최근 2주 출석일
    val usualRate: Double,    // 평소 출석률 (수업일 대비)
)

/**
 * 퇴관 위험도 = 평소보다 덜 나오는 정도(최대 50) + 마지막 출석 후 빠진 정도(최대 40) + 출석 아주 적음(10)
 *  - 평소 = 최근 2주 이전 8주, 최근 = 최근 2주 (오늘 제외)
 *  - 주말·공휴일·휴관일, 부모님께 연락받은 '사유 결석'은 계산에서 뺌
 *  - 주 2~3회만 오는 아이도 '자기 평소' 기준으로 비교하므로 억울하게 높게 나오지 않음
 */
object RiskAnalyzer {
    const val LOOKBACK = 70

    fun analyze(students: List<Student>, events: List<AttendEvent>, absences: List<Absence>, today: String = Repo.today()): List<Risk> {
        val yesterday = Repo.shiftDay(today, -1)
        val recentStart = Repo.shiftDay(today, -14)
        val baseStart = Repo.shiftDay(today, -LOOKBACK)
        val dataStart = events.minOfOrNull { it.day }?.takeIf { it.isNotBlank() } ?: today
        val att = events.groupBy { it.studentId }.mapValues { e -> e.value.map { it.day }.toSet() }
        val exc = absences.groupBy { it.studentId }.mapValues { a -> a.value.map { it.day }.toSet() }

        fun openDays(from: String, to: String, excused: Set<String>): List<String> {
            if (from > to) return emptyList()
            val out = mutableListOf<String>(); var d = from; var guard = 0
            while (d <= to && guard++ < 200) { if (!Holidays.isOff(d) && d !in excused) out += d; d = Repo.shiftDay(d, 1) }
            return out
        }

        return students.filter { it.isActive }.map { s ->
            val days = att[s.id].orEmpty(); val ex = exc[s.id].orEmpty()
            val joinStart = if (s.joinedMonth.isNotBlank()) "${s.joinedMonth}-01" else dataStart
            val start = maxOf(baseStart, dataStart, joinStart)
            val baseOpen = openDays(start, Repo.shiftDay(recentStart, -1), ex)
            val recentOpen = openDays(maxOf(recentStart, start), yesterday, ex)
            val baseAtt = baseOpen.count { it in days }
            val recentAtt = recentOpen.count { it in days }
            val last = days.filter { it <= today }.maxOrNull()
            val gapOpen = openDays(if (last == null) start else Repo.shiftDay(last, 1), yesterday, ex).size
            val reasons = mutableListOf<String>()

            if (baseOpen.size < 5 && recentOpen.size < 5) {
                return@map Risk(s, 0, "기록 부족", listOf("출석 기록이 쌓이는 중 (2주 이상 필요)"), last, recentAtt, 0.0)
            }
            val usual = when {
                baseOpen.size >= 5 -> baseAtt.toDouble() / baseOpen.size
                else -> 0.6 // 평소 기록이 아직 없으면 주 3회 정도로 가정
            }
            val recentRate = if (recentOpen.isEmpty()) usual else recentAtt.toDouble() / recentOpen.size

            var score = 0.0
            if (usual > 0.05) {
                val ratio = (recentRate / usual).coerceAtMost(1.0)
                score += (1 - ratio) * 50
                if (ratio < 0.85) reasons += "최근 2주 ${recentAtt}일 출석 (평소의 ${(ratio * 100).roundToInt()}%)"
            }
            val gapScore = (gapOpen * maxOf(usual, 0.3) / 4.0).coerceAtMost(1.0) * 40
            score += gapScore
            if (last == null) reasons += "기록 기간 동안 출석 없음"
            else if (gapScore >= 15) reasons += "마지막 출석 ${last.substring(5).replace('-', '/')} (수업일 ${gapOpen}일째 안 옴)"
            if (recentOpen.size >= 5 && recentRate < 0.2) { score += 10; reasons += "최근 출석률 ${(recentRate * 100).roundToInt()}%" }
            val exRecent = ex.count { it >= recentStart }
            if (exRecent > 0) reasons += "사유 결석 ${exRecent}일 (계산에서 제외)"

            val sc = score.roundToInt().coerceIn(0, 100)
            Risk(s, sc, when { sc >= 60 -> "위험"; sc >= 30 -> "주의"; else -> "안전" }, reasons, last, recentAtt, usual)
        }.sortedByDescending { it.score }
    }
}
