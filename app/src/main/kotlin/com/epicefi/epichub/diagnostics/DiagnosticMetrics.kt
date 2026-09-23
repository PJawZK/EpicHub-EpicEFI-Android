package com.epicefi.epichub.diagnostics

import java.util.EnumMap
import kotlin.math.ceil

class DiagnosticMetrics(
    private val distributionCapacity: Int = 256,
) {
    init {
        require(distributionCapacity > 0) { "Distribution capacity must be positive." }
    }

    private class Series(
        private val capacity: Int,
    ) {
        private val samples = DoubleArray(capacity)
        private var writeIndex = 0
        private var storedCount = 0
        var totalCount: Long = 0
            private set
        var current: Double = 0.0
            private set
        var minimum: Double = Double.POSITIVE_INFINITY
            private set
        var maximum: Double = Double.NEGATIVE_INFINITY
            private set

        fun observe(value: Double) {
            current = value
            minimum = minOf(minimum, value)
            maximum = maxOf(maximum, value)
            totalCount += 1
            samples[writeIndex] = value
            writeIndex = (writeIndex + 1) % capacity
            storedCount = minOf(storedCount + 1, capacity)
        }

        fun increment(delta: Double) {
            observe(current + delta)
        }

        fun summary(metric: DiagnosticMetricId): DiagnosticMetricSummary {
            if (storedCount == 0) {
                return DiagnosticMetricSummary(
                    metric = metric,
                    sampleCount = 0,
                    current = 0.0,
                    minimum = 0.0,
                    maximum = 0.0,
                    p50 = 0.0,
                    p95 = 0.0,
                    p99 = 0.0,
                )
            }

            val ordered = DoubleArray(storedCount)
            for (index in 0 until storedCount) {
                ordered[index] = samples[index]
            }
            ordered.sort()

            fun percentile(percent: Double): Double {
                val rank = ceil((percent / 100.0) * ordered.size).toInt().coerceIn(1, ordered.size)
                return ordered[rank - 1]
            }

            return DiagnosticMetricSummary(
                metric = metric,
                sampleCount = totalCount,
                current = current,
                minimum = minimum,
                maximum = maximum,
                p50 = percentile(50.0),
                p95 = percentile(95.0),
                p99 = percentile(99.0),
            )
        }
    }

    private val series = EnumMap<DiagnosticMetricId, Series>(DiagnosticMetricId::class.java)

    @Synchronized
    fun observe(metric: DiagnosticMetricId, value: Double) {
        series.getOrPut(metric) { Series(distributionCapacity) }.observe(value)
    }

    @Synchronized
    fun increment(metric: DiagnosticMetricId, delta: Double = 1.0) {
        series.getOrPut(metric) { Series(distributionCapacity) }.increment(delta)
    }

    @Synchronized
    fun snapshot(): List<DiagnosticMetricSummary> =
        DiagnosticMetricId.entries.map { metric ->
            series[metric]?.summary(metric) ?: Series(distributionCapacity).summary(metric)
        }
}
