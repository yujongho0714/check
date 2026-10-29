package com.jungdo.attend.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.jungdo.attend.data.AssetPack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/*
 * 드레스업 캐릭터 그리기 (7단계). assets/dressup/ 의 몸(base.webp) + 옷 시트(8부위)를 겹쳐 그림.
 *  - 그림 좌표는 dress.json 규칙: 몸 한 칸(288x251)은 캔버스(311x294) 왼쪽 위(0,0)에 그리고,
 *    옷은 동작별 [dx,dy,w,h,프레임들] 로 "시트의 (sx,sy) 에서 w x h 를 잘라 캔버스 (dx,dy) 에" 그림.
 *  - 겹치는 순서: 망토 → 몸 → 신발 → 무기 → 방패 → 겉옷(armor) → 갑옷(coat) → 헤어 → 투구
 *  - 마스크(e): 옷에 마스크가 있으면 그 모양대로 "몸"을 지움 (헤어·투구가 몸의 머리를 가리는 등)
 *  - 왼쪽은 오른쪽 그림을 좌우로 뒤집어서 그림. 서기 = 걷기 첫 프레임.
 */

/** 옷 한 동작의 좌표: 캔버스 (dx,dy) 에 w x h 로 그림. frames = 시트 안 각 프레임의 왼쪽 위 [sx,sy] */
class DressMotion(val dx: Int, val dy: Int, val w: Int, val h: Int, val frames: List<IntArray>)

/** 옷 한 벌. key 는 "coat:012" 형식 */
class DressItem(val key: String, val name: String, val motions: Map<String, DressMotion>, val sheet: String, val mask: String?)

/** dress.json 을 읽은 결과 */
class DressPack(
    val w: Int, val h: Int, val dur: Int, val footX: Float,
    val baseW: Int, val baseH: Int,
    val baseMotions: Map<String, List<IntArray>>,
    val itemsByCat: Map<String, List<DressItem>>,
) {
    private val byKey: Map<String, DressItem> = itemsByCat.values.flatten().associateBy { it.key }
    fun item(key: String): DressItem? = byKey[key]
}

object DressAssets {
    @Volatile private var pack: DressPack? = null
    fun peek(): DressPack? = pack

    fun pack(ctx: Context): DressPack = pack ?: synchronized(this) { pack ?: parse(ctx).also { pack = it } }

    /** 관리자가 올린 옷 그림이 바뀌었을 때: 읽어 둔 옷 데이터와 그림을 비워서 다시 읽게 함 */
    fun clear() {
        synchronized(this) { pack = null }
        cache.evictAll()
    }

    private fun parse(ctx: Context): DressPack {
        val root = JSONObject(AssetPack.open(ctx, "dressup/dress.json").bufferedReader(Charsets.UTF_8).use { it.readText() })
        val base = root.getJSONObject("base")
        val box = base.getJSONArray("box")
        val bv = base.getJSONObject("v")
        val baseMotions = HashMap<String, List<IntArray>>()
        for (k in bv.keys()) baseMotions[k] = frames(bv.getJSONArray(k))
        val cats = LinkedHashMap<String, List<DressItem>>()
        val co = root.getJSONObject("cats")
        for (cat in co.keys()) {
            val arr = co.getJSONArray(cat)
            val list = ArrayList<DressItem>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val v = o.getJSONObject("v")
                val motions = HashMap<String, DressMotion>()
                for (k in v.keys()) {
                    val m = v.getJSONArray(k)
                    motions[k] = DressMotion(m.getInt(0), m.getInt(1), m.getInt(2), m.getInt(3), frames(m.getJSONArray(4)))
                }
                list.add(DressItem(cat + ":" + o.getString("id"), o.optString("n"), motions, o.getString("s"), o.optString("e").ifBlank { null }))
            }
            cats[cat] = list
        }
        val foot = root.getJSONArray("foot")
        return DressPack(
            root.getInt("W"), root.getInt("H"), root.getInt("dur"), foot.getDouble(0).toFloat(),
            box.getInt(2), box.getInt(3), baseMotions, cats,
        )
    }

    private fun frames(a: JSONArray): List<IntArray> = (0 until a.length()).map { i -> a.getJSONArray(i).let { intArrayOf(it.getInt(0), it.getInt(1)) } }

    // ── 그림 시트 (한 번 읽은 건 기억해 둠, 약 24MB 까지) ──
    private val cache = object : LruCache<String, ImageBitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }

    fun peekImage(rel: String): ImageBitmap? = cache.get(rel)

    fun image(ctx: Context, rel: String): ImageBitmap? =
        cache.get(rel) ?: runCatching {
            AssetPack.open(ctx, "dressup/$rel").use { BitmapFactory.decodeStream(it) }?.asImageBitmap()
        }.getOrNull()?.also { cache.put(rel, it) }
}

