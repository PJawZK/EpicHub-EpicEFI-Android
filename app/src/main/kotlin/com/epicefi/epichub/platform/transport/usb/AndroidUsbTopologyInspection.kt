package com.epicefi.epichub.platform.transport.usb

import android.hardware.usb.UsbDevice

object AndroidUsbTopologyInspection {
    fun inspect(device: UsbDevice): UsbDeviceTopologyFacts =
        UsbDeviceTopologyFacts(
            interfaces =
                buildList {
                    for (interfaceIndex in 0 until device.interfaceCount) {
                        val usbInterface = device.getInterface(interfaceIndex)
                        add(
                            UsbInterfaceFacts(
                                index = interfaceIndex,
                                id = usbInterface.id,
                                interfaceClass = usbInterface.interfaceClass,
                                interfaceSubclass = usbInterface.interfaceSubclass,
                                interfaceProtocol = usbInterface.interfaceProtocol,
                                endpoints =
                                    buildList {
                                        for (endpointIndex in 0 until usbInterface.endpointCount) {
                                            val endpoint = usbInterface.getEndpoint(endpointIndex)
                                            add(
                                                UsbEndpointFacts(
                                                    address = endpoint.address,
                                                    direction = endpoint.direction,
                                                    type = endpoint.type,
                                                    maxPacketSize = endpoint.maxPacketSize,
                                                ),
                                            )
                                        }
                                    },
                            ),
                        )
                    }
                },
        )

    fun resolveCdc(device: UsbDevice): UsbCdcTopologyResolution =
        UsbCdcTopologyResolver.resolve(inspect(device))
}
