package com.jungdo.attend.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import com.jungdo.attend.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL



data class UpdateInfo(val version: String, val apkUrl: String, val notes: String)

object Updater {
    private val REPO = BuildConfig.GITHUB_REPO

    /** 지금 설치된 앱의 빌드 번호 */
    private fun currentCode(ctx: Context): Long =
        PackageInfoCompat.getLongVersionCode(ctx.packageManager.getPackageInfo(ctx.packageName, 0))

    /** 새 버전이 있으면 정보, 없거나 실패(인터넷 없음 등)면 null */
    suspend fun check(ctx: Context): UpdateInfo? = withContext(Dispatchers.IO) {
        if (REPO.isBlank()) return@withContext null
        runCatching {
            val c = URL("https://api.github.com/repos/$REPO/releases/latest").openConnection() as HttpURLConnection
            c.connectTimeout = 8000; c.readTimeout = 8000
            c.setRequestProperty("Accept", "application/vnd.github+json")
            val j = JSONObject(c.inputStream.bufferedReader().readText())
            val tag = j.getString("tag_name")            // 예: build-12
            val code = tag.filter { it.isDigit() }.toLongOrNull() ?: 0L
            val assets = j.getJSONArray("assets")
            var apk: String? = null
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.getString("name").endsWith(".apk")) { apk = a.getString("browser_download_url"); break }
            }
            if (apk != null && code > currentCode(ctx)) UpdateInfo("1.0.$code", apk, j.optString("body")) else null
        }.getOrNull()
    }

    suspend fun download(ctx: Context, url: String, onProgress: (Int) -> Unit): File? = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(ctx.cacheDir, "updates").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
            val out = File(dir, "update.apk")
            val c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 10000; c.readTimeout = 20000
            val total = c.contentLength.toLong()
            c.inputStream.use { ins -> out.outputStream().use { os ->
                val buf = ByteArray(32 * 1024); var done = 0L; var n: Int; var last = -1
                while (ins.read(buf).also { n = it } > 0) {
                    os.write(buf, 0, n); done += n
                    if (total > 0) { val p = (done * 100 / total).toInt(); if (p != last) { last = p; onProgress(p) } }
                }
            } }
            out
        }.getOrNull()
    }

    /** 설치 화면 띄우기. '출처를 알 수 없는 앱 설치' 허용이 없으면 설정 화면부터 열어줌 → true: 설치창 열림 */
    fun install(ctx: Context, apk: File): Boolean {
        if (!ctx.packageManager.canRequestPackageInstalls()) {
            ctx.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return false
        }
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", apk)
        ctx.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
        return true
    }
}

/** 메인 화면(Compose)에서 한 번 호출: UpdatePrompt() */
@Composable
fun UpdatePrompt() {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var info by remember { mutableStateOf<UpdateInfo?>(null) }
    var progress by remember { mutableStateOf(-1) }
    var msg by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { info = Updater.check(ctx) }

    info?.let { u ->
        AlertDialog(
            onDismissRequest = { if (progress < 0) info = null },
            title = { Text("새 버전 ${u.version} 이(가) 나왔어요") },
            text = {
                Text(buildString {
                    if (u.notes.isNotBlank()) { appendLine(u.notes.take(300)); appendLine() }
                    append(when {
                        msg.isNotBlank() -> msg
                        progress in 0..99 -> "내려받는 중… $progress%"
                        progress == 100 -> "설치 화면으로 넘어가요"
                        else -> "지금 업데이트할까요? (기존 데이터는 그대로 유지돼요)"
                    })
                })
            },
            confirmButton = {
                TextButton(enabled = progress < 0, onClick = {
                    scope.launch {
                        progress = 0; msg = ""
                        val f = Updater.download(ctx, u.apkUrl) { progress = it }
                        if (f == null) { progress = -1; msg = "내려받기 실패. 인터넷을 확인해 주세요." }
                        else if (Updater.install(ctx, f)) info = null
                        else { progress = -1; msg = "'이 앱의 설치 허용'을 켜고 돌아와서 다시 눌러 주세요." }
                    }
                }) { Text("업데이트") }
            },
            dismissButton = { TextButton(enabled = progress < 0, onClick = { info = null }) { Text("나중에") } },
        )
    }
}
