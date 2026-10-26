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

const val DEFAULT_IN = "[용인대 정도태권도 2관] {이름} 수련생이 {시각}에 도장에 도착했습니다.\n{안내}"
const val DEFAULT_OUT = "[용인대 정도태권도 2관] {이름} 수련생이 {시각}에 수업을 마치고 하원합니다.\n{안내}"

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
        cfgDoc().set(mapOf("tplIn" to c.tplIn, "tplOut" to c.tplOut))
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
