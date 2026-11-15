package com.rfid.rolltrack.ui.diagnostics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rfid.rolltrack.AppContainer
import com.rfid.rolltrack.scanner.ScanEvent
import com.rfid.rolltrack.scanner.TagIdNormalizer
import com.rfid.rolltrack.ui.common.AppTopBar
import com.rfid.rolltrack.ui.common.CollectScans
import com.rfid.rolltrack.ui.theme.Palette

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

    CollectScans(container = container) { event ->
        when (event) {
            is ScanEvent.Tag -> {
                val formats = TagIdNormalizer.allFormats(event.tagId)
                reads.add(
                    0,
                    DiagnosticRead(
                        raw = event.tagId,
                        source = event.source.label,
                        canonical = event.tagId,
                        formats = formats
                    )
                )
                container.feedback.tagAccepted()
            }
            is ScanEvent.Code -> {
                reads.add(
                    0,
                    DiagnosticRead(
                        raw = event.raw,
                        source = event.source.label,
                        canonical = "N/A",
                        formats = emptyList()
                    )
                )
                container.feedback.tagAccepted()
            }
        }
    }

    Scaffold(
        topBar = { AppTopBar(container = container, title = "Diagnostics & Tag Tester", onBack = onBack) },
        containerColor = Palette.Bg
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Tag Reader Verification", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Palette.Navy)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Tap any tag on built-in NFC or USB reader. Confirm both readers yield the identical Canonical ID.",
                        fontSize = 13.sp,
                        color = Palette.Muted
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(reads) { r ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(r.canonical, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Palette.Navy)
                                Text(r.source, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Palette.Ok)
                            }
                            Spacer(Modifier.height(6.dp))
                            for ((label, valStr) in r.formats) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(label, fontSize = 12.sp, color = Palette.Muted)
                                    Text(valStr, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
