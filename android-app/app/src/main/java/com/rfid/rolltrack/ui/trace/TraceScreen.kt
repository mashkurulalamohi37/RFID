package com.rfid.rolltrack.ui.trace

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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rfid.rolltrack.AppContainer
import com.rfid.rolltrack.ui.common.AppTopBar
import com.rfid.rolltrack.ui.common.CollectScans
import com.rfid.rolltrack.ui.common.containerViewModel
import com.rfid.rolltrack.ui.components.StatusCard
import com.rfid.rolltrack.ui.theme.Palette

@Composable
fun TraceScreen(
    container: AppContainer,
    initialTag: String? = null,
    onBack: () -> Unit
) {
    val vm: TraceViewModel = containerViewModel(container) { TraceViewModel(it) }

    val result by vm.result.collectAsStateWithLifecycle()
    val banner by vm.banner.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()

    var inputCode by rememberSaveable { mutableStateOf(initialTag.orEmpty()) }

    LaunchedEffect(initialTag) {
        if (!initialTag.isNullOrBlank()) {
            vm.trace(initialTag)
        }
    }

    CollectScans(container = container) { vm.onScan(it) }

    Scaffold(
        topBar = { AppTopBar(container = container, title = "Trace Roll", onBack = onBack) },
        containerColor = Palette.Bg
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            banner?.let {
                StatusCard(tone = it.tone, text = it.text, onDismiss = { vm.dismissBanner() })
                Spacer(Modifier.height(8.dp))
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Lookup Roll by RFID / Code", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Palette.Navy)
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = inputCode,
                            onValueChange = { inputCode = it },
                            label = { Text("RFID Tag / Roll No") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = { vm.trace(inputCode) },
                            colors = ButtonDefaults.buttonColors(containerColor = Palette.Navy)
                        ) {
                            Text("Trace")
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            result?.let { res ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Roll: ${res.rollNo.ifBlank { res.tagId }}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Palette.Navy)
                        Text("Status: ${res.status}", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Palette.Ok)
                        res.weightKg?.let { Text("Weight: $it kg", fontSize = 14.sp, color = Palette.Muted) }
                        res.program?.let { Text("Program: $it", fontSize = 14.sp, color = Palette.Muted) }
                        res.supplier?.let { Text("Supplier: $it", fontSize = 14.sp, color = Palette.Muted) }

                        if (res.events.isNotEmpty()) {
                            Spacer(Modifier.height(12.dp))
                            Text("History", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Palette.Navy)
                            Spacer(Modifier.height(6.dp))
                            LazyColumn {
                                items(res.events) { ev ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column {
                                            Text(ev.step, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                            if (ev.detail.isNotBlank()) Text(ev.detail, fontSize = 12.sp, color = Palette.Muted)
                                        }
                                        Text(ev.time, fontSize = 12.sp, color = Palette.Muted)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
