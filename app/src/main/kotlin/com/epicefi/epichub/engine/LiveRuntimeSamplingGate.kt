package com.epicefi.epichub.engine

/**
 * Explicitly requested samples only. This state machine owns no USB port, timer,
 * thread, coroutine, JNI handle, or Dashboard source.
 */
internal data class LiveRuntimeSamplingPolicy(
    val minIntervalMs: Long = 50L,
    val maxFreshAgeMs: Long = 250L,
    val timeoutMs: Int = 100,
) {
    init {
        require(minIntervalMs in 1L..10_000L)
        require(maxFreshAgeMs in minIntervalMs..60_000L)
        require(timeoutMs in 1..60_000)
    }
}

internal enum class LiveRuntimeValidity { Unknown, Fresh, Stale, Invalid }

internal data class LiveRuntimeSampleView(
    val source: String = "real-ecu-v1",
    val validity: LiveRuntimeValidity,
    val epoch: Long?,
    /** Previous measurements may remain visible, but must not be used as live values unless Fresh. */
    val frame: LiveRuntimeFrame?,
    val ageMs: Long?,
    val reason: String?,
) {
    val canUseAsLive: Boolean
        get() = validity == LiveRuntimeValidity.Fresh && epoch == frame?.epoch
}

/**
 * Freshness is evaluated on every read, without relying on a scheduled timer.
 * A connection replacement drops the old frame entirely (no cross-ECU carryover).
 * Transport failure preserves historical numbers, but explicitly marks Invalid.
 */
internal class LiveRuntimeSamplingGate(
    private val policy: LiveRuntimeSamplingPolicy = LiveRuntimeSamplingPolicy(),
) {
    private var activeEpoch: Long? = null
    private var previous: LiveRuntimeFrame? = null
    private var lastAttemptMs: Long? = null
    private var currentState: LiveRuntimeValidity = LiveRuntimeValidity.Unknown
    private var stateReason: String? = null

    val timeoutMs: Int
        get() = policy.timeoutMs

    fun bind(epoch: Long) {
        require(epoch > 0L)
        activeEpoch = epoch
        previous = null
        lastAttemptMs = null
        currentState = LiveRuntimeValidity.Unknown
        stateReason = null
    }

    /** Explicit poll admission. Calling this does not itself communicate with an ECU. */
    fun tryBegin(nowMs: Long): Boolean {
        if (activeEpoch == null || nowMs < 0L) return false
        val previousAttempt = lastAttemptMs
        if (previousAttempt != null) {
            if (nowMs < previousAttempt) {
                fail("Monotonic clock regressed.")
                return false
            }
            if (nowMs - previousAttempt < policy.minIntervalMs) return false
        }
        if (currentState == LiveRuntimeValidity.Invalid) return false
        lastAttemptMs = nowMs
        return true
    }

    /** Called only after the native CRC/status/epoch read cycle returned a Fresh result. */
    fun accept(epoch: Long, frame: LiveRuntimeFrame, observedAtMs: Long): Boolean {
        val last = previous
        if (activeEpoch != epoch || epoch <= 0L ||
            frame.epoch != epoch || frame.sequence <= 0L || frame.profileGeneration <= 0L ||
            frame.receivedAtMs < 0L || observedAtMs < frame.receivedAtMs ||
            observedAtMs - frame.receivedAtMs >= policy.maxFreshAgeMs ||
            frame.readings.isEmpty() || frame.readings.size > 1_024 ||
            frame.readings.any { it.id <= 0L || !it.value.isFinite() } ||
            frame.readings.map { it.id }.toSet().size != frame.readings.size ||
            (last != null && (frame.sequence <= last.sequence ||
                frame.receivedAtMs < last.receivedAtMs ||
                frame.profileGeneration != last.profileGeneration ||
                frame.definitionRevision != last.definitionRevision))
        ) {
            fail("Runtime batch failed epoch, freshness, or ordering validation.")
            return false
        }
        previous = frame.copy(readings = frame.readings.toList())
        currentState = LiveRuntimeValidity.Fresh
        stateReason = null
        return true
    }

    /** A completed transport/validation failure revokes Fresh authority immediately. */
    fun fail(reason: String) {
        activeEpoch = null
        currentState = LiveRuntimeValidity.Invalid
        stateReason = reason
    }

    /** Connection closure or explicit unplug never retains Fresh authority. */
    fun disconnect(reason: String = "Runtime controller disconnected.") {
        activeEpoch = null
        currentState = if (currentState == LiveRuntimeValidity.Invalid) {
            LiveRuntimeValidity.Invalid
        } else if (previous != null) {
            LiveRuntimeValidity.Stale
        } else {
            LiveRuntimeValidity.Unknown
        }
        stateReason = reason
    }

    fun snapshot(nowMs: Long): LiveRuntimeSampleView {
        val frame = previous
        val age = frame?.let {
            if (nowMs >= it.receivedAtMs) nowMs - it.receivedAtMs else null
        }
        val effectiveValidity = when {
            nowMs < 0L || (frame != null && age == null) -> LiveRuntimeValidity.Invalid
            currentState == LiveRuntimeValidity.Invalid -> LiveRuntimeValidity.Invalid
            activeEpoch == null -> if (frame != null) LiveRuntimeValidity.Stale else LiveRuntimeValidity.Unknown
            currentState == LiveRuntimeValidity.Fresh && (age == null || age >= policy.maxFreshAgeMs) ->
                LiveRuntimeValidity.Stale
            else -> currentState
        }
        return LiveRuntimeSampleView(
            validity = effectiveValidity,
            epoch = activeEpoch,
            frame = frame?.copy(readings = frame.readings.toList()),
            ageMs = age,
            reason = when {
                effectiveValidity == LiveRuntimeValidity.Stale && activeEpoch != null ->
                    "Runtime sample exceeded its allowed age."
                effectiveValidity == LiveRuntimeValidity.Invalid && nowMs < 0L ->
                    "Invalid monotonic time."
                effectiveValidity == LiveRuntimeValidity.Invalid && frame != null && age == null ->
                    "Monotonic clock regressed."
                else -> stateReason
            },
        )
    }
}
