package com.epicefi.epichub

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.epicefi.epichub.engine.NativeEngine
import com.epicefi.epichub.engine.RuntimeSnapshot
import kotlinx.coroutines.delay

@Composable
fun EpicHubApp() {
    val engine = remember { NativeEngine.create() }
    var snapshot by remember(engine) { mutableStateOf<RuntimeSnapshot?>(null) }

    DisposableEffect(engine) {
        onDispose {
            engine.close()
        }
    }

    LaunchedEffect(engine) {
        while (true) {
            snapshot = engine.readRuntimeSnapshot()
            delay(250)
        }
    }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "EpicHub",
                    style = MaterialTheme.typography.headlineLarge,
                )
                Text("Phase 2 foundation bootstrap")
                Text("Native engine: ${if (engine.isAlive) "alive" else "unavailable"}")
                Text("Active native engines: ${NativeEngine.activeCount()}")
                RuntimeSnapshotSummary(snapshot)
            }
        }
    }
}

@Composable
private fun RuntimeSnapshotSummary(snapshot: RuntimeSnapshot?) {
    if (snapshot == null) {
        Text("Runtime snapshot: waiting")
        return
    }

    Text("Snapshot sequence: ${snapshot.sequence}")
    Text("Session: ${snapshot.sessionId} · source: ${snapshot.sourceKind}")
    snapshot.channels.forEach { channel ->
        Text(
            "Synthetic 0x${channel.id.toString(16).uppercase()}: " +
                "${channel.value} (${channel.validity.name.lowercase()})",
        )
    }
}
