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

    /** 장비를 팔면 받는 골드 (1벌당) */
    const val SELL_GOLD = 1

    /**
     * 옷 이름으로 보는 장비 성별 ("여" / "남" / 모르면 "")
     *  1) 이름에 따로 떨어진 "여" / "남" 표시가 있으면 그대로 (예: "여 푸른 드레스", "[여]저고리", "여성용")
     *  2) 없으면 여자 옷 낱말(드레스·치마·저고리 …)이 들어 있는지로 판단
     *  → 이름에 표시도 낱말도 없으면 "" (남녀 공용으로 보고, 성별이 달라서 파는 대상에 안 넣음)
     *  여자 옷 낱말을 늘리고 싶으면 FEMALE_WORDS 에 한 줄만 더하면 돼요.
     */
    val FEMALE_WORDS = listOf("드레스", "치마", "저고리", "원피스", "스커트")
    private val MARK_F = Regex("(^|[\\s\\[\\(\\-_/])여(자|성|아)?($|[\\s\\]\\)\\-_/])|^여(자|성|아)")
    private val MARK_M = Regex("(^|[\\s\\[\\(\\-_/])남(자|성|아)?($|[\\s\\]\\)\\-_/])|^남(자|성|아)")

    fun genderOfName(name: String): String = when {
        MARK_F.containsMatchIn(name) -> "여"
        MARK_M.containsMatchIn(name) -> "남"
        FEMALE_WORDS.any { name.contains(it) } -> "여"
        else -> ""
    }

    /** 이 옷이 그 원생(성별 studentGender)과 성별이 다른지. 둘 중 하나라도 모르면 false */
    fun mismatch(studentGender: String, itemName: String): Boolean {
        val g = genderOfName(itemName)
        return studentGender.isNotBlank() && g.isNotBlank() && g != studentGender
    }

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
