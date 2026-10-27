package com.epicefi.epichub.platform.transport.usb

import android.content.Context
import android.hardware.usb.UsbManager
import android.os.SystemClock
import com.epicefi.epichub.engine.DefinitionDrivenLiveTuneConnectResult
import com.epicefi.epichub.engine.EngineAuthoritySerialization
import com.epicefi.epichub.engine.RuntimeCandidateBindingResult
import com.epicefi.epichub.engine.RuntimeReadCycleResult
import com.epicefi.epichub.engine.LiveRuntimeSamplingGate
import com.epicefi.epichub.engine.LiveRuntimeSamplingPolicy
import com.epicefi.epichub.engine.LiveRuntimeSampleView
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
 * Read-only access check; never requests permissions or opens another USB channel.
 * Android's concrete owner checks the selected endpoint is still attached and permitted.
 */
internal fun interface AndroidUsbEndpointAccess {
    fun isUsable(endpointId: String): Boolean
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
    private val monotonicMs: () -> Long = { SystemClock.elapsedRealtime() },
    samplingPolicy: LiveRuntimeSamplingPolicy = LiveRuntimeSamplingPolicy(),
    private val endpointAccess: AndroidUsbEndpointAccess = AndroidUsbEndpointAccess { true },
) : AutoCloseable {
    constructor(
        usbManager: UsbManager,
        connectionOwnerProvider: () -> LiveTuneConnectionOwner?,
    ) : this(
        connectionOwnerProvider = connectionOwnerProvider,
        endpointAccess = AndroidUsbEndpointAccess { endpointId ->
            val device = AndroidUsbCandidateDiscovery(usbManager).findDevice(endpointId)
            device != null && runCatching { usbManager.hasPermission(device) }.getOrDefault(false)
        },
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

    /**
     * Context-aware construction additionally subscribes to system USB detach events.
     * The existing UsbManager-only constructor remains usable and still checks the
     * actual endpoint grant on every read/load/write and source snapshot.
     */
    constructor(
        context: Context,
        usbManager: UsbManager,
        connectionOwnerProvider: () -> LiveTuneConnectionOwner?,
    ) : this(usbManager, connectionOwnerProvider) {
        detachObserver = AndroidUsbDetachObserver(context) { endpoint ->
            onEndpointUnavailable(endpoint)
        }
    }

    private var detachObserver: AndroidUsbDetachObserver? = null

    @Volatile
    private var closed = false

    private val runtimeSampling = LiveRuntimeSamplingGate(samplingPolicy)
    private var connectedChannel: UsbReadOnlyTransactionChannel? = null
    private var connectedOwner: LiveTuneConnectionOwner? = null
    private var endpointId: String? = null
    private var runtimeCandidate: RuntimeCandidateBindingResult =
        RuntimeCandidateBindingResult.Unavailable("No active ECU connection.")

    /** Candidate authority only. This is not a live-channel publication status. */
    internal val runtimeCandidateStatus: RuntimeCandidateBindingResult
        get() = synchronized(this) {
            if (closed || connectedOwner?.isAlive != true ||
                connectedOwner !== connectionOwnerProvider() ||
                endpointId?.let(endpointAccess::isUsable) != true) {
                RuntimeCandidateBindingResult.Unavailable("No active ECU connection.")
            } else {
                runtimeCandidate
            }
        }

    val isConnected: Boolean
        get() = synchronized(this) {
            !closed && connectedChannel != null && connectedOwner?.isAlive == true &&
                connectedOwner === connectionOwnerProvider() &&
                endpointId?.let(endpointAccess::isUsable) == true
        }

    val connectedEndpointId: String?
        get() = synchronized(this) {
            endpointId?.takeIf {
                connectedOwner?.isAlive == true &&
                    connectedOwner === connectionOwnerProvider() && endpointAccess.isUsable(it)
            }
        }

    /** Called by an owning Android USB detach/permission-loss observer, never by the UI. */
    @Synchronized
    internal fun onEndpointUnavailable(changedEndpointId: String): Boolean =
        EngineAuthoritySerialization.exclusive {
            if (closed || changedEndpointId.isBlank() || endpointId != changedEndpointId) {
                false
            } else {
                disconnectCurrent()
            }
        }

    /** Reconcile physical device presence and grant before any retained transport use. */
    private fun hasUsableRetainedEndpoint(): Boolean {
        val selected = endpointId ?: return false
        if (endpointAccess.isUsable(selected)) return true
        disconnectCurrent()
        return false
    }

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

        // Preserve opener-specific PermissionRequired versus DeviceUnavailable.
        // If a test/opener offers a channel after access was revoked, close it
        // before issuing even the first identity-query transfer.
        if (!endpointAccess.isUsable(endpointId)) {
            channel.close()
            return AndroidUsbLiveConnectResult.DeviceUnavailable
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
                if (!endpointAccess.isUsable(endpointId)) {
                    authority.disconnect()
                    channel.close()
                    return AndroidUsbLiveConnectResult.DeviceUnavailable
                }
                val candidate = authority.bindRuntimeCandidate(
                    endpointId = endpointId,
                    identity = connected.identity,
                    profileGeneration = connected.profileGeneration,
                    signatureLimit = maxResponseBytes,
                )
                if (!endpointAccess.isUsable(endpointId)) {
                    authority.disconnect()
                    channel.close()
                    return AndroidUsbLiveConnectResult.DeviceUnavailable
                }
                connectedChannel = channel
                connectedOwner = authority
                this.endpointId = endpointId
                runtimeCandidate = candidate
                if (candidate is RuntimeCandidateBindingResult.Bound) {
                    runtimeSampling.bind(candidate.sessionEpoch)
                }
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
        if (!hasUsableRetainedEndpoint()) return AndroidUsbLiveLoadResult.NotConnected
        val channel = connectedChannel ?: return AndroidUsbLiveLoadResult.NotConnected
        val authority = connectedOwner ?: return AndroidUsbLiveLoadResult.NotConnected
        if (!authority.isAlive || connectionOwnerProvider() !== authority) {
            disconnectCurrent()
            return AndroidUsbLiveLoadResult.NotConnected
        }
        val result = authority.loadCurrentController(
            signatureLimit = signatureLimit,
            channel = channel,
            timeoutMs = timeoutMs,
        )
        if (connectedOwner !== authority || connectedChannel !== channel ||
            !authority.isAlive || connectionOwnerProvider() !== authority ||
            !hasUsableRetainedEndpoint()
        ) {
            disconnectCurrent()
            return AndroidUsbLiveLoadResult.NotConnected
        }
        return AndroidUsbLiveLoadResult.Completed(result)
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
        if (!hasUsableRetainedEndpoint()) return AndroidUsbLiveRamWriteResult.NotConnected
        val channel = connectedChannel ?: return AndroidUsbLiveRamWriteResult.NotConnected
        val authority = connectedOwner ?: return AndroidUsbLiveRamWriteResult.NotConnected
        if (!authority.isAlive || connectionOwnerProvider() !== authority) {
            disconnectCurrent()
            return AndroidUsbLiveRamWriteResult.NotConnected
        }
        val result = authority.writeCurrentDraftToRam(
            signatureLimit = signatureLimit,
            channel = channel,
            timeoutMs = timeoutMs,
        )
        if (connectedOwner !== authority || connectedChannel !== channel ||
            !authority.isAlive || connectionOwnerProvider() !== authority ||
            !hasUsableRetainedEndpoint()
        ) {
            disconnectCurrent()
            return AndroidUsbLiveRamWriteResult.NotConnected
        }
        return AndroidUsbLiveRamWriteResult.Completed(result)
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
        if (!hasUsableRetainedEndpoint()) return AndroidUsbLiveBurnResult.NotConnected
        val channel = connectedChannel ?: return AndroidUsbLiveBurnResult.NotConnected
        val authority = connectedOwner ?: return AndroidUsbLiveBurnResult.NotConnected
        if (!authority.isAlive || connectionOwnerProvider() !== authority) {
            disconnectCurrent()
            return AndroidUsbLiveBurnResult.NotConnected
        }
        val result = authority.burnPageToNonvolatile(
            page = page,
            signatureLimit = signatureLimit,
            channel = channel,
            timeoutMs = timeoutMs,
        )
        if (connectedOwner !== authority || connectedChannel !== channel ||
            !authority.isAlive || connectionOwnerProvider() !== authority ||
            !hasUsableRetainedEndpoint()
        ) {
            disconnectCurrent()
            return AndroidUsbLiveBurnResult.NotConnected
        }
        return AndroidUsbLiveBurnResult.Completed(result)
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
            if (!hasUsableRetainedEndpoint()) {
                return@exclusive RuntimeReadCycleResult.Unavailable("USB endpoint unavailable or permission revoked.")
            }
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
            if (result is RuntimeReadCycleResult.Fresh &&
                (result.frame.epoch != candidate.sessionEpoch ||
                    connectedChannel !== channel || connectedOwner !== authority ||
                    !authority.isAlive || connectionOwnerProvider() !== authority ||
                    !hasUsableRetainedEndpoint())
            ) {
                disconnectCurrent()
                return@exclusive RuntimeReadCycleResult.Unavailable(
                    "Runtime response returned after its USB/profile authority changed.",
                )
            }
            if (result !is RuntimeReadCycleResult.Fresh) {
                disconnectCurrent()
            }
            result
        }

    /**
     * One admission-controlled read per explicit caller tick. No timer, poller,
     * coroutine or additional USB owner is created by this API.
     */
    @Synchronized
    internal fun sampleRuntimeIfDue(): LiveRuntimeSampleView =
        EngineAuthoritySerialization.exclusive {
            val now = monotonicMs()
            if (closed) return@exclusive runtimeSampling.snapshot(now)
            if (!hasUsableRetainedEndpoint()) return@exclusive runtimeSampling.snapshot(now)
            val authority = connectedOwner
            if (authority == null || !authority.isAlive || connectionOwnerProvider() !== authority) {
                if (connectedChannel != null) disconnectCurrent()
                return@exclusive runtimeSampling.snapshot(now)
            }
            val candidate = runtimeCandidate as? RuntimeCandidateBindingResult.Bound
                ?: return@exclusive runtimeSampling.snapshot(now)
            if (!runtimeSampling.tryBegin(now)) return@exclusive runtimeSampling.snapshot(now)

            val result = readRuntimeOnce(runtimeSampling.timeoutMs)
            when (result) {
                is RuntimeReadCycleResult.Fresh -> {
                    if (!runtimeSampling.accept(candidate.sessionEpoch, result.frame, monotonicMs())) {
                        disconnectCurrent()
                        runtimeSampling.fail("Validated runtime batch lost sampling authority.")
                    }
                }
                is RuntimeReadCycleResult.Unavailable -> {
                    // readRuntimeOnce already revoked the retained connection on errors.
                    disconnectCurrent()
                    runtimeSampling.fail(result.reason)
                }
            }
            runtimeSampling.snapshot(monotonicMs())
        }

    /** Read-side freshness assessment performs no ECU transaction. */
    @Synchronized
    internal fun sampledRuntimeSnapshot(): LiveRuntimeSampleView =
        EngineAuthoritySerialization.exclusive {
            hasUsableRetainedEndpoint()
            val authority = connectedOwner
            if (authority != null &&
                (!authority.isAlive || connectionOwnerProvider() !== authority)
            ) {
                disconnectCurrent()
            }
            runtimeSampling.snapshot(monotonicMs())
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
                detachObserver?.close()
                detachObserver = null
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
        runtimeSampling.disconnect()
        try {
            authority?.disconnect()
        } finally {
            channel?.close()
        }
        return hadConnection
    }
}
