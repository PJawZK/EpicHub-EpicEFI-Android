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
    fun cborStartsWithVersionedFourteenFieldCanonicalMap() {
        val capture = sampleCapture()
        val encoded = DiagnosticCaptureCborEncoder.encode(capture, sampleMetadata())

        assertEquals(0xAE, encoded[0].toInt() and 0xFF)
        assertEquals(0x6D, encoded[1].toInt() and 0xFF)
        assertEquals(
            "schemaVersion",
            encoded.copyOfRange(2, 15).toString(Charsets.UTF_8),
        )
        assertEquals(DiagnosticCaptureCborEncoder.SCHEMA_VERSION.toInt(), encoded[15].toInt())
        assertTrue(encoded.containsUtf8("nativeEngineEvent"))
        assertTrue(encoded.containsUtf8("worstFrames"))
        assertTrue(encoded.containsUtf8("retainedSampleCount"))
        assertTrue(encoded.containsUtf8("gitSha"))
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
        metrics = listOf(
            DiagnosticMetricSummary(
                metric = DiagnosticMetricId.UI_FRAME_TOTAL_MS,
                sampleCount = 300,
                retainedSampleCount = 256,
                first = 10.0,
                current = 12.0,
                minimum = 8.0,
                maximum = 40.0,
                p50 = 11.0,
                p95 = 20.0,
                p99 = 30.0,
            ),
        ),
        lifecycleOwners = emptyList(),
        markers = emptyList(),
        worstFrames = listOf(
            DiagnosticWorstFrame(
                frameNumber = 42,
                monotonicNanos = 15,
                wallClockMillis = 35,
                destinationId = "analyzer",
                totalMs = 40.0,
                budgetMs = 16.0,
                overrunMs = 24.0,
                vsyncDelayMs = 0.0,
                unknownDelayMs = 1.0,
                animationMs = 2.0,
                layoutMeasureMs = 0.2,
                drawMs = 10.0,
                syncMs = 0.3,
                commandIssueMs = 4.0,
                swapBuffersMs = 3.0,
                gpuMs = 8.0,
            ),
        ),
        note = null,
    )

    private fun sampleMetadata() = DiagnosticExportMetadata(
        appVersion = "test",
        buildType = "performance",
        gitSha = "abc123",
        manufacturer = "test",
        model = "test",
        androidApi = 37,
        supportedAbis = listOf("arm64-v8a"),
        processAbi = "arm64-v8a",
        processIs64Bit = true,
        displayWidthPx = 1920,
        displayHeightPx = 1080,
        densityDpi = 320,
        refreshRateHz = 60.0,
        orientation = "landscape",
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
