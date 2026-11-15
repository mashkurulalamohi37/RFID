package com.rfid.rolltrack.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rfid.rolltrack.ui.theme.Palette

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmText: String = "Confirm",
    dismissText: String = "Cancel",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = { Text(message) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = Palette.Navy)
            ) {
                Text(confirmText)
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text(dismissText)
            }
        }
    )
}

@Composable
fun StatusCard(
    tone: Banner.Tone,
    text: String,
    onDismiss: (() -> Unit)? = null
) {
    val (bg, fg) = when (tone) {
        Banner.Tone.OK -> Palette.OkBg to Palette.Ok
        Banner.Tone.WARN -> Palette.WarnBg to Palette.Warn
        Banner.Tone.BAD -> Palette.BadBg to Palette.Bad
        Banner.Tone.INFO -> Palette.InfoBg to Palette.Info
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = bg),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = text,
                color = fg,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                modifier = Modifier.weight(1f)
            )
            if (onDismiss != null) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "✕",
                    color = fg,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { onDismiss() }
                )
            }
        }
    }
}

@Composable
fun CounterPill(
    label: String,
    count: Int,
    color: Color = Palette.Navy
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontSize = 13.sp, color = color, fontWeight = FontWeight.Medium)
            Spacer(Modifier.width(6.dp))
            Text("$count", fontSize = 15.sp, color = color, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun RollTrackLogo(
    modifier: Modifier = Modifier.size(72.dp)
) {
    Box(
        modifier = modifier
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(Color(0xFF0D2040)),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.foundation.Canvas(
            modifier = Modifier
                .size(72.dp)
                .padding(10.dp)
        ) {
            val w = size.width
            val h = size.height
            val cx = w / 2f
            val cy = h / 2f

            // Central Fabric / Chip Core
            drawRoundRect(
                color = Color(0xFF1976D2),
                topLeft = androidx.compose.ui.geometry.Offset(cx - w * 0.22f, cy - h * 0.22f),
                size = androidx.compose.ui.geometry.Size(w * 0.44f, h * 0.44f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.08f, w * 0.08f)
            )

            // Inner Microprocessor White Core
            drawRoundRect(
                color = Color.White,
                topLeft = androidx.compose.ui.geometry.Offset(cx - w * 0.12f, cy - h * 0.12f),
                size = androidx.compose.ui.geometry.Size(w * 0.24f, h * 0.24f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.04f, w * 0.04f)
            )

            // Chip silicon center
            drawRoundRect(
                color = Color(0xFF0D2040),
                topLeft = androidx.compose.ui.geometry.Offset(cx - w * 0.06f, cy - h * 0.06f),
                size = androidx.compose.ui.geometry.Size(w * 0.12f, h * 0.12f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.02f, w * 0.02f)
            )

            val strokeOuter = androidx.compose.ui.graphics.drawscope.Stroke(
                width = w * 0.065f,
                cap = androidx.compose.ui.graphics.StrokeCap.Round
            )
            val strokeInner = androidx.compose.ui.graphics.drawscope.Stroke(
                width = w * 0.055f,
                cap = androidx.compose.ui.graphics.StrokeCap.Round
            )

            // Inner RFID Radio Arcs
            drawArc(
                color = Color(0xFF00E5FF),
                startAngle = 135f,
                sweepAngle = 90f,
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(cx - w * 0.32f, cy - h * 0.32f),
                size = androidx.compose.ui.geometry.Size(w * 0.64f, h * 0.64f),
                style = strokeInner
            )
            drawArc(
                color = Color(0xFF00E5FF),
                startAngle = -45f,
                sweepAngle = 90f,
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(cx - w * 0.32f, cy - h * 0.32f),
                size = androidx.compose.ui.geometry.Size(w * 0.64f, h * 0.64f),
                style = strokeInner
            )

            // Outer RFID Radio Arcs
            drawArc(
                color = Color(0xFF29B6F6),
                startAngle = 130f,
                sweepAngle = 100f,
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(cx - w * 0.44f, cy - h * 0.44f),
                size = androidx.compose.ui.geometry.Size(w * 0.88f, h * 0.88f),
                style = strokeOuter
            )
            drawArc(
                color = Color(0xFF29B6F6),
                startAngle = -50f,
                sweepAngle = 100f,
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(cx - w * 0.44f, cy - h * 0.44f),
                size = androidx.compose.ui.geometry.Size(w * 0.88f, h * 0.88f),
                style = strokeOuter
            )
        }
    }
}
