package com.jungdo.attend.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jungdo.attend.data.Dress
import com.jungdo.attend.data.Loot
import com.jungdo.attend.data.Repo
import com.jungdo.attend.data.Student
import com.jungdo.attend.ui.game.GameButton
import com.jungdo.attend.ui.game.LocalGameTheme
import com.jungdo.attend.ui.game.gameMuted
import com.jungdo.attend.ui.game.PixelText

/**
 * 뽑기 (10단계). 보관함의 "🎁 뽑기" 탭.
 *  - 1회 / 10회 / 전부(가진 골드로 가능한 만큼, 최대 100번)
 *  - 옷 800종 전부 같은 확률. 이미 가진 옷이 나오면 한 개당 환급 골드를 돌려받음
 *  - 골드 계산은 서버 값 기준(Repo.pullDress)이라 여러 기기에서 눌러도 안전
 */
@Composable
fun GachaPanel(s: Student, modifier: Modifier = Modifier) {
    val theme = LocalGameTheme.current
    val pack = rememberDressPack()
    val price = Loot.gachaPrice
    val refund = Loot.dupRefund
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<Dress.PullResult?>(null) }
    var msg by remember { mutableStateOf("") }

    fun nameOf(key: String) = pack?.item(key)?.name?.takeIf { it.isNotBlank() } ?: key
    fun pull(count: Int) {
        if (busy) return
        busy = true; msg = ""
        Repo.pullDress(s.id, count, price, refund) { r, err ->
            busy = false
            if (r != null) result = r else { result = null; msg = err ?: "뽑지 못했어요" }
        }
    }
    val canOne = !busy && s.gold >= price
    val can10 = !busy && s.gold >= price * 10
    val canAll = !busy && (price <= 0 || s.gold >= price)
    val allCount = if (price <= 0) 100 else (s.gold / price).coerceAtMost(100)

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        PixelText("💰 ${s.gold} 골드 · 뽑기 1회 ${price}골드 · 가진 옷 ${s.dress.size}/${Dress.allKeys.size}", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text("옷 800개 중 하나가 같은 확률로 나와요. 이미 가진 옷이 나오면 1개당 ${refund}골드를 돌려줘요.", color = gameMuted(), fontSize = 13.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GameButton("1회", onClick = { pull(1) }, enabled = canOne, modifier = Modifier.weight(1f).height(64.dp), color = theme.primary, fontSize = 22.sp, sound = null)
            GameButton("10회", onClick = { pull(10) }, enabled = can10, modifier = Modifier.weight(1f).height(64.dp), color = theme.primary, fontSize = 22.sp, sound = null)
            GameButton("전부 (${allCount}회)", onClick = { pull(0) }, enabled = canAll && allCount > 0, modifier = Modifier.weight(1.3f).height(64.dp), color = theme.secondary, fontSize = 20.sp, sound = null)
        }
        if (busy) Text("뽑는 중…", color = gameMuted(), fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp))
        if (msg.isNotBlank()) Text(msg, color = BadRed, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp))
        Spacer(Modifier.height(8.dp))

        val r = result
        if (r == null) {
            if (msg.isBlank() && !busy) Text(if (s.gold < price) "골드가 모자라요. 보석을 장착하고 등원·하원하면 골드를 받아요!" else "버튼을 눌러 옷을 뽑아 보세요!", color = gameMuted(), fontSize = 15.sp)
        } else {
            Text(
                "새 옷 ${r.newKeys.size}개 · 중복 ${r.dupCount}개" + if (r.refund > 0) " (+${r.refund}골드)" else "",
                fontWeight = FontWeight.Bold, fontSize = 18.sp,
            )
            if (r.keys.size == 1) {
                // 한 개 뽑았을 땐 그 옷만 입은 캐릭터를 크게
                val k = r.keys.first()
                DressCharacter(mapOf(Dress.catOf(k) to k), 150.dp)
            }
            // 뽑은 순서대로 목록 (같은 옷이 또 나오면 두 번째부터 중복)
            val rows = remember(r) {
                val fresh = r.newKeys.toMutableSet()
                r.keys.map { k -> k to fresh.remove(k) }
            }
            LazyColumn(Modifier.weight(1f, fill = true).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                itemsIndexed(rows) { _, (k, isNew) ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(if (isNew) Color(0x3322C55E) else Color(0x14808080)).padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(if (isNew) "NEW" else "중복", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (isNew) OkGreen else gameMuted(), modifier = Modifier.width(40.dp))
                        Text("${Dress.catLabel(Dress.catOf(k))} · ${nameOf(k)}", fontSize = 15.sp, maxLines = 1, modifier = Modifier.weight(1f))
                        if (!isNew) Text("+${refund}G", fontSize = 13.sp, color = gameMuted())
                    }
                }
            }
        }
    }
}
