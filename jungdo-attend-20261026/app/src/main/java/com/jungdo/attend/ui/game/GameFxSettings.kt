package com.jungdo.attend.ui.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jungdo.attend.data.Prefs
import com.jungdo.attend.ui.Muted
import com.jungdo.attend.ui.SectionTitle

/** 키오스크 관리자 [설정] 탭에 들어가는 "효과음 · 진동" 설정 + 들어보기 */
@Composable
fun GameFxSettings() {
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }
    var sound by remember { mutableStateOf(prefs.fxSound) }
    var haptic by remember { mutableStateOf(prefs.fxHaptic) }
    var volume by remember { mutableStateOf(prefs.fxVolume.toFloat()) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle("화면 테마")
        var themeId by remember { mutableStateOf(GameThemeSwitch.override ?: prefs.gameTheme) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GameThemes.all.forEach { t ->
                GameButton(
                    (if (t.id == themeId) "✔ " else "") + t.name,
                    onClick = { themeId = t.id; prefs.gameTheme = t.id; GameThemeSwitch.override = t.id },
                    modifier = Modifier.weight(1f),
                    color = if (t.id == themeId) t.primary else t.card,
                )
            }
        }
        Text("누르면 바로 바뀌어요.", color = Muted, fontSize = 13.sp)

        SectionTitle("효과음 · 진동")

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("버튼 효과음", fontSize = 18.sp, modifier = Modifier.weight(1f))
            Switch(checked = sound, onCheckedChange = {
                sound = it; prefs.fxSound = it; GameFx.reload(ctx)
            })
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("소리 크기", fontSize = 16.sp, color = Muted)
            Slider(
                value = volume,
                onValueChange = { volume = it },
                onValueChangeFinished = {
                    prefs.fxVolume = volume.toInt(); GameFx.reload(ctx)
                    GameSound.play(GameSound.Fx.TAP) // 바꾼 크기로 한 번 들려줘요
                },
                valueRange = 0f..100f,
                enabled = sound,
                modifier = Modifier.weight(1f).padding(start = 12.dp),
            )
            Text("${volume.toInt()}", fontSize = 16.sp, modifier = Modifier.width(36.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("버튼 진동", fontSize = 18.sp, modifier = Modifier.weight(1f))
            Switch(checked = haptic, onCheckedChange = {
                haptic = it; prefs.fxHaptic = it; GameFx.reload(ctx)
                GameHaptic.pulse(GameHaptic.Kind.SUCCESS)
            })
        }
        Text("진동 모터가 없는 태블릿은 진동이 안 나와요.", color = Muted, fontSize = 13.sp)

        Text("들어보기 (눌러 보세요)", fontSize = 16.sp, color = Muted)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GameButton("등원", onClick = {}, modifier = Modifier.weight(1f),
                color = LocalGameTheme.current.primary, sound = GameSound.Fx.IN, haptic = GameHaptic.Kind.SUCCESS)
            GameButton("하원", onClick = {}, modifier = Modifier.weight(1f),
                color = LocalGameTheme.current.secondary, sound = GameSound.Fx.OUT, haptic = GameHaptic.Kind.SUCCESS)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GameButton("확인", onClick = {}, modifier = Modifier.weight(1f),
                color = LocalGameTheme.current.good, sound = GameSound.Fx.OK, haptic = GameHaptic.Kind.SUCCESS)
            GameButton("주의", onClick = {}, modifier = Modifier.weight(1f),
                color = LocalGameTheme.current.warn, sound = GameSound.Fx.WARN, haptic = GameHaptic.Kind.FAIL)
            GameButton("실패", onClick = {}, modifier = Modifier.weight(1f),
                color = LocalGameTheme.current.bad, sound = GameSound.Fx.FAIL, haptic = GameHaptic.Kind.FAIL)
        }
        GameButton("레벨 업!", onClick = {}, modifier = Modifier.fillMaxWidth(),
            color = LocalGameTheme.current.primary, sound = GameSound.Fx.LEVELUP, haptic = GameHaptic.Kind.SUCCESS)
    }
}
