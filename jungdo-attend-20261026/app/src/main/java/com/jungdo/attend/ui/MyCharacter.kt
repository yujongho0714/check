package com.jungdo.attend.ui

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jungdo.attend.data.Student
import com.jungdo.attend.ui.game.GameButton
import com.jungdo.attend.ui.game.LocalGameTheme
import kotlin.math.abs
import kotlin.math.roundToInt

/*
 * 내 캐릭터 (8단계).
 *  - MyCharacter      : 가만히 보여 주기만 하는 공통 컴포넌트 (9단계 이후 가방·플레이트함·뽑기·드레스룸에서 씀)
 *  - MyCharacterStage : 작은 무대 위에서 움직일 수 있는 캐릭터 (출결 다이얼로그에 붙임)
 *      · 화살표 버튼을 누르고 있으면 그 방향으로 걷기
 *      · 무대를 톡 누르면 그 자리로 걸어가기
 *      · 공격 버튼 = 공격 모션 한 번 (끝나면 서기로 돌아옴)
 *      · 아무것도 안 하면 서기
 */

/** 학생의 지금 옷차림 그대로 그려 주는 공통 캐릭터 */
@Composable
fun MyCharacter(
    student: Student,
    height: Dp,
    modifier: Modifier = Modifier,
    dir: DressDir = DressDir.FRONT,
    action: DressAction = DressAction.STAND,
    onAttackDone: () -> Unit = {},
) {
    DressCharacter(student.dressEquip, height, modifier, dir, action, onAttackDone)
}

/** 누르는 순간 onDown, 손을 떼면 onUp 을 부름 (안에 있는 버튼의 클릭은 그대로 두고 지켜보기만 함) */
private fun Modifier.holdPress(onDown: () -> Unit, onUp: () -> Unit): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            onDown()
            waitForUpOrCancellation(pass = PointerEventPass.Initial)
            onUp()
        }
    }
}

/**
 * 움직이는 내 캐릭터 무대.
 * onTouch = 조작할 때마다 불림 (출결 창이 15초 뒤 저절로 닫히는 시간을 늘리려고).
 * stageHeight 를 안 주면 세로 화면 150dp, 가로 화면 120dp.
 */
@Composable
fun MyCharacterStage(
    student: Student,
    modifier: Modifier = Modifier,
    stageWidth: Dp = 300.dp,
    stageHeight: Dp? = null,
    charHeight: Dp? = null,
    onTouch: () -> Unit = {},
) {
    val theme = LocalGameTheme.current
    val density = LocalDensity.current
    val land = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val stageH = stageHeight ?: if (land) 120.dp else 150.dp
    val charH = charHeight ?: (stageH - 40.dp)
    val pack = rememberDressPack()
    val touch by rememberUpdatedState(onTouch)

    // 크기 계산 (px). 캐릭터 가로세로 비율은 옷 데이터(311:294)를 따름
    val ratio = pack?.let { it.w.toFloat() / it.h } ?: (311f / 294f)
    val stageWpx = with(density) { stageWidth.toPx() }
    val stageHpx = with(density) { stageH.toPx() }
    val charHpx = with(density) { charH.toPx() }
    val charWpx = charHpx * ratio
    val maxX = (stageWpx - charWpx).coerceAtLeast(0f)
    val maxY = (stageHpx - charHpx).coerceAtLeast(0f)
    val speed = with(density) { 150.dp.toPx() }   // 1초에 150dp 걷기

    var pos by remember(maxX, maxY) { mutableStateOf(Offset(maxX / 2f, maxY)) }   // 캐릭터 왼쪽 위 (무대 맨 아래 가운데에서 시작)
    var dir by remember { mutableStateOf(DressDir.FRONT) }
    var held by remember { mutableStateOf<DressDir?>(null) }       // 누르고 있는 화살표
    var target by remember { mutableStateOf<Offset?>(null) }       // 무대를 눌러서 정한 목적지
    var attacking by remember { mutableStateOf(false) }

    val walking = !attacking && (held != null || target != null)
    val action = when {
        attacking -> DressAction.ATTACK
        walking -> DressAction.WALK
        else -> DressAction.STAND
    }

    // 걷는 동안만 매 화면 갱신마다 위치를 옮김 (가만히 있을 땐 아무것도 안 돌림)
    LaunchedEffect(walking) {
        if (!walking) return@LaunchedEffect
        var last = withFrameMillis { it }
        while (true) {
            val now = withFrameMillis { it }
            val dt = (now - last).coerceIn(0L, 64L) / 1000f
            last = now
            val step = speed * dt
            val h = held
            val tg = target
            if (h != null) {
                dir = h
                val moved = when (h) {
                    DressDir.BACK -> Offset(pos.x, pos.y - step)
                    DressDir.FRONT -> Offset(pos.x, pos.y + step)
                    DressDir.LEFT -> Offset(pos.x - step, pos.y)
                    DressDir.RIGHT -> Offset(pos.x + step, pos.y)
                }
                pos = Offset(moved.x.coerceIn(0f, maxX), moved.y.coerceIn(0f, maxY))
            } else if (tg != null) {
                val d = tg - pos
                val dist = d.getDistance()
                if (dist <= step) {
                    pos = tg
                    target = null
                    break
                }
                dir = if (abs(d.x) >= abs(d.y)) (if (d.x > 0) DressDir.RIGHT else DressDir.LEFT)
                      else (if (d.y > 0) DressDir.FRONT else DressDir.BACK)
                pos = pos + d * (step / dist)
            } else break
        }
    }

    fun press(d: DressDir) { touch(); target = null; held = d }
    fun release(d: DressDir) { if (held == d) held = null }
    fun attack() {
        if (attacking || pack == null) return   // 옷 데이터를 아직 읽는 중이면 공격 안 함 (끝나는 신호가 안 와서)
        touch(); target = null; attacking = true
    }

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        // ── 무대 ──
        Box(
            Modifier.width(stageWidth).height(stageH)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0x22808080))
                .pointerInput(maxX, maxY, charWpx, charHpx) {
                    detectTapGestures { o ->
                        touch()
                        if (!attacking) {
                            held = null
                            target = Offset(
                                (o.x - charWpx / 2f).coerceIn(0f, maxX),
                                (o.y - charHpx * 0.9f).coerceIn(0f, maxY),
                            )
                        }
                    }
                },
        ) {
            Box(Modifier.offset { IntOffset(pos.x.roundToInt(), pos.y.roundToInt()) }) {
                DressCharacter(
                    equip = student.dressEquip, height = charH,
                    dir = dir, action = action,
                    onAttackDone = { attacking = false },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        // ── 조작 버튼: ◀ ▲ ▼ ▶ (누르고 있으면 걷기) + 공격 ──
        Row(Modifier.width(stageWidth), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(DressDir.LEFT to "◀", DressDir.BACK to "▲", DressDir.FRONT to "▼", DressDir.RIGHT to "▶").forEach { (d, label) ->
                Box(Modifier.weight(1f).holdPress(onDown = { press(d) }, onUp = { release(d) })) {
                    GameButton(
                        label, onClick = {},
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        color = theme.keyColor, fontSize = 22.sp, sound = null,
                    )
                }
            }
            Box(Modifier.weight(1.6f)) {
                GameButton(
                    "공격", onClick = { attack() },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    color = theme.primary, fontSize = 20.sp, sound = null,
                )
            }
        }
    }
}
