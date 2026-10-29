package com.jungdo.attend.data

/** 보석 한 개의 이름과 능력 이름. 실제 효과(골드 등)는 등급에 따라 정해짐 */
data class GemInfo(val name: String, val skill: String)

object GemData {
    /** 작명이 끝난 보석 전체. 001~099 는 GEM_NAMES_1, 100~396 은 GEM_NAMES_2 */
    private val all: Map<String, GemInfo> = GEM_NAMES_1 + GEM_NAMES_2

    fun info(id: String): GemInfo = all[id] ?: GemInfo("보석 $id", "신비한 힘")
    fun name(id: String): String = info(id).name
    fun skill(id: String): String = info(id).skill
    val namedCount: Int get() = all.size

    /** 보석 효과 설명 (등급에 따라 정해진 실제 효과): 장착하면 등원·하원 때마다 받는 골드, 레전드리는 보너스 상품 기회 */
    fun effect(id: String): String {
        val r = Loot.rarityOf(id)
        val gold = "장착하면 등원·하원 때마다 골드 ${r.goldMin}~${r.goldMax}개"
        val c = Prizes.legendaryChance
        val bonus = if (r == Rarity.LEGENDARY && c > 0.0 && Prizes.legendaryIds.isNotEmpty())
            " + 보너스 상품 당첨 기회 (${if (c % 1.0 == 0.0) c.toInt().toString() else c.toString()}%)" else ""
        fun n(d: Double) = if (d % 1.0 == 0.0) d.toInt().toString() else d.toString()
        val skill = when (r) {
            Rarity.EPIC -> if (Loot.epicGemBonus > 0.0) " + 보석 획득 확률 ${n(Loot.epicGemBonus)}% 상승" else ""
            Rarity.UNIQUE -> if (Loot.uniqueDressBonus > 0.0) " + 장비(옷) 획득 확률 ${n(Loot.uniqueDressBonus)}% 상승" else ""
            else -> ""
        }
        return gold + skill + bonus
    }
}
