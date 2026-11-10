package com.jungdo.attend.data

/**
 * 마크(마인크래프트) 캐릭터 뽑기.
 *  - 스킨 그림은 assets/mc/000.png ~ 101.png (64x64 마크 스킨, 관장님이 모아 준 것)
 *  - 뽑기 1회 10골드, 이미 가진 캐릭터가 나오면 중복 환급 골드
 *  - 판매: 장착하지 않은 캐릭터 1개당 8골드
 */
object Mc {
    const val COUNT = 102
    const val SELL_GOLD = 8          // 캐릭터 되팔기 8골드
    val allKeys: List<String> = (0 until COUNT).map { "%03d".format(it) }
    const val PRICE = 10             // 캐릭터 뽑기 1회 10골드
    val price: Int get() = PRICE
    val refund: Int get() = Loot.dupRefund
    fun path(key: String) = "mc/$key.png"

    fun pull(owned: Collection<String>, n: Int, refundEach: Int): Dress.PullResult {
        val have = owned.toHashSet()
        val keys = ArrayList<String>()
        val fresh = ArrayList<String>()
        var dup = 0
        repeat(n.coerceAtLeast(0)) {
            val k = allKeys.random()
            keys.add(k)
            if (have.add(k)) fresh.add(k) else dup++
        }
        return Dress.PullResult(keys, fresh, dup, dup * refundEach)
    }

    /** 보석 판매 가격 (등급이 높을수록 비쌈) */
    fun gemSellGold(r: Rarity): Int = when (r) {
        Rarity.NORMAL -> 2
        Rarity.RARE -> 4
        Rarity.EPIC -> 8
        Rarity.UNIQUE -> 15
        Rarity.LEGENDARY -> 30
    }
}
