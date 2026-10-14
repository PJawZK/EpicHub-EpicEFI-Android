package com.epicefi.epichub.ui.tuner

import com.epicefi.epichub.engine.LiveTuneSourceLifecycleState
import com.epicefi.epichub.engine.RetainedLiveTuneSource
import com.epicefi.epichub.engine.UsbReadOnlyTuneLoadResult

enum class LiveTuneSourceStatusKind {
    DISCONNECTED,
    CONNECTED,
    LOADING,
    LIVE,
    LOAD_FAILED,
}

enum class LiveTuneSourceFailureKind {
    AUTHORITY_REJECTED,
    TRANSPORT_REJECTED,
    TRANSPORT_FAILURE,
    INSTALL_REJECTED,
}

data class LiveTuneSourceStatus(
    val kind: LiveTuneSourceStatusKind,
    val controllerSignature: String?,
    val retainedControllerSignature: String?,
    val profileGeneration: Long?,
    val pageCount: Int?,
    val attemptId: Long?,
    val failureKind: LiveTuneSourceFailureKind?,
    val liveCurrent: Boolean,
    val retainedLocalTune: Boolean,
)

fun LiveTuneSourceLifecycleState.toTunerStatus(): LiveTuneSourceStatus =
    when (this) {
        is LiveTuneSourceLifecycleState.Disconnected ->
            status(
                kind = LiveTuneSourceStatusKind.DISCONNECTED,
                retainedSource = retainedSource,
            )

        is LiveTuneSourceLifecycleState.Connected ->
            status(
                kind = LiveTuneSourceStatusKind.CONNECTED,
                controllerSignature = identity.signature,
                retainedSource = retainedSource,
            )

        is LiveTuneSourceLifecycleState.Loading ->
            status(
                kind = LiveTuneSourceStatusKind.LOADING,
                controllerSignature = identity.signature,
                retainedSource = retainedSource,
                attemptId = attemptId,
            )

        is LiveTuneSourceLifecycleState.Live ->
            status(
                kind = LiveTuneSourceStatusKind.LIVE,
                controllerSignature = identity.signature,
                retainedSource = source,
                liveCurrent = true,
            )

        is LiveTuneSourceLifecycleState.LoadFailed ->
            status(
                kind = LiveTuneSourceStatusKind.LOAD_FAILED,
                controllerSignature = identity.signature,
                retainedSource = retainedSource,
                failureKind = failure.failureKind(),
            )
    }

private fun status(
    kind: LiveTuneSourceStatusKind,
    controllerSignature: String? = null,
    retainedSource: RetainedLiveTuneSource? = null,
    attemptId: Long? = null,
    failureKind: LiveTuneSourceFailureKind? = null,
    liveCurrent: Boolean = false,
): LiveTuneSourceStatus =
    LiveTuneSourceStatus(
        kind = kind,
        controllerSignature = controllerSignature,
        retainedControllerSignature = retainedSource?.controllerSignature,
        profileGeneration = retainedSource?.profileGeneration,
        pageCount = retainedSource?.pageCount,
        attemptId = attemptId,
        failureKind = failureKind,
        liveCurrent = liveCurrent,
        retainedLocalTune = retainedSource != null,
    )

private fun UsbReadOnlyTuneLoadResult.failureKind(): LiveTuneSourceFailureKind =
    when (this) {
        is UsbReadOnlyTuneLoadResult.AuthorityRejected ->
            LiveTuneSourceFailureKind.AUTHORITY_REJECTED

        is UsbReadOnlyTuneLoadResult.TransportRejected ->
            LiveTuneSourceFailureKind.TRANSPORT_REJECTED

        is UsbReadOnlyTuneLoadResult.TransportFailure ->
            LiveTuneSourceFailureKind.TRANSPORT_FAILURE

        is UsbReadOnlyTuneLoadResult.InstallRejected ->
            LiveTuneSourceFailureKind.INSTALL_REJECTED

        is UsbReadOnlyTuneLoadResult.Loaded ->
            error("A successful live tune load cannot represent a failed lifecycle state.")
    }
