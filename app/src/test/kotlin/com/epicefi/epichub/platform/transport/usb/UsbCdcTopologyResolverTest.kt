package com.epicefi.epichub.platform.transport.usb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UsbCdcTopologyResolverTest {
    @Test
    fun resolves_cdc_by_class_and_bulk_direction_when_interface_numbers_shift() {
        val result =
            UsbCdcTopologyResolver.resolve(
                UsbDeviceTopologyFacts(
                    interfaces =
                        listOf(
                            interfaceFacts(index = 0, interfaceClass = 0x08),
                            interfaceFacts(index = 1, interfaceClass = 0x02),
                            interfaceFacts(
                                index = 2,
                                interfaceClass = 0x0A,
                                endpoints =
                                    listOf(
                                        endpoint(address = 0x82, direction = 0x80, type = 0x02),
                                        endpoint(address = 0x02, direction = 0x00, type = 0x02),
                                    ),
                            ),
                        ),
                ),
            )

        val topology = (result as UsbCdcTopologyResolution.Compatible).topology
        assertEquals(1, topology.controlInterfaceIndex)
        assertEquals(2, topology.dataInterfaceIndex)
        assertEquals(0x82, topology.bulkInEndpointAddress)
        assertEquals(0x02, topology.bulkOutEndpointAddress)
    }

    @Test
    fun ignores_non_bulk_endpoints_on_the_cdc_data_interface() {
        val result =
            UsbCdcTopologyResolver.resolve(
                UsbDeviceTopologyFacts(
                    interfaces =
                        listOf(
                            interfaceFacts(index = 4, interfaceClass = 0x02),
                            interfaceFacts(
                                index = 7,
                                interfaceClass = 0x0A,
                                endpoints =
                                    listOf(
                                        endpoint(address = 0x81, direction = 0x80, type = 0x03),
                                        endpoint(address = 0x83, direction = 0x80, type = 0x02),
                                        endpoint(address = 0x03, direction = 0x00, type = 0x02),
                                    ),
                            ),
                        ),
                ),
            )

        assertTrue(result is UsbCdcTopologyResolution.Compatible)
    }

    @Test
    fun rejects_missing_cdc_data_interface() {
        val result =
            UsbCdcTopologyResolver.resolve(
                UsbDeviceTopologyFacts(
                    interfaces = listOf(interfaceFacts(index = 0, interfaceClass = 0x02)),
                ),
            )

        assertTrue(result is UsbCdcTopologyResolution.Incompatible)
    }

    @Test
    fun fails_closed_when_multiple_cdc_data_interfaces_exist() {
        val result =
            UsbCdcTopologyResolver.resolve(
                UsbDeviceTopologyFacts(
                    interfaces =
                        listOf(
                            interfaceFacts(index = 0, interfaceClass = 0x02),
                            interfaceFacts(index = 1, interfaceClass = 0x0A),
                            interfaceFacts(index = 2, interfaceClass = 0x0A),
                        ),
                ),
            )

        assertTrue(result is UsbCdcTopologyResolution.Ambiguous)
    }

    @Test
    fun fails_closed_when_bulk_endpoint_direction_is_not_unique() {
        val result =
            UsbCdcTopologyResolver.resolve(
                UsbDeviceTopologyFacts(
                    interfaces =
                        listOf(
                            interfaceFacts(index = 0, interfaceClass = 0x02),
                            interfaceFacts(
                                index = 1,
                                interfaceClass = 0x0A,
                                endpoints =
                                    listOf(
                                        endpoint(address = 0x81, direction = 0x80, type = 0x02),
                                        endpoint(address = 0x82, direction = 0x80, type = 0x02),
                                        endpoint(address = 0x01, direction = 0x00, type = 0x02),
                                    ),
                            ),
                        ),
                ),
            )

        assertTrue(result is UsbCdcTopologyResolution.Ambiguous)
    }

    @Test
    fun rejects_zero_packet_size() {
        val result =
            UsbCdcTopologyResolver.resolve(
                UsbDeviceTopologyFacts(
                    interfaces =
                        listOf(
                            interfaceFacts(index = 0, interfaceClass = 0x02),
                            interfaceFacts(
                                index = 1,
                                interfaceClass = 0x0A,
                                endpoints =
                                    listOf(
                                        endpoint(
                                            address = 0x81,
                                            direction = 0x80,
                                            type = 0x02,
                                            maxPacketSize = 0,
                                        ),
                                        endpoint(address = 0x01, direction = 0x00, type = 0x02),
                                    ),
                            ),
                        ),
                ),
            )

        assertTrue(result is UsbCdcTopologyResolution.Incompatible)
    }

    private fun interfaceFacts(
        index: Int,
        interfaceClass: Int,
        endpoints: List<UsbEndpointFacts> = emptyList(),
    ) =
        UsbInterfaceFacts(
            index = index,
            id = index + 10,
            interfaceClass = interfaceClass,
            interfaceSubclass = 0,
            interfaceProtocol = 0,
            endpoints = endpoints,
        )

    private fun endpoint(
        address: Int,
        direction: Int,
        type: Int,
        maxPacketSize: Int = 64,
    ) =
        UsbEndpointFacts(
            address = address,
            direction = direction,
            type = type,
            maxPacketSize = maxPacketSize,
        )
}
