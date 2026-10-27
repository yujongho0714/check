package com.jungdo.attend.report

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 아주 작은 .xlsx 만들기 도구 (라이브러리 없이) — 글자, 숫자, 배경색, 굵게, 테두리, 병합, 열 너비, 틀 고정
 */
class XlsxWriter {
    data class Style(
        val bold: Boolean = false, val size: Int = 10, val color: String = "000000",
        val fill: String? = null, val center: Boolean = true, val border: Boolean = true, val wrap: Boolean = false,
    )

    inner class Sheet(val name: String) {
        internal val cells = sortedMapOf<Int, java.util.SortedMap<Int, Pair<Any, Int>>>()
        internal val widths = HashMap<Int, Double>()
        internal val heights = HashMap<Int, Double>()
        internal val merges = mutableListOf<String>()
        internal var freezeRow = 0; internal var freezeCol = 0
        var landscape = true

        fun set(r: Int, c: Int, v: Any?, st: Style = Style()) {
            cells.getOrPut(r) { sortedMapOf() }[c] = (v ?: "") to styleId(st)
        }
        fun width(c: Int, w: Double) { widths[c] = w }
        fun height(r: Int, h: Double) { heights[r] = h }
        fun merge(r1: Int, c1: Int, r2: Int, c2: Int) { merges += "${ref(r1, c1)}:${ref(r2, c2)}" }
        fun freeze(rows: Int, cols: Int) { freezeRow = rows; freezeCol = cols }
    }

    private val sheets = mutableListOf<Sheet>()
    private val styles = mutableListOf(Style(border = false, center = false))
    fun sheet(name: String) = Sheet(name.take(31).replace(Regex("[\\\\/?*\\[\\]:]"), " ")).also { sheets += it }
    private fun styleId(s: Style): Int { val i = styles.indexOf(s); if (i >= 0) return i; styles += s; return styles.size - 1 }

