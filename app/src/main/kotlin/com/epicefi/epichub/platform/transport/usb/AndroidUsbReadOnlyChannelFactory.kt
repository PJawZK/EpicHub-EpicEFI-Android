package com.epicefi.epichub.platform.transport.usb

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager

sealed interface AndroidUsbReadOnlyOpenResult {
    data class Opened(val channel: UsbReadOnlyTransactionChannel) : AndroidUsbReadOnlyOpenResult

    data object PermissionRequired : AndroidUsbReadOnlyOpenResult

    data class TopologyRejected(val resolution: UsbCdcTopologyResolution) : AndroidUsbReadOnlyOpenResult

    data class OpenFailed(val reason: String) : AndroidUsbReadOnlyOpenResult

    data class ClaimFailed(val reason: String) : AndroidUsbReadOnlyOpenResult
}

object AndroidUsbReadOnlyChannelFactory {
    fun openIfPermitted(
        usbManager: UsbManager,
        device: UsbDevice,
    ): AndroidUsbReadOnlyOpenResult {
        if (!usbManager.hasPermission(device)) {
            return AndroidUsbReadOnlyOpenResult.PermissionRequired
        }

        val resolution = AndroidUsbTopologyInspection.resolveCdc(device)
        val topology =
            when (resolution) {
                is UsbCdcTopologyResolution.Compatible -> resolution.topology
                else -> return AndroidUsbReadOnlyOpenResult.TopologyRejected(resolution)
            }

        val connection =
            usbManager.openDevice(device)
                ?: return AndroidUsbReadOnlyOpenResult.OpenFailed("Android refused to open USB device")
        val controlInterface = device.getInterface(topology.controlInterfaceIndex)
        val dataInterface = device.getInterface(topology.dataInterfaceIndex)

        if (!connection.claimInterface(controlInterface, true)) {
            connection.close()
            return AndroidUsbReadOnlyOpenResult.ClaimFailed("failed to claim CDC communications interface")
        }
        if (!connection.claimInterface(dataInterface, true)) {
            connection.releaseInterface(controlInterface)
            connection.close()
            return AndroidUsbReadOnlyOpenResult.ClaimFailed("failed to claim CDC data interface")
        }

        val bulkIn = dataInterface.endpointByAddress(topology.bulkInEndpointAddress)
        val bulkOut = dataInterface.endpointByAddress(topology.bulkOutEndpointAddress)
        if (bulkIn == null || bulkOut == null) {
            connection.releaseInterface(dataInterface)
            connection.releaseInterface(controlInterface)
            connection.close()
            return AndroidUsbReadOnlyOpenResult.OpenFailed("resolved bulk endpoint disappeared before open")
        }

        return AndroidUsbReadOnlyOpenResult.Opened(
            UsbReadOnlyTransactionChannel(
                AndroidUsbReadOnlyBulkPort(
                    connection = connection,
                    controlInterface = controlInterface,
                    dataInterface = dataInterface,
                    bulkIn = bulkIn,
                    bulkOut = bulkOut,
                ),
            ),
        )
    }
}

private fun UsbInterface.endpointByAddress(address: Int): UsbEndpoint? {
    for (index in 0 until endpointCount) {
        val endpoint = getEndpoint(index)
        if (endpoint.address == address) {
            return endpoint
        }
    }
    return null
}

private class AndroidUsbReadOnlyBulkPort(
    private val connection: UsbDeviceConnection,
    private val controlInterface: UsbInterface,
    private val dataInterface: UsbInterface,
    private val bulkIn: UsbEndpoint,
    private val bulkOut: UsbEndpoint,
) : UsbReadOnlyBulkPort {
    override fun sendRequest(
        request: ByteArray,
        timeoutMs: Int,
    ): Int = connection.bulkTransfer(bulkOut, request, request.size, timeoutMs)

    override fun receiveResponse(
        buffer: ByteArray,
        timeoutMs: Int,
    ): Int = connection.bulkTransfer(bulkIn, buffer, buffer.size, timeoutMs)

    override fun close() {
        connection.releaseInterface(dataInterface)
        connection.releaseInterface(controlInterface)
        connection.close()
    }
}
