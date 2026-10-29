package com.jungdo.attend.data

/**
 * 보석 등급 5단계.
 *  - argb : UI 색 (노말 회색 · 레어 파랑 · 에픽 보라 · 유니크 노랑 · 레전드리 초록)
 *  - goldMin ~ goldMax : 장착한 보석이 등원/하원 때 주는 골드 (구간 안에서 랜덤)
 *  - defaultWeight : 관리자가 아직 확률을 안 정했을 때 쓰는 등급별 획득 비율 (합 100)
 *  레전드리는 골드 외에 정해진 상품(마이쮸 등)도 줌 → 4단계에서 연결
 */
enum class Rarity(
    val code: String,
    val label: String,
    val argb: Long,
    val goldMin: Int,
    val goldMax: Int,
    val defaultWeight: Double,
) {
    NORMAL("normal", "노말", 0xFFB8BCC6, 1, 3, 60.0),
    RARE("rare", "레어", 0xFF4A90E2, 3, 5, 25.0),
    EPIC("epic", "에픽", 0xFF9B59E6, 5, 7, 10.0),
    UNIQUE("unique", "유니크", 0xFFF5C518, 7, 10, 4.0),
    LEGENDARY("legendary", "레전드리", 0xFF2ECC71, 10, 15, 1.0);

    /** 이 등급의 골드를 한 번 굴림 (goldMin ~ goldMax, 양 끝 포함) */
    fun rollGold(): Int = (goldMin..goldMax).random()

    companion object {
        /** 저장된 글자(code)로 등급 찾기. 모르는 값이면 null */
        fun ofOrNull(code: String?): Rarity? = values().firstOrNull { it.code == code }
        fun of(code: String?): Rarity = ofOrNull(code) ?: NORMAL
    }
}
