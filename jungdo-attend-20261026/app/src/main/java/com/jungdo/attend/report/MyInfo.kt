package com.jungdo.attend.report

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.Base64
import com.jungdo.attend.data.AssetPack
import com.jungdo.attend.data.DojangInfo
import com.jungdo.attend.data.Payout
import com.jungdo.attend.data.Rank
import com.jungdo.attend.data.Repo
import com.jungdo.attend.data.Student
import com.jungdo.attend.data.Wins
import kotlinx.coroutines.tasks.await
import java.io.ByteArrayOutputStream
import java.util.Locale

/** 원생 한 명의 "내 정보" (계급 상태 · 출석일수 · 지급받은 상품) — HTML 또는 이미지로 만들어 보냄 */
data class MyInfo(
    val student: Student,
    val totalDays: Int,           // 지금까지 출석한 날 수
    val monthDays: Int,           // 이번 달 출석한 날 수
    val month: String,            // yyyy-MM
    val payouts: List<Payout>,    // 지급받은 상품 (최근순)
    val madeDay: String,
) {
    val level get() = student.level
    val progress get() = Rank.progress(student.exp)
    val nextExp get() = if (level >= Rank.MAX_LEVEL) Rank.MAX_EXP else Rank.expFor(level + 1)
}

object MyInfoReport {
    const val GEM_TOTAL = 396
    const val PLATE_TOTAL = 47

    /** 서버에서 출석 기록·지급내역을 모아 MyInfo 로 */
    suspend fun load(s: Student): MyInfo {
        val ev = Repo.events().whereEqualTo("studentId", s.id).get().await().documents.mapNotNull { it.getString("day") }
        val today = Repo.today()
        val month = today.take(7)
        val days = ev.toSet()
        val pays = Repo.db.collection("payouts").whereEqualTo("studentId", s.id).get().await().documents
            .map(Wins::toPayout).sortedByDescending { it.paidAt }
        return MyInfo(s, days.size, days.count { it.startsWith(month) }, month, pays, today)
    }

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private fun rankPng(ctx: Context, level: Int): ByteArray? = runCatching {
        AssetPack.open(ctx, String.format(Locale.US, "rank/%03d.png", level.coerceIn(1, Rank.MAX_LEVEL))).use { it.readBytes() }
    }.getOrNull()

    private fun monthLabel(m: String) = "${m.take(4)}년 ${m.drop(5).toIntOrNull() ?: 0}월"
    private fun shortDay(d: String) = d.drop(5).split("-").let { if (it.size == 2) "${it[0].toInt()}/${it[1].toInt()}" else d }

    // ───────────── HTML ─────────────
    fun html(ctx: Context, i: MyInfo, dojang: String = DojangInfo.NAME): String {
        val s = i.student
        val img = rankPng(ctx, i.level)?.let { "<img class=\"rank\" src=\"data:image/png;base64,${Base64.encodeToString(it, Base64.NO_WRAP)}\" alt=\"\">" } ?: ""
        val pct = (i.progress * 100).toInt()
        val pays = if (i.payouts.isEmpty()) "<p class=\"none\">아직 받은 상품이 없어요. 열심히 출석해서 도전!</p>"
        else "<ul>" + i.payouts.joinToString("") { "<li><b>${esc(it.prizeName)}</b><span>${esc(it.payDay)}</span></li>" } + "</ul>"
        return """<!doctype html><html lang="ko"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>${esc(s.name)} 내 정보 · ${esc(dojang)}</title>
<style>
*{box-sizing:border-box}
body{margin:0;padding:14px;background:#eef2ff;font-family:'Malgun Gothic','Apple SD Gothic Neo',sans-serif;color:#1f2937}
.card{max-width:480px;margin:0 auto;background:#fff;border-radius:18px;overflow:hidden;box-shadow:0 4px 14px rgba(30,58,138,.15)}
.head{background:#1e3a8a;color:#fff;padding:14px 18px}
.head small{opacity:.8}.head h1{margin:2px 0 0;font-size:22px}
.sec{padding:14px 18px;border-top:1px solid #e5e7eb}
.sec h2{margin:0 0 8px;font-size:15px;color:#1e3a8a}
.rankbox{display:flex;align-items:center;gap:14px}
.rank{width:96px;height:96px;image-rendering:pixelated}
.lv{font-size:28px;font-weight:bold;color:#1e3a8a}
.bar{height:14px;background:#e5e7eb;border-radius:8px;overflow:hidden;margin:6px 0 2px}
.bar i{display:block;height:100%;width:$pct%;background:#f59e0b}
.mut{color:#6b7280;font-size:13px}
.grid{display:grid;grid-template-columns:1fr 1fr;gap:8px}
.box{background:#f3f4f6;border-radius:12px;padding:10px;text-align:center}
.box b{display:block;font-size:24px;color:#1e3a8a}.box span{font-size:12px;color:#6b7280}
ul{margin:0;padding:0;list-style:none}li{display:flex;justify-content:space-between;padding:7px 0;border-bottom:1px dashed #e5e7eb}li span{color:#6b7280;font-size:13px}
.none{color:#6b7280;margin:0}
</style></head><body><div class="card">
<div class="head"><small>${esc(dojang)}</small><h1>${esc(s.name)} <small>${esc(s.grade)}</small></h1></div>
<div class="sec"><h2>🥋 계급</h2><div class="rankbox">$img<div><div class="lv">Lv.${i.level}</div>
<div class="mut">경험치 ${s.exp} / ${i.nextExp}</div></div></div>
<div class="bar"><i></i></div><div class="mut">${if (i.level >= Rank.MAX_LEVEL) "최고 계급이에요!" else "다음 계급까지 ${100 - pct}% 남았어요"}</div></div>
<div class="sec"><h2>📅 출석</h2><div class="grid">
<div class="box"><b>${i.totalDays}일</b><span>지금까지 출석</span></div>
<div class="box"><b>${i.monthDays}일</b><span>${esc(monthLabel(i.month))}</span></div>
<div class="box"><b>${s.streak}일</b><span>연속 출석</span></div>
<div class="box"><b>${s.gems.size}/$GEM_TOTAL</b><span>보석</span></div></div>
<p class="mut" style="margin:8px 0 0">네임플레이트 ${s.plates.size}/$PLATE_TOTAL개 모음</p></div>
<div class="sec"><h2>🎁 받은 상품 (${i.payouts.size})</h2>$pays</div>
<div class="sec mut" style="text-align:center">${esc(i.madeDay)} 기준</div>
</div></body></html>"""
    }

