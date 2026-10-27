package com.jungdo.attend.data

import java.util.Locale

/**
 * 보석 · 네임플레이트 (출석 보상)
 *  - 보석 396종 (assets/gem/001.webp … 396.webp): 등원/하원을 누를 때마다 하나씩 (하루에 등원 한 번, 하원 한 번)
 *  - 네임플레이트 47종 (assets/plate/01.webp … 47.webp): 등원/하원 때마다 30% 확률
 *  - 이미 가진 건 다시 안 나옴. 다 모으면 더 안 나옴
 */
object Loot {
    const val GEM_COUNT = 396
    const val PLATE_COUNT = 47
    const val PLATE_CHANCE = 0.30

    fun gemId(n: Int) = String.format(Locale.US, "%03d", n)
    fun plateId(n: Int) = String.format(Locale.US, "%02d", n)

    val allGems: List<String> by lazy { (1..GEM_COUNT).map { gemId(it) } }
    val allPlates: List<String> by lazy { (1..PLATE_COUNT).map { plateId(it) } }

    fun rollGem(owned: List<String>): String? {
        val have = owned.toSet()
        return allGems.filter { it !in have }.randomOrNull()
    }

    fun rollPlate(owned: List<String>): String? {
        if (Math.random() >= PLATE_CHANCE) return null
        val have = owned.toSet()
        return allPlates.filter { it !in have }.randomOrNull()
    }
}