enum class DressDir { FRONT, BACK, RIGHT, LEFT }
enum class DressAction { STAND, WALK, ATTACK }

/** 옷 데이터(dress.json)를 백그라운드에서 읽어 줌. 읽는 동안은 null */
@Composable
fun rememberDressPack(): DressPack? {
    val ctx = LocalContext.current.applicationContext
    val ver = AssetPack.version // 올린 옷 그림이 바뀌면 다시 읽어요
    val st = produceState<DressPack?>(DressAssets.peek(), ctx, ver) {
        value = withContext(Dispatchers.IO) { runCatching { DressAssets.pack(ctx) }.getOrNull() }
    }
    return st.value
}

/** 몸 위에 그려지는 순서 (몸은 신발 바로 앞) */
private val BEFORE_BODY = listOf("cape")
private val AFTER_BODY = listOf("shoes", "weapon", "shield", "armor", "coat", "hair", "helmet")
/** 마스크로 몸을 지우는 순서는 상관없음 */
private val MASK_CATS = AFTER_BODY

/**
 * 내 캐릭터. equip = 입은 옷 (부위 → "coat:012" 같은 옷 키, Student.dressEquip 그대로).
 * height = 캐릭터 그림 높이. 가로는 311:294 비율로 자동.
 * action 이 ATTACK 이면 공격 모션을 한 번 재생하고 onAttackDone 을 부름 (그때 action 을 STAND 로 돌려 주세요).
 */
@Composable
fun DressCharacter(
    equip: Map<String, String>,
    height: Dp,
    modifier: Modifier = Modifier,
    dir: DressDir = DressDir.FRONT,
    action: DressAction = DressAction.STAND,
    onAttackDone: () -> Unit = {},
) {
    val ctx = LocalContext.current.applicationContext
    val pack = rememberDressPack()
    if (pack == null) {
        Spacer(modifier.size(width = height * (311f / 294f), height = height))
        return
    }

    // 입은 옷: 부위 → 옷 (모르는 키는 무시)
    val worn = remember(pack, equip) {
        (BEFORE_BODY + AFTER_BODY).mapNotNull { cat -> equip[cat]?.let { k -> pack.item(k)?.let { cat to it } } }.toMap()
    }
    // 필요한 그림 시트들 (몸 + 입은 옷의 시트와 마스크)
    val ver = AssetPack.version
    val paths = remember(worn) { listOf("base.webp") + worn.values.flatMap { listOfNotNull(it.sheet, it.mask) } }
    val images by produceState(paths.mapNotNull { p -> DressAssets.peekImage(p)?.let { p to it } }.toMap(), paths, ver) {
        value = withContext(Dispatchers.IO) { paths.mapNotNull { p -> DressAssets.image(ctx, p)?.let { p to it } }.toMap() }
    }

    // 시간 (밀리초): 서기는 멈춤, 걷기는 계속, 공격은 한 번 재생 후 끝
    var t by remember { mutableLongStateOf(0L) }
    val done by rememberUpdatedState(onAttackDone)
    val atkMs = pack.dur * (pack.baseMotions["atk_f"]?.size ?: 2)
    LaunchedEffect(action, pack) {
        t = 0L
        if (action == DressAction.STAND) return@LaunchedEffect
        val start = androidx.compose.runtime.withFrameMillis { it }
        while (true) {
            val el = androidx.compose.runtime.withFrameMillis { it } - start
            t = el
            if (action == DressAction.ATTACK && el >= atkMs) { done(); break }
        }
    }

    val sfx = when (dir) { DressDir.FRONT -> "f"; DressDir.BACK -> "b"; else -> "r" }
    val motion = (if (action == DressAction.ATTACK) "atk_" else "walk_") + sfx
    val baseFrames = pack.baseMotions[motion] ?: pack.baseMotions["walk_f"].orEmpty()
    val fi = when (action) {
        DressAction.STAND -> 0
        DressAction.WALK -> ((t / pack.dur) % baseFrames.size.coerceAtLeast(1)).toInt()
        DressAction.ATTACK -> (t / pack.dur).toInt().coerceAtMost(baseFrames.size - 1)
    }

    Canvas(modifier.size(width = height * (pack.w.toFloat() / pack.h), height = height)) {
        val s = size.height / pack.h
        scale(s, s, pivot = Offset.Zero) {
            if (dir == DressDir.LEFT) scale(-1f, 1f, pivot = Offset(pack.footX, 0f)) { drawCharacter(pack, worn, images, motion, fi, baseFrames) }
            else drawCharacter(pack, worn, images, motion, fi, baseFrames)
        }
    }
}

