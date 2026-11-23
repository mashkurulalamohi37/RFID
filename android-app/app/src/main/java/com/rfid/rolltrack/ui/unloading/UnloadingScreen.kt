package com.rfid.rolltrack.ui.unloading

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.rfid.rolltrack.ui.common.ScanBottomBar
import com.rfid.rolltrack.ui.common.StepScaffold
import com.rfid.rolltrack.ui.common.containerViewModel
import com.rfid.rolltrack.ui.components.ConfirmDialog
import com.rfid.rolltrack.ui.components.CounterPill
import com.rfid.rolltrack.ui.components.StatusCard
import com.rfid.rolltrack.ui.theme.Palette

@Composable
fun UnloadingScreen(
    container: AppContainer,
    onBack: () -> Unit
) {
    val vm: UnloadingViewModel = containerViewModel(container) { UnloadingViewModel(it) }

    val header by vm.header.collectAsStateWithLifecycle()
    val items by vm.items.collectAsStateWithLifecycle()
    val banner by vm.banner.collectAsStateWithLifecycle()
    val summary by vm.summary.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()

    var showRfidPrompt by rememberSaveable { mutableStateOf(false) }
    var showTypeDialog by rememberSaveable { mutableStateOf(false) }
    var showConfirmFinish by rememberSaveable { mutableStateOf(false) }

    val loadingRef = header?.loadingRef.orEmpty()
    val expected = header?.expected
    val expectedIds = expected?.tagIds?.toSet() ?: emptySet()
    val scannedIds = items.map { it.tagId }.toSet()

    val matchedCount = scannedIds.intersect(expectedIds).size
    val extraCount = (scannedIds - expectedIds).size
    val missingCount = (expectedIds - scannedIds).size

    StepScaffold(
        container = container,
        title = "Step 5 - Unloading Reconciliation",
        busy = busy,
        onBack = onBack,
        onScan = { vm.onScan(it) },
        controls = {
            banner?.let {
                StatusCard(tone = it.tone, text = it.text, onDismiss = { vm.dismissBanner() })
                Spacer(Modifier.height(8.dp))
            }
            summary?.let {
                com.rfid.rolltrack.ui.components.SubmitResultCard(summary = it, onDismiss = { vm.dismissSummary() })
                Spacer(Modifier.height(8.dp))
            }

            if (loadingRef.isBlank()) {
                // Trip selection card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Scan roll RFIDs to unload", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Palette.Navy)
                            Text("${items.size} rolls", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Palette.Navy)
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { showTypeDialog = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Type loading ref (optional)")
                        }
                    }
                }
            } else {
                // Trip reconciliation summary
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "Vehicle Manifest: $loadingRef",
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Palette.Navy
                                )
                                Text(
                                    "${expectedIds.size} rolls expected",
                                    fontSize = 13.sp,
                                    color = Palette.Muted
                                )
                            }
                            Text(
                                "${items.size} unloaded",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Palette.Navy
                            )
                        }

                        Spacer(Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CounterPill("Matched", matchedCount, Palette.Ok)
                            CounterPill("Missing", missingCount, Palette.Warn)
                            CounterPill("Extra", extraCount, Palette.Bad)
                        }
                    }
                }
            }
        },
        list = {
            run {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(items, key = { it.id }) { item ->
                        val isMatched = item.tagId in expectedIds
                        val rollNo = expected?.rollNos?.get(item.tagId)

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(item.tagId, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    val statusLabel = if (isMatched) {
                                        "Matched • ${rollNo ?: "Roll"}"
                                    } else {
                                        if (expectedIds.isEmpty()) "Roll" else "Extra (Not in manifest)"
                                    }
                                    Text(
                                        statusLabel,
                                        color = if (isMatched) Palette.Ok else Palette.Warn,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                Text(
                                    "Remove",
                                    color = Palette.Bad,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.clickable { vm.removeItem(item) }
                                )
                            }
                        }
                    }
                }
            }
        },
        bottomBar = {
            run {
                ScanBottomBar(
                    leftText = "Finish vehicle",
                    onLeftClick = { showConfirmFinish = true },
                    submitText = "Receive ${items.size} rolls",
                    submitEnabled = items.isNotEmpty() && !busy,
                    onSubmitClick = { vm.submitRows() }
                )
            }
        }
    )

    if (showRfidPrompt) {
        AlertDialog(
            onDismissRequest = { showRfidPrompt = false },
            title = { Text("Ready to Scan RFID", fontWeight = FontWeight.Bold) },
            text = {
                Text("Please tap the Vehicle RFID card or Gate Pass tag against the tablet's NFC reader or USB reader.")
            },
            confirmButton = {
                Button(onClick = { showRfidPrompt = false }) {
                    Text("OK")
                }
            }
        )
    }

    if (showTypeDialog) {
        var typedRef by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showTypeDialog = false },
            title = { Text("Enter Loading Reference", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = typedRef,
                    onValueChange = { typedRef = it },
                    label = { Text("e.g. LOAD-00001") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (typedRef.isNotBlank()) {
                            vm.openLoading(typedRef)
                            showTypeDialog = false
                        }
                    }
                ) {
                    Text("Load Manifest")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showTypeDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showConfirmFinish) {
        ConfirmDialog(
            title = "Finish unloading vehicle?",
            message = "The current unloading session will be closed on this tablet.",
            confirmText = "Finish",
            onConfirm = {
                showConfirmFinish = false
                vm.finishVehicle()
            },
            onDismiss = { showConfirmFinish = false }
        )
    }
}
