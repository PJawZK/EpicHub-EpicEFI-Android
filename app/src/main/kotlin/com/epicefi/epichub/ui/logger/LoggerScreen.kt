package com.epicefi.epichub.ui.logger

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.epicefi.epichub.engine.RuntimeSnapshot
import com.epicefi.epichub.ui.components.KeyValueRow
import com.epicefi.epichub.ui.components.StructuralCard

private val loggerTabs = listOf("Live Logger", "Trigger Logger")

@Composable
fun LoggerScreen(
    snapshot: State<RuntimeSnapshot?>,
    modifier: Modifier = Modifier,
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Logger", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Structural placeholder — final visual treatment will follow the accepted Logger prototype.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        TabRow(selectedTabIndex = selectedTab) {
            loggerTabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title) },
                )
            }
        }

        if (selectedTab == 0) {
            RecordingHealth(snapshot)
        } else {
            StructuralCard(
                title = "Trigger lanes",
                modifier = Modifier.fillMaxWidth(),
                subtitle = "Dedicated timing capture surface placeholder.",
            ) {
                listOf("Crank", "TDC/reference", "CAM1", "CAM2", "Injection", "Ignition").forEach {
                    Text(it)
                }
            }
        }
    }
}

@Composable
private fun RecordingHealth(snapshot: State<RuntimeSnapshot?>) {
    val current = snapshot.value
    StructuralCard(
        title = "Recording health",
        modifier = Modifier.fillMaxWidth(),
        subtitle = "No file writer is attached yet.",
    ) {
        KeyValueRow("Source", current?.sourceKind?.toString() ?: "waiting")
        KeyValueRow("Visible channels", current?.channels?.size?.toString() ?: "0")
        KeyValueRow("Writer", "not implemented")
        KeyValueRow("Drops", "0 (synthetic foundation)")
    }
}
