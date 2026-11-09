package com.jungdo.attend.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.jungdo.attend.data.Belts

/** 띠 그림(작은 막대) + 띠 이름. 띠가 없으면 아무것도 안 그림 */
@Composable
fun BeltTag(belt: String, fontSize: TextUnit = 13.sp, color: Color = Color(0xFF374151), barWidth: Int = 22) {
    if (belt.isBlank()) return
    val c = Belts.colors(belt)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (c != null) {
            Column(
                Modifier.width(barWidth.dp).clip(RoundedCornerShape(3.dp))
                    .border(1.dp, Color(0x33000000), RoundedCornerShape(3.dp)),
            ) {
                Box(Modifier.fillMaxWidth().height(3.dp).background(Color(c.first)))
                Box(Modifier.fillMaxWidth().height(4.dp).background(Color(c.second)))
                Box(Modifier.fillMaxWidth().height(3.dp).background(Color(c.first)))
            }
        }
        Text(Belts.norm(belt), fontSize = fontSize, color = color, fontWeight = FontWeight.Bold)
    }
}

/** 키오스크용 큰 띠 표시: "🥋 내 띠  [막대] 1품 5급" (+ 줄넘기 급) — 아이들이 출석할 때 자기 띠를 확인 */
@Composable
fun KidBeltBadge(belt: String, jump: String = "", big: Boolean = true) {
    if (belt.isBlank() && jump.isBlank()) return
    val fs = if (big) 24.sp else 19.sp
    Row(
        Modifier.clip(RoundedCornerShape(14.dp)).background(Color(0xF2FFFFFF))
            .border(2.dp, Color(0x22000000), RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (belt.isNotBlank()) {
            Text("🥋 내 띠", fontSize = fs, color = Color(0xFF6B7280), fontWeight = FontWeight.Bold)
            BeltTag(belt, fontSize = fs, color = Color(0xFF111827), barWidth = if (big) 46 else 36)
        }
        if (jump.isNotBlank()) Text("  🪢 줄넘기 $jump", fontSize = fs, color = Color(0xFF1D4ED8), fontWeight = FontWeight.Bold)
    }
}
