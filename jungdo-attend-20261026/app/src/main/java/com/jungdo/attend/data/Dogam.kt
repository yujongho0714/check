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

    fun newId(): String = "d" + System.currentTimeMillis().toString(36) + (Math.random() * 1000).toInt()
}
