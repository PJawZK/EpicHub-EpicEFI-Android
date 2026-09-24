package com.epicefi.epichub.platform.diagnostics

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.hardware.display.DisplayManager
import android.net.Uri
import android.os.Build
import android.os.Process
import android.view.Display
import androidx.core.content.FileProvider
import com.epicefi.epichub.BuildConfig
import com.epicefi.epichub.diagnostics.DiagnosticCapture
import com.epicefi.epichub.diagnostics.DiagnosticCaptureArchive
import com.epicefi.epichub.diagnostics.DiagnosticCaptureCborEncoder
import com.epicefi.epichub.diagnostics.DiagnosticExportMetadata
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

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

    fun createShareIntent(
        context: Context,
        capture: DiagnosticCapture,
    ): Intent {
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
        return Intent.createChooser(send, "Share EpicHub diagnostic capture").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun writeArchive(
        context: Context,
        capture: DiagnosticCapture,
        output: java.io.OutputStream,
    ) {
        val metadata = buildMetadata(context)
        val cbor = DiagnosticCaptureCborEncoder.encode(capture, metadata)
        val report = buildReport(capture, metadata).toByteArray(Charsets.UTF_8)
        DiagnosticCaptureArchive.write(output, cbor, report)
    }

    private fun buildMetadata(context: Context): DiagnosticExportMetadata {
        val displayMetrics = context.resources.displayMetrics
        val configuration = context.resources.configuration
        val display = context.getSystemService(DisplayManager::class.java)
            ?.getDisplay(Display.DEFAULT_DISPLAY)
        val processIs64Bit = Process.is64Bit()
        val activeAbi = if (processIs64Bit) {
            Build.SUPPORTED_64_BIT_ABIS.firstOrNull()
        } else {
            Build.SUPPORTED_32_BIT_ABIS.firstOrNull()
        } ?: Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"

        val orientation = when (configuration.orientation) {
            Configuration.ORIENTATION_LANDSCAPE -> "landscape"
            Configuration.ORIENTATION_PORTRAIT -> "portrait"
            else -> "undefined"
        }

        return DiagnosticExportMetadata(
            appVersion = BuildConfig.VERSION_NAME,
            buildType = BuildConfig.BUILD_TYPE,
            gitSha = BuildConfig.GIT_SHA,
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            androidApi = Build.VERSION.SDK_INT,
            supportedAbis = Build.SUPPORTED_ABIS.toList(),
            processAbi = activeAbi,
            processIs64Bit = processIs64Bit,
            displayWidthPx = displayMetrics.widthPixels,
            displayHeightPx = displayMetrics.heightPixels,
            densityDpi = displayMetrics.densityDpi,
            refreshRateHz = display?.refreshRate?.toDouble() ?: 0.0,
            orientation = orientation,
        )
    }

    private fun buildReport(
        capture: DiagnosticCapture,
        metadata: DiagnosticExportMetadata,
    ): String = buildString {
        val durationSeconds = (capture.stoppedAtNanos - capture.startedAtNanos) / 1_000_000_000.0
        appendLine("EpicHub diagnostic capture")
        appendLine("schema: ${DiagnosticCaptureCborEncoder.SCHEMA_VERSION}")
        appendLine("capture: ${capture.captureId}")
        appendLine("mode: ${capture.mode}")
        appendLine("durationSeconds: ${durationSeconds.formatMetric()}")
        appendLine("app: ${metadata.appVersion} (${metadata.buildType})")
        appendLine("gitSha: ${metadata.gitSha}")
        appendLine("device: ${metadata.manufacturer} ${metadata.model}")
        appendLine("androidApi: ${metadata.androidApi}")
        appendLine("supportedAbis: ${metadata.supportedAbis.joinToString()}")
        appendLine("processAbi: ${metadata.processAbi}")
        appendLine("processIs64Bit: ${metadata.processIs64Bit}")
        appendLine(
            "display: ${metadata.displayWidthPx}x${metadata.displayHeightPx} " +
                "densityDpi=${metadata.densityDpi} refreshHz=${metadata.refreshRateHz.formatMetric()} " +
                "orientation=${metadata.orientation}",
        )
        appendLine("events: ${capture.events.size}")
        appendLine("metrics: ${capture.metrics.size}")
        appendLine("lifecycleOwners: ${capture.lifecycleOwners.size}")
        appendLine("markers: ${capture.markers.size}")
        appendLine("worstFrames: ${capture.worstFrames.size}")
        capture.note?.let { appendLine("note: $it") }
        appendLine()
        appendLine("Metrics")
        capture.metrics
            .filter { it.sampleCount > 0 }
            .forEach { metric ->
                append(metric.metric.stableId)
                append(" [")
                append(metric.metric.unit)
                append("] first=")
                append(metric.first.formatMetric())
                append(" current=")
                append(metric.current.formatMetric())
                append(" delta=")
                append((metric.current - metric.first).formatMetric())
                append(" min=")
                append(metric.minimum.formatMetric())
                append(" max=")
                append(metric.maximum.formatMetric())
                append(" p50=")
                append(metric.p50.formatMetric())
                append(" p95=")
                append(metric.p95.formatMetric())
                append(" p99=")
                append(metric.p99.formatMetric())
                append(" samples=")
                append(metric.sampleCount)
                append(" retained=")
                appendLine(metric.retainedSampleCount)
            }
        appendLine()
        appendLine("Worst frames")
        if (capture.worstFrames.isEmpty()) {
            appendLine("none")
        } else {
            capture.worstFrames.forEach { frame ->
                append("frame=")
                append(frame.frameNumber)
                append(" route=")
                append(frame.destinationId)
                append(" totalMs=")
                append(frame.totalMs.formatMetric())
                append(" budgetMs=")
                append(frame.budgetMs.formatMetric())
                append(" overrunMs=")
                append(frame.overrunMs.formatMetric())
                frame.animationMs?.let { append(" animationMs=${it.formatMetric()}") }
                frame.layoutMeasureMs?.let { append(" layoutMs=${it.formatMetric()}") }
                frame.drawMs?.let { append(" drawMs=${it.formatMetric()}") }
                frame.commandIssueMs?.let { append(" commandMs=${it.formatMetric()}") }
                frame.gpuMs?.let { append(" gpuMs=${it.formatMetric()}") }
                frame.vsyncDelayMs?.let { append(" vsyncDelayMs=${it.formatMetric()}") }
                appendLine()
            }
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

    private fun Double.formatMetric(): String = String.format(Locale.US, "%.3f", this)
}
