package com.jungdo.attend.data

import java.util.Locale

/**
 * 드레스업 옷 800종 (assets/dressup, 8부위 x 100)
 *  - 옷 키 = "부위:번호"  예) "coat:012"  (번호는 assets/dressup/dress.json 안 순서, 3자리)
 *  - 뽑기·출석 드롭은 800종 모두 같은 확률 (희귀도 없음)
 */
object Dress {
    /** 드레스룸 탭 순서 */
    val CATS = listOf("coat", "armor", "helmet", "hair", "shield", "weapon", "shoes", "cape")
    const val PER_CAT = 100

    fun catLabel(cat: String) = when (cat) {
        "coat" -> "갑옷"; "armor" -> "겉옷"; "helmet" -> "투구"; "hair" -> "헤어"
        "shield" -> "방패"; "weapon" -> "무기"; "shoes" -> "신발"; "cape" -> "망토"
        else -> cat
    }

    fun key(cat: String, no: Int) = cat + ":" + String.format(Locale.US, "%03d", no)
    fun catOf(key: String) = key.substringBefore(':')
    fun noOf(key: String) = key.substringAfter(':').toIntOrNull() ?: 0

    val allKeys: List<String> by lazy { CATS.flatMap { c -> (1..PER_CAT).map { key(c, it) } } }

    /** 한 개 뽑기 (이미 가진 것도 나올 수 있음 → 중복은 골드로 환급) */
    fun pickOne(): String = allKeys.random()

    /** 뽑기 한 번의 결과 */
    class PullResult(
        val keys: List<String>,      // 뽑은 순서대로 전부
        val newKeys: List<String>,   // 처음 얻은 옷
        val dupCount: Int,           // 중복 개수
        val refund: Int,             // 중복 환급 골드 합계
    )

    /** n개를 뽑고 새 옷/중복/환급을 계산 (같은 뽑기 안에서 두 번 나오면 두 번째는 중복) */
    fun pull(owned: Collection<String>, n: Int, refundEach: Int): PullResult {
        val have = owned.toHashSet()
        val keys = ArrayList<String>()
        val fresh = ArrayList<String>()
        var dup = 0
        repeat(n.coerceAtLeast(0)) {
            val k = pickOne()
            keys.add(k)
            if (have.add(k)) fresh.add(k) else dup++
        }
        return PullResult(keys, fresh, dup, dup * refundEach)
    }
}
