package com.epicefi.epichub.ui.tuner

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

private const val DISTANCE_EPSILON = 1e-9
private const val VIEWPORT_WIDTH_FRACTION = 0.78
private const val VIEWPORT_HEIGHT_FRACTION = 0.68
private const val VIEWPORT_CENTER_Y_FRACTION = 0.52
private const val GRID_X_SCALE = 1.2
private const val GRID_DEPTH_SCALE = 1.0
private const val VALUE_Z_SCALE = 0.95

enum class Tuner3dInteractionMode {
    INSPECT,
    SELECT_EDIT,
}

data class Tuner3dProjectedCell(
    val row: Int,
    val col: Int,
    val x: Double,
    val y: Double,
)

object Tuner3dGeometry {
    fun projectCells(
        rows: Int,
        cols: Int,
        values: DoubleArray,
        width: Double,
        height: Double,
        camera: Tuner3dCameraState = Tuner3dCameraState.DEFAULT,
    ): List<Tuner3dProjectedCell> {
        if (rows <= 0 || cols <= 0 || width <= 0.0 || height <= 0.0) return emptyList()
        if (!width.isFinite() || !height.isFinite() || values.size != rows * cols) return emptyList()
        if (values.any { !it.isFinite() }) return emptyList()

        val normalizedCamera = camera.normalized()
        val minValue = values.minOrNull() ?: return emptyList()
        val maxValue = values.maxOrNull() ?: minValue
        val valueSpan = max(maxValue - minValue, DISTANCE_EPSILON)
        val yaw = Math.toRadians(normalizedCamera.yawDegrees)
        val pitch = Math.toRadians(normalizedCamera.pitchDegrees)
        val cosYaw = cos(yaw)
        val sinYaw = sin(yaw)
        val cosPitch = cos(pitch)
        val sinPitch = sin(pitch)

        data class RawPoint(
            val row: Int,
            val col: Int,
            val x: Double,
            val y: Double,
        )

        val raw = buildList(rows * cols) {
            for (row in 0 until rows) {
                for (col in 0 until cols) {
                    val index = row * cols + col
                    val gridX = centeredCoordinate(col, cols) * GRID_X_SCALE
                    val depth = centeredCoordinate(row, rows) * GRID_DEPTH_SCALE
                    val z = ((values[index] - minValue) / valueSpan - 0.5) * VALUE_Z_SCALE

                    val yawX = gridX * cosYaw - depth * sinYaw
                    val yawDepth = gridX * sinYaw + depth * cosYaw
                    val projectedVertical = z * cosPitch + yawDepth * sinPitch
                    add(RawPoint(row, col, yawX, -projectedVertical))
                }
            }
        }

        val minX = raw.minOf { it.x }
        val maxX = raw.maxOf { it.x }
        val minY = raw.minOf { it.y }
        val maxY = raw.maxOf { it.y }
        val spanX = max(maxX - minX, DISTANCE_EPSILON)
        val spanY = max(maxY - minY, DISTANCE_EPSILON)
        val fitScale = min(
            width * VIEWPORT_WIDTH_FRACTION / spanX,
            height * VIEWPORT_HEIGHT_FRACTION / spanY,
        ) * normalizedCamera.zoom
        val rawCenterX = (minX + maxX) * 0.5
        val rawCenterY = (minY + maxY) * 0.5
        val screenCenterX = width * 0.5
        val screenCenterY = height * VIEWPORT_CENTER_Y_FRACTION

        return raw.map { point ->
            Tuner3dProjectedCell(
                row = point.row,
                col = point.col,
                x = screenCenterX + (point.x - rawCenterX) * fitScale,
                y = screenCenterY + (point.y - rawCenterY) * fitScale,
            )
        }
    }

    fun hitTest(
        cells: List<Tuner3dProjectedCell>,
        x: Double,
        y: Double,
        maxDistance: Double,
    ): TunerCellPosition? {
        if (!x.isFinite() || !y.isFinite() || !maxDistance.isFinite() || maxDistance < 0.0) {
            return null
        }

        var best: Tuner3dProjectedCell? = null
        var bestDistance = Double.POSITIVE_INFINITY
        for (cell in cells) {
            val distance = hypot(x - cell.x, y - cell.y)
            if (distance > maxDistance + DISTANCE_EPSILON) continue

            val currentBest = best
            val isCloser = distance < bestDistance - DISTANCE_EPSILON
            val isDeterministicTie =
                currentBest != null &&
                    abs(distance - bestDistance) <= DISTANCE_EPSILON &&
                    (cell.row < currentBest.row ||
                        (cell.row == currentBest.row && cell.col < currentBest.col))
            if (currentBest == null || isCloser || isDeterministicTie) {
                best = cell
                bestDistance = distance
            }
        }

        return best?.let { TunerCellPosition(row = it.row, col = it.col) }
    }

    private fun centeredCoordinate(index: Int, count: Int): Double =
        if (count <= 1) 0.0 else index.toDouble() / (count - 1).toDouble() - 0.5
}
