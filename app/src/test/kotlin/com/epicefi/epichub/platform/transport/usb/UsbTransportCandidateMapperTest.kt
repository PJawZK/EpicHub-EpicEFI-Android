package com.epicefi.epichub.platform.transport.usb

import com.epicefi.epichub.platform.transport.TransportKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class UsbTransportCandidateMapperTest {
    @Test
    fun maps_usb_facts_without_creating_ecu_identity_or_profile_authority() {
        val candidate =
            UsbTransportCandidateMapper.map(
                UsbDeviceFacts(
                    deviceId = 17,
                    deviceName = "/dev/bus/usb/001/002",
                    vendorId = 0x0483,
                    productId = 0x5740,
                    deviceClass = 2,
                    deviceSubclass = 0,
                    deviceProtocol = 0,
                    interfaceCount = 2,
                    manufacturerName = "EpicEFI",
                    productName = "Mega144H7",
                    version = "1.00",
                ),
            )

        assertEquals(TransportKind.USB, candidate.kind)
        assertEquals("usb:/dev/bus/usb/001/002:17", candidate.endpointId)
        assertEquals("Mega144H7", candidate.displayName)
        assertEquals("1155", candidate.attributes["vendorId"])
        assertEquals("22336", candidate.attributes["productId"])
        assertEquals("2", candidate.attributes["interfaceCount"])
        assertFalse(candidate.attributes.containsKey("signature"))
        assertFalse(candidate.attributes.containsKey("profile"))
        assertFalse(candidate.attributes.containsKey("profileName"))
    }

    @Test
    fun keeps_unknown_usb_devices_as_candidates_instead_of_filtering_by_known_vid_pid() {
        val candidate =
            UsbTransportCandidateMapper.map(
                UsbDeviceFacts(
                    deviceId = 3,
                    deviceName = "unknown-usb-device",
                    vendorId = 0x1234,
                    productId = 0x5678,
                    deviceClass = 255,
                    deviceSubclass = 1,
                    deviceProtocol = 2,
                    interfaceCount = 1,
                    manufacturerName = null,
                    productName = null,
                    version = null,
                ),
            )

        assertEquals("unknown-usb-device", candidate.displayName)
        assertEquals("4660", candidate.attributes["vendorId"])
        assertEquals("22136", candidate.attributes["productId"])
    }
}
