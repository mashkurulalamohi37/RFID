package com.rfid.rolltrack.ui.login

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rfid.rolltrack.AppContainer
import com.rfid.rolltrack.config.UserSession
import com.rfid.rolltrack.ui.components.StatusCard
import com.rfid.rolltrack.ui.theme.Palette
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    container: AppContainer,
    onLoginSuccess: () -> Unit
) {
    val settings by container.settings.settings.collectAsStateWithLifecycle(com.rfid.rolltrack.config.AppSettings())

    // Security: never pre-filled, and wiped whenever the app is left, so both are typed every time.
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_STOP) {
        username = ""
        password = ""
    }
    var errorMsg by rememberSaveable { mutableStateOf<String?>(null) }
    var busy by rememberSaveable { mutableStateOf(false) }
    var showServerSettings by rememberSaveable { mutableStateOf(false) }
    var serverUrlInput by rememberSaveable(settings.baseUrl) { mutableStateOf(settings.baseUrl) }

    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Palette.Navy),
        contentAlignment = Alignment.Center
    ) {
        // Gear icon in top right for Server Config
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            contentAlignment = Alignment.TopEnd
        ) {
            Text(
                "⚙ Server",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clickable { showServerSettings = true }
                    .padding(8.dp)
            )
        }

        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                com.rfid.rolltrack.ui.components.RollTrackLogo(modifier = Modifier.size(76.dp))
                Spacer(Modifier.height(14.dp))

                Text(
                    "RollTrack",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = Palette.Navy
                )
                Text(
                    if (settings.demoMode) "RFID Roll Tracking (Demo Mode)" else "RFID Roll Tracking System",
                    fontSize = 13.sp,
                    color = if (settings.demoMode) Palette.Blue else Palette.Muted,
                    fontWeight = if (settings.demoMode) FontWeight.SemiBold else FontWeight.Normal
                )
                Spacer(Modifier.height(18.dp))

                errorMsg?.let {
                    StatusCard(tone = com.rfid.rolltrack.ui.components.Banner.Tone.BAD, text = it)
                    Spacer(Modifier.height(10.dp))
                }

                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(18.dp))

                Button(
                    onClick = {
                        if (username.isBlank() || password.isBlank()) {
                            errorMsg = "Please enter username and password"
                            return@Button
                        }
                        busy = true
                        errorMsg = null
                        scope.launch {
                            try {
                                val res = container.api.login(username, password)
                                container.settings.setSession(
                                    UserSession(
                                        userId = res.userId,
                                        userName = username,
                                        fullName = res.displayName
                                    )
                                )
                                onLoginSuccess()
                            } catch (e: Exception) {
                                errorMsg = if (settings.demoMode) {
                                    e.message ?: "Login failed"
                                } else {
                                    "Login failed: ${e.message ?: e.javaClass.simpleName}\n\nServer: ${settings.baseUrl}\nCheck Wi-Fi, or open Settings → Diagnostics to see the API call."
                                }
                            } finally {
                                busy = false
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Palette.Navy),
                    enabled = !busy
                ) {
                    if (busy) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                    } else {
                        Text("Sign in", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(Modifier.height(12.dp))

                // One-click offline demo mode button
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            container.settings.updateSettings { it.copy(demoMode = true) }
                            container.settings.setSession(
                                UserSession(
                                    userId = "1",
                                    userName = username.ifBlank { "sumon" },
                                    fullName = "Sumon (Offline Demo)"
                                )
                            )
                            onLoginSuccess()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                ) {
                    Text("Use Demo Mode (Offline Testing)", fontSize = 14.sp, color = Palette.Blue)
                }
            }
        }
    }

    if (showServerSettings) {
        AlertDialog(
            onDismissRequest = { showServerSettings = false },
            title = { Text("ERP Server Connection", fontWeight = FontWeight.Bold, color = Palette.Navy) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Configure the Oracle ORDS ERP server address or enable offline simulated mode.",
                        fontSize = 13.sp,
                        color = Palette.Muted
                    )

                    OutlinedTextField(
                        value = serverUrlInput,
                        onValueChange = { serverUrlInput = it },
                        label = { Text("Oracle ORDS Base URL") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Demo Mode (Offline ERP)", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("Simulated factory data without live server", fontSize = 11.sp, color = Palette.Muted)
                        }
                        Switch(
                            checked = settings.demoMode,
                            onCheckedChange = { checked ->
                                scope.launch {
                                    container.settings.updateSettings { it.copy(demoMode = checked) }
                                }
                            }
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            container.settings.updateSettings { it.copy(baseUrl = serverUrlInput.trim()) }
                            showServerSettings = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Palette.Navy)
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showServerSettings = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}