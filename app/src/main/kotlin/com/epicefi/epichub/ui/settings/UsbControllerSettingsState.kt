package com.epicefi.epichub.ui.settings

import com.epicefi.epichub.platform.transport.TransportCandidate
import com.epicefi.epichub.platform.transport.TransportKind
import com.epicefi.epichub.platform.transport.usb.UsbPermissionRequestCompletion
import com.epicefi.epichub.platform.transport.usb.UsbPermissionRequestStartResult

data class UsbControllerSettingsState(
    val candidates: List<TransportCandidate> = emptyList(),
    val selectedEndpointId: String? = null,
    val permissionPendingEndpointId: String? = null,
    val message: String? = null,
) {
    val selectedCandidate: TransportCandidate?
        get() = candidates.firstOrNull { it.endpointId == selectedEndpointId }

    val permissionPending: Boolean
        get() = permissionPendingEndpointId != null

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
                    message = "USB access granted. Controller connection remains a separate action.",
                )
            is UsbPermissionRequestCompletion.Denied ->
                copy(
                    permissionPendingEndpointId = null,
                    message = "USB access was denied for the selected device.",
                )
            UsbPermissionRequestCompletion.Ignored -> this
        }
}
