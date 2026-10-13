package com.epicefi.epichub.ui.tuner

import kotlin.math.hypot
import kotlin.math.max

private const val THREE_D_X_WIDTH_FRACTION = 0.68
private const val THREE_D_Y_HEIGHT_FRACTION = 0.34
private const val THREE_D_Z_HEIGHT_FRACTION = 0.34
private const val THREE_D_ORIGIN_X_FRACTION = 0.12
private const val THREE_D_ORIGIN_Y_FRACTION = 0.76
private const val THREE_D_ROW_X_SKEW = 0.22
private const val DISTANCE_EPSILON = 1e-9

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
    ): List<Tuner3dProjectedCell> {
        if (rows <= 0 || cols <= 0 || width <= 0.0 || height <= 0.0) return emptyList()
        if (values.size != rows * cols) return emptyList()

        val minValue = values.minOrNull() ?: return emptyList()
        val maxValue = values.maxOrNull() ?: minValue
        val span = max(maxValue - minValue, DISTANCE_EPSILON)
        val xStep = width * THREE_D_X_WIDTH_FRACTION / max(cols - 1, 1)
        val yStep = height * THREE_D_Y_HEIGHT_FRACTION / max(rows - 1, 1)
        val zScale = height * THREE_D_Z_HEIGHT_FRACTION
        val originX = width * THREE_D_ORIGIN_X_FRACTION
        val originY = height * THREE_D_ORIGIN_Y_FRACTION

        return buildList(rows * cols) {
            for (row in 0 until rows) {
                for (col in 0 until cols) {
                    val index = row * cols + col
                    val normalized = (values[index] - minValue) / span
                    add(
                        Tuner3dProjectedCell(
                            row = row,
                            col = col,
                            x = originX + col * xStep + row * xStep * THREE_D_ROW_X_SKEW,
                            y = originY - row * yStep - normalized * zScale,
                        ),
                    )
                }
            }
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
                    kotlin.math.abs(distance - bestDistance) <= DISTANCE_EPSILON &&
                    (cell.row < currentBest.row ||
                        (cell.row == currentBest.row && cell.col < currentBest.col))
            if (currentBest == null || isCloser || isDeterministicTie) {
                best = cell
                bestDistance = distance
            }
        }

        return best?.let { TunerCellPosition(row = it.row, col = it.col) }
    }
}
