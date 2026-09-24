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
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.epicefi.epichub.diagnostics.ActiveDiagnosticCapture
import com.epicefi.epichub.diagnostics.DiagnosticCapture
import com.epicefi.epichub.diagnostics.DiagnosticsSnapshot
import com.epicefi.epichub.engine.RuntimeSnapshot
import com.epicefi.epichub.navigation.AppDestination
import com.epicefi.epichub.navigation.PrimaryWorkspace
import com.epicefi.epichub.ui.analyzer.AnalyzerScreen
import com.epicefi.epichub.ui.dashboard.DashboardScreen
import com.epicefi.epichub.ui.diagnostics.DiagnosticsScreen
import com.epicefi.epichub.ui.diagnostics.DiagnosticsTab
import com.epicefi.epichub.ui.logger.LoggerScreen
import com.epicefi.epichub.ui.settings.SettingsScreen
import com.epicefi.epichub.ui.tools.TuningToolsScreen
import com.epicefi.epichub.ui.tuner.TunerScreen

@Composable
fun EpicHubShell(
    destination: AppDestination,
    onDestinationSelected: (AppDestination) -> Unit,
    onWorkspaceSelected: (PrimaryWorkspace) -> Unit,
    settingsOpen: Boolean,
    onOpenSettings: () -> Unit,
    onCloseSettings: () -> Unit,
    runtimeSnapshot: State<RuntimeSnapshot?>,
    diagnostics: State<DiagnosticsSnapshot>,
    activeCapture: ActiveDiagnosticCapture?,
    engineAlive: Boolean,
    activeEngineCount: Int,
    onDiagnosticsTabSelected: (DiagnosticsTab) -> Unit,
    onCaptureRecentProblem: () -> Unit,
    onStartReproductionCapture: () -> Unit,
    onAddCaptureMarker: () -> Unit,
    onStopCapture: () -> Unit,
    onSaveCapture: (DiagnosticCapture) -> Unit,
    onShareCapture: (DiagnosticCapture) -> Unit,
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
                    activeCapture = activeCapture,
                    engineAlive = engineAlive,
                    activeEngineCount = activeEngineCount,
                    compact = !wide,
                    onOpenSettings = onOpenSettings,
                    onCaptureRecentProblem = onCaptureRecentProblem,
                    onStartReproductionCapture = onStartReproductionCapture,
                    onAddCaptureMarker = onAddCaptureMarker,
                    onStopCapture = onStopCapture,
                )

                if (wide) {
                    Row(modifier = Modifier.weight(1f)) {
                        PrimaryNavigationRail(
                            workspace = destination.workspace,
                            onWorkspaceSelected = onWorkspaceSelected,
                        )
                        Box(modifier = Modifier.weight(1f)) {
                            ShellContent(
                                destination = destination,
                                onDestinationSelected = onDestinationSelected,
                                settingsOpen = settingsOpen,
                                onCloseSettings = onCloseSettings,
                                runtimeSnapshot = runtimeSnapshot,
                                diagnostics = diagnostics,
                                onDiagnosticsTabSelected = onDiagnosticsTabSelected,
                                onSaveCapture = onSaveCapture,
                                onShareCapture = onShareCapture,
                            )
                        }
                    }
                } else {
                    Box(modifier = Modifier.weight(1f)) {
                        ShellContent(
                            destination = destination,
                            onDestinationSelected = onDestinationSelected,
                            settingsOpen = settingsOpen,
                            onCloseSettings = onCloseSettings,
                            runtimeSnapshot = runtimeSnapshot,
                            diagnostics = diagnostics,
                            onDiagnosticsTabSelected = onDiagnosticsTabSelected,
                            onSaveCapture = onSaveCapture,
                            onShareCapture = onShareCapture,
                        )
                    }
                    PrimaryNavigationBar(
                        workspace = destination.workspace,
                        onWorkspaceSelected = onWorkspaceSelected,
                    )
                }
            }
        }
    }
}

@Composable
private fun SystemStatusHeader(
    snapshot: State<RuntimeSnapshot?>,
    activeCapture: ActiveDiagnosticCapture?,
    engineAlive: Boolean,
    activeEngineCount: Int,
    compact: Boolean,
    onOpenSettings: () -> Unit,
    onCaptureRecentProblem: () -> Unit,
    onStartReproductionCapture: () -> Unit,
    onAddCaptureMarker: () -> Unit,
    onStopCapture: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 2.dp,
    ) {
        if (compact) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = "EpicHub",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    HeaderCaptureControls(
                        activeCapture = activeCapture,
                        compact = true,
                        onCaptureRecentProblem = onCaptureRecentProblem,
                        onStartReproductionCapture = onStartReproductionCapture,
                        onAddCaptureMarker = onAddCaptureMarker,
                        onStopCapture = onStopCapture,
                    )
                    TextButton(onClick = onOpenSettings) {
                        Text("Settings")
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = if (engineAlive) {
                            "Engine active ($activeEngineCount)"
                        } else {
                            "Engine unavailable"
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                    RuntimeSourceStatus(snapshot)
                }
            }
        } else {
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
                RuntimeSourceStatus(snapshot)
                Spacer(modifier = Modifier.weight(1f))
                HeaderCaptureControls(
                    activeCapture = activeCapture,
                    compact = false,
                    onCaptureRecentProblem = onCaptureRecentProblem,
                    onStartReproductionCapture = onStartReproductionCapture,
                    onAddCaptureMarker = onAddCaptureMarker,
                    onStopCapture = onStopCapture,
                )
                TextButton(onClick = onOpenSettings) {
                    Text("Settings")
                }
            }
        }
    }
}

