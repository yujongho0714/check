package com.jungdo.attend.server

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jungdo.attend.data.Repo
import com.jungdo.attend.data.Student
import com.jungdo.attend.report.Exporter
import com.jungdo.attend.report.MyInfo
import com.jungdo.attend.report.MyInfoReport
import com.jungdo.attend.ui.BadRed
import com.jungdo.attend.ui.Muted
import com.jungdo.attend.ui.Pill
import com.jungdo.attend.ui.SectionTitle
import kotlinx.coroutines.launch

/**
 * 내 정보 보내기 — 출결번호를 넣으면 그 원생의 계급·출석일수·받은 상품을 HTML 또는 이미지로 만들어
 * 문자/카톡 등으로 보냄 (공유 창에서 앱을 고르면 학부모 번호가 미리 채워져요)
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MyInfoTab(students: List<Student>) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var no by remember { mutableStateOf("") }
    var picked by remember { mutableStateOf<Student?>(null) }
    var info by remember { mutableStateOf<MyInfo?>(null) }
    var asImage by remember { mutableStateOf(true) }
    var err by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }

    val found = if (no.isBlank()) emptyList() else students.filter { it.matchesNo(no.trim()) }
    // 번호가 바뀌면 이전 결과는 지움. 한 명뿐이면 자동 선택 (형제는 같은 번호 → 고르기)
    LaunchedEffect(no, students) { info = null; err = ""; picked = found.singleOrNull() }

    // 고른 원생의 최신 정보 불러오기
    LaunchedEffect(picked?.id) {
        val s = picked ?: return@LaunchedEffect
        loading = true
        runCatching { MyInfoReport.load(s) }.onSuccess { info = it }.onFailure { err = "불러오지 못했어요: ${it.message}" }
        loading = false
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle("내 정보 보내기")
        Text("출결번호를 넣으면 그 원생의 계급·출석일수·받은 상품을 HTML 이나 이미지로 만들어 학부모님께 보낼 수 있어요.", color = Muted, fontSize = 13.sp)
        OutlinedTextField(no, { no = it.filter(Char::isDigit) }, label = { Text("출결번호") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())

        if (no.isNotBlank() && found.isEmpty()) Text("그 번호의 원생이 없어요.", color = BadRed)
        if (found.size > 1) {
            Text("같은 번호가 ${found.size}명이에요 (형제). 누구 정보를 보낼까요?", color = Muted)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                found.forEach { s -> Pill(s.name, picked?.id == s.id, 16.sp) { picked = s } }
            }
        }
        if (loading) Text("불러오는 중…", color = Muted)
        if (err.isNotBlank()) Text(err, color = BadRed)

        info?.let { i ->
            val s = i.student
            Text("${s.name} (${s.grade}) · Lv.${i.level} · 출석 ${i.totalDays}일 · 받은 상품 ${i.payouts.size}개", fontWeight = FontWeight.Bold)

            Text("보낼 모양", color = Muted, fontSize = 13.sp)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Pill("🖼 이미지 (문자·카톡에 바로 보임)", asImage, 15.sp) { asImage = true }
                Pill("📄 HTML 파일 (눌러서 열기)", !asImage, 15.sp) { asImage = false }
            }

            val bmp = remember(i, asImage) { if (asImage) MyInfoReport.bitmap(ctx, i) else null }
            bmp?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxWidth()) }
            if (!asImage) Text("HTML 은 받는 폰에서 파일을 눌러 브라우저로 열어야 해서, 안 열리는 폰이 있을 수 있어요. 문자로는 이미지가 더 안정적이에요.", color = Muted, fontSize = 13.sp)

            Row(verticalAlignment = Alignment.CenterVertically) {
                val phone = s.phones.firstOrNull().orEmpty()
                Button(onClick = {
                    scope.launch {
                        runCatching {
                            val base = "내정보_${s.name}_${i.madeDay}"
                            if (asImage) Exporter.share(ctx, MyInfoReport.png(bmp ?: MyInfoReport.bitmap(ctx, i)), "$base.png", Exporter.PNG, "내 정보 보내기", phone)
                            else Exporter.share(ctx, MyInfoReport.html(ctx, i).toByteArray(Charsets.UTF_8), "$base.html", Exporter.HTML, "내 정보 보내기", phone)
                        }.onFailure { err = "보내지 못했어요: ${it.message}" }
                    }
                }) { Text(if (asImage) "이미지 보내기" else "HTML 보내기") }
                Spacer(Modifier.width(8.dp))
                if (phone.isNotBlank()) Text("받는 분 ${Repo.prettyPhone(phone)}", color = Muted, fontSize = 13.sp)
            }
            Text("보내기를 누르면 앱 고르는 창이 떠요 → 문자(메시지) 나 카카오톡을 고르세요.", color = Muted, fontSize = 13.sp)
        }
    }
}
