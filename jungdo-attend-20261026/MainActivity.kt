package com.jungdo.attend

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.google.firebase.auth.FirebaseAuth
import com.jungdo.attend.data.AssetPack
import com.jungdo.attend.data.Prefs
import com.jungdo.attend.kiosk.KioskScreen
import com.jungdo.attend.server.ServerScreen
import com.jungdo.attend.ui.LoginScreen
import com.jungdo.attend.ui.ModeScreen
import com.jungdo.attend.ui.Navy
import com.jungdo.attend.update.UpdatePrompt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Navy)) {
                Surface(Modifier.fillMaxSize()) { AppRoot() }
            }
        }
    }
}

/**
 * 화면 흐름: 로그인 → (처음 한 번) 모드 선택 → 키오스크 화면(기기 A) 또는 문자 서버 화면(기기 B)
 */
@Composable
fun AppRoot() {
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }
    var loggedIn by remember { mutableStateOf(FirebaseAuth.getInstance().currentUser != null) }
    var mode by remember { mutableStateOf(prefs.mode) }

    // 모드 변경 / 로그아웃 후 처음 화면으로
    val reset: () -> Unit = {
        prefs.mode = null
        mode = null
        loggedIn = FirebaseAuth.getInstance().currentUser != null
    }

    // 로그인하면 계정에 올라온 그림(계급·보석·UI)을 지켜보다가 새로 올라오면 자동으로 받아요
    DisposableEffect(loggedIn) {
        val reg = if (loggedIn) AssetPack.startSync(ctx) else null
        onDispose { reg?.remove() }
    }

    UpdatePrompt() // 앱을 켜면 새 버전 확인 → "업데이트할까요?"

    when {
        !loggedIn -> LoginScreen(onDone = { loggedIn = true })
        mode == null -> ModeScreen(onPick = { m -> prefs.mode = m; mode = m })
        mode == Prefs.MODE_KIOSK -> KioskScreen(onReset = reset)
        else -> ServerScreen(onReset = reset)
    }
}
