package com.epicefi.epichub.ui.settings

import com.epicefi.epichub.platform.transport.TransportCandidate
import com.epicefi.epichub.platform.transport.TransportKind
import com.epicefi.epichub.platform.transport.usb.UsbPermissionRequestCompletion
import com.epicefi.epichub.platform.transport.usb.UsbPermissionRequestStartResult
import com.epicefi.epichub.platform.transport.usb.UsbPermissionTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UsbControllerSettingsStateTest {
    private val first =
        TransportCandidate(
            kind = TransportKind.USB,
            endpointId = "usb:/dev/one:1",
            displayName = "Controller one",
        )
    private val second =
        TransportCandidate(
            kind = TransportKind.USB,
            endpointId = "usb:/dev/two:2",
            displayName = "Controller two",
        )

    @Test
    fun refreshDoesNotAutoSelectCandidate() {
        val state = UsbControllerSettingsState().refresh(listOf(first, second))

        assertEquals(2, state.candidates.size)
        assertNull(state.selectedCandidate)
        assertFalse(state.permissionPending)
    }

    @Test
    fun refreshRetainsOnlyAttachedSelectionAndPendingRequest() {
        val state =
            UsbControllerSettingsState(
                candidates = listOf(first, second),
                selectedEndpointId = first.endpointId,
                permissionPendingEndpointId = first.endpointId,
            ).refresh(listOf(first))

        assertEquals(first, state.selectedCandidate)
        assertEquals(first.endpointId, state.permissionPendingEndpointId)

        val detached = state.refresh(listOf(second))
        assertNull(detached.selectedCandidate)
        assertNull(detached.permissionPendingEndpointId)
        assertTrue(detached.message.orEmpty().contains("no longer attached"))
    }

    @Test
    fun requestedPermissionTracksSelectedEndpoint() {
        val state =
            UsbControllerSettingsState()
                .refresh(listOf(first))
                .select(first.endpointId)
                .permissionStarted(UsbPermissionRequestStartResult.Requested)

        assertTrue(state.permissionPending)
        assertEquals(first.endpointId, state.permissionPendingEndpointId)
    }

    @Test
    fun grantedPermissionClearsPendingButDoesNotClaimConnection() {
        val target = UsbPermissionTarget(first.endpointId, "/dev/one", 1)
        val state =
            UsbControllerSettingsState(
                candidates = listOf(first),
                selectedEndpointId = first.endpointId,
                permissionPendingEndpointId = first.endpointId,
            ).permissionCompleted(UsbPermissionRequestCompletion.Granted(target))

        assertFalse(state.permissionPending)
        assertEquals(first, state.selectedCandidate)
        assertTrue(state.message.orEmpty().contains("separate action"))
    }

    @Test
    fun busyPermissionPreservesActiveEndpoint() {
        val state =
            UsbControllerSettingsState(candidates = listOf(first, second), selectedEndpointId = second.endpointId)
                .permissionStarted(UsbPermissionRequestStartResult.Busy(first.endpointId))

        assertEquals(first.endpointId, state.permissionPendingEndpointId)
        assertEquals(second, state.selectedCandidate)
    }

    @Test
    fun ignoredCompletionDoesNotMutateState() {
        val state = UsbControllerSettingsState(candidates = listOf(first), selectedEndpointId = first.endpointId)

        assertEquals(state, state.permissionCompleted(UsbPermissionRequestCompletion.Ignored))
    }
}
