package com.jungdo.attend.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jungdo.attend.data.AwardResult
import com.jungdo.attend.data.Prize
import com.jungdo.attend.data.Prizes
import com.jungdo.attend.data.Repo
import com.jungdo.attend.ui.game.GamePanel
import com.jungdo.attend.ui.game.GameSlot
import com.jungdo.attend.ui.game.LocalGameTheme
import com.jungdo.attend.ui.game.gameMuted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

private fun prizePct(v: Double): String =
    if (v % 1.0 == 0.0) v.toInt().toString() else String.format(Locale.US, "%.2f", v).trimEnd('0').trimEnd('.')

/** 상품 사진 (사진이 없으면 🎁 칸). fit=true 면 잘라내지 않고 통째로 보여줌 */
@Composable
fun PrizeImage(photo: String, size: Dp, fit: Boolean = false) {
    val bmp = remember(photo) { Prizes.decode(photo)?.asImageBitmap() }
    if (bmp == null) {
        Box(Modifier.size(size).clip(RoundedCornerShape(12.dp)).background(Color(0xFFE5E7EB)), contentAlignment = Alignment.Center) {
            Text("🎁", fontSize = (size.value * 0.5f).sp)
        }
        return
    }
    Image(
        bitmap = bmp, contentDescription = null,
        contentScale = if (fit) ContentScale.Fit else ContentScale.Crop,
        modifier = Modifier.size(size).clip(RoundedCornerShape(12.dp)),
    )
}

/** 키오스크 완료 화면: 이번에 당첨된 상품 (사진 + 이름 + 당첨 문구) */
@Composable
fun PrizeRow(a: AwardResult) {
    val w = a.prize ?: return
    val pop = remember(a) { Animatable(0.3f) }
    LaunchedEffect(a) {
        delay(1200)
        pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
    }
    val theme = LocalGameTheme.current
    val popMod = Modifier.graphicsLayer {
        scaleX = pop.value; scaleY = pop.value; alpha = ((pop.value - 0.3f) / 0.7f).coerceIn(0f, 1f)
    }
    if (theme.skin == null) {
        // 기본(파란색) 테마: 예전 모양 그대로
        Column(
            popMod
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFFFEF3C7))
                .padding(horizontal = 28.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("🎉 상품 당첨! 🎉", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color(0xFFB45309))
            Spacer(Modifier.size(8.dp))
            PrizeImage(w.photo, 150.dp, fit = true)
            Spacer(Modifier.size(8.dp))
            Text(w.name, fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827), textAlign = TextAlign.Center)
            if (w.message.isNotBlank()) Text(w.message, fontSize = 20.sp, color = Color(0xFF92400E), textAlign = TextAlign.Center)
        }
    } else {
        // 새싹 농장 테마: 크림 나무 틀 + 나무 슬롯 안에 상품 사진
        GamePanel(popMod, padding = 22.dp) {
            Text("🎉 상품 당첨! 🎉", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = theme.title)
            Spacer(Modifier.size(8.dp))
            GameSlot { PrizeImage(w.photo, 130.dp, fit = true) }
            Spacer(Modifier.size(8.dp))
            Text(w.name, fontSize = 30.sp, fontWeight = FontWeight.Bold, color = theme.text, textAlign = TextAlign.Center)
            if (w.message.isNotBlank()) Text(w.message, fontSize = 20.sp, color = gameMuted(), textAlign = TextAlign.Center)
        }
    }
}

/**
 * 관리자 설정: 상품(마이쮸 등) 등록 · 당첨 확률
 * 상품은 Firestore(prizes)에 올라가서 출석 단말기에도 바로 똑같이 적용돼요.
 */
