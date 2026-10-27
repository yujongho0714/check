package com.jungdo.attend.data

import com.google.firebase.firestore.Source
import kotlinx.coroutines.tasks.await

/**
 * 문자 문구 설정 — Firestore 에 저장되므로 어느 기기에서 고쳐도 바로 반영됩니다.
 *
 *  config/messages   : tplIn(등원 문구), tplOut(하원 문구)
 *  notices/{날짜}     : common(전체 공통 안내), byClass{ "3부": "..." } (부별 안내)
 *
 * 문구 안에 쓸 수 있는 칸
 *  {이름} {시각} {구분}(등원/하원) {부}(등원 시각으로 자동 판정한 부) {학년} {날짜}(10월 26일)
 *  {안내}(그날 안내 — 그 부의 안내가 있으면 그걸, 없으면 공통)
 */
data class MessageConfig(val tplIn: String = DEFAULT_IN, val tplOut: String = DEFAULT_OUT)

data class DayNotice(val common: String = "", val byClass: Map<String, String> = emptyMap()) {
    fun forClass(part: String): String =
        byClass[part]?.takeIf { it.isNotBlank() } ?: common
}

const val DEFAULT_IN = "[${DojangInfo.SHORT}] {이름} 수련생이 {시각}에 도장에 도착했습니다.\n{안내}"
const val DEFAULT_OUT = "[${DojangInfo.SHORT}] {이름} 수련생이 {시각}에 수업을 마치고 하원합니다.\n{안내}"

const val DEFAULT_REPLY = "{호칭} 안녕하세요 에고 ㅠㅠ 넵 알겠습니다 {이름} 푹 쉬고 내일 만나도록 하겠습니다 연락주셔서 감사합니다~!"

object Messages {
    private fun cfgDoc() = Repo.db.collection("config").document("messages")
    fun noticeDoc(day: String) = Repo.db.collection("notices").document(day)

    fun toConfig(m: Map<String, Any?>?) = MessageConfig(
        tplIn = (m?.get("tplIn") as? String)?.takeIf { it.isNotBlank() } ?: DEFAULT_IN,
        tplOut = (m?.get("tplOut") as? String)?.takeIf { it.isNotBlank() } ?: DEFAULT_OUT,
    )

    fun toNotice(m: Map<String, Any?>?): DayNotice = DayNotice(
        common = m?.get("common") as? String ?: "",
        byClass = (m?.get("byClass") as? Map<*, *>)
            ?.mapNotNull { (k, v) -> if (k is String && v is String) k to v else null }?.toMap() ?: emptyMap(),
    )

    fun saveConfig(c: MessageConfig) {
        cfgDoc().set(mapOf("tplIn" to c.tplIn, "tplOut" to c.tplOut), com.google.firebase.firestore.SetOptions.merge())
    }

    fun saveNotice(day: String, n: DayNotice) {
        noticeDoc(day).set(mapOf("common" to n.common, "byClass" to n.byClass.filterValues { it.isNotBlank() }))
    }

    /** 서버에서 최신값을 가져오고, 인터넷이 안 되면 기기에 저장된 값 사용 */
    private suspend fun getMap(ref: com.google.firebase.firestore.DocumentReference): Map<String, Any?>? =
        try { ref.get().await().data } catch (e: Exception) {
            try { ref.get(Source.CACHE).await().data } catch (e2: Exception) { null }
        }

    suspend fun loadConfig() = toConfig(getMap(cfgDoc()))

    // ── 결석 연락 답장 문구 ({이름} 필수) ──
    fun saveReplyTpl(t: String) { cfgDoc().set(mapOf("tplReply" to t), com.google.firebase.firestore.SetOptions.merge()) }
    suspend fun loadReplyTpl(): String = (getMap(cfgDoc())?.get("tplReply") as? String)?.takeIf { it.isNotBlank() } ?: DEFAULT_REPLY

    /** 한글 3글자 이름은 성을 빼고 부름 (김윤우 → 윤우). 그 외는 그대로 */
    fun givenName(full: String): String {
        val n = full.trim()
        return if (n.length == 3 && n.all { it in '가'..'힣' }) n.substring(1) else n
    }

    /** {이름}=성 뺀 이름(형제면 "윤우, 서연") {성명}=전체 이름 {호칭}=어머님/아버님 {날짜} */
    fun buildReply(tpl: String, fullNames: List<String>, label: String, day: String = Repo.today()): String {
        val dateText = "${day.substring(5, 7).toInt()}월 ${day.substring(8, 10).toInt()}일"
        return tpl
            .replace("{이름}", fullNames.joinToString(", ") { givenName(it) })
            .replace("{성명}", fullNames.joinToString(", "))
            .replace("{호칭}", label.ifBlank { "보호자님" }.let { if (it == "보호자") "보호자님" else it })
            .replace("{날짜}", dateText)
            .trim()
    }
    fun replyHasName(tpl: String) = tpl.contains("{이름}") || tpl.contains("{성명}")
    suspend fun loadNotice(day: String) = toNotice(getMap(noticeDoc(day)))

    /** 실제 문자 내용 만들기 */
    fun build(ev: AttendEvent, cfg: MessageConfig, notice: DayNotice, partLabel: String): String {
        val tpl = if (ev.type == EventType.IN) cfg.tplIn else cfg.tplOut
        val day = Repo.dayOf(ev.clientTime) // yyyy-MM-dd
        val dateText = "${day.substring(5, 7).toInt()}월 ${day.substring(8, 10).toInt()}일"
        val text = tpl
            .replace("{이름}", ev.name)
            .replace("{시각}", Repo.timeText(ev.clientTime))
            .replace("{구분}", EventType.label(ev.type))
            .replace("{부}", partLabel)
            .replace("{학년}", ev.grade)
            .replace("{날짜}", dateText)
            .replace("{안내}", notice.forClass(partLabel).trim())
        // 안내가 비어 있을 때 남는 빈 줄 정리
        return text.lines().joinToString("\n") { it.trimEnd() }.replace(Regex("\n{3,}"), "\n\n").trim()
    }

    /** 한글 SMS 한 통은 70자. 넘으면 여러 통으로 나뉘어 발송 */
    fun smsCount(text: String): Int = if (text.length <= 70) 1 else (text.length + 66) / 67
}
