package com.jungdo.attend.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jungdo.attend.data.Prefs

/** 이 기기를 무엇으로 쓸지 고르기 (처음 한 번만) */
@Composable
fun ModeScreen(onPick: (String) -> Unit) {
    Column(
        Modifier.fillMaxSize().background(PageBg).padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("이 기기를 어떻게 쓸까요?", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(24.dp))
        ModeCard("🧒  출석 단말기 (기기 A)", "유심 없는 공기계/태블릿.\n도장 벽에 두고 아이들이 이름을 눌러 등원·하원.", InBlue) {
            onPick(Prefs.MODE_KIOSK)
        }
        Spacer(Modifier.height(16.dp))
        ModeCard("📱  문자 발송 폰 (기기 B)", "유심 있는 관장님 폰.\n출석 신호를 받아 학부모님께 문자를 자동 발송.", OutOrange) {
            onPick(Prefs.MODE_SERVER)
        }
    }
}

@Composable
private fun ModeCard(title: String, desc: String, color: Color, onClick: () -> Unit) {
    Column(
        Modifier.widthIn(max = 480.dp).fillMaxWidth()
            .clip(RoundedCornerShape(20.dp)).background(Color.White)
            .clickable(onClick = onClick).padding(24.dp),
    ) {
        Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = color)
        Spacer(Modifier.height(8.dp))
        Text(desc, fontSize = 16.sp, color = Muted)
    }
}
