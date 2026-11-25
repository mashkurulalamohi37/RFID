package com.rfid.rolltrack.ui.production

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.KeyboardActions
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rfid.rolltrack.AppContainer
import com.rfid.rolltrack.data.remote.ApiLog
import com.rfid.rolltrack.data.local.ItemState
import com.rfid.rolltrack.ui.common.ScanBottomBar
import com.rfid.rolltrack.ui.components.ConfirmDialog
import com.rfid.rolltrack.ui.common.StepScaffold
import com.rfid.rolltrack.ui.common.containerViewModel
import com.rfid.rolltrack.ui.components.StatusCard
import com.rfid.rolltrack.ui.theme.Palette

/** Step 2 - Record weight: one roll tag at a time, weight sent as soon as it is saved. */
@Composable
fun ProductionScreen(
    container: AppContainer,
    onBack: () -> Unit
) {
    val vm: ProductionViewModel = containerViewModel(container) { ProductionViewModel(it) }

    val banner by vm.banner.collectAsStateWithLifecycle()
    val summary by vm.summary.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val entry by vm.entry.collectAsStateWithLifecycle()
    val items by vm.items.collectAsStateWithLifecycle()
    var showConfirmClear by remember { mutableStateOf(false) }

    StepScaffold(
        container = container,
        title = "Step 2 - Record weight",
        busy = busy,
        onBack = onBack,
        onScan = { vm.onScan(it) },
        controls = {
            banner?.let {
                StatusCard(tone = it.tone, text = it.text, onDismiss = { vm.dismissBanner() })
                Spacer(Modifier.height(8.dp))
            }
            summary?.let {
                if (it.kind == com.rfid.rolltrack.data.SubmitSummary.Kind.ALL_REJECTED) {
                    com.rfid.rolltrack.ui.components.SubmitResultCard(summary = it, onDismiss = { vm.dismissSummary() })
                    Spacer(Modifier.height(8.dp))
                }
            }
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Scan one roll", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Palette.Navy)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Hold the reader to ONE roll tag and enter its weight. The roll is marked below. " +
                            "Repeat for each roll, then tap Submit. Tap a roll to change its weight.",
                        fontSize = 13.sp,
                        color = Palette.Muted
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Marked rolls (${items.size})",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Palette.Muted
            )
        },
        list = {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(items, key = { it.id }) { row ->
                    val failed = row.state == ItemState.FAILED
                    val waiting = row.state == ItemState.PENDING || row.state == ItemState.SENDING
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                            .clickable(enabled = !waiting) { vm.edit(row) },
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(row.tagId, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text("${row.weightKg} kg", color = Palette.Ok, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                when {
                                    failed -> Text("Not saved: ${row.errorMessage ?: "refused"}", fontSize = 12.sp, color = Palette.Bad)
                                    waiting -> Text("Waiting to send…", fontSize = 12.sp, color = Palette.Warn)
                                    else -> Text("Marked ${ApiLog.fmt(row.scannedAt).take(8)} · not submitted", fontSize = 12.sp, color = Palette.Muted)
                                }
                            }
                            if (!waiting) {
                                Text(
                                    "Remove",
                                    color = Palette.Bad,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.clickable { vm.removeItem(row) }
                                )
                            }
                        }
                    }
                }
            }
        },
        bottomBar = {
            val toSend = items.count { it.state == ItemState.LOCAL || it.state == ItemState.FAILED }
            ScanBottomBar(
                leftText = "Clear list",
                onLeftClick = { if (items.isNotEmpty()) showConfirmClear = true },
                submitText = "Submit $toSend",
                submitEnabled = toSend > 0 && !busy && entry == null,
                onSubmitClick = { vm.submitWeights() }
            )
        }
    )

    if (showConfirmClear) {
        ConfirmDialog(
            title = "Clear the list?",
            message = "Rolls not yet submitted will be removed from this PDA.",
            confirmText = "Clear",
            onConfirm = {
                showConfirmClear = false
                vm.startNew()
            },
            onDismiss = { showConfirmClear = false }
        )
    }

    entry?.let { e ->
        var weightInput by remember(e.tagId) { mutableStateOf(e.typed) }
        val focus = remember { FocusRequester() }
        LaunchedEffect(e.tagId) { runCatching { focus.requestFocus() } }
        AlertDialog(
            onDismissRequest = { if (!busy) vm.cancelEntry() },
            title = { Text("Roll weight", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(e.tagId, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Palette.Navy)
                    e.sentKg?.let {
                        Text("Already submitted earlier: $it kg", fontSize = 12.sp, color = Palette.Warn)
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = weightInput,
                        onValueChange = { weightInput = it },
                        label = { Text("Weight in kg (e.g. 24.5)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { if (!busy) vm.saveWeight(weightInput) }),
                        singleLine = true,
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth().focusRequester(focus)
                    )
                    e.error?.let {
                        Spacer(Modifier.height(6.dp))
                        Text(it, fontSize = 12.sp, color = Palette.Bad)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { vm.saveWeight(weightInput) },
                    enabled = !busy && weightInput.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = Palette.Navy)
                ) { Text(if (busy) "Saving..." else "Save") }
            },
            dismissButton = {
                OutlinedButton(onClick = { vm.cancelEntry() }, enabled = !busy) { Text("Cancel") }
            }
        )
    }
}
