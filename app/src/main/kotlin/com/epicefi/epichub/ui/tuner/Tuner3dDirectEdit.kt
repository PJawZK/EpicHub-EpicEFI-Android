package com.epicefi.epichub.ui.tuner

import kotlin.math.roundToInt

private const val MAX_DIRECT_PULL_STEPS = 64

object Tuner3dDirectEdit {
    fun pullDeltaForDrag(
        startY: Double,
        currentY: Double,
        pixelsPerStep: Double,
        editIncrement: Double,
    ): Double? {
        if (!startY.isFinite() || !currentY.isFinite() || !pixelsPerStep.isFinite() || !editIncrement.isFinite()) {
            return null
        }
        if (pixelsPerStep <= 0.0 || editIncrement <= 0.0) return null

        val steps = ((startY - currentY) / pixelsPerStep)
            .roundToInt()
            .coerceIn(-MAX_DIRECT_PULL_STEPS, MAX_DIRECT_PULL_STEPS)
        if (steps == 0) return null
        return steps * editIncrement
    }
}
