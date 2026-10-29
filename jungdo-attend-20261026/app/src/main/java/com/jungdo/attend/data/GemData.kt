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
}
