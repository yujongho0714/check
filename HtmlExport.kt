package com.jungdo.attend.report

import com.jungdo.attend.data.Days
import com.jungdo.attend.data.Season

/** 월간 출석부 → HTML (앱 화면 표시 + 파일 저장/공유/인쓰기 겸용) */
object HtmlExport {
    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    fun build(sheet: MonthSheet, risks: List<Risk>, dojang: String = com.jungdo.attend.data.DojangInfo.NAME): String {
        val se = Season.of(sheet.month)
        val off = "#" + Season.OFF_FILL
        val title = "${Days.monthLabel(sheet.month)} 출석부"
        val sb = StringBuilder()
        sb.append("""<!doctype html><html lang="ko"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>${esc(title)} · ${esc(dojang)}</title>
<style>
:root{--dark:#${se.dark};--light:#${se.light};--off:$off}
*{box-sizing:border-box}
body{margin:0;padding:12px;font-family:'Malgun Gothic','Apple SD Gothic Neo',sans-serif;background:var(--light);color:#1f2937}
h1{margin:0 0 4px;font-size:22px;color:var(--dark)}
.sub{color:#555;font-size:13px;margin-bottom:10px}
.wrap{overflow:auto;background:#fff;border:2px solid var(--dark);border-radius:8px}
table{border-collapse:collapse;font-size:12px;white-space:nowrap}
th,td{border:1px solid #c9ced6;padding:3px 4px;text-align:center;min-width:22px}
thead th{background:var(--dark);color:#fff;position:sticky;top:0;z-index:2}
thead th.off{background:var(--off);color:#0f172a}
thead tr.wd th{top:22px;font-weight:normal;font-size:11px}
td.off{background:var(--off)}
td.name,th.name{text-align:left;position:sticky;left:0;background:#fff;z-index:1;min-width:64px}
thead th.name{z-index:3;background:var(--dark)}
tr.grp td{background:var(--light);color:var(--dark);font-weight:bold;text-align:left;font-size:13px}
td.p{color:#1d4ed8;font-weight:bold}
td.x{color:#dc2626;font-weight:bold;background:#fee2e2}
tr.leave td{color:#9ca3af}
td.note{text-align:left;max-width:360px;white-space:normal;font-size:11px}
td.sum{font-weight:bold}
.legend{margin:10px 0;font-size:12px;display:flex;flex-wrap:wrap;gap:6px 14px}
.legend span b{display:inline-block;width:14px;height:14px;vertical-align:middle;border:1px solid #999;margin-right:4px}
h2{font-size:16px;color:var(--dark);margin:18px 0 6px}
.risk td{text-align:left;white-space:normal}
.lv-위험{color:#fff;background:#dc2626;font-weight:bold}
.lv-주의{color:#fff;background:#ea580c;font-weight:bold}
@media print{body{background:#fff;padding:0}.wrap{overflow:visible;border:none}@page{size:A4 landscape;margin:8mm}thead th{position:static}td.name,th.name{position:static}}
</style></head><body>
""")
        sb.append("<h1>${se.emoji} ${esc(title)} <small style=\"font-size:14px;color:#555\">· ${esc(dojang)} · ${esc(se.name)}</small></h1>")
        sb.append("<div class=\"sub\">수업일 ${sheet.openDays}일 · 원생 ${sheet.rows.size}명 · 칸의 숫자 = 등원한 부 (등원 시각으로 자동 판정) · ○ 하원만 기록 · <b style=\"color:#dc2626\">결</b> 사유 결석</div>")

        sb.append("<div class=\"wrap\"><table><thead><tr><th>No</th><th class=\"name\">이름</th><th>학년</th><th>출석</th><th>결석</th>")
        for (d in 1..sheet.days) {
            val offName = sheet.offNames[d]
            sb.append("<th class=\"${if (offName != null) "off" else ""}\" title=\"${esc(offName ?: "")}\">$d</th>")
        }
        sb.append("<th>비고 (사유·형제)</th></tr><tr class=\"wd\"><th></th><th class=\"name\"></th><th></th><th></th><th></th>")
        for (d in 1..sheet.days) {
            val offName = sheet.offNames[d]
            val label = if (offName != null && offName.length > 1) offName.take(2) else sheet.dayLabel(d)
            sb.append("<th class=\"${if (offName != null) "off" else ""}\">${esc(label)}</th>")
        }
        sb.append("<th></th></tr></thead><tbody>")

        for ((grade, rows) in sheet.groups) {
            sb.append("<tr class=\"grp\"><td colspan=\"${sheet.days + 6}\">${esc(grade)} (${rows.size}명)</td></tr>")
            for (r in rows) {
                val leave = r.student.status != "ACTIVE"
                sb.append("<tr class=\"${if (leave) "leave" else ""}\"><td>${r.no}</td><td class=\"name\">${esc(r.student.name)}${if (r.student.demo) " <span title=\"시범단\">★</span>" else ""}</td><td>${esc(r.student.grade)}</td>")
                sb.append("<td class=\"sum\">${r.attended}</td><td class=\"sum\">${if (r.excused > 0) r.excused.toString() else ""}</td>")
                for (d in 1..sheet.days) {
                    val c = r.cells[d]
                    val cls = buildList {
                        if (sheet.isOff(d)) add("off")
                        if (c?.attended == true) add("p") else if (c?.excused != null) add("x")
                    }.joinToString(" ")
                    val tip = c?.excused?.let { " title=\"${esc(it)}\"" } ?: ""
                    sb.append("<td class=\"$cls\"$tip>${esc(c?.text ?: "")}</td>")
                }
                sb.append("<td class=\"note\">${esc(r.note)}</td></tr>")
            }
        }
        sb.append("</tbody></table></div>")

        sb.append("<div class=\"legend\"><span><b style=\"background:$off\"></b>주말·공휴일·휴관일</span>")
        sb.append("<span><b style=\"background:#fee2e2\"></b>사유 결석</span><span>★ 시범단</span>")
        sheet.partRanges.forEach { sb.append("<span>${esc(it)}</span>") }
        sb.append("</div>")

        val holidays = sheet.offNames.filter { it.value.length > 1 }
        if (holidays.isNotEmpty()) {
            sb.append("<div class=\"sub\">이 달의 휴일: ")
            sb.append(holidays.entries.joinToString(", ") { "${it.key}일 ${esc(it.value)}" })
            sb.append("</div>")
        }

        val flagged = risks.filter { it.level == "위험" || it.level == "주의" }
        if (flagged.isNotEmpty()) {
            sb.append("<h2>퇴관 위험 원생 (${flagged.size}명)</h2><div class=\"wrap\"><table class=\"risk\"><thead><tr><th>단계</th><th>점수</th><th>이름</th><th>학년</th><th>이유</th></tr></thead><tbody>")
            flagged.forEach { k ->
                sb.append("<tr><td class=\"lv-${k.level}\">${k.level}</td><td>${k.score}</td><td>${esc(k.student.name)}</td><td>${esc(k.student.grade)}</td><td>${esc(k.reasons.joinToString(" · "))}</td></tr>")
            }
            sb.append("</tbody></table></div>")
        }
        sb.append("</body></html>")
        return sb.toString()
    }
}