    companion object {
        fun col(c: Int): String { var n = c + 1; val sb = StringBuilder(); while (n > 0) { val m = (n - 1) % 26; sb.insert(0, ('A' + m)); n = (n - 1) / 26 }; return sb.toString() }
        fun ref(r: Int, c: Int) = "${col(c)}${r + 1}"
        private fun x(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
            .filter { it == '\n' || it == '\t' || it.code >= 0x20 }
    }

    private fun stylesXml(): String {
        val fonts = styles.map { Triple(it.bold, it.size, it.color) }.distinct()
        val fills = styles.mapNotNull { it.fill }.distinct()
        val sb = StringBuilder("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
        sb.append("<fonts count=\"${fonts.size}\">")
        fonts.forEach { (b, sz, c) -> sb.append("<font>${if (b) "<b/>" else ""}<sz val=\"$sz\"/><color rgb=\"FF$c\"/><name val=\"맑은 고딕\"/><family val=\"2\"/></font>") }
        sb.append("</fonts><fills count=\"${fills.size + 2}\"><fill><patternFill patternType=\"none\"/></fill><fill><patternFill patternType=\"gray125\"/></fill>")
        fills.forEach { sb.append("<fill><patternFill patternType=\"solid\"><fgColor rgb=\"FF$it\"/><bgColor indexed=\"64\"/></patternFill></fill>") }
        sb.append("</fills><borders count=\"2\"><border><left/><right/><top/><bottom/><diagonal/></border>")
        sb.append("<border><left style=\"thin\"><color rgb=\"FFB0B7C3\"/></left><right style=\"thin\"><color rgb=\"FFB0B7C3\"/></right><top style=\"thin\"><color rgb=\"FFB0B7C3\"/></top><bottom style=\"thin\"><color rgb=\"FFB0B7C3\"/></bottom><diagonal/></border></borders>")
        sb.append("<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs><cellXfs count=\"${styles.size}\">")
        styles.forEach { s ->
            val fontId = fonts.indexOf(Triple(s.bold, s.size, s.color))
            val fillId = s.fill?.let { fills.indexOf(it) + 2 } ?: 0
            sb.append("<xf numFmtId=\"0\" fontId=\"$fontId\" fillId=\"$fillId\" borderId=\"${if (s.border) 1 else 0}\" xfId=\"0\" applyFont=\"1\" applyFill=\"1\" applyBorder=\"1\" applyAlignment=\"1\">")
            sb.append("<alignment horizontal=\"${if (s.center) "center" else "left"}\" vertical=\"center\"${if (s.wrap) " wrapText=\"1\"" else ""}/></xf>")
        }
        sb.append("</cellXfs><cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles></styleSheet>")
        return sb.toString()
    }

    private fun sheetXml(s: Sheet): String {
        val sb = StringBuilder("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">""")
        sb.append("<sheetPr><pageSetUpPr fitToPage=\"1\"/></sheetPr><sheetViews><sheetView workbookViewId=\"0\">")
        if (s.freezeRow > 0 || s.freezeCol > 0) {
            val tl = ref(s.freezeRow, s.freezeCol)
            sb.append("<pane${if (s.freezeCol > 0) " xSplit=\"${s.freezeCol}\"" else ""}${if (s.freezeRow > 0) " ySplit=\"${s.freezeRow}\"" else ""} topLeftCell=\"$tl\" activePane=\"bottomRight\" state=\"frozen\"/>")
        }
        sb.append("</sheetView></sheetViews><sheetFormatPr defaultRowHeight=\"16.5\"/>")
        if (s.widths.isNotEmpty()) {
            sb.append("<cols>")
            s.widths.toSortedMap().forEach { (c, w) -> sb.append("<col min=\"${c + 1}\" max=\"${c + 1}\" width=\"$w\" customWidth=\"1\"/>") }
            sb.append("</cols>")
        }
        sb.append("<sheetData>")
        s.cells.forEach { (r, row) ->
            val h = s.heights[r]
            sb.append("<row r=\"${r + 1}\"${if (h != null) " ht=\"$h\" customHeight=\"1\"" else ""}>")
            row.forEach { (c, pair) ->
                val (v, st) = pair
                val rf = ref(r, c)
                when (v) {
                    is Number -> sb.append("<c r=\"$rf\" s=\"$st\"><v>$v</v></c>")
                    else -> {
                        val t = v.toString()
                        if (t.isEmpty()) sb.append("<c r=\"$rf\" s=\"$st\"/>")
                        else sb.append("<c r=\"$rf\" s=\"$st\" t=\"inlineStr\"><is><t xml:space=\"preserve\">${x(t)}</t></is></c>")
                    }
                }
            }
            sb.append("</row>")
        }
        sb.append("</sheetData>")
        if (s.merges.isNotEmpty()) {
            sb.append("<mergeCells count=\"${s.merges.size}\">"); s.merges.forEach { sb.append("<mergeCell ref=\"$it\"/>") }; sb.append("</mergeCells>")
        }
        sb.append("<pageMargins left=\"0.3\" right=\"0.3\" top=\"0.4\" bottom=\"0.4\" header=\"0.2\" footer=\"0.2\"/>")
        sb.append("<pageSetup paperSize=\"9\" orientation=\"${if (s.landscape) "landscape" else "portrait"}\" fitToWidth=\"1\" fitToHeight=\"0\"/>")
        sb.append("</worksheet>")
        return sb.toString()
    }

    fun bytes(): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            fun put(name: String, text: String) { z.putNextEntry(ZipEntry(name)); z.write(text.toByteArray(Charsets.UTF_8)); z.closeEntry() }
            val sheetXmls = sheets.map { sheetXml(it) } // 스타일 번호가 다 정해진 뒤 styles.xml 생성
            put("[Content_Types].xml", buildString {
                append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>""")
                sheets.indices.forEach { append("<Override PartName=\"/xl/worksheets/sheet${it + 1}.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>") }
                append("</Types>")
            })
            put("_rels/.rels", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>""")
            put("xl/workbook.xml", buildString {
                append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets>""")
                sheets.forEachIndexed { i, s -> append("<sheet name=\"${x(s.name)}\" sheetId=\"${i + 1}\" r:id=\"rId${i + 1}\"/>") }
                append("</sheets></workbook>")
            })
            put("xl/_rels/workbook.xml.rels", buildString {
                append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
                sheets.indices.forEach { append("<Relationship Id=\"rId${it + 1}\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet${it + 1}.xml\"/>") }
                append("<Relationship Id=\"rId${sheets.size + 1}\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/></Relationships>")
            })
            sheetXmls.forEachIndexed { i, xml -> put("xl/worksheets/sheet${i + 1}.xml", xml) }
            put("xl/styles.xml", stylesXml())
        }
        return out.toByteArray()
    }
}
