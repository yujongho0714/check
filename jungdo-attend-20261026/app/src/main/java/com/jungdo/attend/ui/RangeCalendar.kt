package com.jungdo.attend.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jungdo.attend.data.Holidays
import com.jungdo.attend.data.Repo
import java.util.Calendar

/**
 * 결석 날짜 고르기 달력
 *  - 날짜를 한 번 누르면 그 하루
 *  - 하루가 골라진 상태에서 그 뒤 날짜를 누르면 → 시작일~그날까지 기간
 *  - 기간이 골라진 상태에서 다시 누르면 → 새로 하루부터
 * 주말·공휴일은 빨강/파랑으로 보이고, 결석 처리에서는 저절로 빠져요.
 */
@Composable
fun RangeCalendar(from: String, to: String, onChange: (String, String) -> Unit) {
    var ym by remember { mutableStateOf(from.take(7)) }   // yyyy-MM
    val today = Repo.today()
    val y = ym.take(4).toInt(); val m = ym.substring(5, 7).toInt()
    val cal = Calendar.getInstance().apply { clear(); set(y, m - 1, 1) }
    val firstDow = cal.get(Calendar.DAY_OF_WEEK) - 1       // 0=일
    val nDays = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    fun shiftMonth(d: Int) { val c = Calendar.getInstance().apply { clear(); set(y, m - 1 + d, 1) }; ym = "%04d-%02d".format(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1) }

    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).border(1.dp, Color(0xFFE5E7EB), RoundedCornerShape(12.dp)).padding(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { shiftMonth(-1) }) { Text("◀", fontSize = 18.sp) }
            Spacer(Modifier.weight(1f))
            Text("${y}년 ${m}월", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { shiftMonth(1) }) { Text("▶", fontSize = 18.sp) }
        }
        Row(Modifier.fillMaxWidth()) {
            "일월화수목금토".forEachIndexed { i, c ->
                Text(c.toString(), Modifier.weight(1f).padding(vertical = 2.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    color = when (i) { 0 -> Color(0xFFDC2626); 6 -> Color(0xFF2563EB); else -> Muted }, fontSize = 13.sp)
            }
        }
        val cells = firstDow + nDays
        val rows = (cells + 6) / 7
        for (r in 0 until rows) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                for (c in 0 until 7) {
                    val idx = r * 7 + c - firstDow + 1
                    if (idx < 1 || idx > nDays) { Box(Modifier.weight(1f).aspectRatio(1.15f)); continue }
                    val day = "%s-%02d".format(ym, idx)
                    val inRange = day in from..to
                    val edge = day == from || day == to
                    val off = Holidays.isOff(day)
                    val bg = when { edge -> Navy; inRange -> Color(0xFFDBEAFE); else -> Color.Transparent }
                    val fg = when {
                        edge -> Color.White
                        c == 0 || (off && c != 6) -> Color(0xFFDC2626)
                        c == 6 -> Color(0xFF2563EB)
                        else -> Color(0xFF111827)
                    }
                    Box(
                        Modifier.weight(1f).aspectRatio(1.15f).padding(vertical = 2.dp).clip(RoundedCornerShape(8.dp)).background(bg)
                            .then(if (day == today && !edge) Modifier.border(2.dp, Navy, RoundedCornerShape(8.dp)) else Modifier)
                            .clickable {
                                if (from == to && day > from) onChange(from, day)   // 두 번째 누름 → 기간
                                else onChange(day, day)                              // 하루
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("$idx", color = fg, fontSize = 16.sp, fontWeight = if (edge || day == today) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }
        }
        Text("한 번 누르면 하루 · 이어서 뒤 날짜를 누르면 그날까지 기간 · 다시 누르면 새로 고르기", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(4.dp))
    }
}
