package com.jungdo.attend.data

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** 학부모에게서 받은 문자 한 건 (Firestore: inbox/{id}) */
data class InboxMsg(
    val id: String,
    val day: String,
    val at: Long,
    val from: String,                 // 보낸 번호 (숫자만)
    val body: String,
    val studentIds: List<String>,     // 번호로 찾은 아이들 (등록 안 된 번호면 비어 있음)
    val names: String,                // 화면 표시용 "윤우, 서연"
    val label: String,                // 어머님/아버님… (등록 안 된 번호면 "")
    val kind: String,                 // InboxKind.*
    val handled: Boolean,
    val reply: String?,               // 보낸 답장 문자
    val replyError: String?,
)

object InboxKind {
    const val ABSENT = "ABSENT"       // 결석 키워드 → 사유 창이 떴던 문자
    const val SCHEDULE = "SCHEDULE"   // 시간·차량 → 카톡 알림
    const val OTHER = "OTHER"         // 키워드에 안 걸린 문자 (놓치기 쉬운 문자)
    const val UNKNOWN = "UNKNOWN"     // 등록 안 된 번호 + 결석 키워드
    fun label(k: String) = when (k) {
        ABSENT -> "결석 문자"; SCHEDULE -> "시간·차량"; UNKNOWN -> "등록 안 된 번호"; else -> "기타 문자"
    }
}

object Inbox {
    /** 앱이 화면을 닫아도 끝까지 처리해야 하는 일(답장 발송 등)용 */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun col() = Repo.db.collection("inbox")

    /** 저장하고 문서 id 를 바로 돌려줌 (인터넷이 없어도 나중에 자동 전송) */
    fun add(from: String, body: String, students: List<Student>, label: String, kind: String): String {
        val ref = col().document()
        val now = System.currentTimeMillis()
        ref.set(mapOf(
            "day" to Repo.dayOf(now), "at" to now, "from" to from, "body" to body,
            "studentIds" to students.map { it.id }, "names" to students.joinToString(", ") { it.name },
            "label" to label, "kind" to kind,
            "handled" to (kind == InboxKind.SCHEDULE),
            "createdAt" to FieldValue.serverTimestamp(),
        ))
        return ref.id
    }

    fun markHandled(id: String, handled: Boolean = true) {
        col().document(id).set(mapOf("handled" to handled), SetOptions.merge())
    }

    fun markReplied(id: String, text: String, error: String?) {
        col().document(id).set(mapOf("reply" to text, "replyError" to error, "handled" to true), SetOptions.merge())
    }

    fun delete(id: String) { col().document(id).delete() }

    fun toMsg(d: DocumentSnapshot) = InboxMsg(
        id = d.id,
        day = d.getString("day") ?: "",
        at = d.getLong("at") ?: 0L,
        from = d.getString("from") ?: "",
        body = d.getString("body") ?: "",
        studentIds = (d.get("studentIds") as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
        names = d.getString("names") ?: "",
        label = d.getString("label") ?: "",
        kind = d.getString("kind") ?: InboxKind.OTHER,
        handled = d.getBoolean("handled") ?: false,
        reply = d.getString("reply"),
        replyError = d.getString("replyError"),
    )
}

@Composable
fun rememberInboxRange(from: String, to: String): State<List<InboxMsg>> = produceState(emptyList<InboxMsg>(), from, to) {
    // where 조건은 day 범위 1개만 (색인 불필요), 정렬은 앱에서
    val reg = Inbox.col().whereGreaterThanOrEqualTo("day", from).whereLessThanOrEqualTo("day", to)
        .addSnapshotListener { snap, _ -> if (snap != null) value = snap.documents.map(Inbox::toMsg).sortedByDescending { it.at } }
    awaitDispose { reg.remove() }
}
