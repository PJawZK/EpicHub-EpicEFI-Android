package com.epicefi.epichub.engine

import com.epicefi.epichub.platform.transport.usb.UsbReadOnlyBulkPort
import com.epicefi.epichub.platform.transport.usb.UsbReadOnlyTransactionChannel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LiveTuneDefinitionDrivenConnectTest {
    @Test
    fun triesDistinctDefinitionQueriesAndConnectsOnlyAfterExactProfileSelection() {
        val lifecycle = FakeLifecycle()
        val probe =
            SequenceProbe(
                ArrayDeque(
                    listOf(
                        UsbRuntimeIdentityProbeResult.Identified(
                            ValidatedRuntimeIdentity("unknown.signature"),
                        ),
                        UsbRuntimeIdentityProbeResult.Identified(
                            ValidatedRuntimeIdentity("exact.signature"),
                        ),
                    ),
                ),
            )
        val selector =
            FakeSelector { response, _ ->
                when (response.toString(Charsets.UTF_8)) {
                    "unknown.signature" -> rejected(RuntimeProfileSelectionRejection.UnknownController)
                    "exact.signature" -> RuntimeProfileSelectionResult.Selected(42L)
                    else -> error("unexpected signature")
                }
            }
        val compiler =
            FakeCompiler(
                mapOf(
                    "first" to byteArrayOf('S'.code.toByte()),
                    "duplicate" to byteArrayOf('S'.code.toByte()),
                    "second" to byteArrayOf('Q'.code.toByte()),
                ),
            )
        val owner = owner(
            lifecycle = lifecycle,
            probe = probe,
            selector = selector,
            compiler = compiler,
            bundles = listOf(bundle("first"), bundle("duplicate"), bundle("second")),
        )

        val result = owner.connectUsingDefinitionQueries(channel(), 256, 500)

        assertEquals(
            DefinitionDrivenLiveTuneConnectResult.Connected(
                identity = ValidatedRuntimeIdentity("exact.signature"),
                profileGeneration = 42L,
                queryIndex = 1,
            ),
            result,
        )
        assertEquals(1, lifecycle.disconnectCalls)
        assertEquals(1, lifecycle.connectCalls)
        assertEquals("exact.signature", lifecycle.connectedIdentity?.signature)
        assertEquals(listOf("S", "Q"), probe.plans.map { it.query.toString(Charsets.UTF_8) })
        assertEquals(2, selector.calls)
    }

    @Test
    fun validButUnknownSignaturesNeverPromoteConnectedAuthority() {
        val lifecycle = FakeLifecycle()
        val probe =
            SequenceProbe(
                ArrayDeque(
                    listOf(
                        UsbRuntimeIdentityProbeResult.Identified(ValidatedRuntimeIdentity("unknown.one")),
                        UsbRuntimeIdentityProbeResult.Identified(ValidatedRuntimeIdentity("unknown.two")),
                    ),
                ),
            )
        val selector = FakeSelector { _, _ -> rejected(RuntimeProfileSelectionRejection.UnknownController) }
        val owner = owner(
            lifecycle = lifecycle,
            probe = probe,
            selector = selector,
            compiler = FakeCompiler(mapOf("first" to bytes("S"), "second" to bytes("Q"))),
            bundles = listOf(bundle("first"), bundle("second")),
        )

        val result = owner.connectUsingDefinitionQueries(channel(), 256, 500)

        assertEquals(
            DefinitionDrivenLiveTuneConnectResult.NoAuthorizedController(
                attemptedQueries = 2,
                lastProbeResult =
                    UsbRuntimeIdentityProbeResult.Identified(
                        ValidatedRuntimeIdentity("unknown.two"),
                    ),
                lastProfileRejection = RuntimeProfileSelectionRejection.UnknownController,
            ),
            result,
        )
        assertEquals(1, lifecycle.disconnectCalls)
        assertEquals(0, lifecycle.connectCalls)
        assertNull(lifecycle.connectedIdentity)
    }

    @Test
    fun probeFailuresCanFallThroughToAnotherDefinitionAuthorizedQuery() {
        val lifecycle = FakeLifecycle()
        val probe =
            SequenceProbe(
                ArrayDeque(
                    listOf(
                        UsbRuntimeIdentityProbeResult.TransportFailure("unsupported query timeout"),
                        UsbRuntimeIdentityProbeResult.Identified(ValidatedRuntimeIdentity("exact.signature")),
                    ),
                ),
            )
        val owner = owner(
            lifecycle = lifecycle,
            probe = probe,
            selector = FakeSelector { _, _ -> RuntimeProfileSelectionResult.Selected(77L) },
            compiler = FakeCompiler(mapOf("first" to bytes("S"), "second" to bytes("Q"))),
            bundles = listOf(bundle("first"), bundle("second")),
        )

        val result = owner.connectUsingDefinitionQueries(channel(), 128, 200)

        assertEquals(
            DefinitionDrivenLiveTuneConnectResult.Connected(
                ValidatedRuntimeIdentity("exact.signature"),
                77L,
                1,
            ),
            result,
        )
        assertEquals(listOf("S", "Q"), probe.plans.map { it.query.toString(Charsets.UTF_8) })
    }

    @Test
    fun registryInfrastructureFailureStopsClosedWithoutTryingLaterQueries() {
        val lifecycle = FakeLifecycle()
        val probe =
            SequenceProbe(
                ArrayDeque(
                    listOf(
                        UsbRuntimeIdentityProbeResult.Identified(ValidatedRuntimeIdentity("candidate")),
                    ),
                ),
            )
        val owner = owner(
            lifecycle = lifecycle,
            probe = probe,
            selector = FakeSelector { _, _ -> rejected(RuntimeProfileSelectionRejection.RegistryFailure) },
            compiler = FakeCompiler(mapOf("first" to bytes("S"), "second" to bytes("Q"))),
            bundles = listOf(bundle("first"), bundle("second")),
        )

        val result = owner.connectUsingDefinitionQueries(channel(), 128, 200)

        assertEquals(
            DefinitionDrivenLiveTuneConnectResult.ProfileAuthorityRejected(
                ValidatedRuntimeIdentity("candidate"),
                RuntimeProfileSelectionRejection.RegistryFailure,
            ),
            result,
        )
        assertEquals(1, probe.plans.size)
        assertEquals(0, lifecycle.connectCalls)
    }

    @Test
    fun probePlanCompileFailureDoesNoTransportIoAndPreservesExistingLifecycle() {
        val lifecycle = FakeLifecycle(
            initialState = LiveTuneSourceLifecycleState.Connected(ValidatedRuntimeIdentity("existing")),
        )
        val probe = SequenceProbe(ArrayDeque())
        val owner = owner(
            lifecycle = lifecycle,
            probe = probe,
            selector = FakeSelector { _, _ -> error("must not select") },
            compiler =
                object : DefinitionProbeQueryCompileOperation {
                    override fun compile(import: DefinitionBundleImport) =
                        DefinitionProbeQueryCompileResult.Rejected(
                            DefinitionProbeQueryRejection.CompileFailure,
                        )
                },
            bundles = listOf(bundle("bad")),
        )

        val result = owner.connectUsingDefinitionQueries(channel(), 128, 200)

        assertEquals(
            DefinitionDrivenLiveTuneConnectResult.ProbePlanRejected(
                bundleIndex = 0,
                reason = DefinitionProbeQueryRejection.CompileFailure,
            ),
            result,
        )
        assertEquals(0, probe.plans.size)
        assertEquals(0, lifecycle.disconnectCalls)
        assertEquals(0, lifecycle.connectCalls)
    }

    private fun owner(
        lifecycle: FakeLifecycle,
        probe: SequenceProbe,
        selector: FakeSelector,
        compiler: DefinitionProbeQueryCompileOperation,
        bundles: List<DefinitionBundleImport>,
    ) =
        LiveTuneConnectionOwner(
            registryOwner = FakeCloseable(),
            registryAlive = { true },
            lifecycle = lifecycle,
            identityProbe = probe,
            definitionBundles = bundles,
            profileSelector = selector,
            probeQueryCompiler = compiler,
        )

    private fun bundle(name: String) =
        DefinitionBundleImport(
            profileName = name,
            profileGeneration = 1L,
            definitionFingerprint = null,
            rootDefinition = "root-$name",
            semanticTables = emptyList(),
        )

    private fun rejected(reason: RuntimeProfileSelectionRejection) =
        RuntimeProfileSelectionResult.Rejected(reason)

    private fun bytes(value: String) = value.toByteArray(Charsets.UTF_8)

    private fun channel() = UsbReadOnlyTransactionChannel(FakeBulkPort())

    private class FakeCompiler(
        private val queries: Map<String, ByteArray>,
    ) : DefinitionProbeQueryCompileOperation {
        override fun compile(import: DefinitionBundleImport): DefinitionProbeQueryCompileResult =
            DefinitionProbeQueryCompileResult.Compiled(
                queries.getValue(import.profileName),
            )
    }

    private class SequenceProbe(
        private val results: ArrayDeque<UsbRuntimeIdentityProbeResult>,
    ) : RuntimeIdentityProbeOperation {
        val plans = mutableListOf<RuntimeIdentityProbePlan>()

        override fun probe(
            channel: UsbReadOnlyTransactionChannel,
            plan: RuntimeIdentityProbePlan,
        ): UsbRuntimeIdentityProbeResult {
            plans += plan
            return results.removeFirst()
        }
    }

    private class FakeSelector(
        private val operation: (ByteArray, Int) -> RuntimeProfileSelectionResult,
    ) : RuntimeProfileSelectionOperation {
        var calls = 0
            private set

        override fun select(
            response: ByteArray,
            maxResponseBytes: Int,
        ): RuntimeProfileSelectionResult {
            calls += 1
            return operation(response, maxResponseBytes)
        }
    }

    private class FakeLifecycle(
        initialState: LiveTuneSourceLifecycleState = LiveTuneSourceLifecycleState.Disconnected(),
    ) : LiveTuneLifecycleControl {
        override var state: LiveTuneSourceLifecycleState = initialState
            private set
        var connectedIdentity: ValidatedRuntimeIdentity? = null
            private set
        var connectCalls = 0
            private set
        var disconnectCalls = 0
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
        ): UsbReadOnlyTuneLoadResult? = null
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
