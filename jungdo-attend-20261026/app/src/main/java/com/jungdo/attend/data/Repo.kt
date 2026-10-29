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
 *  config/loot          : plateChance(%)  ← 네임플레이트 얻을 확률 (보석은 아래 rarityChances 합)
 *                         dressChance(%) 출석 옷 드롭, rarityChances{normal,rare,epic,unique,legendary} 등급별 보석 확률(%),
 *                         gachaPrice 뽑기 1회 골드, dupRefund 중복 환급 골드
 *  config/dogam          : sets[ {id, name, gems["001",…](1~5개), goldIn, goldOut} ]  ← 도감 (보석 세트 추가 골드)
 *  config/gemRarity     : r{ "001": "rare", … }  ← 보석별 등급 (없으면 노말)
 *                         on["001", …]  ← 관리자가 사용 지정한 보석 (칸이 없으면 396종 전부 사용)
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
    fun gemRarityDoc() = db.collection("config").document("gemRarity")
    fun dogamDoc() = db.collection("config").document("dogam")
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
            retryIn = d.getString("retryIn") ?: "",
            retryOut = d.getString("retryOut") ?: "",
            prizeDay = d.getString("prizeDay") ?: "",
            gems = strList(d.get("gems")),
            plates = strList(d.get("plates")),
            plate = d.getString("plate") ?: "",
            gold = (d.getLong("gold") ?: 0L).toInt(),
            gem = d.getString("gem") ?: "",
            dress = strList(d.get("dress")),
            dressEquip = (d.get("dressEquip") as? Map<*, *>)
                ?.mapNotNull { e -> val k = e.key as? String; val v = e.value as? String; if (k != null && v != null) k to v else null }
                ?.toMap() ?: emptyMap(),
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
    /** (임시) 이 날짜 하루만: 패치 전에 이미 눌러서 보상을 못 받은 친구가 보상을 딱 한 번 더 받을 수 있음 (등원·하원 합쳐서 1회, 경험치·연속출석은 그대로) */
    const val RETRY_DAY = "2026-10-29"

    fun retryEligible(s: Student, type: String): Boolean {
        if (s.id.isBlank() || today() != RETRY_DAY) return false
        val already = if (type == EventType.IN) s.inDay == RETRY_DAY else s.outDay == RETRY_DAY
        val used = s.retryIn == RETRY_DAY || s.retryOut == RETRY_DAY   // 등원·하원 합쳐서 한 번만
        return already && !used
    }

    fun awardExp(s: Student, type: String): AwardResult {
        val retry = retryEligible(s, type)
        val p = Rank.plan(s, type, today())
        val base = p.result.copy(equipped = s.plate)
        if ((p.fields.isEmpty() && !retry) || s.id.isBlank()) return base
        val gem = Loot.rollGem(s.gems)
        val plate = Loot.rollPlate(s.plates)
        val f = HashMap<String, Any>(p.fields)
        if (retry) f[if (type == EventType.IN) "retryIn" else "retryOut"] = RETRY_DAY
        if (gem != null) {
            f["gems"] = FieldValue.arrayUnion(gem)
            if (s.gem.isBlank()) f["gem"] = gem   // 처음 얻은 보석은 자동 장착
        }
        // 장착한 보석(처음 얻은 보석이면 방금 자동 장착된 것)의 골드 지급 — 등원/하원마다 한 번 (위 early return 덕분에 같은 날 중복 지급 없음)
        val equippedGem = s.gem.ifBlank { gem ?: "" }
        val gold = Loot.goldFor(equippedGem)
        // 옷 드롭: 새 옷이면 추가, 이미 가진 옷이면 환급 골드 (뽑기와 같은 규칙)
        val dressKey = Loot.rollDress()
        val dressDup = dressKey != null && dressKey in s.dress
        val dressRefund = if (dressDup) Loot.dupRefund.coerceAtLeast(0) else 0
        if (dressKey != null && !dressDup) f["dress"] = FieldValue.arrayUnion(dressKey)
        // 도감: 방금 얻은 보석까지 포함해서, 전부 모은 도감의 등원/하원 추가 골드
        val dogamGold = Dogam.bonusFor(type, s.gems + listOfNotNull(gem))
        val totalGold = gold + dressRefund + dogamGold
        if (totalGold > 0) f["gold"] = FieldValue.increment(totalGold.toLong())
        // 상품 추첨: 한 번만 굴려서 상품별 구간 → (장착 보석이 레전드리면 그 뒤에 레전드리 보너스 구간이 이어짐)
        // 오늘 이미 상품에 당첨된 원생(등원에서 당첨 → 하원)은 오늘 상품 추첨 안 함. 원생 기록(prizeDay)에 직접 남겨서 확실하게 막음
        val legendEquipped = equippedGem.isNotBlank() && Loot.rarityOf(equippedGem) == Rarity.LEGENDARY
        val prize = if (s.prizeDay == today()) null else Prizes.roll(s.id, legendEquipped)
        if (prize != null) f["prizeDay"] = today()
        if (plate != null) {
            f["plates"] = FieldValue.arrayUnion(plate)
            if (s.plate.isBlank()) f["plate"] = plate
        }
        students().document(s.id).update(f)
        if (prize != null) { Prizes.consume(prize.id); Prizes.markWon(s.id); runCatching { recordWin(s, prize) } }
        return base.copy(
            gemId = gem, plateId = plate, gold = gold, dogamGold = dogamGold,
            dressKey = dressKey, dressDup = dressDup, dressRefund = dressRefund,
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
        Loot.plateChance = (snap.getDouble("plateChance") ?: Loot.DEFAULT_PLATE_CHANCE).coerceIn(0.0, 100.0)
        Loot.dressChance = (snap.getDouble("dressChance") ?: Loot.DEFAULT_DRESS_CHANCE).coerceIn(0.0, 100.0)
        Loot.gachaPrice = (snap.getLong("gachaPrice") ?: Loot.DEFAULT_GACHA_PRICE.toLong()).toInt().coerceAtLeast(0)
        Loot.dupRefund = (snap.getLong("dupRefund") ?: Loot.DEFAULT_DUP_REFUND.toLong()).toInt().coerceAtLeast(0)
        Prizes.legendaryChance = (snap.getDouble("legendPrizeChance") ?: 0.0).coerceIn(0.0, 100.0)
        Prizes.legendaryIds = (snap.get("legendPrizeIds") as? List<*>)?.mapNotNull { it as? String }?.toSet() ?: emptySet()
        // 등급별 확률(%): 새 값(rarityChances)이 있으면 그대로. 없으면 예전 설정(보석 확률 × 등급 비율)을 같은 확률로 환산
        val rc = snap.get("rarityChances") as? Map<*, *>
        Loot.rarityChances = if (rc != null) {
            Rarity.values().associateWith { r -> ((rc[r.code] as? Number)?.toDouble() ?: 0.0).coerceAtLeast(0.0) }
        } else {
            val gc = (snap.getDouble("gemChance") ?: Loot.DEFAULT_GEM_CHANCE).coerceIn(0.0, 100.0)
            val w = snap.get("rarityWeights") as? Map<*, *>
            val ws = Rarity.values().associateWith { r -> ((w?.get(r.code) as? Number)?.toDouble() ?: r.defaultWeight).coerceAtLeast(0.0) }
            val sum = ws.values.sum()
            Rarity.values().associateWith { r -> if (sum > 0) gc * (ws[r] ?: 0.0) / sum else 0.0 }
        }
        val fromCache = snap.metadata.isFromCache
        Loot.syncNote = (if (fromCache) "기기에 저장된 값 사용 중 (서버 확인 전) · " else "서버 값 적용됨 · ") + timeText(System.currentTimeMillis())
    }

    /**
     * 확률·상품을 서버에서 지금 바로 다시 받아 적용 (실시간 감시가 늦거나 끊겨 있어도 최신 값으로 뽑도록).
     * 출결번호를 입력하는 순간 + 관리자 화면의 [서버에서 다시 받기] 에서 부름. 인터넷이 없으면 조용히 무시.
     */
    fun refreshLootNow() {
        gemRarityDoc().get(com.google.firebase.firestore.Source.SERVER)
            .addOnSuccessListener { applyGemRarity(it) }
        lootDoc().get(com.google.firebase.firestore.Source.SERVER)
            .addOnSuccessListener { applyLoot(it) }
            .addOnFailureListener { Loot.syncNote = "⚠ 서버에서 못 받았어요 (인터넷 확인) · 지금은 기기 값 사용" }
        prizes().get(com.google.firebase.firestore.Source.SERVER)
            .addOnSuccessListener { snap -> Prizes.list = snap.documents.map(::toPrize).sortedBy { it.name } }
    }

    fun saveLootConfig(plate: Double) {
        lootDoc().set(mapOf("plateChance" to plate.coerceIn(0.0, 100.0)), SetOptions.merge())
    }

    // ---------- 드레스업: 등급·뽑기·골드 ----------
    /** 보석별 등급을 지켜보다가 Loot 에 반영 (로그인하면 시작, 모든 기기 공통) */
    fun startGemRaritySync(): ListenerRegistration = gemRarityDoc().addSnapshotListener { snap, err ->
        if (err != null || snap == null) return@addSnapshotListener
        applyGemRarity(snap)
    }

    private fun applyGemRarity(snap: DocumentSnapshot) {
        val m = snap.get("r") as? Map<*, *>
        Loot.gemRarity = m?.mapNotNull { e ->
            val id = e.key as? String ?: return@mapNotNull null
            val r = Rarity.ofOrNull(e.value as? String) ?: return@mapNotNull null
            id to r
        }?.toMap() ?: emptyMap()
        // on 칸이 있으면 그 보석만 사용, 없으면(지정한 적 없음) 전부 사용
        val on = snap.get("on") as? List<*>
        Loot.gemEnabled = on?.mapNotNull { it as? String }?.toSet()
    }

    // ───────────── 도감 (config/dogam) ─────────────
    fun startDogamSync(): ListenerRegistration = dogamDoc().addSnapshotListener { snap, err ->
        if (err != null || snap == null) return@addSnapshotListener
        val arr = snap.get("sets") as? List<*>
        Dogam.sets = arr?.mapNotNull { e ->
            val m = e as? Map<*, *> ?: return@mapNotNull null
            val id = m["id"] as? String ?: return@mapNotNull null
            val gems = (m["gems"] as? List<*>)?.mapNotNull { it as? String }.orEmpty().take(Dogam.MAX_GEMS)
            DogamSet(
                id, (m["name"] as? String).orEmpty(), gems,
                ((m["goldIn"] as? Number)?.toInt() ?: 0).coerceAtLeast(0),
                ((m["goldOut"] as? Number)?.toInt() ?: 0).coerceAtLeast(0),
            )
        }.orEmpty()
    }

    /** 관리자: 도감 목록 전체 저장 (추가·수정·삭제 모두 이걸로) */
    fun saveDogam(list: List<DogamSet>) {
        Dogam.sets = list   // 이 기기에는 바로 반영
        dogamDoc().set(mapOf("sets" to list.map {
            mapOf("id" to it.id, "name" to it.name, "gems" to it.gems, "goldIn" to it.goldIn.toLong(), "goldOut" to it.goldOut.toLong())
        }))
    }

    /** 관리자: 고른 보석들의 사용을 한꺼번에 켜기(on=true) / 끄기(on=false) */
    fun saveGemEnabled(ids: Collection<String>, on: Boolean) {
        if (ids.isEmpty()) return
        val cur = Loot.gemEnabled
        if (cur == null) {
            if (on) return   // 지정한 적이 없으면 이미 전부 사용중
            // 처음 지정: 전부 - 끌 보석 = 사용할 보석 목록으로 저장
            gemRarityDoc().set(mapOf("on" to (Loot.allGems.toSet() - ids.toSet()).sorted()), SetOptions.merge())
        } else {
            val v = if (on) FieldValue.arrayUnion(*ids.toTypedArray()) else FieldValue.arrayRemove(*ids.toTypedArray())
            gemRarityDoc().set(mapOf<String, Any>("on" to v), SetOptions.merge())
        }
    }

    /** 관리자: "이 보석들만 사용" (나머지는 전부 끔) */
    fun saveGemEnabledOnly(ids: Collection<String>) {
        gemRarityDoc().set(mapOf("on" to ids.toSet().sorted()), SetOptions.merge())
    }

    /** 관리자: 지정 풀기 → 다시 396종 전부 사용 */
    fun clearGemEnabled() {
        gemRarityDoc().set(mapOf<String, Any>("on" to FieldValue.delete()), SetOptions.merge())
    }

    /** 관리자: 고른 보석들을 한 번에 같은 등급으로 */
    fun saveGemRarity(ids: Collection<String>, rarity: Rarity) {
        if (ids.isEmpty()) return
        gemRarityDoc().set(mapOf("r" to ids.associateWith { rarity.code }), SetOptions.merge())
    }

    /** 관리자: 등급별 획득 비율 */
    fun saveRarityChances(w: Map<Rarity, Double>) {
        lootDoc().set(mapOf("rarityChances" to w.entries.associate { it.key.code to it.value.coerceIn(0.0, 100.0) }), SetOptions.merge())
    }

    /** 관리자: 레전드리 보석 장착자가 받는 보너스 상품 (확률 %, 상품 id 목록). 확률 0 이면 꺼짐 */
    fun saveLegendaryPrize(chance: Double, prizeIds: Collection<String>) {
        lootDoc().set(mapOf(
            "legendPrizeChance" to chance.coerceIn(0.0, 100.0),
            "legendPrizeIds" to prizeIds.toSet().sorted(),
        ), SetOptions.merge())
    }

    /** 관리자: 뽑기 가격 / 중복 환급 / 출석 옷 드롭 확률 */
    fun saveGachaConfig(price: Int, refund: Int, dressChance: Double) {
        lootDoc().set(mapOf(
            "gachaPrice" to price.coerceAtLeast(0),
            "dupRefund" to refund.coerceAtLeast(0),
            "dressChance" to dressChance.coerceIn(0.0, 100.0),
        ), SetOptions.merge())
    }

    /** 장착 보석 바꾸기 ("" 이면 해제) */
    fun setGem(id: String, gemId: String) {
        students().document(id).update(mapOf("gem" to gemId))
    }

    /** 골드 더하기/빼기 (여러 기기에서 동시에 해도 안전하게 서버에서 계산) */
    fun addGold(id: String, delta: Int) {
        if (delta != 0) students().document(id).update(mapOf<String, Any>("gold" to FieldValue.increment(delta.toLong())))
    }

    /** 뽑기/드롭 결과 반영: 새 옷 추가 + 골드 변화(뽑기 비용은 -, 환급은 +) */
    fun grantDress(id: String, newKeys: List<String>, goldDelta: Int) {
        val f = HashMap<String, Any>()
        if (newKeys.isNotEmpty()) f["dress"] = FieldValue.arrayUnion(*newKeys.toTypedArray())
        if (goldDelta != 0) f["gold"] = FieldValue.increment(goldDelta.toLong())
        if (f.isNotEmpty()) students().document(id).update(f)
    }

    /**
     * 뽑기 (10단계). 서버에 저장된 골드·옷 기준으로 한 번에 처리해요 (여러 기기에서 눌러도 골드가 마이너스가 안 됨).
     * count > 0 : 그 횟수만큼 / count <= 0 : "전부 뽑기" = 가진 골드로 가능한 만큼 (최대 100번)
     * 끝나면 onDone(결과, null), 못 뽑으면 onDone(null, 이유 글)
     */
    fun pullDress(id: String, count: Int, price: Int, refund: Int, onDone: (Dress.PullResult?, String?) -> Unit) {
        val ref = students().document(id)
        db.runTransaction { tx ->
            val d = tx.get(ref)
            val gold = (d.getLong("gold") ?: 0L).toInt()
            val owned = strList(d.get("dress"))
            val n = if (count > 0) count else if (price > 0) (gold / price).coerceAtMost(100) else 100
            if (n <= 0 || gold < n * price) return@runTransaction null
            val r = Dress.pull(owned, n, refund)
            val f = HashMap<String, Any>()
            if (r.newKeys.isNotEmpty()) f["dress"] = FieldValue.arrayUnion(*r.newKeys.toTypedArray())
            val delta = r.refund - n * price
            if (delta != 0) f["gold"] = FieldValue.increment(delta.toLong())
            if (f.isNotEmpty()) tx.update(ref, f)
            r
        }.addOnSuccessListener { r -> if (r == null) onDone(null, "골드가 모자라요") else onDone(r, null) }
         .addOnFailureListener { onDone(null, "인터넷을 확인하고 다시 눌러 주세요") }
    }

    /** 입은 옷 전체를 한 번에 바꾸기 (모두 벗기 = 빈 map, 랜덤 코디 등) */
    fun setDressEquipAll(id: String, equip: Map<String, String>) {
        students().document(id).update(mapOf<String, Any>("dressEquip" to equip))
    }

    /** 옷 입기/벗기 (key 가 "" 이면 벗기) */
    fun setDressEquip(id: String, cat: String, key: String) {
        students().document(id).update(mapOf<String, Any>("dressEquip.$cat" to (if (key.isBlank()) FieldValue.delete() else key)))
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

    // ---------- 화면 크기 설정 (모든 기기 공통) ----------
    fun uiDoc() = db.collection("config").document("ui")

    fun startUiSync(): ListenerRegistration = uiDoc().addSnapshotListener { snap, _ ->
        if (snap == null) return@addSnapshotListener
        // L_ = 가로 화면, P_ = 세로 화면. 값이 아직 없으면 예전에 저장한 값(접두어 없는 것)을 그대로 씀
        fun num(key: String, legacy: String, def: Double): Float = (snap.getDouble(key) ?: snap.getDouble(legacy) ?: def).toFloat()
        fun lay(p: String) = PopupLayout(
            box = num("${p}popBox", "popBox", 1.0).coerceIn(UiSize.MIN, UiSize.MAX),
            char = num("${p}popChar", "popChar", 1.0).coerceIn(UiSize.MIN, UiSize.CHAR_MAX),
            pop = num("${p}popScale", "popScale", 1.0).coerceIn(UiSize.POP_MIN, UiSize.POP_MAX),
            x = num("${p}popX", "popX", 0.0).coerceIn(-UiSize.X_RANGE, UiSize.X_RANGE),
            y = num("${p}popY", "popY", 0.0).coerceIn(-UiSize.Y_RANGE, UiSize.Y_RANGE),
            plate = num("${p}popPlate", "popPlate", 1.0).coerceIn(UiSize.ELEM_MIN, UiSize.ELEM_MAX),
            rank = num("${p}popRank", "popRank", 1.0).coerceIn(UiSize.ELEM_MIN, UiSize.ELEM_MAX),
            btn = num("${p}popBtn", "popBtn", 1.0).coerceIn(UiSize.ELEM_MIN, UiSize.ELEM_MAX),
            sub = num("${p}popSub", "popSub", 1.0).coerceIn(UiSize.ELEM_MIN, UiSize.ELEM_MAX),
        )
        UiSize.popLand = lay("L_")
        UiSize.popPort = lay("P_")
        UiSize.boxLand = num("L_boxScale", "boxScale", 1.0).coerceIn(UiSize.MIN, UiSize.MAX)
        UiSize.charLand = num("L_charScale", "charScale", 1.0).coerceIn(UiSize.MIN, UiSize.MAX)
        UiSize.boxPort = num("P_boxScale", "boxScale", 1.0).coerceIn(UiSize.MIN, UiSize.MAX)
        UiSize.charPort = num("P_charScale", "charScale", 1.0).coerceIn(UiSize.MIN, UiSize.MAX)
    }

    fun saveUiSize(box: Float, char: Float, land: Boolean) {
        val b = box.coerceIn(UiSize.MIN, UiSize.MAX); val c = char.coerceIn(UiSize.MIN, UiSize.MAX)
        if (land) { UiSize.boxLand = b; UiSize.charLand = c } else { UiSize.boxPort = b; UiSize.charPort = c }   // 이 기기는 바로 적용
        val p = if (land) "L_" else "P_"
        uiDoc().set(mapOf("${p}boxScale" to b.toDouble(), "${p}charScale" to c.toDouble()), SetOptions.merge())
    }

    /** 출결 팝업 배치(칸별 크기·위치) 저장 — 이 기기는 바로 적용, 다른 기기는 서버 동기화로 */
    fun saveUiLayout(l: PopupLayout, land: Boolean) {
        val fixed = PopupLayout(
            box = l.box.coerceIn(UiSize.MIN, UiSize.MAX), char = l.char.coerceIn(UiSize.MIN, UiSize.CHAR_MAX),
            pop = l.pop.coerceIn(UiSize.POP_MIN, UiSize.POP_MAX),
            x = l.x.coerceIn(-UiSize.X_RANGE, UiSize.X_RANGE), y = l.y.coerceIn(-UiSize.Y_RANGE, UiSize.Y_RANGE),
            plate = l.plate.coerceIn(UiSize.ELEM_MIN, UiSize.ELEM_MAX), rank = l.rank.coerceIn(UiSize.ELEM_MIN, UiSize.ELEM_MAX),
            btn = l.btn.coerceIn(UiSize.ELEM_MIN, UiSize.ELEM_MAX), sub = l.sub.coerceIn(UiSize.ELEM_MIN, UiSize.ELEM_MAX),
        )
        if (land) UiSize.popLand = fixed else UiSize.popPort = fixed
        val p = if (land) "L_" else "P_"
        uiDoc().set(mapOf(
            "${p}popBox" to fixed.box.toDouble(), "${p}popChar" to fixed.char.toDouble(), "${p}popPlate" to fixed.plate.toDouble(),
            "${p}popRank" to fixed.rank.toDouble(), "${p}popBtn" to fixed.btn.toDouble(), "${p}popSub" to fixed.sub.toDouble(),
            "${p}popScale" to fixed.pop.toDouble(), "${p}popX" to fixed.x.toDouble(), "${p}popY" to fixed.y.toDouble(),
        ), SetOptions.merge())
    }

    /** 서버의 상품 목록을 지켜보다가 Prizes 에 반영 (로그인하면 시작, 모든 기기 공통) */
    fun startPrizeSync(): ListenerRegistration {
        val r1 = prizes().addSnapshotListener { snap, _ ->
            if (snap == null) return@addSnapshotListener
            Prizes.list = snap.documents.map(::toPrize).sortedBy { it.name }
        }
        // 오늘 이미 당첨된 원생 (지급 여부와 관계없이 wins 에 오늘 날짜 기록이 있으면 오늘만 제외 — 날이 바뀌면 자동으로 다시 추첨)
        val r2 = wins().addSnapshotListener { snap, _ ->
            if (snap == null) return@addSnapshotListener
            val m = HashMap<String, String>()
            snap.documents.forEach { d ->
                val sid = d.getString("studentId") ?: return@forEach
                val day = d.getString("day") ?: return@forEach
                if (day > (m[sid] ?: "")) m[sid] = day
            }
            Prizes.winnerDays = Prizes.winnerDays + m
        }
        return ListenerRegistration { r1.remove(); r2.remove() }
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
