package com.epicefi.epichub.ui.tuner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Tuner3dCameraTest {
    @Test
    fun cameraGestureIsBoundedDeterministically() {
        val next = Tuner3dCameraState.DEFAULT.applyGesture(
            panX = 10_000.0,
            panY = -10_000.0,
            zoomFactor = 100.0,
        )

        assertEquals(80.0, next.yawDegrees, 0.0)
        assertEquals(80.0, next.pitchDegrees, 0.0)
        assertEquals(2.5, next.zoom, 0.0)
    }

    @Test
    fun invalidGestureDoesNotMutateCamera() {
        val camera = Tuner3dCameraState.DEFAULT
        assertEquals(camera, camera.applyGesture(Double.NaN, 0.0, 1.0))
        assertEquals(camera, camera.applyGesture(0.0, 0.0, 0.0))
    }

    @Test
    fun normalizedCameraRepairsInvalidPresentationValues() {
        val normalized = Tuner3dCameraState(
            yawDegrees = Double.NaN,
            pitchDegrees = Double.POSITIVE_INFINITY,
            zoom = -4.0,
        ).normalized()

        assertEquals(Tuner3dCameraState.DEFAULT.yawDegrees, normalized.yawDegrees, 0.0)
        assertEquals(Tuner3dCameraState.DEFAULT.pitchDegrees, normalized.pitchDegrees, 0.0)
        assertEquals(0.65, normalized.zoom, 0.0)
    }

    @Test
    fun orbitChangesProjectedGeometryWithoutChangingCellIdentity() {
        val values = doubleArrayOf(
            42.0, 46.0, 50.0,
            55.0, 60.0, 66.0,
        )
        val defaultProjection = Tuner3dGeometry.projectCells(
            rows = 2,
            cols = 3,
            values = values,
            width = 600.0,
            height = 300.0,
        )
        val rotatedProjection = Tuner3dGeometry.projectCells(
            rows = 2,
            cols = 3,
            values = values,
            width = 600.0,
            height = 300.0,
            camera = Tuner3dCameraState.DEFAULT.applyGesture(120.0, -70.0, 1.0),
        )

        assertEquals(defaultProjection.map { it.row to it.col }, rotatedProjection.map { it.row to it.col })
        assertTrue(defaultProjection.zip(rotatedProjection).any { (before, after) ->
            before.x != after.x || before.y != after.y
        })
    }

    @Test
    fun zoomChangesScreenSpacing() {
        val values = doubleArrayOf(1.0, 2.0, 3.0, 4.0)
        val normal = Tuner3dGeometry.projectCells(
            rows = 2,
            cols = 2,
            values = values,
            width = 600.0,
            height = 300.0,
            camera = Tuner3dCameraState.DEFAULT,
        )
        val zoomed = Tuner3dGeometry.projectCells(
            rows = 2,
            cols = 2,
            values = values,
            width = 600.0,
            height = 300.0,
            camera = Tuner3dCameraState.DEFAULT.copy(zoom = 2.0),
        )

        val normalSpan = normal.maxOf { it.x } - normal.minOf { it.x }
        val zoomedSpan = zoomed.maxOf { it.x } - zoomed.minOf { it.x }
        assertTrue(zoomedSpan > normalSpan)
    }

    @Test
    fun transformedHitStillReturnsSemanticCell() {
        val values = doubleArrayOf(
            10.0, 30.0, 50.0,
            70.0, 90.0, 110.0,
        )
        val camera = Tuner3dCameraState.DEFAULT.applyGesture(140.0, 55.0, 1.4)
        val cells = Tuner3dGeometry.projectCells(
            rows = 2,
            cols = 3,
            values = values,
            width = 600.0,
            height = 300.0,
            camera = camera,
        )
        val target = cells.first { it.row == 1 && it.col == 1 }

        assertEquals(
            TunerCellPosition(row = 1, col = 1),
            Tuner3dGeometry.hitTest(cells, target.x, target.y, maxDistance = 12.0),
        )
    }

    @Test
    fun cameraDoesNotCreateOrRemoveProjectedCells() {
        val values = DoubleArray(20) { it.toDouble() }
        val defaultProjection = Tuner3dGeometry.projectCells(4, 5, values, 700.0, 320.0)
        val extremeProjection = Tuner3dGeometry.projectCells(
            4,
            5,
            values,
            700.0,
            320.0,
            Tuner3dCameraState(yawDegrees = 80.0, pitchDegrees = 10.0, zoom = 2.5),
        )

        assertEquals(20, defaultProjection.size)
        assertEquals(20, extremeProjection.size)
        assertEquals(defaultProjection.map { it.row to it.col }, extremeProjection.map { it.row to it.col })
        assertNotEquals(defaultProjection, extremeProjection)
    }
}
