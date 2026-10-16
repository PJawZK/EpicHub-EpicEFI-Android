package com.epicefi.epichub.engine

import com.epicefi.epichub.platform.transport.usb.UsbReadOnlyBulkPort
import com.epicefi.epichub.platform.transport.usb.UsbReadOnlyTransactionChannel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class LiveTuneRamWriteRoutingTest {
    private val identity = ValidatedRuntimeIdentity("exact.signature")
    private val source =
        RetainedLiveTuneSource(
            controllerSignature = identity.signature,
            profileGeneration = 42L,
            pageCount = 2,
        )

    @Test
    fun liveOwnerUsesExactLiveIdentityAndCallerChannelForRamWrite() {
        val lifecycle = FakeLifecycle(LiveTuneSourceLifecycleState.Live(identity, source))
        val operation = FakeRamWriteOperation()
        val owner = owner(lifecycle, operation)
        val channel = UsbReadOnlyTransactionChannel(UnusedPort())

        val result = owner.writeCurrentDraftToRam(256, channel, 1_500)

        assertEquals(
            LiveTuneOwnerRamWriteResult.Completed(operation.result),
            result,
        )
        assertEquals(identity.signature, operation.signature?.toString(Charsets.UTF_8))
        assertEquals(256, operation.signatureLimit)
        assertEquals(1_500, operation.timeoutMs)
        assertSame(channel, operation.channel)
        assertEquals(1, operation.calls)
    }

    @Test
    fun connectedButNotLoadedOwnerCannotWriteRam() {
        val lifecycle = FakeLifecycle(LiveTuneSourceLifecycleState.Connected(identity))
        val operation = FakeRamWriteOperation()
        val owner = owner(lifecycle, operation)
        val channel = UsbReadOnlyTransactionChannel(UnusedPort())

        assertEquals(
            LiveTuneOwnerRamWriteResult.NotWritable(lifecycle.state),
            owner.writeCurrentDraftToRam(256, channel, 1_500),
        )
        assertEquals(0, operation.calls)
    }

    @Test
    fun closedOwnerRejectsBeforeRamWriteOperation() {
        val lifecycle = FakeLifecycle(LiveTuneSourceLifecycleState.Live(identity, source))
        val operation = FakeRamWriteOperation()
        val owner = owner(lifecycle, operation)
        owner.close()

        assertEquals(
            LiveTuneOwnerRamWriteResult.OwnerClosed,
            owner.writeCurrentDraftToRam(
                256,
                UsbReadOnlyTransactionChannel(UnusedPort()),
                1_500,
            ),
        )
        assertEquals(0, operation.calls)
    }

    private fun owner(
        lifecycle: FakeLifecycle,
        operation: FakeRamWriteOperation,
    ): LiveTuneConnectionOwner =
        LiveTuneConnectionOwner(
            registryOwner = FakeCloseable(),
            registryAlive = { true },
            lifecycle = lifecycle,
            identityProbe = RuntimeIdentityProbeOperation { _, _ ->
                UsbRuntimeIdentityProbeResult.Identified(identity)
            },
            ramWriteOperation = operation,
        )

    private class FakeRamWriteOperation : LiveTuneRamWriteOperation {
        val result =
            LiveTuneRamWriteResult.BatchRejected(
                GuardedWriteBatchRejection.PlanFailure,
                null,
            )
        var calls = 0
        var signature: ByteArray? = null
        var signatureLimit = -1
        var timeoutMs = -1
        var channel: UsbReadOnlyTransactionChannel? = null

        override fun write(
            signatureResponse: ByteArray,
            signatureLimit: Int,
            channel: UsbReadOnlyTransactionChannel,
            timeoutMs: Int,
        ): LiveTuneRamWriteResult {
            calls += 1
            signature = signatureResponse.copyOf()
            this.signatureLimit = signatureLimit
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
