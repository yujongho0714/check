package com.jungdo.attend.data

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/*
 * ───────────────────────────────────────────────────────────────
 *  이미지 팩 (계급 · 보석 · 네임플레이트 · UI 테마)
 *
 *  - 그림은 앱 안에 넣지 않고, 앱 화면에서 zip 으로 올려요.
 *  - 올린 그림은 이 기기의 filesDir/assetpack/ 에 저장돼요. (앱 기본 assets 보다 먼저 찾아요)
 *  - 올리면 같은 도장 계정(Firestore)에도 저장돼서, 다른 기기(공기계)가 자동으로 받아가요.
 *  - zip 안 폴더 이름: rank / gem / plate / theme  (그 앞에 다른 폴더가 있어도 돼요)
 * ───────────────────────────────────────────────────────────────
 */
object AssetPack {
    private val ROOTS = setOf("rank", "gem", "plate", "theme")
    private val EXT = setOf("png", "webp", "jpg", "jpeg", "json", "txt")
    private const val MAX_TOTAL = 150L * 1024 * 1024
    private const val MAX_ENTRIES = 20000
    private const val CHUNK = 700_000

    private const val SP = "jungdo_assetpack"
    private const val META_COLL = "config"
    private const val META_DOC = "assetpack"
    private const val CHUNK_COLL = "assetpack_chunks"

    /** 그림이 바뀔 때마다 올라가는 숫자. 화면이 이 값을 읽어서 그림을 다시 불러와요 */
    var version by mutableLongStateOf(0L)
        private set

    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val db: FirebaseFirestore get() = FirebaseFirestore.getInstance()

    private fun sp(ctx: Context) = ctx.getSharedPreferences(SP, Context.MODE_PRIVATE)
    private fun dir(ctx: Context) = File(ctx.filesDir, "assetpack")

    // ───────────── 그림 열기 (올린 것 먼저, 없으면 앱 기본) ─────────────
    fun open(ctx: Context, path: String): InputStream {
        if (!path.contains("..")) {
            val f = File(dir(ctx), path)
            if (f.isFile) return FileInputStream(f)
        }
        return ctx.assets.open(path)
    }

    // ───────────── 테마 선택 ─────────────
    fun activeThemeId(ctx: Context): String {
        sp(ctx).getString("activeTheme", null)?.trim()?.takeIf { it.isNotBlank() }?.let { return it }
        return runCatching { open(ctx, "theme/active.txt").bufferedReader().use { it.readText() }.trim() }
            .getOrDefault("").ifBlank { "classic" }
    }

    /** 쓸 수 있는 테마 이름들 (앱 기본 + 올린 것) */
    fun themes(ctx: Context): List<String> {
        val a = runCatching { ctx.assets.list("theme")?.toList() }.getOrNull().orEmpty().filter { !it.contains('.') }
        val b = File(dir(ctx), "theme").listFiles()?.filter { it.isDirectory }?.map { it.name }.orEmpty()
        return (a + b).distinct().sorted()
    }

    suspend fun setActiveTheme(ctx: Context, id: String) {
        sp(ctx).edit().putString("activeTheme", id).apply()
        bump()
        runCatching { db.collection(META_COLL).document(META_DOC).set(mapOf("activeTheme" to id), SetOptions.merge()).await() }
    }

    // ───────────── 지금 상태 ─────────────
    class Stats(val rank: Int, val gem: Int, val plate: Int, val themes: List<String>) {
        val empty get() = rank == 0 && gem == 0 && plate == 0 && themes.isEmpty()
    }

    fun stats(ctx: Context): Stats {
        fun n(d: String) = File(dir(ctx), d).listFiles()?.count { it.isFile } ?: 0
        val th = File(dir(ctx), "theme").listFiles()?.filter { it.isDirectory }?.map { it.name }.orEmpty().sorted()
        return Stats(n("rank"), n("gem"), n("plate"), th)
    }

