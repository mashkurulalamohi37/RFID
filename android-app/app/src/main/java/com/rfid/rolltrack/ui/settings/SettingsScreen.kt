package com.rfid.rolltrack.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
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
import com.rfid.rolltrack.ui.theme.Palette
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    container: AppContainer,
    onOpenDiagnostics: () -> Unit,
    onBack: () -> Unit
) {
    val settings by container.settings.settings.collectAsStateWithLifecycle(com.rfid.rolltrack.config.AppSettings())
    val scope = rememberCoroutineScope()

    // What the operator is typing; null = not edited, show the saved address. Saved only on "Save address",
    // so stored settings never rewrite the field mid-typing.
    var editedUrl by rememberSaveable { mutableStateOf<String?>(null) }
    var savedNote by rememberSaveable { mutableStateOf(false) }
    val shownUrl = editedUrl ?: settings.baseUrl
    val urlChanged = editedUrl != null && editedUrl!!.trim() != settings.baseUrl
    val urlValid = editedUrl?.trim()?.let { it.startsWith("http://", true) || it.startsWith("https://", true) } == true

    Scaffold(
        topBar = { AppTopBar(container = container, title = "Settings", onBack = onBack) },
        containerColor = Palette.Bg
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Connection & Modes", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Palette.Navy)

                    OutlinedTextField(
                        value = shownUrl,
                        onValueChange = {
                            editedUrl = it
                            savedNote = false
                        },
                        label = { Text("Oracle ORDS Base URL") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                val url = editedUrl?.trim().orEmpty()
                                if (url.isNotEmpty()) {
                                    scope.launch {
                                        container.settings.updateSettings { s -> s.copy(baseUrl = url) }
                                        editedUrl = null
                                        savedNote = true
                                    }
                                }
                            },
                            enabled = urlChanged && urlValid
                        ) { Text("Save address") }
                        if (savedNote) {
                            Text("Saved. In use now.", fontSize = 13.sp, color = Palette.Ok)
                        } else if (urlChanged && !urlValid) {
                            Text("Must start with http:// or https://", fontSize = 13.sp, color = Palette.Bad)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Demo Mode (Offline ERP)", fontWeight = FontWeight.SemiBold)
                            Text("Uses internal simulated factory ERP", fontSize = 12.sp, color = Palette.Muted)
                        }
                        Switch(
                            checked = settings.demoMode,
                            onCheckedChange = { checked ->
                                scope.launch { container.settings.updateSettings { it.copy(demoMode = checked) } }
                            }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Simulate No Network", fontWeight = FontWeight.SemiBold)
                            Text("Forces offline queue testing", fontSize = 12.sp, color = Palette.Muted)
                        }
                        Switch(
                            checked = settings.simulateOffline,
                            onCheckedChange = { checked ->
                                scope.launch { container.settings.updateSettings { it.copy(simulateOffline = checked) } }
                            }
                        )
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Hardware & Diagnostics", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Palette.Navy)
                    Spacer(Modifier.height(4.dp))
                    Text("Device: ${settings.deviceModel} (${settings.deviceId})", fontSize = 13.sp, color = Palette.Navy, fontWeight = FontWeight.Medium)
                    Text("Serial: ${settings.serialNumber} • HW: ${settings.hardwareVersion}", fontSize = 12.sp, color = Palette.Muted)
                    Text("Model: ${settings.functionModel}", fontSize = 12.sp, color = Palette.Muted)
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = onOpenDiagnostics,
                        colors = ButtonDefaults.buttonColors(containerColor = Palette.Navy),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Open Diagnostics & Tag Tester")
                    }
                }
            }
        }
    }
}