private fun DrawScope.drawFrame(img: ImageBitmap, sx: Int, sy: Int, w: Int, h: Int, dx: Int, dy: Int, blend: BlendMode = BlendMode.SrcOver) {
    if (sx < 0 || sy < 0 || sx + w > img.width || sy + h > img.height) return
    drawImage(
        img, srcOffset = IntOffset(sx, sy), srcSize = IntSize(w, h),
        dstOffset = IntOffset(dx, dy), dstSize = IntSize(w, h),
        filterQuality = FilterQuality.None, blendMode = blend,
    )
}

private fun DrawScope.drawItem(it: DressItem, images: Map<String, ImageBitmap>, motion: String, fi: Int) {
    val m = it.motions[motion] ?: return
    val img = images[it.sheet] ?: return
    val f = m.frames[fi.coerceAtMost(m.frames.size - 1)]
    drawFrame(img, f[0], f[1], m.w, m.h, m.dx, m.dy)
}

private fun DrawScope.drawCharacter(
    pack: DressPack, worn: Map<String, DressItem>, images: Map<String, ImageBitmap>,
    motion: String, fi: Int, baseFrames: List<IntArray>,
) {
    BEFORE_BODY.forEach { c -> worn[c]?.let { drawItem(it, images, motion, fi) } }

    // 몸: 따로 한 장(레이어)에 그려서, 옷의 마스크 모양만큼 지운 뒤 합침
    val baseImg = images["base.webp"]
    if (baseImg != null && baseFrames.isNotEmpty()) {
        drawIntoCanvas { canvas ->
            canvas.saveLayer(Rect(0f, 0f, pack.w.toFloat(), pack.h.toFloat()), Paint())
            val bf = baseFrames[fi.coerceAtMost(baseFrames.size - 1)]
            drawFrame(baseImg, bf[0], bf[1], pack.baseW, pack.baseH, 0, 0)
            MASK_CATS.forEach { c ->
                val item = worn[c] ?: return@forEach
                val mk = item.mask ?: return@forEach
                val m = item.motions[motion] ?: return@forEach
                val mi = images[mk] ?: return@forEach
                val f = m.frames[fi.coerceAtMost(m.frames.size - 1)]
                drawFrame(mi, f[0], f[1], m.w, m.h, m.dx, m.dy, BlendMode.DstOut)
            }
            canvas.restore()
        }
    }

    AFTER_BODY.forEach { c -> worn[c]?.let { drawItem(it, images, motion, fi) } }
}