    // ───────────── 이미지 (PNG) ─────────────
    fun bitmap(ctx: Context, i: MyInfo, dojang: String = DojangInfo.NAME): Bitmap {
        val s = i.student
        val w = 1080
        val pad = 56f
        val pays = i.payouts.take(8)
        val payRows = if (pays.isEmpty()) 1 else pays.size
        val h = (1010 + payRows * 78 + 140).toInt()
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.parseColor("#EEF2FF"))
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        fun text(t: String, x: Float, y: Float, size: Float, color: String, bold: Boolean = false, align: Paint.Align = Paint.Align.LEFT) {
            p.reset(); p.isAntiAlias = true; p.textSize = size; p.color = Color.parseColor(color)
            p.typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT; p.textAlign = align
            c.drawText(t, x, y, p)
        }
        fun rect(l: Float, t: Float, r: Float, b: Float, color: String, rad: Float = 28f) {
            p.reset(); p.isAntiAlias = true; p.color = Color.parseColor(color); c.drawRoundRect(RectF(l, t, r, b), rad, rad, p)
        }
        // 카드
        rect(24f, 24f, w - 24f, h - 24f, "#FFFFFF", 40f)
        rect(24f, 24f, w - 24f, 200f, "#1E3A8A", 40f); rect(24f, 120f, w - 24f, 200f, "#1E3A8A", 0f)
        text(dojang, pad, 80f, 30f, "#C7D2FE")
        text("${s.name}  ${s.grade}", pad, 160f, 60f, "#FFFFFF", true)

        // 계급
        text("🥋 계급", pad, 280f, 38f, "#1E3A8A", true)
        rankPng(ctx, i.level)?.let { b ->
            BitmapFactory.decodeByteArray(b, 0, b.size)?.let { rb ->
                p.reset(); p.isFilterBitmap = false
                c.drawBitmap(rb, null, RectF(pad, 310f, pad + 240f, 550f), p)
            }
        }
        text("Lv.${i.level}", pad + 280f, 420f, 100f, "#1E3A8A", true)
        text("경험치 ${s.exp} / ${i.nextExp}", pad + 280f, 480f, 40f, "#6B7280")
        val barL = pad; val barR = w - pad; val barT = 580f
        rect(barL, barT, barR, barT + 36f, "#E5E7EB", 18f)
        val fill = (barL + (barR - barL) * i.progress).coerceAtLeast(barL + 36f)
        rect(barL, barT, fill, barT + 36f, "#F59E0B", 18f)
        text(if (i.level >= Rank.MAX_LEVEL) "최고 계급이에요!" else "다음 계급까지 ${100 - (i.progress * 100).toInt()}% 남았어요", pad, 664f, 32f, "#6B7280")

        // 출석
        text("📅 출석", pad, 750f, 38f, "#1E3A8A", true)
        val bw = (w - pad * 2 - 20f) / 2f
        fun box(col: Int, row: Int, big: String, small: String) {
            val l = pad + col * (bw + 20f); val t = 780f + row * 120f
            rect(l, t, l + bw, t + 104f, "#F3F4F6", 24f)
            text(big, l + bw / 2, t + 56f, 48f, "#1E3A8A", true, Paint.Align.CENTER)
            text(small, l + bw / 2, t + 92f, 26f, "#6B7280", false, Paint.Align.CENTER)
        }
        box(0, 0, "${i.totalDays}일", "지금까지 출석")
        box(1, 0, "${i.monthDays}일", monthLabel(i.month))
        box(0, 1, "${s.streak}일", "연속 출석")
        box(1, 1, "${s.gems.size}/$GEM_TOTAL", "보석 · 네임플레이트 ${s.plates.size}개")

        // 받은 상품
        var y = 1070f
        text("🎁 받은 상품 (${i.payouts.size})", pad, y, 38f, "#1E3A8A", true)
        y += 30f
        if (pays.isEmpty()) {
            y += 56f; text("아직 받은 상품이 없어요. 열심히 출석해서 도전!", pad, y, 32f, "#6B7280")
        } else for (pz in pays) {
            y += 70f
            text(pz.prizeName, pad, y, 36f, "#1F2937", true)
            text(shortDay(pz.payDay), w - pad, y, 30f, "#6B7280", false, Paint.Align.RIGHT)
            p.reset(); p.color = Color.parseColor("#E5E7EB"); c.drawRect(pad, y + 14f, w - pad, y + 16f, p)
            y += 8f
        }
        if (i.payouts.size > pays.size) { y += 56f; text("… 외 ${i.payouts.size - pays.size}개", pad, y, 28f, "#6B7280") }
        text("${i.madeDay} 기준", w / 2f, h - 60f, 28f, "#9CA3AF", false, Paint.Align.CENTER)
        return bmp
    }

    fun png(bmp: Bitmap): ByteArray = ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
}
