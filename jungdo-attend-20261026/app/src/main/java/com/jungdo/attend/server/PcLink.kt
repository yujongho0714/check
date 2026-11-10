package com.jungdo.attend.server

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.FirebaseApp
import com.jungdo.attend.ui.Muted
import com.jungdo.attend.ui.SectionTitle

/**
 * PC 관리 화면 연결 코드
 * PC(yujongho0714.github.io/check/)에서 이 코드 + 도장 계정으로 로그인하면 같은 출결 데이터를 봐요.
 * 코드에는 비밀이 아닌 Firebase 프로젝트 주소(프로젝트 ID·키)만 들어 있고, 실제 데이터는 도장 계정 로그인이 있어야 열려요.
 */
object PcLink {
    fun code(): String {
        val o = FirebaseApp.getInstance().options
        return "JD1-" + android.util.Base64.encodeToString(
            "${o.projectId}|${o.apiKey}|${o.storageBucket ?: ""}".toByteArray(),
            android.util.Base64.NO_WRAP or android.util.Base64.URL_SAFE,
        )
    }
}

@Composable
fun PcLinkSettings() {
    val ctx = LocalContext.current
    val code = runCatching { PcLink.code() }.getOrDefault("")
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle("💻 PC에서 관리하기")
        Text("PC 크롬에서 yujongho0714.github.io/check 를 열고, 아래 연결 코드와 도장 계정(이 폰 로그인과 같은 이메일·비밀번호)을 넣으면 PC·문자 폰·키오스크가 모두 같은 내용으로 맞춰져요. 연결 코드는 처음 한 번만 넣으면 돼요.",
            color = Muted, fontSize = 13.sp)
        SelectionContainer { Text(code, fontFamily = FontFamily.Monospace, fontSize = 12.sp) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("PC 연결 코드", code))
                Toast.makeText(ctx, "연결 코드를 복사했어요", Toast.LENGTH_SHORT).show()
            }) { Text("코드 복사") }
            OutlinedButton(onClick = {
                val i = Intent(Intent.ACTION_SEND).setType("text/plain")
                    .putExtra(Intent.EXTRA_TEXT, "정도 출결 PC 연결 코드\n$code\n\nhttps://yujongho0714.github.io/check/")
                ctx.startActivity(Intent.createChooser(i, "PC로 보내기 (카톡 나와의 채팅 등)"))
            }) { Text("카톡으로 PC에 보내기") }
        }
    }
}
