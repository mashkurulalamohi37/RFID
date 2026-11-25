package com.rfid.rolltrack.ui.issue

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.draw.clip
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
fun TagIssueScreen(
    container: AppContainer,
    onBack: () -> Unit
) {
    val vm: TagIssueViewModel = containerViewModel(container) { TagIssueViewModel(it) }

    val header by vm.header.collectAsStateWithLifecycle()
    val items by vm.items.collectAsStateWithLifecycle()
    val banner by vm.banner.collectAsStateWithLifecycle()
    val summary by vm.summary.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()

    var showTypeDialog by rememberSaveable { mutableStateOf(false) }
    var showConfirmNew by rememberSaveable { mutableStateOf(false) }
    var showCameraScan by rememberSaveable { mutableStateOf(false) }
    var countInput by rememberSaveable { mutableStateOf("") }

    val req = header?.request

    StepScaffold(
        container = container,
        title = "Step 1 - RFID Tag Issue",
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

            if (req == null) {
                // Request lookup selection
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "Load the request",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Palette.Navy
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Scan the requisition barcode / QR, or type the requisition number. " +
                                "Then hold the left trigger to read the roll tags.",
                            fontSize = 13.sp,
                            color = Palette.Muted
                        )
                        Spacer(Modifier.height(14.dp))
                        Button(
                            onClick = { showCameraScan = true },
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Palette.Navy)
                        ) {
                            Text("Scan barcode / QR")
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { showTypeDialog = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Type request no.")
                        }
                    }
                }
            } else {
                // Request loaded summary
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
                                    req.refCode,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Palette.Navy
                                )
                                Text(
                                    "${req.supplier} • ${req.program}",
                                    fontSize = 13.sp,
                                    color = Palette.Muted
                                )
                            }
                            Text(
                                "Balance: ${req.effectiveBalance}",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Palette.Ok
                            )
                        }

                        Spacer(Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CounterPill("Required", req.required, Palette.Navy)
                            CounterPill("Issued", req.shownIssued, Palette.Warn)
                            CounterPill("Balance", req.effectiveBalance, Palette.Ok)
                        }

                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilterChip(
                                selected = header?.countOnly != true,
                                onClick = { vm.setCountOnly(false) },
                                label = { Text("Scan tags") }
                            )
                            FilterChip(
                                selected = header?.countOnly == true,
                                onClick = { vm.setCountOnly(true) },
                                label = { Text("Count only") }
                            )
                        }

                        if (header?.countOnly != true) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Hold the left trigger to read the roll tags.",
                                fontSize = 13.sp,
                                color = Palette.Muted
                            )
                        }

                        if (header?.countOnly == true) {
                            Spacer(Modifier.height(10.dp))
                            OutlinedTextField(
                                value = countInput,
                                onValueChange = { countInput = it.filter { c -> c.isDigit() } },
                                label = { Text(if (vm.exactTarget() > 0) "Tag count (must be ${vm.exactTarget()})" else "Tag count") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        },
        list = {
            if (req != null && header?.countOnly != true) {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(items, key = { it.id }) { item ->
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
                                Column(Modifier.weight(1f)) {
                                    Text(item.tagId, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    if (item.state == com.rfid.rolltrack.data.local.ItemState.FAILED) {
                                        Text(
                                            "Not saved: ${item.errorMessage ?: "rejected by ERP"}",
                                            color = Palette.Bad,
                                            fontSize = 12.sp
                                        )
                                    } else {
                                        Text(item.source, color = Palette.Muted, fontSize = 12.sp)
                                    }
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
            if (req != null) {
                val isCountOnly = header?.countOnly == true
                val submitCount = if (isCountOnly) countInput.toIntOrNull() ?: 0 else items.size
                // Exactly the balance (max 10) must be submitted: the button shows progress and unlocks at the target.
                val target = vm.exactTarget()
                ScanBottomBar(
                    leftText = "New request",
                    onLeftClick = { showConfirmNew = true },
                    submitText = if (target > 0) "Submit $submitCount / $target tags" else "Balance 0",
                    submitEnabled = target > 0 && submitCount == target && !busy,
                    onSubmitClick = {
                        if (isCountOnly) {
                            vm.submitCount(submitCount)
                        } else {
                            vm.submitTags()
                        }
                    }
                )
            }
        }
    )

    if (showTypeDialog) {
        var typedCode by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showTypeDialog = false },
            title = { Text("Enter Request Number", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = typedCode,
                    onValueChange = { typedCode = it },
                    label = { Text("e.g. BPKWL-13/IR25060017") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (typedCode.isNotBlank()) {
                            vm.loadRequest(typedCode)
                            showTypeDialog = false
                        }
                    }
                ) {
                    Text("Load")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showTypeDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showCameraScan) {
        com.rfid.rolltrack.ui.components.BarcodeScanDialog(
            title = "Scan requisition barcode / QR",
            onResult = { code ->
                showCameraScan = false
                vm.loadRequest(code.trim())
            },
            onDismiss = { showCameraScan = false }
        )
    }

    if (showConfirmNew) {
        ConfirmDialog(
            title = "Start a new request?",
            message = "Any unsent tags will be cleared for this session.",
            confirmText = "Start new",
            onConfirm = {
                showConfirmNew = false
                vm.startNew()
            },
            onDismiss = { showConfirmNew = false }
        )
    }
}
