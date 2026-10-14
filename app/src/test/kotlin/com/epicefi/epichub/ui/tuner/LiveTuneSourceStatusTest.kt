package com.epicefi.epichub.ui.tuner

import com.epicefi.epichub.engine.LiveTuneSourceLifecycleState
import com.epicefi.epichub.engine.ReadOnlyPageAuthorityRejection
import com.epicefi.epichub.engine.RetainedLiveTuneSource
import com.epicefi.epichub.engine.UsbReadOnlyTuneLoadResult
import com.epicefi.epichub.engine.ValidatedRuntimeIdentity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LiveTuneSourceStatusTest {
    private val identity = ValidatedRuntimeIdentity("rusEFI exact.signature")
    private val retained =
        RetainedLiveTuneSource(
            controllerSignature = "rusEFI retained.signature",
            profileGeneration = 42L,
            pageCount = 2,
        )

    @Test
    fun disconnectedRetainedTuneIsNotReportedAsLive() {
        val status = LiveTuneSourceLifecycleState.Disconnected(retained).toTunerStatus()

        assertEquals(LiveTuneSourceStatusKind.DISCONNECTED, status.kind)
        assertNull(status.controllerSignature)
        assertEquals(retained.controllerSignature, status.retainedControllerSignature)
        assertEquals(42L, status.profileGeneration)
        assertEquals(2, status.pageCount)
        assertTrue(status.retainedLocalTune)
        assertFalse(status.liveCurrent)
    }

    @Test
    fun connectedStateRequiresFreshLoadEvenWithRetainedTune() {
        val status = LiveTuneSourceLifecycleState.Connected(identity, retained).toTunerStatus()

        assertEquals(LiveTuneSourceStatusKind.CONNECTED, status.kind)
        assertEquals(identity.signature, status.controllerSignature)
        assertTrue(status.retainedLocalTune)
        assertFalse(status.liveCurrent)
    }

    @Test
    fun loadingCarriesAttemptAndDoesNotPromoteRetainedTune() {
        val status =
            LiveTuneSourceLifecycleState.Loading(
                identity = identity,
                attemptId = 7L,
                retainedSource = retained,
            ).toTunerStatus()

        assertEquals(LiveTuneSourceStatusKind.LOADING, status.kind)
        assertEquals(7L, status.attemptId)
        assertTrue(status.retainedLocalTune)
        assertFalse(status.liveCurrent)
    }

    @Test
    fun liveStateIsTheOnlyStateMarkedCurrent() {
        val source = retained.copy(controllerSignature = identity.signature)
        val status = LiveTuneSourceLifecycleState.Live(identity, source).toTunerStatus()

        assertEquals(LiveTuneSourceStatusKind.LIVE, status.kind)
        assertEquals(identity.signature, status.controllerSignature)
        assertEquals(identity.signature, status.retainedControllerSignature)
        assertTrue(status.retainedLocalTune)
        assertTrue(status.liveCurrent)
        assertNull(status.failureKind)
    }

    @Test
    fun failedLoadPreservesRetainedTuneWithoutLiveStatus() {
        val status =
            LiveTuneSourceLifecycleState.LoadFailed(
                identity = identity,
                failure =
                    UsbReadOnlyTuneLoadResult.AuthorityRejected(
                        ReadOnlyPageAuthorityRejection.UnknownController,
                    ),
                retainedSource = retained,
            ).toTunerStatus()

        assertEquals(LiveTuneSourceStatusKind.LOAD_FAILED, status.kind)
        assertEquals(LiveTuneSourceFailureKind.AUTHORITY_REJECTED, status.failureKind)
        assertTrue(status.retainedLocalTune)
        assertFalse(status.liveCurrent)
    }

    @Test
    fun failureKindsRemainDistinctForPresentation() {
        val failures =
            listOf(
                UsbReadOnlyTuneLoadResult.TransportRejected(0, "permission") to
                    LiveTuneSourceFailureKind.TRANSPORT_REJECTED,
                UsbReadOnlyTuneLoadResult.TransportFailure(1, "usb") to
                    LiveTuneSourceFailureKind.TRANSPORT_FAILURE,
                UsbReadOnlyTuneLoadResult.InstallRejected(
                    com.epicefi.epichub.engine.LiveTuneInstallRejection.DecodeFailure,
                ) to LiveTuneSourceFailureKind.INSTALL_REJECTED,
            )

        failures.forEach { (failure, expected) ->
            val status =
                LiveTuneSourceLifecycleState.LoadFailed(identity, failure, retained).toTunerStatus()
            assertEquals(expected, status.failureKind)
            assertFalse(status.liveCurrent)
        }
    }
}
