package com.jungdo.attend.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jungdo.attend.data.Repo
import com.jungdo.attend.data.UiSize
import kotlin.math.roundToInt

/** 출결 팝업·보관함의 박스/캐릭터 크기를 정하는 곳 (관리자 설정 / 관장님 폰 설정 둘 다에 들어가요). 저장하면 모든 기기에 똑같이 적용 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UiSizeSettings() {
    var box by remember(UiSize.box) { mutableStateOf(UiSize.box) }
    var ch by remember(UiSize.char) { mutableStateOf(UiSize.char) }
    var msg by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle("캐릭터 · 내 정보 크기 (모든 기기, 모든 원생에게 적용)")
        Text(
            "출결 팝업과 보관함에서 박스와 캐릭터가 얼마나 크게 보일지 정해요. 100% = 각 기기 화면에 딱 맞는 기본 크기예요. 저장하면 키오스크와 다른 폰에도 바로 적용돼요.",
            color = Muted, fontSize = 13.sp,
        )
        Text("박스 크기 ${(box * 100).roundToInt()}%  (캐릭터 무대 · 보관함 왼쪽 칸)", fontSize = 15.sp)
        Slider(box, { box = it; msg = "" }, valueRange = UiSize.MIN..UiSize.MAX)
        Text("캐릭터 크기 ${(ch * 100).roundToInt()}%  (박스 안에서)", fontSize = 15.sp)
        Slider(ch, { ch = it; msg = "" }, valueRange = UiSize.MIN..UiSize.MAX)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { Repo.saveUiSize(box, ch); msg = "저장했어요 ✔ 모든 기기에 적용돼요" }) { Text("저장 (모든 기기에 적용)") }
            OutlinedButton(onClick = { box = 1f; ch = 1f; Repo.saveUiSize(1f, 1f); msg = "기본 크기(100%)로 돌렸어요 ✔" }) { Text("기본값으로") }
        }
        if (msg.isNotBlank()) Text(msg, color = OkGreen, fontSize = 14.sp)
    }
}
