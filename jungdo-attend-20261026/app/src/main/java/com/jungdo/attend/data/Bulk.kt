package com.jungdo.attend.data

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue

/**
 * 단체 문자 + 그룹
 *  groups/{id} : name, studentIds[]            ← 국기원 심사, 줄넘기, 현장체험학습 … 자주 쓰는 묶음
 *  bulk/{id}   : title, items[{n,p,t}], results{ "0":"OK" | "실패 이유" }, status, createdMs, lastMs
 *               items 는 '받는 번호별로 이미 완성된 문자'. 문자 서버(관장님 폰)가 하나씩 보내고 results 에 기록
 */
data class Group(val id: String, val name: String, val studentIds: List<String>)

data class BulkItem(val names: String, val phone: String, val text: String)

data class BulkJob(
    val id: String,
    val title: String,
    val createdMs: Long,
    val status: String,
    val items: List<BulkItem>,
    val results: Map<Int, String>,
) {
    val sent get() = results.values.count { it == "OK" }
    val failedIdx get() = results.filterValues { it != "OK" }.keys.sorted()
}

object BulkStatus {
    const val PENDING = "PENDING"   // 서버가 아직 안 가져감 / 이어서 보낼 것
    const val SENDING = "SENDING"
    const val DONE = "DONE"
}

object Bulk {
    fun col() = Repo.db.collection("bulk")
    fun groups() = Repo.db.collection("groups")

    // ── 그룹 ──
    fun saveGroup(id: String?, name: String, studentIds: List<String>): String {
        val ref = if (id == null) groups().document() else groups().document(id)
        ref.set(mapOf("name" to name.trim(), "studentIds" to studentIds, "updatedAt" to FieldValue.serverTimestamp()))
        return ref.id
    }
    fun deleteGroup(id: String) { groups().document(id).delete() }
    fun toGroup(d: DocumentSnapshot) = Group(
        d.id, d.getString("name") ?: "",
        (d.get("studentIds") as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
    )

    // ── 문자 만들기 ──
    /**
     * 받는 번호별로 문자를 만든다. 같은 번호(형제)는 1통만 보내고 {이름}에 "윤우, 서연"처럼 같이 넣음.
     * 반환: (보낼 목록, 번호가 없어 못 보내는 아이 이름들)
     */
    fun buildItems(template: String, kids: List<Student>, allPhones: Boolean, day: String = Repo.today()): Pair<List<BulkItem>, List<String>> {
        val byPhone = LinkedHashMap<String, MutableList<Pair<Student, String>>>() // 번호 → (아이, 호칭)
        val noPhone = mutableListOf<String>()
        kids.forEach { s ->
            val all = s.phones.mapIndexed { i, p -> Repo.normalizePhone(p) to s.phoneLabel(i) }.filter { it.first.length >= 9 }.distinctBy { it.first }
            val use = if (allPhones) all else all.take(1)
            if (use.isEmpty()) noPhone += s.name
            use.forEach { (p, l) -> byPhone.getOrPut(p) { mutableListOf() } += s to l }
        }
        val items = byPhone.map { (phone, list) ->
            val text = Messages.buildReply(template, list.map { it.first.name }, list.first().second, day)
            BulkItem(list.joinToString(", ") { it.first.name }, phone, text)
        }
        return items to noPhone
    }

    fun create(title: String, items: List<BulkItem>) {
        val now = System.currentTimeMillis()
        col().add(mapOf(
            "title" to title, "createdMs" to now, "lastMs" to now, "status" to BulkStatus.PENDING,
            "items" to items.map { mapOf("n" to it.names, "p" to it.phone, "t" to it.text) },
            "results" to emptyMap<String, String>(),
        ))
    }

    /** 실패한 것만 다시 보내기: 실패 기록을 지우고 대기로 되돌리면 서버가 빠진 것만 이어서 보냄 */
    fun retryFailed(job: BulkJob) {
        val upd = HashMap<String, Any?>()
        job.failedIdx.forEach { upd["results.$it"] = FieldValue.delete() }
        upd["status"] = BulkStatus.PENDING
        upd["lastMs"] = System.currentTimeMillis()
        col().document(job.id).update(upd)
    }

    fun delete(id: String) { col().document(id).delete() }

    fun toJob(d: DocumentSnapshot): BulkJob {
        val items = (d.get("items") as? List<*>)?.mapNotNull { m ->
            (m as? Map<*, *>)?.let { BulkItem(it["n"] as? String ?: "", it["p"] as? String ?: "", it["t"] as? String ?: "") }
        } ?: emptyList()
        val res = (d.get("results") as? Map<*, *>)?.mapNotNull { (k, v) ->
            val i = (k as? String)?.toIntOrNull(); val s = v as? String
            if (i != null && s != null) i to s else null
        }?.toMap() ?: emptyMap()
        return BulkJob(d.id, d.getString("title") ?: "", d.getLong("createdMs") ?: 0L, d.getString("status") ?: BulkStatus.PENDING, items, res)
    }
}

@Composable
fun rememberGroups(): State<List<Group>> = produceState(emptyList<Group>()) {
    val reg = Bulk.groups().addSnapshotListener { snap, _ ->
        if (snap != null) value = snap.documents.map(Bulk::toGroup).sortedBy { it.name }
    }
    awaitDispose { reg.remove() }
}

@Composable
fun rememberBulkJobs(): State<List<BulkJob>> = produceState(emptyList<BulkJob>()) {
    val since = System.currentTimeMillis() - 60L * 86_400_000L // 최근 60일만
    val reg = Bulk.col().whereGreaterThanOrEqualTo("createdMs", since).addSnapshotListener { snap, _ ->
        if (snap != null) value = snap.documents.map(Bulk::toJob).sortedByDescending { it.createdMs }.take(20)
    }
    awaitDispose { reg.remove() }
}
