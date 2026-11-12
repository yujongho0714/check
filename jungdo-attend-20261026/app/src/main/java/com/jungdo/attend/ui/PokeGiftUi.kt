package com.jungdo.attend.ui

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebViewClient
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jungdo.attend.data.PokeGift
import com.jungdo.attend.data.Student
import com.jungdo.attend.ui.game.GameButton
import com.jungdo.attend.ui.game.LocalGameTheme
import com.jungdo.attend.ui.game.PixelText
import com.jungdo.attend.ui.game.gameMuted

/**
 * 보관함의 "⚡ 포켓몬" 탭: 포켓몬 어드벤처 출석 뽑기
 *  - 포켓몬 뽑기 / 기술 뽑기 (처음 한 번씩 무료) → 가방에 들어감
 *  - 가방에서 [사용] → 본인 휴대폰 번호 입력 → 그 번호로 선물 코드 + 게임 주소 문자가 바로 감
 *  - 실수로 닫혀도 가방에 남아 있고, 보낸 코드는 계속 보이며 [다시 보내기] 가능
 */
@Composable
fun PokeGiftPanel(s: Student, modifier: Modifier = Modifier) {
    val theme = LocalGameTheme.current
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }
    var last by remember { mutableStateOf<PokeGift.Item?>(null) }
    var sendRaw by remember { mutableStateOf<String?>(null) }
    var anim by remember { mutableStateOf<PokeGift.Item?>(null) }
    val freeP = !s.pokeFree.contains("P")
    val freeM = !s.pokeFree.contains("M")
    val bag = s.pokeBag.mapNotNull { r -> PokeGift.parse(r)?.let { r to it } }.sortedWith(compareBy({ it.second.used }, { -it.second.rar }))

    fun pull(kind: String) {
        if (busy) return
        busy = true; msg = ""
        PokeGift.pull(s.id, kind) { r, err -> busy = false; last = r; if (err != null) msg = err else if (r != null) anim = r }
    }

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        PixelText("💰 ${s.gold} 골드 · 포켓몬 어드벤처 뽑기", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text("뽑은 건 아래 가방에 들어가요. [사용]을 누르고 내 휴대폰 번호를 넣으면 게임 선물 코드가 문자로 와요.", color = gameMuted(), fontSize = 13.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GameButton(if (freeP) "⚡ 포켓몬 뽑기\n(첫 1회 무료)" else "⚡ 포켓몬 뽑기\n${PokeGift.PRICE_P}골드", onClick = { pull("P") },
                enabled = !busy && (freeP || s.gold >= PokeGift.PRICE_P), modifier = Modifier.weight(1f).height(72.dp), color = theme.primary, fontSize = 19.sp, sound = null)
            GameButton(if (freeM) "📀 기술 뽑기\n(첫 1회 무료)" else "📀 기술 뽑기\n${PokeGift.PRICE_M}골드", onClick = { pull("M") },
                enabled = !busy && (freeM || s.gold >= PokeGift.PRICE_M), modifier = Modifier.weight(1f).height(72.dp), color = theme.secondary, fontSize = 19.sp, sound = null)
        }
        if (busy) Text("뽑는 중…", color = gameMuted(), fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp))
        if (msg.isNotBlank()) Text(msg, color = BadRed, fontSize = 15.sp, modifier = Modifier.padding(top = 6.dp))
        last?.let { it ->
            Text("🎉 [${PokeGift.RAR[it.rar]}] ${it.label}", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(PokeGift.RARC[it.rar]), modifier = Modifier.padding(top = 6.dp))
            Text("가방에 들어갔어요! 아래에서 [사용]을 눌러 내 폰으로 받으세요.", color = gameMuted(), fontSize = 13.sp)
        }
        Spacer(Modifier.height(8.dp))
        Text("🎒 포켓몬 가방 ${bag.size}개", fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.fillMaxWidth())
        LazyColumn(Modifier.weight(1f, fill = true).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(bag, key = { it.second.id }) { (raw, it) ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (it.used) Color(0x14808080) else Color(0x2222C55E)).padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("[${PokeGift.RAR[it.rar]}] ${it.label}", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color(PokeGift.RARC[it.rar]))
                        if (it.used) Text("코드 ${it.code} · ${mask(it.sentTo)}(으)로 보냄", fontSize = 13.sp, color = gameMuted())
                        else Text("아직 안 받았어요", fontSize = 13.sp, color = gameMuted())
                    }
                    GameButton(if (it.used) "다시 보내기" else "사용", onClick = { sendRaw = raw }, modifier = Modifier.height(52.dp), color = if (it.used) theme.secondary else theme.primary, fontSize = 17.sp)
                }
            }
        }
    }

    anim?.let { a -> PackOpenDialog(a) { anim = null } }

    sendRaw?.let { raw ->
        var phone by remember(raw) { mutableStateOf(PokeGift.parse(raw)?.sentTo ?: "") }
        var err by remember(raw) { mutableStateOf("") }
        var sending by remember(raw) { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { if (!sending) sendRaw = null },
            title = { Text("내 휴대폰 번호", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("내 폰 번호를 써 주세요. (부모님 번호 말고 내 번호!)\n게임에서도 같은 번호를 넣어야 선물을 받을 수 있어요.", fontSize = 15.sp)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = phone, onValueChange = { v -> phone = v.filter { c -> c.isDigit() }.take(11) }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), placeholder = { Text("01012345678") })
                    if (err.isNotBlank()) Text(err, color = BadRed, fontSize = 14.sp)
                    if (sending) Text("보내는 중…", color = gameMuted(), fontSize = 14.sp)
                }
            },
            confirmButton = {
                TextButton(enabled = !sending, onClick = {
                    sending = true; err = ""
                    PokeGift.send(s.id, s.name, raw, phone) { code, e ->
                        sending = false
                        if (e != null) err = e else { msg = "📩 ${mask(PokeGift.norm(phone))}(으)로 보냈어요! 코드 $code"; sendRaw = null }
                    }
                }) { Text("문자로 받기", fontSize = 17.sp, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(enabled = !sending, onClick = { sendRaw = null }) { Text("닫기") } },
        )
    }
}

