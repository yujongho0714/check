package com.jungdo.attend.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject

/**
 * 마이룸 (복셀 가구 하우징 + 마크/바람 캐릭터).
 *  - 화면은 assets/myroom (웹 화면) 을 앱 안에서 띄우고, 저장은 원생 문서의 "myroom" 글자(JSON)에 해요.
 *    myroom = { fur:[{id,k}], placed:[{id,k,x,z,rot}], npc:[캐릭터 키], ctl:"조종 캐릭터 키", nid:다음 번호 }
 *  - 관리자 설정(config/myroom): on = 켜기/끄기, drop = 등원·하원 때 가구가 나올 확률(%)
 *  - 관리자가 추가한 가구·캐릭터: myroomAssets 모음 (type=fur|skin)
 */
object MyRoom {
    const val FUR_PRICE = 1        // 가구 뽑기 1골드
    const val DEFAULT_DROP = 30.0  // 등원·하원 때 가구가 나올 확률 기본 30%

    var enabled by mutableStateOf(true)
    var dropChance by mutableDoubleStateOf(DEFAULT_DROP)
    /** 관리자가 추가한 에셋 (JSON 글자 그대로, 웹 화면에 넘김) */
    var custom by mutableStateOf<List<JSONObject>>(emptyList())

    /** 처음부터 들어 있는 가구 (키 → 등급). 가구 그림은 assets/myroom/fur.json */
    val builtin: Map<String, String> = mapOf(
        "bed1" to "노말", "bed2" to "노말", "bed3" to "레어", "bedd" to "에픽", "bedd2" to "유니크",
        "ch1" to "노말", "ch2" to "노말", "ch3" to "레어", "ch4" to "에픽", "sofa1" to "레어", "sofa2" to "에픽",
        "clo1" to "레어", "clo2" to "에픽", "shf1" to "노말", "shf2" to "레어", "pl1" to "노말", "pl2" to "노말", "pl3" to "레어",
        "clk" to "에픽", "tb1" to "레어", "tb2" to "노말", "tb3" to "노말", "tv" to "유니크", "pc" to "에픽", "game" to "유니크",
        "wb" to "레어", "och" to "노말", "desk" to "노말", "bin" to "노말", "cof" to "레어", "peng" to "유니크", "slide" to "레전드리",
        "dog" to "레전드리", "bike" to "유니크", "castle" to "레어", "lamp" to "에픽",
    )
    private val furName: Map<String, String> = mapOf(
        "bed1" to "나무 침대", "bed2" to "싱글 침대", "bed3" to "파스텔 침대", "bedd" to "더블 침대", "bedd2" to "큰 더블 침대",
        "ch1" to "나무 의자", "ch2" to "쿠션 의자", "ch3" to "둥근 의자", "ch4" to "안락 의자", "sofa1" to "소파", "sofa2" to "긴 소파",
        "clo1" to "옷장", "clo2" to "큰 옷장", "shf1" to "선반", "shf2" to "책 선반", "pl1" to "화분", "pl2" to "큰 화분", "pl3" to "꽃 화분",
        "clk" to "괘종시계", "tb1" to "큰 식탁", "tb2" to "커피 테이블", "tb3" to "협탁", "tv" to "TV", "pc" to "컴퓨터", "game" to "게임기",
        "wb" to "화이트보드", "och" to "사무 의자", "desk" to "공부 책상", "bin" to "휴지통", "cof" to "커피머신", "peng" to "흔들 펭귄",
        "slide" to "미끄럼틀", "dog" to "강아지", "bike" to "분홍 자전거", "castle" to "모래성", "lamp" to "가로등",
    )

    fun nameOf(k: String): String = furName[k] ?: custom.firstOrNull { it.optString("k") == k }?.optString("n")?.ifBlank { null } ?: "가구"

    /** 등급 확률로 가구 하나 고르기 (관리자가 추가한 가구 포함) */
    fun rollKey(): String {
        val pool = HashMap<String, String>(builtin)
        custom.filter { it.optString("type") == "fur" }.forEach { pool[it.optString("k")] = it.optString("r", "노말") }
        val r = Math.random()
        val by = when { r < .03 -> "레전드리"; r < .10 -> "유니크"; r < .25 -> "에픽"; r < .55 -> "레어"; else -> "노말" }
        val c = pool.filterValues { it == by }.keys.toList().ifEmpty { pool.keys.toList() }
        return c.random()
    }

    /** 등원·하원 때 가구 드롭 (켜져 있을 때만) */
    fun rollDrop(): String? = if (enabled && Math.random() * 100 < dropChance) rollKey() else null

    fun parse(json: String): JSONObject = runCatching { JSONObject(json.ifBlank { "{}" }) }.getOrElse { JSONObject() }

    /** 가구 하나를 가방에 넣은 새 myroom 글자와 그 번호 */
    fun withFur(json: String, k: String): Pair<String, Int> {
        val o = parse(json)
        val fur = o.optJSONArray("fur") ?: JSONArray()
        var nid = o.optInt("nid", 1)
        for (i in 0 until fur.length()) nid = maxOf(nid, fur.getJSONObject(i).optInt("id") + 1)
        fur.put(JSONObject().put("id", nid).put("k", k))
        o.put("fur", fur); o.put("nid", nid + 1)
        return o.toString() to nid
    }

    /** 가방에서 가구 하나 빼기 (놓은 자리도 같이). 없으면 null */
    fun withoutFur(json: String, id: Int): String? {
        val o = parse(json)
        val fur = o.optJSONArray("fur") ?: return null
        val keep = JSONArray(); var found = false
        for (i in 0 until fur.length()) { val f = fur.getJSONObject(i); if (f.optInt("id") == id) found = true else keep.put(f) }
        if (!found) return null
        o.put("fur", keep)
        o.optJSONArray("placed")?.let { pl ->
            val kp = JSONArray()
            for (i in 0 until pl.length()) { val p = pl.getJSONObject(i); if (p.optInt("id") != id) kp.put(p) }
            o.put("placed", kp)
        }
        return o.toString()
    }

    /** 웹 화면이 보낸 방 모습(놓은 가구·친구·조종 캐릭터)만 바꿔 넣기. 가방(fur)은 서버 값 그대로 */
    fun withLayout(json: String, layout: String): String {
        val o = parse(json)
        val l = parse(layout)
        listOf("placed", "npc").forEach { k -> l.optJSONArray(k)?.let { o.put(k, it) } }
        if (l.has("ctl")) o.put("ctl", l.optString("ctl"))
        return o.toString()
    }
}
