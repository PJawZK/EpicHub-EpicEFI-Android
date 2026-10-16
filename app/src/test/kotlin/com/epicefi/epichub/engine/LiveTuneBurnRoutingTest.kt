package com.epicefi.epichub.engine

import com.epicefi.epichub.platform.transport.usb.UsbReadOnlyBulkPort
import com.epicefi.epichub.platform.transport.usb.UsbReadOnlyTransactionChannel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class LiveTuneBurnRoutingTest {
    private val identity = ValidatedRuntimeIdentity("exact.signature")
    private val source =
        RetainedLiveTuneSource(
            controllerSignature = identity.signature,
            profileGeneration = 42L,
            pageCount = 2,
        )

    @Test
    fun liveOwnerUsesExactLiveIdentityCallerChannelAndDefinitionPageForBurn() {
        val lifecycle = FakeLifecycle(LiveTuneSourceLifecycleState.Live(identity, source))
        val operation = FakeBurnOperation()
        val owner = owner(lifecycle, operation)
        val channel = UsbReadOnlyTransactionChannel(UnusedPort())

        val result = owner.burnPageToNonvolatile(2, 256, channel, 1_500)

        assertEquals(
            LiveTuneOwnerBurnResult.Completed(operation.result),
            result,
        )
        assertEquals(identity.signature, operation.signature?.toString(Charsets.UTF_8))
        assertEquals(256, operation.signatureLimit)
        assertEquals(2, operation.page)
        assertEquals(1_500, operation.timeoutMs)
        assertSame(channel, operation.channel)
        assertEquals(1, operation.calls)
    }

    @Test
    fun connectedButNotLoadedOwnerCannotBurn() {
        val lifecycle = FakeLifecycle(LiveTuneSourceLifecycleState.Connected(identity))
        val operation = FakeBurnOperation()
        val owner = owner(lifecycle, operation)

        assertEquals(
            LiveTuneOwnerBurnResult.NotBurnable(lifecycle.state),
            owner.burnPageToNonvolatile(
                1,
                256,
                UsbReadOnlyTransactionChannel(UnusedPort()),
                1_500,
            ),
        )
        assertEquals(0, operation.calls)
    }

    @Test
    fun closedOwnerRejectsBeforeBurnOperation() {
        val lifecycle = FakeLifecycle(LiveTuneSourceLifecycleState.Live(identity, source))
        val operation = FakeBurnOperation()
        val owner = owner(lifecycle, operation)
        owner.close()

        assertEquals(
            LiveTuneOwnerBurnResult.OwnerClosed,
            owner.burnPageToNonvolatile(
                1,
                256,
                UsbReadOnlyTransactionChannel(UnusedPort()),
                1_500,
            ),
        )
        assertEquals(0, operation.calls)
    }

    private fun owner(
        lifecycle: FakeLifecycle,
        operation: FakeBurnOperation,
    ): LiveTuneConnectionOwner =
        LiveTuneConnectionOwner(
            registryOwner = FakeCloseable(),
            registryAlive = { true },
            lifecycle = lifecycle,
            identityProbe = RuntimeIdentityProbeOperation { _, _ ->
                UsbRuntimeIdentityProbeResult.Identified(identity)
            },
            burnOperation = operation,
        )

    private class FakeBurnOperation : LiveTuneBurnOperation {
        val result = LiveTuneBurnResult.PlanRejected(GuardedBurnRejection.PlanFailure)
        var calls = 0
        var signature: ByteArray? = null
        var signatureLimit = -1
        var page = -1
        var timeoutMs = -1
        var channel: UsbReadOnlyTransactionChannel? = null

        override fun burn(
            signatureResponse: ByteArray,
            signatureLimit: Int,
            page: Int,
            channel: UsbReadOnlyTransactionChannel,
            timeoutMs: Int,
        ): LiveTuneBurnResult {
            calls += 1
            signature = signatureResponse.copyOf()
            this.signatureLimit = signatureLimit
            this.page = page
            this.timeoutMs = timeoutMs
            this.channel = channel
            return result
        }
    }

    private class FakeLifecycle(
        override var state: LiveTuneSourceLifecycleState,
    ) : LiveTuneLifecycleControl {
        override fun onConnected(identity: ValidatedRuntimeIdentity) {
            state = LiveTuneSourceLifecycleState.Connected(identity)
        }

        override fun onDisconnected() {
            state = LiveTuneSourceLifecycleState.Disconnected()
        }

        override fun loadCurrentController(
            signatureLimit: Int,
            channel: UsbReadOnlyTransactionChannel,
            timeoutMs: Int,
        ): UsbReadOnlyTuneLoadResult? = null
    }

    private class FakeCloseable : AutoCloseable {
        override fun close() = Unit
    }

    private class UnusedPort : UsbReadOnlyBulkPort {
        override fun sendRequest(request: ByteArray, timeoutMs: Int): Int =
            throw AssertionError("transport should not be used by routing test")

        override fun receiveResponse(buffer: ByteArray, timeoutMs: Int): Int =
            throw AssertionError("transport should not be used by routing test")

        override fun close() = Unit
    }
}
