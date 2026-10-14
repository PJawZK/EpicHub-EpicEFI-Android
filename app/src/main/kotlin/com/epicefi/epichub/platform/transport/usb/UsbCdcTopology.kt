package com.epicefi.epichub.platform.transport.usb

private const val USB_CLASS_COMM = 0x02
private const val USB_CLASS_CDC_DATA = 0x0A
private const val USB_ENDPOINT_DIRECTION_IN = 0x80
private const val USB_ENDPOINT_DIRECTION_OUT = 0x00
private const val USB_ENDPOINT_TYPE_BULK = 0x02

data class UsbEndpointFacts(
    val address: Int,
    val direction: Int,
    val type: Int,
    val maxPacketSize: Int,
)

data class UsbInterfaceFacts(
    val index: Int,
    val id: Int,
    val interfaceClass: Int,
    val interfaceSubclass: Int,
    val interfaceProtocol: Int,
    val endpoints: List<UsbEndpointFacts>,
)

data class UsbDeviceTopologyFacts(
    val interfaces: List<UsbInterfaceFacts>,
)

data class UsbCdcTopology(
    val controlInterfaceIndex: Int,
    val dataInterfaceIndex: Int,
    val bulkInEndpointAddress: Int,
    val bulkOutEndpointAddress: Int,
    val bulkInMaxPacketSize: Int,
    val bulkOutMaxPacketSize: Int,
)

sealed interface UsbCdcTopologyResolution {
    data class Compatible(val topology: UsbCdcTopology) : UsbCdcTopologyResolution

    data class Incompatible(val reason: String) : UsbCdcTopologyResolution

    data class Ambiguous(val reason: String) : UsbCdcTopologyResolution
}

object UsbCdcTopologyResolver {
    fun resolve(facts: UsbDeviceTopologyFacts): UsbCdcTopologyResolution {
        val controlInterfaces = facts.interfaces.filter { it.interfaceClass == USB_CLASS_COMM }
        val dataInterfaces = facts.interfaces.filter { it.interfaceClass == USB_CLASS_CDC_DATA }

        if (controlInterfaces.isEmpty()) {
            return UsbCdcTopologyResolution.Incompatible("missing CDC communications interface")
        }
        if (dataInterfaces.isEmpty()) {
            return UsbCdcTopologyResolution.Incompatible("missing CDC data interface")
        }
        if (controlInterfaces.size != 1) {
            return UsbCdcTopologyResolution.Ambiguous(
                "expected one CDC communications interface, found ${controlInterfaces.size}",
            )
        }
        if (dataInterfaces.size != 1) {
            return UsbCdcTopologyResolution.Ambiguous(
                "expected one CDC data interface, found ${dataInterfaces.size}",
            )
        }

        val dataInterface = dataInterfaces.single()
        val bulkIn =
            dataInterface.endpoints.filter {
                it.type == USB_ENDPOINT_TYPE_BULK && it.direction == USB_ENDPOINT_DIRECTION_IN
            }
        val bulkOut =
            dataInterface.endpoints.filter {
                it.type == USB_ENDPOINT_TYPE_BULK && it.direction == USB_ENDPOINT_DIRECTION_OUT
            }

        if (bulkIn.size != 1 || bulkOut.size != 1) {
            val reason =
                "CDC data interface requires exactly one bulk IN and one bulk OUT endpoint; " +
                    "found in=${bulkIn.size}, out=${bulkOut.size}"
            return if (bulkIn.size > 1 || bulkOut.size > 1) {
                UsbCdcTopologyResolution.Ambiguous(reason)
            } else {
                UsbCdcTopologyResolution.Incompatible(reason)
            }
        }

        val inEndpoint = bulkIn.single()
        val outEndpoint = bulkOut.single()
        if (inEndpoint.maxPacketSize <= 0 || outEndpoint.maxPacketSize <= 0) {
            return UsbCdcTopologyResolution.Incompatible("bulk endpoint max packet size must be positive")
        }

        return UsbCdcTopologyResolution.Compatible(
            UsbCdcTopology(
                controlInterfaceIndex = controlInterfaces.single().index,
                dataInterfaceIndex = dataInterface.index,
                bulkInEndpointAddress = inEndpoint.address,
                bulkOutEndpointAddress = outEndpoint.address,
                bulkInMaxPacketSize = inEndpoint.maxPacketSize,
                bulkOutMaxPacketSize = outEndpoint.maxPacketSize,
            ),
        )
    }
}
