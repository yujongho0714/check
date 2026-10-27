package com.jungdo.attend.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jungdo.attend.data.Status

val Navy = Color(0xFF1E3A8A)
val InBlue = Color(0xFF2563EB)
val OutOrange = Color(0xFFEA580C)
val OkGreen = Color(0xFF16A34A)
val BadRed = Color(0xFFDC2626)
val Muted = Color(0xFF6B7280)
val PageBg = Color(0xFFF3F4F6)

fun statusColor(s: String): Color = when (s) {
    Status.SENT -> OkGreen
    Status.FAILED -> BadRed
    Status.EXPIRED -> Muted
    else -> Color(0xFFCA8A04) // 대기/발송중: 노랑
}

/** 둥근 선택 버튼 (부 선택, 초성 선택 등) */
@Composable
fun Pill(text: String, selected: Boolean, fontSize: TextUnit = 18.sp, onClick: () -> Unit) {
    Text(
        text,
        fontSize = fontSize,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        color = if (selected) Color.White else Color(0xFF111827),
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) Navy else Color.White)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

/** 상태 표시 작은 딱지 */
@Composable
fun StatusChip(status: String) {
    Text(
        Status.label(status),
        color = Color.White,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(statusColor(status))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

fun agoText(lastSeen: Long?, now: Long): String {
    if (lastSeen == null) return "기록 없음"
    val sec = ((now - lastSeen) / 1000).coerceAtLeast(0)
    return when {
        sec < 60 -> "${sec}초 전"
        sec < 3600 -> "${sec / 60}분 전"
        sec < 86400 -> "${sec / 3600}시간 전"
        else -> "${sec / 86400}일 전"
    }
}

/** 생존 신호가 3분 안에 왔으면 연결된 것으로 봄 (1분마다 보냄) */
fun isOnline(lastSeen: Long?, now: Long) = lastSeen != null && now - lastSeen < 3 * 60_000
