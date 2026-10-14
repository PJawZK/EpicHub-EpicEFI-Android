package com.epicefi.epichub.platform.transport.usb

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import com.epicefi.epichub.platform.transport.TransportCandidate

class AndroidUsbCandidateDiscovery(
    private val usbManager: UsbManager,
) {
    fun discover(): List<TransportCandidate> =
        usbManager.deviceList.values
            .map { device -> UsbTransportCandidateMapper.map(device.toFacts()) }
            .sortedBy(TransportCandidate::endpointId)
}

private fun UsbDevice.toFacts(): UsbDeviceFacts =
    UsbDeviceFacts(
        deviceId = deviceId,
        deviceName = deviceName,
        vendorId = vendorId,
        productId = productId,
        deviceClass = deviceClass,
        deviceSubclass = deviceSubclass,
        deviceProtocol = deviceProtocol,
        interfaceCount = interfaceCount,
        manufacturerName = safelyRead { manufacturerName },
        productName = safelyRead { productName },
        version = safelyRead { version },
    )

private inline fun <T> safelyRead(block: () -> T): T? =
    try {
        block()
    } catch (_: SecurityException) {
        null
    }
