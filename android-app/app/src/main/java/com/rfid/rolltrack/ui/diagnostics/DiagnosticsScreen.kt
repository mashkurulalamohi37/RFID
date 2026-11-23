package com.rfid.rolltrack.ui.diagnostics

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rfid.rolltrack.AppContainer
import com.rfid.rolltrack.ServerHealth
import com.rfid.rolltrack.data.remote.ApiCall
import com.rfid.rolltrack.data.remote.ApiLog
import com.rfid.rolltrack.scanner.ScanEvent
import com.rfid.rolltrack.scanner.TagIdNormalizer
import com.rfid.rolltrack.ui.common.AppTopBar
import com.rfid.rolltrack.ui.common.CollectScans
import com.rfid.rolltrack.ui.theme.Palette
import kotlinx.coroutines.launch

data class DiagnosticRead(
    val raw: String,
    val source: String,
    val canonical: String,
    val formats: List<Pair<String, String>>,
    val timestamp: Long = System.currentTimeMillis()
)

@Composable
fun DiagnosticsScreen(
    container: AppContainer,
    onBack: () -> Unit
) {
    val reads = remember { mutableStateListOf<DiagnosticRead>() }
    val calls by ApiLog.calls.collectAsStateWithLifecycle()
    val health by container.serverHealth.state.collectAsStateWithLifecycle()
    val lastChecked by container.serverHealth.lastChecked.collectAsStateWithLifecycle()
    val pendingCount by container.scanRepository.observePendingCount().collectAsStateWithLifecycle(0)
    var actionMsg by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    CollectScans(container = container) { event ->
        when (event) {
            is ScanEvent.Tag -> {
                reads.add(0, DiagnosticRead(event.tagId, event.source.label, event.tagId, TagIdNormalizer.allFormats(event.tagId)))
                container.feedback.tagAccepted()
            }
            is ScanEvent.Code -> {
                reads.add(0, DiagnosticRead(event.raw, event.source.label, "N/A", emptyList()))
                container.feedback.tagAccepted()
            }
        }
    }

    Scaffold(
        topBar = { AppTopBar(container = container, title = "Diagnostics", onBack = onBack) },
        containerColor = Palette.Bg
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // ---------- Server ----------
            item {
                SectionCard {
                    Text("ERP Server", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Palette.Navy)
                    Spacer(Modifier.height(4.dp))
                    val (txt, col) = when (val h = health) {
                        is ServerHealth.State.Up -> "Reachable · ${h.ms} ms" to Palette.Ok
                        is ServerHealth.State.Down -> "Unreachable · ${h.reason}" to Palette.Bad
                        ServerHealth.State.NoInternet -> "No internet on device" to Palette.Bad
                        ServerHealth.State.Unknown -> "Not checked yet" to Palette.Muted
                    }
                    Text(txt, color = col, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text(container.serverHealth.pingUrl(), color = Palette.Muted, fontSize = 11.sp)
                    if (lastChecked > 0) Text("Last check ${ApiLog.fmt(lastChecked)}", color = Palette.Muted, fontSize = 11.sp)
                    Spacer(Modifier.height(8.dp))
                    Row {
                        Button(
                            enabled = !busy,
                            onClick = {
                                busy = true
                                scope.launch {
                                    val s = container.serverHealth.check()
                                    actionMsg = "Ping: ${s.label}" + when (s) {
                                        is ServerHealth.State.Up -> " (${s.ms} ms)"
                                        is ServerHealth.State.Down -> " - ${s.reason}"
                                        else -> ""
                                    }
                                    busy = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Palette.Navy)
                        ) { Text("Ping server") }
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(
                            enabled = !busy && pendingCount > 0,
                            onClick = {
                                busy = true
                                scope.launch {
                                    actionMsg = container.scanRepository.retryPending()
                                    busy = false
                                }
                            }
                        ) { Text("Retry pending ($pendingCount)") }
                    }
                    actionMsg?.let {
                        Spacer(Modifier.height(6.dp))
                        Text(it, fontSize = 13.sp, color = Palette.Navy, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            // ---------- API call log ----------
            item {
                SectionCard {
                    Row(Modifier.fillMaxWidth()) {
                        Text("API calls (${calls.size})", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Palette.Navy, modifier = Modifier.weight(1f))
                        Text("Copy all", color = Palette.Navy, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable { clipboard.setText(AnnotatedString(ApiLog.dump())); actionMsg = "API log copied to clipboard" })
                        Spacer(Modifier.width(16.dp))
                        Text("Clear", color = Palette.Bad, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable { ApiLog.clear() })
                    }
                    Text("Newest first. Tap a call for request/response. Also in Logcat: adb logcat -s RollTrack:*", fontSize = 11.sp, color = Palette.Muted)
                }
            }
            if (calls.isEmpty()) {
                item { Text("No API calls yet. Log in or submit a batch.", fontSize = 13.sp, color = Palette.Muted) }
            }
            items(calls, key = { it.id }) { c -> ApiCallRow(c) }

            // ---------- Tag reader ----------
            item {
                SectionCard {
                    Text("Tag Reader Verification", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Palette.Navy)
                    Spacer(Modifier.height(4.dp))
                    Text("Tap any tag on built-in NFC or USB reader. Confirm both readers yield the identical Canonical ID.", fontSize = 13.sp, color = Palette.Muted)
                }
            }
            items(reads) { r ->
                SectionCard {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(r.canonical, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Palette.Navy)
                        Text(r.source, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Palette.Ok)
                    }
                    Spacer(Modifier.height(6.dp))
                    for ((label, valStr) in r.formats) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(label, fontSize = 12.sp, color = Palette.Muted)
                            Text(valStr, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(Modifier.padding(14.dp)) { content() }
    }
}

@Composable
private fun ApiCallRow(c: ApiCall) {
    var open by remember { mutableStateOf(false) }
    val color = when {
        c.error != null || c.verdict == "REJECTED" || (c.httpCode ?: 0) !in 200..299 -> Palette.Bad
        c.verdict == "UNCERTAIN" -> Palette.Warn
        else -> Palette.Ok
    }
    val endpoint = c.url.substringAfter("/ords/").substringBefore('?')
    Card(
        modifier = Modifier.fillMaxWidth().clickable { open = !open },
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth()) {
                Text("${c.method} $endpoint", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Palette.Navy, modifier = Modifier.weight(1f))
                Text(c.httpCode?.toString() ?: "ERR", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = color)
            }
            Text(
                "${ApiLog.fmt(c.at)} · ${c.durationMs} ms" + (c.verdictMessage?.let { " · $it" } ?: c.error?.let { " · $it" } ?: ""),
                fontSize = 11.sp, color = color
            )
            if (open) {
                Spacer(Modifier.height(6.dp))
                Text(c.toText(), fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = Palette.Navy)
            }
        }
    }
}
