package com.epicefi.epichub.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.epicefi.epichub.ui.components.StructuralCard

private val groups = listOf(
    "General",
    "Units & formatting",
    "Dashboard presentation",
    "Tuner presentation",
    "Tuning Tools",
    "Logging & storage",
    "Connections",
    "Warnings & alerts",
    "Accessories",
    "Diagnostics & privacy",
    "Appearance",
    "About / build information",
)

@Composable
fun SettingsScreen(
    onClose: () -> Unit,
    profilePackages: ProfilePackageSettingsState = ProfilePackageSettingsState(),
    onSelectProfilePackages: () -> Unit = {},
    onClearProfilePackages: () -> Unit = {},
    usbController: UsbControllerSettingsState = UsbControllerSettingsState(),
    onRefreshUsbCandidates: () -> Unit = {},
    onSelectUsbCandidate: (String) -> Unit = {},
    onRequestUsbPermission: () -> Unit = {},
    onConnectUsbController: () -> Unit = {},
    onDisconnectUsbController: () -> Unit = {},
    onLoadUsbTune: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Settings", style = MaterialTheme.typography.headlineMedium)
            Button(onClick = onClose) {
                Text("Back to app")
            }
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(groups) { group ->
                StructuralCard(
                    title = group,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    val purpose = when (group) {
                        "Connections" -> "Profile authority, transport access, controller connection and read-only tune loading remain explicit separate steps."
                        "Warnings & alerts" -> "Rule definitions, thresholds, debounce/cooldown and enablement live here; active incidents/history remain observable elsewhere."
                        "Diagnostics & privacy" -> "Diagnostic capture/export/privacy policy only; diagnostics do not own subsystem control."
                        else -> "Settings surface reserved for this responsibility group."
                    }
                    Text(purpose)

                    if (group == "Connections") {
                        Text("ECU profile packages", style = MaterialTheme.typography.titleSmall)
                        Text(
                            when {
                                profilePackages.busy -> "Updating profile package selection…"
                                profilePackages.hasSelection ->
                                    "${profilePackages.selectedDocumentCount} profile document(s) selected."
                                else -> "No profile package selected."
                            },
                        )
                        Text(
                            "Profile packages establish definition/profile authority only. Selecting one does not connect to USB or load an ECU tune.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                enabled = !profilePackages.busy && !usbController.operationBusy,
                                onClick = onSelectProfilePackages,
                            ) {
                                Text(if (profilePackages.hasSelection) "Replace packages" else "Select packages")
                            }
                            OutlinedButton(
                                enabled =
                                    !profilePackages.busy &&
                                        !usbController.operationBusy &&
                                        profilePackages.hasSelection,
                                onClick = onClearProfilePackages,
                            ) {
                                Text("Clear")
                            }
                        }
                        profilePackages.message?.let { message ->
                            Text(message, style = MaterialTheme.typography.bodySmall)
                        }

                        Text("USB controller access", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "USB selection and permission identify only a transport endpoint. Connect probes definition-authorized queries and requires an exact installed profile match. Load tune is a separate read-only action.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                enabled = !usbController.operationBusy,
                                onClick = onRefreshUsbCandidates,
                            ) {
                                Text("Refresh USB")
                            }
                            OutlinedButton(
                                enabled =
                                    profilePackages.hasSelection &&
                                        usbController.selectedCandidate != null &&
                                        !usbController.operationBusy &&
                                        !usbController.connected,
                                onClick = onRequestUsbPermission,
                            ) {
                                Text("Request access")
                            }
                        }

                        if (usbController.candidates.isEmpty()) {
                            Text("No USB candidates listed.", style = MaterialTheme.typography.bodySmall)
                        } else {
                            usbController.candidates.forEach { candidate ->
                                val selected = candidate.endpointId == usbController.selectedEndpointId
                                val connected = candidate.endpointId == usbController.connectedEndpointId
                                OutlinedButton(
                                    enabled = !usbController.operationBusy && !connected,
                                    onClick = { onSelectUsbCandidate(candidate.endpointId) },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(
                                        buildString {
                                            if (connected) append("Connected · ")
                                            else if (selected) append("Selected · ")
                                            append(candidate.displayName ?: candidate.endpointId)
                                        },
                                    )
                                }
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (usbController.connected) {
                                OutlinedButton(
                                    enabled = !usbController.operationBusy,
                                    onClick = onDisconnectUsbController,
                                ) {
                                    Text("Disconnect")
                                }
                                Button(
                                    enabled = !usbController.operationBusy,
                                    onClick = onLoadUsbTune,
                                ) {
                                    Text(if (usbController.loadBusy) "Loading…" else "Load tune")
                                }
                            } else {
                                Button(
                                    enabled =
                                        profilePackages.hasSelection &&
                                            usbController.selectedCandidate != null &&
                                            !usbController.operationBusy,
                                    onClick = onConnectUsbController,
                                ) {
                                    Text(if (usbController.connectionBusy) "Connecting…" else "Connect")
                                }
                            }
                        }

                        usbController.connectedSignature?.let { signature ->
                            Text(
                                "Authorized controller: $signature",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        usbController.connectedProfileGeneration?.let { generation ->
                            Text(
                                "Profile generation: $generation",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }

                        if (!profilePackages.hasSelection) {
                            Text(
                                "Select at least one ECU profile package before requesting access or connecting.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        usbController.message?.let { message ->
                            Text(message, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}
