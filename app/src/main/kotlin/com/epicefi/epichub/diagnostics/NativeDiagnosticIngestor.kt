package com.epicefi.epichub.diagnostics

import com.epicefi.epichub.engine.NativeDiagnosticBatch
import com.epicefi.epichub.engine.NativeDiagnosticCode
import com.epicefi.epichub.engine.NativeDiagnosticSeverity

class NativeDiagnosticIngestor {
    private var lastDroppedTotal = 0L

    fun ingest(
        batch: NativeDiagnosticBatch,
        diagnostics: DiagnosticHub,
        sessionId: Long?,
    ) {
        if (batch.droppedTotal > lastDroppedTotal) {
            diagnostics.invariantViolation(
                invariantId = "native.diagnostics.events_dropped",
                detail = "Native diagnostic ring dropped ${batch.droppedTotal - lastDroppedTotal} event(s); total=${batch.droppedTotal}.",
                domain = DiagnosticDomain.NATIVE_ENGINE,
                correlationId = sessionId?.let { "native-session-$it" },
            )
        }
        lastDroppedTotal = maxOf(lastDroppedTotal, batch.droppedTotal)

        batch.events.forEach { event ->
            val code = when (event.code) {
                NativeDiagnosticCode.ENGINE_CREATED -> DiagnosticEventCode.NATIVE_ENGINE_CREATED
                NativeDiagnosticCode.RUNTIME_SNAPSHOT_PUBLISHED -> DiagnosticEventCode.RUNTIME_SNAPSHOT_OBSERVED
                NativeDiagnosticCode.UNKNOWN -> DiagnosticEventCode.NATIVE_ENGINE_EVENT
            }
            val domain = when (event.code) {
                NativeDiagnosticCode.RUNTIME_SNAPSHOT_PUBLISHED -> DiagnosticDomain.RUNTIME
                else -> DiagnosticDomain.NATIVE_ENGINE
            }
            diagnostics.record(
                severity = event.severity.toDiagnosticSeverity(),
                domain = domain,
                code = code,
                correlationId = sessionId?.let { "native-session-$it" },
                sessionId = sessionId,
                payload = DiagnosticPayload.NativeEngineEvent(
                    nativeEventId = event.eventId,
                    engineElapsedNanos = event.engineElapsedNanos,
                    rawCode = event.rawCode,
                    arg0 = event.arg0,
                    arg1 = event.arg1,
                ),
            )
        }
    }

    private fun NativeDiagnosticSeverity.toDiagnosticSeverity(): DiagnosticSeverity = when (this) {
        NativeDiagnosticSeverity.TRACE -> DiagnosticSeverity.TRACE
        NativeDiagnosticSeverity.INFO -> DiagnosticSeverity.INFO
        NativeDiagnosticSeverity.WARNING -> DiagnosticSeverity.WARNING
        NativeDiagnosticSeverity.ERROR -> DiagnosticSeverity.ERROR
        NativeDiagnosticSeverity.CRITICAL -> DiagnosticSeverity.CRITICAL
        NativeDiagnosticSeverity.UNKNOWN -> DiagnosticSeverity.WARNING
    }
}
