package com.jungdo.attend.ui

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewAssetLoader
import com.jungdo.attend.data.MyRoom
import com.jungdo.attend.data.Student

/** 학생이 장착한 마크 캐릭터 키 (없으면 가진 것 중 첫째, 그것도 없으면 "") */
fun mcKeyOf(s: Student): String = s.mcSkin.ifBlank { s.mcSkins.firstOrNull() ?: "" }

/** 관리자(PC)가 올린 스킨이면 data URL, 아니면 "" */
fun customSkinUrl(k: String): String = MyRoom.custom.firstOrNull { it.optString("type") == "skin" && it.optString("k") == k }?.optString("b") ?: ""

/** 3D 마크 복셀 캐릭터 (손가락으로 돌리기 · 누르면 동작). assets/myroom/char.html 을 띄움 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun McChar3D(key: String, modifier: Modifier = Modifier, onTouch: () -> Unit = {}) {
    val touch by rememberUpdatedState(onTouch)
    val bridge = remember {
        object {
            @JavascriptInterface fun skin(k: String): String = customSkinUrl(k)
            @JavascriptInterface fun touch() { touch() }
        }
    }
    AndroidView(
        modifier = modifier,
        factory = { c ->
            val loader = WebViewAssetLoader.Builder().addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(c)).build()
            WebView(c).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                settings.javaScriptEnabled = true
                settings.allowFileAccess = false
                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? = loader.shouldInterceptRequest(request.url)
                }
                addJavascriptInterface(bridge, "AB")
                tag = key
                loadUrl("https://appassets.androidplatform.net/assets/myroom/char.html?k=" + android.net.Uri.encode(key))
            }
        },
        update = { w -> if (w.tag != key) { w.tag = key; w.loadUrl("https://appassets.androidplatform.net/assets/myroom/char.html?k=" + android.net.Uri.encode(key)) } },
        onRelease = { it.destroy() },
    )
}

/** 가진 마크 캐릭터 얼굴 줄 (누르면 그걸로 장착) */
@Composable
fun McSkinPicker(s: Student, size: Dp = 40.dp, onPick: (String) -> Unit) {
    val ctx = LocalContext.current
    val cur = mcKeyOf(s)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        s.mcSkins.forEach { k ->
            val face: ImageBitmap? = remember(k) { mcFace(ctx, k) }
            Box(
                Modifier.size(size).clip(RoundedCornerShape(8.dp)).background(Color(0x22000000))
                    .border(if (k == cur) 3.dp else 1.dp, if (k == cur) Color(0xFF22C55E) else Color(0x55000000), RoundedCornerShape(8.dp))
                    .clickable { onPick(k) }.padding(4.dp)
            ) { face?.let { Image(it, null, contentScale = ContentScale.Fit, filterQuality = FilterQuality.None) } }
        }
    }
}

/** 스킨 얼굴(앞머리 8×8 + 겉머리) 그림 */
fun mcFace(ctx: android.content.Context, k: String): ImageBitmap? = runCatching {
    val src: android.graphics.Bitmap = customSkinUrl(k).let { u ->
        if (u.startsWith("data:")) { val b = android.util.Base64.decode(u.substringAfter(","), android.util.Base64.DEFAULT); android.graphics.BitmapFactory.decodeByteArray(b, 0, b.size) }
        else ctx.assets.open("mc/$k.png").use { android.graphics.BitmapFactory.decodeStream(it) }
    }
    val out = android.graphics.Bitmap.createBitmap(8, 8, android.graphics.Bitmap.Config.ARGB_8888)
    val cv = android.graphics.Canvas(out)
    val sc = src.width / 64
    cv.drawBitmap(src, android.graphics.Rect(8 * sc, 8 * sc, 16 * sc, 16 * sc), android.graphics.Rect(0, 0, 8, 8), null)
    cv.drawBitmap(src, android.graphics.Rect(40 * sc, 8 * sc, 48 * sc, 16 * sc), android.graphics.Rect(0, 0, 8, 8), null)
    out.asImageBitmap()
}.getOrNull()
