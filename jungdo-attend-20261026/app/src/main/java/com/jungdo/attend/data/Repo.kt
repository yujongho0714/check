package com.jungdo.attend.data

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Firestore 구조
 *  students/{id}        : name, grade, phones[], phoneLabels[], siblingKey, demo, status, joinedMonth, leftMonth …
 *  events/{id}          : studentId, name, grade, phones[], type(IN/OUT), status, clientTime, day(yyyy-MM-dd), part, error, message
 *  absences/{날짜_id}    : day, studentId, name, reason   ← 부모님 연락 받은 사유 결석
 *  config/messages      : 문자 문구 / notices/{날짜} : 그날 안내
 *  config/holidays      : days{ "2026-12-31": "도장 휴관" }
 *  status/kiosk, status/server : 1분마다 생존 신호
 */
object Repo {
    val db: FirebaseFirestore get() = FirebaseFirestore.getInstance()

    fun students() = db.collection("students")
    fun events() = db.collection("events")
    fun absences() = db.collection("absences")
    fun statusDoc(which: String) = db.collection("status").document(which)
    fun holidaysDoc() = db.collection("config").document("holidays")

    // ---------- 날짜/시간/번호 도우미 ----------
    fun dayOf(ms: Long): String = SimpleDateFormat("yyyy-MM-dd", Locale.KOREA).format(Date(ms))
    fun today(): String = dayOf(System.currentTimeMillis())
    fun timeText(ms: Long): String = SimpleDateFormat("a h:mm", Locale.KOREA).format(Date(ms)) // 오후 3:05
    fun hmText(ms: Long): String = SimpleDateFormat("HH:mm", Locale.KOREA).format(Date(ms))
    fun shiftDay(day: String, delta: Int): String {
        val f = SimpleDateFormat("yyyy-MM-dd", Locale.KOREA)
        val d = f.parse(day) ?: return day
        return f.format(Date(d.time + delta * 86_400_000L + 3_600_000L)) // +1h 안전장치
    }
    fun normalizePhone(s: String): String = s.filter { it.isDigit() }.let { if (it.startsWith("82") && it.length >= 11) "0" + it.drop(2) else it }
    fun prettyPhone(s: String): String = when (s.length) {
        11 -> "${s.substring(0, 3)}-${s.substring(3, 7)}-${s.substring(7)}"
        10 -> "${s.substring(0, 3)}-${s.substring(3, 6)}-${s.substring(6)}"
        else -> s
    }

    // ---------- 변환 ----------
    private fun strList(v: Any?): List<String> = (v as? List<*>)?.mapNotNull { it as? String } ?: emptyList()

    fun toStudent(d: DocumentSnapshot) = Student(
        id = d.id,
        name = d.getString("name") ?: "",
        grade = d.getString("grade") ?: d.getString("className") ?: "",
        phones = strList(d.get("phones")),
        phoneLabels = strList(d.get("phoneLabels")),
        memo = d.getString("memo") ?: "",
        siblingKey = d.getString("siblingKey") ?: "",
        demo = d.getBoolean("demo") ?: false,
        status = d.getString("status") ?: StudentStatus.ACTIVE,
        statusReason = d.getString("statusReason") ?: "",
        joinedMonth = d.getString("joinedMonth") ?: "",
        leftMonth = d.getString("leftMonth") ?: "",
        order = (d.getLong("order") ?: 0L).toInt(),
        attendNo = d.getString("attendNo") ?: "",
        school = d.getString("school") ?: "",
        fee = (d.getLong("fee") ?: 0L).toInt(),
    )

    fun studentData(s: Student): Map<String, Any?> = mapOf(
        "name" to s.name.trim(),
        "grade" to s.grade.trim(),
        "phones" to s.phones,
        "phoneLabels" to s.phoneLabels,
        "memo" to s.memo,
        "siblingKey" to s.siblingKey,
        "demo" to s.demo,
        "status" to s.status,
        "statusReason" to s.statusReason,
        "joinedMonth" to s.joinedMonth,
        "leftMonth" to s.leftMonth,
        "order" to s.order,
        "attendNo" to s.attendNo,
        "school" to s.school,
        "fee" to s.fee,
        "updatedAt" to FieldValue.serverTimestamp(),
    )

    fun toEvent(id: String, m: Map<String, Any?>) = AttendEvent(
        id = id,
        studentId = m["studentId"] as? String ?: "",
        name = m["name"] as? String ?: "",
        grade = (m["grade"] ?: m["className"]) as? String ?: "",
        phones = strList(m["phones"]),
        type = m["type"] as? String ?: EventType.IN,
        status = m["status"] as? String ?: Status.PENDING,
        clientTime = (m["clientTime"] as? Number)?.toLong() ?: 0L,
        day = m["day"] as? String ?: "",
        error = m["error"] as? String,
        message = m["message"] as? String,
    )

