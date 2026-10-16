package com.epicefi.epichub.platform.transport.usb

import com.epicefi.epichub.engine.DefinitionBundleImport
import com.epicefi.epichub.engine.DefinitionProbeQueryCompileOperation
import com.epicefi.epichub.engine.DefinitionProbeQueryCompileResult
import com.epicefi.epichub.engine.GuardedBurnRejection
import com.epicefi.epichub.engine.LiveTuneBurnOperation
import com.epicefi.epichub.engine.LiveTuneBurnResult
import com.epicefi.epichub.engine.LiveTuneConnectionOwner
import com.epicefi.epichub.engine.LiveTuneLifecycleControl
import com.epicefi.epichub.engine.LiveTuneOwnerBurnResult
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

class AndroidUsbLiveBurnSessionTest {
    @Test
    fun retainedAuthorizedUsbChannelAndLiveIdentityAreUsedForBurn() {
        val identity = ValidatedRuntimeIdentity("exact.signature")
        val lifecycle = FakeLifecycle()
        val burnOperation = FakeBurnOperation()
        val owner = owner(identity, lifecycle, burnOperation)
        val channel = UsbReadOnlyTransactionChannel(FakeBulkPort())
        val session =
            AndroidUsbLiveConnectionSessionOwner(
                { owner },
                AndroidUsbEndpointChannelOpener {
                    AndroidUsbEndpointOpenResult.Opened(channel)
                },
            )

        assertTrue(session.connect("usb:test", 256, 500) is AndroidUsbLiveConnectResult.Connected)
        lifecycle.markLive(identity)

        val result = session.burnPageToNonvolatile(page = 2, signatureLimit = 256, timeoutMs = 1_500)

        assertEquals(
            AndroidUsbLiveBurnResult.Completed(
                LiveTuneOwnerBurnResult.Completed(burnOperation.result),
            ),
            result,
        )
        assertSame(channel, burnOperation.channel)
        assertEquals(identity.signature, burnOperation.signature?.toString(Charsets.UTF_8))
        assertEquals(2, burnOperation.page)
        assertEquals(256, burnOperation.signatureLimit)
        assertEquals(1_500, burnOperation.timeoutMs)
        assertEquals(1, burnOperation.calls)
    }

    @Test
    fun disconnectedSessionCannotReachBurnAuthority() {
        val identity = ValidatedRuntimeIdentity("exact.signature")
        val lifecycle = FakeLifecycle()
        val burnOperation = FakeBurnOperation()
        val owner = owner(identity, lifecycle, burnOperation)
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
            AndroidUsbLiveBurnResult.NotConnected,
            session.burnPageToNonvolatile(page = 1, signatureLimit = 256, timeoutMs = 1_500),
        )
        assertEquals(0, burnOperation.calls)
    }

    private fun owner(
        identity: ValidatedRuntimeIdentity,
        lifecycle: FakeLifecycle,
        burnOperation: FakeBurnOperation,
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
            burnOperation = burnOperation,
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

    private class FakeBurnOperation : LiveTuneBurnOperation {
        val result = LiveTuneBurnResult.PlanRejected(GuardedBurnRejection.PlanFailure)
        var calls = 0
        var channel: UsbReadOnlyTransactionChannel? = null
        var signature: ByteArray? = null
        var signatureLimit = -1
        var page = -1
        var timeoutMs = -1

        override fun burn(
            signatureResponse: ByteArray,
            signatureLimit: Int,
            page: Int,
            channel: UsbReadOnlyTransactionChannel,
            timeoutMs: Int,
        ): LiveTuneBurnResult {
            calls += 1
            this.channel = channel
            signature = signatureResponse.copyOf()
            this.signatureLimit = signatureLimit
            this.page = page
            this.timeoutMs = timeoutMs
            return result
        }
    }

    private class FakeCloseable : AutoCloseable {
        override fun close() = Unit
    }

    private class FakeBulkPort : UsbReadOnlyBulkPort {
        override fun sendRequest(request: ByteArray, timeoutMs: Int): Int = request.size
        override fun receiveResponse(buffer: ByteArray, timeoutMs: Int): Int = -1
        override fun close() = Unit
    }
}
