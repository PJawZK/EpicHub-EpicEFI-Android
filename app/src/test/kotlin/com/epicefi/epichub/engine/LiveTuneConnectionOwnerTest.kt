package com.epicefi.epichub.engine

import com.epicefi.epichub.platform.transport.usb.UsbReadOnlyBulkPort
import com.epicefi.epichub.platform.transport.usb.UsbReadOnlyTransactionChannel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveTuneConnectionOwnerTest {
    @Test
    fun successfulProbePromotesExactIdentityIntoLifecycle() {
        val identity = ValidatedRuntimeIdentity("exact.signature")
        val lifecycle = FakeLifecycle()
        val probe = FakeProbe(UsbRuntimeIdentityProbeResult.Identified(identity))
        val owner = owner(lifecycle = lifecycle, probe = probe)
        val channel = channel()
        val plan = RuntimeIdentityProbePlan(byteArrayOf('S'.code.toByte()), 128, 250)

        val result = owner.probeAndConnect(channel, plan)

        assertEquals(LiveTuneConnectResult.Connected(identity), result)
        assertEquals(identity, lifecycle.connectedIdentity)
        assertSame(channel, probe.channel)
        assertSame(plan, probe.plan)
    }

    @Test
    fun rejectedProbeDoesNotMutateLifecycleConnection() {
        val lifecycle = FakeLifecycle()
        val rejection = UsbRuntimeIdentityProbeResult.TransportRejected("permission required")
        val owner = owner(lifecycle = lifecycle, probe = FakeProbe(rejection))

        val result = owner.probeAndConnect(channel(), RuntimeIdentityProbePlan(byteArrayOf(1), 8, 100))

        assertEquals(LiveTuneConnectResult.ProbeRejected(rejection), result)
        assertEquals(0, lifecycle.connectCalls)
    }

    @Test
    fun loadDelegatesExactBoundsAndReturnsCompletedResult() {
        val loaded = UsbReadOnlyTuneLoadResult.Loaded(profileGeneration = 42L, pageCount = 2)
        val lifecycle = FakeLifecycle(loadResult = loaded)
        val owner = owner(lifecycle = lifecycle)
        val channel = channel()

        val result = owner.loadCurrentController(256, channel, 750)

        assertEquals(LiveTuneOwnerLoadResult.Completed(loaded), result)
        assertEquals(256, lifecycle.signatureLimit)
        assertEquals(750, lifecycle.timeoutMs)
        assertSame(channel, lifecycle.channel)
    }

    @Test
    fun nullLifecycleLoadBecomesTypedNotLoadableState() {
        val state = LiveTuneSourceLifecycleState.Disconnected()
        val lifecycle = FakeLifecycle(initialState = state, loadResult = null)
        val owner = owner(lifecycle = lifecycle)

        val result = owner.loadCurrentController(64, channel(), 100)

        assertEquals(LiveTuneOwnerLoadResult.NotLoadable(state), result)
    }

    @Test
    fun disconnectDropsLiveAuthorityThroughLifecycleOnly() {
        val lifecycle = FakeLifecycle()
        val registry = FakeCloseable()
        val owner = owner(lifecycle = lifecycle, registry = registry)

        owner.disconnect()

        assertEquals(1, lifecycle.disconnectCalls)
        assertFalse(registry.closed)
        assertTrue(owner.isAlive)
    }

    @Test
    fun closeDisconnectsAndClosesRegistryExactlyOnce() {
        val lifecycle = FakeLifecycle()
        val registry = FakeCloseable()
        val owner = owner(lifecycle = lifecycle, registry = registry)

        owner.close()
        owner.close()

        assertEquals(1, lifecycle.disconnectCalls)
        assertEquals(1, registry.closeCalls)
        assertTrue(registry.closed)
        assertFalse(owner.isAlive)
        assertEquals(
            LiveTuneConnectResult.OwnerClosed,
            owner.probeAndConnect(channel(), RuntimeIdentityProbePlan(byteArrayOf(1), 8, 100)),
        )
        assertEquals(
            LiveTuneOwnerLoadResult.OwnerClosed,
            owner.loadCurrentController(8, channel(), 100),
        )
    }

    @Test
    fun deadRegistryMakesOwnerFailClosedWithoutProbeOrLoad() {
        val lifecycle = FakeLifecycle()
        val probe = FakeProbe(UsbRuntimeIdentityProbeResult.Identified(ValidatedRuntimeIdentity("unused")))
        val owner =
            LiveTuneConnectionOwner(
                registryOwner = FakeCloseable(),
                registryAlive = { false },
                lifecycle = lifecycle,
                identityProbe = probe,
            )

        assertEquals(
            LiveTuneConnectResult.OwnerClosed,
            owner.probeAndConnect(channel(), RuntimeIdentityProbePlan(byteArrayOf(1), 8, 100)),
        )
        assertEquals(LiveTuneOwnerLoadResult.OwnerClosed, owner.loadCurrentController(8, channel(), 100))
        assertEquals(0, probe.calls)
        assertEquals(0, lifecycle.loadCalls)
    }

    private fun owner(
        lifecycle: FakeLifecycle = FakeLifecycle(),
        probe: FakeProbe = FakeProbe(UsbRuntimeIdentityProbeResult.TransportRejected("unused")),
        registry: FakeCloseable = FakeCloseable(),
    ) =
        LiveTuneConnectionOwner(
            registryOwner = registry,
            registryAlive = { !registry.closed },
            lifecycle = lifecycle,
            identityProbe = probe,
        )

    private fun channel() = UsbReadOnlyTransactionChannel(FakeBulkPort())

    private class FakeProbe(
        private val result: UsbRuntimeIdentityProbeResult,
    ) : RuntimeIdentityProbeOperation {
        var calls = 0
            private set
        var channel: UsbReadOnlyTransactionChannel? = null
            private set
        var plan: RuntimeIdentityProbePlan? = null
            private set

        override fun probe(
            channel: UsbReadOnlyTransactionChannel,
            plan: RuntimeIdentityProbePlan,
        ): UsbRuntimeIdentityProbeResult {
            calls += 1
            this.channel = channel
            this.plan = plan
            return result
        }
    }

    private class FakeLifecycle(
        initialState: LiveTuneSourceLifecycleState = LiveTuneSourceLifecycleState.Disconnected(),
        private val loadResult: UsbReadOnlyTuneLoadResult? =
            UsbReadOnlyTuneLoadResult.TransportRejected(-1, "unused"),
    ) : LiveTuneLifecycleControl {
        override var state: LiveTuneSourceLifecycleState = initialState
            private set
        var connectedIdentity: ValidatedRuntimeIdentity? = null
            private set
        var connectCalls = 0
            private set
        var disconnectCalls = 0
            private set
        var loadCalls = 0
            private set
        var signatureLimit: Int? = null
            private set
        var timeoutMs: Int? = null
            private set
        var channel: UsbReadOnlyTransactionChannel? = null
            private set

        override fun onConnected(identity: ValidatedRuntimeIdentity) {
            connectCalls += 1
            connectedIdentity = identity
            state = LiveTuneSourceLifecycleState.Connected(identity)
        }

        override fun onDisconnected() {
            disconnectCalls += 1
            state = LiveTuneSourceLifecycleState.Disconnected()
        }

        override fun loadCurrentController(
            signatureLimit: Int,
            channel: UsbReadOnlyTransactionChannel,
            timeoutMs: Int,
        ): UsbReadOnlyTuneLoadResult? {
            loadCalls += 1
            this.signatureLimit = signatureLimit
            this.channel = channel
            this.timeoutMs = timeoutMs
            return loadResult
        }
    }

    private class FakeCloseable : AutoCloseable {
        var closed = false
            private set
        var closeCalls = 0
            private set

        override fun close() {
            closeCalls += 1
            closed = true
        }
    }

    private class FakeBulkPort : UsbReadOnlyBulkPort {
        override fun sendRequest(request: ByteArray, timeoutMs: Int): Int = request.size

        override fun receiveResponse(buffer: ByteArray, timeoutMs: Int): Int = -1

        override fun close() = Unit
    }
}