    // ───────────── zip 올리기 (이 기기에 풀고 → 계정에 저장) ─────────────
    suspend fun importAndPush(ctx: Context, uris: List<Uri>, say: (String) -> Unit): String = mutex.withLock {
        withContext(Dispatchers.IO) {
            try {
                // 다른 기기에서 먼저 올린 게 있으면 받아서 합친 뒤에 올려요 (덮어써서 사라지는 것 방지)
                runCatching { doPull(ctx, force = false, say = say) }
                var files = 0; var skipped = 0
                uris.forEachIndexed { i, u ->
                    say("zip ${i + 1}/${uris.size} 푸는 중…")
                    val c = Counters()
                    val ins = ctx.contentResolver.openInputStream(u) ?: throw IOException("파일을 열 수 없어요")
                    ins.use { extract(it, dir(ctx), c) }
                    files += c.files; skipped += c.skipped
                }
                if (files == 0) return@withContext "인식된 그림이 없어요. zip 안에 rank / gem / plate / theme 폴더가 있어야 해요. (건너뜀 $skipped)"
                bump()
                say("계정에 저장하는 중… (다른 기기가 받아가요)")
                val ok = push(ctx, say)
                "그림 ${files}장을 적용했어요 ✔" + (if (skipped > 0) " (건너뜀 $skipped)" else "") +
                    if (ok) "\n계정에 저장됐어요. 다른 기기는 잠시 뒤 자동으로 받아요." else "\n⚠️ 이 기기에는 적용됐지만 계정 저장은 실패했어요. 인터넷을 확인하고 [동기화]를 눌러 주세요."
            } catch (e: Exception) {
                "실패: ${e.message ?: e.javaClass.simpleName}"
            }
        }
    }

    /** 다른 기기에서 올린 최신 그림을 받아요 */
    suspend fun syncNow(ctx: Context, say: (String) -> Unit): String = mutex.withLock {
        withContext(Dispatchers.IO) {
            try {
                doPull(ctx, force = true, say = say) ?: "이미 최신이에요"
            } catch (e: Exception) {
                "실패: ${e.message ?: e.javaClass.simpleName}"
            }
        }
    }

    /** 올린 그림을 모두 지우고 앱 기본으로 (계정에서도 지워서 다른 기기도 기본으로 돌아가요) */
    suspend fun clearAll(ctx: Context): String = mutex.withLock {
        withContext(Dispatchers.IO) {
            try {
                val oldN = runCatching { (db.collection(META_COLL).document(META_DOC).get().await().getLong("chunks") ?: 0L).toInt() }.getOrDefault(0)
                dir(ctx).deleteRecursively()
                sp(ctx).edit().remove("activeTheme").apply()
                bump()
                val v = System.currentTimeMillis()
                db.collection(META_COLL).document(META_DOC).set(
                    mapOf("version" to v, "chunks" to 0L, "bytes" to 0L, "activeTheme" to FieldValue.delete()), SetOptions.merge()
                ).await()
                for (i in 0 until oldN) runCatching { db.collection(CHUNK_COLL).document(i.toString()).delete().await() }
                sp(ctx).edit().putLong("localVersion", v).apply()
                "지웠어요. 앱 기본 그림을 써요."
            } catch (e: Exception) {
                "실패: ${e.message ?: e.javaClass.simpleName}"
            }
        }
    }

    // ───────────── 자동 동기화 (로그인 후 계속 지켜봐요) ─────────────
    fun startSync(ctx: Context): ListenerRegistration {
        val app = ctx.applicationContext
        return db.collection(META_COLL).document(META_DOC).addSnapshotListener { snap, _ ->
            if (snap == null || !snap.exists()) return@addSnapshotListener
            scope.launch {
                runCatching { mutex.withLock { withContext(Dispatchers.IO) { doPull(app, force = false, say = {}) } } }
            }
        }
    }

    // ───────────── 안쪽 일 ─────────────
    private class Counters { var files = 0; var skipped = 0 }

    private fun bump() {
        runCatching { com.jungdo.attend.ui.RankThemes.clear() }
        runCatching { com.jungdo.attend.ui.clearAssetCache() }
        version += 1
    }

    /** zip 안 경로를 rank/… gem/… plate/… theme/… 로 정리. 안 맞으면 null */
    private fun normalize(name: String): String? {
        val p = name.replace('\\', '/').split('/').filter { it.isNotEmpty() && it != "." }
        if (p.isEmpty() || p.any { it == ".." || it.startsWith("__MACOSX") } || p.last().startsWith(".")) return null
        val i = p.indexOfFirst { it in ROOTS }
        if (i < 0) return null
        val rel = p.drop(i)
        return if (rel.size >= 2) rel.joinToString("/") else null
    }

    private fun extract(input: InputStream, dest: File, c: Counters) {
        dest.mkdirs()
        val base = dest.canonicalPath + File.separator
        var total = 0L
        var entries = 0
        ZipInputStream(BufferedInputStream(input)).use { zin ->
            while (true) {
                val e: ZipEntry = zin.nextEntry ?: break
                if (++entries > MAX_ENTRIES) throw IOException("zip 안 파일이 너무 많아요")
                if (e.isDirectory) continue
                val rel = normalize(e.name)
                val ext = e.name.substringAfterLast('.', "").lowercase()
                if (rel == null || ext !in EXT) { c.skipped++; continue }
                val out = File(dest, rel)
                if (!out.canonicalPath.startsWith(base)) { c.skipped++; continue }
                out.parentFile?.mkdirs()
                FileOutputStream(out).use { fo ->
                    val buf = ByteArray(32 * 1024)
                    while (true) {
                        val n = zin.read(buf)
                        if (n < 0) break
                        total += n
                        if (total > MAX_TOTAL) throw IOException("zip 이 너무 커요")
                        fo.write(buf, 0, n)
                    }
                }
                c.files++
            }
        }
    }

