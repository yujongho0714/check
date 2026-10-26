package com.jungdo.attend.importer

import com.jungdo.attend.data.Days
import com.jungdo.attend.data.Repo
import com.jungdo.attend.data.Student
import com.jungdo.attend.data.StudentStatus
import com.google.firebase.firestore.SetOptions

/** 수련생 현황표에서 읽은 원생 한 명 */
data class RosterEntry(val name: String, val grade: String, val demo: Boolean, val leave: Boolean, val leaveReason: String, val order: Int)

data class RosterResult(
    val sheetName: String,
    val entries: List<RosterEntry>,
    val siblingGroups: List<List<String>>,  // 형제명단: 세로로 묶인 이름들
    val leftReasons: Map<String, String>,   // 퇴관 명단 (이름 → 사유)
)

/**
 * '정도 태권도 2관 수련생 현황' 엑셀 읽기
 *  - 학년 머리글(유치부, 1학년 … 중학생) 아래 이름들 = 재원생
 *  - 회색 칸 = 시범단
 *  - '휴관 / 휴관사유' 표 = 휴관,  '퇴관 / 퇴관사유' 표 = 퇴관 사유
 *  - '형제명단' 표: 번호 아래 세로로 적힌 이름끼리 형제 (다른 시트에 있어도 찾음)
 */
object RosterParser {
    private val NAME = Regex("^([가-힣]{2,4})(\\(\\d+\\))?$")
    private val GRADE = Regex("^(유치부|\\d학년|중학생|고등학생|중고등|성인|\\d~\\d세|[\\d/]+/중고등)$")
    private val STOP = Regex("형제|퇴관|휴관|총|합계|사유|인원")

    fun cleanName(t: String): String? = NAME.find(t.trim())?.groupValues?.get(1)

    private fun isGray(rgb: String?): Boolean {
        if (rgb == null || rgb.length != 6) return false
        val r = rgb.substring(0, 2).toInt(16); val g = rgb.substring(2, 4).toInt(16); val b = rgb.substring(4, 6).toInt(16)
        return maxOf(r, g, b) - minOf(r, g, b) < 16 && r in 0x60..0xDF
    }

    /** 학년 머리글 줄 찾기 → (행, 학년 열들). 아래에 이름이 가장 많은 줄을 고름 */
    private fun findHeader(s: XSheet): Pair<Int, List<Int>>? {
        var best: Pair<Int, List<Int>>? = null; var bestScore = 0
        for (r in 0..s.maxRow) {
            val cols = (0..s.maxCol).filter { GRADE.matches(s.text(r, it)) }
            if (cols.size < 2) continue
            val score = cols.sumOf { c -> (r + 1..minOf(s.maxRow, r + 40)).count { cleanName(s.text(it, c)) != null } }
            if (score > bestScore) { bestScore = score; best = r to cols }
        }
        return best
    }

    /** 이 엑셀이 '수련생 현황' 모양(학년 머리글 + 아래 이름들)인지 */
    fun hasGradeHeader(sheets: List<XSheet>): Boolean = sheets.any { findHeader(it) != null }

    fun gradeSheetIndex(sheets: List<XSheet>): Int = sheets.indexOfFirst { findHeader(it) != null }.coerceAtLeast(0)

