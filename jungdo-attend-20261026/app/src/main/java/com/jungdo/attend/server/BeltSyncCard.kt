package com.jungdo.attend.server

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jungdo.attend.data.BeltSync
import com.jungdo.attend.data.Student
import com.jungdo.attend.ui.Muted
import com.jungdo.attend.ui.Navy
import com.jungdo.attend.ui.SectionTitle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 관장님 폰이 켜져 있는 동안 10분마다 통합관리 띠를 받아서 맞춤 (처음 켤 때 한 번 바로) */
@Composable
fun BeltAutoSync(students: List<Student>) {
    val ctx = LocalContext.current
    val cur by rememberUpdatedState(students)
    LaunchedEffect(Unit) {
        while (true) {
            if (cur.isNotEmpty() && BeltSync.pass(ctx).isNotBlank()) runCatching { BeltSync.pull(ctx, cur) }
            delay(if (cur.isEmpty()) 5_000L else 10 * 60_000L)
        }
    }
}

/** 명단 탭 맨 위: 통합관리 띠 연동 설정 */
@Composable
fun BeltSyncCard(students: List<Student>) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var pw by remember { mutableStateOf(BeltSync.pass(ctx)) }
    var busy by remember { mutableStateOf(false) }
    val st by BeltSync.status.collectAsState()
    val info = st.ifBlank { BeltSync.lastInfo(ctx) }
    val withBelt = students.count { it.belt.isNotBlank() && it.isActive }
    Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionTitle("🥋 통합관리 띠 연동")
            Text("통합관리에서 띠가 바뀌면 출결 앱에도 저절로 바뀌어요 (10분마다 + 지금 받기). 통합관리 [출결 앱 띠 보내기]에 넣은 비밀번호와 똑같이 넣어 주세요.",
                color = Muted, fontSize = 13.sp)
            OutlinedTextField(pw, { pw = it }, label = { Text("연동 비밀번호") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = !busy && pw.isNotBlank(), onClick = {
                    BeltSync.setPass(ctx, pw); busy = true
                    scope.launch { runCatching { BeltSync.pull(ctx, students, force = true) }; busy = false }
                }) { Text(if (busy) "받는 중…" else "저장하고 지금 받기") }
            }
            if (info.isNotBlank()) Text(info, color = Navy, fontSize = 13.sp)
            Text("지금 띠가 들어간 원생 ${withBelt}명", color = Muted, fontSize = 12.sp)
        }
    }
}
