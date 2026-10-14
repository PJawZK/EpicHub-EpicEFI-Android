package com.epicefi.epichub.platform.transport.usb

import com.epicefi.epichub.platform.transport.TransportCandidate
import com.epicefi.epichub.platform.transport.TransportKind

data class UsbDeviceFacts(
    val deviceId: Int,
    val deviceName: String,
    val vendorId: Int,
    val productId: Int,
    val deviceClass: Int,
    val deviceSubclass: Int,
    val deviceProtocol: Int,
    val interfaceCount: Int,
    val manufacturerName: String?,
    val productName: String?,
    val version: String?,
)

object UsbTransportCandidateMapper {
    fun map(facts: UsbDeviceFacts): TransportCandidate =
        TransportCandidate(
            kind = TransportKind.USB,
            endpointId = "usb:${facts.deviceName}:${facts.deviceId}",
            displayName = facts.productName ?: facts.manufacturerName ?: facts.deviceName,
            attributes = buildMap {
                put("vendorId", facts.vendorId.toString())
                put("productId", facts.productId.toString())
                put("deviceClass", facts.deviceClass.toString())
                put("deviceSubclass", facts.deviceSubclass.toString())
                put("deviceProtocol", facts.deviceProtocol.toString())
                put("interfaceCount", facts.interfaceCount.toString())
                facts.manufacturerName?.let { put("manufacturerName", it) }
                facts.productName?.let { put("productName", it) }
                facts.version?.let { put("version", it) }
            },
        )
}