@Composable
fun PrizeSettings() {
    val list = Prizes.list
    var editing by remember { mutableStateOf<Prize?>(null) }
    var deleting by remember { mutableStateOf<Prize?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle("상품 (마이쮸 등) · 당첨 확률")
        Text("등원·하원을 누를 때마다 보석 뽑기와 함께 상품도 한 번 뽑아요 (하루에 등원 한 번·하원 한 번). 상품마다 따로 뽑아서, 적은 확률이 그 상품의 당첨 확률 그대로예요 (마이쮸 5%면 95%는 꽝). 한 번에 상품은 최대 1개만 줘요.", color = Muted, fontSize = 13.sp)
        Button(onClick = { editing = Prize() }) { Text("+ 상품 추가") }
        if (list.isEmpty()) Text("등록된 상품이 없어요. 상품을 추가하면 뽑기에 들어가요.", color = Muted)
        list.forEach { p ->
            Row(
                Modifier.fillMaxWidth().clickable { editing = p }.padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PrizeImage(p.photo, 56.dp)
                Column(Modifier.weight(1f)) {
                    Text(p.name, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    val st = p.stock
                    Text("당첨 ${prizePct(p.chance)}% · " + (if (st == null) "재고 무제한" else if (st <= 0) "재고 없음 (당첨 안 됨)" else "재고 ${st}개"),
                        color = if (st != null && st <= 0) BadRed else Muted, fontSize = 14.sp)
                }
                Text("수정", color = Navy, fontSize = 14.sp)
            }
        }
    }

    editing?.let { p -> PrizeEditDialog(p, onClose = { editing = null }, onDelete = { deleting = p; editing = null }) }
    deleting?.let { p ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("'${p.name}' 상품을 지울까요?") },
            text = { Text("지우면 더 이상 뽑기에 안 나와요. (이미 당첨된 기록은 그대로 남아요)") },
            confirmButton = { Button(onClick = { Repo.deletePrize(p.id); deleting = null }) { Text("지우기") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("취소") } },
        )
    }
}

@Composable
private fun PrizeEditDialog(p: Prize, onClose: () -> Unit, onDelete: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val isNew = p.id.isBlank()
    var name by remember { mutableStateOf(p.name) }
    var chance by remember { mutableStateOf(if (isNew) "" else prizePct(p.chance)) }
    var message by remember { mutableStateOf(if (isNew) "축하해요! 관장님께 받아가세요 🎁" else p.message) }
    var stock by remember { mutableStateOf(p.stock?.toString() ?: "") }
    var photo by remember { mutableStateOf(p.photo) }
    var err by remember { mutableStateOf("") }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            val enc = withContext(Dispatchers.Default) { Prizes.encodePhoto(ctx, uri) }
            if (enc != null) { photo = enc; err = "" } else { err = "사진을 읽지 못했어요. 다른 사진으로 해 보세요." }
        }
    }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(if (isNew) "상품 추가" else "상품 수정") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.clickable { picker.launch("image/*") }) { PrizeImage(photo, 96.dp) }
                    Column {
                        OutlinedButton(onClick = { picker.launch("image/*") }) { Text(if (photo.isBlank()) "사진 고르기" else "사진 바꾸기") }
                        if (photo.isNotBlank()) TextButton(onClick = { photo = "" }) { Text("사진 빼기", color = BadRed) }
                    }
                }
                OutlinedTextField(name, { name = it; err = "" }, label = { Text("상품 이름 (예: 마이쮸)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(chance, { chance = it.filter { c -> c.isDigit() || c == '.' }; err = "" }, label = { Text("당첨 확률 %") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                Text("다른 상품과 상관없이 이 상품만의 확률이에요 (예: 5 → 95% 꽝)", color = Muted, fontSize = 13.sp)
                OutlinedTextField(message, { message = it }, label = { Text("당첨 문구 (키오스크 화면에 나와요)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(stock, { stock = it.filter(Char::isDigit); err = "" }, label = { Text("재고 개수 (비우면 무제한)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                if (err.isNotBlank()) Text(err, color = BadRed, fontSize = 14.sp)
                if (!isNew) TextButton(onClick = onDelete) { Text("이 상품 지우기", color = BadRed) }
            }
        },
        confirmButton = {
            Button(onClick = {
                val c = chance.toDoubleOrNull()
                val st = if (stock.isBlank()) null else stock.toIntOrNull()
                if (name.isBlank()) {
                    err = "상품 이름을 적어 주세요."
                } else if (c == null || c <= 0.0 || c > 100.0) {
                    err = "당첨 확률은 0보다 크고 100 이하로 적어 주세요."
                } else if (stock.isNotBlank() && st == null) {
                    err = "재고는 숫자로 적어 주세요."
                } else {
                    Repo.savePrize(p.copy(name = name, chance = c, message = message, stock = st, photo = photo))
                    onClose()
                }
            }) { Text("저장") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("취소") } },
    )
}
