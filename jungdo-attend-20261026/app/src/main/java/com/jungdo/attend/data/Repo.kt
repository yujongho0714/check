package com.jungdo.attend.data

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
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
 *  config/loot          : gemChance(%), plateChance(%)  ← 보석·네임플레이트 얻을 확률
 *  prizes/{id}          : name, chance(%), message, stock(없으면 무제한), photo(base64)  ← 상품(마이쮸 등)
 *  wins/{id}            : studentId, name, prizeId, prizeName, day, clientTime, paid(false=지급 대기)  ← 당첨 기록
 *  payouts/{winId}      : winId, studentId, name, prizeName, winTime, paidAt, payDay  ← 지급내역 (지급 누를 때 기록)
 *  status/kiosk, status/server : 1분마다 생존 신호
 */
object Repo {
    val db: FirebaseFirestore get() = FirebaseFirestore.getInstance()

    fun students() = db.collection("students")
    fun events() = db.collection("events")
    fun absences() = db.collection("absences")
    fun statusDoc(which: String) = db.collection("status").document(which)
    fun holidaysDoc() = db.collection("config").document("holidays")
    fun lootDoc() = db.collection("config").document("loot")
    fun prizes() = db.collection("prizes")
    fun wins() = db.collection("wins")

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

    fun toStudent(d: DocumentSnapshot): Student {
        val stored = d.getString("grade") ?: d.getString("className") ?: ""
        val base = Student(
            id = d.id,
            name = d.getString("name") ?: "",
            grade = stored,
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
            age = (d.getLong("age") ?: 0L).toInt(),
            ageYear = (d.getLong("ageYear") ?: 0L).toInt(),
            exp = (d.getLong("exp") ?: 0L).toInt(),
            streak = (d.getLong("streak") ?: 0L).toInt(),
            streakDay = d.getString("streakDay") ?: "",
            inDay = d.getString("inDay") ?: "",
            outDay = d.getString("outDay") ?: "",
            gems = strList(d.get("gems")),
            plates = strList(d.get("plates")),
            plate = d.getString("plate") ?: "",
        )
        // 나이가 있으면 올해 학년을 계산해서 보여줌 (저장된 grade 글자는 옛 값일 수 있음)
        if (base.age > 0 && base.ageYear > 0) return base.copy(grade = GradeCalc.labelOf(base.curAge))
        // 나이가 없는 옛 데이터: 정확한 학년(2학년 등)이면 올해 그 나이로 보고 시작 (migrateAges 가 저장해 줌)
        val a = GradeCalc.ageOf(stored)
        return if (a > 0) base.withAge(a) else base
    }

