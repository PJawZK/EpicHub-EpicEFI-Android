package com.epicefi.epichub.engine

import com.epicefi.epichub.platform.transport.usb.UsbReadOnlyTransactionChannel
import java.nio.ByteBuffer

/**
 * Candidate runtime authority is NOT live telemetry. A candidate has a registry-owned
 * definition and session epoch, but cannot be read without a separately qualified USB poller.
 */
internal sealed interface RuntimeCandidateBindingResult {
    data class Bound(val sessionEpoch: Long) : RuntimeCandidateBindingResult

    data class Unavailable(val reason: String) : RuntimeCandidateBindingResult
}

internal interface RuntimeCandidateBindingOperation {
    fun bind(
        endpointId: String,
        signature: String,
        profileGeneration: Long,
        signatureLimit: Int,
    ): RuntimeCandidateBindingResult

    fun disconnect()

    /** Unsupported by default for offline fake candidates; never invent a frame. */
    fun read(
        epoch: Long,
        channel: UsbReadOnlyTransactionChannel,
        timeoutMs: Int,
    ): RuntimeReadCycleResult =
        RuntimeReadCycleResult.Unavailable("Runtime read capability is not installed.")
}

/**
 * Called exclusively by a selected LiveTuneConnectionOwner, never the Dashboard.
 * Registry selection and USB identity must have already succeeded on the same connection.
 */
internal class NativeRegistryRuntimeCandidateBinding(
    private val registry: NativeProfileRegistry,
) : RuntimeCandidateBindingOperation {
    private val reader = NativeRuntimeReadCycle(registry::readOnlyRegistryHandle, registry::isAlive)

    override fun read(
        epoch: Long,
        channel: UsbReadOnlyTransactionChannel,
        timeoutMs: Int,
    ): RuntimeReadCycleResult = EngineAuthoritySerialization.exclusive {
        reader.read(epoch, channel, timeoutMs)
    }

    override fun bind(
        endpointId: String,
        signature: String,
        profileGeneration: Long,
        signatureLimit: Int,
    ): RuntimeCandidateBindingResult =
        EngineAuthoritySerialization.exclusive {
            // Reject malformed requests and revoke any earlier candidate.
            if (endpointId.isBlank() || signature.isBlank() || profileGeneration <= 0L || signatureLimit <= 0) {
                disconnect()
                return@exclusive RuntimeCandidateBindingResult.Unavailable("Invalid bound-runtime identity.")
            }
            val handle = registry.readOnlyRegistryHandle()
            if (handle == 0L || !registry.isAlive) {
                return@exclusive RuntimeCandidateBindingResult.Unavailable("Profile registry is closed.")
            }
            val signatureBytes = signature.toByteArray(Charsets.UTF_8)
            val signatureBuffer = ByteBuffer.allocateDirect(signatureBytes.size).apply {
                put(signatureBytes)
                rewind()
            }
            val result = NativeProfileRegistryBridge.bindRuntimeCandidate(
                handle,
                endpointId,
                signatureBuffer,
                signatureBytes.size,
                signatureLimit,
                profileGeneration,
            )
            if (result > 0L) {
                RuntimeCandidateBindingResult.Bound(result)
            } else {
                disconnect()
                RuntimeCandidateBindingResult.Unavailable("Runtime definition is not authorized (native status $result).")
            }
        }

    override fun disconnect() {
        EngineAuthoritySerialization.exclusive {
            val handle = registry.readOnlyRegistryHandle()
            if (handle != 0L) {
                NativeProfileRegistryBridge.disconnectRuntimeCandidate(handle)
            }
        }
    }
}
