package com.jungdo.attend.report

/**
 * 신규 원생 등록 양식 (.xlsx)
 * - 첫 번째 시트의 머리글은 출결 프로그램 'contact_날짜.xls' 와 똑같아서, 이 양식에 적어 올려도 / 출결 프로그램에서 내보낸 파일을 올려도 같은 방식으로 읽는다.
 * - 한 아이에 번호가 둘(어머니·아버지)이면 같은 이름으로 줄을 하나 더 쓰면 된다.
 */
object ImportTemplate {
    val HEADERS = listOf("이름", "학부모 연락처", "학부모 구분(어머니/아버지)", "학생 연락처", "출결번호", "납부일", "수강료", "학교명", "학년")

    fun build(): ByteArray {
        val w = XlsxWriter()
        val head = XlsxWriter.Style(bold = true, fill = "DCE6F5")
        val cell = XlsxWriter.Style(center = false)
        val note = XlsxWriter.Style(center = false, border = false, wrap = true)

        val s1 = w.sheet("신규원생")
        HEADERS.forEachIndexed { c, h -> s1.set(0, c, h, head) }
        // 아래 칸에 한 줄에 한 명씩 적으면 됩니다 (머리글 줄은 지우지 마세요)
        for (r in 1..60) for (c in HEADERS.indices) s1.set(r, c, "", cell)
        val widths = listOf(12.0, 16.0, 24.0, 16.0, 10.0, 8.0, 10.0, 14.0, 10.0)
        widths.forEachIndexed { c, v -> s1.width(c, v) }
        s1.freeze(1, 0)

        val s2 = w.sheet("작성 방법")
        s2.width(0, 90.0)
        val lines = listOf(
            "【신규 원생 양식 작성 방법】",
            "1. 첫 번째 시트('신규원생')의 머리글 줄은 그대로 두고, 아래 칸에 한 줄에 한 명씩 적어요.",
            "2. 꼭 필요한 칸: 이름, 학부모 연락처. 나머지는 비워도 돼요.",
            "3. 학부모 구분은 '어머니' 또는 '아버지'. 비우면 어머님으로 들어가요.",
            "4. 같은 아이에게 번호가 두 개(어머니·아버지)면 같은 이름으로 줄을 하나 더 쓰고 번호·구분만 바꿔요.",
            "5. 출결번호를 비우면 학부모 번호 끝 4자리로 자동으로 들어가요. 형제는 같은 번호를 쓰면 같은 번호로 들어가요.",
            "6. 학년은 '1학년', '2학년', '유치부', '중학생'처럼 쓰거나 '초등학교1', '유치원7'처럼 써도 돼요. 비워도 돼요.",
            "7. 번호는 010-1234-5678 처럼 하이픈이 있어도, 없어도 돼요.",
            "8. 완성되면 앱 [명단·형제] 탭 → '파일 올리기'에서 이 파일을 고르세요. 이미 등록된 원생은 그대로 두고, 새 이름만 추가돼요.",
            "9. 같은 이름이 이미 재원 중이면 번호·출결번호만 갱신돼요. 동명이인은 자동 연결하지 않으니 앱에서 직접 확인하세요.",
        )
        lines.forEachIndexed { r, t -> s2.set(r, 0, t, note) }
        return w.bytes()
    }
}