    fun toEvent(d: DocumentSnapshot) = toEvent(d.id, d.data ?: emptyMap())

    fun toAbsence(d: DocumentSnapshot) = Absence(
        id = d.id,
        day = d.getString("day") ?: "",
        studentId = d.getString("studentId") ?: "",
        name = d.getString("name") ?: "",
        reason = d.getString("reason") ?: "",
    )

    // ---------- 원생 ----------
    fun saveStudent(s: Student) {
        val joined = s.joinedMonth.ifBlank { Days.thisMonth() }
        val data = studentData(s.copy(joinedMonth = joined))
        if (s.id.isBlank()) students().add(data) else students().document(s.id).set(data, SetOptions.merge())
    }

    /** 재원 상태 바꾸기. 퇴관은 이번 달부터 출석부·출석체크에서 빠짐 (예전 달 기록은 그대로) */
    fun setStatus(s: Student, status: String, reason: String = "") {
        students().document(s.id).update(mapOf(
            "status" to status,
            "statusReason" to reason,
            "leftMonth" to if (status == StudentStatus.LEFT) Days.thisMonth() else "",
        ))
    }

    fun deleteStudent(id: String) {
        students().document(id).delete()
    }

    /** 일괄 등록(키오스크 관리자 화면): 같은 이름+학년이 있으면 덮어쓰기 */
    fun bulkUpsert(list: List<Student>, existing: List<Student>): Int {
        val byKey = existing.associateBy { "${it.name}|${it.grade}" }
        list.chunked(400).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { s ->
                val old = byKey["${s.name}|${s.grade}"]
                val ref = old?.let { students().document(it.id) } ?: students().document()
                val merged = (old ?: Student(joinedMonth = Days.thisMonth())).copy(name = s.name, grade = s.grade, phones = s.phones, phoneLabels = s.phoneLabels)
                batch.set(ref, studentData(merged), SetOptions.merge())
            }
            batch.commit()
        }
        return list.size
    }

    /** 형제 묶기: 같은 siblingKey 를 주고, 학부모 번호를 서로 합쳐서 똑같이 */
    fun linkSiblings(group: List<Student>) {
        if (group.size < 2) return
        val key = group.firstNotNullOfOrNull { it.siblingKey.ifBlank { null } } ?: ("g" + System.currentTimeMillis())
        val (phones, labels) = mergePhones(group)
        val batch = db.batch()
        group.forEach { batch.update(students().document(it.id), mapOf("siblingKey" to key, "phones" to phones, "phoneLabels" to labels)) }
        batch.commit()
    }

    fun mergePhones(group: List<Student>): Pair<List<String>, List<String>> {
        val phones = mutableListOf<String>(); val labels = mutableListOf<String>()
        group.forEach { s -> s.phones.forEachIndexed { i, p -> if (p.isNotBlank() && p !in phones) { phones += p; labels += s.phoneLabel(i) } } }
        return sortByLabel(phones, labels)
    }

    /** 어머님 → 아버님 → 그 외 순서 (첫 번째가 대표 번호) */
    fun sortByLabel(phones: List<String>, labels: List<String>): Pair<List<String>, List<String>> {
        fun rank(l: String) = when { l.contains("어머") || l == "모" -> 0; l.contains("아버") || l == "부" -> 1; else -> 2 }
        val idx = phones.indices.sortedBy { rank(labels.getOrElse(it) { "" }) }
        return idx.map { phones[it] } to idx.map { labels.getOrElse(it) { "보호자" } }
    }

    // ---------- 등원/하원 신호 (기기 A → Firestore → 기기 B) ----------
    fun addEvent(s: Student, type: String) {
        val now = System.currentTimeMillis()
        // await 하지 않음: 와이파이가 잠깐 끊겨도 Firestore 가 보관했다가 연결되면 자동 전송
        events().add(mapOf(
            "studentId" to s.id,
            "name" to s.name,
            "grade" to s.grade,
            "phones" to s.phones,
            "type" to type,
            "status" to Status.PENDING,
            "clientTime" to now,
            "day" to dayOf(now),
            "createdAt" to FieldValue.serverTimestamp(),
            "kioskModel" to Build.MODEL,
        ))
    }

    fun retry(eventId: String) {
        events().document(eventId).update(mapOf("status" to Status.PENDING, "error" to null, "retryAt" to System.currentTimeMillis()))
    }

    fun cancel(eventId: String) {
        events().document(eventId).delete()
    }

    // ---------- 사유 결석 ----------
    fun saveAbsences(students: List<Student>, days: List<String>, reason: String) {
        val batch = db.batch()
        students.forEach { s ->
            days.forEach { d ->
                batch.set(absences().document("${d}_${s.id}"), mapOf(
                    "day" to d, "studentId" to s.id, "name" to s.name, "reason" to reason.trim(),
                    "createdAt" to FieldValue.serverTimestamp(),
                ))
            }
        }
        batch.commit()
    }

    fun deleteAbsence(id: String) {
        absences().document(id).delete()
    }

    // ---------- 휴일 ----------
    fun saveCustomHoliday(day: String, name: String?) {
        if (name == null) holidaysDoc().update(com.google.firebase.firestore.FieldPath.of("days", day), FieldValue.delete())
        else holidaysDoc().set(mapOf("days" to mapOf(day to name)), SetOptions.merge())
    }

    fun heartbeat(which: String, extra: Map<String, Any?> = emptyMap()) {
        statusDoc(which).set(
            mapOf("lastSeen" to FieldValue.serverTimestamp(), "model" to Build.MODEL) + extra,
            SetOptions.merge(),
        )
    }
}

