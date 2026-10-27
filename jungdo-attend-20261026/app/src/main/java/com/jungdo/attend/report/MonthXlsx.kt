package com.jungdo.attend.report

import com.jungdo.attend.data.Days
import com.jungdo.attend.data.Season
import com.jungdo.attend.data.StudentStatus
import com.jungdo.attend.report.XlsxWriter.Style

/** 월간 출석부 → 엑셀(.xlsx). 시트: 출석부 / 결석사유 / 퇴관위험 */
object MonthXlsx {
    fun build(sheet: MonthSheet, risks: List<Risk>, dojang: String = com.jungdo.attend.data.DojangInfo.NAME): ByteArray {
        val se = Season.of(sheet.month)
        val off = Season.OFF_FILL
        val w = XlsxWriter()
        val ms = w.sheet("${sheet.month.substring(5).toInt()}월 출석부")
        val lastCol = 5 + sheet.days // 0:No 1:이름 2:학년 3:출석 4:결석 5..:일 마지막:비고

        // 제목 (계절색)
        ms.set(0, 0, "${se.emoji} ${Days.monthLabel(sheet.month)} 출석부 · $dojang · ${se.name}", Style(bold = true, size = 16, color = "FFFFFF", fill = se.dark, center = false, border = false))
        for (c in 1..lastCol) ms.set(0, c, "", Style(fill = se.dark, border = false))
        ms.merge(0, 0, 0, lastCol); ms.height(0, 30.0)
        ms.set(1, 0, "수업일 ${sheet.openDays}일 · 원생 ${sheet.rows.size}명 · 숫자 = 등원한 부 · ○ 하원만 기록 · 결 = 사유 결석 · " + sheet.partRanges.joinToString("  "),
            Style(size = 9, color = "444444", center = false, border = false))
        ms.merge(1, 0, 1, lastCol)

        // 머리글 2줄 (날짜 / 요일·공휴일)
        val head = Style(bold = true, color = "FFFFFF", fill = se.dark)
        val headOff = Style(bold = true, color = "0F172A", fill = off)
        listOf("No", "이름", "학년", "출석", "결석").forEachIndexed { i, t -> ms.set(2, i, t, head); ms.set(3, i, "", head); ms.merge(2, i, 3, i) }
        for (d in 1..sheet.days) {
            val offName = sheet.offNames[d]
            val st = if (offName != null) headOff else head
            ms.set(2, 4 + d, d, st)
            ms.set(3, 4 + d, if (offName != null && offName.length > 1) offName.take(2) else sheet.dayLabel(d), st.copy(bold = false, size = 8))
        }
        ms.set(2, lastCol, "비고 (사유·형제)", head); ms.set(3, lastCol, "", head); ms.merge(2, lastCol, 3, lastCol)

        // 본문
        val base = Style()
        val baseOff = Style(fill = off)
        val present = Style(bold = true, color = "1D4ED8")
        val presentOff = present.copy(fill = off)
        val excused = Style(bold = true, color = "DC2626", fill = "FEE2E2")
        val grey = Style(color = "9CA3AF")
        val group = Style(bold = true, size = 11, color = se.dark, fill = se.light, center = false)
        var r = 4
        for ((grade, rows) in sheet.groups) {
            ms.set(r, 0, "$grade (${rows.size}명)", group)
            for (c in 1..lastCol) ms.set(r, c, "", group)
            ms.merge(r, 0, r, lastCol); r++
            for (row in rows) {
                val inactive = row.student.status != StudentStatus.ACTIVE
                val nameSt = if (inactive) grey.copy(center = false) else base.copy(center = false, bold = true)
                ms.set(r, 0, row.no, base)
                ms.set(r, 1, row.student.name + if (row.student.demo) " ★" else "", nameSt)
                ms.set(r, 2, row.student.grade, base)
                ms.set(r, 3, row.attended, base.copy(bold = true))
                ms.set(r, 4, if (row.excused > 0) row.excused else "", base.copy(bold = true, color = "DC2626"))
                for (d in 1..sheet.days) {
                    val c = row.cells[d]; val isOff = sheet.isOff(d)
                    val st = when {
                        c?.attended == true -> if (isOff) presentOff else present
                        c?.excused != null -> excused
                        else -> if (isOff) baseOff else base
                    }
                    ms.set(r, 4 + d, c?.text ?: "", st)
                }
                ms.set(r, lastCol, row.note, Style(size = 9, center = false, wrap = false))
                r++
            }
        }
        ms.width(0, 4.0); ms.width(1, 10.0); ms.width(2, 7.0); ms.width(3, 5.0); ms.width(4, 5.0)
        for (d in 1..sheet.days) ms.width(4 + d, 3.6)
        ms.width(lastCol, 40.0)
        ms.freeze(4, 2)

        // 결석 사유
        val rs = w.sheet("결석사유"); rs.landscape = false
        listOf("날짜", "이름", "학년", "사유").forEachIndexed { i, t -> rs.set(0, i, t, head) }
        var rr = 1
        sheet.rows.flatMap { row -> row.cells.filter { !it.value.attended && it.value.excused != null }.map { Triple(it.key, row, it.value.excused!!) } }
            .sortedWith(compareBy({ it.first }, { it.second.student.name }))
            .forEach { (d, row, why) ->
                rs.set(rr, 0, "${sheet.month.substring(5).toInt()}/$d (${sheet.dayLabel(d)})", base)
                rs.set(rr, 1, row.student.name, base); rs.set(rr, 2, row.student.grade, base); rs.set(rr, 3, why, base.copy(center = false)); rr++
            }
        rs.width(0, 12.0); rs.width(1, 10.0); rs.width(2, 8.0); rs.width(3, 50.0); rs.freeze(1, 0)

        // 퇴관 위험
        val ks = w.sheet("퇴관위험"); ks.landscape = false
        listOf("단계", "점수", "이름", "학년", "최근2주 출석", "평소 출석률", "마지막 출석", "이유").forEachIndexed { i, t -> ks.set(0, i, t, head) }
        risks.forEachIndexed { i, k ->
            val lv = when (k.level) { "위험" -> Style(bold = true, color = "FFFFFF", fill = "DC2626"); "주의" -> Style(bold = true, color = "FFFFFF", fill = "EA580C"); else -> base }
            ks.set(i + 1, 0, k.level, lv); ks.set(i + 1, 1, k.score, base); ks.set(i + 1, 2, k.student.name, base); ks.set(i + 1, 3, k.student.grade, base)
            ks.set(i + 1, 4, k.recent, base); ks.set(i + 1, 5, "${(k.usualRate * 100).toInt()}%", base)
            ks.set(i + 1, 6, k.lastDay ?: "-", base); ks.set(i + 1, 7, k.reasons.joinToString(" · "), base.copy(center = false))
        }
        ks.width(0, 8.0); ks.width(1, 6.0); ks.width(2, 10.0); ks.width(3, 8.0); ks.width(4, 11.0); ks.width(5, 11.0); ks.width(6, 12.0); ks.width(7, 60.0); ks.freeze(1, 0)

        return w.bytes()
    }
}
