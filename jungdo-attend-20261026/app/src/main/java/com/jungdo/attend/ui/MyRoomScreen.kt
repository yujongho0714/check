package com.jungdo.attend.ui

import android.annotation.SuppressLint
import android.net.Uri
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.webkit.WebViewAssetLoader
import com.jungdo.attend.data.AssetPack
import com.jungdo.attend.data.Mc
import com.jungdo.attend.data.MyRoom
import com.jungdo.attend.data.Repo
import com.jungdo.attend.data.Student
import org.json.JSONArray
import org.json.JSONObject

/**
 * 마이룸 창 (전체 화면). 화면은 assets/myroom/index.html (three.js 복셀 방) 을 앱 안 웹 화면으로 띄우고,
 * 저장·뽑기·판매는 아래 다리(AB)로 받아서 서버(Firestore)에 해요.
 *  - s = 원생 (null 이면 관리자 에셋 관리만)
 *  - admin = true 면 [🔧 관리자] 탭이 보여서 복셀 가구(.obj+.png)·캐릭터 스킨(.png)을 추가·삭제할 수 있어요
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MyRoomDialog(s: Student?, admin: Boolean = false, onClose: () -> Unit) {
    val ctx = LocalContext.current
    var web by remember { mutableStateOf<WebView?>(null) }
    var ready by remember { mutableStateOf(false) }
    val cur by rememberUpdatedState(s)
    val closeCb by rememberUpdatedState(onClose)
    // 파일 고르기 (관리자 에셋 추가)
    var fileCb by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        fileCb?.onReceiveValue(if (uri != null) arrayOf(uri) else null); fileCb = null
    }

    fun js(code: String) { web?.post { web?.evaluateJavascript(code, null) } }
    fun reply(o: JSONObject) = js("window.onBridge(" + JSONObject.quote(o.toString()) + ")")

    fun stateJson(): String {
        val st = cur
        val o = JSONObject()
        o.put("admin", admin)
        o.put("furPrice", MyRoom.FUR_PRICE); o.put("mcPrice", Mc.PRICE); o.put("mcSell", Mc.SELL_GOLD)
        o.put("custom", JSONArray(MyRoom.custom.map { it }))
        if (st != null) {
            o.put("name", st.name); o.put("gold", st.gold)
            o.put("mc", JSONArray(st.mcSkins)); o.put("mcSkin", st.mcSkin)
            o.put("room", MyRoom.parse(st.myroom))
            run {
                val items = st.pokeBag.mapNotNull { com.jungdo.attend.data.PokeGift.parse(it) }
                o.put("poke", JSONArray(items.filter { it.kind == "P" }.map { JSONObject().put("dex", PokeGift.baseDex(it.idx)).put("sh", it.sh).put("lv", it.lv).put("rar", it.rar).put("name", com.jungdo.attend.data.PokeGift.MONS.firstOrNull { m -> m.dex == it.idx }?.name ?: "") }))
                o.put("pfree", st.pokeFree).put("pP", com.jungdo.attend.data.PokeGift.PRICE_P).put("pM", com.jungdo.attend.data.PokeGift.PRICE_M)
                o.put("pmv", JSONArray(items.filter { it.kind == "M" }.mapNotNull { com.jungdo.attend.data.PokeGift.MOVES.getOrNull(it.idx) }.distinctBy { it.id }.map { JSONObject().put("id", it.id).put("name", it.name) }))
            }
            if (st.dressEquip.isNotEmpty()) o.put("dress", JSONObject(st.dressEquip as Map<*, *>)) else o.put("dress", JSONObject.NULL)
        } else {
            o.put("name", "관리자"); o.put("gold", 0); o.put("mc", JSONArray()); o.put("room", JSONObject())
        }
        return o.toString()
    }

    // 원생 정보(골드·가방 등)나 관리자 에셋이 바뀌면 화면에 다시 보내 줌
    LaunchedEffect(ready, s, MyRoom.custom) {
        if (ready) js("window.onState(" + JSONObject.quote(stateJson()) + ")")
    }

    val bridge = remember {
        object {
            @JavascriptInterface fun ready() { web?.post { ready = true; js("window.onState(" + JSONObject.quote(stateJson()) + ")") } }
            @JavascriptInterface fun close() { web?.post { closeCb() } }
            @JavascriptInterface fun saveRoom(layout: String) { cur?.id?.takeIf { it.isNotBlank() }?.let { Repo.saveRoom(it, layout) } }
            @JavascriptInterface fun setMcSkin(k: String) { cur?.id?.takeIf { it.isNotBlank() }?.let { Repo.setMcSkin(it, k) } }
            @JavascriptInterface fun pullFur(k: String, price: String) {
                val id = cur?.id ?: return
                Repo.pullFur(id, k, price.toIntOrNull() ?: MyRoom.FUR_PRICE) { ok, err -> reply(JSONObject().put("op", "pullFur").put("ok", ok).put("k", k).put("err", err ?: "")) }
            }
            @JavascriptInterface fun sellFur(itemId: String, gold: String) {
                val id = cur?.id ?: return
                val g = gold.toIntOrNull() ?: 1
                Repo.sellFur(id, itemId.toIntOrNull() ?: -1, g) { ok -> reply(JSONObject().put("op", "sellFur").put("ok", ok).put("gold", g).put("err", if (ok) "" else "팔지 못했어요")) }
            }
            @JavascriptInterface fun pokePull(kind: String) {
                val id = cur?.id ?: return
                com.jungdo.attend.data.PokeGift.pull(id, kind) { r, err ->
                    val o = JSONObject().put("op", "pokePull").put("ok", r != null).put("err", err ?: "")
                    if (r != null) {
                        val nm = if (r.kind == "P") com.jungdo.attend.data.PokeGift.MONS.firstOrNull { it.dex == r.idx }?.name ?: "포켓몬" else com.jungdo.attend.data.PokeGift.MOVES.getOrNull(r.idx)?.name ?: "기술"
                        o.put("k", r.kind).put("idx", r.idx).put("rar", r.rar).put("sh", r.sh).put("lv", r.lv).put("name", nm)
                    }
                    web?.post { reply(o) }
                }
            }
            @JavascriptInterface fun pullMc() {
                val id = cur?.id ?: return
                Repo.pullMc(id, 1, Mc.PRICE, Mc.refund) { r, err ->
                    val k = r?.keys?.firstOrNull()
                    reply(JSONObject().put("op", "pullMc").put("ok", k != null).put("k", k ?: "").put("dup", (r?.dupCount ?: 0) > 0).put("err", err ?: ""))
                }
            }
            @JavascriptInterface fun sellMc(k: String) {
                val id = cur?.id ?: return
                Repo.sellMc(id, k) { g -> reply(JSONObject().put("op", "sellMc").put("ok", g > 0).put("gold", g).put("err", if (g > 0) "" else "팔지 못했어요 (조종 중이면 못 팔아요)")) }
            }
            @JavascriptInterface fun assetData(k: String) {
                Repo.myroomAssetData(k) { g, b -> reply(JSONObject().put("op", "asset").put("k", k).put("ok", b != null).put("g", g ?: "").put("b", b ?: "")) }
            }
            @JavascriptInterface fun addAsset(json: String) {
                if (!admin) return
                Repo.addMyRoomAsset(json) { ok -> reply(JSONObject().put("op", "addAsset").put("ok", ok).put("err", if (ok) "" else "저장하지 못했어요 (너무 크면 안 돼요)")) }
            }
            @JavascriptInterface fun delAsset(k: String) {
                if (!admin) return
                Repo.delMyRoomAsset(k) { ok -> reply(JSONObject().put("op", "delAsset").put("ok", ok).put("err", if (ok) "" else "지우지 못했어요")) }
            }
        }
    }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val conf = androidx.compose.ui.platform.LocalConfiguration.current
        AndroidView(
            modifier = Modifier.size(conf.screenWidthDp.dp, conf.screenHeightDp.dp),
            factory = { c ->
                // https://appassets.androidplatform.net/assets/… = 앱 안 그림, /pack/… = 관리자가 올린 그림 묶음(옷 입히기 등)
                val loader = WebViewAssetLoader.Builder()
                    .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(c))
                    .addPathHandler("/pack/") { path ->
                        runCatching {
                            val mime = when {
                                path.endsWith(".json") -> "application/json"
                                path.endsWith(".webp") -> "image/webp"
                                path.endsWith(".png") -> "image/png"
                                else -> "application/octet-stream"
                            }
                            WebResourceResponse(mime, null, AssetPack.open(c, path))
                        }.getOrNull()
                    }
                    .build()
                WebView(c).apply {
                    layoutParams = android.view.ViewGroup.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT)
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = false
                    settings.mediaPlaybackRequiresUserGesture = false
                    webViewClient = object : WebViewClient() {
                        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                            loader.shouldInterceptRequest(request.url)
                    }
                    webChromeClient = object : WebChromeClient() {
                        override fun onShowFileChooser(v: WebView?, cb: ValueCallback<Array<Uri>>?, p: FileChooserParams?): Boolean {
                            fileCb?.onReceiveValue(null)
                            fileCb = cb
                            runCatching { picker.launch("*/*") }.onFailure { fileCb?.onReceiveValue(null); fileCb = null }
                            return true
                        }
                    }
                    addJavascriptInterface(bridge, "AB")
                    loadUrl("https://appassets.androidplatform.net/assets/myroom/index.html")
                    web = this
                }
            },
            onRelease = { it.destroy() },
        )
    }
}