/** 상대 기기의 상태 */
/** 연결 문제 진단용: 마지막 Firestore 오류 (키오스크 빈 화면에 표시) */
object Diag { var error by androidx.compose.runtime.mutableStateOf("") }

data class DeviceStatus(val lastSeen: Long?, val running: Boolean, val model: String?)

// ---------- Compose 에서 실시간으로 구독하는 도우미 ----------

@Composable
fun rememberStudents(): State<List<Student>> = produceState(emptyList<Student>()) {
    val reg = Repo.students().addSnapshotListener { snap, err ->
        if (err != null) Diag.error = err.code.name + ": " + (err.message ?: "")
        else Diag.error = ""
        if (snap != null) value = snap.documents.map(Repo::toStudent)
            .sortedWith(compareBy<Student>({ gradeRank(it.grade) }, { it.grade }, { it.order }, { it.name }))
    }
    awaitDispose { reg.remove() }
}

@Composable
fun rememberEventsOfDay(day: String): State<List<AttendEvent>> = produceState(emptyList<AttendEvent>(), day) {
    // where 조건 1개만 써서 Firestore '색인 만들기'가 필요 없게 함 (정렬은 앱에서)
    val reg = Repo.events().whereEqualTo("day", day).addSnapshotListener { snap, _ ->
        if (snap != null) value = snap.documents.map { Repo.toEvent(it) }.sortedByDescending { it.clientTime }
    }
    awaitDispose { reg.remove() }
}

/** from ~ to (포함) 기간의 등원/하원 기록 */
@Composable
fun rememberEventsRange(from: String, to: String): State<List<AttendEvent>> = produceState(emptyList<AttendEvent>(), from, to) {
    val reg = Repo.events().whereGreaterThanOrEqualTo("day", from).whereLessThanOrEqualTo("day", to)
        .addSnapshotListener { snap, _ -> if (snap != null) value = snap.documents.map { Repo.toEvent(it) } }
    awaitDispose { reg.remove() }
}

@Composable
fun rememberAbsencesRange(from: String, to: String): State<List<Absence>> = produceState(emptyList<Absence>(), from, to) {
    val reg = Repo.absences().whereGreaterThanOrEqualTo("day", from).whereLessThanOrEqualTo("day", to)
        .addSnapshotListener { snap, _ -> if (snap != null) value = snap.documents.map(Repo::toAbsence) }
    awaitDispose { reg.remove() }
}

/** 관장님이 추가한 휴일을 받아와 Holidays.custom 에 반영. 값이 바뀌면 숫자가 올라감(화면 갱신용) */
@Composable
fun rememberCustomHolidays(): State<Map<String, String>> = produceState(Holidays.custom) {
    val reg = Repo.holidaysDoc().addSnapshotListener { snap, _ ->
        val m = (snap?.get("days") as? Map<*, *>)?.mapNotNull { (k, v) -> if (k is String && v is String) k to v else null }?.toMap() ?: emptyMap()
        Holidays.custom = m
        value = m
    }
    awaitDispose { reg.remove() }
}

@Composable
fun rememberDeviceStatus(which: String): State<DeviceStatus?> = produceState<DeviceStatus?>(null, which) {
    val reg = Repo.statusDoc(which).addSnapshotListener { snap, _ ->
        if (snap != null && snap.exists()) value = DeviceStatus(
            lastSeen = snap.getTimestamp("lastSeen")?.toDate()?.time,
            running = snap.getBoolean("running") ?: true,
            model = snap.getString("model"),
        )
    }
    awaitDispose { reg.remove() }
}

@Composable
fun rememberNow(periodMs: Long): State<Long> = produceState(System.currentTimeMillis(), periodMs) {
    while (true) {
        delay(periodMs)
        value = System.currentTimeMillis()
    }
}
