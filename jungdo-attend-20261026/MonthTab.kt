package com.jungdo.attend.server

import android.annotation.SuppressLint
import android.webkit.WebView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.jungdo.attend.data.ClassTimes
import com.jungdo.attend.data.Days
import com.jungdo.attend.data.Repo
import com.jungdo.attend.data.Student
import com.jungdo.attend.data.rememberAbsencesRange
import com.jungdo.attend.data.rememberCustomHolidays
import com.jungdo.attend.data.rememberEventsRange
import com.jungdo.attend.report.Exporter
import com.jungdo.attend.report.HtmlExport
import com.jungdo.attend.report.MonthReport
import com.jungdo.attend.report.MonthXlsx
import com.jungdo.attend.report.RiskAnalyzer
import com.jungdo.attend.ui.Muted

/** 월간 출석부: 화면에서 보기 + 엑셀/HTML 저장·공유. 지난 달도 ◀ 로 볼 수 있음 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MonthTab(students: List<Student>) {
    val ctx = LocalContext.current
    val ct = remember { ClassTimes(ctx) }
    var month by remember { mutableStateOf(Days.thisMonth()) }
    val events by rememberEventsRange("$month-01", "$month-31")
    val absences by rememberAbsencesRange("$month-01", "$month-31")
    val custom by rememberCustomHolidays()
    val today = Repo.today()
    val riskFrom = Repo.shiftDay(today, -RiskAnalyzer.LOOKBACK)
    val riskEvents by rememberEventsRange(riskFrom, today)
    val riskAbs by rememberAbsencesRange(riskFrom, Repo.shiftDay(today, 60))

    val sheet = remember(month, students, events, absences, custom) { MonthReport.build(month, students, events, absences, ct) }
    val risks = remember(students, riskEvents, riskAbs, custom, month) {
        if (month == Days.thisMonth()) RiskAnalyzer.analyze(students, riskEvents, riskAbs) else emptyList()
    }
    val html = remember(sheet, risks) { HtmlExport.build(sheet, risks) }
    val baseName = "jungdo-attendance-$month"

    var pending by remember { mutableStateOf<ByteArray?>(null) }
    var msg by remember { mutableStateOf("") }
    val saveXlsx = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(Exporter.XLSX)) { uri ->
        val b = pending; if (uri != null && b != null) { Exporter.write(ctx, uri, b); msg = "엑셀 저장 완료 ✔" }
    }
    val saveHtml = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(Exporter.HTML)) { uri ->
        val b = pending; if (uri != null && b != null) { Exporter.write(ctx, uri, b); msg = "HTML 저장 완료 ✔" }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { month = Days.shiftMonth(month, -1) }) { Text("◀") }
            Text("  ${Days.monthLabel(month)}  ", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            OutlinedButton(onClick = { month = Days.shiftMonth(month, 1) }) { Text("▶") }
            Text("  출석 ${sheet.rows.sumOf { it.attended }}회 · ${sheet.rows.size}명", color = Muted, fontSize = 13.sp)
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(onClick = { pending = MonthXlsx.build(sheet, risks); saveXlsx.launch("$baseName.xlsx") }) { Text("엑셀 저장") }
            Button(onClick = { pending = html.toByteArray(); saveHtml.launch("$baseName.html") }) { Text("HTML 저장") }
            OutlinedButton(onClick = { Exporter.share(ctx, MonthXlsx.build(sheet, risks), "$baseName.xlsx", Exporter.XLSX) }) { Text("엑셀 보내기") }
            OutlinedButton(onClick = { Exporter.share(ctx, html.toByteArray(), "$baseName.html", Exporter.HTML) }) { Text("HTML 보내기") }
        }
        if (msg.isNotBlank()) Text(msg, color = com.jungdo.attend.ui.OkGreen, modifier = Modifier.padding(horizontal = 12.dp))
        AndroidView(
            factory = { c ->
                WebView(c).apply {
                    settings.builtInZoomControls = true
                    settings.displayZoomControls = false
                    settings.useWideViewPort = true
                    settings.loadWithOverviewMode = false
                }
            },
            update = { wv ->
                if (wv.tag != html.hashCode()) {
                    wv.tag = html.hashCode()
                    wv.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
                }
            },
            modifier = Modifier.fillMaxWidth().weight(1f),
        )
    }
}
