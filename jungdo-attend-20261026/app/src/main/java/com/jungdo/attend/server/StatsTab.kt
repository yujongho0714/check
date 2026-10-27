package com.jungdo.attend.server

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jungdo.attend.data.Absence
import com.jungdo.attend.data.Days
import com.jungdo.attend.data.rememberAbsencesRange
import com.jungdo.attend.ui.Muted
import com.jungdo.attend.ui.Navy

/** 결석 사유 분류 — 앞에서부터 처음 맞는 칸에 들어가요 (사유 글자에 키워드가 있으면) */
object ReasonCategory {
    val RULES = listOf(
        "질병·병원" to listOf("아파", "아프", "병원", "감기", "독감", "몸살", "코로나", "장염", "수술", "입원", "다쳐", "다침", "골절", "치료", "진료", "컨디션", "기침", "두통", "배탈", "열이", "열나"),
        "여행·휴가" to listOf("여행", "휴가", "캠핑", "놀러", "해외", "제주"),
        "가족 일정" to listOf("가족", "할머니", "할아버지", "제사", "결혼식", "장례", "친척", "외갓집", "본가"),
        "학교 행사" to listOf("학교", "소풍", "체험", "수련회", "시험", "행사", "방과후", "운동회", "현장학습"),
        "학원·다른 활동" to listOf("학원", "과외", "피아노", "수영", "영어", "수학", "레슨", "대회"),
        "휴식·방학" to listOf("방학", "쉬", "쉴", "휴식"),
        "개인 사정" to listOf("개인"),
    )
    const val OTHER = "기타·분류 안 됨"
    fun of(reason: String): String = RULES.firstOrNull { (_, ws) -> ws.any { reason.contains(it) } }?.first ?: OTHER
}

/** 월별 결석 사유 통계 */
@Composable
fun StatsTab() {
    var month by remember { mutableStateOf(Days.thisMonth()) }
    val abs by rememberAbsencesRange(Days.dayOf(month, 1), Days.dayOf(month, Days.daysInMonth(month)))
    var open by remember { mutableStateOf<String?>(null) }

    val byCat: Map<String, List<Absence>> = abs.groupBy { ReasonCategory.of(it.reason) }
    val order = ReasonCategory.RULES.map { it.first } + ReasonCategory.OTHER
    val rows = order.mapNotNull { c -> byCat[c]?.let { c to it } }.sortedByDescending { it.second.size }
    val maxN = rows.maxOfOrNull { it.second.size } ?: 1
    val perKid = abs.groupBy { it.studentId }.values.map { l -> l.first().name to l.size }.sortedByDescending { it.second }.take(10)

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("결석 사유 통계", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { month = Days.shiftMonth(month, -1) }) { Text("◀") }
                    Text(Days.monthLabel(month), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    OutlinedButton(onClick = { month = Days.shiftMonth(month, 1) }) { Text("▶") }
                }
                Text("부모님께 연락받아 저장한 사유 결석 기준이에요. 결석 ${abs.size}건 (아이 ${abs.map { it.studentId }.distinct().size}명 · 날짜별로 1건씩 셈)", color = Muted, fontSize = 13.sp)
            }
        }
        if (rows.isEmpty()) item { Text("이 달에 저장된 사유 결석이 없어요.", color = Muted) }
        items(rows, key = { it.first }) { (cat, list) ->
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(12.dp),
                modifier = Modifier.clickable { open = if (open == cat) null else cat }) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(cat, fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.weight(1f))
                        Text("${list.size}건 · ${list.map { it.studentId }.distinct().size}명", color = Muted)
                    }
                    Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(Color(0xFFE5E7EB))) {
                        Box(Modifier.fillMaxWidth(list.size.toFloat() / maxN).height(10.dp).background(Navy))
                    }
                    if (open == cat) {
                        // 아이별로 묶어서: 이름 (n일) 사유들
                        list.groupBy { it.studentId }.values.sortedByDescending { it.size }.forEach { kid ->
                            val dates = kid.sortedBy { it.day }.joinToString(", ") { "${it.day.substring(5, 7).toInt()}/${it.day.substring(8, 10).toInt()}" }
                            val reasons = kid.map { it.reason }.distinct().joinToString(" / ")
                            Text("${kid.first().name} ${kid.size}일 ($dates)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(reasons, color = Muted, fontSize = 13.sp, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }
        }
        if (perKid.isNotEmpty()) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(12.dp)) {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("이 달 결석이 많은 아이 TOP ${perKid.size}", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        perKid.forEach { (n, c) ->
                            Row { Text(n, modifier = Modifier.width(120.dp)); Text("${c}일", color = Muted) }
                        }
                    }
                }
            }
        }
        item { Text("글자 분류라서 사유에 '감기', '여행' 같은 단어가 있어야 맞게 분류돼요. 칸을 누르면 아이별 내용이 펼쳐져요.", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 16.dp)) }
    }
}