    /** "휴관"+"사유" 같은 작은 표: 이름 → 사유 */
    private fun namedList(s: XSheet, title: String): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        for (r in 0..s.maxRow) for (c in 0..s.maxCol) {
            if (s.text(r, c) != title || !s.text(r, c + 1).contains("사유")) continue
            var blanks = 0
            for (rr in r + 1..s.maxRow) {
                val n = cleanName(s.text(rr, c))
                if (n == null) { if (++blanks >= 2) break; continue }
                blanks = 0; out[n] = s.text(rr, c + 1)
            }
        }
        return out
    }

    private fun siblings(s: XSheet): List<List<String>> {
        for (r in 0..s.maxRow) for (c in 0..s.maxCol) {
            if (!s.text(r, c).contains("형제")) continue
            // 바로 아래 줄의 번호(1,2,3…)가 있는 열들
            val numRow = r + 1
            var start = c
            while (start > 0 && s.text(numRow, start - 1).toIntOrNull() != null) start--
            val cols = mutableListOf<Int>(); var cc = start
            while (cc <= s.maxCol && s.text(numRow, cc).toIntOrNull() != null) { cols += cc; cc++ }
            if (cols.isEmpty()) continue
            val groups = cols.map { col ->
                val names = mutableListOf<String>()
                var rr = numRow + 1
                while (rr <= s.maxRow) { val n = cleanName(s.text(rr, col)) ?: break; names += n; rr++ }
                names
            }.filter { it.size >= 2 }
            if (groups.isNotEmpty()) return groups
        }
        return emptyList()
    }

    fun parse(sheets: List<XSheet>, index: Int): RosterResult {
        val s = sheets[index]
        val (hr, cols) = findHeader(s) ?: throw IllegalArgumentException("'${s.name}' 시트에서 학년 머리글(유치부, 1학년 …)을 못 찾았어요.")
        val leaves = namedList(s, "휴관")
        val left = namedList(s, "퇴관")
        val entries = mutableListOf<RosterEntry>()
        cols.forEachIndexed { ci, c ->
            val grade = s.text(hr, c)
            var blanks = 0
            for (r in hr + 1..s.maxRow) {
                val t = s.text(r, c)
                if (t.isNotEmpty() && STOP.containsMatchIn(t)) break
                val n = cleanName(t)
                if (n == null) { if (++blanks >= 15) break; continue }
                blanks = 0
                val fill = s.cell(r, c)?.fill
                val isLeave = n in leaves
                entries += RosterEntry(n, grade, demo = isGray(fill), leave = isLeave, leaveReason = leaves[n] ?: "", order = ci * 1000 + r)
            }
        }
        // 형제명단: 같은 시트 → 다른 시트 순서로 찾기
        val sib = siblings(s).ifEmpty { sheets.firstNotNullOfOrNull { sh -> siblings(sh).ifEmpty { null } } ?: emptyList() }
        return RosterResult(s.name, entries, sib, left)
    }
}

