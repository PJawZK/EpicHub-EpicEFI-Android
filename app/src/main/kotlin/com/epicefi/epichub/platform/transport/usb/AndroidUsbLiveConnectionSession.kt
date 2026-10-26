package com.epicefi.epichub.platform.transport.usb

import android.hardware.usb.UsbManager
import com.epicefi.epichub.engine.DefinitionDrivenLiveTuneConnectResult
import com.epicefi.epichub.engine.EngineAuthoritySerialization
import com.epicefi.epichub.engine.RuntimeCandidateBindingResult
import com.epicefi.epichub.engine.RuntimeReadCycleResult
import com.epicefi.epichub.engine.LiveTuneConnectionOwner
import com.epicefi.epichub.engine.LiveTuneOwnerBurnResult
import com.epicefi.epichub.engine.LiveTuneOwnerLoadResult
import com.epicefi.epichub.engine.LiveTuneOwnerRamWriteResult
import com.epicefi.epichub.engine.ValidatedRuntimeIdentity

sealed interface AndroidUsbEndpointOpenResult {
    data class Opened(val channel: UsbReadOnlyTransactionChannel) : AndroidUsbEndpointOpenResult

    data object DeviceUnavailable : AndroidUsbEndpointOpenResult

    data object PermissionRequired : AndroidUsbEndpointOpenResult

    data class TopologyRejected(val reason: String) : AndroidUsbEndpointOpenResult

    data class OpenFailed(val reason: String) : AndroidUsbEndpointOpenResult

    data class ClaimFailed(val reason: String) : AndroidUsbEndpointOpenResult
}

sealed interface AndroidUsbLiveConnectResult {
    data class Connected(
        val endpointId: String,
        val identity: ValidatedRuntimeIdentity,
        val profileGeneration: Long,
    ) : AndroidUsbLiveConnectResult

    data object ProfileAuthorityUnavailable : AndroidUsbLiveConnectResult

    data object DeviceUnavailable : AndroidUsbLiveConnectResult

    data object PermissionRequired : AndroidUsbLiveConnectResult

    data class TopologyRejected(val reason: String) : AndroidUsbLiveConnectResult

    data class OpenFailed(val reason: String) : AndroidUsbLiveConnectResult

    data class ClaimFailed(val reason: String) : AndroidUsbLiveConnectResult

    data class AuthorityRejected(
        val result: DefinitionDrivenLiveTuneConnectResult,
    ) : AndroidUsbLiveConnectResult

    data class InvalidSelection(val reason: String) : AndroidUsbLiveConnectResult

    data object OwnerClosed : AndroidUsbLiveConnectResult
}

sealed interface AndroidUsbLiveLoadResult {
    data class Completed(val result: LiveTuneOwnerLoadResult) : AndroidUsbLiveLoadResult

    data object NotConnected : AndroidUsbLiveLoadResult

    data object OwnerClosed : AndroidUsbLiveLoadResult
}

internal sealed interface AndroidUsbLiveRamWriteResult {
    data class Completed(
        val result: LiveTuneOwnerRamWriteResult,
    ) : AndroidUsbLiveRamWriteResult

    data object NotConnected : AndroidUsbLiveRamWriteResult

    data object OwnerClosed : AndroidUsbLiveRamWriteResult
}

internal sealed interface AndroidUsbLiveBurnResult {
    data class Completed(
        val result: LiveTuneOwnerBurnResult,
    ) : AndroidUsbLiveBurnResult

    data object NotConnected : AndroidUsbLiveBurnResult

    data object OwnerClosed : AndroidUsbLiveBurnResult
}

internal fun interface AndroidUsbEndpointChannelOpener {
    fun open(endpointId: String): AndroidUsbEndpointOpenResult
}

/**
 * Owns the physical Android USB channel for one explicitly connected live-tune controller.
 *
 * Transport ownership is deliberately downstream of profile authority. A channel is retained only
 * after [LiveTuneConnectionOwner] has both identified the controller and exact-selected an installed
 * compiled profile. Failed connection attempts always close their candidate channel.
 */
