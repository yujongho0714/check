package com.jungdo.attend.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** 도감 하나: 이름 + 보석 1~5개 + 등원/하원 때 추가로 주는 골드 */
data class DogamSet(
    val id: String,
    val name: String,
    val gems: List<String>,
    val goldIn: Int,
    val goldOut: Int,
    val rarity: String = "",          // 자동으로 만든 도감이면 등급 코드 (normal/rare/…)
    val gemBonus: Double = 0.0,       // 활성화되면 보석 획득 확률에 더해지는 % (퍼센트 포인트, 합적용)
    val dressBonus: Double = 0.0,     // 활성화되면 옷(장비) 획득 확률에 더해지는 % (퍼센트 포인트, 합적용)
)

/**
 * 도감 (보석 세트 효과)
 *  - 관리자가 도감을 만들고(이름, 보석 1~5개, 등원 추가골드, 하원 추가골드), 서버(config/dogam)에 저장돼서 모든 기기에 적용
 *  - 아이가 그 도감의 보석을 "전부" 가지고 있으면 도감이 활성화됨 → 등원/하원 때마다 추가 골드
 *  - 활성화된 도감이 여러 개면 추가 골드를 모두 더함
 *  - 장착 여부와는 상관없음 (가지고만 있으면 됨)
 */
object Dogam {
    const val MAX_GEMS = 5

    /** 지금 적용 중인 도감들. Repo.startDogamSync 가 서버 값으로 계속 갱신 */
    var sets by mutableStateOf<List<DogamSet>>(emptyList())

    fun isActive(d: DogamSet, owned: Collection<String>): Boolean =
        d.gems.isNotEmpty() && owned.toSet().containsAll(d.gems)

    fun active(owned: Collection<String>): List<DogamSet> {
        val have = owned.toSet()
        return sets.filter { it.gems.isNotEmpty() && have.containsAll(it.gems) }
    }

    /** 등원(IN) / 하원(OUT) 때 도감으로 받는 추가 골드 합계 */
    fun bonusFor(type: String, owned: Collection<String>): Int =
        active(owned).sumOf { if (type == EventType.IN) it.goldIn else it.goldOut }.coerceAtLeast(0)

    fun totalIn(owned: Collection<String>): Int = bonusFor(EventType.IN, owned)
    fun totalOut(owned: Collection<String>): Int = bonusFor(EventType.OUT, owned)

    // ── 활성화된 도감들의 확률 보너스: 모두 "더하기(합적용)". 곱하기보다 항상 같거나 낮아서 당첨 확률이 덜 올라감 ──
    fun gemBonusFor(owned: Collection<String>): Double = active(owned).sumOf { it.gemBonus }.coerceAtLeast(0.0)
    fun dressBonusFor(owned: Collection<String>): Double = active(owned).sumOf { it.dressBonus }.coerceAtLeast(0.0)

    /** 자동으로 만들 때 기본값: 보석 2개 짝 → 0.01%, 짝이 없는 1개 → 절반(0.005%) */
    const val PAIR_BONUS = 0.01
    const val SINGLE_BONUS = 0.005

    /** 도감 제작기 설정 (관리자가 바꿀 수 있음). 짝 기준 값이고, 짝 없는 1개는 절반 */
    var makerGold by mutableStateOf(mapOf(Rarity.NORMAL to 2, Rarity.RARE to 4, Rarity.EPIC to 0, Rarity.UNIQUE to 0, Rarity.LEGENDARY to 6))
    var makerPct by mutableStateOf(mapOf(Rarity.EPIC to PAIR_BONUS, Rarity.UNIQUE to PAIR_BONUS))

    /**
     * 등급별로 보석을 무작위로 2개씩 짝지어 도감을 만듦. 홀수면 마지막 1개는 절반 효과.
     *  노말·레어·레전드리 = 등원·하원 추가 골드 / 에픽 = 보석 획득 확률 +% / 유니크 = 장비(옷) 획득 확률 +%
     *  (효과 크기는 makerGold / makerPct — 도감 제작기 화면에서 바꿈. 확률 보너스는 활성 도감끼리 전부 "더하기")
     */
    fun build(groups: Map<Rarity, List<String>>): List<DogamSet> {
        val out = ArrayList<DogamSet>()
        for (r in Rarity.values()) {
            val g = (groups[r] ?: emptyList()).shuffled()
            g.chunked(2).forEachIndexed { i, pair ->
                val single = pair.size == 1
                val pct = (makerPct[r] ?: 0.0).coerceAtLeast(0.0)
                val bonus = if (single) pct / 2 else pct
                val goldPair = (makerGold[r] ?: 0).coerceAtLeast(0)
                val gold = if (single) goldPair / 2 else goldPair
                out.add(DogamSet(
                    id = newId() + "_" + r.code + i, name = "${r.label} 도감 ${i + 1}", gems = pair,
                    goldIn = gold, goldOut = gold, rarity = r.code,
                    gemBonus = if (r == Rarity.EPIC) bonus else 0.0,
                    dressBonus = if (r == Rarity.UNIQUE) bonus else 0.0,
                ))
            }
        }
        return out
    }

    fun fmt(d: Double): String =
        if (d % 1.0 == 0.0) d.toInt().toString() else String.format(java.util.Locale.US, "%.3f", d).trimEnd('0').trimEnd('.')

    /** 도감 하나의 효과 설명 */
    fun effectText(d: DogamSet): String {
        val parts = ArrayList<String>()
        if (d.goldIn > 0 || d.goldOut > 0) parts.add("등원 +${d.goldIn} · 하원 +${d.goldOut} 골드")
        if (d.gemBonus > 0.0) parts.add("보석 획득 확률 +${fmt(d.gemBonus)}%")
        if (d.dressBonus > 0.0) parts.add("장비(옷) 획득 확률 +${fmt(d.dressBonus)}%")
        return if (parts.isEmpty()) "효과 없음" else parts.joinToString(" · ")
    }

    /** 활성화된 도감의 확률 보너스 합 (없으면 빈 글자) */
    fun bonusSummary(owned: Collection<String>): String {
        val g = gemBonusFor(owned); val d = dressBonusFor(owned)
        return (if (g > 0.0) " · 보석 획득 +${fmt(g)}%" else "") + (if (d > 0.0) " · 장비 획득 +${fmt(d)}%" else "")
    }

    fun newId(): String = "d" + System.currentTimeMillis().toString(36) + (Math.random() * 1000).toInt()
}
