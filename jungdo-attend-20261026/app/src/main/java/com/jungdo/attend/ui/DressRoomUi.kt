package com.jungdo.attend.ui

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jungdo.attend.data.Dress
import com.jungdo.attend.data.Repo
import com.jungdo.attend.data.Student
import com.jungdo.attend.ui.game.GameButton
import com.jungdo.attend.ui.game.GamePill
import com.jungdo.attend.ui.game.LocalGameTheme
import com.jungdo.attend.ui.game.gameMuted
import com.jungdo.attend.ui.game.PixelText

/**
 * 드레스룸 (11단계). 보관함의 "👗 드레스룸" 탭.
 *  - 위(또는 가로 화면에선 왼쪽): 내 캐릭터 + 방향(앞·뒤·오른쪽·왼쪽) + 서기·걷기·공격 미리보기
 *  - 부위 탭 8개 → 그 부위에서 **가진 옷만** 목록으로 → 누르면 입기, 입고 있는 걸 다시 누르면 벗기
 *  - [랜덤 코디] = 가진 옷 안에서 부위마다 하나씩 / [모두 벗기]
 *  - 입고 벗는 건 누르는 즉시 서버(students/{id}.dressEquip)에 저장돼서 어느 기기에서나 같은 모습
 */
@Composable
fun DressRoomPanel(s: Student, modifier: Modifier = Modifier) {
    val theme = LocalGameTheme.current
    val pack = rememberDressPack()
    val land = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    var cat by remember { mutableStateOf(Dress.CATS.first()) }
    var dir by remember { mutableStateOf(DressDir.FRONT) }
    var action by remember { mutableStateOf(DressAction.STAND) }

    // 부위별로 가진 옷 (번호 순)
    val owned = remember(s.dress) {
        s.dress.distinct().groupBy { Dress.catOf(it) }.mapValues { (_, v) -> v.sortedBy { Dress.noOf(it) } }
    }
    val list = owned[cat].orEmpty()
    val worn = s.dressEquip[cat]

    fun nameOf(key: String) = pack?.item(key)?.name?.takeIf { it.isNotBlank() } ?: "${Dress.noOf(key)}번"

    val preview: @Composable () -> Unit = {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            DressCharacter(s.dressEquip, if (land) 150.dp else 140.dp, dir = dir, action = action, onAttackDone = { action = DressAction.STAND })
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(DressDir.FRONT to "앞", DressDir.BACK to "뒤", DressDir.RIGHT to "오른쪽", DressDir.LEFT to "왼쪽").forEach { (d, label) ->
                    GamePill(label, dir == d, 14.sp) { dir = d }
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                GamePill("서기", action == DressAction.STAND, 14.sp) { action = DressAction.STAND }
                GamePill("걷기", action == DressAction.WALK, 14.sp) { action = DressAction.WALK }
                GamePill("공격", action == DressAction.ATTACK, 14.sp) { action = DressAction.ATTACK }
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GameButton(
                    "랜덤 코디", onClick = {
                        // 가진 옷이 있는 부위마다 하나씩 (없는 부위는 그대로 벗은 상태)
                        Repo.setDressEquipAll(s.id, owned.mapValues { (_, v) -> v.random() })
                    },
                    enabled = owned.isNotEmpty(), modifier = Modifier.width(if (land) 112.dp else 140.dp).height(44.dp),
                    color = theme.primary, fontSize = 16.sp, sound = null,
                )
                GameButton(
                    "모두 벗기", onClick = { Repo.setDressEquipAll(s.id, emptyMap()) },
                    enabled = s.dressEquip.isNotEmpty(), modifier = Modifier.width(if (land) 112.dp else 140.dp).height(44.dp),
                    color = theme.secondary, fontSize = 16.sp, sound = null,
                )
            }
        }
    }

    val picker: @Composable (Modifier) -> Unit = { m ->
        Column(m) {
            // 부위 탭 (옆으로 밀어서 보기) — 뒤의 숫자는 그 부위에서 가진 옷 수
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Dress.CATS.forEach { c ->
                    GamePill("${Dress.catLabel(c)} ${owned[c]?.size ?: 0}", cat == c, 15.sp) { cat = c }
                }
            }
            Spacer(Modifier.height(6.dp))
            if (list.isEmpty()) {
                PixelText("아직 ${Dress.catLabel(cat)} 옷이 없어요. 🎁 뽑기에서 모아 보세요!", color = gameMuted(), fontSize = 15.sp, modifier = Modifier.padding(8.dp))
            } else {
                Text("옷을 누르면 입어요 (입은 옷을 다시 누르면 벗어요)", color = gameMuted(), fontSize = 13.sp)
                LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(list) { key ->
                        val on = worn == key
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                .background(if (on) Color(0x3322C55E) else Color(0x14808080))
                                .clickable { Repo.setDressEquip(s.id, cat, if (on) "" else key) }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("${Dress.noOf(key)}", fontSize = 13.sp, color = gameMuted(), modifier = Modifier.width(34.dp))
                            Text(nameOf(key), fontSize = 17.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal, maxLines = 1, modifier = Modifier.weight(1f))
                            if (on) PixelText("✔ 입는 중", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White,
                                modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(OkGreen).padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                    }
                }
            }
        }
    }

    if (land) {
        Row(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.width(250.dp)) { preview() }
            picker(Modifier.weight(1f))
        }
    } else {
        Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
            preview()
            Spacer(Modifier.height(8.dp))
            picker(Modifier.weight(1f).fillMaxWidth())
        }
    }
}