class AndroidUsbLiveConnectionSessionOwner internal constructor(
    private val connectionOwnerProvider: () -> LiveTuneConnectionOwner?,
    private val opener: AndroidUsbEndpointChannelOpener,
) : AutoCloseable {
    constructor(
        usbManager: UsbManager,
        connectionOwnerProvider: () -> LiveTuneConnectionOwner?,
    ) : this(
        connectionOwnerProvider = connectionOwnerProvider,
        opener =
            AndroidUsbEndpointChannelOpener { endpointId ->
                val discovery = AndroidUsbCandidateDiscovery(usbManager)
                val device = discovery.findDevice(endpointId)
                    ?: return@AndroidUsbEndpointChannelOpener AndroidUsbEndpointOpenResult.DeviceUnavailable
                when (val opened = AndroidUsbReadOnlyChannelFactory.openIfPermitted(usbManager, device)) {
                    is AndroidUsbReadOnlyOpenResult.Opened ->
                        AndroidUsbEndpointOpenResult.Opened(opened.channel)
                    AndroidUsbReadOnlyOpenResult.PermissionRequired ->
                        AndroidUsbEndpointOpenResult.PermissionRequired
                    is AndroidUsbReadOnlyOpenResult.TopologyRejected ->
                        AndroidUsbEndpointOpenResult.TopologyRejected(opened.resolution.toString())
                    is AndroidUsbReadOnlyOpenResult.OpenFailed ->
                        AndroidUsbEndpointOpenResult.OpenFailed(opened.reason)
                    is AndroidUsbReadOnlyOpenResult.ClaimFailed ->
                        AndroidUsbEndpointOpenResult.ClaimFailed(opened.reason)
                }
            },
    )

    @Volatile
    private var closed = false

    private var connectedChannel: UsbReadOnlyTransactionChannel? = null
    private var connectedOwner: LiveTuneConnectionOwner? = null
    private var endpointId: String? = null
    private var runtimeCandidate: RuntimeCandidateBindingResult =
        RuntimeCandidateBindingResult.Unavailable("No active ECU connection.")

    /** Candidate authority only. This is not a live-channel publication status. */
    internal val runtimeCandidateStatus: RuntimeCandidateBindingResult
        get() = synchronized(this) {
            if (closed || connectedOwner?.isAlive != true) {
                RuntimeCandidateBindingResult.Unavailable("No active ECU connection.")
            } else {
                runtimeCandidate
            }
        }

    val isConnected: Boolean
        get() = synchronized(this) { !closed && connectedChannel != null && connectedOwner?.isAlive == true }

    val connectedEndpointId: String?
        get() = synchronized(this) { endpointId }

    @Synchronized
    fun connect(
        endpointId: String,
        maxResponseBytes: Int,
        timeoutMs: Int,
    ): AndroidUsbLiveConnectResult = EngineAuthoritySerialization.exclusive {
        connectSerialized(endpointId, maxResponseBytes, timeoutMs)
    }

    private fun connectSerialized(
        endpointId: String,
        maxResponseBytes: Int,
        timeoutMs: Int,
    ): AndroidUsbLiveConnectResult {
        if (closed) return AndroidUsbLiveConnectResult.OwnerClosed
        disconnectCurrent()
        if (endpointId.isBlank()) {
            return AndroidUsbLiveConnectResult.InvalidSelection("USB endpoint must be selected.")
        }
        val authority =
            connectionOwnerProvider()
                ?: return AndroidUsbLiveConnectResult.ProfileAuthorityUnavailable
        val opened = opener.open(endpointId)
        val channel =
            when (opened) {
                is AndroidUsbEndpointOpenResult.Opened -> opened.channel
                AndroidUsbEndpointOpenResult.DeviceUnavailable ->
                    return AndroidUsbLiveConnectResult.DeviceUnavailable
                AndroidUsbEndpointOpenResult.PermissionRequired ->
                    return AndroidUsbLiveConnectResult.PermissionRequired
                is AndroidUsbEndpointOpenResult.TopologyRejected ->
                    return AndroidUsbLiveConnectResult.TopologyRejected(opened.reason)
                is AndroidUsbEndpointOpenResult.OpenFailed ->
                    return AndroidUsbLiveConnectResult.OpenFailed(opened.reason)
                is AndroidUsbEndpointOpenResult.ClaimFailed ->
                    return AndroidUsbLiveConnectResult.ClaimFailed(opened.reason)
            }

        return when (
            val connected =
                authority.connectUsingDefinitionQueries(
                    channel = channel,
                    maxResponseBytes = maxResponseBytes,
                    timeoutMs = timeoutMs,
                )
        ) {
            is DefinitionDrivenLiveTuneConnectResult.Connected -> {
                val candidate = authority.bindRuntimeCandidate(
                    endpointId = endpointId,
                    identity = connected.identity,
                    profileGeneration = connected.profileGeneration,
                    signatureLimit = maxResponseBytes,
                )
                connectedChannel = channel
                connectedOwner = authority
                this.endpointId = endpointId
                runtimeCandidate = candidate
                AndroidUsbLiveConnectResult.Connected(
                    endpointId = endpointId,
                    identity = connected.identity,
                    profileGeneration = connected.profileGeneration,
                )
            }

            else -> {
                channel.close()
                AndroidUsbLiveConnectResult.AuthorityRejected(connected)
            }
        }
    }

    @Synchronized
    fun loadCurrentController(
        signatureLimit: Int,
        timeoutMs: Int,
    ): AndroidUsbLiveLoadResult = EngineAuthoritySerialization.exclusive {
        loadCurrentControllerSerialized(signatureLimit, timeoutMs)
    }

    private fun loadCurrentControllerSerialized(
        signatureLimit: Int,
        timeoutMs: Int,
    ): AndroidUsbLiveLoadResult {
        if (closed) return AndroidUsbLiveLoadResult.OwnerClosed
        val channel = connectedChannel ?: return AndroidUsbLiveLoadResult.NotConnected
        val authority = connectedOwner ?: return AndroidUsbLiveLoadResult.NotConnected
        if (!authority.isAlive) {
            disconnectCurrent()
            return AndroidUsbLiveLoadResult.NotConnected
        }
        return AndroidUsbLiveLoadResult.Completed(
            authority.loadCurrentController(
                signatureLimit = signatureLimit,
                channel = channel,
                timeoutMs = timeoutMs,
            ),
        )
    }

    @Synchronized
    internal fun writeCurrentDraftToRam(
        signatureLimit: Int,
        timeoutMs: Int,
    ): AndroidUsbLiveRamWriteResult = EngineAuthoritySerialization.exclusive {
        writeCurrentDraftToRamSerialized(signatureLimit, timeoutMs)
    }

    private fun writeCurrentDraftToRamSerialized(
        signatureLimit: Int,
        timeoutMs: Int,
    ): AndroidUsbLiveRamWriteResult {
        if (closed) return AndroidUsbLiveRamWriteResult.OwnerClosed
        val channel = connectedChannel ?: return AndroidUsbLiveRamWriteResult.NotConnected
        val authority = connectedOwner ?: return AndroidUsbLiveRamWriteResult.NotConnected
        if (!authority.isAlive) {
            disconnectCurrent()
            return AndroidUsbLiveRamWriteResult.NotConnected
        }
        return AndroidUsbLiveRamWriteResult.Completed(
            authority.writeCurrentDraftToRam(
                signatureLimit = signatureLimit,
                channel = channel,
                timeoutMs = timeoutMs,
            ),
        )
    }

    @Synchronized
    internal fun burnPageToNonvolatile(
        page: Int,
        signatureLimit: Int,
        timeoutMs: Int,
    ): AndroidUsbLiveBurnResult = EngineAuthoritySerialization.exclusive {
        burnPageToNonvolatileSerialized(page, signatureLimit, timeoutMs)
    }

    private fun burnPageToNonvolatileSerialized(
        page: Int,
        signatureLimit: Int,
        timeoutMs: Int,
    ): AndroidUsbLiveBurnResult {
        if (closed) return AndroidUsbLiveBurnResult.OwnerClosed
        val channel = connectedChannel ?: return AndroidUsbLiveBurnResult.NotConnected
        val authority = connectedOwner ?: return AndroidUsbLiveBurnResult.NotConnected
        if (!authority.isAlive) {
            disconnectCurrent()
            return AndroidUsbLiveBurnResult.NotConnected
        }
        return AndroidUsbLiveBurnResult.Completed(
            authority.burnPageToNonvolatile(
                page = page,
                signatureLimit = signatureLimit,
                channel = channel,
                timeoutMs = timeoutMs,
            ),
        )
    }

    /**
     * Explicit one-shot acquisition, not a background poller or Dashboard source.
     * One retained USB channel and one engine lock span native prepare/USB/complete.
     * Any failure closes the physical channel and revokes the candidate.
     */
    @Synchronized
    internal fun readRuntimeOnce(timeoutMs: Int): RuntimeReadCycleResult =
        EngineAuthoritySerialization.exclusive {
            if (closed) return@exclusive RuntimeReadCycleResult.Unavailable("USB owner is closed.")
            val channel = connectedChannel
                ?: return@exclusive RuntimeReadCycleResult.Unavailable("No USB connection.")
            val authority = connectedOwner
                ?: return@exclusive RuntimeReadCycleResult.Unavailable("No profile owner.")
            if (!authority.isAlive || connectionOwnerProvider() !== authority) {
                disconnectCurrent()
                return@exclusive RuntimeReadCycleResult.Unavailable("Selected profile owner changed.")
            }
            val candidate = runtimeCandidate as? RuntimeCandidateBindingResult.Bound
                ?: return@exclusive RuntimeReadCycleResult.Unavailable("No verified runtime candidate.")
            val result = try {
                authority.readRuntimeOnce(candidate.sessionEpoch, channel, timeoutMs)
            } catch (failure: RuntimeException) {
                RuntimeReadCycleResult.Unavailable("Runtime transaction failed: ${failure.javaClass.simpleName}")
            } catch (failure: LinkageError) {
                RuntimeReadCycleResult.Unavailable("Native runtime bridge unavailable: ${failure.javaClass.simpleName}")
            }
            if (result !is RuntimeReadCycleResult.Fresh) {
                disconnectCurrent()
            }
            result
        }

    @Synchronized
    fun disconnect(): Boolean = EngineAuthoritySerialization.exclusive {
        if (closed) false else disconnectCurrent()
    }

    @Synchronized
    override fun close() {
        EngineAuthoritySerialization.exclusive {
            if (!closed) {
                disconnectCurrent()
                closed = true
            }
        }
    }

    private fun disconnectCurrent(): Boolean {
        val channel = connectedChannel
        val authority = connectedOwner
        val hadConnection = channel != null || authority != null
        connectedChannel = null
        connectedOwner = null
        endpointId = null
        runtimeCandidate = RuntimeCandidateBindingResult.Unavailable("No active ECU connection.")
        try {
            authority?.disconnect()
        } finally {
            channel?.close()
        }
        return hadConnection
    }
}
