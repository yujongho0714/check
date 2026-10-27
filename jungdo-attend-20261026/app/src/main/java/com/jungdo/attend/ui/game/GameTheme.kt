package com.jungdo.attend.ui.game

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.jungdo.attend.data.Prefs

/**
 * 앱 전체 게임 테마 (색 + 버튼 모양). 화면 쪽은 LocalGameTheme.current 로 읽어 써요.
 * 테마를 하나 추가하려면 아래 GameThemes 에 한 덩어리만 더하면 돼요. (STEP 2부터 채워 넣을 자리)
 */
data class GameTheme(
    val id: String,
    val name: String,
    val primary: Color,      // 기본 버튼 / 등원 쪽
    val secondary: Color,    // 하원 쪽
    val good: Color,
    val bad: Color,
    val warn: Color,
    val page: Color,         // 화면 배경
    val card: Color,         // 카드/창 배경
    val text: Color,
    val textOnColor: Color,  // 색 버튼 위 글자
    val buttonRadius: Int,   // 버튼 모서리(dp)
    val buttonDepth: Int,    // 버튼 아래 "두께"(dp). 0 이면 납작
    val pressScale: Float,   // 눌렀을 때 크기 (0.94 = 6% 작아짐)
    val skin: String? = null,                 // 그림 틀을 쓰는 테마면 assets/game/<skin>/ 폴더 이름
    val bar: Color = Color(0xFF1E3A8A),       // 상단 바
    val title: Color = Color(0xFF1E3A8A),     // 큰 제목·숫자 글자색
    val keyColor: Color = Color(0xFF2563EB),  // 번호 패드 숫자 키
    val keyAltColor: Color = Color(0xFF6B7280), // 지움·확인 키
    val doneEmoji: String = "✅",
)

object GameThemes {
    /** 지금 앱과 똑같은 모양(색은 ui/Common.kt 와 동일). 눌림 효과만 더해져요 */
    val Classic = GameTheme(
        id = "classic", name = "기본",
        primary = Color(0xFF2563EB), secondary = Color(0xFFEA580C),
        good = Color(0xFF16A34A), bad = Color(0xFFDC2626), warn = Color(0xFFCA8A04),
        page = Color(0xFFF3F4F6), card = Color.White,
        text = Color(0xFF111827), textOnColor = Color.White,
        buttonRadius = 14, buttonDepth = 4, pressScale = 0.95f,
    )

    /** 새싹 농장 (Sprout Lands): 크림색 나무 버튼 + 초록 들판 */
    val Sprout = GameTheme(
        id = "sprout", name = "새싹 농장",
        primary = Color(0xFFA6DB6F), secondary = Color(0xFFFFB566),
        good = Color(0xFF8FD14F), bad = Color(0xFFE98B7B), warn = Color(0xFFFFD166),
        page = Color(0xFF86C06C), card = Color(0xFFF3E5C2),
        text = Color(0xFF5B3A1E), textOnColor = Color(0xFF5B3A1E),
        buttonRadius = 8, buttonDepth = 4, pressScale = 0.97f,
        skin = "sprout",
        bar = Color(0xFF8A5A33), title = Color(0xFF5B3A1E),
        keyColor = Color.White, keyAltColor = Color(0xFFDDBB8A),
        doneEmoji = "🌱",
    )

    val all: List<GameTheme> = listOf(Sprout, Classic)

    fun byId(id: String): GameTheme = all.firstOrNull { it.id == id } ?: Classic
}

val LocalGameTheme = staticCompositionLocalOf { GameThemes.Classic }

/** 설정에서 테마를 바꾸면 앱을 다시 켜지 않아도 바로 바뀌게 하는 스위치 */
object GameThemeSwitch {
    var override: String? by mutableStateOf(null)
}

/** 앱 맨 위에서 한 번 감싸 주면, 안쪽 모든 화면이 같은 테마를 써요 */
@Composable
fun ProvideGameTheme(content: @Composable () -> Unit) {
    val ctx: Context = LocalContext.current
    val saved = remember { Prefs(ctx).gameTheme }
    val theme = GameThemes.byId(GameThemeSwitch.override ?: saved)
    CompositionLocalProvider(LocalGameTheme provides theme, content = content)
}
