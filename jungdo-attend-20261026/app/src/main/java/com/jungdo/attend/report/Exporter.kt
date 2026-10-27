package com.jungdo.attend.report

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/** 만든 파일 저장/공유 */
object Exporter {
    const val XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    const val HTML = "text/html"
    const val PNG = "image/png"

    /** 사용자가 고른 위치(다운로드 폴더 등)에 저장 */
    fun write(ctx: Context, uri: Uri, bytes: ByteArray) {
        ctx.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) }
    }

    /** 카톡·메일 등으로 바로 보내기 */
    fun share(ctx: Context, bytes: ByteArray, fileName: String, mime: String, title: String = "출석부 보내기", phone: String = "") {
        val dir = File(ctx.cacheDir, "exports").apply { mkdirs() }
        val f = File(dir, fileName).apply { writeBytes(bytes) }
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
        val send = Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        if (phone.isNotBlank()) send.putExtra("address", phone) // 문자 앱이 받는 사람을 미리 채워 줌 (앱에 따라 무시될 수 있음)
        ctx.startActivity(Intent.createChooser(send, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
