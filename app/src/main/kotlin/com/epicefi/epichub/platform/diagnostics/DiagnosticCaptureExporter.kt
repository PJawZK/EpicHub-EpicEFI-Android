package com.epicefi.epichub.platform.diagnostics

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.FileProvider
import com.epicefi.epichub.BuildConfig
import com.epicefi.epichub.diagnostics.DiagnosticCapture
import com.epicefi.epichub.diagnostics.DiagnosticCaptureCborEncoder
import com.epicefi.epichub.diagnostics.DiagnosticExportMetadata
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object DiagnosticCaptureExporter {
    fun exportToCache(
        context: Context,
        capture: DiagnosticCapture,
    ): File {
        val exportDirectory = File(context.cacheDir, "diagnostic-exports").apply { mkdirs() }
        val outputFile = File(exportDirectory, "EpicHub-Diagnostics-${capture.captureId}.zip")
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

        ZipOutputStream(FileOutputStream(outputFile)).use { zip ->
            zip.putNextEntry(ZipEntry("capture.cbor"))
            zip.write(cbor)
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("report.txt"))
            zip.write(report)
            zip.closeEntry()
        }
        return outputFile
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
