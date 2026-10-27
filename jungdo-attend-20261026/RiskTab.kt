package com.jungdo.attend.server

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jungdo.attend.call.Phone
import com.jungdo.attend.data.Repo
import com.jungdo.attend.data.Student
import com.jungdo.attend.data.rememberAbsencesRange
import com.jungdo.attend.data.rememberCustomHolidays
import com.jungdo.attend.data.rememberEventsRange
import com.jungdo.attend.report.RiskAnalyzer
import com.jungdo.attend.ui.BadRed
import com.jungdo.attend.ui.Muted
import com.jungdo.attend.ui.OkGreen
import com.jungdo.attend.ui.OutOrange
import com.jungdo.attend.ui.Pill

fun levelColor(l: String) = when (l) { "위험" -> BadRed; "주의" -> OutOrange; "안전" -> OkGreen; else -> Muted }

/** 퇴관 위험도: 결석이 늘어난 아이를 먼저 보여주고 바로 전화 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RiskTab(students: List<Student>) {
    val ctx = LocalContext.current
    val today = Repo.today()
    val from = Repo.shiftDay(today, -RiskAnalyzer.LOOKBACK)
    val events by rememberEventsRange(from, today)
    val absences by rememberAbsencesRange(from, today)
    val custom by rememberCustomHolidays()
    val risks = remember(students, events, absences, custom) { RiskAnalyzer.analyze(students, events, absences) }
    var filter by remember { mutableStateOf("위험+주의") }
    val shown = when (filter) { "위험+주의" -> risks.filter { it.level == "위험" || it.level == "주의" }; else -> risks }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Column {
                Text("퇴관 위험도", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("아이마다 '자기 평소 출석'과 최근 2주를 비교해요. 주말·공휴일·사유 결석은 빼고 계산합니다.\n" +
                    "위험 ${risks.count { it.level == "위험" }} · 주의 ${risks.count { it.level == "주의" }} · 안전 ${risks.count { it.level == "안전" }} · 기록 부족 ${risks.count { it.level == "기록 부족" }}",
                    color = Muted, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
                    listOf("위험+주의", "전체").forEach { f -> Pill(f, filter == f, 15.sp) { filter = f } }
                }
            }
        }
        if (shown.isEmpty()) item { Text("걱정되는 아이가 없어요 👍", color = OkGreen, modifier = Modifier.padding(16.dp)) }
        items(shown, key = { it.student.id }) { k ->
            Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(k.level, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                            modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(levelColor(k.level)).padding(horizontal = 8.dp, vertical = 2.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(k.student.name, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text("  ${k.student.grade}${if (k.student.demo) " · 시범단" else ""}", color = Muted)
                        Spacer(Modifier.weight(1f))
                        Text("${k.score}점", fontWeight = FontWeight.Bold, color = levelColor(k.level))
                    }
                    k.reasons.forEach { Text("• $it", fontSize = 14.sp) }
                    if (k.student.phones.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        k.student.phones.forEachIndexed { i, p ->
                            OutlinedButton(onClick = { Phone.call(ctx, p, listOf(k.student.id)) }) { Text("📞 ${k.student.phoneLabel(i)}") }
                        }
                    }
                }
            }
        }
    }
}
