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
import androidx.compose.runtime.State
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.epicefi.epichub.engine.RuntimeSnapshot
import com.epicefi.epichub.ui.components.KeyValueRow
import com.epicefi.epichub.ui.components.StructuralCard

@Composable
fun DashboardScreen(
    snapshot: State<RuntimeSnapshot?>,
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
        DashboardRuntimeContent(snapshot)
    }
}

@Composable
private fun DashboardRuntimeContent(snapshot: State<RuntimeSnapshot?>) {
    val current = snapshot.value

    StructuralCard(
        title = "Runtime source",
        modifier = Modifier.fillMaxWidth(),
        subtitle = "Existing synthetic RuntimeSnapshot foundation",
    ) {
        if (current == null) {
            Text("Waiting for runtime snapshot")
        } else {
            KeyValueRow("Sequence", current.sequence.toString())
            KeyValueRow("Session", current.sessionId.toString())
            KeyValueRow("Source", current.sourceKind.toString())
            KeyValueRow("Channels", current.channels.size.toString())
        }
    }

    current?.channels?.forEach { channel ->
        key(channel.id) {
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
