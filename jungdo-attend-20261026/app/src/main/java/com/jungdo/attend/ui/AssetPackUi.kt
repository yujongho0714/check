package com.jungdo.attend.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jungdo.attend.data.AssetPack
import kotlinx.coroutines.launch

/** 계급 · 보석 · 네임플레이트 · UI 테마 그림을 zip 으로 올리는 곳 (관리자 설정 / 관장님 폰 설정 둘 다에 들어가요) */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AssetPackSettings() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }
    var confirmClear by remember { mutableStateOf(false) }

    val ver = AssetPack.version // 그림이 바뀌면 아래 값들을 다시 읽어요
    val stats = remember(ver) { AssetPack.stats(ctx) }
    val themes = remember(ver) { AssetPack.themes(ctx) }
    val active = remember(ver) { AssetPack.activeThemeId(ctx) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        busy = true; msg = "읽는 중…"
        scope.launch {
            msg = AssetPack.importAndPush(ctx, uris) { msg = it }
            busy = false
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle("그림 올리기 (계급 · 보석 · 네임플레이트 · UI)")
        Text(
            "그림 zip 파일을 고르면 이 앱에 적용되고, 같은 계정으로 로그인한 다른 기기(공기계)도 자동으로 받아가요. 한 번에 여러 개 골라도 돼요.",
            color = Muted, fontSize = 13.sp,
        )
        Text(
            if (stats.empty) "지금: 올린 그림 없음 (앱 기본)"
            else "지금: 계급 ${stats.rank}장 · 보석 ${stats.gem}장 · 네임플레이트 ${stats.plate}장 · 테마 ${stats.themes.size}개",
            fontSize = 14.sp,
        )

        Button(enabled = !busy, onClick = {
            picker.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream"))
        }) { Text("그림 zip 올리기") }

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(enabled = !busy, onClick = {
                busy = true; msg = "확인하는 중…"
                scope.launch { msg = AssetPack.syncNow(ctx) { msg = it }; busy = false }
            }) { Text("다른 기기와 맞추기") }
            OutlinedButton(enabled = !busy && !stats.empty, onClick = { confirmClear = true }) { Text("올린 그림 지우기", color = BadRed) }
        }

        if (themes.size > 1) {
            Text("UI 테마 고르기", fontSize = 14.sp)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                themes.forEach { t ->
                    val pick = { scope.launch { AssetPack.setActiveTheme(ctx, t) }; Unit }
                    if (t == active) Button(onClick = pick) { Text(t) }
                    else OutlinedButton(onClick = pick) { Text(t) }
                }
            }
        }

        if (msg.isNotBlank()) Text(msg, color = if (msg.startsWith("실패") || msg.contains("⚠️")) BadRed else OkGreen, fontSize = 14.sp)
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("올린 그림을 모두 지울까요?") },
            text = { Text("이 기기와 계정에 저장된 그림이 지워지고, 다른 기기도 앱 기본 그림으로 돌아가요.") },
            confirmButton = {
                Button(onClick = {
                    confirmClear = false; busy = true; msg = "지우는 중…"
                    scope.launch { msg = AssetPack.clearAll(ctx); busy = false }
                }) { Text("지우기") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("아니요") } },
        )
    }
}
