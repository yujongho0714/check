package com.jungdo.attend.importer

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

/** 엑셀 칸 하나: 글자 + 배경색(RRGGBB, 없으면 null) */
data class XCell(val text: String, val fill: String?)

/** 엑셀 시트 하나 (행·열은 0부터) */
class XSheet(val name: String, private val cells: Map<Long, XCell>) {
    val maxRow: Int = cells.keys.maxOfOrNull { (it shr 20).toInt() } ?: 0
    val maxCol: Int = cells.keys.maxOfOrNull { (it and 0xFFFFF).toInt() } ?: 0
    fun cell(r: Int, c: Int): XCell? = cells[key(r, c)]
    fun text(r: Int, c: Int): String = cell(r, c)?.text?.trim() ?: ""
    companion object { fun key(r: Int, c: Int): Long = (r.toLong() shl 20) or c.toLong() }
}

/**
 * .xlsx 파일을 직접 읽는 아주 작은 리더 (추가 라이브러리 없이)
 * - 글자/숫자 값과 칸 배경색(시범단 회색 등)만 읽음
 * - 옛날 .xls 는 지원 안 함 → 엑셀에서 '다른 이름으로 저장 → .xlsx' 후 사용
 */
object XlsxReader {

    fun read(input: InputStream): List<XSheet> {
        val files = HashMap<String, ByteArray>()
        ZipInputStream(input).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                if (!e.isDirectory) files[e.name.trimStart('/')] = zip.readBytes()
            }
        }
        require(files.containsKey("xl/workbook.xml")) { "엑셀(.xlsx) 파일이 아니에요. 옛날 .xls 파일이면 엑셀에서 .xlsx 로 다시 저장해 주세요." }

        val shared = files["xl/sharedStrings.xml"]?.let(::parseShared) ?: emptyList()
        val xfFills = files["xl/styles.xml"]?.let(::parseStyles) ?: emptyList()
        val rels = files["xl/_rels/workbook.xml.rels"]?.let(::parseRels) ?: emptyMap()

        return parseWorkbook(files["xl/workbook.xml"]!!).mapNotNull { (name, rid) ->
            val target = rels[rid] ?: return@mapNotNull null
            val path = if (target.startsWith("/")) target.trimStart('/') else "xl/" + target.removePrefix("./")
            val bytes = files[path] ?: return@mapNotNull null
            XSheet(name, parseSheet(bytes, shared, xfFills))
        }
    }

    private fun parser(bytes: ByteArray): XmlPullParser =
        Xml.newPullParser().apply { setInput(ByteArrayInputStream(bytes), "UTF-8") }

    private fun parseWorkbook(bytes: ByteArray): List<Pair<String, String>> {
        val p = parser(bytes); val out = mutableListOf<Pair<String, String>>()
        while (p.next() != XmlPullParser.END_DOCUMENT) {
            if (p.eventType == XmlPullParser.START_TAG && p.name == "sheet") {
                val rid = p.getAttributeValue(null, "r:id")
                    ?: (0 until p.attributeCount).firstOrNull { p.getAttributeName(it).let { n -> n.endsWith(":id") || n == "id" } }?.let { p.getAttributeValue(it) }
                if (rid != null) out += (p.getAttributeValue(null, "name") ?: "시트") to rid
            }
        }
        return out
    }

    private fun parseRels(bytes: ByteArray): Map<String, String> {
        val p = parser(bytes); val out = HashMap<String, String>()
        while (p.next() != XmlPullParser.END_DOCUMENT) {
            if (p.eventType == XmlPullParser.START_TAG && p.name == "Relationship") {
                val id = p.getAttributeValue(null, "Id"); val target = p.getAttributeValue(null, "Target")
                if (id != null && target != null) out[id] = target
            }
        }
        return out
    }

    /** 공유 문자열. 한글 엑셀의 '윗주(rPh)' 글자는 빼고 읽음 */
    private fun parseShared(bytes: ByteArray): List<String> {
        val p = parser(bytes); val out = mutableListOf<String>()
        val sb = StringBuilder(); var inSi = false; var inPh = false; var inT = false
        while (p.next() != XmlPullParser.END_DOCUMENT) {
            when (p.eventType) {
                XmlPullParser.START_TAG -> when (p.name) {
                    "si" -> { inSi = true; sb.setLength(0) }
                    "rPh" -> inPh = true
                    "t" -> inT = true
                }
                XmlPullParser.TEXT -> if (inSi && inT && !inPh) sb.append(p.text)
                XmlPullParser.END_TAG -> when (p.name) {
                    "si" -> { inSi = false; out += sb.toString() }
                    "rPh" -> inPh = false
                    "t" -> inT = false
                }
            }
        }
        return out
    }

    /** cellXfs 순서대로 배경색(RRGGBB) */
    private fun parseStyles(bytes: ByteArray): List<String?> {
        val p = parser(bytes)
        val fills = mutableListOf<String?>(); val xfs = mutableListOf<String?>()
        var inFills = false; var inCellXfs = false; var curFill: String? = null; var solid = false
        while (p.next() != XmlPullParser.END_DOCUMENT) {
            if (p.eventType == XmlPullParser.START_TAG) when (p.name) {
                "fills" -> inFills = true
                "fill" -> if (inFills) { curFill = null; solid = false }
                "patternFill" -> if (inFills) solid = p.getAttributeValue(null, "patternType") == "solid"
                "fgColor" -> if (inFills && solid) {
                    val rgb = p.getAttributeValue(null, "rgb")
                    curFill = when {
                        rgb != null && rgb.length >= 6 -> rgb.takeLast(6).uppercase()
                        p.getAttributeValue(null, "theme") == "0" -> "FFFFFF"
                        else -> null
                    }
                }
                "cellXfs" -> inCellXfs = true
                "xf" -> if (inCellXfs) {
                    val fid = p.getAttributeValue(null, "fillId")?.toIntOrNull() ?: 0
                    xfs += fills.getOrNull(fid)
                }
            } else if (p.eventType == XmlPullParser.END_TAG) when (p.name) {
                "fill" -> if (inFills) fills += curFill
                "fills" -> inFills = false
                "cellXfs" -> inCellXfs = false
            }
        }
        return xfs
    }

    private fun colIndex(ref: String): Int {
        var c = 0
        for (ch in ref) { if (ch.isLetter()) c = c * 26 + (ch.uppercaseChar() - 'A' + 1) else break }
        return c - 1
    }
    private fun rowIndex(ref: String): Int = ref.filter { it.isDigit() }.toIntOrNull()?.minus(1) ?: 0

    private fun parseSheet(bytes: ByteArray, shared: List<String>, xfFills: List<String?>): Map<Long, XCell> {
        val p = parser(bytes); val out = HashMap<Long, XCell>()
        var ref = ""; var type: String? = null; var style = 0
        val v = StringBuilder(); var inV = false; var inIsT = false
        while (p.next() != XmlPullParser.END_DOCUMENT) {
            when (p.eventType) {
                XmlPullParser.START_TAG -> when (p.name) {
                    "c" -> {
                        ref = p.getAttributeValue(null, "r") ?: ""
                        type = p.getAttributeValue(null, "t")
                        style = p.getAttributeValue(null, "s")?.toIntOrNull() ?: 0
                        v.setLength(0)
                    }
                    "v" -> inV = true
                    "t" -> inIsT = type == "inlineStr"
                }
                XmlPullParser.TEXT -> if (inV || inIsT) v.append(p.text)
                XmlPullParser.END_TAG -> when (p.name) {
                    "v" -> inV = false
                    "t" -> inIsT = false
                    "c" -> {
                        val raw = v.toString()
                        val text = when (type) {
                            "s" -> shared.getOrNull(raw.trim().toIntOrNull() ?: -1) ?: ""
                            "b" -> if (raw == "1") "TRUE" else "FALSE"
                            else -> raw.removeSuffix(".0")
                        }
                        val fill = xfFills.getOrNull(style)?.takeIf { it != "FFFFFF" }
                        if (text.isNotEmpty() || fill != null) out[XSheet.key(rowIndex(ref), colIndex(ref))] = XCell(text, fill)
                    }
                }
            }
        }
        return out
    }
}
