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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.epicefi.epichub.engine.NativeEngine

@Composable
fun EpicHubApp() {
    val engine = remember { NativeEngine.create() }

    DisposableEffect(engine) {
        onDispose {
            engine.close()
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
            }
        }
    }
}
