package com.epicefi.epichub.engine

import com.epicefi.epichub.platform.transport.usb.UsbReadOnlyTransactionChannel

/**
 * A locally retained tune remains useful after transport loss, but it must never be presented as
 * current live-controller state. Reconnecting likewise does not make the retained snapshot live
 * again; a fresh exact identity and complete load are required.
 */
data class RetainedLiveTuneSource(
    val controllerSignature: String,
    val profileGeneration: Long,
    val pageCount: Int,
)

sealed interface LiveTuneSourceLifecycleState {
    data class Disconnected(
        val retainedSource: RetainedLiveTuneSource? = null,
    ) : LiveTuneSourceLifecycleState

    data class Connected(
        val identity: ValidatedRuntimeIdentity,
        val retainedSource: RetainedLiveTuneSource? = null,
    ) : LiveTuneSourceLifecycleState

    data class Loading(
        val identity: ValidatedRuntimeIdentity,
        val attemptId: Long,
        val retainedSource: RetainedLiveTuneSource? = null,
    ) : LiveTuneSourceLifecycleState

    data class Live(
        val identity: ValidatedRuntimeIdentity,
        val source: RetainedLiveTuneSource,
    ) : LiveTuneSourceLifecycleState

    data class LoadFailed(
        val identity: ValidatedRuntimeIdentity,
        val failure: UsbReadOnlyTuneLoadResult,
        val retainedSource: RetainedLiveTuneSource? = null,
    ) : LiveTuneSourceLifecycleState
}

data class LiveTuneLoadAttempt internal constructor(
    val id: Long,
    val identity: ValidatedRuntimeIdentity,
)

internal fun interface LiveTuneLoadOperation {
    fun load(
        signatureResponse: ByteArray,
        signatureLimit: Int,
        channel: UsbReadOnlyTransactionChannel,
        timeoutMs: Int,
    ): UsbReadOnlyTuneLoadResult
}

class LiveTuneSourceLifecycle internal constructor(
    private val loadOperation: LiveTuneLoadOperation,
) {
    constructor(engine: NativeEngine, registry: NativeProfileRegistry) : this(
        UsbReadOnlyTuneLoader(engine, registry).let { loader ->
            LiveTuneLoadOperation(loader::load)
        },
    )

    @Volatile
    var state: LiveTuneSourceLifecycleState = LiveTuneSourceLifecycleState.Disconnected()
        private set

    private var nextAttemptId = 1L

    @Synchronized
    fun onConnected(identity: ValidatedRuntimeIdentity) {
        state =
            LiveTuneSourceLifecycleState.Connected(
                identity = identity,
                retainedSource = retainedSource(state),
            )
    }

    @Synchronized
    fun onDisconnected() {
        state = LiveTuneSourceLifecycleState.Disconnected(retainedSource(state))
    }

    @Synchronized
    fun beginLoad(): LiveTuneLoadAttempt? {
        val connected =
            when (val current = state) {
                is LiveTuneSourceLifecycleState.Connected -> current
                is LiveTuneSourceLifecycleState.Live ->
                    LiveTuneSourceLifecycleState.Connected(current.identity, current.source)
                is LiveTuneSourceLifecycleState.LoadFailed ->
                    LiveTuneSourceLifecycleState.Connected(current.identity, current.retainedSource)
                is LiveTuneSourceLifecycleState.Disconnected,
                is LiveTuneSourceLifecycleState.Loading,
                -> return null
            }
        val attempt = LiveTuneLoadAttempt(nextAttemptId++, connected.identity)
        state =
            LiveTuneSourceLifecycleState.Loading(
                identity = attempt.identity,
                attemptId = attempt.id,
                retainedSource = connected.retainedSource,
            )
        return attempt
    }

    /**
     * Applies a completed loader result only if it belongs to the still-active connection/load
     * attempt. Results from a disconnected or superseded attempt are ignored.
     */
    @Synchronized
    fun completeLoad(
        attempt: LiveTuneLoadAttempt,
        result: UsbReadOnlyTuneLoadResult,
    ): Boolean {
        val loading = state as? LiveTuneSourceLifecycleState.Loading ?: return false
        if (loading.attemptId != attempt.id || loading.identity != attempt.identity) {
            return false
        }

        state =
            when (result) {
                is UsbReadOnlyTuneLoadResult.Loaded -> {
                    val source =
                        RetainedLiveTuneSource(
                            controllerSignature = loading.identity.signature,
                            profileGeneration = result.profileGeneration,
                            pageCount = result.pageCount,
                        )
                    LiveTuneSourceLifecycleState.Live(loading.identity, source)
                }
                else ->
                    LiveTuneSourceLifecycleState.LoadFailed(
                        identity = loading.identity,
                        failure = result,
                        retainedSource = loading.retainedSource,
                    )
            }
        return true
    }

    /**
     * Convenience path for the current synchronous USB loader. The attempt token still protects
     * this boundary if connection ownership later moves the operation to a coroutine/thread.
     */
    fun loadCurrentController(
        signatureLimit: Int,
        channel: UsbReadOnlyTransactionChannel,
        timeoutMs: Int,
    ): UsbReadOnlyTuneLoadResult? {
        val attempt = beginLoad() ?: return null
        val result =
            loadOperation.load(
                signatureResponse = attempt.identity.signature.toByteArray(Charsets.UTF_8),
                signatureLimit = signatureLimit,
                channel = channel,
                timeoutMs = timeoutMs,
            )
        completeLoad(attempt, result)
        return result
    }

    private fun retainedSource(state: LiveTuneSourceLifecycleState): RetainedLiveTuneSource? =
        when (state) {
            is LiveTuneSourceLifecycleState.Disconnected -> state.retainedSource
            is LiveTuneSourceLifecycleState.Connected -> state.retainedSource
            is LiveTuneSourceLifecycleState.Loading -> state.retainedSource
            is LiveTuneSourceLifecycleState.Live -> state.source
            is LiveTuneSourceLifecycleState.LoadFailed -> state.retainedSource
        }
}
