package com.jungdo.attend.ui

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import com.jungdo.attend.ui.game.GamePanel
import com.jungdo.attend.ui.game.GameSlot
import com.jungdo.attend.ui.game.GameTextButton
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
 *  - 장비 팔기: 입고 있지 않은 옷은 1벌당 Dress.SELL_GOLD 골드에 팔 수 있음. 원생 성별(남/여)과 다른 성별 옷은
 *    "성별 다름" 표시가 붙고, [성별 다른 장비 모두 팔기] 로 한 번에 팔 수 있음 (성별은 옷 이름으로 판단 → Dress.genderOfName)
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
    var meSend by remember { mutableStateOf(false) }
    var meMsg by remember { mutableStateOf("") }

    // 부위별로 가진 옷 (번호 순)
    val owned = remember(s.dress) {
        s.dress.distinct().groupBy { Dress.catOf(it) }.mapValues { (_, v) -> v.sortedBy { Dress.noOf(it) } }
    }
    val list = owned[cat].orEmpty()
    val worn = s.dressEquip[cat]

    fun nameOf(key: String) = pack?.item(key)?.name?.takeIf { it.isNotBlank() } ?: "${Dress.noOf(key)}번"
    fun wrongSex(key: String) = Dress.mismatch(s.gender, nameOf(key))

    // 팔기 확인 창 (팔 옷 목록) · 결과 글
    var sellKeys by remember { mutableStateOf<List<String>?>(null) }
    var sellMsg by remember { mutableStateOf("") }
    var menuKey by remember { mutableStateOf<String?>(null) }   // 누른 칸
    val wornKeys = s.dressEquip.values.toSet()
    // 성별이 달라서 팔 수 있는 옷 (입고 있는 건 빼고, 모든 부위)
    val wrongAll = s.dress.distinct().filter { wrongSex(it) && it !in wornKeys }

    val preview: @Composable () -> Unit = {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            DressCharacter(s.dressEquip, if (land) 260.dp else 320.dp, dir = dir, action = action, onAttackDone = { action = DressAction.STAND }, zoom = com.jungdo.attend.data.UiSize.layout(land).bag)
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
            Spacer(Modifier.height(6.dp))
            GameButton(
                "🎮 포켓몬 게임으로 내 캐릭터 보내기", onClick = { meSend = true },
                enabled = s.dress.isNotEmpty() || s.dressEquip.isNotEmpty(), modifier = Modifier.width(if (land) 232.dp else 288.dp).height(48.dp),
                color = theme.primary, fontSize = 15.sp, sound = null,
            )
            if (meMsg.isNotBlank()) Text(meMsg, fontSize = 13.sp, color = gameMuted())
        }
    }

    if (meSend) {
        var phone by remember { mutableStateOf(s.pokeBag.mapNotNull { com.jungdo.attend.data.PokeGift.parse(it)?.sentTo }.firstOrNull { it.isNotBlank() } ?: "") }
        var err by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { meSend = false },
            title = { Text("내 휴대폰 번호", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("내 폰으로 게임 링크가 가요. (부모님 번호 말고 내 번호!)\n게임에서도 같은 번호를 써야 해요.", fontSize = 15.sp)
                    Spacer(Modifier.height(8.dp))
                    androidx.compose.material3.OutlinedTextField(value = phone, onValueChange = { v -> phone = v.filter { c -> c.isDigit() }.take(11) }, singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone), placeholder = { Text("01012345678") })
                    if (err.isNotBlank()) Text(err, color = Color(0xFFE5484D), fontSize = 14.sp)
                }
            },
            confirmButton = { TextButton(onClick = {
                val e = com.jungdo.attend.data.MeCode.send(s, phone)
                if (e != null) err = e else { meSend = false; meMsg = "📩 내 폰으로 캐릭터 링크를 보냈어요!" }
            }) { Text("문자로 받기", fontSize = 17.sp, fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton(onClick = { meSend = false }) { Text("닫기") } },
        )
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
            if (wrongAll.isNotEmpty()) {
                GameButton(
                    "성별 다른 장비 모두 팔기 (${wrongAll.size}벌 · ${wrongAll.size * Dress.SELL_GOLD}골드)", onClick = { sellKeys = wrongAll },
                    modifier = Modifier.fillMaxWidth().height(44.dp), color = theme.secondary, fontSize = 15.sp, sound = null,
                )
                Spacer(Modifier.height(6.dp))
            }
            if (sellMsg.isNotBlank()) {
                PixelText(sellMsg, color = gameMuted(), fontSize = 13.sp, modifier = Modifier.padding(bottom = 4.dp))
            }
            if (list.isEmpty()) {
                PixelText("아직 ${Dress.catLabel(cat)} 옷이 없어요. 🎁 뽑기에서 모아 보세요!", color = gameMuted(), fontSize = 15.sp, modifier = Modifier.padding(8.dp))
            } else {
                Text("칸을 누르면 크게 입어 보고 [장착하기 · 판매하기]를 고를 수 있어요", color = gameMuted(), fontSize = 13.sp)
                Spacer(Modifier.height(4.dp))
                // 가방 칸: 한 칸에 옷 한 벌 (입은 옷은 초록 표시)
                LazyVerticalGrid(GridCells.Adaptive(if (land) 92.dp else 86.dp), Modifier.weight(1f).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    gridItems(list) { key ->
                        val on = worn == key
                        Box(contentAlignment = Alignment.TopStart) {
                            GameSlot(Modifier.fillMaxWidth().aspectRatio(0.82f)
                                .then(if (on) Modifier.background(Color(0x3322C55E), RoundedCornerShape(8.dp)) else Modifier)
                                .clickable { menuKey = key }) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    DressItemIcon(key, 64.dp)
                                    Text(nameOf(key), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal)
                                }
                            }
                            if (on) PixelText("입는 중", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White,
                                modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(OkGreen).padding(horizontal = 5.dp, vertical = 1.dp))
                            else if (wrongSex(key)) PixelText("성별 다름", fontSize = 10.sp, color = Color.White,
                                modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Color(0xFFB45309)).padding(horizontal = 5.dp, vertical = 1.dp))
                        }
                    }
                }
            }
        }
    }

    // 칸을 누르면: 크게 입어 보기 + 장착하기 / 판매하기 / 닫기
    menuKey?.let { key ->
        val on = worn == key
        val tryOn = remember(s.dressEquip, key) { s.dressEquip + (Dress.catOf(key) to key) }
        Dialog(onDismissRequest = { menuKey = null }) {
            GamePanel(Modifier.fillMaxWidth(), padding = 22.dp) {
                DressCharacter(tryOn, 340.dp, action = DressAction.WALK, zoom = com.jungdo.attend.data.UiSize.layout(land).bag)
                Spacer(Modifier.height(6.dp))
                Text(nameOf(key), fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("${Dress.catLabel(Dress.catOf(key))} · ${Dress.noOf(key)}번" + if (on) " · 입는 중" else "", fontSize = 14.sp, color = gameMuted())
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GameButton(if (on) "벗기" else "장착하기", onClick = {
                        Repo.setDressEquip(s.id, Dress.catOf(key), if (on) "" else key); menuKey = null
                    }, color = theme.good, fontSize = 18.sp, sound = null)
                    GameButton("판매하기 +${Dress.SELL_GOLD}G", onClick = { menuKey = null; sellKeys = listOf(key) },
                        enabled = !on, color = theme.secondary, fontSize = 18.sp, sound = null)
                    GameTextButton("닫기", 18.sp) { menuKey = null }
                }
                if (on) Text("입고 있는 옷은 벗어야 팔 수 있어요", fontSize = 13.sp, color = gameMuted(), modifier = Modifier.padding(top = 6.dp))
            }
        }
    }

    sellKeys?.let { keys ->
        AlertDialog(
            onDismissRequest = { sellKeys = null },
            title = { Text("정말로 판매하시겠습니까?") },
            text = {
                Text(
                    (if (keys.size == 1) "「${nameOf(keys[0])}」" else "${keys.size}벌") +
                        "을 팔고 ${keys.size * Dress.SELL_GOLD}골드를 받을까요?\n판 옷은 사라지고 되돌릴 수 없어요. (뽑기에서 다시 나올 수는 있어요)",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    sellKeys = null
                    Repo.sellDress(s.id, keys) { n, gold ->
                        sellMsg = when {
                            n < 0 -> "인터넷을 확인하고 다시 눌러 주세요"
                            n == 0 -> "팔 수 있는 옷이 없어요 (입고 있는 옷은 못 팔아요)"
                            else -> "${n}벌을 팔아서 ${gold}골드를 받았어요!"
                        }
                    }
                }) { Text("예") }
            },
            dismissButton = { TextButton(onClick = { sellKeys = null }) { Text("아니오") } },
        )
    }

    if (land) {
        Row(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.width(300.dp)) { preview() }
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
