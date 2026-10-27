package com.epicefi.epichub.platform.transport.usb

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * System detach notifications only. The retained AndroidUsbLiveConnectionSessionOwner remains
 * the sole USB transport and registry authority; this observer opens no ports and grants nothing.
 */
internal class AndroidUsbDetachObserver(
    context: Context,
    private val onDetachedEndpoint: (String) -> Unit,
) : AutoCloseable {
    private val appContext = context.applicationContext

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != UsbManager.ACTION_USB_DEVICE_DETACHED) return
            val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
            } ?: return
            onDetachedEndpoint("usb:${device.deviceName}:${device.deviceId}")
        }
    }

    init {
        ContextCompat.registerReceiver(
            appContext,
            receiver,
            IntentFilter(UsbManager.ACTION_USB_DEVICE_DETACHED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    override fun close() {
        runCatching { appContext.unregisterReceiver(receiver) }
    }
}
