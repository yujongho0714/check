package com.jungdo.attend.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

/**
 * 보석 · 네임플레이트 (출석 보상)
 *  - 보석 396종 (assets/gem/001.webp … 396.webp): 등원/하원을 누를 때마다 gemChance(%) 확률로 하나씩
 *  - 네임플레이트 47종 (assets/plate/01.webp … 47.webp): 등원/하원 때마다 plateChance(%) 확률
 *  - 확률은 관리자 화면에서 바꾸고, Firestore config/loot 에 저장돼서 모든 기기에 똑같이 적용됨
 *  - 이미 가진 건 다시 안 나옴. 다 모으면 더 안 나옴
 *  - (드레스업) 보석마다 등급(노말~레전드리)이 있고, 관리자가 정한 등급별 비율로 "등급 먼저 → 그 등급 안에서 미보유 보석" 순서로 뽑음
 *    · 뽑힌 등급에 미보유 보석이 없으면 미보유 보석이 남은 다른 등급으로 이월 (비율 0% 등급은 안 나옴)
 *  - (드레스업) 관리자가 "사용 지정"한 보석만 나옴. 지정한 적이 없으면(gemEnabled == null) 396종 전부 사용
 *  - (드레스업) 옷 드롭 확률 / 뽑기 가격 / 중복 환급 골드도 여기서 들고 있음
 */
object Loot {
    const val GEM_COUNT = 396
    const val PLATE_COUNT = 47
    /** 기본 확률 (%) — 서버 값이 아직 안 왔을 때 / 값이 없을 때 */
    const val DEFAULT_GEM_CHANCE = 100.0
    const val DEFAULT_PLATE_CHANCE = 30.0
    const val DEFAULT_DRESS_CHANCE = 5.0   // 출석 때 옷 한 개 얻을 확률 (%)
    const val DEFAULT_GACHA_PRICE = 3      // 뽑기 1회 골드
    const val DEFAULT_DUP_REFUND = 1       // 중복 옷 1개당 돌려받는 골드

    /** 지금 적용 중인 확률 (%, 0 ~ 100). Repo.startLootSync 가 서버 값으로 계속 갱신 */
    var gemChance by mutableDoubleStateOf(DEFAULT_GEM_CHANCE)
    var plateChance by mutableDoubleStateOf(DEFAULT_PLATE_CHANCE)
    var dressChance by mutableDoubleStateOf(DEFAULT_DRESS_CHANCE)
    var gachaPrice by mutableIntStateOf(DEFAULT_GACHA_PRICE)
    var dupRefund by mutableIntStateOf(DEFAULT_DUP_REFUND)

    /** 등급별 획득 비율 (합이 100이 아니어도 비율로 계산). 서버(config/loot.rarityWeights) 값이 오면 갱신 */
    var rarityWeights by mutableStateOf<Map<Rarity, Double>>(Rarity.values().associateWith { it.defaultWeight })

    /** 보석별 등급 (서버 config/gemRarity). 없는 보석은 노말 */
    var gemRarity by mutableStateOf<Map<String, Rarity>>(emptyMap())

    /** 관리자가 사용하기로 한 보석 번호들 (서버 config/gemRarity.on). null 이면 지정한 적 없음 → 전부 사용 */
    var gemEnabled by mutableStateOf<Set<String>?>(null)

    fun isGemOn(gemId: String): Boolean = gemEnabled?.contains(gemId) ?: true
    /** 지금 사용중인 보석 수 (가방의 "n/전체" 표시 등에 씀) */
    val enabledCount: Int get() = gemEnabled?.count { it in allGemSet } ?: GEM_COUNT
    private val allGemSet: Set<String> by lazy { allGems.toSet() }

    /** 마지막으로 서버 값을 받은 상태 (관리자 화면에서 "지금 이 기기에 적용된 값"을 확인하는 용도) */
    var syncNote by androidx.compose.runtime.mutableStateOf("서버 값 기다리는 중…")

    private fun hit(percent: Double) = Math.random() * 100.0 < percent

    fun gemId(n: Int) = String.format(Locale.US, "%03d", n)
    fun plateId(n: Int) = String.format(Locale.US, "%02d", n)

    val allGems: List<String> by lazy { (1..GEM_COUNT).map { gemId(it) } }
    val allPlates: List<String> by lazy { (1..PLATE_COUNT).map { plateId(it) } }

    fun rarityOf(gemId: String): Rarity = gemRarity[gemId] ?: Rarity.NORMAL

    fun rollGem(owned: List<String>): String? {
        if (!hit(gemChance)) return null
        return pickGem(owned)
    }

    /** 등급 먼저 뽑고(관리자 비율), 그 등급의 미보유 보석 중 하나. 이월 규칙은 위 설명 참고 */
    fun pickGem(owned: List<String>): String? {
        val have = owned.toSet()
        val pool = allGems.filter { it !in have && isGemOn(it) }.groupBy { rarityOf(it) }
        val cands = Rarity.values().filter { (rarityWeights[it] ?: 0.0) > 0.0 && !pool[it].isNullOrEmpty() }
        if (cands.isEmpty()) return null
        val total = cands.sumOf { rarityWeights[it] ?: 0.0 }
        var x = Math.random() * total
        for (r in cands) {
            x -= rarityWeights[r] ?: 0.0
            if (x < 0) return pool[r]!!.random()
        }
        return pool[cands.last()]!!.random()
    }

    /** 도감 보너스 배율. 추후 도감 시스템이 여기만 바꾸면 골드 계산 전체에 적용됨 (지금은 1.0 = 보너스 없음) */
    fun goldBonus(): Double = 1.0

    /** 장착한 보석 하나가 등원/하원 때 주는 골드 (등급 구간 랜덤 × 도감 보너스). 장착 보석이 없으면 0 */
    fun goldFor(equippedGem: String): Int {
        if (equippedGem.isBlank()) return 0
        return Math.round(rarityOf(equippedGem).rollGold() * goldBonus()).toInt().coerceAtLeast(0)
    }

    /** 출석 때 옷 드롭: dressChance(%) 확률로 800종 중 하나 (전부 같은 확률, 이미 가진 옷도 나올 수 있음 → 환급) */
    fun rollDress(): String? = if (hit(dressChance)) Dress.pickOne() else null

    fun rollPlate(owned: List<String>): String? {
        if (!hit(plateChance)) return null
        val have = owned.toSet()
        return allPlates.filter { it !in have }.randomOrNull()
    }
}
