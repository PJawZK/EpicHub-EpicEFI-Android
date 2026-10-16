package com.epicefi.epichub.platform.transport.usb

import android.hardware.usb.UsbManager
import com.epicefi.epichub.engine.DefinitionDrivenLiveTuneConnectResult
import com.epicefi.epichub.engine.LiveTuneConnectionOwner
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

    val isConnected: Boolean
        get() = synchronized(this) { !closed && connectedChannel != null && connectedOwner?.isAlive == true }

    val connectedEndpointId: String?
        get() = synchronized(this) { endpointId }

    @Synchronized
    fun connect(
        endpointId: String,
        maxResponseBytes: Int,
        timeoutMs: Int,
    ): AndroidUsbLiveConnectResult {
        if (closed) return AndroidUsbLiveConnectResult.OwnerClosed
        if (endpointId.isBlank()) {
            return AndroidUsbLiveConnectResult.InvalidSelection("USB endpoint must be selected.")
        }

        disconnectCurrent()
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
                connectedChannel = channel
                connectedOwner = authority
                this.endpointId = endpointId
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
    fun disconnect(): Boolean {
        if (closed) return false
        return disconnectCurrent()
    }

    @Synchronized
    override fun close() {
        if (closed) return
        disconnectCurrent()
        closed = true
    }

    private fun disconnectCurrent(): Boolean {
        val channel = connectedChannel
        val authority = connectedOwner
        val hadConnection = channel != null || authority != null
        connectedChannel = null
        connectedOwner = null
        endpointId = null
        try {
            channel?.close()
        } finally {
            authority?.disconnect()
        }
        return hadConnection
    }
}
