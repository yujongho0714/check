package com.jungdo.attend.data

/** 원생 한 명 (Firestore: students/{id}) */
data class Student(
    val id: String = "",
    val name: String = "",
    val grade: String = "",                     // 유치부, 1학년 … 6학년, 중1~중3, 고1~고3, 성인 (나이가 있으면 자동 계산)
    val phones: List<String> = emptyList(),     // 학부모 연락처 (숫자만). 첫 번째가 대표 번호
    val phoneLabels: List<String> = emptyList(),// phones 와 같은 순서: 어머님/아버님/할머님…
    val memo: String = "",
    val siblingKey: String = "",                // 같은 값이면 형제 (예: "g1")
    val demo: Boolean = false,                  // 시범단 (현황표에서 회색 칸)
    val status: String = StudentStatus.ACTIVE,  // 재원/휴관/퇴관
    val statusReason: String = "",
    val joinedMonth: String = "",               // yyyy-MM, 이 달부터 출석부에 나옴
    val leftMonth: String = "",                 // yyyy-MM, 이 달부터 출석부·출석체크에서 빠짐
    val order: Int = 0,                         // 현황표 순서
    val attendNo: String = "",                  // 출결번호 (보통 학부모 번호 끝 4자리, 형제는 같음)
    val school: String = "",
    val fee: Int = 0,                           // 수강료
    val age: Int = 0,                           // 나이 (ageYear 해에 정한 나이. 0 이면 자동 진급 안 함)
    val ageYear: Int = 0,                       // 나이를 정한 해 (예: 2026)
    val exp: Int = 0,                           // 경험치 (등원·하원 때마다 오름)
    val streak: Int = 0,                        // 연속 출석 일수
    val streakDay: String = "",                 // 연속 출석을 마지막으로 센 날 (yyyy-MM-dd)
    val inDay: String = "",                     // 등원 경험치를 마지막으로 받은 날
    val outDay: String = "",                    // 하원 경험치를 마지막으로 받은 날
    val gems: List<String> = emptyList(),       // 모은 보석 번호 ("001" … "396")
    val plates: List<String> = emptyList(),     // 모은 네임플레이트 번호 ("01" … "47")
    val plate: String = "",                     // 지금 쓰고 있는 네임플레이트 (비어 있으면 안 씀)
) {
    /** 계급 레벨 (1 ~ 866) */
    val level: Int get() = Rank.levelOf(exp)
    val isActive get() = status == StudentStatus.ACTIVE
    /** 올해 나이 (해가 바뀔 때마다 1살씩 올라감). 나이를 모르면 0 */
    val curAge: Int get() = if (age > 0 && ageYear > 0) age + (GradeCalc.thisYear() - ageYear) else 0
    /** 나이를 정해서 학년까지 같이 바꾼 원생 (올해 기준) */
    fun withAge(a: Int, year: Int = GradeCalc.thisYear()): Student =
        if (a > 0) copy(age = a, ageYear = year, grade = GradeCalc.labelOf(a)) else copy(age = 0, ageYear = 0)
    /** 그 달(yyyy-MM)에 몇 학년이었는지 — 지난 출석부는 그때 학년으로 보여주려고 */
    fun gradeAt(month: String): String {
        if (age <= 0 || ageYear <= 0) return grade
        val y = month.take(4).toIntOrNull() ?: return grade
        return GradeCalc.labelOf(age + (y - ageYear))
    }
    /** 해당 월 출석부에 나와야 하는지 (퇴관한 달부터는 안 나오고, 예전 달에는 그대로 나옴) */
    fun inMonth(month: String) =
        (joinedMonth.isBlank() || joinedMonth <= month) && (leftMonth.isBlank() || month < leftMonth)
    fun phoneLabel(i: Int) = phoneLabels.getOrNull(i)?.ifBlank { null } ?: "보호자"

    /**
     * 출결번호 입력과 맞는지: 직접 정한 출결번호이거나, 등록된 학부모 번호(어머님·아버님 등 전부) 중
     * 하나의 뒷자리 4개와 같으면 OK. (대표번호 하나만이 아니라 등록된 번호 전부 확인)
     */
    fun matchesNo(no: String): Boolean {
        val n = no.trim()
        if (n.isEmpty()) return false
        if (attendNo.isNotBlank() && attendNo == n) return true
        if (n.length != 4) return false
        return phones.any { p ->
            val d = p.filter { it.isDigit() }
            d.length >= 4 && d.takeLast(4) == n
        }
    }

    /** 같은 학부모 번호를 하나라도 같이 쓰는지 (형제 찾기용 — 번호 전체가 같아야 함) */
    fun sharesPhone(o: Student): Boolean {
        val mine = phones.map { Repo.normalizePhone(it) }.filter { it.length >= 9 }.toSet()
        if (mine.isEmpty()) return false
        return o.phones.any { Repo.normalizePhone(it) in mine }
    }
}

object StudentStatus {
    const val ACTIVE = "ACTIVE"   // 재원
    const val LEAVE = "LEAVE"     // 휴관 (출석체크·미등원 목록에서 빠짐, 출석부엔 '휴관' 표시)
    const val LEFT = "LEFT"       // 퇴관
    fun label(s: String) = when (s) { LEAVE -> "휴관"; LEFT -> "퇴관"; else -> "재원" }
}

/** 등원/하원 기록 한 건 (Firestore: events/{id}) — 기기 A가 만들고 기기 B가 처리 */
data class AttendEvent(
    val id: String,
    val studentId: String,
    val name: String,
    val grade: String,
    val phones: List<String>,
    val type: String,        // EventType.IN / OUT
    val status: String,      // Status.*
    val clientTime: Long,    // 아이가 버튼 누른 시각(ms)
    val day: String,         // yyyy-MM-dd
    val error: String?,
    val message: String?,
    val dlvOk: Int = 0,          // 통신사가 '도착했어요' 라고 알려준 건수 (문자 조각 단위)
    val dlvFail: Int = 0,        // '도착 실패' 로 알려준 건수
    val dlvNeed: Int = 0,        // 도착 확인을 기다리는 전체 건수
    val dlvError: String? = null,
)

/** 부모님께 받은 연락 → 사유 결석 (Firestore: absences/{날짜_원생id}) */
data class Absence(
    val id: String,
    val day: String,
    val studentId: String,
    val name: String,
    val reason: String,
)

object EventType {
    const val IN = "IN"
    const val OUT = "OUT"
    fun label(t: String) = if (t == IN) "등원" else "하원"
}

object Status {
    const val PENDING = "PENDING"   // A가 보냄, B가 아직 안 가져감
    const val SENDING = "SENDING"   // B가 가져가서 문자 보내는 중
    const val SENT = "SENT"         // 문자 발송 성공
    const val FAILED = "FAILED"     // 문자 발송 실패
    const val EXPIRED = "EXPIRED"   // 너무 늦게 받아서 안 보냄

    fun label(s: String) = when (s) {
        PENDING -> "대기"
        SENDING -> "발송중"
        SENT -> "발송완료"
        FAILED -> "실패"
        EXPIRED -> "시간초과"
        else -> s
    }
}

/** 학년 정렬 순서 (현황표 순) */
val GRADE_ORDER = listOf("유치부", "6~7세", "1학년", "2학년", "3학년", "4학년", "5학년", "6학년", "4/5/6/중고등", "중학생", "중1", "중2", "중3", "중고등", "고등학생", "고1", "고2", "고3", "성인")
fun gradeRank(g: String): Int = GRADE_ORDER.indexOf(g).let { if (it < 0) 99 else it }
