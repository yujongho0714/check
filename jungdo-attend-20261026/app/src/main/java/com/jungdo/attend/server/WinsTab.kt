package com.jungdo.attend.server

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jungdo.attend.data.Prizes
import com.jungdo.attend.data.Repo
import com.jungdo.attend.data.Win
import com.jungdo.attend.data.Wins
import com.jungdo.attend.data.rememberPendingWins
import com.jungdo.attend.ui.Muted
import com.jungdo.attend.ui.OkGreen
import com.jungdo.attend.ui.dayLabel

/** 상품 당첨 중 아직 안 나눠준 것 — 상품을 건네주고 [지급] 을 누르면 목록에서 사라지고 지급내역(payouts)에 기록돼요 */
@Composable
fun WinsTab() {
    val wins by rememberPendingWins()
    var confirm by remember { mutableStateOf<Win?>(null) }
    val ctx = LocalContext.current

    // 당첨 알림을 눌러 들어온 경우: 그 건의 지급 확인창을 바로 띄움
    val target by WinAlert.openWin.collectAsState()
    LaunchedEffect(target, wins) {
        val id = target ?: return@LaunchedEffect
        val w = wins.firstOrNull { it.id == id }
        if (w != null) { confirm = w; WinAlert.openWin.value = null }
        else { delay(4_000); if (WinAlert.openWin.value == id) WinAlert.openWin.value = null } // 이미 지급된 건이면 정리
    }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Column(Modifier.padding(top = 12.dp)) {
                Text("지급 대기 · ${wins.size}건", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("상품을 나눠주고 [지급] 을 누르세요. 누르면 목록에서 사라지고 지급내역에 남아요.", color = Muted, fontSize = 13.sp)
            }
        }
        if (wins.isEmpty()) item { Text("지급할 상품이 없어요 ✔", color = Muted, modifier = Modifier.padding(top = 8.dp)) }
        items(wins, key = { it.id }) { w ->
            val photo = remember(w.prizeId, Prizes.list) { Prizes.list.firstOrNull { it.id == w.prizeId }?.photo?.let(Prizes::decode) }
            Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (photo != null) {
                        Image(photo.asImageBitmap(), null, Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
                    } else {
                        Text("🎁", fontSize = 34.sp, modifier = Modifier.width(56.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(w.name + if (w.grade.isNotBlank()) " (${w.grade})" else "", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Text(w.prizeName, fontSize = 15.sp)
                        Text("${dayLabel(w.day)} ${Repo.timeText(w.time)} 당첨", color = Muted, fontSize = 12.sp)
                    }
                    Button(onClick = { confirm = w }) { Text("지급") }
                }
            }
        }
    }

    confirm?.let { w ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text("${w.name} 에게 지급했나요?") },
            text = { Text("${w.prizeName} 을(를) 건네줬다면 [지급 완료] 를 눌러 주세요.") },
            confirmButton = { Button(onClick = { Wins.pay(w); WinAlert.cancel(ctx, w.id); confirm = null }) { Text("지급 완료") } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("아니요") } },
        )
    }
}
