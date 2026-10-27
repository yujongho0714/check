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
 *  - chance : 당첨 확률 (%) — 등원/하원 한 번 누를 때마다 이 확률로 당첨. 모든 상품의 합이 100% 를 넘으면 안 됨
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

    /**
     * 상품 추첨. 확률 칸을 차례로 이어 붙여서 한 번만 뽑음 (한 번에 상품은 최대 1개).
     * 재고가 0 인 상품 칸에 걸리면 꽝. 아무 칸에도 안 걸려도 꽝 → null
     */
    fun roll(): Prize? {
        val r = Math.random() * 100.0
        var acc = 0.0
        for (p in list) {
            if (p.chance <= 0.0) continue
            acc += p.chance
            if (r < acc) {
                val st = p.stock
                return if (st == null || st > 0) p else null
            }
        }
        return null
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
