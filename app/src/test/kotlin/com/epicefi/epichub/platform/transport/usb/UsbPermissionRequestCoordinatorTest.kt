package com.epicefi.epichub.platform.transport.usb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UsbPermissionRequestCoordinatorTest {
    private val first = UsbPermissionTarget("usb:/dev/one:1", "/dev/one", 1)
    private val second = UsbPermissionTarget("usb:/dev/two:2", "/dev/two", 2)

    @Test
    fun alreadyGrantedDoesNotStartRequest() {
        val coordinator = UsbPermissionRequestCoordinator()
        var requests = 0

        val result = coordinator.begin(first, alreadyGranted = true) {
            requests += 1
            null
        }

        assertEquals(UsbPermissionRequestStartResult.AlreadyGranted, result)
        assertEquals(0, requests)
        assertNull(coordinator.pendingTarget)
    }

    @Test
    fun onlyOnePermissionRequestCanBePending() {
        val coordinator = UsbPermissionRequestCoordinator()

        assertEquals(
            UsbPermissionRequestStartResult.Requested,
            coordinator.begin(first, alreadyGranted = false) { null },
        )
        assertEquals(
            UsbPermissionRequestStartResult.Busy(first.endpointId),
            coordinator.begin(second, alreadyGranted = false) { null },
        )
        assertEquals(first, coordinator.pendingTarget)
    }

    @Test
    fun requestFailureClearsPendingAndAllowsRetry() {
        val coordinator = UsbPermissionRequestCoordinator()

        assertEquals(
            UsbPermissionRequestStartResult.Rejected("permission API failed"),
            coordinator.begin(first, alreadyGranted = false) { "permission API failed" },
        )
        assertNull(coordinator.pendingTarget)
        assertEquals(
            UsbPermissionRequestStartResult.Requested,
            coordinator.begin(second, alreadyGranted = false) { null },
        )
    }

    @Test
    fun mismatchedCompletionIsIgnoredWithoutConsumingActiveRequest() {
        val coordinator = UsbPermissionRequestCoordinator()
        coordinator.begin(first, alreadyGranted = false) { null }

        assertEquals(
            UsbPermissionRequestCompletion.Ignored,
            coordinator.complete(second, granted = true),
        )
        assertEquals(first, coordinator.pendingTarget)
    }

    @Test
    fun matchingGrantedCompletionClearsPending() {
        val coordinator = UsbPermissionRequestCoordinator()
        coordinator.begin(first, alreadyGranted = false) { null }

        assertEquals(
            UsbPermissionRequestCompletion.Granted(first),
            coordinator.complete(first, granted = true),
        )
        assertNull(coordinator.pendingTarget)
    }

    @Test
    fun matchingDeniedCompletionClearsPending() {
        val coordinator = UsbPermissionRequestCoordinator()
        coordinator.begin(first, alreadyGranted = false) { null }

        assertEquals(
            UsbPermissionRequestCompletion.Denied(first),
            coordinator.complete(first, granted = false),
        )
        assertNull(coordinator.pendingTarget)
    }

    @Test
    fun closeInvalidatesPendingAndRejectsFutureRequests() {
        val coordinator = UsbPermissionRequestCoordinator()
        coordinator.begin(first, alreadyGranted = false) { null }

        coordinator.close()
        coordinator.close()

        assertTrue(coordinator.isClosed)
        assertNull(coordinator.pendingTarget)
        assertEquals(
            UsbPermissionRequestStartResult.OwnerClosed,
            coordinator.begin(second, alreadyGranted = false) { null },
        )
        assertEquals(
            UsbPermissionRequestCompletion.Ignored,
            coordinator.complete(first, granted = true),
        )
    }
}
