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
    var callPopupAny: Boolean
        get() = sp.getBoolean("callPopupAny", true)
        set(v) = sp.edit().putBoolean("callPopupAny", v).apply()

    companion object {
        const val MODE_KIOSK = "KIOSK"
        const val MODE_SERVER = "SERVER"
    }
}