private fun mask(n: String) = if (n.length >= 10) n.take(3) + "-****-" + n.takeLast(4) else n

/**
 * 뽑기 직후 카드팩 뜯기 연출 (게임 사이트의 pack.html을 그대로 사용 — 게임과 똑같은 연출)
 * 인터넷이 안 되거나 페이지가 안 뜨면(화면이 까맣게 멈추는 문제) 앱 안의 간단한 연출로 바로 바꿔서 보여줘요.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun PackOpenDialog(item: PokeGift.Item, onDone: () -> Unit) {
    val url = "https://${PokeGift.GAME_URL}/pack.html?k=${item.kind}&id=${item.idx}&r=${item.rar}&sh=${item.sh}&lv=${item.lv}&back=1"
    var failed by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onDone, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)) {
        Box(Modifier.fillMaxSize().background(Color(0xFF05050C))) {
            if (failed) {
                NativePackReveal(item, onDone)
            } else {
                AndroidView(modifier = Modifier.fillMaxSize(), factory = { ctx ->
                    WebView(ctx).apply {
                        var finished = false
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.mediaPlaybackRequiresUserGesture = false
                        setBackgroundColor(0xFF05050C.toInt())
                        webViewClient = object : WebViewClient() {
                            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                                if (request.isForMainFrame && !finished) failed = true
                            }
                            override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, errorResponse: WebResourceResponse) {
                                if (request.isForMainFrame && !finished) failed = true
                            }
                            override fun onPageFinished(view: WebView, u: String?) {
                                // 페이지는 열렸는데 카드팩 화면(canvas)이 안 그려졌으면 → 앱 연출로 교체
                                view.postDelayed({
                                    if (finished) return@postDelayed
                                    view.evaluateJavascript("(function(){return document.querySelectorAll('canvas').length})()") { r ->
                                        if (!finished && r?.trim('"') == "0") failed = true
                                    }
                                }, 3000)
                            }
                        }
                        addJavascriptInterface(object { @JavascriptInterface fun packDone() { post { finished = true; onDone() } } }, "Android")
                        loadUrl(url)
                    }
                })
            }
            TextButton(onClick = onDone, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) { Text("✕ 닫기", color = Color.White, fontSize = 18.sp) }
        }
    }
}

/** 인터넷 없이도 보이는 앱 안 카드팩 연출: 팩이 흔들림 → 눌러서 뜯기 → 카드가 튀어나옴 → 눌러서 닫기 */
@Composable
private fun NativePackReveal(item: PokeGift.Item, onDone: () -> Unit) {
    var opened by remember { mutableStateOf(false) }
    val inf = rememberInfiniteTransition()
    val wob by inf.animateFloat(-7f, 7f, infiniteRepeatable(tween(240, easing = LinearEasing), RepeatMode.Reverse))
    val sc = remember { Animatable(0.15f) }
    LaunchedEffect(opened) { if (opened) sc.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 170f)) }
    val col = Color(PokeGift.RARC[item.rar])
    Box(
        Modifier.fillMaxSize().clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { if (!opened) opened = true else onDone() },
        contentAlignment = Alignment.Center,
    ) {
        if (!opened) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("🎁", fontSize = 140.sp, modifier = Modifier.graphicsLayer { rotationZ = wob })
                Text("눌러서 뜯기!", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
        } else {
            Column(
                Modifier.graphicsLayer { scaleX = sc.value; scaleY = sc.value }
                    .clip(RoundedCornerShape(24.dp)).background(Color(0xFF14142A)).border(4.dp, col, RoundedCornerShape(24.dp)).padding(horizontal = 40.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("★".repeat(item.rar + 1), color = col, fontSize = 30.sp)
                Text(if (item.kind == "P") (if (item.sh == 1) "✨" else "⚡") else "📀", fontSize = 100.sp)
                Text(item.label, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Text("[${PokeGift.RAR[item.rar]}]", color = col, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                Text("가방에 들어갔어요!\n화면을 누르면 닫혀요", color = Color(0xFFCCCCDD), fontSize = 16.sp, textAlign = TextAlign.Center)
            }
        }
    }
}
