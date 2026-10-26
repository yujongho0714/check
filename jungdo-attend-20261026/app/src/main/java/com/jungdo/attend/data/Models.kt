package com.jungdo.attend.data

/** 원생 한 명 (Firestore: students/{id}) */
data class Student(
    val id: String = "",
    val name: String = "",
    val grade: String = "",                     // 유치부, 1학년 … 중학생 (수련생 현황 기준)
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
) {
    val isActive get() = status == StudentStatus.ACTIVE
    /** 해당 월 출석부에 나와야 하는지 (퇴관한 달부터는 안 나오고, 예전 달에는 그대로 나옴) */
    fun inMonth(month: String) =
        (joinedMonth.isBlank() || joinedMonth <= month) && (leftMonth.isBlank() || month < leftMonth)
    fun phoneLabel(i: Int) = phoneLabels.getOrNull(i)?.ifBlank { null } ?: "보호자"
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
val GRADE_ORDER = listOf("유치부", "6~7세", "1학년", "2학년", "3학년", "4학년", "5학년", "6학년", "4/5/6/중고등", "중학생", "중고등", "고등학생", "성인")
fun gradeRank(g: String): Int = GRADE_ORDER.indexOf(g).let { if (it < 0) 99 else it }
