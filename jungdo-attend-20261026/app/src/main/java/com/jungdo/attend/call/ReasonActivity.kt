package com.jungdo.attend.call

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import com.google.firebase.auth.FirebaseAuth
import com.jungdo.attend.ui.AbsenceForm
import com.jungdo.attend.ui.Navy

/**
 * 통화가 끝나면 뜨는 '결석 사유 입력' 창
 *  - 앱에서 건 전화: 그 아이(+형제)가 선택된 상태
 *  - 일반 전화: 학부모 번호로 찾은 아이(+형제)가 선택된 상태
 */
class ReasonActivity : ComponentActivity() {
    companion object {
        fun intent(ctx: Context, ids: List<String>, number: String, header: String, missed: Boolean): Intent =
            Intent(ctx, ReasonActivity::class.java)
                .putExtra("ids", ids.toTypedArray())
                .putExtra("num", number)
                .putExtra("header", header)
                .putExtra("missed", missed)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }

    /** 창이 이미 떠 있는데 새 문자·전화가 오면 새 내용으로 다시 그림 */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        recreate()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setFinishOnTouchOutside(false)
        if (FirebaseAuth.getInstance().currentUser == null) { finish(); return }
        val ids = intent.getStringArrayExtra("ids")?.toList() ?: emptyList()
        val number = intent.getStringExtra("num") ?: ""
        val header = intent.getStringExtra("header") ?: "통화가 끝났어요"
        val missed = intent.getBooleanExtra("missed", false)
        val smsBody = intent.getStringExtra("smsBody")
        val inboxId = intent.getStringExtra("inboxId")
        val addNumber = intent.getStringExtra("addNumber")
        val replyNumber = intent.getStringExtra("replyNumber")
        val replyLabel = intent.getStringExtra("replyLabel") ?: ""
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Navy)) {
                AbsenceForm(
                    preselected = ids,
                    header = header,
                    callNumber = number.ifBlank { null },
                    missedCall = missed,
                    smsBody = smsBody,
                    inboxId = inboxId,
                    addNumber = addNumber,
                    replyNumber = replyNumber,
                    replyLabel = replyLabel,
                    onDone = { finish() },
                )
            }
        }
    }
}
