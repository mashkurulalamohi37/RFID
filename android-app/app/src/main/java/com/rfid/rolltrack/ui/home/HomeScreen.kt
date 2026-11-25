package com.rfid.rolltrack.ui.home

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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
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
import com.rfid.rolltrack.ui.common.AppTopBar
import com.rfid.rolltrack.ui.common.CollectScans
import com.rfid.rolltrack.ui.components.ConfirmDialog
import com.rfid.rolltrack.ui.theme.Palette
import kotlinx.coroutines.launch

data class HomeTile(
    val step: String,
    val title: String,
    val subtitle: String,
    val route: String,
    val color: Color
)

@Composable
fun HomeScreen(
    container: AppContainer,
    onOpen: (String) -> Unit,
    onLoggedOut: () -> Unit
) {
    val session by container.settings.session.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var showLogoutConfirm by rememberSaveable { mutableStateOf(false) }

    val tiles = listOf(
        HomeTile("1", "Tag Issue", "Scan request RFID, issue tags", "issue", Palette.Blue),
        HomeTile("2", "Record weight", "Scan one roll, enter its weight", "production", Palette.Ok),
        HomeTile("4", "Loading", "Load rolls onto vehicle", "loading", Color(0xFF8D6E63)),
        HomeTile("5", "Unloading", "Receive vehicle at gate", "unloading", Palette.Sky),
        HomeTile("7", "Store Delivery", "Rolls into tubes for store", "delivery", Color(0xFF7E57C2)),
        HomeTile("T", "Trace Roll", "Tap a tag, see its history", "trace", Palette.Navy)
    )

    // Tap any tag on home screen to instantly trace it
    CollectScans(container = container) { event ->
        if (event is com.rfid.rolltrack.scanner.ScanEvent.Tag) {
            onOpen("trace?tag=${event.tagId}")
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                container = container,
                title = "RollTrack",
                actions = {
                    Text(
                        "⚙",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clickable { onOpen("settings") }
                            .padding(horizontal = 8.dp)
                    )
                }
            )
        },
        containerColor = Palette.Bg
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            // User header card
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "Hello, ${session.userName.ifBlank { "Operator" }}",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Palette.Navy
                    )
                    Text("User ID ${session.userId.ifBlank { "1" }}", fontSize = 13.sp, color = Palette.Muted)
                }
                Text(
                    "Log out",
                    color = Palette.Blue,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    modifier = Modifier.clickable { showLogoutConfirm = true }
                )
            }

            // Step tiles
            LazyVerticalGrid(
                columns = GridCells.Fixed(1),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(tiles) { tile ->
                    Tile(tile = tile, onClick = { onOpen(tile.route) })
                }
            }

            Spacer(Modifier.height(12.dp))

            // User requirement info card
            RfidWorkflowsCard()
        }
    }

    if (showLogoutConfirm) {
        ConfirmDialog(
            title = "Log out?",
            message = "Unsent batches will stay stored locally on this tablet and will be synced automatically.",
            confirmText = "Log out",
            onConfirm = {
                showLogoutConfirm = false
                scope.launch {
                    container.settings.clearSession()
                    onLoggedOut()
                }
            },
            onDismiss = { showLogoutConfirm = false }
        )
    }
}

@Composable
private fun Tile(tile: HomeTile, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = tile.color)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    tile.step,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text(
                    tile.title,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
                Text(
                    tile.subtitle,
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 13.sp
                )
            }
        }
    }
}

@Composable
private fun RfidWorkflowsCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F1FC)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF7BAAF7))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF0F2B59))
                    .padding(horizontal = 24.dp, vertical = 5.dp)
            ) {
                Text(
                    "RFID Issues",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
            Spacer(Modifier.height(10.dp))
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RfidBullet(number = "1", text = "Tag issue – RFID scan will be done")
                RfidBullet(number = "2", text = "Record weight – scan one roll, enter its weight")
                RfidBullet(number = "3", text = "Loading and Unloading – RFID scan will be done")
            }
        }
    }
}

@Composable
private fun RfidBullet(number: String, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(Color(0xFF1976D2)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                number,
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text,
            color = Color(0xFF1F2937),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
