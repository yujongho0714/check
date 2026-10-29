package com.jungdo.attend.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.ByteArrayOutputStream

/**
 * 상품 한 가지 (Firestore: prizes/{id})
 *  - chance : 당첨 확률 (%) — 등원/하원 한 번 누를 때마다 이 확률로 당첨. 상품마다 따로 굴리므로 합이 100% 를 넘어도 됨
 *  - stock  : 남은 재고. null 이면 무제한. 0 이 되면 더 이상 당첨 안 됨
 *  - photo  : 작게 줄인 사진 (JPEG 을 글자로 바꾼 base64)
 */
data class Prize(
    val id: String = "",
    val name: String = "",
    val chance: Double = 0.0,
    val message: String = "",
    val stock: Int? = null,
    val photo: String = "",
)

/** 키오스크 완료 화면에 보여줄 당첨 내용 */
data class PrizeWin(val prizeId: String, val name: String, val message: String, val photo: String)

object Prizes {
    /** 지금 등록된 상품들. Repo.startPrizeSync 가 서버 값으로 계속 갱신 */
    var list by mutableStateOf<List<Prize>>(emptyList())

    /** 다른 상품(except) 을 뺀 확률 합계 (%) */
    fun totalChance(except: String? = null): Double = list.filter { it.id != except }.sumOf { it.chance }

    /** 원생 id → 마지막으로 상품에 당첨된 날(yyyy-MM-dd). wins 기록 + 방금 당첨된 것. Repo.startPrizeSync 가 서버 값으로 계속 갱신 */
    var winnerDays: Map<String, String> = emptyMap()

    /** true 면 "오늘" 당첨된 원생은 오늘 다시 상품 추첨에서 제외 (등원에 당첨 → 하원에 또 당첨 방지). 다음 날이 되면 저절로 풀려서 따로 초기화할 필요 없음 */
    const val ONE_WIN_PER_STUDENT = true

    fun hasWon(studentId: String): Boolean = studentId.isNotBlank() && winnerDays[studentId] == Repo.today()

    fun markWon(studentId: String) { if (studentId.isNotBlank()) winnerDays = winnerDays + (studentId to Repo.today()) }

    /**
     * 상품 추첨 — 상품마다 자기 확률(chance %)로 따로 굴림. 다른 상품의 확률·순서에 영향받지 않음.
     * 재고 0 인 상품은 후보에서 빠지고, 여러 상품이 동시에 맞으면 그중 하나를 무작위로 (한 번에 최대 1개).
     * 오늘 이미 당첨된 원생(studentId)은 오늘 추첨 안 함 (내일부터 다시 추첨).
     */
    fun roll(studentId: String = ""): Prize? {
        if (ONE_WIN_PER_STUDENT && hasWon(studentId)) return null
        val hits = list.filter { p ->
            p.chance > 0.0 && (p.stock == null || p.stock > 0) && Math.random() * 100.0 < p.chance
        }
        return hits.randomOrNull()
    }

    /**
     * 레전드리 보너스 상품 — 장착한 보석이 레전드리일 때만, 일반 상품 추첨에서 안 맞았으면 한 번 더 굴림.
     * legendaryChance(%) 에 맞으면 legendaryIds 로 지정된 상품 중 재고 있는 것 하나. 서버(config/loot) 값은 Repo.applyLoot 가 채움.
     * 관리자가 아직 안 정했으면(확률 0 또는 상품 없음) 아무 일도 안 일어남.
     */
    var legendaryChance by mutableStateOf(0.0)
    var legendaryIds by mutableStateOf<Set<String>>(emptySet())

    fun rollLegendary(studentId: String = ""): Prize? {
        if (ONE_WIN_PER_STUDENT && hasWon(studentId)) return null
        if (legendaryChance <= 0.0 || Math.random() * 100.0 >= legendaryChance) return null
        return list.filter { it.id in legendaryIds && (it.stock == null || it.stock > 0) }.randomOrNull()
    }

    /** 당첨 직후 화면에서 바로 재고를 하나 줄임 (같이 온 형제가 연달아 뽑을 때 재고 넘침 방지) */
    fun consume(id: String) {
        list = list.map { if (it.id == id && it.stock != null) it.copy(stock = (it.stock - 1).coerceAtLeast(0)) else it }
    }

    fun decode(photo: String): Bitmap? {
        if (photo.isBlank()) return null
        return runCatching {
            val b = Base64.decode(photo, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(b, 0, b.size)
        }.getOrNull()
    }

    /** 갤러리에서 고른 사진을 긴 쪽 360px 로 줄여서 JPEG → base64 글자로 (약 20~50KB). 실패하면 null */
    fun encodePhoto(ctx: Context, uri: Uri): String? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 720) sample *= 2
        val src = ctx.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: error("decode")
        val orient = runCatching {
            ctx.contentResolver.openInputStream(uri)?.use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1) }
        }.getOrNull() ?: 1
        val deg = when (orient) { 6 -> 90f; 3 -> 180f; 8 -> 270f; else -> 0f }
        val scale = minOf(1f, 360f / maxOf(src.width, src.height))
        val m = Matrix().apply { postScale(scale, scale); postRotate(deg) }
        val scaled = Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
        // 투명한 배경(PNG)은 JPEG 에서 까맣게 되므로 흰 바탕 위에 올림
        val out = Bitmap.createBitmap(scaled.width, scaled.height, Bitmap.Config.ARGB_8888)
        Canvas(out).apply { drawColor(android.graphics.Color.WHITE); drawBitmap(scaled, 0f, 0f, null) }
        val bos = ByteArrayOutputStream()
        out.compress(Bitmap.CompressFormat.JPEG, 80, bos)
        Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP)
    }.getOrNull()
}
