package com.epicefi.epichub.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.epicefi.epichub.engine.RuntimeSnapshot
import com.epicefi.epichub.ui.components.KeyValueRow
import com.epicefi.epichub.ui.components.StructuralCard

@Composable
fun DashboardScreen(
    snapshot: RuntimeSnapshot?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Dashboard", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Structural placeholder — final visual treatment will follow the accepted Dashboard prototype.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        StructuralCard(
            title = "Runtime source",
            modifier = Modifier.fillMaxWidth(),
            subtitle = "Existing synthetic RuntimeSnapshot foundation",
        ) {
            if (snapshot == null) {
                Text("Waiting for runtime snapshot")
            } else {
                KeyValueRow("Sequence", snapshot.sequence.toString())
                KeyValueRow("Session", snapshot.sessionId.toString())
                KeyValueRow("Source", snapshot.sourceKind.toString())
                KeyValueRow("Channels", snapshot.channels.size.toString())
            }
        }

        snapshot?.channels?.forEach { channel ->
            StructuralCard(
                title = "Synthetic 0x${channel.id.toString(16).uppercase()}",
                modifier = Modifier.fillMaxWidth(),
            ) {
                KeyValueRow("Value", channel.value.toString())
                KeyValueRow("Validity", channel.validity.name)
            }
        }
    }
}
