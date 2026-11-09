package com.jungdo.attend.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * 띠(품·급)와 줄넘기 급
 *  - 태권도 띠: 흰띠 … 빨검띠, 그다음 "1품 12급" … "1품 1급", "2품 24급" … (통합관리와 같은 글자)
 *  - 줄넘기 급: 9급 … 1급
 */
object Belts {
    val COLORS = listOf("흰띠", "흰노띠", "노랑띠", "노초띠", "초록띠", "초파띠", "파랑띠", "보라띠", "밤띠", "밤빨띠", "빨강띠", "빨검띠")
    val JUMP = (9 downTo 1).map { "${it}급" }
    /** 국기원 문자 바로 고르기 */
    val KUKKIWON = listOf("1품 1급", "2품 1급", "3품 1급", "빨검띠")

    /** "1품5급" / "1품 5" / " 1 품 5 급 " → "1품 5급" , 색띠는 그대로 */
    fun norm(raw: String): String {
        val s = raw.trim().replace(Regex("\\s+"), " ")
        if (s.isEmpty()) return ""
        if (s in COLORS) return s
        Regex("^(\\d+)\\s*품\\s*(\\d+)?\\s*급?$").find(s)?.let { m ->
            val p = m.groupValues[1].toInt()
            val g = m.groupValues[2].toIntOrNull() ?: (p * 12)
            return "${p}품 ${g}급"
        }
        return s
    }

    /** 띠 색 (막대 그림용): 위·가운데 두 가지 색 */
    fun colors(belt: String): Pair<Long, Long>? = when (norm(belt)) {
        "흰띠" -> 0xFFF2F2F2 to 0xFFF2F2F2
        "흰노띠" -> 0xFFF2F2F2 to 0xFFFFD400
        "노랑띠" -> 0xFFFFD400 to 0xFFFFD400
        "노초띠" -> 0xFFFFD400 to 0xFF2E9E44
        "초록띠" -> 0xFF2E9E44 to 0xFF2E9E44
        "초파띠" -> 0xFF2E9E44 to 0xFF1565C0
        "파랑띠" -> 0xFF1565C0 to 0xFF1565C0
        "보라띠" -> 0xFF6A1B9A to 0xFF6A1B9A
        "밤띠" -> 0xFF5D3A1A to 0xFF5D3A1A
        "밤빨띠" -> 0xFF5D3A1A to 0xFFC62828
        "빨강띠" -> 0xFFC62828 to 0xFFC62828
        "빨검띠" -> 0xFFC62828 to 0xFF141414
        else -> {
            val p = Regex("^(\\d+)품").find(norm(belt))?.groupValues?.get(1)?.toIntOrNull()
            when {
                p == null -> null
                p >= 3 -> 0xFF141414 to 0xFF141414
                else -> 0xFF141414 to 0xFFC62828
            }
        }
    }

    /** 띠 순서 (낮은 띠 → 높은 띠). 모르면 맨 뒤 */
    fun rank(belt: String): Int {
        val b = norm(belt)
        val ci = COLORS.indexOf(b)
        if (ci >= 0) return ci
        val m = Regex("^(\\d+)품 (\\d+)급$").find(b) ?: return 9999
        val p = m.groupValues[1].toInt(); val g = m.groupValues[2].toInt()
        return 100 + p * 100 - g
    }
}

/**
 * 통합관리(심사 앱) → 출결 앱 띠 연동
 * 통합관리가 띠 명단을 비밀번호로 잠가서(AES) 공용 칸에 올려두면, 관장님 폰이 받아서 풀고 원생 띠를 맞춤.
 * 비밀번호는 코드·저장소에 없고 양쪽 기기에 관장님이 직접 넣음.
 */
object BeltSync {
    private const val URL_BLOB =
        "https://taekwondo-wordgame-default-rtdb.asia-southeast1.firebasedatabase.app/apps/pub_beltsync/data/blob.json"

    val status = MutableStateFlow("")
    private var lastStamp = 0L

    private fun sp(ctx: Context) = ctx.getSharedPreferences("jungdo_attend", Context.MODE_PRIVATE)
    fun pass(ctx: Context): String = sp(ctx).getString("beltPass", "") ?: ""
    fun setPass(ctx: Context, v: String) { sp(ctx).edit().putString("beltPass", v.trim()).apply(); lastStamp = 0L }
    fun lastInfo(ctx: Context): String = sp(ctx).getString("beltInfo", "") ?: ""

