package com.rfid.rolltrack.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rfid.rolltrack.AppContainer
import com.rfid.rolltrack.ui.theme.Palette

@Composable
inline fun <reified T : ViewModel> containerViewModel(
    container: AppContainer,
    crossinline create: (AppContainer) -> T
): T {
    return viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <VM : ViewModel> create(modelClass: Class<VM>): VM {
                return create(container) as VM
            }
        }
    )
}

@Composable
fun ScanBottomBar(
    leftText: String = "New",
    onLeftClick: () -> Unit,
    submitText: String,
    submitEnabled: Boolean,
    onSubmitClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onLeftClick,
                modifier = Modifier.weight(0.4f)
            ) {
                Text(leftText)
            }
            Button(
                onClick = onSubmitClick,
                enabled = submitEnabled,
                modifier = Modifier.weight(0.6f),
                colors = ButtonDefaults.buttonColors(containerColor = Palette.Navy)
            ) {
                Text(submitText)
            }
        }
    }
}
