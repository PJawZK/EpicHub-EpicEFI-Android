package com.epicefi.epichub.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.epicefi.epichub.diagnostics.DiagnosticCapture
import com.epicefi.epichub.diagnostics.DiagnosticsSnapshot
import com.epicefi.epichub.engine.RuntimeSnapshot
import com.epicefi.epichub.navigation.AppDestination
import com.epicefi.epichub.ui.analyzer.AnalyzerScreen
import com.epicefi.epichub.ui.dashboard.DashboardScreen
import com.epicefi.epichub.ui.diagnostics.DiagnosticsScreen
import com.epicefi.epichub.ui.logger.LoggerScreen
import com.epicefi.epichub.ui.settings.SettingsScreen
import com.epicefi.epichub.ui.tools.TuningToolsScreen
import com.epicefi.epichub.ui.tuner.TunerScreen

@Composable
fun EpicHubShell(
    destination: AppDestination,
    onDestinationSelected: (AppDestination) -> Unit,
    settingsOpen: Boolean,
    onOpenSettings: () -> Unit,
    onCloseSettings: () -> Unit,
    runtimeSnapshot: RuntimeSnapshot?,
    diagnostics: DiagnosticsSnapshot,
    engineAlive: Boolean,
    activeEngineCount: Int,
    onCaptureRecentProblem: () -> Unit,
    onStartReproductionCapture: () -> Unit,
    onAddCaptureMarker: () -> Unit,
    onStopCapture: () -> Unit,
    onExportCapture: (DiagnosticCapture) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
        color = MaterialTheme.colorScheme.background,
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val wide = maxWidth >= 840.dp
            Column(modifier = Modifier.fillMaxSize()) {
                SystemStatusHeader(
                    snapshot = runtimeSnapshot,
                    diagnostics = diagnostics,
                    engineAlive = engineAlive,
                    activeEngineCount = activeEngineCount,
                    onOpenSettings = onOpenSettings,
                    onCaptureRecentProblem = onCaptureRecentProblem,
                    onStartReproductionCapture = onStartReproductionCapture,
                    onAddCaptureMarker = onAddCaptureMarker,
                    onStopCapture = onStopCapture,
                )

                if (wide) {
                    Row(modifier = Modifier.weight(1f)) {
                        PrimaryNavigationRail(
                            destination = destination,
                            onDestinationSelected = onDestinationSelected,
                        )
                        Box(modifier = Modifier.weight(1f)) {
                            ShellContent(
                                destination = destination,
                                settingsOpen = settingsOpen,
                                onCloseSettings = onCloseSettings,
                                runtimeSnapshot = runtimeSnapshot,
                                diagnostics = diagnostics,
                                onExportCapture = onExportCapture,
                            )
                        }
                    }
                } else {
                    Box(modifier = Modifier.weight(1f)) {
                        ShellContent(
                            destination = destination,
                            settingsOpen = settingsOpen,
                            onCloseSettings = onCloseSettings,
                            runtimeSnapshot = runtimeSnapshot,
                            diagnostics = diagnostics,
                            onExportCapture = onExportCapture,
                        )
                    }
                    PrimaryNavigationBar(
                        destination = destination,
                        onDestinationSelected = onDestinationSelected,
                    )
                }
            }
        }
    }
}

@Composable
private fun SystemStatusHeader(
    snapshot: RuntimeSnapshot?,
    diagnostics: DiagnosticsSnapshot,
    engineAlive: Boolean,
    activeEngineCount: Int,
    onOpenSettings: () -> Unit,
    onCaptureRecentProblem: () -> Unit,
    onStartReproductionCapture: () -> Unit,
    onAddCaptureMarker: () -> Unit,
    onStopCapture: () -> Unit,
) {
    var captureMenuExpanded by remember { mutableStateOf(false) }
    val activeCapture = diagnostics.activeCapture

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "EpicHub",
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = if (engineAlive) "Engine active ($activeEngineCount)" else "Engine unavailable",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = snapshot?.let { "Source ${it.sourceKind} · #${it.sequence}" } ?: "Source waiting",
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(modifier = Modifier.weight(1f))

            if (activeCapture == null) {
                Box {
                    Button(onClick = { captureMenuExpanded = true }) {
                        Text("Capture")
                    }
                    DropdownMenu(
                        expanded = captureMenuExpanded,
                        onDismissRequest = { captureMenuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Capture recent problem") },
                            onClick = {
                                captureMenuExpanded = false
                                onCaptureRecentProblem()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Start reproduction capture") },
                            onClick = {
                                captureMenuExpanded = false
                                onStartReproductionCapture()
                            },
                        )
                    }
                }
            } else {
                Text(
                    text = "Capture #${activeCapture.captureId}",
                    style = MaterialTheme.typography.labelMedium,
                )
                TextButton(onClick = onAddCaptureMarker) {
                    Text("Mark")
                }
                Button(onClick = onStopCapture) {
                    Text("Stop")
                }
            }

            TextButton(onClick = onOpenSettings) {
                Text("Settings")
            }
        }
    }
}

@Composable
private fun PrimaryNavigationRail(
    destination: AppDestination,
    onDestinationSelected: (AppDestination) -> Unit,
) {
    NavigationRail(modifier = Modifier.fillMaxHeight()) {
        AppDestination.entries.forEach { item ->
            NavigationRailItem(
                selected = item == destination,
                onClick = { onDestinationSelected(item) },
                icon = { Text(item.glyph) },
                label = { Text(item.compactLabel) },
                alwaysShowLabel = true,
            )
        }
    }
}

@Composable
private fun PrimaryNavigationBar(
    destination: AppDestination,
    onDestinationSelected: (AppDestination) -> Unit,
) {
    NavigationBar {
        AppDestination.entries.forEach { item ->
            NavigationBarItem(
                selected = item == destination,
                onClick = { onDestinationSelected(item) },
                icon = { Text(item.glyph) },
                label = { Text(item.compactLabel) },
                alwaysShowLabel = true,
            )
        }
    }
}

@Composable
private fun ShellContent(
    destination: AppDestination,
    settingsOpen: Boolean,
    onCloseSettings: () -> Unit,
    runtimeSnapshot: RuntimeSnapshot?,
    diagnostics: DiagnosticsSnapshot,
    onExportCapture: (DiagnosticCapture) -> Unit,
) {
    if (settingsOpen) {
        SettingsScreen(onClose = onCloseSettings)
        return
    }

    when (destination) {
        AppDestination.DASHBOARD -> DashboardScreen(runtimeSnapshot)
        AppDestination.TUNER -> TunerScreen()
        AppDestination.TUNING_TOOLS -> TuningToolsScreen()
        AppDestination.LOGGER -> LoggerScreen(runtimeSnapshot)
        AppDestination.ANALYZER -> AnalyzerScreen()
        AppDestination.DIAGNOSTICS -> DiagnosticsScreen(
            diagnostics = diagnostics,
            onExportCapture = onExportCapture,
        )
    }
}
