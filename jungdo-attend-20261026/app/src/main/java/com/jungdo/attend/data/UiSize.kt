package com.jungdo.attend.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue

/**
 * 출결 팝업·보관함의 박스/캐릭터 크기 (관리자가 정하면 모든 기기·모든 원생에게 똑같이 적용)
 *  - 1.0 = 이 기기 화면에 딱 맞게 자동 계산된 기본 크기
 *  - box  : 캐릭터 무대(출결 팝업) / 왼쪽 내 정보 칸(보관함) 크기 배율
 *  - char : 그 박스 안에서 캐릭터 크기 배율
 *  - Firestore config/ui 에 저장, Repo.startUiSync 가 계속 받아서 갱신
 */
/** 출결 팝업 배치 한 벌: 박스·캐릭터 크기 배율, 팝업 전체 크기 배율, 팝업 위치(화면 가운데 기준 dp, 오른쪽·아래가 +) */
data class PopupLayout(val box: Float = 1f, val char: Float = 1f, val pop: Float = 1f, val x: Float = 0f, val y: Float = 0f)

object UiSize {
    const val MIN = 0.5f
    const val MAX = 2.0f
    const val POP_MIN = 0.5f
    const val POP_MAX = 1.2f
    const val X_RANGE = 400f
    const val Y_RANGE = 300f
    var box by mutableFloatStateOf(1f)
    var char by mutableFloatStateOf(1f)
    /** 출결 팝업 전체 크기 배율 / 위치 (관리자가 팝업을 보면서 정함, 모든 기기 공통) */
    var popScale by mutableFloatStateOf(1f)
    var popX by mutableFloatStateOf(0f)
    var popY by mutableFloatStateOf(0f)

    fun layout() = PopupLayout(box, char, popScale, popX, popY)
}
