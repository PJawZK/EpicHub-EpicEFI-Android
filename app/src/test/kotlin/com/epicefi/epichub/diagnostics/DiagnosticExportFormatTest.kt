package com.epicefi.epichub.diagnostics

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticExportFormatTest {
    @Test
    fun cborStartsWithVersionedThirteenFieldCanonicalMap() {
        val capture = sampleCapture()
        val encoded = DiagnosticCaptureCborEncoder.encode(capture, sampleMetadata())

        assertEquals(0xAD, encoded[0].toInt() and 0xFF)
        assertEquals(0x6D, encoded[1].toInt() and 0xFF)
        assertEquals(
            "schemaVersion",
            encoded.copyOfRange(2, 15).toString(Charsets.UTF_8),
        )
        assertEquals(DiagnosticCaptureCborEncoder.SCHEMA_VERSION.toInt(), encoded[15].toInt())
        assertTrue(encoded.containsUtf8("nativeEngineEvent"))
    }

    @Test
    fun archiveContainsOnlyCanonicalCoreEntriesByDefault() {
        val cbor = byteArrayOf(1, 2, 3)
        val report = "diagnostic report".toByteArray()
        val output = ByteArrayOutputStream()

        DiagnosticCaptureArchive.write(output, cbor, report)

        val entries = linkedMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(output.toByteArray())).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entries[entry.name] = zip.readBytes()
                zip.closeEntry()
            }
        }

        assertEquals(DiagnosticCaptureArchive.canonicalEntryNames, entries.keys)
        assertArrayEquals(cbor, entries.getValue("capture.cbor"))
        assertArrayEquals(report, entries.getValue("report.txt"))
    }

    private fun sampleCapture() = DiagnosticCapture(
        captureId = 7,
        mode = DiagnosticCaptureMode.REPRODUCTION,
        startedAtNanos = 10,
        triggerAtNanos = null,
        stoppedAtNanos = 20,
        createdAtWallClockMillis = 30,
        events = listOf(
            DiagnosticEvent(
                eventId = 1,
                monotonicNanos = 11,
                wallClockMillis = 31,
                severity = DiagnosticSeverity.INFO,
                domain = DiagnosticDomain.NATIVE_ENGINE,
                code = DiagnosticEventCode.NATIVE_ENGINE_CREATED,
                correlationId = "native-session-1",
                sessionId = 1,
                payload = DiagnosticPayload.NativeEngineEvent(
                    nativeEventId = 1,
                    engineElapsedNanos = 0,
                    rawCode = 1,
                    arg0 = 1,
                    arg1 = 0,
                ),
            ),
        ),
        metrics = emptyList(),
        lifecycleOwners = emptyList(),
        markers = emptyList(),
        note = null,
    )

    private fun sampleMetadata() = DiagnosticExportMetadata(
        appVersion = "test",
        buildType = "test",
        manufacturer = "test",
        model = "test",
        androidApi = 37,
        supportedAbis = listOf("arm64-v8a"),
    )

    private fun ByteArray.containsUtf8(value: String): Boolean {
        val needle = value.toByteArray(Charsets.UTF_8)
        if (needle.isEmpty() || needle.size > size) return false
        for (start in 0..size - needle.size) {
            if (needle.indices.all { offset -> this[start + offset] == needle[offset] }) {
                return true
            }
        }
        return false
    }
}
