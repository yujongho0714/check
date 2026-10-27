package com.jungdo.attend.report

import com.jungdo.attend.data.Absence
import com.jungdo.attend.data.AttendEvent
import com.jungdo.attend.data.ClassTimes
import com.jungdo.attend.data.Days
import com.jungdo.attend.data.EventType
import com.jungdo.attend.data.Holidays
import com.jungdo.attend.data.Student
import com.jungdo.attend.data.StudentStatus
import com.jungdo.attend.data.gradeRank

/** 하루 칸: 출석한 부들(예: [3] 또는 [3,6]), 하원만 찍힌 날, 사유 결석 */
class MonthCell(val parts: List<Int>, val outOnly: Boolean, val excused: String?) {
    val attended get() = parts.isNotEmpty() || outOnly
    val text: String get() = when {
        parts.isNotEmpty() -> parts.joinToString("·")
        outOnly -> "○"
        excused != null -> "결"
        else -> ""
    }
}

class MonthRow(
    val no: Int,
    val student: Student,
    val cells: Map<Int, MonthCell>,
    val attended: Int,
    val excused: Int,
    val reasons: List<String>,       // "10/3 감기"
    val siblings: List<String>,
) {
    val note: String get() = buildList {
        if (student.status == StudentStatus.LEAVE) add("휴관" + student.statusReason.let { if (it.isBlank()) "" else "($it)" })
        if (student.status == StudentStatus.LEFT) add("퇴관" + (student.leftMonth.takeIf { it.length >= 7 }?.let { "(${it.substring(5, 7).toInt()}월)" } ?: ""))
        if (student.demo) add("시범단")
        if (siblings.isNotEmpty()) add("형제:" + siblings.joinToString(","))
        if (reasons.isNotEmpty()) add(reasons.joinToString(" / "))
    }.joinToString(" · ")
}

class MonthSheet(
    val month: String,
    val days: Int,
    val groups: List<Pair<String, List<MonthRow>>>,  // 학년별
    val offNames: Map<Int, String>,                  // 쉬는 날 → 이름("토","일" 또는 공휴일 이름)
    val openDays: Int,
    val partRanges: List<String>,
) {
    val rows get() = groups.flatMap { it.second }
    fun dayLabel(d: Int) = Holidays.weekdayKo(Days.dayOf(month, d))
    fun isOff(d: Int) = offNames.containsKey(d)
}

object MonthReport {
    fun build(month: String, students: List<Student>, events: List<AttendEvent>, absences: List<Absence>, ct: ClassTimes): MonthSheet {
        val days = Days.daysInMonth(month)
        val offNames = (1..days).mapNotNull { d ->
            val day = Days.dayOf(month, d)
            if (!Holidays.isOff(day)) null else d to (Holidays.name(day) ?: Holidays.weekdayKo(day))
        }.toMap()

        // 지난달 출석부는 그때 학년으로 (나이가 있는 원생은 그 해 기준으로 다시 계산)
        val inMonth = students.filter { it.inMonth(month) }.map { it.copy(grade = it.gradeAt(month)) }
        val bySib = inMonth.filter { it.siblingKey.isNotBlank() }.groupBy { it.siblingKey }
        val evBy = events.filter { it.day.startsWith(month) }.groupBy { it.studentId }
        val abBy = absences.filter { it.day.startsWith(month) }.groupBy { it.studentId }

        var no = 0
        val groups = inMonth.groupBy { it.grade.ifBlank { "기타" } }
            .toList().sortedWith(compareBy({ gradeRank(it.first) }, { it.first }))
            .map { (grade, list) ->
                grade to list.sortedWith(compareBy({ it.order }, { it.name })).map { s ->
                    val evs = evBy[s.id].orEmpty()
                    val abs = abBy[s.id].orEmpty().associateBy { it.day }
                    val cells = HashMap<Int, MonthCell>()
                    for (d in 1..days) {
                        val day = Days.dayOf(month, d)
                        val dayEv = evs.filter { it.day == day }
                        val parts = dayEv.filter { it.type == EventType.IN }.map { ct.partOf(it.clientTime) }.distinct().sorted()
                        val outOnly = parts.isEmpty() && dayEv.any { it.type == EventType.OUT }
                        val ex = abs[day]?.reason?.ifBlank { "사유 없음" }
                        if (parts.isNotEmpty() || outOnly || ex != null) cells[d] = MonthCell(parts, outOnly, ex)
                    }
                    val reasons = cells.toSortedMap().filter { !it.value.attended && it.value.excused != null }
                        .map { (d, c) -> "${month.substring(5).toInt()}/$d ${c.excused}" }
                    MonthRow(
                        no = ++no, student = s, cells = cells,
                        attended = cells.values.count { it.attended },
                        excused = cells.values.count { !it.attended && it.excused != null },
                        reasons = reasons,
                        siblings = bySib[s.siblingKey].orEmpty().filter { it.id != s.id }.map { it.name },
                    )
                }
            }
        return MonthSheet(month, days, groups, offNames, days - offNames.size, ClassTimes.PARTS.indices.map { "${it + 1}부 ${ct.rangeText(it + 1)}" })
    }
}
