package com.epicefi.epichub.ui.tuner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.epicefi.epichub.ui.components.StructuralCard

@Composable
fun TunerScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Tuner", style = MaterialTheme.typography.headlineMedium)
        StructuralCard(
            title = "Profile / session authority",
            modifier = Modifier.fillMaxWidth(),
            subtitle = "No real ECU/profile is attached in this slice.",
        ) {
            Text("Offline structural surface")
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(onClick = {}, label = { Text("System") })
            AssistChip(onClick = {}, label = { Text("Category") })
            AssistChip(onClick = {}, label = { Text("Feature") })
        }

        StructuralCard(
            title = "Editor workspace",
            modifier = Modifier.fillMaxWidth(),
            subtitle = "Parameters / Tables / Curves will be profile-driven.",
        ) {
            Text("No calibration editing authority is enabled yet.")
        }
    }
}
