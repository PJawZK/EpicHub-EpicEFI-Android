package com.epicefi.epichub.platform.diagnostics

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import com.epicefi.epichub.BuildConfig
import com.epicefi.epichub.diagnostics.DiagnosticCapture
import com.epicefi.epichub.diagnostics.DiagnosticCaptureArchive
import com.epicefi.epichub.diagnostics.DiagnosticCaptureCborEncoder
import com.epicefi.epichub.diagnostics.DiagnosticExportMetadata
import java.io.File
import java.io.FileOutputStream

object DiagnosticCaptureExporter {
    fun suggestedFileName(capture: DiagnosticCapture): String =
        "EpicHub-Diagnostics-${capture.captureId}.zip"

    fun exportToCache(
        context: Context,
        capture: DiagnosticCapture,
    ): File {
        val exportDirectory = File(context.cacheDir, "diagnostic-exports").apply { mkdirs() }
        val outputFile = File(exportDirectory, suggestedFileName(capture))
        FileOutputStream(outputFile).use { output ->
            writeArchive(context, capture, output)
        }
        return outputFile
    }

    fun save(
        context: Context,
        capture: DiagnosticCapture,
        destination: Uri,
    ) {
        val output = checkNotNull(context.contentResolver.openOutputStream(destination, "w")) {
            "Android did not provide a writable stream for the selected diagnostic destination."
        }
        output.use { stream ->
            writeArchive(context, capture, stream)
        }
    }

    fun share(
        context: Context,
        capture: DiagnosticCapture,
    ) {
        val file = exportToCache(context, capture)
        val uri = FileProvider.getUriForFile(
            context,
            "${BuildConfig.APPLICATION_ID}.diagnostics",
            file,
        )
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "EpicHub diagnostic capture #${capture.captureId}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(send, "Share EpicHub diagnostic capture").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(chooser)
    }

    private fun writeArchive(
        context: Context,
        capture: DiagnosticCapture,
        output: java.io.OutputStream,
    ) {
        val metadata = DiagnosticExportMetadata(
            appVersion = BuildConfig.VERSION_NAME,
            buildType = BuildConfig.BUILD_TYPE,
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            androidApi = Build.VERSION.SDK_INT,
            supportedAbis = Build.SUPPORTED_ABIS.toList(),
        )
        val cbor = DiagnosticCaptureCborEncoder.encode(capture, metadata)
        val report = buildReport(capture, metadata).toByteArray(Charsets.UTF_8)
        DiagnosticCaptureArchive.write(output, cbor, report)
    }

    private fun buildReport(
        capture: DiagnosticCapture,
        metadata: DiagnosticExportMetadata,
    ): String = buildString {
        appendLine("EpicHub diagnostic capture")
        appendLine("schema: ${DiagnosticCaptureCborEncoder.SCHEMA_VERSION}")
        appendLine("capture: ${capture.captureId}")
        appendLine("mode: ${capture.mode}")
        appendLine("app: ${metadata.appVersion} (${metadata.buildType})")
        appendLine("device: ${metadata.manufacturer} ${metadata.model}")
        appendLine("androidApi: ${metadata.androidApi}")
        appendLine("abis: ${metadata.supportedAbis.joinToString()}")
        appendLine("events: ${capture.events.size}")
        appendLine("metrics: ${capture.metrics.size}")
        appendLine("lifecycleOwners: ${capture.lifecycleOwners.size}")
        appendLine("markers: ${capture.markers.size}")
        capture.note?.let { appendLine("note: $it") }
        appendLine()
        appendLine("Metrics")
        capture.metrics
            .filter { it.sampleCount > 0 }
            .forEach { metric ->
                append(metric.metric.stableId)
                append(" [")
                append(metric.metric.unit)
                append("] current=")
                append("%.3f".format(metric.current))
                append(" min=")
                append("%.3f".format(metric.minimum))
                append(" max=")
                append("%.3f".format(metric.maximum))
                append(" p50=")
                append("%.3f".format(metric.p50))
                append(" p95=")
                append("%.3f".format(metric.p95))
                append(" p99=")
                append("%.3f".format(metric.p99))
                append(" samples=")
                appendLine(metric.sampleCount)
            }
        appendLine()
        appendLine("Recent events")
        capture.events.takeLast(80).forEach { event ->
            append(event.eventId)
            append("  ")
            append(event.severity)
            append("  ")
            append(event.domain)
            append("  ")
            append(event.code.stableId)
            event.correlationId?.let {
                append("  correlation=")
                append(it)
            }
            appendLine()
        }
    }
}
