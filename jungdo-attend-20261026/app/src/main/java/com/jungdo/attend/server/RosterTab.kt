package com.jungdo.attend.server

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jungdo.attend.data.Student
import com.jungdo.attend.data.StudentStatus
import com.jungdo.attend.importer.ContactRow
import com.jungdo.attend.importer.ContactTable
import com.jungdo.attend.importer.ImportFiles
import com.jungdo.attend.importer.Loaded
import com.jungdo.attend.importer.ImportPlan
import com.jungdo.attend.importer.RosterParser
import com.jungdo.attend.importer.RosterResult
import com.jungdo.attend.importer.VContact
import com.jungdo.attend.importer.VcfParser
import com.jungdo.attend.importer.XSheet
import com.jungdo.attend.report.Exporter
import com.jungdo.attend.report.ImportTemplate
import com.jungdo.attend.ui.BadRed
import com.jungdo.attend.ui.InBlue
import com.jungdo.attend.ui.Muted
import com.jungdo.attend.ui.OkGreen
import com.jungdo.attend.ui.Pill
import com.jungdo.attend.ui.SectionTitle
import com.jungdo.attend.ui.StudentEditDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 명단·형제: 수련생 현황 엑셀 + 연락처(vcf) 불러오기 → 재원/퇴관 자동 정리, 형제 번호 공유
 * 매달(또는 수시로) 최신 현황표를 올리면 현황표에 없는 아이는 이번 달부터 퇴관 처리
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RosterTab(students: List<Student>) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var sheets by remember { mutableStateOf<List<XSheet>?>(null) }
    var sheetIdx by remember { mutableStateOf(0) }
    var contacts by remember { mutableStateOf<List<VContact>?>(null) }
    var table by remember { mutableStateOf<List<ContactRow>?>(null) }
    var error by remember { mutableStateOf("") }
    var applied by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<Student?>(null) }
    var query by remember { mutableStateOf("") }
    var showLeft by remember { mutableStateOf(false) }

    val roster: RosterResult? = remember(sheets, sheetIdx) {
        sheets?.let { runCatching { RosterParser.parse(it, sheetIdx) }.onFailure { e -> error = e.message ?: "읽기 실패" }.getOrNull() }
    }
    val plan: ImportPlan? = remember(roster, contacts, table, students) {
        if (roster == null && contacts == null && table == null) null else ImportPlan.build(students, roster, contacts, table)
    }

    var loadedInfo by remember { mutableStateOf<List<String>>(emptyList()) }

    // 파일을 몇 개든 한꺼번에 고르면, 내용을 보고 현황 / 연락처 명단 / 휴대폰 연락처(vcf) / 신규 원생 양식을 알아서 구분
    val pickFiles = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) scope.launch {
            error = ""; applied = ""
            val info = mutableListOf<String>(); val problems = mutableListOf<String>()
            for (uri in uris) {
                val name = ImportFiles.displayName(ctx, uri)
                val bytes = withContext(Dispatchers.IO) { runCatching { ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes() } }.getOrNull() }
                if (bytes == null) { problems += "$name: 파일을 열지 못했어요"; continue }
                when (val r = withContext(Dispatchers.Default) { ImportFiles.detect(name, bytes) }) {
                    is Loaded.Roster -> { sheets = r.sheets; sheetIdx = RosterParser.gradeSheetIndex(r.sheets); info += "✔ $name → 수련생 현황" }
                    is Loaded.Contacts -> { contacts = contacts.orEmpty() + r.list; info += "✔ $name → 휴대폰 연락처 ${r.list.size}개" }
                    is Loaded.Table -> { table = table.orEmpty() + r.rows; info += "✔ $name → 연락처 명단·양식 ${r.rows.size}줄" }
                    is Loaded.Unknown -> problems += "$name: ${r.why}"
                }
            }
            loadedInfo = info
            error = problems.joinToString("\n")
        }
    }

    val shownStudents = students.filter { (showLeft || it.status != StudentStatus.LEFT) && (query.isBlank() || it.name.contains(query.trim())) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionTitle("수련생 현황 불러오기")
                    Text("파일을 한꺼번에 골라 올리면 앱이 내용을 보고 알아서 구분해요 (현황 .xlsx / 연락처 .xls·.xlsx / 휴대폰 연락처 .vcf / 신규 원생 양식). 몇 번에 나눠 올려도 돼요.\n" +
                        "• 수련생 현황: 학년별 명단·시범단(회색)·휴관·형제명단을 읽고, 현황표에 없는 아이는 이번 달부터 퇴관 처리해요 (지난 기록은 남음).\n" +
                        "• 연락처 명단·휴대폰 연락처: 학부모 번호·출결번호·학교를 넣고 형제는 번호를 공유해요.\n" +
                        "• 현황 없이 연락처 명단/양식만 올리면: 기존 원생은 그대로 두고 새 이름만 추가해요.", color = Muted, fontSize = 13.sp)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { pickFiles.launch(arrayOf("*/*")) }) { Text("📂 파일 올리기 (여러 개 가능)") }
                        OutlinedButton(onClick = { Exporter.share(ctx, ImportTemplate.build(), "정도출결_신규원생_양식.xlsx", Exporter.XLSX, "양식 보내기") }) { Text("📄 양식 받기") }
                        if (sheets != null || contacts != null || table != null) {
                            OutlinedButton(onClick = { sheets = null; contacts = null; table = null; loadedInfo = emptyList(); error = "" }) { Text("선택 지우기") }
                        }
                    }
                    sheets?.let { list ->
                        Text("시트 선택", fontWeight = FontWeight.Bold)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            list.forEachIndexed { i, s -> Pill(s.name, i == sheetIdx, 14.sp) { sheetIdx = i; error = "" } }
                        }
                    }
                    loadedInfo.forEach { Text(it, color = OkGreen, fontSize = 14.sp) }
                    if (error.isNotBlank()) Text(error, color = BadRed)
                    plan?.let { p ->
                        HorizontalDivider()
                        Text("미리보기", fontWeight = FontWeight.Bold)
                        Text(p.summary(), fontSize = 14.sp)
                        Button(onClick = {
                            p.apply()
                            applied = "적용했어요 ✔"
                            sheets = null; contacts = null; table = null; loadedInfo = emptyList()
                        }) { Text("이대로 적용하기") }
                    }
                    if (applied.isNotBlank()) Text(applied, color = OkGreen, fontWeight = FontWeight.Bold)
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(query, { query = it }, label = { Text("이름 검색") }, singleLine = true, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = { editing = Student() }) { Text("+ 추가") }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                Text("재원 ${students.count { it.isActive }} · 휴관 ${students.count { it.status == StudentStatus.LEAVE }} · 퇴관 ${students.count { it.status == StudentStatus.LEFT }}", color = Muted, modifier = Modifier.weight(1f))
                Pill(if (showLeft) "퇴관자 숨기기" else "퇴관자 보기", showLeft, 13.sp) { showLeft = !showLeft }
            }
        }
        items(shownStudents, key = { it.id }) { s ->
            val sibs = students.filter { s.siblingKey.isNotBlank() && it.siblingKey == s.siblingKey && it.id != s.id }
            Column(Modifier.fillMaxWidth().clickable { editing = s }.padding(vertical = 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(s.name, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = if (s.isActive) Color.Unspecified else Muted)
                    Text("  ${s.grade}", color = Muted)
                    if (s.demo) Text("  ★시범단", color = InBlue, fontSize = 13.sp)
                    if (!s.isActive) Text("  ${StudentStatus.label(s.status)}${if (s.statusReason.isNotBlank()) "(${s.statusReason})" else ""}", color = BadRed, fontSize = 13.sp)
                    Spacer(Modifier.weight(1f))
                    if (s.attendNo.isNotBlank()) Text("#${s.attendNo}  ", color = Muted, fontSize = 13.sp)
                    Text(if (s.phones.isEmpty()) "번호 없음" else "📞 ${s.phones.size}", color = if (s.phones.isEmpty()) BadRed else Muted, fontSize = 13.sp)
                }
                if (sibs.isNotEmpty()) Text("형제: " + sibs.joinToString(", ") { it.name }, color = InBlue, fontSize = 13.sp)
            }
            HorizontalDivider()
        }
    }
    editing?.let { StudentEditDialog(it, students) { editing = null } }
}
