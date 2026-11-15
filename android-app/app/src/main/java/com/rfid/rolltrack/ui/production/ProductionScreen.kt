package com.rfid.rolltrack.ui.production

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
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rfid.rolltrack.AppContainer
import com.rfid.rolltrack.ui.common.ScanBottomBar
import com.rfid.rolltrack.ui.common.StepScaffold
import com.rfid.rolltrack.ui.common.containerViewModel
import com.rfid.rolltrack.ui.components.ConfirmDialog
import com.rfid.rolltrack.ui.components.StatusCard
import com.rfid.rolltrack.ui.theme.Palette

@Composable
fun ProductionScreen(
    container: AppContainer,
    onBack: () -> Unit
) {
    val vm: ProductionViewModel = containerViewModel(container) { ProductionViewModel(it) }

    val header by vm.header.collectAsStateWithLifecycle()
    val items by vm.items.collectAsStateWithLifecycle()
    val banner by vm.banner.collectAsStateWithLifecycle()
    val summary by vm.summary.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val workOrders by vm.workOrders.collectAsStateWithLifecycle()
    val editingItem by vm.editingItem.collectAsStateWithLifecycle()

    var showRfidPrompt by rememberSaveable { mutableStateOf(false) }
    var showConfirmChange by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }

    val ctx = header?.context

    StepScaffold(
        container = container,
        title = "Step 2 - Subcontract Production",
        busy = busy,
        onBack = onBack,
        onScan = { vm.onScan(it) },
        controls = {
            banner?.let {
                StatusCard(tone = it.tone, text = it.text, onDismiss = { vm.dismissBanner() })
                Spacer(Modifier.height(8.dp))
            }
            summary?.let {
                StatusCard(
                    tone = if (it.accepted > 0) com.rfid.rolltrack.ui.components.Banner.Tone.OK else com.rfid.rolltrack.ui.components.Banner.Tone.WARN,
                    text = it.message,
                    onDismiss = { vm.dismissSummary() }
                )
                Spacer(Modifier.height(8.dp))
            }

            if (ctx == null) {
                // Program / Work Order selection
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { showRfidPrompt = true },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Palette.Navy)
                            ) {
                                Text("Scan Program RFID")
                            }
                        }

                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Select Work Order or Tap Program RFID",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Palette.Navy
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Tap the Program / Job Card RFID against the reader, or select a work order below.",
                            fontSize = 13.sp,
                            color = Palette.Muted
                        )

                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = {
                                searchQuery = it
                                vm.searchWorkOrders(it)
                            },
                            label = { Text("Search Work Order / Program No") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            } else {
                // Active Program summary
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
                            Column {
                                Text(
                                    ctx.programNo,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Palette.Navy
                                )
                                Text(
                                    "${ctx.woNo} • ${ctx.supplier}",
                                    fontSize = 13.sp,
                                    color = Palette.Muted
                                )
                            }
                            Text(
                                "${items.size} rolls",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Palette.Navy
                            )
                        }

                        if (ctx.pendingFifo.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            val nextTag = ctx.pendingFifo.firstOrNull { expected -> items.none { it.tagId == expected } }
                            if (nextTag != null) {
                                Text(
                                    "Next FIFO Tag: $nextTag",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Palette.Warn
                                )
                            }
                        }
                    }
                }
            }
        },
        list = {
            if (ctx == null) {
                // Work Order list
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(workOrders, key = { it.woNo }) { wo ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable { vm.openProgram(wo.programNo) },
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Column(Modifier.padding(14.dp)) {
                                Text(
                                    "${wo.woNo} (${wo.programNo})",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = Palette.Navy
                                )
                                Text(wo.unit, fontSize = 13.sp, color = Palette.Muted)
                                Text(wo.location, fontSize = 12.sp, color = Palette.Muted)
                            }
                        }
                    }
                }
            } else {
                // Scanned rolls list
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(items, key = { it.id }) { item ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                                .clickable { vm.openEditor(item) },
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
                                    val weightText = if (item.weightKg != null && item.weightKg > 0.0) {
                                        "${item.weightKg} kg"
                                    } else {
                                        "Tap to enter weight"
                                    }
                                    Text(
                                        weightText,
                                        color = if (item.weightKg != null) Palette.Ok else Palette.Bad,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp
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
            if (ctx != null) {
                ScanBottomBar(
                    leftText = "Change program",
                    onLeftClick = { showConfirmChange = true },
                    submitText = "Save ${items.size} rolls",
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
                Text("Please tap the Program or Job-Card RFID card against the tablet's NFC reader or USB reader.")
            },
            confirmButton = {
                Button(onClick = { showRfidPrompt = false }) {
                    Text("OK")
                }
            }
        )
    }

    editingItem?.let { item ->
        var weightInput by remember(item.id) {
            mutableStateOf(item.weightKg?.toString() ?: "")
        }
        AlertDialog(
            onDismissRequest = { vm.closeEditor() },
            title = { Text("Enter Roll Weight (${item.tagId})", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    OutlinedTextField(
                        value = weightInput,
                        onValueChange = { weightInput = it },
                        label = { Text("Weight in kg (e.g. 24.5)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val parsed = weightInput.toDoubleOrNull() ?: 0.0
                        if (parsed > 0.0) {
                            vm.setWeight(item, parsed)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Palette.Navy)
                ) {
                    Text("Save Weight")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { vm.closeEditor() }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showConfirmChange) {
        ConfirmDialog(
            title = "Change program?",
            message = "The current program will be closed. Any unsent rolls will be cleared.",
            confirmText = "Change",
            onConfirm = {
                showConfirmChange = false
                vm.startNew()
            },
            onDismiss = { showConfirmChange = false }
        )
    }
}
