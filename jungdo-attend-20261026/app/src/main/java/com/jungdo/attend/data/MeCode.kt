package com.jungdo.attend.data

import android.util.Base64
import java.security.MessageDigest

/**
 * 내 출결 캐릭터(드레스업)를 포켓몬 어드벤처 게임으로 보내는 링크
 *  - 가진 옷(800칸 비트) + 입은 옷(부위 8칸) → 짧은 코드 + 본인 번호로 만든 확인 글자(MAC)
 *  - 게임(src/6e_me.js)과 똑같은 방식이어야 해요. 부위 순서도 같아야 함.
 */
object MeCode {
    private const val SEC = "jungdo2-me-v1"
    private val CATS = listOf("armor", "cape", "coat", "hair", "helmet", "shield", "shoes", "weapon")
    private fun b64(b: ByteArray) = Base64.encodeToString(b, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)

    fun code(own: List<String>, equip: Map<String, String>, num: String): String {
        val u = ByteArray(109); u[0] = 1
        equip.forEach { (cat, key) ->
            val c = CATS.indexOf(cat); val n = Dress.noOf(key)
            if (c >= 0 && n in 1..100) u[1 + c] = n.toByte()
        }
        (own + equip.values).distinct().forEach { key ->
            val c = CATS.indexOf(Dress.catOf(key)); val n = Dress.noOf(key)
            if (c >= 0 && n in 1..100) { val b = c * 100 + n - 1; u[9 + (b shr 3)] = (u[9 + (b shr 3)].toInt() or (1 shl (b and 7))).toByte() }
        }
        val p = b64(u)
        val mac = b64(MessageDigest.getInstance("SHA-256").digest("$SEC|${num.filter { it.isDigit() }}|$p".toByteArray()).copyOf(6))
        return "$p.$mac"
    }

    fun link(s: Student, num: String) = "https://${PokeGift.GAME_URL}/?me=" + code(s.dress, s.dressEquip, num)

    fun smsText(s: Student, num: String) =
        "👕 [정도2관] ${s.name}의 출결 캐릭터를 포켓몬 게임으로 보냈어요!\n\n아래 링크를 누르면 게임에 내 캐릭터가 들어가요:\n${link(s, num)}\n\n" +
            "게임에서 이름과 이 폰 번호로 시작해요. 메뉴 → 👕 옷장에서 가진 옷·무기로 갈아입을 수 있어요.\n(링크가 게임 앱으로 안 열리면, 길게 눌러 복사 → 게임 메뉴 → 👕 옷장에 붙여 넣기)"

    /** 본인 폰으로 문자 보내기 (출석 문자 보내는 폰이 보내요) */
    fun send(s: Student, phone: String): String? {
        val num = phone.filter { it.isDigit() }
        if (num.length < 10 || !num.startsWith("01")) return "휴대폰 번호를 다시 확인해 주세요"
        if (s.dress.isEmpty() && s.dressEquip.isEmpty()) return "아직 가진 옷이 없어요"
        Bulk.create("내 캐릭터 · ${s.name}", listOf(BulkItem(s.name, num, smsText(s, num))))
        return null
    }
}
