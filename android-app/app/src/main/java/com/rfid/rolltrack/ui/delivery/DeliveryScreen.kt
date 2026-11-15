package com.rfid.rolltrack.ui.delivery

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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.rfid.rolltrack.ui.common.ScanBottomBar
import com.rfid.rolltrack.ui.common.StepScaffold
import com.rfid.rolltrack.ui.common.containerViewModel
import com.rfid.rolltrack.ui.components.StatusCard
import com.rfid.rolltrack.ui.theme.Palette

@Composable
fun DeliveryScreen(
    container: AppContainer,
    onBack: () -> Unit
) {
    val vm: DeliveryViewModel = containerViewModel(container) { DeliveryViewModel(it) }

    val header by vm.header.collectAsStateWithLifecycle()
    val items by vm.items.collectAsStateWithLifecycle()
    val banner by vm.banner.collectAsStateWithLifecycle()
    val summary by vm.summary.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()

    var storeInput by rememberSaveable { mutableStateOf(header?.storeCode ?: "MAIN") }

    StepScaffold(
        container = container,
        title = "Step 7 - Store Delivery",
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

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "Destination Store & Tube Tag",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Palette.Navy
                    )
                    Spacer(Modifier.height(8.dp))

                    OutlinedTextField(
                        value = storeInput,
                        onValueChange = {
                            storeInput = it
                            vm.setStore(it)
                        },
                        label = { Text("Store Code") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(10.dp))
                    val tube = header?.tubeTag
                    val tubeLabel = if (tube.isNullOrBlank()) "Tap Tube RFID tag to assign" else "Tube RFID: $tube"
                    Text(
                        tubeLabel,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (tube.isNullOrBlank()) Palette.Warn else Palette.Ok
                    )
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
                leftText = "Next tube",
                onLeftClick = { vm.nextTube() },
                submitText = "Send ${items.size} rolls",
                submitEnabled = items.isNotEmpty() && !busy && !header?.tubeTag.isNullOrBlank(),
                onSubmitClick = { vm.submitTube() }
            )
        }
    )
}
