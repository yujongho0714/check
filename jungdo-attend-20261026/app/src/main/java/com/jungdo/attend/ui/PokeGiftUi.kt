package com.jungdo.attend.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jungdo.attend.data.PokeGift
import com.jungdo.attend.data.Student
import com.jungdo.attend.ui.game.GameButton
import com.jungdo.attend.ui.game.LocalGameTheme
import com.jungdo.attend.ui.game.PixelText
import com.jungdo.attend.ui.game.gameMuted

/**
 * 보관함의 "⚡ 포켓몬" 탭: 포켓몬 어드벤처 출석 뽑기
 *  - 포켓몬 뽑기 / 기술 뽑기 (처음 한 번씩 무료) → 가방에 들어감
 *  - 가방에서 [사용] → 본인 휴대폰 번호 입력 → 그 번호로 선물 코드 + 게임 주소 문자가 바로 감
 *  - 실수로 닫혀도 가방에 남아 있고, 보낸 코드는 계속 보이며 [다시 보내기] 가능
 */
@Composable
fun PokeGiftPanel(s: Student, modifier: Modifier = Modifier) {
    val theme = LocalGameTheme.current
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }
    var last by remember { mutableStateOf<PokeGift.Item?>(null) }
    var sendRaw by remember { mutableStateOf<String?>(null) }
    val freeP = !s.pokeFree.contains("P")
    val freeM = !s.pokeFree.contains("M")
    val bag = s.pokeBag.mapNotNull { r -> PokeGift.parse(r)?.let { r to it } }.sortedWith(compareBy({ it.second.used }, { -it.second.rar }))

    fun pull(kind: String) {
        if (busy) return
        busy = true; msg = ""
        PokeGift.pull(s.id, kind) { r, err -> busy = false; last = r; if (err != null) msg = err }
    }

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        PixelText("💰 ${s.gold} 골드 · 포켓몬 어드벤처 뽑기", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text("뽑은 건 아래 가방에 들어가요. [사용]을 누르고 내 휴대폰 번호를 넣으면 게임 선물 코드가 문자로 와요.", color = gameMuted(), fontSize = 13.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GameButton(if (freeP) "⚡ 포켓몬 뽑기\n(첫 1회 무료)" else "⚡ 포켓몬 뽑기\n${PokeGift.PRICE_P}골드", onClick = { pull("P") },
                enabled = !busy && (freeP || s.gold >= PokeGift.PRICE_P), modifier = Modifier.weight(1f).height(72.dp), color = theme.primary, fontSize = 19.sp, sound = null)
            GameButton(if (freeM) "📀 기술 뽑기\n(첫 1회 무료)" else "📀 기술 뽑기\n${PokeGift.PRICE_M}골드", onClick = { pull("M") },
                enabled = !busy && (freeM || s.gold >= PokeGift.PRICE_M), modifier = Modifier.weight(1f).height(72.dp), color = theme.secondary, fontSize = 19.sp, sound = null)
        }
        if (busy) Text("뽑는 중…", color = gameMuted(), fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp))
        if (msg.isNotBlank()) Text(msg, color = BadRed, fontSize = 15.sp, modifier = Modifier.padding(top = 6.dp))
        last?.let { it ->
            Text("🎉 [${PokeGift.RAR[it.rar]}] ${it.label}", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(PokeGift.RARC[it.rar]), modifier = Modifier.padding(top = 6.dp))
            Text("가방에 들어갔어요! 아래에서 [사용]을 눌러 내 폰으로 받으세요.", color = gameMuted(), fontSize = 13.sp)
        }
        Spacer(Modifier.height(8.dp))
        Text("🎒 포켓몬 가방 ${bag.size}개", fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.fillMaxWidth())
        LazyColumn(Modifier.weight(1f, fill = true).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(bag, key = { it.second.id }) { (raw, it) ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (it.used) Color(0x14808080) else Color(0x2222C55E)).padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("[${PokeGift.RAR[it.rar]}] ${it.label}", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color(PokeGift.RARC[it.rar]))
                        if (it.used) Text("코드 ${it.code} · ${mask(it.sentTo)}(으)로 보냄", fontSize = 13.sp, color = gameMuted())
                        else Text("아직 안 받았어요", fontSize = 13.sp, color = gameMuted())
                    }
                    GameButton(if (it.used) "다시 보내기" else "사용", onClick = { sendRaw = raw }, modifier = Modifier.height(52.dp), color = if (it.used) theme.secondary else theme.primary, fontSize = 17.sp)
                }
            }
        }
    }

    sendRaw?.let { raw ->
        var phone by remember(raw) { mutableStateOf(PokeGift.parse(raw)?.sentTo ?: "") }
        var err by remember(raw) { mutableStateOf("") }
        var sending by remember(raw) { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { if (!sending) sendRaw = null },
            title = { Text("내 휴대폰 번호", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("내 폰 번호를 써 주세요. (부모님 번호 말고 내 번호!)\n게임에서도 같은 번호를 넣어야 선물을 받을 수 있어요.", fontSize = 15.sp)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = phone, onValueChange = { v -> phone = v.filter { c -> c.isDigit() }.take(11) }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), placeholder = { Text("01012345678") })
                    if (err.isNotBlank()) Text(err, color = BadRed, fontSize = 14.sp)
                    if (sending) Text("보내는 중…", color = gameMuted(), fontSize = 14.sp)
                }
            },
            confirmButton = {
                TextButton(enabled = !sending, onClick = {
                    sending = true; err = ""
                    PokeGift.send(s.id, s.name, raw, phone) { code, e ->
                        sending = false
                        if (e != null) err = e else { msg = "📩 ${mask(PokeGift.norm(phone))}(으)로 보냈어요! 코드 $code"; sendRaw = null }
                    }
                }) { Text("문자로 받기", fontSize = 17.sp, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(enabled = !sending, onClick = { sendRaw = null }) { Text("닫기") } },
        )
    }
}

private fun mask(n: String) = if (n.length >= 10) n.take(3) + "-****-" + n.takeLast(4) else n
