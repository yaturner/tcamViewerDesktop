package com.das.tcamviewerdesktop.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FirmwareTest {
    /** Minimal ESP-IDF image: magic byte, then esp_app_desc_t at offset 32. */
    private fun fakeImage(version: String, project: String): ByteArray {
        val bytes = ByteArray(256)
        bytes[0] = 0xE9.toByte()
        byteArrayOf(0x32, 0x54, 0xCD.toByte(), 0xAB.toByte()).copyInto(bytes, 32)
        version.toByteArray().copyInto(bytes, 48)
        project.toByteArray().copyInto(bytes, 80)
        return bytes
    }

    @Test
    fun parsesVersionAndProject() {
        val image = Firmware.parseImage(fakeImage("3.3", "tCamMini"))!!
        assertEquals("3.3", image.version)
        assertEquals("tCamMini", image.projectName)
    }

    @Test
    fun rejectsNonApplicationImages() {
        assertNull(Firmware.parseImage(ByteArray(256)))
        val noDescriptor = fakeImage("1.1", "tCam").also { it[32] = 0 }
        assertNull(Firmware.parseImage(noDescriptor))
        assertNull(Firmware.parseImage(ByteArray(10)))
    }

    @Test
    fun comparesVersions() {
        assertTrue(Firmware.isNewer("3.3", "3.2"))
        assertTrue(Firmware.isNewer("3.10", "3.9"))
        assertTrue(Firmware.isNewer("2.0", "1.9.9"))
        assertFalse(Firmware.isNewer("3.3", "3.3"))
        assertFalse(Firmware.isNewer("1.1", "3.3"))
    }

    @Test
    fun picksFirmwareByModel() {
        assertEquals("tCam", Firmware.expectedProjectName(0x70001))
        assertEquals("tCamMini", Firmware.expectedProjectName(262402)) // tCam-Mini WiFi
        assertEquals("tCamMini", Firmware.expectedProjectName(0x42003)) // Ethernet (tCam-POE)
        assertTrue(Firmware.supportsOta(262402))
        assertFalse(Firmware.supportsOta(2))
    }
}
