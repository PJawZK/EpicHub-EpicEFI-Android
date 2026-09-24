package com.epicefi.epichub.diagnostics

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

internal data class DiagnosticExportMetadata(
    val appVersion: String,
    val buildType: String,
    val gitSha: String,
    val manufacturer: String,
    val model: String,
    val androidApi: Int,
    val supportedAbis: List<String>,
    val processAbi: String,
    val processIs64Bit: Boolean,
    val displayWidthPx: Int,
    val displayHeightPx: Int,
    val densityDpi: Int,
    val refreshRateHz: Double,
    val orientation: String,
)

internal object DiagnosticCaptureCborEncoder {
    const val SCHEMA_VERSION = 3L

    fun encode(
        capture: DiagnosticCapture,
        metadata: DiagnosticExportMetadata,
    ): ByteArray {
        val writer = CborWriter()
        writer.writeMapHeader(14)
        writer.writeText("schemaVersion")
        writer.writeLong(SCHEMA_VERSION)
        writer.writeText("captureId")
        writer.writeLong(capture.captureId)
        writer.writeText("mode")
        writer.writeText(capture.mode.name)
        writer.writeText("startedAtNanos")
        writer.writeLong(capture.startedAtNanos)
        writer.writeText("triggerAtNanos")
        writer.writeNullableLong(capture.triggerAtNanos)
        writer.writeText("stoppedAtNanos")
        writer.writeLong(capture.stoppedAtNanos)
        writer.writeText("createdAtWallClockMillis")
        writer.writeLong(capture.createdAtWallClockMillis)
        writer.writeText("note")
        writer.writeNullableText(capture.note)
        writer.writeText("metadata")
        writeMetadata(writer, metadata)
        writer.writeText("events")
        writer.writeArrayHeader(capture.events.size)
        capture.events.forEach { writeEvent(writer, it) }
        writer.writeText("metrics")
        writer.writeArrayHeader(capture.metrics.size)
        capture.metrics.forEach { writeMetric(writer, it) }
        writer.writeText("lifecycleOwners")
        writer.writeArrayHeader(capture.lifecycleOwners.size)
        capture.lifecycleOwners.forEach { writeLifecycleOwner(writer, it) }
        writer.writeText("markers")
        writer.writeArrayHeader(capture.markers.size)
        capture.markers.forEach { writeMarker(writer, it) }
        writer.writeText("worstFrames")
        writer.writeArrayHeader(capture.worstFrames.size)
        capture.worstFrames.forEach { writeWorstFrame(writer, it) }
        return writer.toByteArray()
    }

    private fun writeMetadata(writer: CborWriter, metadata: DiagnosticExportMetadata) {
        writer.writeMapHeader(14)
        writer.writeText("appVersion")
        writer.writeText(metadata.appVersion)
        writer.writeText("buildType")
        writer.writeText(metadata.buildType)
        writer.writeText("gitSha")
        writer.writeText(metadata.gitSha)
        writer.writeText("manufacturer")
        writer.writeText(metadata.manufacturer)
        writer.writeText("model")
        writer.writeText(metadata.model)
        writer.writeText("androidApi")
        writer.writeLong(metadata.androidApi.toLong())
        writer.writeText("supportedAbis")
        writer.writeArrayHeader(metadata.supportedAbis.size)
        metadata.supportedAbis.forEach(writer::writeText)
        writer.writeText("processAbi")
        writer.writeText(metadata.processAbi)
        writer.writeText("processIs64Bit")
        writer.writeBoolean(metadata.processIs64Bit)
        writer.writeText("displayWidthPx")
        writer.writeLong(metadata.displayWidthPx.toLong())
        writer.writeText("displayHeightPx")
        writer.writeLong(metadata.displayHeightPx.toLong())
        writer.writeText("densityDpi")
        writer.writeLong(metadata.densityDpi.toLong())
        writer.writeText("refreshRateHz")
        writer.writeDouble(metadata.refreshRateHz)
        writer.writeText("orientation")
        writer.writeText(metadata.orientation)
    }

    private fun writeEvent(writer: CborWriter, event: DiagnosticEvent) {
        writer.writeMapHeader(9)
        writer.writeText("eventId")
        writer.writeLong(event.eventId)
        writer.writeText("monotonicNanos")
        writer.writeLong(event.monotonicNanos)
        writer.writeText("wallClockMillis")
        writer.writeLong(event.wallClockMillis)
        writer.writeText("severity")
        writer.writeText(event.severity.name)
        writer.writeText("domain")
        writer.writeText(event.domain.name)
        writer.writeText("code")
        writer.writeText(event.code.stableId)
        writer.writeText("correlationId")
        writer.writeNullableText(event.correlationId)
        writer.writeText("sessionId")
        writer.writeNullableLong(event.sessionId)
        writer.writeText("payload")
        writePayload(writer, event.payload)
    }

