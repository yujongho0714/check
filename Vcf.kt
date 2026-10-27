package com.jungdo.attend.importer

import java.io.ByteArrayOutputStream
import java.io.InputStream

/** 연락처 한 개 (이름, 번호들) */
data class VContact(val name: String, val phones: List<String>)

/** 휴대폰에서 내보낸 연락처(.vcf) 읽기 — 한글 이름(QUOTED-PRINTABLE) 지원 */
object VcfParser {
    fun parse(input: InputStream): List<VContact> {
        var raw = input.readBytes().toString(Charsets.UTF_8).replace("\r\n", "\n")
        raw = raw.replace("=\n", "") // 줄 바꿈으로 잘린 QP 이어붙이기
        val out = mutableListOf<VContact>()
        raw.split("BEGIN:VCARD").drop(1).forEach { card ->
            var fn: String? = null; var n: String? = null
            val phones = mutableListOf<String>()
            card.lines().forEach { line ->
                val idx = line.indexOf(':')
                if (idx <= 0) return@forEach
                val key = line.substring(0, idx).uppercase()
                val value = line.substring(idx + 1)
                val decoded = if (key.contains("QUOTED-PRINTABLE")) decodeQp(value) else value
                when {
                    key == "FN" || key.startsWith("FN;") -> fn = decoded.trim()
                    key == "N" || key.startsWith("N;") -> n = decoded.split(';').filter { it.isNotBlank() }.reversed().joinToString("")
                    key.startsWith("TEL") -> decoded.filter { it.isDigit() }.let { d ->
                        val num = if (d.startsWith("82") && d.length >= 11) "0" + d.drop(2) else d
                        if (num.length >= 9 && num !in phones) phones += num
                    }
                }
            }
            val name = fn ?: n
            if (!name.isNullOrBlank() && phones.isNotEmpty()) out += VContact(name, phones)
        }
        return out
    }

    private fun decodeQp(s: String): String {
        val bytes = ByteArrayOutputStream()
        var i = 0
        while (i < s.length) {
            val ch = s[i]
            if (ch == '=' && i + 2 < s.length) {
                val hex = s.substring(i + 1, minOf(i + 3, s.length))
                val b = hex.toIntOrNull(16)
                if (hex.length == 2 && b != null) { bytes.write(b); i += 3; continue }
            }
            bytes.write(ch.code and 0xFF)
            i++
        }
        return bytes.toString("UTF-8")
    }
}

/**
 * 연락처 이름 → 원생 이름 찾기
 *  "김주아/주원어머님" → 김주아, 김주원   "표재영 재원 어머님" → 표재영, 표재원
 *  "김지원승원어머님" → 김지원, 김승원     "오하람하율모" → 오하람, 오하율
 * 선생님/상담/유치원/학원 등은 건너뜀
 */
object ContactMatcher {
    private val REL = "(외할머니|외할아버지|어머님|어머니|아버님|아버지|할머님|할머니|할아버님|할아버지|고모님|삼촌|이모|엄마|아빠|맘|모|부)"
    private val SKIP = Regex("선생님|상담|체험|유치원|어린이집|학원|관장|사범|차량|실장|1관|3관|본관|매니저|세무|정수기|엘레베이터|유플러스|형사|돌봄|늘봄|업무폰|도장폰|원장")

    fun label(fn: String): String = when {
        Regex("할머님|할머니").containsMatchIn(fn) -> "할머님"
        Regex("할아버님|할아버지").containsMatchIn(fn) -> "할아버님"
        Regex("어머님|어머니|엄마|맘|모(?=\\s|$|\\()").containsMatchIn(fn) -> "어머님"
        Regex("아버님|아버지|아빠|부(?=\\s|$|\\()").containsMatchIn(fn) -> "아버님"
        Regex("고모님|삼촌|이모").containsMatchIn(fn) -> Regex("고모님|삼촌|이모").find(fn)!!.value
        else -> "보호자"
    }

    fun childNames(fn: String): List<String> {
        if (SKIP.containsMatchIn(fn)) return emptyList()
        var s = fn.replace(Regex("\\(.*?\\)"), " ")
        s = s.replace(Regex("\\d+세|\\d+학년|선수부|정도|수련생|형님"), " ")
        s = s.replace(Regex("$REL(?=[\\s/,]|$)"), " ").replace('-', ' ')
        val parts = s.split(Regex("[\\s/,·.]+")).filter { it.matches(Regex("[가-힣]+")) }
        val out = mutableListOf<String>(); var sur: Char? = null
        for (p in parts) when (p.length) {
            3 -> { sur = p[0]; out += p }
            2 -> out += if (sur != null) "$sur$p" else p
            4 -> out += p
            5 -> { sur = p[0]; out += p.substring(0, 3); out += "${p[0]}${p.substring(3)}" }
            6 -> { sur = p[0]; out += p.substring(0, 3); out += p.substring(3) }
        }
        return out
    }

    /** 원생 이름 → (번호, 호칭) 목록 */
    fun match(names: Collection<String>, contacts: List<VContact>): Map<String, List<Pair<String, String>>> {
        val set = names.toSet()
        val out = HashMap<String, MutableList<Pair<String, String>>>()
        contacts.forEach { c ->
            val lb = label(c.name)
            childNames(c.name).filter { it in set }.distinct().forEach { n ->
                val list = out.getOrPut(n) { mutableListOf() }
                c.phones.forEach { p -> if (list.none { it.first == p }) list += p to lb }
            }
        }
        return out
    }
}
