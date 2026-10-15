package com.epicefi.epichub.ui.settings

import com.epicefi.epichub.engine.LiveTuneOwnerLoadResult
import com.epicefi.epichub.engine.UsbReadOnlyTuneLoadResult
import com.epicefi.epichub.engine.ValidatedRuntimeIdentity
import com.epicefi.epichub.platform.transport.TransportCandidate
import com.epicefi.epichub.platform.transport.TransportKind
import com.epicefi.epichub.platform.transport.usb.AndroidUsbLiveConnectResult
import com.epicefi.epichub.platform.transport.usb.AndroidUsbLiveLoadResult
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
        assertFalse(state.connected)
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
        assertFalse(state.connected)
        assertEquals(first, state.selectedCandidate)
        assertTrue(state.message.orEmpty().contains("Use Connect"))
    }

    @Test
    fun successfulConnectTracksExactEndpointIdentityAndProfileWithoutClaimingTuneLoad() {
        val state =
            UsbControllerSettingsState(candidates = listOf(first), selectedEndpointId = first.endpointId)
                .connectionStarted()
                .connectionCompleted(
                    AndroidUsbLiveConnectResult.Connected(
                        endpointId = first.endpointId,
                        identity = ValidatedRuntimeIdentity("exact.signature"),
                        profileGeneration = 42L,
                    ),
                )

        assertTrue(state.connected)
        assertEquals(first.endpointId, state.connectedEndpointId)
        assertEquals("exact.signature", state.connectedSignature)
        assertEquals(42L, state.connectedProfileGeneration)
        assertFalse(state.connectionBusy)
        assertFalse(state.loadBusy)
        assertTrue(state.message.orEmpty().contains("not loaded yet"))
    }

    @Test
    fun successfulLoadKeepsConnectionAndReportsAtomicPageInstall() {
        val connected =
            UsbControllerSettingsState(
                candidates = listOf(first),
                selectedEndpointId = first.endpointId,
                connectedEndpointId = first.endpointId,
                connectedSignature = "exact.signature",
                connectedProfileGeneration = 42L,
            )

        val loaded =
            connected.loadStarted().loadCompleted(
                AndroidUsbLiveLoadResult.Completed(
                    LiveTuneOwnerLoadResult.Completed(
                        UsbReadOnlyTuneLoadResult.Loaded(
                            profileGeneration = 42L,
                            pageCount = 2,
                        ),
                    ),
                ),
            )

        assertTrue(loaded.connected)
        assertFalse(loaded.loadBusy)
        assertTrue(loaded.message.orEmpty().contains("2 page(s)"))
    }

    @Test
    fun disconnectClearsOnlyLiveConnectionMetadata() {
        val state =
            UsbControllerSettingsState(
                candidates = listOf(first),
                selectedEndpointId = first.endpointId,
                connectedEndpointId = first.endpointId,
                connectedSignature = "exact.signature",
                connectedProfileGeneration = 42L,
                message = "old",
            ).disconnected()

        assertFalse(state.connected)
        assertEquals(first.endpointId, state.selectedEndpointId)
        assertNull(state.connectedSignature)
        assertNull(state.connectedProfileGeneration)
        assertTrue(state.message.orEmpty().contains("detached"))
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
