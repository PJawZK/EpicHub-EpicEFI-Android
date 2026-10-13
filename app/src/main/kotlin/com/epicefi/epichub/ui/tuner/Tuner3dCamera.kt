package com.epicefi.epichub.ui.tuner

import kotlin.math.max

private const val MIN_YAW_DEGREES = -80.0
private const val MAX_YAW_DEGREES = 80.0
private const val MIN_PITCH_DEGREES = 10.0
private const val MAX_PITCH_DEGREES = 80.0
private const val MIN_ZOOM = 0.65
private const val MAX_ZOOM = 2.5
private const val ORBIT_DEGREES_PER_PIXEL = 0.22

/**
 * Presentation-only camera state for the 3D table surface.
 *
 * This state never enters TuneDraft, JNI, edit history, project persistence or ECU mutation.
 */
data class Tuner3dCameraState(
    val yawDegrees: Double = -32.0,
    val pitchDegrees: Double = 38.0,
    val zoom: Double = 1.0,
) {
    fun applyGesture(
        panX: Double,
        panY: Double,
        zoomFactor: Double,
    ): Tuner3dCameraState {
        if (!panX.isFinite() || !panY.isFinite() || !zoomFactor.isFinite() || zoomFactor <= 0.0) {
            return this
        }
        return copy(
            yawDegrees = (yawDegrees + panX * ORBIT_DEGREES_PER_PIXEL)
                .coerceIn(MIN_YAW_DEGREES, MAX_YAW_DEGREES),
            pitchDegrees = (pitchDegrees - panY * ORBIT_DEGREES_PER_PIXEL)
                .coerceIn(MIN_PITCH_DEGREES, MAX_PITCH_DEGREES),
            zoom = (zoom * zoomFactor).coerceIn(MIN_ZOOM, MAX_ZOOM),
        )
    }

    fun normalized(): Tuner3dCameraState = copy(
        yawDegrees = yawDegrees
            .takeIf(Double::isFinite)
            ?.coerceIn(MIN_YAW_DEGREES, MAX_YAW_DEGREES)
            ?: DEFAULT.yawDegrees,
        pitchDegrees = pitchDegrees
            .takeIf(Double::isFinite)
            ?.coerceIn(MIN_PITCH_DEGREES, MAX_PITCH_DEGREES)
            ?: DEFAULT.pitchDegrees,
        zoom = max(zoom.takeIf(Double::isFinite) ?: DEFAULT.zoom, MIN_ZOOM)
            .coerceAtMost(MAX_ZOOM),
    )

    companion object {
        val DEFAULT = Tuner3dCameraState()
    }
}
