package com.epicefi.epichub.platform.transport.usb

data class UsbPermissionTarget(
    val endpointId: String,
    val deviceName: String,
    val deviceId: Int,
)

sealed interface UsbPermissionRequestStartResult {
    data object AlreadyGranted : UsbPermissionRequestStartResult

    data object Requested : UsbPermissionRequestStartResult

    data class Busy(
        val activeEndpointId: String,
    ) : UsbPermissionRequestStartResult

    data class Rejected(
        val reason: String,
    ) : UsbPermissionRequestStartResult

    data object OwnerClosed : UsbPermissionRequestStartResult
}

sealed interface UsbPermissionRequestCompletion {
    data class Granted(
        val target: UsbPermissionTarget,
    ) : UsbPermissionRequestCompletion

    data class Denied(
        val target: UsbPermissionTarget,
    ) : UsbPermissionRequestCompletion

    data object Ignored : UsbPermissionRequestCompletion
}

/**
 * Owns one explicit Android USB permission request at a time without owning transport/profile state.
 *
 * Device identity here is transport-candidate identity only. A granted permission authorizes Android
 * access to that exact USB device; it never grants ECU/profile/tune authority.
 */
internal class UsbPermissionRequestCoordinator : AutoCloseable {
    @Volatile
    private var closed = false

    private var pending: UsbPermissionTarget? = null

    val isClosed: Boolean
        get() = closed

    val pendingTarget: UsbPermissionTarget?
        get() = synchronized(this) { pending }

    @Synchronized
    fun begin(
        target: UsbPermissionTarget,
        alreadyGranted: Boolean,
        request: () -> String?,
    ): UsbPermissionRequestStartResult {
        if (closed) return UsbPermissionRequestStartResult.OwnerClosed

        pending?.let { active ->
            return UsbPermissionRequestStartResult.Busy(active.endpointId)
        }

        if (alreadyGranted) {
            return UsbPermissionRequestStartResult.AlreadyGranted
        }

        pending = target
        val failure =
            try {
                request()
            } catch (error: Exception) {
                error.message ?: error::class.java.simpleName
            }

        if (failure != null) {
            pending = null
            return UsbPermissionRequestStartResult.Rejected(failure)
        }

        return UsbPermissionRequestStartResult.Requested
    }

    @Synchronized
    fun complete(
        target: UsbPermissionTarget,
        granted: Boolean,
    ): UsbPermissionRequestCompletion {
        if (closed) return UsbPermissionRequestCompletion.Ignored
        val active = pending ?: return UsbPermissionRequestCompletion.Ignored
        if (active != target) return UsbPermissionRequestCompletion.Ignored

        pending = null
        return if (granted) {
            UsbPermissionRequestCompletion.Granted(active)
        } else {
            UsbPermissionRequestCompletion.Denied(active)
        }
    }

    @Synchronized
    override fun close() {
        if (closed) return
        pending = null
        closed = true
    }
}