    private var agesMigrated = false
    /** 업데이트 후 처음 한 번: 나이가 없는 원생 중 정확한 학년이 적힌 원생에게 나이(올해 기준)를 저장 → 내년부터 자동 진급 */
    fun migrateAges(docs: List<DocumentSnapshot>) {
        if (agesMigrated || docs.isEmpty()) return
        agesMigrated = true
        val year = GradeCalc.thisYear()
        val todo = docs.filter { (it.getLong("age") ?: 0L) <= 0L && GradeCalc.ageOf(it.getString("grade") ?: "") > 0 }
        todo.chunked(400).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { d ->
                batch.update(students().document(d.id), mapOf("age" to GradeCalc.ageOf(d.getString("grade") ?: ""), "ageYear" to year))
            }
            batch.commit()
        }
    }

    fun studentData(s: Student): Map<String, Any?> = mapOf(
        "name" to s.name.trim(),
        "grade" to s.grade.trim(),
        "age" to s.age,
        "ageYear" to s.ageYear,
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
        dlvOk = (m["dlvOk"] as? Number)?.toInt() ?: 0,
        dlvFail = (m["dlvFail"] as? Number)?.toInt() ?: 0,
        dlvNeed = (m["dlvNeed"] as? Number)?.toInt() ?: 0,
        dlvError = m["dlvError"] as? String,
        matchedPhone = m["matchedPhone"] as? String,
        sentMs = (m["sentMs"] as? Number)?.toLong() ?: 0L,
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

    /**
     * 등원/하원을 눌렀을 때 경험치 + 보석 주기. 같은 날 같은 버튼은 한 번만.
     * 보석·네임플레이트는 관리자가 정한 확률(Loot.gemChance / plateChance)로 (이미 가진 건 제외). 처음 얻은 네임플레이트는 자동으로 씀.
     * 상품(마이쮸 등)도 같이 추첨: 관리자가 정한 상품별 확률로 최대 1개 (Prizes.roll). 당첨되면 wins 에 기록.
     * (경험치·보석 칸은 studentData 에 넣지 않아서 명단 저장이 덮어쓰지 않음)
     */
    fun awardExp(s: Student, type: String): AwardResult {
        val p = Rank.plan(s, type, today())
        val base = p.result.copy(equipped = s.plate)
        if (p.fields.isEmpty() || s.id.isBlank()) return base
        val gem = Loot.rollGem(s.gems)
        val plate = Loot.rollPlate(s.plates)
        val prize = Prizes.roll()
        val f = HashMap<String, Any>(p.fields)
        if (gem != null) f["gems"] = FieldValue.arrayUnion(gem)
        if (plate != null) {
            f["plates"] = FieldValue.arrayUnion(plate)
            if (s.plate.isBlank()) f["plate"] = plate
        }
        students().document(s.id).update(f)
        if (prize != null) { Prizes.consume(prize.id); runCatching { recordWin(s, prize) } }
        return base.copy(
            gemId = gem, plateId = plate,
            prize = prize?.let { PrizeWin(it.id, it.name, it.message, it.photo) },
            equipped = if (s.plate.isBlank() && plate != null) plate else s.plate,
        )
    }

    /** 상품 당첨 기록 (지급 대기 = paid false) + 재고가 있는 상품이면 하나 줄임 */
    fun recordWin(s: Student, p: Prize) {
        val now = System.currentTimeMillis()
        val batch = db.batch()
        batch.set(wins().document(), mapOf(
            "studentId" to s.id, "name" to s.name, "grade" to s.grade,
            "prizeId" to p.id, "prizeName" to p.name,
            "clientTime" to now, "day" to dayOf(now),
            "paid" to false,
            "createdAt" to FieldValue.serverTimestamp(),
        ))
        if (p.stock != null) batch.update(prizes().document(p.id), mapOf<String, Any>("stock" to FieldValue.increment(-1)))
        batch.commit()
    }

    /** 키오스크에서 본인이 네임플레이트 바꾸기 ("" 이면 안 씀) */
    fun setPlate(id: String, plateId: String) {
        students().document(id).update(mapOf("plate" to plateId))
    }

    /** 관장님이 경험치를 직접 고칠 때 */
    fun setExp(id: String, exp: Int) {
        students().document(id).update(mapOf("exp" to exp.coerceIn(0, Rank.MAX_EXP)))
    }

    /** 재원 상태 바꾸기. 퇴관은 이번 달부터 출석부·출석체크에서 빠짐 (예전 달 기록은 그대로) */
    fun setStatus(s: Student, status: String, reason: String = "") {
        students().document(s.id).update(mapOf(
            "status" to status,
            "statusReason" to reason,
            "leftMonth" to if (status == StudentStatus.LEFT) Days.thisMonth() else "",
        ))
    }

    /** 학부모 번호 추가 (이미 있으면 무시) */
    fun addPhone(s: Student, number: String, label: String) {
        val n = normalizePhone(number)
        if (n.length < 9 || s.phones.any { normalizePhone(it) == n }) return
        val (p, l) = sortByLabel(s.phones + n, (s.phones.indices.map { s.phoneLabel(it) }) + label)
        students().document(s.id).update(mapOf("phones" to p, "phoneLabels" to l))
    }

    fun deleteStudent(id: String) {
        students().document(id).delete()
    }

    /** 일괄 등록(키오스크 관리자 화면): 같은 이름+학년이 있으면 덮어쓰기 */
    fun bulkUpsert(list: List<Student>, existing: List<Student>): Int {
        val byKey = existing.associateBy { "${it.name}|${it.grade}" }
        val byName = existing.groupBy { it.name }
        list.chunked(400).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { s ->
                val old = byKey["${s.name}|${s.grade}"] ?: byName[s.name]?.singleOrNull()
                val ref = old?.let { students().document(it.id) } ?: students().document()
                val merged = GradeCalc.applyLabel(old ?: Student(joinedMonth = Days.thisMonth()), s.grade)
                    .copy(name = s.name, phones = s.phones, phoneLabels = s.phoneLabels)
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
    /**
     * @param typedNo 아이가 키오스크에 입력한 번호(보통 뒷 4자리). 등록된 번호 중 뒷자리가 같은 번호가 있으면
     *                그 번호로만 문자를 보냄 (어머님 번호로 눌렀는데 아버님께도 가는 일 방지). 출결번호로 눌렀으면 기존 설정대로.
     */
    fun addEvent(s: Student, type: String, typedNo: String? = null) {
        val now = System.currentTimeMillis()
        val n = typedNo?.trim().orEmpty()
        val matched: String? = if (n.length == 4 && n != s.attendNo) {
            s.phones.map(::normalizePhone).filter { it.length >= 9 && it.takeLast(4) == n }.distinct().singleOrNull()
        } else null
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
            "matchedPhone" to matched,
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

    // ---------- 보석·네임플레이트 확률 ----------
    /** 서버의 확률 값을 지켜보다가 Loot 에 반영 (로그인하면 시작, 모든 기기 공통) */
    fun startLootSync(): ListenerRegistration = lootDoc().addSnapshotListener { snap, err ->
        if (err != null) { Loot.syncNote = "⚠ 서버 연결 오류: ${err.message ?: err.code}"; return@addSnapshotListener }
        if (snap == null) return@addSnapshotListener
        applyLoot(snap)
    }

    private fun applyLoot(snap: DocumentSnapshot) {
        // getDouble 은 정수(10)로 저장돼 있어도 읽혀요. 값이 없을 때만 기본값
        Loot.gemChance = (snap.getDouble("gemChance") ?: Loot.DEFAULT_GEM_CHANCE).coerceIn(0.0, 100.0)
        Loot.plateChance = (snap.getDouble("plateChance") ?: Loot.DEFAULT_PLATE_CHANCE).coerceIn(0.0, 100.0)
        val fromCache = snap.metadata.isFromCache
        Loot.syncNote = (if (fromCache) "기기에 저장된 값 사용 중 (서버 확인 전) · " else "서버 값 적용됨 · ") + timeText(System.currentTimeMillis())
    }

    /**
     * 확률·상품을 서버에서 지금 바로 다시 받아 적용 (실시간 감시가 늦거나 끊겨 있어도 최신 값으로 뽑도록).
     * 출결번호를 입력하는 순간 + 관리자 화면의 [서버에서 다시 받기] 에서 부름. 인터넷이 없으면 조용히 무시.
     */
    fun refreshLootNow() {
        lootDoc().get(com.google.firebase.firestore.Source.SERVER)
            .addOnSuccessListener { applyLoot(it) }
            .addOnFailureListener { Loot.syncNote = "⚠ 서버에서 못 받았어요 (인터넷 확인) · 지금은 기기 값 사용" }
        prizes().get(com.google.firebase.firestore.Source.SERVER)
            .addOnSuccessListener { snap -> Prizes.list = snap.documents.map(::toPrize).sortedBy { it.name } }
    }

    fun saveLootConfig(gem: Double, plate: Double) {
        lootDoc().set(mapOf("gemChance" to gem.coerceIn(0.0, 100.0), "plateChance" to plate.coerceIn(0.0, 100.0)), SetOptions.merge())
    }

    // ---------- 상품(마이쮸 등) ----------
    fun toPrize(d: DocumentSnapshot) = Prize(
        id = d.id,
        name = d.getString("name") ?: "",
        chance = d.getDouble("chance") ?: 0.0,
        message = d.getString("message") ?: "",
        stock = d.getLong("stock")?.toInt(),
        photo = d.getString("photo") ?: "",
    )

    /** 서버의 상품 목록을 지켜보다가 Prizes 에 반영 (로그인하면 시작, 모든 기기 공통) */
    fun startPrizeSync(): ListenerRegistration = prizes().addSnapshotListener { snap, _ ->
        if (snap == null) return@addSnapshotListener
        Prizes.list = snap.documents.map(::toPrize).sortedBy { it.name }
    }

    /** 새 상품이면 id 가 비어 있음. stock 이 null 이면 무제한 */
    fun savePrize(p: Prize) {
        val data = mapOf(
            "name" to p.name.trim(), "chance" to p.chance.coerceIn(0.0, 100.0), "message" to p.message.trim(),
            "stock" to p.stock, "photo" to p.photo, "updatedAt" to FieldValue.serverTimestamp(),
        )
        if (p.id.isBlank()) prizes().add(data) else prizes().document(p.id).set(data)
    }

    fun deletePrize(id: String) {
        prizes().document(id).delete()
    }

    fun heartbeat(which: String, extra: Map<String, Any?> = emptyMap()) {
        statusDoc(which).set(
            mapOf("lastSeen" to FieldValue.serverTimestamp(), "model" to Build.MODEL) + extra,
            SetOptions.merge(),
        )
    }
}

/** 상대 기기의 상태 */
data class DeviceStatus(val lastSeen: Long?, val running: Boolean, val model: String?)

// ---------- Compose 에서 실시간으로 구독하는 도우미 ----------

@Composable
fun rememberStudents(): State<List<Student>> = produceState(emptyList<Student>()) {
    val reg = Repo.students().addSnapshotListener { snap, _ ->
        if (snap != null) {
            Repo.migrateAges(snap.documents)
            value = snap.documents.map(Repo::toStudent)
                .sortedWith(compareBy<Student>({ gradeRank(it.grade) }, { it.grade }, { it.order }, { it.name }))
        }
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
