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
        val pair = res as Pair<Long, JSONObject>
        val stamp = pair.first; val data = pair.second
        // 통합관리 목록: [이름 · 학년 · 띠]  (옛 버전은 이름 → 띠)
        data class E(val n: String, val g: String, val b: String)
        val list = ArrayList<E>()
        data.optJSONArray("l")?.let { a -> for (k in 0 until a.length()) a.optJSONObject(k)?.let { o -> list += E(o.optString("n").trim(), o.optString("g").trim(), Belts.norm(o.optString("b"))) } }
        if (list.isEmpty()) data.optJSONObject("b")?.let { b -> b.keys().forEach { k -> list += E(k.trim(), "", Belts.norm(b.optString(k))) } }

        val alive = students.filter { it.status != StudentStatus.LEFT }
        val byName = list.filter { it.b.isNotBlank() }.groupBy { it.n }
        val newBelt = HashMap<String, String>()   // 원생 id → 띠 ("" = 모름, 비움)
        var paired = 0
        alive.filter { !it.beltManual }.groupBy { it.name.trim() }.forEach { (name, kids) ->
            val cands = byName[name] ?: return@forEach
            fun sAge(s: Student) = if (s.curAge > 0) s.curAge else gradeKey(s.grade)
            fun sCat(s: Student) = catOf(s.grade, s.curAge)
            if (kids.size == 1 && cands.size == 1) {
                // 한 명씩이어도 유치부 ↔ 초등부처럼 분명히 다르면 다른 아이로 봄
                val sc = sCat(kids[0]); val ec = catOf(cands[0].g, 0)
                newBelt[kids[0].id] = if (sc >= 0 && ec >= 0 && sc != ec) "" else cands[0].b
                return@forEach
            }
            // 이름이 같은 아이가 여럿: ① 나이·학년이 딱 맞으면 → ② 유치부/초등부/중등… 이 하나만 맞으면
            val left = cands.toMutableList()
            val rest = ArrayList<Student>()
            kids.forEach { s ->
                val sg = sAge(s); val sc = sCat(s)
                var hit = left.filter { sg < 99 && gradeKey(it.g) == sg }
                if (hit.size != 1) hit = left.filter { sc >= 0 && catOf(it.g, 0) == sc }
                if (hit.size == 1) { newBelt[s.id] = hit[0].b; left -= hit[0] } else rest += s
            }
            // ③ 남은 아이는 나이 어린 아이 ↔ 낮은 띠 순서로 짝지음 (숫자가 같을 때만)
            if (rest.isNotEmpty()) {
                if (rest.size == left.size) {
                    rest.sortedBy { sAge(it) }.zip(left.sortedBy { Belts.rank(it.b) }).forEach { (s, e) -> newBelt[s.id] = e.b }
                    paired += rest.size
                } else rest.forEach { newBelt[it.id] = "" }   // 모르면 잘못된 띠를 두지 않고 비움
            }
        }
        val changes = alive.mapNotNull { s -> newBelt[s.id]?.let { nb -> if (nb != s.belt) s.id to nb else null } }
        changes.chunked(400).forEach { chunk ->
            val batch = Repo.db.batch()
            chunk.forEach { (id, nb) -> batch.update(Repo.students().document(id), mapOf("belt" to nb)) }
            batch.commit()
        }
        lastStamp = stamp
        val names = alive.map { it.name.trim() }.toSet()
        val noBelt = alive.count { it.name.trim() !in byName }
        val extra = byName.keys.count { it !in names }
        val manual = alive.count { it.beltManual }
        val time = Repo.timeText(System.currentTimeMillis())
        val msg = "$time 맞춤 · 띠 바뀐 원생 ${changes.size}명" +
            (if (manual > 0) " · 직접 고정 ${manual}명" else "") +
            (if (paired > 0) " · 이름 같은 아이 ${paired}명은 나이 순으로 맞춤(틀리면 직접 고쳐 주세요)" else "") +
            (if (noBelt > 0) " · 통합관리에 없는 원생 ${noBelt}명" else "") +
            (if (extra > 0) " · 출결에 없는 이름 ${extra}명" else "")
        sp(ctx).edit().putString("beltInfo", msg).apply()
        status.value = msg
        return msg
    }

    /** 학년 → 나이 순서 숫자 (6세 → 6, 초1/1학년 → 8, 중1 → 14). 모르면 99 */
    fun gradeKey(raw: String): Int {
        val g = raw.replace(" ", "")
        if (g.isBlank()) return 99
        val n = Regex("(\\d+)").find(g)?.groupValues?.get(1)?.toIntOrNull() ?: return 99
        return when {
            g.contains("세") || g.contains("살") -> n
            g.startsWith("중") -> 13 + n
            g.startsWith("고") -> 16 + n
            g.contains("학년") || g.startsWith("초") -> 7 + n
            else -> 99
        }
    }

    /** 큰 분류: 0 유치부 / 1 초등 / 2 중등 / 3 고등 / 4 성인 / -1 모름 (나이를 알면 나이로) */
    fun catOf(raw: String, age: Int): Int {
        if (age > 0) return when { age <= 7 -> 0; age <= 13 -> 1; age <= 16 -> 2; age <= 19 -> 3; else -> 4 }
        val g = raw.replace(" ", "")
        return when {
            g.isBlank() -> -1
            g.contains("유치") || g.contains("세") || g.contains("살") -> 0
            g.startsWith("중") -> 2
            g.startsWith("고") -> 3
            g.contains("성인") -> 4
            g.startsWith("초") || g.contains("학년") -> 1
            else -> -1
        }
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
