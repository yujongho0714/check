package com.jungdo.attend.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth

/** 두 기기 모두 같은 '도장 계정'으로 로그인 (Firebase Authentication 에서 만든 계정) */
@Composable
fun LoginScreen(onDone: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var pw by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.widthIn(max = 420.dp)) {
            Text("정도 출결", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Navy)
            Text("도장 계정으로 로그인하세요.\n키오스크와 관장님 폰 모두 같은 계정을 씁니다.", color = Muted)
            Spacer(Modifier.height(24.dp))
            OutlinedTextField(
                email, { email = it }, label = { Text("이메일") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                pw, { pw = it }, label = { Text("비밀번호") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(20.dp))
            Button(
                enabled = !busy && email.isNotBlank() && pw.isNotBlank(),
                onClick = {
                    busy = true; err = null
                    FirebaseAuth.getInstance().signInWithEmailAndPassword(email.trim(), pw)
                        .addOnSuccessListener { onDone() }
                        .addOnFailureListener { busy = false; err = "로그인 실패: ${it.message}" }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text(if (busy) "로그인 중…" else "로그인", fontSize = 18.sp) }
            err?.let { Spacer(Modifier.height(12.dp)); Text(it, color = BadRed) }
        }
    }
}
