package com.epicefi.epichub.engine

import com.epicefi.epichub.platform.transport.usb.UsbReadOnlyBulkPort
import com.epicefi.epichub.platform.transport.usb.UsbReadOnlyTransactionChannel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveTuneSourceLifecycleTest {
    @Test
    fun successful_load_becomes_live_then_disconnect_retains_snapshot_as_offline_only() {
        val lifecycle = lifecycleReturning(UsbReadOnlyTuneLoadResult.Loaded(4_242L, 2))
        val identity = ValidatedRuntimeIdentity("exact.controller.signature")

        lifecycle.onConnected(identity)
        val result = lifecycle.loadCurrentController(256, emptyChannel(), 200)

        assertEquals(UsbReadOnlyTuneLoadResult.Loaded(4_242L, 2), result)
        val live = lifecycle.state as LiveTuneSourceLifecycleState.Live
        assertEquals(identity, live.identity)
        assertEquals(
            RetainedLiveTuneSource("exact.controller.signature", 4_242L, 2),
            live.source,
        )

        lifecycle.onDisconnected()
        assertEquals(
            LiveTuneSourceLifecycleState.Disconnected(live.source),
            lifecycle.state,
        )
    }

    @Test
    fun reconnect_does_not_promote_retained_snapshot_without_fresh_load() {
        val lifecycle = lifecycleReturning(UsbReadOnlyTuneLoadResult.Loaded(11L, 1))
        val first = ValidatedRuntimeIdentity("controller.one")
        lifecycle.onConnected(first)
        lifecycle.loadCurrentController(64, emptyChannel(), 100)
        val retained = (lifecycle.state as LiveTuneSourceLifecycleState.Live).source
        lifecycle.onDisconnected()

        val second = ValidatedRuntimeIdentity("controller.two")
        lifecycle.onConnected(second)

        assertEquals(
            LiveTuneSourceLifecycleState.Connected(second, retained),
            lifecycle.state,
        )
    }

    @Test
    fun failed_reload_preserves_previous_local_snapshot_but_not_live_status() {
        var calls = 0
        val lifecycle =
            LiveTuneSourceLifecycle(
                LiveTuneLoadOperation { _, _, _, _ ->
                    calls += 1
                    if (calls == 1) {
                        UsbReadOnlyTuneLoadResult.Loaded(22L, 2)
                    } else {
                        UsbReadOnlyTuneLoadResult.TransportFailure(0, "link ended")
                    }
                },
            )
        val identity = ValidatedRuntimeIdentity("controller.reload")
        lifecycle.onConnected(identity)
        lifecycle.loadCurrentController(128, emptyChannel(), 100)
        val retained = (lifecycle.state as LiveTuneSourceLifecycleState.Live).source

        val result = lifecycle.loadCurrentController(128, emptyChannel(), 100)

        assertEquals(UsbReadOnlyTuneLoadResult.TransportFailure(0, "link ended"), result)
        assertEquals(
            LiveTuneSourceLifecycleState.LoadFailed(
                identity,
                UsbReadOnlyTuneLoadResult.TransportFailure(0, "link ended"),
                retained,
            ),
            lifecycle.state,
        )
    }

    @Test
    fun disconnect_invalidates_in_flight_attempt_and_stale_completion_is_ignored() {
        val lifecycle = lifecycleReturning(UsbReadOnlyTuneLoadResult.Loaded(1L, 1))
        val identity = ValidatedRuntimeIdentity("controller.stale")
        lifecycle.onConnected(identity)
        val attempt = lifecycle.beginLoad()!!

        lifecycle.onDisconnected()
        val accepted =
            lifecycle.completeLoad(
                attempt,
                UsbReadOnlyTuneLoadResult.Loaded(99L, 3),
            )

        assertFalse(accepted)
        assertEquals(LiveTuneSourceLifecycleState.Disconnected(), lifecycle.state)
    }

    @Test
    fun reconnect_invalidates_previous_attempt_even_for_same_signature() {
        val lifecycle = lifecycleReturning(UsbReadOnlyTuneLoadResult.Loaded(1L, 1))
        val identity = ValidatedRuntimeIdentity("same.signature")
        lifecycle.onConnected(identity)
        val firstAttempt = lifecycle.beginLoad()!!
        lifecycle.onConnected(identity)
        val secondAttempt = lifecycle.beginLoad()!!

        assertTrue(secondAttempt.id > firstAttempt.id)
        assertFalse(
            lifecycle.completeLoad(
                firstAttempt,
                UsbReadOnlyTuneLoadResult.Loaded(7L, 1),
            ),
        )
        assertTrue(
            lifecycle.completeLoad(
                secondAttempt,
                UsbReadOnlyTuneLoadResult.Loaded(8L, 2),
            ),
        )
        assertEquals(
            RetainedLiveTuneSource("same.signature", 8L, 2),
            (lifecycle.state as LiveTuneSourceLifecycleState.Live).source,
        )
    }

    @Test
    fun load_requires_connected_identity_and_forwards_exact_signature_bytes() {
        var seenSignature: ByteArray? = null
        val lifecycle =
            LiveTuneSourceLifecycle(
                LiveTuneLoadOperation { signature, _, _, _ ->
                    seenSignature = signature.copyOf()
                    UsbReadOnlyTuneLoadResult.Loaded(5L, 1)
                },
            )

        assertNull(lifecycle.loadCurrentController(64, emptyChannel(), 100))
        lifecycle.onConnected(ValidatedRuntimeIdentity("rusEFI exact.ä"))
        lifecycle.loadCurrentController(64, emptyChannel(), 100)

        assertEquals("rusEFI exact.ä", seenSignature!!.toString(Charsets.UTF_8))
    }

    private fun lifecycleReturning(result: UsbReadOnlyTuneLoadResult): LiveTuneSourceLifecycle =
        LiveTuneSourceLifecycle(LiveTuneLoadOperation { _, _, _, _ -> result })

    private fun emptyChannel(): UsbReadOnlyTransactionChannel =
        UsbReadOnlyTransactionChannel(
            object : UsbReadOnlyBulkPort {
                override fun sendRequest(request: ByteArray, timeoutMs: Int): Int = request.size

                override fun receiveResponse(buffer: ByteArray, timeoutMs: Int): Int = 0

                override fun close() = Unit
            },
        )
}
