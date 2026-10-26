package com.epicefi.epichub.engine

import com.epicefi.epichub.platform.transport.usb.UsbReadOnlyTransactionChannel

sealed interface LiveTuneConnectionOwnerOpenResult {
    data class Opened(
        val owner: LiveTuneConnectionOwner,
        val importedBundleCount: Int,
    ) : LiveTuneConnectionOwnerOpenResult

    data class BundleRejected(
        val bundleIndex: Int,
        val reason: DefinitionBundleImportRejection,
    ) : LiveTuneConnectionOwnerOpenResult

    data class CatalogFailure(
        val reason: String,
    ) : LiveTuneConnectionOwnerOpenResult
}

sealed interface LiveTuneConnectResult {
    data class Connected(
        val identity: ValidatedRuntimeIdentity,
    ) : LiveTuneConnectResult

    data class ProbeRejected(
        val result: UsbRuntimeIdentityProbeResult,
    ) : LiveTuneConnectResult

    data object OwnerClosed : LiveTuneConnectResult
}

sealed interface DefinitionDrivenLiveTuneConnectResult {
    data class Connected(
        val identity: ValidatedRuntimeIdentity,
        val profileGeneration: Long,
        val queryIndex: Int,
    ) : DefinitionDrivenLiveTuneConnectResult

    data class ProbePlanRejected(
        val bundleIndex: Int,
        val reason: DefinitionProbeQueryRejection,
    ) : DefinitionDrivenLiveTuneConnectResult

    data class ProfileAuthorityRejected(
        val identity: ValidatedRuntimeIdentity,
        val reason: RuntimeProfileSelectionRejection,
    ) : DefinitionDrivenLiveTuneConnectResult

    data class NoAuthorizedController(
        val attemptedQueries: Int,
        val lastProbeResult: UsbRuntimeIdentityProbeResult?,
        val lastProfileRejection: RuntimeProfileSelectionRejection?,
    ) : DefinitionDrivenLiveTuneConnectResult

    data class InvalidConfiguration(
        val reason: String,
    ) : DefinitionDrivenLiveTuneConnectResult

    data object OwnerClosed : DefinitionDrivenLiveTuneConnectResult
}

sealed interface LiveTuneOwnerLoadResult {
    data class Completed(
        val result: UsbReadOnlyTuneLoadResult,
    ) : LiveTuneOwnerLoadResult

    data class NotLoadable(
        val state: LiveTuneSourceLifecycleState,
    ) : LiveTuneOwnerLoadResult

    data object OwnerClosed : LiveTuneOwnerLoadResult
}

internal sealed interface LiveTuneOwnerRamWriteResult {
    data class Completed(
        val result: LiveTuneRamWriteResult,
    ) : LiveTuneOwnerRamWriteResult

    data class NotWritable(
        val state: LiveTuneSourceLifecycleState,
    ) : LiveTuneOwnerRamWriteResult

    data object OperationUnavailable : LiveTuneOwnerRamWriteResult

    data object OwnerClosed : LiveTuneOwnerRamWriteResult
}

internal sealed interface LiveTuneOwnerBurnResult {
    data class Completed(
        val result: LiveTuneBurnResult,
    ) : LiveTuneOwnerBurnResult

    data class NotBurnable(
        val state: LiveTuneSourceLifecycleState,
    ) : LiveTuneOwnerBurnResult

    data object OperationUnavailable : LiveTuneOwnerBurnResult

    data object OwnerClosed : LiveTuneOwnerBurnResult
}

internal fun interface RuntimeIdentityProbeOperation {
    fun probe(
        channel: UsbReadOnlyTransactionChannel,
        plan: RuntimeIdentityProbePlan,
    ): UsbRuntimeIdentityProbeResult
}

internal fun interface RuntimeProfileSelectionOperation {
    fun select(
        response: ByteArray,
        maxResponseBytes: Int,
    ): RuntimeProfileSelectionResult
}

internal fun interface DefinitionProbeQueryCompileOperation {
    fun compile(import: DefinitionBundleImport): DefinitionProbeQueryCompileResult
}

internal interface LiveTuneLifecycleControl {
    val state: LiveTuneSourceLifecycleState

    fun onConnected(identity: ValidatedRuntimeIdentity)

    fun onDisconnected()

