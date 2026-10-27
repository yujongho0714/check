package com.jungdo.attend.data

import android.content.Context

/** 기기마다 따로 저장되는 설정값 */
class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("jungdo_attend", Context.MODE_PRIVATE)

    /** KIOSK(기기 A) 또는 SERVER(기기 B). null 이면 처음 실행 */
    var mode: String?
        get() = sp.getString("mode", null)
        set(v) = sp.edit().putString("mode", v).apply()

    /** 키오스크 관리자 화면 비밀번호 */
    var adminPin: String
        get() = sp.getString("pin", "0000") ?: "0000"
        set(v) = sp.edit().putString("pin", v).apply()

    /** 키오스크: 버튼 누르면 "홍길동 등원" 음성 */
    var ttsOn: Boolean
        get() = sp.getBoolean("tts", true)
        set(v) = sp.edit().putBoolean("tts", v).apply()

    /** 게임 효과음 켜기/끄기 (버튼 누를 때 소리) */
    var fxSound: Boolean
        get() = sp.getBoolean("fxSound", true)
        set(v) = sp.edit().putBoolean("fxSound", v).apply()

    /** 게임 효과음 크기 0~100 */
    var fxVolume: Int
        get() = sp.getInt("fxVolume", 80)
        set(v) = sp.edit().putInt("fxVolume", v.coerceIn(0, 100)).apply()

    /** 버튼 누를 때 진동 */
    var fxHaptic: Boolean
        get() = sp.getBoolean("fxHaptic", true)
        set(v) = sp.edit().putBoolean("fxHaptic", v).apply()

    /** 앱 전체 게임 테마 이름 (GameThemes 의 id) */
    var gameTheme: String
        get() = sp.getString("gameTheme", "sprout") ?: "sprout"
        set(v) = sp.edit().putString("gameTheme", v).apply()

    /** 문자 서버: 켜둔 상태인지 (재부팅 시 자동 시작 여부) */
    var serverEnabled: Boolean
        get() = sp.getBoolean("serverEnabled", false)
        set(v) = sp.edit().putBoolean("serverEnabled", v).apply()

    /** 이 시간(분)보다 늦게 도착한 신호는 문자를 보내지 않음 (예: 문자폰이 꺼져 있다가 밤에 켜진 경우) */
    var expireMinutes: Int
        get() = sp.getInt("expireMin", 30)
        set(v) = sp.edit().putInt("expireMin", v).apply()

    /** 문자를 대표 번호 1개에만 보낼지(false), 등록된 학부모 번호 전부에 보낼지(true) */
    var smsAllPhones: Boolean
        get() = sp.getBoolean("smsAll", false)
        set(v) = sp.edit().putBoolean("smsAll", v).apply()

    /** 일반 전화(앱 밖에서 걸거나 받은 전화)도 학부모 번호면 통화 후 사유 입력 창 띄우기 */
    /** 학부모에게서 문자가 오면 그 아이 사유 입력 창 띄우기 (문자 내용 자동 입력) */
    var smsPopup: Boolean
        get() = sp.getBoolean("smsPopup", true)
        set(v) = sp.edit().putBoolean("smsPopup", v).apply()

    /** 결석 관련 키워드 (쉼표로 구분) — 문자에 하나라도 있으면 사유 입력 창 */
    var absentWords: String
        get() = sp.getString("absentWords", "결석,쉬,쉴,쉽니다,빠지,빠질,빠져,빠집,여행") ?: ""
        set(v) = sp.edit().putString("absentWords", v).apply()

    /** 시간·차량 스케줄 키워드 — 있으면 '카톡으로 보내기' 알림 */
    var scheduleWords: String
        get() = sp.getString("scheduleWords", "차량,스케줄,시간표,시간 변경,시간변경,픽업,셔틀,데리러,태워") ?: ""
        set(v) = sp.edit().putString("scheduleWords", v).apply()

    var scheduleToKakao: Boolean
        get() = sp.getBoolean("scheduleToKakao", true)
        set(v) = sp.edit().putBoolean("scheduleToKakao", v).apply()

    /** 사유 저장할 때 학부모께 답장 문자 보내기 (사유 창 체크박스의 기본값) */
    var replyOn: Boolean
        get() = sp.getBoolean("replyOn", true)
        set(v) = sp.edit().putBoolean("replyOn", v).apply()

    var callPopupAny: Boolean
        get() = sp.getBoolean("callPopupAny", true)
        set(v) = sp.edit().putBoolean("callPopupAny", v).apply()

    companion object {
        const val MODE_KIOSK = "KIOSK"
        const val MODE_SERVER = "SERVER"
    }
}
