package com.jungdo.attend.importer

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns

/** 파일 하나를 읽어서 '무슨 파일인지' 알아낸 결과 */
sealed class Loaded {
    class Roster(val name: String, val sheets: List<XSheet>) : Loaded()      // 수련생 현황 .xlsx
    class Contacts(val name: String, val list: List<VContact>) : Loaded()    // 휴대폰 연락처 .vcf
    class Table(val name: String, val rows: List<ContactRow>) : Loaded()     // 연락처 명단 (contact_날짜.xls / 엑셀로 다시 저장한 것 / 신규 원생 양식)
    class Unknown(val name: String, val why: String) : Loaded()
}

/**
 * 파일 이름·확장자가 아니라 '내용'을 보고 종류를 알아내서, 어떤 버튼으로 올려도 알아서 맞는 곳에 넣는다.
 *  - .vcf                                   → 휴대폰 연락처
 *  - .xlsx 안에 학년 머리글(유치부, 1학년…)    → 수련생 현황
 *  - .xlsx / .xls(HTML) 안에 이름·연락처 머리글 → 연락처 명단 / 신규 원생 양식
 */
object ImportFiles {
    fun displayName(ctx: Context, uri: Uri): String = runCatching {
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull() ?: "파일"

    fun detect(name: String, bytes: ByteArray): Loaded {
        return try {
            val head = String(bytes, 0, minOf(bytes.size, 4000), Charsets.UTF_8)
            val isZip = bytes.size > 2 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()
            val isOleXls = bytes.size > 8 && bytes[0] == 0xD0.toByte() && bytes[1] == 0xCF.toByte() && bytes[2] == 0x11.toByte()
            when {
                head.contains("BEGIN:VCARD") -> Loaded.Contacts(name, VcfParser.parse(bytes.inputStream()))
                isZip -> {
                    val sheets = XlsxReader.read(bytes.inputStream())
                    if (RosterParser.hasGradeHeader(sheets)) Loaded.Roster(name, sheets)
                    else Loaded.Table(name, ContactTable.parse(bytes))
                }
                ContactTable.looksLike(bytes) -> Loaded.Table(name, ContactTable.parse(bytes))
                isOleXls -> Loaded.Unknown(name, "옛날 엑셀(.xls, 97-2003) 형식이에요. 엑셀에서 '다른 이름으로 저장 → Excel 통합 문서(.xlsx)'로 바꿔서 올려 주세요.")
                else -> Loaded.Unknown(name, "무슨 파일인지 모르겠어요. (수련생 현황 .xlsx / 연락처 .xls·.xlsx / 휴대폰 연락처 .vcf / 신규 원생 양식)")
            }
        } catch (e: Exception) {
            Loaded.Unknown(name, e.message ?: "읽지 못했어요")
        }
    }
}
