package com.epicefi.epichub.platform.transport.usb

import com.epicefi.epichub.engine.DefinitionBundleImport
import com.epicefi.epichub.engine.DefinitionProbeQueryCompileOperation
import com.epicefi.epichub.engine.DefinitionProbeQueryCompileResult
import com.epicefi.epichub.engine.GuardedWriteBatchRejection
import com.epicefi.epichub.engine.LiveTuneConnectionOwner
import com.epicefi.epichub.engine.LiveTuneLifecycleControl
import com.epicefi.epichub.engine.LiveTuneOwnerRamWriteResult
import com.epicefi.epichub.engine.LiveTuneRamWriteOperation
import com.epicefi.epichub.engine.LiveTuneRamWriteResult
import com.epicefi.epichub.engine.LiveTuneSourceLifecycleState
import com.epicefi.epichub.engine.RetainedLiveTuneSource
import com.epicefi.epichub.engine.RuntimeIdentityProbeOperation
import com.epicefi.epichub.engine.RuntimeProfileSelectionOperation
import com.epicefi.epichub.engine.RuntimeProfileSelectionResult
import com.epicefi.epichub.engine.UsbReadOnlyTuneLoadResult
import com.epicefi.epichub.engine.UsbRuntimeIdentityProbeResult
import com.epicefi.epichub.engine.ValidatedRuntimeIdentity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidUsbLiveRamWriteSessionTest {
    @Test
    fun retainedAuthorizedUsbChannelIsUsedForCurrentDraftRamWrite() {
        val identity = ValidatedRuntimeIdentity("exact.signature")
        val lifecycle = FakeLifecycle()
        val writeOperation = FakeRamWriteOperation()
        val owner = owner(identity, lifecycle, writeOperation)
        val port = FakeBulkPort()
        val channel = UsbReadOnlyTransactionChannel(port)
        val session =
            AndroidUsbLiveConnectionSessionOwner(
                { owner },
                AndroidUsbEndpointChannelOpener {
                    AndroidUsbEndpointOpenResult.Opened(channel)
                },
            )

        assertTrue(session.connect("usb:test", 256, 500) is AndroidUsbLiveConnectResult.Connected)
        lifecycle.markLive(identity)

        val result = session.writeCurrentDraftToRam(256, 1_500)

        assertEquals(
            AndroidUsbLiveRamWriteResult.Completed(
                LiveTuneOwnerRamWriteResult.Completed(writeOperation.result),
            ),
            result,
        )
        assertSame(channel, writeOperation.channel)
        assertEquals(identity.signature, writeOperation.signature?.toString(Charsets.UTF_8))
        assertEquals(1, writeOperation.calls)
    }

    @Test
    fun disconnectedSessionCannotReachRamWriteAuthority() {
        val identity = ValidatedRuntimeIdentity("exact.signature")
        val lifecycle = FakeLifecycle()
        val writeOperation = FakeRamWriteOperation()
        val owner = owner(identity, lifecycle, writeOperation)
        val session =
            AndroidUsbLiveConnectionSessionOwner(
                { owner },
                AndroidUsbEndpointChannelOpener {
                    AndroidUsbEndpointOpenResult.Opened(
                        UsbReadOnlyTransactionChannel(FakeBulkPort()),
                    )
                },
            )

        assertEquals(
            AndroidUsbLiveRamWriteResult.NotConnected,
            session.writeCurrentDraftToRam(256, 1_500),
        )
        assertEquals(0, writeOperation.calls)
    }

    @Test
    fun replacingSelectedProfileBeforeWriteRevokesOldOwnerWithoutIssuingWrite() {
        val identity = ValidatedRuntimeIdentity("exact.signature")
        val lifecycle = FakeLifecycle()
        val operation = FakeRamWriteOperation()
        val first = owner(identity, lifecycle, operation)
        val replacement = owner(identity, FakeLifecycle(), FakeRamWriteOperation())
        var selected = first
        val port = FakeBulkPort()
        val session = AndroidUsbLiveConnectionSessionOwner(
            { selected },
            AndroidUsbEndpointChannelOpener {
                AndroidUsbEndpointOpenResult.Opened(UsbReadOnlyTransactionChannel(port))
            },
        )
        assertTrue(session.connect("usb:test", 256, 500) is AndroidUsbLiveConnectResult.Connected)
        lifecycle.markLive(identity)
        selected = replacement
        assertEquals(AndroidUsbLiveRamWriteResult.NotConnected, session.writeCurrentDraftToRam(256, 500))
        assertEquals(0, operation.calls)
        assertTrue(port.closed)
    }

    @Test
    fun losingUsbGrantWithinWriteCallbackCannotReportCompletedRamWrite() {
        val identity = ValidatedRuntimeIdentity("exact.signature")
        val lifecycle = FakeLifecycle()
        val operation = FakeRamWriteOperation()
        val first = owner(identity, lifecycle, operation)
        var permitted = true
        val port = FakeBulkPort()
        val session = AndroidUsbLiveConnectionSessionOwner(
            { first },
            AndroidUsbEndpointChannelOpener {
                AndroidUsbEndpointOpenResult.Opened(UsbReadOnlyTransactionChannel(port))
            },
            endpointAccess = AndroidUsbEndpointAccess { permitted },
        )
        assertTrue(session.connect("usb:test", 256, 500) is AndroidUsbLiveConnectResult.Connected)
        lifecycle.markLive(identity)
        operation.beforeReturn = { permitted = false }
        assertEquals(AndroidUsbLiveRamWriteResult.NotConnected, session.writeCurrentDraftToRam(256, 500))
        assertEquals(1, operation.calls)
        assertTrue(port.closed)
        assertTrue(!session.isConnected)
    }

    private fun owner(
        identity: ValidatedRuntimeIdentity,
        lifecycle: FakeLifecycle,
        writeOperation: FakeRamWriteOperation,
    ): LiveTuneConnectionOwner =
        LiveTuneConnectionOwner(
            registryOwner = FakeCloseable(),
            registryAlive = { true },
            lifecycle = lifecycle,
            identityProbe =
                RuntimeIdentityProbeOperation { _, _ ->
                    UsbRuntimeIdentityProbeResult.Identified(identity)
                },
            definitionBundles = listOf(bundle()),
            profileSelector =
                RuntimeProfileSelectionOperation { _, _ ->
                    RuntimeProfileSelectionResult.Selected(42L)
                },
            probeQueryCompiler =
                DefinitionProbeQueryCompileOperation {
                    DefinitionProbeQueryCompileResult.Compiled(byteArrayOf('S'.code.toByte()))
                },
            ramWriteOperation = writeOperation,
        )

    private fun bundle() =
        DefinitionBundleImport(
            profileName = "test",
            profileGeneration = 42L,
            definitionFingerprint = null,
            rootDefinition = "root",
            semanticTables = emptyList(),
        )

    private class FakeLifecycle : LiveTuneLifecycleControl {
        override var state: LiveTuneSourceLifecycleState = LiveTuneSourceLifecycleState.Disconnected()
            private set

        override fun onConnected(identity: ValidatedRuntimeIdentity) {
            state = LiveTuneSourceLifecycleState.Connected(identity)
        }

        fun markLive(identity: ValidatedRuntimeIdentity) {
            state =
                LiveTuneSourceLifecycleState.Live(
                    identity,
                    RetainedLiveTuneSource(
                        controllerSignature = identity.signature,
                        profileGeneration = 42L,
                        pageCount = 2,
                    ),
                )
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

    private class FakeRamWriteOperation : LiveTuneRamWriteOperation {
        val result =
            LiveTuneRamWriteResult.BatchRejected(
                GuardedWriteBatchRejection.PlanFailure,
                null,
            )
        var calls = 0
        var channel: UsbReadOnlyTransactionChannel? = null
        var signature: ByteArray? = null
        var beforeReturn: (() -> Unit)? = null

        override fun write(
            signatureResponse: ByteArray,
            signatureLimit: Int,
            channel: UsbReadOnlyTransactionChannel,
            timeoutMs: Int,
        ): LiveTuneRamWriteResult {
            calls += 1
            this.channel = channel
            signature = signatureResponse.copyOf()
            beforeReturn?.invoke()
            return result
        }
    }

    private class FakeCloseable : AutoCloseable {
        override fun close() = Unit
    }

    private class FakeBulkPort : UsbReadOnlyBulkPort {
        var closed = false
            private set
        override fun sendRequest(request: ByteArray, timeoutMs: Int): Int = request.size
        override fun receiveResponse(buffer: ByteArray, timeoutMs: Int): Int = -1
        override fun close() {
            closed = true
        }
    }
}