@Composable
private fun RuntimeSourceStatus(snapshot: State<RuntimeSnapshot?>) {
    val current = snapshot.value
    Text(
        text = current?.let { "Source ${it.sourceKind} · #${it.sequence}" } ?: "Source waiting",
        style = MaterialTheme.typography.bodySmall,
    )
}

@Composable
private fun HeaderCaptureControls(
    activeCapture: ActiveDiagnosticCapture?,
    compact: Boolean,
    onCaptureRecentProblem: () -> Unit,
    onStartReproductionCapture: () -> Unit,
    onAddCaptureMarker: () -> Unit,
    onStopCapture: () -> Unit,
) {
    var captureMenuExpanded by remember { mutableStateOf(false) }

    if (activeCapture == null) {
        Box {
            Button(onClick = { captureMenuExpanded = true }) {
                Text(if (compact) "Problem" else "Problem capture")
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
            text = if (compact) {
                "Problem #${activeCapture.captureId}"
            } else {
                "Problem capture #${activeCapture.captureId}"
            },
            style = MaterialTheme.typography.labelMedium,
        )
        TextButton(onClick = onAddCaptureMarker) {
            Text("Mark")
        }
        Button(onClick = onStopCapture) {
            Text("Stop")
        }
    }
}

@Composable
private fun PrimaryNavigationRail(
    workspace: PrimaryWorkspace,
    onWorkspaceSelected: (PrimaryWorkspace) -> Unit,
) {
    NavigationRail(modifier = Modifier.fillMaxHeight()) {
        PrimaryWorkspace.entries.forEach { item ->
            NavigationRailItem(
                selected = item == workspace,
                onClick = { onWorkspaceSelected(item) },
                icon = { Text(item.glyph) },
                label = { Text(item.compactLabel) },
                alwaysShowLabel = true,
            )
        }
    }
}

@Composable
private fun PrimaryNavigationBar(
    workspace: PrimaryWorkspace,
    onWorkspaceSelected: (PrimaryWorkspace) -> Unit,
) {
    NavigationBar {
        PrimaryWorkspace.entries.forEach { item ->
            NavigationBarItem(
                selected = item == workspace,
                onClick = { onWorkspaceSelected(item) },
                icon = { Text(item.glyph) },
                label = { Text(item.compactLabel) },
                alwaysShowLabel = true,
            )
        }
    }
}

@Composable
private fun WorkspaceRouteTabs(
    destination: AppDestination,
    onDestinationSelected: (AppDestination) -> Unit,
) {
    val routes = AppDestination.routesFor(destination.workspace)
    if (routes.size <= 1) return

    val selectedIndex = routes.indexOf(destination).coerceAtLeast(0)
    TabRow(selectedTabIndex = selectedIndex) {
        routes.forEach { route ->
            Tab(
                selected = route == destination,
                onClick = { onDestinationSelected(route) },
                text = { Text(route.compactLabel) },
            )
        }
    }
}

@Composable
private fun ShellContent(
    destination: AppDestination,
    onDestinationSelected: (AppDestination) -> Unit,
    settingsOpen: Boolean,
    onCloseSettings: () -> Unit,
    runtimeSnapshot: State<RuntimeSnapshot?>,
    diagnostics: State<DiagnosticsSnapshot>,
    onDiagnosticsTabSelected: (DiagnosticsTab) -> Unit,
    onSaveCapture: (DiagnosticCapture) -> Unit,
    onShareCapture: (DiagnosticCapture) -> Unit,
) {
    if (settingsOpen) {
        SettingsScreen(onClose = onCloseSettings)
        return
    }

    when (destination.workspace) {
        PrimaryWorkspace.DASHBOARD -> DashboardScreen(runtimeSnapshot)
        PrimaryWorkspace.DIAGNOSTICS -> DiagnosticsScreen(
            diagnostics = diagnostics,
            onTabSelected = onDiagnosticsTabSelected,
            onSaveCapture = onSaveCapture,
            onShareCapture = onShareCapture,
        )
        PrimaryWorkspace.TUNER -> Column(modifier = Modifier.fillMaxSize()) {
            WorkspaceRouteTabs(destination, onDestinationSelected)
            Box(modifier = Modifier.weight(1f)) {
                when (destination) {
                    AppDestination.TUNING_TOOLS -> TuningToolsScreen()
                    else -> TunerScreen()
                }
            }
        }
        PrimaryWorkspace.LOGGER -> Column(modifier = Modifier.fillMaxSize()) {
            WorkspaceRouteTabs(destination, onDestinationSelected)
            Box(modifier = Modifier.weight(1f)) {
                when (destination) {
                    AppDestination.ANALYZER -> AnalyzerScreen()
                    else -> LoggerScreen(runtimeSnapshot)
                }
            }
        }
    }
}
