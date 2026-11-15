package com.rfid.rolltrack.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rfid.rolltrack.AppContainer
import com.rfid.rolltrack.scanner.ScanEvent
import com.rfid.rolltrack.ui.theme.Palette

@Composable
fun CollectScans(
    container: AppContainer,
    onScan: (ScanEvent) -> Unit
) {
    val currentOnScan by rememberUpdatedState(onScan)
    LaunchedEffect(container.scanHub) {
        container.scanHub.events.collect { event ->
            currentOnScan(event)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    container: AppContainer,
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val online by container.network.online.collectAsStateWithLifecycle()
    val pendingCount by container.scanRepository.observePendingCount().collectAsStateWithLifecycle(0)
    val settings by container.settings.settings.collectAsStateWithLifecycle(com.rfid.rolltrack.config.AppSettings())

    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onBack == null) {
                    com.rfid.rolltrack.ui.components.RollTrackLogo(modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Text("←", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
            }
        },
        actions = {
            if (settings.demoMode) {
                Chip(text = "DEMO", color = Palette.Warn)
                Spacer(Modifier.width(6.dp))
            }
            if (pendingCount > 0) {
                Chip(text = "$pendingCount unsent", color = Palette.Warn)
                Spacer(Modifier.width(6.dp))
            }
            val onlineColor = if (online && !settings.simulateOffline) Palette.Ok else Palette.Bad
            val onlineText = if (online && !settings.simulateOffline) "Online" else "Offline"
            Chip(text = onlineText, color = onlineColor)
            Spacer(Modifier.width(8.dp))
            actions()
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Palette.Navy)
    )
}

@Composable
fun Chip(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(color)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun StepScaffold(
    container: AppContainer,
    title: String,
    busy: Boolean = false,
    onBack: (() -> Unit)? = null,
    onScan: (ScanEvent) -> Unit,
    controls: @Composable () -> Unit = {},
    list: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {}
) {
    CollectScans(container = container, onScan = onScan)

    Scaffold(
        topBar = { AppTopBar(container = container, title = title, onBack = onBack) },
        bottomBar = bottomBar,
        containerColor = Palette.Bg
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                controls()
                Spacer(Modifier.height(8.dp))
                Box(modifier = Modifier.weight(1f)) {
                    list()
                }
            }

            if (busy) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Palette.Navy)
                }
            }
        }
    }
}
