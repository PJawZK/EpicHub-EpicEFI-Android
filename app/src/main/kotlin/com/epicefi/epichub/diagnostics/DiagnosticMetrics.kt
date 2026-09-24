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
        var first: Double = 0.0
            private set
        var current: Double = 0.0
            private set
        var minimum: Double = Double.POSITIVE_INFINITY
            private set
        var maximum: Double = Double.NEGATIVE_INFINITY
            private set

        fun observe(value: Double) {
            if (totalCount == 0L) {
                first = value
            }
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

        fun copy(metric: DiagnosticMetricId): SeriesCopy = SeriesCopy(
            metric = metric,
            sampleCount = totalCount,
            retainedSamples = samples.copyOf(storedCount),
            first = first,
            current = current,
            minimum = if (storedCount == 0) 0.0 else minimum,
            maximum = if (storedCount == 0) 0.0 else maximum,
        )
    }

    private data class SeriesCopy(
        val metric: DiagnosticMetricId,
        val sampleCount: Long,
        val retainedSamples: DoubleArray,
        val first: Double,
        val current: Double,
        val minimum: Double,
        val maximum: Double,
    ) {
        fun summary(): DiagnosticMetricSummary {
            if (retainedSamples.isEmpty()) {
                return DiagnosticMetricSummary(
                    metric = metric,
                    sampleCount = 0,
                    retainedSampleCount = 0,
                    first = 0.0,
                    current = 0.0,
                    minimum = 0.0,
                    maximum = 0.0,
                    p50 = 0.0,
                    p95 = 0.0,
                    p99 = 0.0,
                )
            }

            retainedSamples.sort()

            fun percentile(percent: Double): Double {
                val rank = ceil((percent / 100.0) * retainedSamples.size)
                    .toInt()
                    .coerceIn(1, retainedSamples.size)
                return retainedSamples[rank - 1]
            }

            return DiagnosticMetricSummary(
                metric = metric,
                sampleCount = sampleCount,
                retainedSampleCount = retainedSamples.size,
                first = first,
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

    fun snapshot(): List<DiagnosticMetricSummary> {
        val copies = synchronized(this) {
            DiagnosticMetricId.entries.map { metric ->
                series[metric]?.copy(metric) ?: SeriesCopy(
                    metric = metric,
                    sampleCount = 0,
                    retainedSamples = doubleArrayOf(),
                    first = 0.0,
                    current = 0.0,
                    minimum = 0.0,
                    maximum = 0.0,
                )
            }
        }
        return copies.map(SeriesCopy::summary)
    }
}
