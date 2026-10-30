package com.jungdo.attend.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jungdo.attend.data.Days
import com.jungdo.attend.data.GradeCalc
import com.jungdo.attend.data.Rank
import com.jungdo.attend.data.Repo
import com.jungdo.attend.data.Student
import com.jungdo.attend.data.StudentStatus

/** 원생 추가/수정 (관장님 폰·키오스크 관리자 공용) */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StudentEditDialog(s: Student, all: List<Student>, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(s.name) }
    var grade by remember { mutableStateOf(s.grade) }
    var age by remember { mutableStateOf(if (s.curAge > 0) s.curAge.toString() else "") }
    var gender by remember { mutableStateOf(s.gender) }
    var status by remember { mutableStateOf(s.status) }
    var reason by remember { mutableStateOf(s.statusReason) }
    var demo by remember { mutableStateOf(s.demo) }
    val phones = remember { (0 until 3).map { i -> mutableStateOf(s.phones.getOrNull(i)?.let(Repo::prettyPhone) ?: "") } }
    val labels = remember { (0 until 3).map { i -> mutableStateOf(s.phoneLabels.getOrNull(i) ?: listOf("어머님", "아버님", "할머님")[i]) } }
    val currentSibs = all.filter { s.siblingKey.isNotBlank() && it.siblingKey == s.siblingKey && it.id != s.id }.map { it.name }
    var sibText by remember { mutableStateOf(currentSibs.joinToString(", ")) }
    var memo by remember { mutableStateOf(s.memo) }
    var attendNo by remember { mutableStateOf(s.attendNo) }
    var expText by remember { mutableStateOf(s.exp.toString()) }
    var confirmDel by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (s.id.isBlank()) "원생 추가" else "원생 수정") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("이름") }, singleLine = true)
                OutlinedTextField(grade, { v -> grade = v; GradeCalc.ageOf(v).let { a -> if (a > 0) age = a.toString() } },
                    label = { Text("학년 (예: 2학년, 중1, 고2)") }, singleLine = true)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    GradeCalc.PICK.forEach { g ->
                        Pill(g, grade == g, 13.sp) {
                            grade = g
                            val a = GradeCalc.ageOf(g)
                            if (g == "유치부" && (age.toIntOrNull() ?: 0) > GradeCalc.KINDER_MAX) age = ""
                            if (a > 0) age = a.toString() else if (g == "성인" && (age.toIntOrNull() ?: 0) < GradeCalc.ADULT_START) age = GradeCalc.ADULT_START.toString()
                        }
                    }
                }
                OutlinedTextField(age, { v -> age = v.filter(Char::isDigit).take(2); age.toIntOrNull()?.let { a -> if (a > 0) grade = GradeCalc.labelOf(a) } },
                    label = { Text("나이 (매년 1살씩 올라가요)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                val ageNow = age.toIntOrNull() ?: 0
                if (ageNow > 0) Text("올해 ${GradeCalc.labelOf(ageNow)} → 내년 ${GradeCalc.labelOf(ageNow + 1)}", color = Muted, fontSize = 12.sp)
                else Text("나이를 넣으면 해마다 학년이 저절로 올라가요. 유치부(7세 이하)는 나이를 꼭 넣어 주세요. 8세=1학년, 14세=중1, 17세=고1, 20세=성인.", color = Muted, fontSize = 12.sp)
                Text("성별 (장비 성별이 다르면 팔 수 있어요)")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("남", "여").forEach { g -> Pill(g, gender == g, 15.sp) { gender = if (gender == g) "" else g } }
                }
                Text("상태")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(StudentStatus.ACTIVE, StudentStatus.LEAVE, StudentStatus.LEFT).forEach { st ->
                        Pill(StudentStatus.label(st), status == st, 15.sp) { status = st }
                    }
                }
                if (status != StudentStatus.ACTIVE) OutlinedTextField(reason, { reason = it }, label = { Text("${StudentStatus.label(status)} 사유") })
                if (status == StudentStatus.LEFT) Text("퇴관은 이번 달부터 출석체크·출석부에서 빠지고, 지난달 기록은 그대로 남아요.", color = Muted, fontSize = 12.sp)
                Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(demo, { demo = it }); Text("시범단") }
                Text("학부모 연락처 (첫 번째가 대표 번호 · 문자 받는 번호)")
                (0 until 3).forEach { i ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(labels[i].value, { labels[i].value = it }, singleLine = true, modifier = Modifier.width(90.dp))
                        OutlinedTextField(phones[i].value, { phones[i].value = it }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.width(170.dp))
                    }
                }
                OutlinedTextField(attendNo, { attendNo = it.filter(Char::isDigit) }, label = { Text("출결번호 (단말기에서 번호로 출석)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                if (s.id.isNotBlank()) {
                    OutlinedTextField(expText, { expText = it.filter(Char::isDigit).take(6) }, label = { Text("경험치 (고칠 때만 · 최고 ${Rank.MAX_EXP})") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    Text("지금 계급 Lv.${Rank.levelOf(expText.toIntOrNull() ?: s.exp)} · 연속 출석 ${s.streak}일", color = Muted, fontSize = 12.sp)
                }
                OutlinedTextField(sibText, { sibText = it }, label = { Text("형제 이름 (쉼표로 구분)") }, singleLine = true)
                Text("형제끼리는 학부모 번호가 똑같이 합쳐져요.", color = Muted, fontSize = 12.sp)
                OutlinedTextField(memo, { memo = it }, label = { Text("메모") })
            }
        },
        confirmButton = {
            Button(enabled = name.isNotBlank(), onClick = {
                val ps = mutableListOf<String>(); val ls = mutableListOf<String>()
                (0 until 3).forEach { i -> val p = Repo.normalizePhone(phones[i].value); if (p.length >= 9 && p !in ps) { ps += p; ls += labels[i].value.ifBlank { "보호자" } } }
                val leftMonth = when {
                    status == StudentStatus.LEFT && s.status != StudentStatus.LEFT -> Days.thisMonth()
                    status == StudentStatus.LEFT -> s.leftMonth.ifBlank { Days.thisMonth() }
                    else -> ""
                }
                val sibNames = sibText.split(',', ' ', '/').map { it.trim() }.filter { it.isNotBlank() }
                val edited = s.copy(
                    name = name.trim(), status = status, statusReason = if (status == StudentStatus.ACTIVE) "" else reason.trim(),
                    demo = demo, phones = ps, phoneLabels = ls, memo = memo.trim(), leftMonth = leftMonth, attendNo = attendNo.trim(),
                    siblingKey = if (sibNames.isEmpty()) "" else s.siblingKey,
                )
                // 나이가 있으면 나이로 학년을 정하고(해마다 자동 진급), 없으면 적은 학년 글자를 그대로 둠
                val ageNum = age.toIntOrNull() ?: 0
                val updated = (if (ageNum > 0) edited.withAge(ageNum) else GradeCalc.applyLabel(edited, grade)).copy(gender = gender)
                Repo.saveStudent(updated)
                if (s.id.isNotBlank() && gender != s.gender) Repo.setGender(s.id, gender)
                if (s.id.isNotBlank()) expText.toIntOrNull()?.let { e -> if (e != s.exp) Repo.setExp(s.id, e) }
                if (updated.id.isNotBlank() && sibNames.isNotEmpty()) {
                    val sibs = all.filter { it.name in sibNames && it.id != s.id && it.status != StudentStatus.LEFT }
                    Repo.linkSiblings(listOf(updated) + sibs)
                }
                onDismiss()
            }) { Text("저장") }
        },
        dismissButton = {
            Row {
                if (s.id.isNotBlank()) TextButton(onClick = { if (confirmDel) { Repo.deleteStudent(s.id); onDismiss() } else confirmDel = true }) {
                    Text(if (confirmDel) "기록까지 완전 삭제" else "삭제", color = BadRed)
                }
                TextButton(onClick = onDismiss) { Text("취소") }
            }
        },
    )
}
