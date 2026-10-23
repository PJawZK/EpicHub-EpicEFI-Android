package com.epicefi.epichub.platform.transport.usb

import com.epicefi.epichub.engine.DefinitionBundleImport
import com.epicefi.epichub.engine.DefinitionDrivenRuntimeProbePlanCompiler
import com.epicefi.epichub.engine.DefinitionProbeQueryCompileOperation
import com.epicefi.epichub.engine.DefinitionProbeQueryCompileResult
import com.epicefi.epichub.engine.LiveTuneConnectionOwner
import com.epicefi.epichub.engine.LiveTuneLifecycleControl
import com.epicefi.epichub.engine.LiveTuneSourceLifecycleState
import com.epicefi.epichub.engine.RuntimeCandidateBindingOperation
import com.epicefi.epichub.engine.RuntimeCandidateBindingResult
import com.epicefi.epichub.engine.RuntimeIdentityProbeOperation
import com.epicefi.epichub.engine.RuntimeIdentityProbePlan
import com.epicefi.epichub.engine.RuntimeProfileSelectionOperation
import com.epicefi.epichub.engine.RuntimeProfileSelectionResult
import com.epicefi.epichub.engine.UsbReadOnlyTuneLoadResult
import com.epicefi.epichub.engine.UsbRuntimeIdentityProbeResult
import com.epicefi.epichub.engine.ValidatedRuntimeIdentity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidUsbLiveConnectionSessionTest {
    @Test
    fun successfulAuthorizedConnectRetainsChannelUntilExplicitDisconnect() {
        val authority = authorizedOwner("exact.signature", 42L)
        val port = FakeBulkPort()
        val channel = UsbReadOnlyTransactionChannel(port)
        val opener = FakeOpener(AndroidUsbEndpointOpenResult.Opened(channel))
        val session = AndroidUsbLiveConnectionSessionOwner({ authority.owner }, opener)

        val result = session.connect("usb:test", 256, 500)

        assertEquals(
            AndroidUsbLiveConnectResult.Connected(
                endpointId = "usb:test",
                identity = ValidatedRuntimeIdentity("exact.signature"),
                profileGeneration = 42L,
            ),
            result,
        )
        assertTrue(session.isConnected)
        assertEquals("usb:test", session.connectedEndpointId)
        assertFalse(port.closed)

        assertTrue(session.disconnect())
        assertTrue(port.closed)
        assertFalse(session.isConnected)
        assertNull(session.connectedEndpointId)
        assertEquals(2, authority.lifecycle.disconnectCalls)
    }

    @Test
    fun rejectedAuthorityAlwaysClosesCandidateChannel() {
        val authority = unknownOwner()
        val port = FakeBulkPort()
        val session =
            AndroidUsbLiveConnectionSessionOwner(
                { authority.owner },
                FakeOpener(AndroidUsbEndpointOpenResult.Opened(UsbReadOnlyTransactionChannel(port))),
            )

        val result = session.connect("usb:test", 256, 500)

        assertTrue(result is AndroidUsbLiveConnectResult.AuthorityRejected)
        assertTrue(port.closed)
        assertFalse(session.isConnected)
        assertNull(session.connectedEndpointId)
    }

    @Test
    fun missingProfileAuthorityDoesNotOpenTransport() {
        val opener = FakeOpener(AndroidUsbEndpointOpenResult.DeviceUnavailable)
        val session = AndroidUsbLiveConnectionSessionOwner({ null }, opener)

        assertEquals(
            AndroidUsbLiveConnectResult.ProfileAuthorityUnavailable,
            session.connect("usb:test", 256, 500),
        )
        assertEquals(0, opener.calls)
    }

    @Test
    fun reconnectClosesOldChannelAndDisconnectsExactOldOwnerBeforeReplacement() {
        val first = authorizedOwner("first.signature", 1L)
        val second = authorizedOwner("second.signature", 2L)
        var current = first.owner
        val firstPort = FakeBulkPort()
        val secondPort = FakeBulkPort()
        val opener =
            QueueOpener(
                ArrayDeque(
                    listOf(
                        AndroidUsbEndpointOpenResult.Opened(UsbReadOnlyTransactionChannel(firstPort)),
                        AndroidUsbEndpointOpenResult.Opened(UsbReadOnlyTransactionChannel(secondPort)),
                    ),
                ),
            )
        val session = AndroidUsbLiveConnectionSessionOwner({ current }, opener)

        assertTrue(session.connect("usb:first", 256, 500) is AndroidUsbLiveConnectResult.Connected)
        current = second.owner
        val secondResult = session.connect("usb:second", 256, 500)

        assertTrue(secondResult is AndroidUsbLiveConnectResult.Connected)
        assertTrue(firstPort.closed)
        assertFalse(secondPort.closed)
        assertEquals(2, first.lifecycle.disconnectCalls)
        assertEquals(1, second.lifecycle.disconnectCalls)
        assertEquals("usb:second", session.connectedEndpointId)
    }

    @Test
    fun closeReleasesRetainedChannelAndBecomesTerminal() {
        val authority = authorizedOwner("exact.signature", 42L)
        val port = FakeBulkPort()
        val opener = FakeOpener(
            AndroidUsbEndpointOpenResult.Opened(UsbReadOnlyTransactionChannel(port)),
        )
        val session = AndroidUsbLiveConnectionSessionOwner({ authority.owner }, opener)
        assertTrue(session.connect("usb:test", 256, 500) is AndroidUsbLiveConnectResult.Connected)

        session.close()

        assertTrue(port.closed)
        assertFalse(session.isConnected)
        assertEquals(
            AndroidUsbLiveConnectResult.OwnerClosed,
            session.connect("usb:test", 256, 500),
        )
    }

    @Test
    fun candidateIsBoundOnlyAfterSelectedUsbIdentityAndRevokedBeforePortClose() {
        val candidate = FakeRuntimeCandidate()
        val authority = authorizedOwner("exact.signature", 42L, candidate)
        val port = FakeBulkPort()
        val session = AndroidUsbLiveConnectionSessionOwner(
            { authority.owner },
            FakeOpener(AndroidUsbEndpointOpenResult.Opened(UsbReadOnlyTransactionChannel(port))),
        )
        assertEquals(0, candidate.bindCalls)
        assertTrue(session.connect("usb:one", 256, 500) is AndroidUsbLiveConnectResult.Connected)
        assertEquals(1, candidate.bindCalls)
        assertEquals("usb:one", candidate.endpoint)
        assertEquals("exact.signature", candidate.signature)
        assertEquals(42L, candidate.generation)
        assertEquals(RuntimeCandidateBindingResult.Bound(17L), session.runtimeCandidateStatus)
        assertTrue(session.disconnect())
        assertTrue(port.closed)
        assertTrue(candidate.revocations >= 1)
        assertTrue(session.runtimeCandidateStatus is RuntimeCandidateBindingResult.Unavailable)
    }

    @Test
    fun unsupportedCandidateDoesNotRejectExactProfileTuneConnection() {
        val candidate = FakeRuntimeCandidate(unsupported = true)
        val authority = authorizedOwner("exact.signature", 42L, candidate)
        val port = FakeBulkPort()
        val session = AndroidUsbLiveConnectionSessionOwner(
            { authority.owner },
            FakeOpener(AndroidUsbEndpointOpenResult.Opened(UsbReadOnlyTransactionChannel(port))),
        )
        assertTrue(session.connect("usb:one", 256, 500) is AndroidUsbLiveConnectResult.Connected)
        assertTrue(session.isConnected)
        assertFalse(port.closed)
        assertTrue(session.runtimeCandidateStatus is RuntimeCandidateBindingResult.Unavailable)
        session.close()
        assertTrue(port.closed)
    }

    @Test
    fun malformedReconnectRevokesCandidateAndClosesExistingChannel() {
        val candidate = FakeRuntimeCandidate()
        val authority = authorizedOwner("exact.signature", 42L, candidate)
        val port = FakeBulkPort()
        val session = AndroidUsbLiveConnectionSessionOwner(
            { authority.owner },
            FakeOpener(AndroidUsbEndpointOpenResult.Opened(UsbReadOnlyTransactionChannel(port))),
        )
        session.connect("usb:one", 256, 500)
        val before = candidate.revocations
        assertTrue(session.connect(" ", 256, 500) is AndroidUsbLiveConnectResult.InvalidSelection)
        assertTrue(candidate.revocations > before)
        assertFalse(session.isConnected)
        assertTrue(port.closed)
    }

    @Test
    fun profileOwnerCloseRevokesCandidateWithoutMakingRetainedSessionLive() {
        val candidate = FakeRuntimeCandidate()
        val authority = authorizedOwner("exact.signature", 42L, candidate)
        val port = FakeBulkPort()
        val session = AndroidUsbLiveConnectionSessionOwner(
            { authority.owner },
            FakeOpener(AndroidUsbEndpointOpenResult.Opened(UsbReadOnlyTransactionChannel(port))),
        )
        session.connect("usb:one", 256, 500)
        val previousRevocations = candidate.revocations
        authority.owner.close()
        assertTrue(candidate.revocations > previousRevocations)
        assertFalse(session.isConnected)
        assertTrue(session.runtimeCandidateStatus is RuntimeCandidateBindingResult.Unavailable)
        assertEquals(AndroidUsbLiveLoadResult.NotConnected, session.loadCurrentController(256, 500))
        assertTrue(port.closed)
    }

    private class FakeRuntimeCandidate(
        private val unsupported: Boolean = false,
    ) : RuntimeCandidateBindingOperation {
        var bindCalls = 0
        var revocations = 0
        var endpoint: String? = null
        var signature: String? = null
        var generation: Long = 0

        override fun bind(
            endpointId: String,
            signature: String,
            profileGeneration: Long,
            signatureLimit: Int,
        ): RuntimeCandidateBindingResult {
            bindCalls++
            endpoint = endpointId
            this.signature = signature
            generation = profileGeneration
            return if (unsupported) {
                RuntimeCandidateBindingResult.Unavailable("Unsupported output definition")
            } else {
                RuntimeCandidateBindingResult.Bound(17L)
            }
        }

        override fun disconnect() {
            revocations++
        }
    }

    private fun authorizedOwner(
        signature: String,
        generation: Long,
        candidate: RuntimeCandidateBindingOperation? = null,
    ): OwnerFixture {
        val lifecycle = FakeLifecycle()
        val owner =
            LiveTuneConnectionOwner(
                registryOwner = FakeCloseable(),
                registryAlive = { true },
                lifecycle = lifecycle,
                identityProbe =
                    RuntimeIdentityProbeOperation { _, _ ->
                        UsbRuntimeIdentityProbeResult.Identified(ValidatedRuntimeIdentity(signature))
                    },
                definitionBundles = listOf(bundle()),
                profileSelector =
                    RuntimeProfileSelectionOperation { _, _ ->
                        RuntimeProfileSelectionResult.Selected(generation)
                    },
                probeQueryCompiler =
                    DefinitionProbeQueryCompileOperation {
                        DefinitionProbeQueryCompileResult.Compiled(byteArrayOf('S'.code.toByte()))
                    },
                runtimeCandidateBinding = candidate,
            )
        return OwnerFixture(owner, lifecycle)
    }

    private fun unknownOwner(): OwnerFixture {
        val lifecycle = FakeLifecycle()
        val owner =
            LiveTuneConnectionOwner(
                registryOwner = FakeCloseable(),
                registryAlive = { true },
                lifecycle = lifecycle,
                identityProbe =
                    RuntimeIdentityProbeOperation { _, _ ->
                        UsbRuntimeIdentityProbeResult.Identified(
                            ValidatedRuntimeIdentity("unknown.signature"),
                        )
                    },
                definitionBundles = listOf(bundle()),
                profileSelector =
                    RuntimeProfileSelectionOperation { _, _ ->
                        RuntimeProfileSelectionResult.Rejected(
                            com.epicefi.epichub.engine.RuntimeProfileSelectionRejection.UnknownController,
                        )
                    },
                probeQueryCompiler =
                    DefinitionProbeQueryCompileOperation {
                        DefinitionProbeQueryCompileResult.Compiled(byteArrayOf('S'.code.toByte()))
                    },
            )
        return OwnerFixture(owner, lifecycle)
    }

    private fun bundle() =
        DefinitionBundleImport(
            profileName = "test",
            profileGeneration = 1L,
            definitionFingerprint = null,
            rootDefinition = "root",
            semanticTables = emptyList(),
        )

    private data class OwnerFixture(
        val owner: LiveTuneConnectionOwner,
        val lifecycle: FakeLifecycle,
    )

    private class FakeLifecycle : LiveTuneLifecycleControl {
        override var state: LiveTuneSourceLifecycleState = LiveTuneSourceLifecycleState.Disconnected()
            private set
        var disconnectCalls = 0
            private set

        override fun onConnected(identity: ValidatedRuntimeIdentity) {
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
        ): UsbReadOnlyTuneLoadResult? = null
    }

    private class FakeOpener(
        private val result: AndroidUsbEndpointOpenResult,
    ) : AndroidUsbEndpointChannelOpener {
        var calls = 0
            private set

        override fun open(endpointId: String): AndroidUsbEndpointOpenResult {
            calls += 1
            return result
        }
    }

    private class QueueOpener(
        private val results: ArrayDeque<AndroidUsbEndpointOpenResult>,
    ) : AndroidUsbEndpointChannelOpener {
        override fun open(endpointId: String): AndroidUsbEndpointOpenResult = results.removeFirst()
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