    private fun writePayload(writer: CborWriter, payload: DiagnosticPayload) {
        when (payload) {
            DiagnosticPayload.None -> {
                writer.writeMapHeader(1)
                writer.writeText("type")
                writer.writeText("none")
            }

            is DiagnosticPayload.DestinationChanged -> {
                writer.writeMapHeader(2)
                writer.writeText("type")
                writer.writeText("destinationChanged")
                writer.writeText("destinationId")
                writer.writeText(payload.destinationId)
            }

            is DiagnosticPayload.NativeEngineState -> {
                writer.writeMapHeader(3)
                writer.writeText("type")
                writer.writeText("nativeEngineState")
                writer.writeText("alive")
                writer.writeBoolean(payload.alive)
                writer.writeText("activeEngineCount")
                writer.writeLong(payload.activeEngineCount.toLong())
            }

            is DiagnosticPayload.NativeEngineEvent -> {
                writer.writeMapHeader(6)
                writer.writeText("type")
                writer.writeText("nativeEngineEvent")
                writer.writeText("nativeEventId")
                writer.writeLong(payload.nativeEventId)
                writer.writeText("engineElapsedNanos")
                writer.writeLong(payload.engineElapsedNanos)
                writer.writeText("rawCode")
                writer.writeLong(payload.rawCode.toLong())
                writer.writeText("arg0")
                writer.writeLong(payload.arg0)
                writer.writeText("arg1")
                writer.writeLong(payload.arg1)
            }

            is DiagnosticPayload.RuntimeSnapshotState -> {
                writer.writeMapHeader(5)
                writer.writeText("type")
                writer.writeText("runtimeSnapshotState")
                writer.writeText("sequence")
                writer.writeLong(payload.sequence)
                writer.writeText("sessionId")
                writer.writeLong(payload.sessionId)
                writer.writeText("sourceKind")
                writer.writeLong(payload.sourceKind.toLong())
                writer.writeText("channelCount")
                writer.writeLong(payload.channelCount.toLong())
            }

            is DiagnosticPayload.LifecycleState -> {
                writer.writeMapHeader(5)
                writer.writeText("type")
                writer.writeText("lifecycleState")
                writer.writeText("ownerId")
                writer.writeText(payload.ownerId)
                writer.writeText("ownerKind")
                writer.writeText(payload.ownerKind)
                writer.writeText("state")
                writer.writeText(payload.state)
                writer.writeText("generation")
                writer.writeLong(payload.generation)
            }

            is DiagnosticPayload.CaptureState -> {
                writer.writeMapHeader(4)
                writer.writeText("type")
                writer.writeText("captureState")
                writer.writeText("captureId")
                writer.writeLong(payload.captureId)
                writer.writeText("mode")
                writer.writeText(payload.mode.name)
                writer.writeText("markerCount")
                writer.writeLong(payload.markerCount.toLong())
            }

            is DiagnosticPayload.InvariantViolation -> {
                writer.writeMapHeader(3)
                writer.writeText("type")
                writer.writeText("invariantViolation")
                writer.writeText("invariantId")
                writer.writeText(payload.invariantId)
                writer.writeText("detail")
                writer.writeText(payload.detail)
            }
        }
    }

    private fun writeMetric(writer: CborWriter, metric: DiagnosticMetricSummary) {
        writer.writeMapHeader(12)
        writer.writeText("id")
        writer.writeText(metric.metric.stableId)
        writer.writeText("unit")
        writer.writeText(metric.metric.unit)
        writer.writeText("kind")
        writer.writeText(metric.metric.kind.name)
        writer.writeText("sampleCount")
        writer.writeLong(metric.sampleCount)
        writer.writeText("retainedSampleCount")
        writer.writeLong(metric.retainedSampleCount.toLong())
        writer.writeText("first")
        writer.writeDouble(metric.first)
        writer.writeText("current")
        writer.writeDouble(metric.current)
        writer.writeText("minimum")
        writer.writeDouble(metric.minimum)
        writer.writeText("maximum")
        writer.writeDouble(metric.maximum)
        writer.writeText("p50")
        writer.writeDouble(metric.p50)
        writer.writeText("p95")
        writer.writeDouble(metric.p95)
        writer.writeText("p99")
        writer.writeDouble(metric.p99)
    }

    private fun writeLifecycleOwner(writer: CborWriter, owner: DiagnosticLifecycleOwner) {
        writer.writeMapHeader(7)
        writer.writeText("ownerId")
        writer.writeText(owner.ownerId)
        writer.writeText("ownerKind")
        writer.writeText(owner.ownerKind)
        writer.writeText("generation")
        writer.writeLong(owner.generation)
        writer.writeText("createdAtNanos")
        writer.writeLong(owner.createdAtNanos)
        writer.writeText("currentState")
        writer.writeText(owner.currentState)
        writer.writeText("destroyedAtNanos")
        writer.writeNullableLong(owner.destroyedAtNanos)
        writer.writeText("lateEventCount")
        writer.writeLong(owner.lateEventCount)
    }

