package com.jungdo.attend.importer

import java.io.ByteArrayInputStream

/** 출결 프로그램에서 내보낸 연락처 명단 한 줄 */
data class ContactRow(
    val name: String,
    val phone: String,
    val label: String,       // 어머님/아버님
    val attendNo: String,    // 출결번호
    val school: String,
    val fee: Int,
)

/**
 * 출결 프로그램 '연락처 내보내기' 파일 (contact_날짜.xls) 읽기
 * - 확장자는 .xls 지만 실제로는 HTML 표 → 그대로 읽음
 * - 엑셀에서 .xlsx 로 다시 저장한 파일도 읽음
 * 머리글: 이름 | 학부모 연락처 | 학부모 구분(어머니/아버지) | 학생 연락처 | 출결번호 | 납부일 | 수강료 | 학교명 | 학년
 */
object ContactTable {
    fun looksLike(bytes: ByteArray): Boolean {
        val head = String(bytes, 0, minOf(bytes.size, 4000), Charsets.UTF_8)
        return head.contains("<td", ignoreCase = true) && head.contains("출결번호")
    }

    fun parse(bytes: ByteArray): List<ContactRow> {
        val rows: List<List<String>> = if (bytes.size > 2 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()) {
            val sheet = XlsxReader.read(ByteArrayInputStream(bytes)).first()
            (0..sheet.maxRow).map { r -> (0..sheet.maxCol).map { c -> sheet.text(r, c) } }
        } else parseHtml(String(bytes, Charsets.UTF_8).removePrefix("﻿"))

        val hi = rows.indexOfFirst { r -> r.any { it.trim() == "이름" } && r.any { it.contains("연락처") } }
        require(hi >= 0) { "연락처 명단에서 '이름', '학부모 연락처' 머리글을 못 찾았어요." }
        val h = rows[hi].map { it.trim() }
        fun col(vararg keys: String) = h.indexOfFirst { t -> keys.any { t.startsWith(it) } }
        val cName = col("이름"); val cPhone = col("학부모 연락처", "학부모연락처", "연락처")
        val cLabel = col("학부모 구분", "구분"); val cNo = col("출결번호"); val cSchool = col("학교"); val cFee = col("수강료")

        return rows.drop(hi + 1).mapNotNull { r ->
            val name = r.getOrNull(cName)?.trim().orEmpty()
            if (RosterParser.cleanName(name) == null) return@mapNotNull null
            val phone = r.getOrNull(cPhone).orEmpty().filter { it.isDigit() }
            val lb = r.getOrNull(cLabel).orEmpty()
            ContactRow(
                name = RosterParser.cleanName(name)!!,
                phone = phone,
                label = when { lb.contains("아버") -> "아버님"; lb.contains("어머") -> "어머님"; lb.isBlank() -> "어머님"; else -> lb },
                attendNo = if (cNo >= 0) r.getOrNull(cNo).orEmpty().trim().removeSuffix(".0") else "",
                school = if (cSchool >= 0) r.getOrNull(cSchool).orEmpty().trim() else "",
                fee = if (cFee >= 0) r.getOrNull(cFee).orEmpty().filter { it.isDigit() }.toIntOrNull() ?: 0 else 0,
            )
        }
    }

    private fun parseHtml(html: String): List<List<String>> {
        val tr = Regex("<tr[^>]*>(.*?)</tr>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        val td = Regex("<t[dh][^>]*>(.*?)</t[dh]>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        return tr.findAll(html).map { m ->
            td.findAll(m.groupValues[1]).map { c ->
                c.groupValues[1].replace(Regex("<[^>]+>"), "")
                    .replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").trim()
            }.toList()
        }.toList()
    }
}
