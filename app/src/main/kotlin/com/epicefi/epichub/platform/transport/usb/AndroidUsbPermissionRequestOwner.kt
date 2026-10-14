package com.epicefi.epichub.platform.transport.usb

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.epicefi.epichub.platform.transport.TransportCandidate
import com.epicefi.epichub.platform.transport.TransportKind

/**
 * Explicit Android USB permission owner.
 *
 * This class requests Android access to one selected USB transport candidate. Permission does not
 * imply controller identity, profile selection, tune authority, connection, page loading, write, or Burn.
 */
class AndroidUsbPermissionRequestOwner(
    context: Context,
    private val usbManager: UsbManager,
    private val onCompletion: (UsbPermissionRequestCompletion) -> Unit,
) : AutoCloseable {
    private val appContext = context.applicationContext
    private val coordinator = UsbPermissionRequestCoordinator()
    private val permissionAction = "${appContext.packageName}.USB_PERMISSION.${System.identityHashCode(this)}"
    private var pendingIntent: PendingIntent? = null

    private val receiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context?,
                intent: Intent?,
            ) {
                if (intent?.action != permissionAction) return
                val device = intent.usbDeviceExtra() ?: return
                val target = device.toPermissionTarget()
                val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                val completion = coordinator.complete(target, granted)
                if (completion != UsbPermissionRequestCompletion.Ignored) {
                    pendingIntent?.cancel()
                    pendingIntent = null
                    onCompletion(completion)
                }
            }
        }

    init {
        ContextCompat.registerReceiver(
            appContext,
            receiver,
            IntentFilter(permissionAction),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    val isClosed: Boolean
        get() = coordinator.isClosed

    val pendingEndpointId: String?
        get() = coordinator.pendingTarget?.endpointId

    fun request(candidate: TransportCandidate): UsbPermissionRequestStartResult {
        if (candidate.kind != TransportKind.USB) {
            return UsbPermissionRequestStartResult.Rejected("selected transport candidate is not USB")
        }

        val device = findDevice(candidate.endpointId)
            ?: return UsbPermissionRequestStartResult.Rejected(
                "selected USB device is no longer attached",
            )
        val target = device.toPermissionTarget()

        return coordinator.begin(
            target = target,
            alreadyGranted = usbManager.hasPermission(device),
        ) {
            val intent = Intent(permissionAction).setPackage(appContext.packageName)
            val requestIntent =
                PendingIntent.getBroadcast(
                    appContext,
                    target.deviceId,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
                )
            pendingIntent = requestIntent
            try {
                usbManager.requestPermission(device, requestIntent)
                null
            } catch (error: Exception) {
                pendingIntent = null
                requestIntent.cancel()
                error.message ?: error::class.java.simpleName
            }
        }
    }

    @Synchronized
    override fun close() {
        if (coordinator.isClosed) return
        coordinator.close()
        pendingIntent?.cancel()
        pendingIntent = null
        runCatching { appContext.unregisterReceiver(receiver) }
    }

    private fun findDevice(endpointId: String): UsbDevice? =
        usbManager.deviceList.values.firstOrNull { device ->
            device.toPermissionTarget().endpointId == endpointId
        }
}

private fun UsbDevice.toPermissionTarget(): UsbPermissionTarget =
    UsbPermissionTarget(
        endpointId = "usb:$deviceName:$deviceId",
        deviceName = deviceName,
        deviceId = deviceId,
    )

private fun Intent.usbDeviceExtra(): UsbDevice? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
    } else {
        @Suppress("DEPRECATION")
        getParcelableExtra(UsbManager.EXTRA_DEVICE)
    }
