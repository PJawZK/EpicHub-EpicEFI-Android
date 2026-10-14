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

sealed interface LiveTuneOwnerLoadResult {
    data class Completed(
        val result: UsbReadOnlyTuneLoadResult,
    ) : LiveTuneOwnerLoadResult

    data class NotLoadable(
        val state: LiveTuneSourceLifecycleState,
    ) : LiveTuneOwnerLoadResult

    data object OwnerClosed : LiveTuneOwnerLoadResult
}

internal fun interface RuntimeIdentityProbeOperation {
    fun probe(
        channel: UsbReadOnlyTransactionChannel,
        plan: RuntimeIdentityProbePlan,
    ): UsbRuntimeIdentityProbeResult
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
) : AutoCloseable {
    @Volatile
    private var closed = false

    val isAlive: Boolean
        get() = !closed && registryAlive()

    val state: LiveTuneSourceLifecycleState
        get() = lifecycle.state

    fun probeAndConnect(
        channel: UsbReadOnlyTransactionChannel,
        plan: RuntimeIdentityProbePlan,
    ): LiveTuneConnectResult {
        if (!isAlive) {
            return LiveTuneConnectResult.OwnerClosed
        }

        return when (val probed = identityProbe.probe(channel, plan)) {
            is UsbRuntimeIdentityProbeResult.Identified -> {
                lifecycle.onConnected(probed.identity)
                LiveTuneConnectResult.Connected(probed.identity)
            }

            else -> LiveTuneConnectResult.ProbeRejected(probed)
        }
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

    fun disconnect() {
        if (!closed) {
            lifecycle.onDisconnected()
        }
    }

    @Synchronized
    override fun close() {
        if (closed) return
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
                    LiveTuneConnectionOwnerOpenResult.Opened(
                        owner =
                            LiveTuneConnectionOwner(
                                registryOwner = registryOwner,
                                registryAlive = registryOwner::isAlive,
                                lifecycle = lifecycle,
                                identityProbe = RuntimeIdentityProbeOperation(probe::probe),
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
