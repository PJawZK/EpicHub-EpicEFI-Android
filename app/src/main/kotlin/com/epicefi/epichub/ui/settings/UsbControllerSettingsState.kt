package com.epicefi.epichub.ui.settings

import com.epicefi.epichub.engine.LiveTuneOwnerLoadResult
import com.epicefi.epichub.engine.UsbReadOnlyTuneLoadResult
import com.epicefi.epichub.platform.transport.TransportCandidate
import com.epicefi.epichub.platform.transport.TransportKind
import com.epicefi.epichub.platform.transport.usb.AndroidUsbLiveConnectResult
import com.epicefi.epichub.platform.transport.usb.AndroidUsbLiveLoadResult
import com.epicefi.epichub.platform.transport.usb.UsbPermissionRequestCompletion
import com.epicefi.epichub.platform.transport.usb.UsbPermissionRequestStartResult

data class UsbControllerSettingsState(
    val candidates: List<TransportCandidate> = emptyList(),
    val selectedEndpointId: String? = null,
    val permissionPendingEndpointId: String? = null,
    val connectedEndpointId: String? = null,
    val connectedSignature: String? = null,
    val connectedProfileGeneration: Long? = null,
    val connectionBusy: Boolean = false,
    val loadBusy: Boolean = false,
    val message: String? = null,
) {
    val selectedCandidate: TransportCandidate?
        get() = candidates.firstOrNull { it.endpointId == selectedEndpointId }

    val permissionPending: Boolean
        get() = permissionPendingEndpointId != null

    val connected: Boolean
        get() = connectedEndpointId != null

    val operationBusy: Boolean
        get() = permissionPending || connectionBusy || loadBusy

    fun refresh(discovered: List<TransportCandidate>): UsbControllerSettingsState {
        val usbCandidates = discovered.filter { it.kind == TransportKind.USB }
        val retainedSelection = selectedEndpointId?.takeIf { selected ->
            usbCandidates.any { it.endpointId == selected }
        }
        val retainedPending = permissionPendingEndpointId?.takeIf { pending ->
            usbCandidates.any { it.endpointId == pending }
        }
        return copy(
            candidates = usbCandidates,
            selectedEndpointId = retainedSelection,
            permissionPendingEndpointId = retainedPending,
            message =
                when {
                    retainedPending == null && permissionPendingEndpointId != null ->
                        "The USB device awaiting permission is no longer attached."
                    usbCandidates.isEmpty() -> "No USB transport candidates found."
                    else -> null
                },
        )
    }

    fun select(endpointId: String): UsbControllerSettingsState =
        if (candidates.any { it.endpointId == endpointId }) {
            copy(selectedEndpointId = endpointId, message = null)
        } else {
            copy(message = "Selected USB candidate is no longer available.")
        }

    fun permissionStarted(result: UsbPermissionRequestStartResult): UsbControllerSettingsState =
        when (result) {
            UsbPermissionRequestStartResult.AlreadyGranted ->
                copy(permissionPendingEndpointId = null, message = "USB access is already granted for the selected device.")
            UsbPermissionRequestStartResult.Requested ->
                copy(
                    permissionPendingEndpointId = selectedEndpointId,
                    message = "Waiting for Android USB permission response…",
                )
            is UsbPermissionRequestStartResult.Busy ->
                copy(
                    permissionPendingEndpointId = result.activeEndpointId,
                    message = "A USB permission request is already in progress.",
                )
            is UsbPermissionRequestStartResult.Rejected ->
                copy(permissionPendingEndpointId = null, message = result.reason)
            UsbPermissionRequestStartResult.OwnerClosed ->
                copy(permissionPendingEndpointId = null, message = "USB permission owner is closed.")
        }

    fun permissionCompleted(result: UsbPermissionRequestCompletion): UsbControllerSettingsState =
        when (result) {
            is UsbPermissionRequestCompletion.Granted ->
                copy(
                    permissionPendingEndpointId = null,
                    selectedEndpointId = result.target.endpointId.takeIf { endpoint ->
                        candidates.any { it.endpointId == endpoint }
                    } ?: selectedEndpointId,
                    message = "USB access granted. Use Connect to identify and authorize the controller.",
                )
            is UsbPermissionRequestCompletion.Denied ->
                copy(
                    permissionPendingEndpointId = null,
                    message = "USB access was denied for the selected device.",
                )
            UsbPermissionRequestCompletion.Ignored -> this
        }

    fun connectionStarted(): UsbControllerSettingsState =
        copy(
            connectionBusy = true,
            loadBusy = false,
            message = "Connecting and verifying exact controller identity…",
        )

    fun connectionCompleted(result: AndroidUsbLiveConnectResult): UsbControllerSettingsState =
        when (result) {
            is AndroidUsbLiveConnectResult.Connected ->
                copy(
                    selectedEndpointId = result.endpointId,
                    connectedEndpointId = result.endpointId,
                    connectedSignature = result.identity.signature,
                    connectedProfileGeneration = result.profileGeneration,
                    connectionBusy = false,
                    loadBusy = false,
                    message = "Controller connected and exact profile authorized. Tune pages are not loaded yet.",
                )
            AndroidUsbLiveConnectResult.ProfileAuthorityUnavailable ->
                disconnected("Select a valid ECU profile package before connecting.")
            AndroidUsbLiveConnectResult.DeviceUnavailable ->
                disconnected("Selected USB device is no longer attached.")
            AndroidUsbLiveConnectResult.PermissionRequired ->
                disconnected("USB permission is required before connecting.")
            is AndroidUsbLiveConnectResult.TopologyRejected ->
                disconnected("USB CDC topology rejected: ${result.reason}")
            is AndroidUsbLiveConnectResult.OpenFailed ->
                disconnected("USB open failed: ${result.reason}")
            is AndroidUsbLiveConnectResult.ClaimFailed ->
                disconnected("USB interface claim failed: ${result.reason}")
            is AndroidUsbLiveConnectResult.AuthorityRejected ->
                disconnected("Controller identity/profile authorization failed: ${result.result}")
            is AndroidUsbLiveConnectResult.InvalidSelection ->
                disconnected(result.reason)
            AndroidUsbLiveConnectResult.OwnerClosed ->
                disconnected("USB connection owner is closed.")
        }

    fun loadStarted(): UsbControllerSettingsState =
        copy(loadBusy = true, connectionBusy = false, message = "Reading and validating complete ECU tune pages…")

    fun loadCompleted(result: AndroidUsbLiveLoadResult): UsbControllerSettingsState =
        when (result) {
            is AndroidUsbLiveLoadResult.Completed ->
                when (val ownerResult = result.result) {
                    is LiveTuneOwnerLoadResult.Completed ->
                        when (val loaded = ownerResult.result) {
                            is UsbReadOnlyTuneLoadResult.Loaded ->
                                copy(
                                    loadBusy = false,
                                    message =
                                        "Live tune loaded atomically: ${loaded.pageCount} page(s), profile generation ${loaded.profileGeneration}.",
                                )
                            else -> copy(loadBusy = false, message = "Tune load rejected: $loaded")
                        }
                    is LiveTuneOwnerLoadResult.NotLoadable ->
                        copy(loadBusy = false, message = "Controller is not loadable in state ${ownerResult.state}.")
                    LiveTuneOwnerLoadResult.OwnerClosed ->
                        disconnected("Live tune connection owner is closed.")
                }
            AndroidUsbLiveLoadResult.NotConnected ->
                disconnected("Connect and authorize the controller before loading its tune.")
            AndroidUsbLiveLoadResult.OwnerClosed ->
                disconnected("USB connection owner is closed.")
        }

    fun disconnected(message: String? = "Controller disconnected. Local tune data, if any, remains detached from the ECU."): UsbControllerSettingsState =
        copy(
            connectedEndpointId = null,
            connectedSignature = null,
            connectedProfileGeneration = null,
            connectionBusy = false,
            loadBusy = false,
            message = message,
        )
}
