package com.jungdo.attend.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 출결 팝업·보관함의 박스/캐릭터 크기 (관리자가 정하면 모든 기기·모든 원생에게 똑같이 적용)
 *  - 1.0 = 이 기기 화면에 딱 맞게 자동 계산된 기본 크기
 *  - box  : 캐릭터 무대(출결 팝업) / 왼쪽 내 정보 칸(보관함) 크기 배율
 *  - char : 그 박스 안에서 캐릭터 크기 배율
 *  - Firestore config/ui 에 저장, Repo.startUiSync 가 계속 받아서 갱신
 */
/**
 * 출결 팝업(등원/하원 화면) 배치 한 벌 — 팝업 안의 칸마다 따로 크기를 정함 (모두 팝업 전용, 보관함 크기와는 별개)
 *  box=캐릭터 박스 · char=박스 안 캐릭터 · pop=팝업 전체 · x,y=팝업 위치(화면 가운데 기준 dp, 오른쪽·아래가 +)
 *  plate=네임플레이트 · rank=계급·레벨 · btn=등원/하원 버튼 · sub=내 보석·플레이트·가방·뽑기 버튼
 */
data class PopupLayout(
    val box: Float = 1f, val char: Float = 1f, val pop: Float = 1f, val x: Float = 0f, val y: Float = 0f,
    val plate: Float = 1f, val rank: Float = 1f, val btn: Float = 1f, val sub: Float = 1f,
)

object UiSize {
    const val MIN = 0.5f
    const val MAX = 2.0f
    // ── 출결 팝업 전용 범위 ──
    const val POP_MIN = 0.5f
    const val POP_MAX = 1.2f
    const val X_RANGE = 400f
    const val Y_RANGE = 300f
    const val CHAR_MAX = 8.0f      // 팝업 캐릭터는 800% 까지
    const val ELEM_MIN = 0.5f      // 네임플레이트·계급·버튼 크기
    const val ELEM_MAX = 2.0f
    /** 팝업 캐릭터 100% 일 때 그려지는 크기 = 캐릭터 칸의 몇 배 (옷 그림에 빈 여백이 커서 기본부터 크게) */
    const val CHAR_BASE = 2.5f

    // ── 가로 화면 / 세로 화면 설정을 따로 저장 (config/ui 의 L_*, P_* 값. 예전에 저장한 값은 처음엔 양쪽에 그대로 적용) ──
    // 출결 팝업 배치
    var popLand by mutableStateOf(PopupLayout())
    var popPort by mutableStateOf(PopupLayout())
    // 보관함(내 정보 칸)의 박스/캐릭터 크기
    var boxLand by mutableFloatStateOf(1f)
    var charLand by mutableFloatStateOf(1f)
    var boxPort by mutableFloatStateOf(1f)
    var charPort by mutableFloatStateOf(1f)

    fun layout(land: Boolean): PopupLayout = if (land) popLand else popPort
    fun box(land: Boolean): Float = if (land) boxLand else boxPort
    fun char(land: Boolean): Float = if (land) charLand else charPort
}