    private fun zipDir(dir: File): ByteArray {
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { z ->
            z.setLevel(Deflater.BEST_SPEED)
            dir.walkTopDown().filter { it.isFile }.forEach { f ->
                z.putNextEntry(ZipEntry(f.relativeTo(dir).path.replace('\\', '/')))
                FileInputStream(f).use { it.copyTo(z) }
                z.closeEntry()
            }
        }
        return bos.toByteArray()
    }

    private suspend fun push(ctx: Context, say: (String) -> Unit): Boolean = try {
        val bytes = zipDir(dir(ctx))
        val metaRef = db.collection(META_COLL).document(META_DOC)
        val oldN = runCatching { (metaRef.get().await().getLong("chunks") ?: 0L).toInt() }.getOrDefault(0)
        val n = (bytes.size + CHUNK - 1) / CHUNK
        val v = System.currentTimeMillis()
        for (i in 0 until n) {
            val part = bytes.copyOfRange(i * CHUNK, minOf(bytes.size, (i + 1) * CHUNK))
            db.collection(CHUNK_COLL).document(i.toString()).set(mapOf("v" to v, "data" to Blob.fromBytes(part))).await()
            say("계정에 저장하는 중… ${i + 1}/$n")
        }
        val meta = hashMapOf<String, Any>("version" to v, "chunks" to n.toLong(), "bytes" to bytes.size.toLong())
        sp(ctx).getString("activeTheme", null)?.let { meta["activeTheme"] = it }
        metaRef.set(meta, SetOptions.merge()).await()
        for (i in n until oldN) runCatching { db.collection(CHUNK_COLL).document(i.toString()).delete().await() }
        sp(ctx).edit().putLong("localVersion", v).apply()
        true
    } catch (e: Exception) {
        false
    }

    /** 계정의 그림이 이 기기보다 새로우면 받아서 통째로 바꿔요. 받았으면 안내 글, 아니면 null */
    private suspend fun doPull(ctx: Context, force: Boolean, say: (String) -> Unit): String? {
        val meta = db.collection(META_COLL).document(META_DOC).get().await()
        if (!meta.exists()) return null

        // 테마 선택은 그림과 별개로 따라가요
        val cloudTheme = meta.getString("activeTheme")?.trim().orEmpty()
        val localTheme = sp(ctx).getString("activeTheme", null).orEmpty()
        if (cloudTheme != localTheme) {
            if (cloudTheme.isBlank()) sp(ctx).edit().remove("activeTheme").apply()
            else sp(ctx).edit().putString("activeTheme", cloudTheme).apply()
            bump()
        }

        val v = meta.getLong("version") ?: 0L
        if (!force && v == sp(ctx).getLong("localVersion", 0L)) return null
        val n = (meta.getLong("chunks") ?: 0L).toInt()
        if (n == 0) {
            dir(ctx).deleteRecursively()
            sp(ctx).edit().putLong("localVersion", v).apply()
            bump()
            return "앱 기본 그림을 써요"
        }
        val bos = ByteArrayOutputStream()
        for (i in 0 until n) {
            say("받는 중… ${i + 1}/$n")
            val d = db.collection(CHUNK_COLL).document(i.toString()).get().await()
            if (d.getLong("v") != v) throw IOException("다른 기기가 올리는 중이에요. 잠시 뒤에 다시 해 주세요")
            bos.write(d.getBlob("data")?.toBytes() ?: throw IOException("그림 조각을 못 읽었어요"))
        }
        if (bos.size().toLong() != (meta.getLong("bytes") ?: -1L)) throw IOException("받은 크기가 달라요. 다시 해 주세요")

        say("적용하는 중…")
        val tmp = File(ctx.filesDir, "assetpack_new")
        tmp.deleteRecursively()
        val c = Counters()
        extract(ByteArrayInputStream(bos.toByteArray()), tmp, c)
        dir(ctx).deleteRecursively()
        if (!tmp.renameTo(dir(ctx))) throw IOException("적용에 실패했어요")
        sp(ctx).edit().putLong("localVersion", v).apply()
        bump()
        return "그림 ${c.files}장을 받아서 적용했어요 ✔"
    }
}
