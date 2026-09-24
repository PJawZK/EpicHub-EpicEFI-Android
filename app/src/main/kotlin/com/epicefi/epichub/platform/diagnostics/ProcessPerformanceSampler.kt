package com.epicefi.epichub.platform.diagnostics

import android.os.Debug
import android.os.Process
import android.os.SystemClock
import com.epicefi.epichub.diagnostics.DiagnosticHub
import com.epicefi.epichub.diagnostics.DiagnosticMetricId

class ProcessPerformanceSampler {
    private var lastWallMillis = SystemClock.elapsedRealtime()
    private var lastCpuMillis = Process.getElapsedCpuTime()
    private var sampleIndex: Long = 0

    fun sample(diagnostics: DiagnosticHub) {
        val nowWallMillis = SystemClock.elapsedRealtime()
        val nowCpuMillis = Process.getElapsedCpuTime()
        val wallDelta = nowWallMillis - lastWallMillis
        val cpuDelta = nowCpuMillis - lastCpuMillis

        if (wallDelta > 0L && cpuDelta >= 0L) {
            diagnostics.observeMetric(
                DiagnosticMetricId.PROCESS_CPU_PERCENT,
                cpuDelta.toDouble() / wallDelta.toDouble() * 100.0,
            )
        }

        val runtime = Runtime.getRuntime()
        val javaHeapUsedBytes = runtime.totalMemory() - runtime.freeMemory()
        diagnostics.observeMetric(
            DiagnosticMetricId.PROCESS_JAVA_HEAP_USED_MB,
            javaHeapUsedBytes.toMebibytes(),
        )
        diagnostics.observeMetric(
            DiagnosticMetricId.PROCESS_NATIVE_HEAP_ALLOCATED_MB,
            Debug.getNativeHeapAllocatedSize().toMebibytes(),
        )

        sampleIndex += 1
        if (sampleIndex % PSS_SAMPLE_INTERVAL == 0L) {
            diagnostics.observeMetric(
                DiagnosticMetricId.PROCESS_PSS_MB,
                Debug.getPss().toDouble() / KIBIBYTES_PER_MEBIBYTE,
            )
        }

        lastWallMillis = nowWallMillis
        lastCpuMillis = nowCpuMillis
    }

    private fun Long.toMebibytes(): Double = toDouble() / BYTES_PER_MEBIBYTE

    private companion object {
        const val BYTES_PER_MEBIBYTE = 1024.0 * 1024.0
        const val KIBIBYTES_PER_MEBIBYTE = 1024.0
        const val PSS_SAMPLE_INTERVAL = 5L
    }
}