    /** 받아서 맞추기. force=false 면 통합관리 내용이 그대로일 때는 아무것도 안 함 */
    suspend fun pull(ctx: Context, students: List<Student>, force: Boolean = false): String {
        val pw = pass(ctx)
        if (pw.isBlank()) return "연동 비밀번호를 먼저 넣어 주세요".also { status.value = it }
        if (students.isEmpty()) return "원생 명단을 불러오는 중이에요".also { status.value = it }
        val r = withContext(Dispatchers.IO) {
            runCatching {
                val c = URL(URL_BLOB).openConnection() as HttpURLConnection
                c.connectTimeout = 15000; c.readTimeout = 20000
                val body = c.inputStream.bufferedReader().use { it.readText() }
                c.disconnect()
                if (body.trim() == "null") return@runCatching null
                val o = JSONObject(body)
                val t = o.optLong("t")
                if (!force && t != 0L && t == lastStamp) return@runCatching "SAME"
                val plain = decrypt(pw, o)
                t to JSONObject(plain)
            }
        }
        val res = r.getOrElse { e ->
            val m = if (e is javax.crypto.AEADBadTagException) "비밀번호가 통합관리와 달라요" else "받지 못했어요 (${e.message ?: "인터넷 확인"})"
            status.value = m; return m
        }
        if (res == null) return "통합관리에서 아직 띠를 보내지 않았어요".also { status.value = it }
        if (res == "SAME") return status.value.ifBlank { lastInfo(ctx) }
        @Suppress("UNCHECKED_CAST")
        val (stamp, data) = res as Pair<Long, JSONObject>
        val b = data.optJSONObject("b") ?: JSONObject()
        val map = HashMap<String, String>()
        b.keys().forEach { k -> map[k.trim()] = Belts.norm(b.optString(k)) }

        val alive = students.filter { it.status != StudentStatus.LEFT }
        val changes = alive.mapNotNull { s -> map[s.name.trim()]?.let { nb -> if (nb.isNotBlank() && nb != s.belt) s.id to nb else null } }
        changes.chunked(400).forEach { chunk ->
            val batch = Repo.db.batch()
            chunk.forEach { (id, nb) -> batch.update(Repo.students().document(id), mapOf("belt" to nb)) }
            batch.commit()
        }
        lastStamp = stamp
        val names = alive.map { it.name.trim() }.toSet()
        val noBelt = alive.count { it.name.trim() !in map }
        val extra = map.keys.count { it !in names }
        val time = Repo.timeText(System.currentTimeMillis())
        val msg = "$time 맞춤 · 띠 바뀐 원생 ${changes.size}명" +
            (if (noBelt > 0) " · 통합관리에 없는 원생 ${noBelt}명" else "") +
            (if (extra > 0) " · 출결에 없는 이름 ${extra}명" else "")
        sp(ctx).edit().putString("beltInfo", msg).apply()
        status.value = msg
        return msg
    }

    /** PBKDF2-HMAC-SHA256, 32바이트 (비밀번호는 UTF-8 그대로 → 웹과 똑같음) */
    private fun pbkdf2(pw: ByteArray, salt: ByteArray, n: Int): ByteArray {
        val mac = javax.crypto.Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(pw, "HmacSHA256"))
        mac.update(salt); mac.update(byteArrayOf(0, 0, 0, 1))
        var u = mac.doFinal()
        val out = u.copyOf()
        for (k in 1 until n) { u = mac.doFinal(u); for (j in out.indices) out[j] = (out[j].toInt() xor u[j].toInt()).toByte() }
        return out
    }

    private fun b64(s: String): ByteArray = Base64.getDecoder().decode(s)

    /** 통합관리(웹)와 같은 방식: PBKDF2-SHA256 → AES-256-GCM */
    private fun decrypt(pw: String, o: JSONObject): String {
        val salt = b64(o.getString("s")); val iv = b64(o.getString("i")); val data = b64(o.getString("d"))
        val n = o.optInt("n", 150000)
        val key = pbkdf2(pw.toByteArray(Charsets.UTF_8), salt, n)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        return String(c.doFinal(data), Charsets.UTF_8)
    }
}
