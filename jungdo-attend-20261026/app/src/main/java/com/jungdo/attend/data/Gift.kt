package com.jungdo.attend.data

/**
 * 관장님 선물 (전체 지급 / 개별 지급)
 *  - 보낸 선물은 원생 문서(students/{id}.gifts)에 글자 한 줄로 쌓여 있다가, 그 원생이 "진짜 첫" 등원/하원을 누를 때 한 번에 받고 사라짐
 *    (같은 날 같은 버튼을 또 눌러서 경험치가 안 오르는 경우에는 선물도 안 받음 → 연타해도 중복 지급 없음)
 *  - 한 줄 형식: "선물번호|종류|옵션|개수"
 *      GEM   | any 또는 등급코드(normal/rare/…)  | 개수 → 아직 없는 보석을 랜덤으로
 *      PLATE | any                                | 개수 → 아직 없는 네임플레이트를 랜덤으로
 *      DRESS | any 또는 부위(coat/armor/…)         | 개수 → 그 부위의 옷을 랜덤으로 (없는 옷 먼저, 다 있으면 중복 환급 골드)
 *      PRIZE | 상품 id                            | 개수 → 상품 당첨 기록(지급 대기)을 개수만큼
 */
object Gifts {
    const val GEM = "GEM"
    const val PLATE = "PLATE"
    const val DRESS = "DRESS"
    const val PRIZE = "PRIZE"
    const val MAX_N = 50

    class Gift(val id: String, val kind: String, val arg: String, val n: Int)

    /** 이 기기에서 이미 받아간 선물 (원생id/선물번호). 서버에서 지워지기 전에 낡은 값으로 또 받는 것을 막음 */
    val claimed = HashSet<String>()

    fun make(kind: String, arg: String, n: Int): String =
        "g" + System.currentTimeMillis().toString(36) + (Math.random() * 1000).toInt() + "|" + kind + "|" + arg + "|" + n.coerceIn(1, MAX_N)

    fun parse(raw: String): Gift? {
        val p = raw.split('|')
        if (p.size < 4) return null
        val n = p[3].toIntOrNull() ?: return null
        return Gift(p[0], p[1], p[2], n.coerceIn(1, MAX_N))
    }

    fun label(raw: String): String {
        val g = parse(raw) ?: return "(알 수 없는 선물)"
        return when (g.kind) {
            GEM -> "보석 ${g.n}개" + (Rarity.ofOrNull(g.arg)?.let { " (${it.label})" } ?: " (아무 등급)")
            PLATE -> "네임플레이트 ${g.n}개"
            DRESS -> (if (g.arg in Dress.CATS) Dress.catLabel(g.arg) else "아무 부위") + " 장비 ${g.n}개"
            PRIZE -> (Prizes.list.firstOrNull { it.id == g.arg }?.name ?: "상품") + " ${g.n}개"
            else -> "(알 수 없는 선물)"
        }
    }
}
