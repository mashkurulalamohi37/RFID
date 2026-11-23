package com.rfid.rolltrack.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rfid.rolltrack.data.SubmitSummary
import com.rfid.rolltrack.ui.theme.Palette

/**
 * Shows exactly what the server said after a submit: verdict, message, HTTP code, raw body, with copy.
 */
@Composable
fun SubmitResultCard(summary: SubmitSummary, onDismiss: () -> Unit) {
    val (title, bg, fg) = when (summary.kind) {
        SubmitSummary.Kind.ALL_ACCEPTED -> Triple("✔ Server accepted", Palette.OkBg, Palette.Ok)
        SubmitSummary.Kind.PARTIAL -> Triple("⚠ Partly accepted", Palette.WarnBg, Palette.Warn)
        SubmitSummary.Kind.ALL_REJECTED -> Triple("✖ Server rejected", Palette.BadBg, Palette.Bad)
        SubmitSummary.Kind.QUEUED -> Triple("⏳ Not sent – queued", Palette.WarnBg, Palette.Warn)
        SubmitSummary.Kind.UNCERTAIN -> Triple("? Sent – not confirmed", Palette.WarnBg, Palette.Warn)
    }
    var expanded by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val detail = buildString {
        append(title).append('\n').append(summary.message).append('\n')
        if (summary.httpCode != 0) append("HTTP ").append(summary.httpCode).append('\n')
        if (summary.errorCode != null) append("Code ").append(summary.errorCode).append('\n')
        if (summary.raw.isNotBlank()) append("Response: ").append(summary.raw)
    }

    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = bg),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Row(Modifier.fillMaxWidth()) {
                Text(title, color = fg, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.weight(1f))
                Text("✕", color = fg, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { onDismiss() })
            }
            Spacer(Modifier.height(4.dp))
            Text(summary.message, color = fg, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            val meta = listOfNotNull(
                summary.httpCode.takeIf { it != 0 }?.let { "HTTP $it" },
                summary.errorCode,
                if (summary.accepted + summary.rejected > 0) "ok ${summary.accepted} / failed ${summary.rejected}" else null
            ).joinToString("  ·  ")
            if (meta.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(meta, color = Palette.Muted, fontSize = 12.sp)
            }
            Spacer(Modifier.height(6.dp))
            Row {
                if (summary.raw.isNotBlank()) {
                    Text(
                        if (expanded) "Hide server response" else "Show server response",
                        color = Palette.Navy, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { expanded = !expanded }
                    )
                    Spacer(Modifier.width(16.dp))
                }
                Text(
                    "Copy", color = Palette.Navy, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { clipboard.setText(AnnotatedString(detail)) }
                )
            }
            if (expanded) {
                Spacer(Modifier.height(4.dp))
                Text(summary.raw.take(3000), fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Palette.Navy)
            }
        }
    }
}