    private fun writeMarker(writer: CborWriter, marker: DiagnosticMarker) {
        writer.writeMapHeader(4)
        writer.writeText("markerId")
        writer.writeLong(marker.markerId)
        writer.writeText("monotonicNanos")
        writer.writeLong(marker.monotonicNanos)
        writer.writeText("wallClockMillis")
        writer.writeLong(marker.wallClockMillis)
        writer.writeText("label")
        writer.writeNullableText(marker.label)
    }

    private fun writeWorstFrame(writer: CborWriter, frame: DiagnosticWorstFrame) {
        writer.writeMapHeader(18)
        writer.writeText("frameNumber")
        writer.writeLong(frame.frameNumber)
        writer.writeText("monotonicNanos")
        writer.writeLong(frame.monotonicNanos)
        writer.writeText("wallClockMillis")
        writer.writeLong(frame.wallClockMillis)
        writer.writeText("destinationId")
        writer.writeText(frame.destinationId)
        writer.writeText("contextAgeMs")
        writer.writeDouble(frame.contextAgeMs)
        writer.writeText("firstDrawFrame")
        writer.writeBoolean(frame.firstDrawFrame)
        writer.writeText("totalMs")
        writer.writeDouble(frame.totalMs)
        writer.writeText("budgetMs")
        writer.writeDouble(frame.budgetMs)
        writer.writeText("overrunMs")
        writer.writeDouble(frame.overrunMs)
        writer.writeText("vsyncDelayMs")
        writer.writeNullableDouble(frame.vsyncDelayMs)
        writer.writeText("unknownDelayMs")
        writer.writeNullableDouble(frame.unknownDelayMs)
        writer.writeText("animationMs")
        writer.writeNullableDouble(frame.animationMs)
        writer.writeText("layoutMeasureMs")
        writer.writeNullableDouble(frame.layoutMeasureMs)
        writer.writeText("drawMs")
        writer.writeNullableDouble(frame.drawMs)
        writer.writeText("syncMs")
        writer.writeNullableDouble(frame.syncMs)
        writer.writeText("commandIssueMs")
        writer.writeNullableDouble(frame.commandIssueMs)
        writer.writeText("swapBuffersMs")
        writer.writeNullableDouble(frame.swapBuffersMs)
        writer.writeText("gpuMs")
        writer.writeNullableDouble(frame.gpuMs)
    }
}

private class CborWriter {
    private val bytes = ByteArrayOutputStream()
    private val data = DataOutputStream(bytes)

    fun writeMapHeader(size: Int) = writeTypeAndValue(5, size.toLong())

    fun writeArrayHeader(size: Int) = writeTypeAndValue(4, size.toLong())

    fun writeText(value: String) {
        val utf8 = value.toByteArray(Charsets.UTF_8)
        writeTypeAndValue(3, utf8.size.toLong())
        data.write(utf8)
    }

    fun writeNullableText(value: String?) {
        if (value == null) {
            writeNull()
        } else {
            writeText(value)
        }
    }

    fun writeLong(value: Long) {
        if (value >= 0) {
            writeTypeAndValue(0, value)
        } else {
            writeTypeAndValue(1, -1L - value)
        }
    }

    fun writeNullableLong(value: Long?) {
        if (value == null) {
            writeNull()
        } else {
            writeLong(value)
        }
    }

    fun writeBoolean(value: Boolean) {
        data.writeByte(if (value) 0xF5 else 0xF4)
    }

    fun writeDouble(value: Double) {
        data.writeByte(0xFB)
        data.writeLong(java.lang.Double.doubleToRawLongBits(value))
    }

    fun writeNullableDouble(value: Double?) {
        if (value == null) {
            writeNull()
        } else {
            writeDouble(value)
        }
    }

    fun toByteArray(): ByteArray = bytes.toByteArray()

    private fun writeNull() {
        data.writeByte(0xF6)
    }

    private fun writeTypeAndValue(majorType: Int, value: Long) {
        require(value >= 0) { "CBOR type value must be non-negative." }
        when {
            value < 24 -> data.writeByte((majorType shl 5) or value.toInt())
            value <= 0xFF -> {
                data.writeByte((majorType shl 5) or 24)
                data.writeByte(value.toInt())
            }
            value <= 0xFFFF -> {
                data.writeByte((majorType shl 5) or 25)
                data.writeShort(value.toInt())
            }
            value <= 0xFFFF_FFFFL -> {
                data.writeByte((majorType shl 5) or 26)
                data.writeInt(value.toInt())
            }
            else -> {
                data.writeByte((majorType shl 5) or 27)
                data.writeLong(value)
            }
        }
    }
}