    fun loadCurrentController(
        signatureLimit: Int,
        channel: UsbReadOnlyTransactionChannel,
        timeoutMs: Int,
    ): UsbReadOnlyTuneLoadResult?
}

internal class NativeLiveTuneLifecycleControl(
    private val lifecycle: LiveTuneSourceLifecycle,
) : LiveTuneLifecycleControl {
    override val state: LiveTuneSourceLifecycleState
        get() = lifecycle.state

    override fun onConnected(identity: ValidatedRuntimeIdentity) {
        lifecycle.onConnected(identity)
    }

    override fun onDisconnected() {
        lifecycle.onDisconnected()
    }

    override fun loadCurrentController(
        signatureLimit: Int,
        channel: UsbReadOnlyTransactionChannel,
        timeoutMs: Int,
    ): UsbReadOnlyTuneLoadResult? =
        lifecycle.loadCurrentController(signatureLimit, channel, timeoutMs)
}

class LiveTuneConnectionOwner internal constructor(
    private val registryOwner: AutoCloseable,
    private val registryAlive: () -> Boolean,
    private val lifecycle: LiveTuneLifecycleControl,
    private val identityProbe: RuntimeIdentityProbeOperation,
    private val definitionBundles: List<DefinitionBundleImport> = emptyList(),
    private val profileSelector: RuntimeProfileSelectionOperation? = null,
    private val probeQueryCompiler: DefinitionProbeQueryCompileOperation? = null,
    private val ramWriteOperation: LiveTuneRamWriteOperation? = null,
    private val burnOperation: LiveTuneBurnOperation? = null,
    private val runtimeCandidateBinding: RuntimeCandidateBindingOperation? = null,
) : AutoCloseable {
    @Volatile
    private var closed = false

    val isAlive: Boolean
        get() = !closed && registryAlive()

    val state: LiveTuneSourceLifecycleState
        get() = lifecycle.state

    /**
     * Only the retained Android USB owner may request this after exact profile selection.
     * Failure is a nonlive runtime capability result, not a rejected tune connection.
     */
    internal fun bindRuntimeCandidate(
        endpointId: String,
        identity: ValidatedRuntimeIdentity,
        profileGeneration: Long,
        signatureLimit: Int,
    ): RuntimeCandidateBindingResult {
        if (!isAlive) return RuntimeCandidateBindingResult.Unavailable("Profile owner is closed.")
        val connected = lifecycle.state as? LiveTuneSourceLifecycleState.Connected
        if (connected?.identity != identity || profileGeneration <= 0L) {
            return RuntimeCandidateBindingResult.Unavailable("Current ECU identity is not authorized.")
        }
        val binder = runtimeCandidateBinding
            ?: return RuntimeCandidateBindingResult.Unavailable("Runtime candidate binding is not installed.")
        return binder.bind(endpointId, identity.signature, profileGeneration, signatureLimit)
    }

    /** No polling. Invoked only by the retained serialized Android USB owner. */
    internal fun readRuntimeOnce(
        epoch: Long,
        channel: UsbReadOnlyTransactionChannel,
        timeoutMs: Int,
    ): RuntimeReadCycleResult {
        if (!isAlive) return RuntimeReadCycleResult.Unavailable("Selected profile owner is closed.")
        if (lifecycle.state is LiveTuneSourceLifecycleState.Disconnected) {
            return RuntimeReadCycleResult.Unavailable("Controller is disconnected.")
        }
        val operation = runtimeCandidateBinding
            ?: return RuntimeReadCycleResult.Unavailable("Runtime capability unavailable.")
        return operation.read(epoch, channel, timeoutMs)
    }

    fun probeAndConnect(
        channel: UsbReadOnlyTransactionChannel,
        plan: RuntimeIdentityProbePlan,
    ): LiveTuneConnectResult {
        if (!isAlive) {
            return LiveTuneConnectResult.OwnerClosed
        }

        return when (val probed = identityProbe.probe(channel, plan)) {
            is UsbRuntimeIdentityProbeResult.Identified -> {
                runtimeCandidateBinding?.disconnect()
                lifecycle.onConnected(probed.identity)
                LiveTuneConnectResult.Connected(probed.identity)
            }

            else -> LiveTuneConnectResult.ProbeRejected(probed)
        }
    }

    fun connectUsingDefinitionQueries(
        channel: UsbReadOnlyTransactionChannel,
        maxResponseBytes: Int,
        timeoutMs: Int,
    ): DefinitionDrivenLiveTuneConnectResult {
        if (!isAlive) {
            return DefinitionDrivenLiveTuneConnectResult.OwnerClosed
        }
        if (maxResponseBytes <= 0) {
            return DefinitionDrivenLiveTuneConnectResult.InvalidConfiguration(
                "response limit must be positive",
            )
        }
        if (timeoutMs <= 0) {
            return DefinitionDrivenLiveTuneConnectResult.InvalidConfiguration(
                "timeout must be positive",
            )
        }
        val selector = profileSelector
            ?: return DefinitionDrivenLiveTuneConnectResult.InvalidConfiguration(
                "profile selector is unavailable",
            )
        val compiler = probeQueryCompiler
            ?: return DefinitionDrivenLiveTuneConnectResult.InvalidConfiguration(
                "definition probe compiler is unavailable",
            )

        val queries = ArrayList<ByteArray>()
        definitionBundles.forEachIndexed { bundleIndex, bundle ->
            when (val compiled = compiler.compile(bundle)) {
                is DefinitionProbeQueryCompileResult.Compiled -> {
                    if (queries.none { existing -> existing.contentEquals(compiled.query) }) {
                        queries += compiled.query
                    }
                }

                is DefinitionProbeQueryCompileResult.Rejected -> {
                    return DefinitionDrivenLiveTuneConnectResult.ProbePlanRejected(
                        bundleIndex = bundleIndex,
                        reason = compiled.reason,
                    )
                }
            }
        }
        if (queries.isEmpty()) {
            return DefinitionDrivenLiveTuneConnectResult.InvalidConfiguration(
                "selected profile catalog contains no runtime probe queries",
            )
        }

        runtimeCandidateBinding?.disconnect()
        lifecycle.onDisconnected()
        var lastProbe: UsbRuntimeIdentityProbeResult? = null
        var lastProfileRejection: RuntimeProfileSelectionRejection? = null
        queries.forEachIndexed { queryIndex, query ->
            val probed =
                identityProbe.probe(
                    channel,
                    RuntimeIdentityProbePlan(
                        query = query,
                        maxResponseBytes = maxResponseBytes,
                        timeoutMs = timeoutMs,
                    ),
                )
            lastProbe = probed
            if (probed !is UsbRuntimeIdentityProbeResult.Identified) {
                return@forEachIndexed
            }

            val signatureResponse = probed.identity.signature.toByteArray(Charsets.UTF_8)
            when (val selected = selector.select(signatureResponse, maxResponseBytes)) {
                is RuntimeProfileSelectionResult.Selected -> {
                    lifecycle.onConnected(probed.identity)
                    return DefinitionDrivenLiveTuneConnectResult.Connected(
                        identity = probed.identity,
                        profileGeneration = selected.profileGeneration,
                        queryIndex = queryIndex,
                    )
                }

                is RuntimeProfileSelectionResult.Rejected -> {
                    lastProfileRejection = selected.reason
                    when (selected.reason) {
                        RuntimeProfileSelectionRejection.UnknownController,
                        RuntimeProfileSelectionRejection.RecognizedControllerWithoutProfile,
                        -> Unit

                        else -> {
                            return DefinitionDrivenLiveTuneConnectResult.ProfileAuthorityRejected(
                                identity = probed.identity,
                                reason = selected.reason,
                            )
                        }
                    }
                }
            }
        }

        return DefinitionDrivenLiveTuneConnectResult.NoAuthorizedController(
            attemptedQueries = queries.size,
            lastProbeResult = lastProbe,
            lastProfileRejection = lastProfileRejection,
        )
    }

    fun loadCurrentController(
        signatureLimit: Int,
        channel: UsbReadOnlyTransactionChannel,
        timeoutMs: Int,
    ): LiveTuneOwnerLoadResult {
        if (!isAlive) {
            return LiveTuneOwnerLoadResult.OwnerClosed
        }

        val result = lifecycle.loadCurrentController(signatureLimit, channel, timeoutMs)
            ?: return LiveTuneOwnerLoadResult.NotLoadable(lifecycle.state)
        return LiveTuneOwnerLoadResult.Completed(result)
    }

    internal fun writeCurrentDraftToRam(
        signatureLimit: Int,
        channel: UsbReadOnlyTransactionChannel,
        timeoutMs: Int,
    ): LiveTuneOwnerRamWriteResult {
        if (!isAlive) {
            return LiveTuneOwnerRamWriteResult.OwnerClosed
        }
        val live = lifecycle.state as? LiveTuneSourceLifecycleState.Live
            ?: return LiveTuneOwnerRamWriteResult.NotWritable(lifecycle.state)
        val operation = ramWriteOperation
            ?: return LiveTuneOwnerRamWriteResult.OperationUnavailable

        return LiveTuneOwnerRamWriteResult.Completed(
            operation.write(
                signatureResponse = live.identity.signature.toByteArray(Charsets.UTF_8),
                signatureLimit = signatureLimit,
                channel = channel,
                timeoutMs = timeoutMs,
            ),
        )
    }

    internal fun burnPageToNonvolatile(
        page: Int,
        signatureLimit: Int,
        channel: UsbReadOnlyTransactionChannel,
        timeoutMs: Int,
    ): LiveTuneOwnerBurnResult {
        if (!isAlive) {
            return LiveTuneOwnerBurnResult.OwnerClosed
        }
        val live = lifecycle.state as? LiveTuneSourceLifecycleState.Live
            ?: return LiveTuneOwnerBurnResult.NotBurnable(lifecycle.state)
        val operation = burnOperation
            ?: return LiveTuneOwnerBurnResult.OperationUnavailable

        return LiveTuneOwnerBurnResult.Completed(
            operation.burn(
                signatureResponse = live.identity.signature.toByteArray(Charsets.UTF_8),
                signatureLimit = signatureLimit,
                page = page,
                channel = channel,
                timeoutMs = timeoutMs,
            ),
        )
    }

    fun disconnect() {
        if (!closed) {
            runtimeCandidateBinding?.disconnect()
            lifecycle.onDisconnected()
        }
    }

    @Synchronized
    override fun close() {
        if (closed) return
        runtimeCandidateBinding?.disconnect()
        lifecycle.onDisconnected()
        registryOwner.close()
        closed = true
    }

    companion object {
        fun open(
            engine: NativeEngine,
            catalog: DefinitionBundleCatalog,
        ): LiveTuneConnectionOwnerOpenResult =
            when (val opened = DefinitionBundleRegistryOwner.open(catalog)) {
                is DefinitionBundleCatalogOpenResult.Opened -> {
                    val registryOwner = opened.registry
                    val nativeRegistry = registryOwner.requireNativeRegistry()
                    val lifecycle =
                        NativeLiveTuneLifecycleControl(
                            LiveTuneSourceLifecycle(engine, nativeRegistry),
                        )
                    val probe = UsbRuntimeIdentityProbe()
                    val probeCompiler = DefinitionDrivenRuntimeProbePlanCompiler()
                    LiveTuneConnectionOwnerOpenResult.Opened(
                        owner =
                            LiveTuneConnectionOwner(
                                registryOwner = registryOwner,
                                registryAlive = registryOwner::isAlive,
                                lifecycle = lifecycle,
                                identityProbe = RuntimeIdentityProbeOperation(probe::probe),
                                definitionBundles = registryOwner.definitionBundles(),
                                profileSelector = RuntimeProfileSelectionOperation(nativeRegistry::select),
                                probeQueryCompiler = DefinitionProbeQueryCompileOperation(probeCompiler::compile),
                                ramWriteOperation = NativeLiveTuneRamWriteOperation(engine, nativeRegistry),
                                burnOperation = NativeLiveTuneBurnOperation(engine, nativeRegistry),
                                runtimeCandidateBinding = NativeRegistryRuntimeCandidateBinding(nativeRegistry),
                            ),
                        importedBundleCount = opened.importedBundleCount,
                    )
                }

                is DefinitionBundleCatalogOpenResult.Rejected ->
                    LiveTuneConnectionOwnerOpenResult.BundleRejected(
                        opened.bundleIndex,
                        opened.reason,
                    )

                is DefinitionBundleCatalogOpenResult.CatalogFailure ->
                    LiveTuneConnectionOwnerOpenResult.CatalogFailure(opened.reason)
            }
    }
}
