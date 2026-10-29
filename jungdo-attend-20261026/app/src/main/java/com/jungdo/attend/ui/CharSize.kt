package com.jungdo.attend.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jungdo.attend.ui.game.gameMuted

/**
 * 팝업 안에서 잠깐 키우고 줄이는 막대 (저장 안 됨 · 창을 닫으면 관리자가 정한 기본 크기로 돌아와요).
 * 모든 기기에 적용되는 기본 크기는 관리자 설정의 "캐릭터 · 내 정보 크기"에서 정해요 (UiSizeSettings).
 */
@Composable
fun rememberCharScale(): MutableState<Float> = remember { mutableStateOf(1f) }

@Composable
fun CharSizeBar(scale: Float, onChange: (Float) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text("작게", fontSize = 13.sp, color = gameMuted())
        Slider(
            value = scale,
            onValueChange = onChange,
            valueRange = 0.6f..1.8f,
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
        )
        Text("크게", fontSize = 13.sp, color = gameMuted())
    }
}
