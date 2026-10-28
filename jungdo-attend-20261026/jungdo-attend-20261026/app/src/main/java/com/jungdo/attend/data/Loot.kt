package com.jungdo.attend.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

/**
 * 보석 · 네임플레이트 (출석 보상)
 *  - 보석 396종 (assets/gem/001.webp … 396.webp): 등원/하원을 누를 때마다 gemChance(%) 확률로 하나씩
 *  - 네임플레이트 47종 (assets/plate/01.webp … 47.webp): 등원/하원 때마다 plateChance(%) 확률
 *  - 확률은 관리자 화면에서 바꾸고, Firestore config/loot 에 저장돼서 모든 기기에 똑같이 적용됨
 *  - 이미 가진 건 다시 안 나옴. 다 모으면 더 안 나옴
 */
object Loot {
    const val GEM_COUNT = 396
    const val PLATE_COUNT = 47
    /** 기본 확률 (%) — 서버 값이 아직 안 왔을 때 / 값이 없을 때 */
    const val DEFAULT_GEM_CHANCE = 100.0
    const val DEFAULT_PLATE_CHANCE = 30.0

    /** 지금 적용 중인 확률 (%, 0 ~ 100). Repo.startLootSync 가 서버 값으로 계속 갱신 */
    var gemChance by mutableDoubleStateOf(DEFAULT_GEM_CHANCE)
    var plateChance by mutableDoubleStateOf(DEFAULT_PLATE_CHANCE)

    /** 마지막으로 서버 값을 받은 상태 (관리자 화면에서 "지금 이 기기에 적용된 값"을 확인하는 용도) */
    var syncNote by androidx.compose.runtime.mutableStateOf("서버 값 기다리는 중…")

    private fun hit(percent: Double) = Math.random() * 100.0 < percent

    fun gemId(n: Int) = String.format(Locale.US, "%03d", n)
    fun plateId(n: Int) = String.format(Locale.US, "%02d", n)

    val allGems: List<String> by lazy { (1..GEM_COUNT).map { gemId(it) } }
    val allPlates: List<String> by lazy { (1..PLATE_COUNT).map { plateId(it) } }

    fun rollGem(owned: List<String>): String? {
        if (!hit(gemChance)) return null
        val have = owned.toSet()
        return allGems.filter { it !in have }.randomOrNull()
    }

    fun rollPlate(owned: List<String>): String? {
        if (!hit(plateChance)) return null
        val have = owned.toSet()
        return allPlates.filter { it !in have }.randomOrNull()
    }
}
