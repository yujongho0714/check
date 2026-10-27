package com.jungdo.attend.data

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue

/** 당첨 기록 한 건 (Firestore: wins/{id}) — paid=false 가 지급 대기 */
data class Win(
    val id: String,
    val studentId: String,
    val name: String,
    val grade: String,
    val prizeId: String,
    val prizeName: String,
    val time: Long,
    val day: String,
)

/**
 * 지급 처리.
 *  - wins/{id}.paid = true  → 지급 대기 목록에서 사라짐
 *  - payouts/{id}           → 지급내역 (누가·무엇을·언제 당첨 / 언제 지급). id 를 당첨 기록과 같게 해서 두 번 눌러도 한 건만 남음
 */
object Wins {
    fun payouts() = Repo.db.collection("payouts")

    fun toWin(d: DocumentSnapshot) = Win(
        id = d.id,
        studentId = d.getString("studentId") ?: "",
        name = d.getString("name") ?: "",
        grade = d.getString("grade") ?: "",
        prizeId = d.getString("prizeId") ?: "",
        prizeName = d.getString("prizeName") ?: "",
        time = d.getLong("clientTime") ?: 0L,
        day = d.getString("day") ?: "",
    )

    fun toPayout(d: DocumentSnapshot) = Payout(
        id = d.id,
        studentId = d.getString("studentId") ?: "",
        name = d.getString("name") ?: "",
        grade = d.getString("grade") ?: "",
        prizeId = d.getString("prizeId") ?: "",
        prizeName = d.getString("prizeName") ?: "",
        winTime = d.getLong("winTime") ?: 0L,
        paidAt = d.getLong("paidAt") ?: 0L,
        payDay = d.getString("payDay") ?: "",
    )

    /** 실수로 지급을 눌렀을 때: 지급내역을 지우고 다시 지급 대기로 되돌림 (재고는 그대로) */
    fun undo(p: Payout) {
        val batch = Repo.db.batch()
        batch.update(Repo.wins().document(p.id), mapOf<String, Any>("paid" to false, "paidAt" to FieldValue.delete()))
        batch.delete(payouts().document(p.id))
        batch.commit()
    }

    fun pay(w: Win) {
        val now = System.currentTimeMillis()
        val batch = Repo.db.batch()
        batch.update(Repo.wins().document(w.id), mapOf<String, Any>("paid" to true, "paidAt" to now))
        batch.set(payouts().document(w.id), mapOf(
            "winId" to w.id, "studentId" to w.studentId, "name" to w.name, "grade" to w.grade,
            "prizeId" to w.prizeId, "prizeName" to w.prizeName,
            "winTime" to w.time, "winDay" to w.day,
            "paidAt" to now, "payDay" to Repo.dayOf(now),
            "createdAt" to FieldValue.serverTimestamp(),
        ))
        batch.commit()
    }
}

/** 지급내역 한 건 (Firestore: payouts/{winId}) */
data class Payout(
    val id: String,
    val studentId: String,
    val name: String,
    val grade: String,
    val prizeId: String,
    val prizeName: String,
    val winTime: Long,
    val paidAt: Long,
    val payDay: String,
)

/** 지급 대기(paid=false) 당첨 목록, 오래된 것부터. 조건 1개라 색인 필요 없음 */
@Composable
fun rememberPendingWins(): State<List<Win>> = produceState(emptyList<Win>()) {
    val reg = Repo.wins().whereEqualTo("paid", false)
        .addSnapshotListener { snap, _ -> if (snap != null) value = snap.documents.map(Wins::toWin).sortedBy { it.time } }
    awaitDispose { reg.remove() }
}

/** 지급내역(최근순). day 범위 조건 1개라 색인 필요 없음 */
@Composable
fun rememberPayouts(from: String, to: String): State<List<Payout>> = produceState(emptyList<Payout>(), from, to) {
    val reg = Wins.payouts().whereGreaterThanOrEqualTo("payDay", from).whereLessThanOrEqualTo("payDay", to)
        .addSnapshotListener { snap, _ -> if (snap != null) value = snap.documents.map(Wins::toPayout).sortedByDescending { it.paidAt } }
    awaitDispose { reg.remove() }
}
