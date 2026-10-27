package com.jungdo.attend.data

/**
 * 학년 자동 진급 계산
 *  - 원생마다 '나이(age)'와 '그 나이를 정한 해(ageYear)'를 저장해 두면, 해가 바뀔 때마다 나이가 1살씩 올라가 학년이 저절로 바뀝니다.
 *  - 나이 → 학년:  7세 이하 유치부 / 8세 초1 … 13세 초6 / 14세 중1 … 16세 중3 / 17세 고1 … 19세 고3 / 20세 이상 성인
 *  - 나이를 모르는 원생(age = 0)은 적어 둔 학년 글자를 그대로 쓰고 자동으로 올라가지 않습니다.
 */
object GradeCalc {
    const val KINDER_MAX = 7     // 유치부 마지막 나이 (8세부터 초1)
    const val MIDDLE_START = 14  // 중1
    const val HIGH_START = 17    // 고1
    const val ADULT_START = 20   // 성인부

    /** 학년 고르기 버튼에 쓰는 순서 */
    val PICK = listOf("유치부", "1학년", "2학년", "3학년", "4학년", "5학년", "6학년", "중1", "중2", "중3", "고1", "고2", "고3", "성인")

    fun thisYear(): Int = Repo.today().take(4).toIntOrNull() ?: 2026

    /** 나이 → 학년 글자 (나이가 0 이하이면 빈 글자) */
    fun labelOf(age: Int): String = when {
        age <= 0 -> ""
        age <= KINDER_MAX -> "유치부"
        age < MIDDLE_START -> "${age - KINDER_MAX}학년"
        age < HIGH_START -> "중${age - (MIDDLE_START - 1)}"
        age < ADULT_START -> "고${age - (HIGH_START - 1)}"
        else -> "성인"
    }

    /** 학년 글자 → 나이. 1학년~6학년, 중1~중3, 고1~고3 처럼 정확한 학년만 (그 밖의 글자는 0) */
    fun ageOf(text: String): Int {
        val t = text.trim()
        Regex("^([1-6])학년$").find(t)?.let { return KINDER_MAX + it.groupValues[1].toInt() }
        Regex("^(?:중학교|중)\\s*([1-3])(?:학년)?$").find(t)?.let { return (MIDDLE_START - 1) + it.groupValues[1].toInt() }
        Regex("^(?:고등학교|고)\\s*([1-3])(?:학년)?$").find(t)?.let { return (HIGH_START - 1) + it.groupValues[1].toInt() }
        return 0
    }

    /** 학년 글자의 큰 분류: 0 유치부 / 1 초등 / 2 중등 / 3 고등 / 4 성인 / -1 모름 */
    fun category(text: String): Int {
        val t = text.trim()
        return when {
            t == "유치부" || t == "6~7세" -> 0
            Regex("^[1-6]학년$").matches(t) -> 1
            t == "중학생" || Regex("^중[1-3]$").matches(t) -> 2
            t == "고등학생" || Regex("^고[1-3]$").matches(t) -> 3
            t == "성인" -> 4
            else -> -1
        }
    }

    /**
     * 현황표·양식·직접 입력처럼 '학년 글자'로 학년을 받았을 때 원생에 반영
     *  - 정확한 학년(2학년, 중1 …)이면 올해 그 나이로 정함
     *  - '중학생'처럼 학년을 알 수 없는 글자인데, 이미 나이가 있고 같은 분류이면 → 나이를 그대로 둠
     *  - 그 밖에는 글자 그대로 두고 나이는 비움 (자동 진급 안 됨 → 원생 수정에서 나이를 넣어 주세요)
     */
    fun applyLabel(s: Student, text: String): Student {
        val t = text.trim()
        val exact = ageOf(t)
        if (exact > 0) return s.withAge(exact)
        if (s.age > 0 && s.ageYear > 0 && category(t) >= 0 && category(t) == category(labelOf(s.curAge))) {
            return s.copy(grade = labelOf(s.curAge))
        }
        return s.copy(grade = t, age = 0, ageYear = 0)
    }

    /** 나이는 모르고 학년 글자만 있는 '자동 진급이 안 되는' 원생인지 (유치부·중학생·고등학생 등) */
    fun needsAge(s: Student): Boolean = s.age <= 0 && s.grade.isNotBlank() && s.grade != "성인"
}
