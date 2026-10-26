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
import com.jungdo.attend.data.GRADE_ORDER
import com.jungdo.attend.data.Repo
import com.jungdo.attend.data.Student
import com.jungdo.attend.data.StudentStatus

/** 원생 추가/수정 (관장님 폰·키오스크 관리자 공용) */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StudentEditDialog(s: Student, all: List<Student>, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(s.name) }
    var grade by remember { mutableStateOf(s.grade) }
    var status by remember { mutableStateOf(s.status) }
    var reason by remember { mutableStateOf(s.statusReason) }
    var demo by remember { mutableStateOf(s.demo) }
    val phones = remember { (0 until 3).map { i -> mutableStateOf(s.phones.getOrNull(i)?.let(Repo::prettyPhone) ?: "") } }
    val labels = remember { (0 until 3).map { i -> mutableStateOf(s.phoneLabels.getOrNull(i) ?: listOf("어머님", "아버님", "할머님")[i]) } }
    val currentSibs = all.filter { s.siblingKey.isNotBlank() && it.siblingKey == s.siblingKey && it.id != s.id }.map { it.name }
    var sibText by remember { mutableStateOf(currentSibs.joinToString(", ")) }
    var memo by remember { mutableStateOf(s.memo) }
    var attendNo by remember { mutableStateOf(s.attendNo) }
    var confirmDel by remember { mutableStateOf(false) }
    val grades = remember(all) { (all.map { it.grade } + GRADE_ORDER.take(9)).filter { it.isNotBlank() }.distinct() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (s.id.isBlank()) "원생 추가" else "원생 수정") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("이름") }, singleLine = true)
                OutlinedTextField(grade, { grade = it }, label = { Text("학년 (예: 2학년)") }, singleLine = true)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    grades.take(10).forEach { g -> Pill(g, grade == g, 13.sp) { grade = g } }
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
                val updated = s.copy(
                    name = name.trim(), grade = grade.trim(), status = status, statusReason = if (status == StudentStatus.ACTIVE) "" else reason.trim(),
                    demo = demo, phones = ps, phoneLabels = ls, memo = memo.trim(), leftMonth = leftMonth, attendNo = attendNo.trim(),
                    siblingKey = if (sibNames.isEmpty()) "" else s.siblingKey,
                )
                Repo.saveStudent(updated)
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