/** 불러오기 결과 미리보기 + 적용 */
class ImportPlan(
    val finalStudents: List<Student>,   // 저장할 원생 (id 비어 있으면 신규)
    val added: List<String>,
    val reactivated: List<String>,
    val leftNow: List<String>,
    val onLeave: List<String>,
    val siblingGroups: Int,
    val withPhone: Int,
    val noPhone: List<String>,
    val duplicates: List<String>,
    val phoneMatched: Int,
    val tableMatched: Int = 0,
    val tableIgnored: List<String> = emptyList(),
) {
    fun summary(): String = buildString {
        appendLine("재원생 ${finalStudents.count { it.status != StudentStatus.LEFT }}명 (휴관 ${onLeave.size}명)")
        if (added.isNotEmpty()) appendLine("• 새로 등록 ${added.size}명: ${added.joinToString(", ")}")
        if (reactivated.isNotEmpty()) appendLine("• 복관 ${reactivated.size}명: ${reactivated.joinToString(", ")}")
        if (leftNow.isNotEmpty()) appendLine("• 퇴관 처리 ${leftNow.size}명 (이번 달부터 출석부·출석체크에서 빠짐): ${leftNow.joinToString(", ")}")
        if (onLeave.isNotEmpty()) appendLine("• 휴관: ${onLeave.joinToString(", ")}")
        appendLine("• 형제 ${siblingGroups}팀 (형제끼리 학부모 번호 공유)")
        if (tableMatched > 0) appendLine("• 출결 연락처 명단에서 번호·출결번호 넣음: ${tableMatched}명")
        if (tableIgnored.isNotEmpty()) appendLine("• 출결 명단에는 있지만 현황표에 없어서 제외: ${tableIgnored.joinToString(", ")}")
        if (phoneMatched > 0) appendLine("• 휴대폰 연락처(vcf)에서 번호 찾음: ${phoneMatched}명")
        appendLine("• 학부모 번호 있는 원생: $withPhone 명")
        if (noPhone.isNotEmpty()) appendLine("• ⚠️ 번호 없음 ${noPhone.size}명 (직접 입력 필요): ${noPhone.joinToString(", ")}")
        if (duplicates.isNotEmpty()) appendLine("• ⚠️ 동명이인(연락처 자동 연결 안 함, 직접 확인): ${duplicates.joinToString(", ")}")
    }

    fun apply() {
        val month = Days.thisMonth()
        finalStudents.chunked(400).forEach { chunk ->
            val batch = Repo.db.batch()
            chunk.forEach { s ->
                val ref = if (s.id.isBlank()) Repo.students().document() else Repo.students().document(s.id)
                batch.set(ref, Repo.studentData(s.copy(joinedMonth = s.joinedMonth.ifBlank { month })), SetOptions.merge())
            }
            batch.commit()
        }
    }

    companion object {
        /**
         * @param roster   수련생 현황 (없으면 명단은 그대로 두고 번호만 갱신)
         * @param contacts 연락처 vcf (없으면 기존 번호 + 형제 공유만)
         * @param table    출결 프로그램 연락처 명단 (번호·출결번호·학교·수강료) — 가장 우선
         */
        fun build(existing: List<Student>, roster: RosterResult?, contacts: List<VContact>?, table: List<ContactRow>? = null): ImportPlan {
            val month = Days.thisMonth()
            val added = mutableListOf<String>(); val reactivated = mutableListOf<String>(); val leftNow = mutableListOf<String>()
            val result = mutableListOf<Student>()

            if (roster != null) {
                val used = HashSet<String>()
                val sibKey = HashMap<String, String>()
                roster.siblingGroups.forEachIndexed { i, g -> g.forEach { sibKey[it] = "g${i + 1}" } }
                roster.entries.forEach { e ->
                    val cands = existing.filter { it.name == e.name && it.id !in used }
                    val old = cands.firstOrNull { it.grade == e.grade } ?: cands.firstOrNull()
                    if (old != null) used += old.id
                    if (old == null) added += e.name
                    else if (old.status == StudentStatus.LEFT) reactivated += e.name
                    val base = old ?: Student(joinedMonth = month)
                    result += base.copy(
                        name = e.name, grade = e.grade, demo = e.demo, order = e.order,
                        status = if (e.leave) StudentStatus.LEAVE else StudentStatus.ACTIVE,
                        statusReason = if (e.leave) e.leaveReason else "",
                        leftMonth = "",
                        siblingKey = sibKey[e.name] ?: "",
                    )
                }
                existing.filter { it.id !in used }.forEach { s ->
                    if (s.status != StudentStatus.LEFT) {
                        leftNow += s.name
                        result += s.copy(status = StudentStatus.LEFT, leftMonth = month, statusReason = roster.leftReasons[s.name] ?: "현황표에 없음")
                    } else result += s
                }
            } else {
                result += existing
                // 현황 엑셀 없이 연락처 명단(contact_날짜.xls)만 올린 경우: 명단에 없는 이름은 새 원생으로 등록
                if (table != null) {
                    val have = existing.filter { it.status != StudentStatus.LEFT }.map { it.name }.toSet()
                    val sibOf = HashMap<String, String>()
                    table.filter { it.phone.length >= 9 }.groupBy { it.phone }.values
                        .filter { g -> g.map { r -> r.name }.distinct().size >= 2 }
                        .forEachIndexed { i, g -> g.forEach { r -> sibOf[r.name] = "t${i + 1}" } }
                    table.map { it.name }.distinct().filter { it !in have }.forEachIndexed { i, n ->
                        val r0 = table.first { it.name == n }
                        added += n
                        result += Student(name = n, grade = r0.grade, joinedMonth = month, order = 1000 + i, siblingKey = sibOf[n] ?: "")
                    }
                }
            }

            // 동명이인
            val counts = result.filter { it.status != StudentStatus.LEFT }.groupingBy { it.name }.eachCount()
            val dups = counts.filter { it.value > 1 }.keys

            // ① 출결 연락처 명단 (이름이 같으면 번호를 대표 번호로, 출결번호·학교·수강료 넣기)
            var tableMatched = 0
            val tableIgnored = mutableListOf<String>()
            val withTable = if (table != null) {
                val byName = table.groupBy { it.name }
                val names = result.filter { it.status != StudentStatus.LEFT }.map { it.name }.toSet()
                tableIgnored += table.map { it.name }.filter { it !in names }.distinct()
                result.map { s ->
                    val rows = if (s.status == StudentStatus.LEFT || s.name in dups) null else byName[s.name]
                    if (rows.isNullOrEmpty()) s else {
                        tableMatched++
                        val phones = mutableListOf<String>(); val labels = mutableListOf<String>()
                        rows.forEach { r -> if (r.phone.length >= 9 && r.phone !in phones) { phones += r.phone; labels += r.label } }
                        s.phones.forEachIndexed { i, ph -> if (ph !in phones) { phones += ph; labels += s.phoneLabel(i) } }
                        val r0 = rows.first()
                        s.copy(phones = phones, phoneLabels = labels,
                            attendNo = r0.attendNo.ifBlank { s.attendNo }, school = r0.school.ifBlank { s.school }, fee = if (r0.fee > 0) r0.fee else s.fee)
                    }
                }
            } else result

            // ② 휴대폰 연락처(vcf) 매칭
            var matched = 0
            val withPhones = if (contacts != null) {
                val m = ContactMatcher.match(withTable.map { it.name }.filter { it !in dups }, contacts)
                withTable.map { s ->
                    val found = if (s.status == StudentStatus.LEFT) null else m[s.name]
                    if (found.isNullOrEmpty()) s else {
                        matched++
                        val phones = s.phones.toMutableList(); val labels = s.phones.indices.map { s.phoneLabel(it) }.toMutableList()
                        found.forEach { (p, l) -> if (p !in phones) { phones += p; labels += l } }
                        // 출결 명단 번호가 있으면 그게 대표(첫 번째), 없으면 어머님 우선
                        val (sp, sl) = if (s.attendNo.isNotBlank() && table != null) phones to labels else Repo.sortByLabel(phones, labels)
                        s.copy(phones = sp, phoneLabels = sl)
                    }
                }
            } else withTable

            // 형제끼리 번호 합치기
            val groups = withPhones.filter { it.siblingKey.isNotBlank() && it.status != StudentStatus.LEFT }.groupBy { it.siblingKey }
            val merged = withPhones.map { s ->
                val g = groups[s.siblingKey]
                if (s.siblingKey.isBlank() || g == null || g.size < 2) s
                else Repo.mergePhones(g).let { (p, l) -> s.copy(phones = p, phoneLabels = l) }
            }

            val active = merged.filter { it.status != StudentStatus.LEFT }
            return ImportPlan(
                finalStudents = merged,
                added = added, reactivated = reactivated, leftNow = leftNow,
                onLeave = active.filter { it.status == StudentStatus.LEAVE }.map { it.name },
                siblingGroups = groups.count { it.value.size >= 2 },
                withPhone = active.count { it.phones.isNotEmpty() },
                noPhone = active.filter { it.phones.isEmpty() }.map { it.name },
                duplicates = dups.toList(),
                phoneMatched = matched,
                tableMatched = tableMatched,
                tableIgnored = tableIgnored,
            )
        }
    }
}
