package com.rfid.rolltrack.ui.loading

import androidx.compose.foundation.background
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
import com.rfid.rolltrack.ui.components.StatusCard
import com.rfid.rolltrack.ui.theme.Palette

@Composable
fun LoadingScreen(
    container: AppContainer,
    onBack: () -> Unit
) {
    val vm: LoadingViewModel = containerViewModel(container) { LoadingViewModel(it) }

    val header by vm.header.collectAsStateWithLifecycle()
    val items by vm.items.collectAsStateWithLifecycle()
    val banner by vm.banner.collectAsStateWithLifecycle()
    val summary by vm.summary.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()

    var showEditFields by rememberSaveable { mutableStateOf(false) }
    var showRfidPrompt by rememberSaveable { mutableStateOf(false) }
    var showConfirmFinish by rememberSaveable { mutableStateOf(false) }

    val load = header?.load

    StepScaffold(
        container = container,
        title = "Step 4 - Vehicle Loading",
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

            // Header card
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
                            val veh = load?.vehicleNo?.ifBlank { "Vehicle Not Set" } ?: "Vehicle Not Set"
                            Text(
                                veh,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = Palette.Navy
                            )
                            val sub = listOfNotNull(
                                load?.challanRef?.takeIf { it.isNotBlank() }?.let { "Challan: $it" },
                                load?.supplier?.takeIf { it.isNotBlank() }
                            ).joinToString(" • ")
                            if (sub.isNotBlank()) {
                                Text(sub, fontSize = 13.sp, color = Palette.Muted)
                            }
                        }
                        Text(
                            "${items.size} rolls",
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
                        Button(
                            onClick = { showRfidPrompt = true },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Palette.Navy)
                        ) {
                            Text("Scan Vehicle RFID")
                        }
                        OutlinedButton(
                            onClick = { showEditFields = true },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Edit Details")
                        }
                    }

                    header?.loadingRefs?.let { refs ->
                        if (refs.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Loading Refs: ${refs.joinToString(", ")}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Palette.Ok
                            )
                        }
                    }
                }
            }
        },
        list = {
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
                            Column {
                                Text(item.tagId, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text(item.source, color = Palette.Muted, fontSize = 12.sp)
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
        },
        bottomBar = {
            ScanBottomBar(
                leftText = "Finish vehicle",
                onLeftClick = { showConfirmFinish = true },
                submitText = "Send ${items.size} rolls",
                submitEnabled = items.isNotEmpty() && !busy,
                onSubmitClick = { vm.submitBatch() }
            )
        }
    )

    if (showRfidPrompt) {
        AlertDialog(
            onDismissRequest = { showRfidPrompt = false },
            title = { Text("Ready to Scan Vehicle RFID", fontWeight = FontWeight.Bold) },
            text = {
                Text("Please tap the Vehicle or Gate Pass RFID card against the tablet's NFC reader or USB reader.")
            },
            confirmButton = {
                Button(onClick = { showRfidPrompt = false }) {
                    Text("OK")
                }
            }
        )
    }

    if (showEditFields) {
        var vNo by remember { mutableStateOf(load?.vehicleNo.orEmpty()) }
        var cRef by remember { mutableStateOf(load?.challanRef.orEmpty()) }
        var sup by remember { mutableStateOf(load?.supplier.orEmpty()) }

        AlertDialog(
            onDismissRequest = { showEditFields = false },
            title = { Text("Vehicle & Challan Details", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = vNo,
                        onValueChange = { vNo = it },
                        label = { Text("Vehicle Number / Tag") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = cRef,
                        onValueChange = { cRef = it },
                        label = { Text("Challan Reference") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = sup,
                        onValueChange = { sup = it },
                        label = { Text("Supplier Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        vm.setFields(vNo, cRef, sup)
                        showEditFields = false
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showEditFields = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showConfirmFinish) {
        ConfirmDialog(
            title = "Finish this vehicle?",
            message = "The current vehicle will be closed on this tablet. Saved rolls remain in the ERP.",
            confirmText = "Finish",
            onConfirm = {
                showConfirmFinish = false
                vm.finishVehicle()
            },
            onDismiss = { showConfirmFinish = false }
        )
    }
}
