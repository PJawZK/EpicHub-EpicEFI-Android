package com.epicefi.epichub.ui.tuner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Tuner3dGeometryTest {
    private val values = doubleArrayOf(
        42.0, 46.0, 50.0,
        55.0, 60.0, 66.0,
    )

    @Test
    fun projectionProducesOneVertexPerCell() {
        val cells = Tuner3dGeometry.projectCells(
            rows = 2,
            cols = 3,
            values = values,
            width = 600.0,
            height = 300.0,
        )

        assertEquals(6, cells.size)
        assertEquals((0..1).flatMap { row -> (0..2).map { col -> row to col } }, cells.map { it.row to it.col })
    }

    @Test
    fun exactVertexHitReturnsItsSemanticCell() {
        val cells = projectedCells()
        val target = cells.first { it.row == 1 && it.col == 2 }

        assertEquals(
            TunerCellPosition(row = 1, col = 2),
            Tuner3dGeometry.hitTest(cells, target.x, target.y, maxDistance = 20.0),
        )
    }

    @Test
    fun nearbyHitReturnsNearestVertexWithinRadius() {
        val cells = projectedCells()
        val target = cells.first { it.row == 0 && it.col == 1 }

        assertEquals(
            TunerCellPosition(row = 0, col = 1),
            Tuner3dGeometry.hitTest(cells, target.x + 7.0, target.y - 4.0, maxDistance = 12.0),
        )
    }

    @Test
    fun outsideHitRadiusReturnsNoCell() {
        val cells = projectedCells()
        assertNull(Tuner3dGeometry.hitTest(cells, x = 599.0, y = 299.0, maxDistance = 8.0))
    }

    @Test
    fun equalDistanceTieUsesRowThenColumnDeterministically() {
        val cells = listOf(
            Tuner3dProjectedCell(row = 1, col = 0, x = 20.0, y = 10.0),
            Tuner3dProjectedCell(row = 0, col = 2, x = 0.0, y = 10.0),
        )

        assertEquals(
            TunerCellPosition(row = 0, col = 2),
            Tuner3dGeometry.hitTest(cells, x = 10.0, y = 10.0, maxDistance = 10.0),
        )
    }

    @Test
    fun nonUniformValueHeightsPreserveRowColumnIdentity() {
        val nonUniform = doubleArrayOf(1.0, 50.0, 2.0, 120.0, 3.0, 180.0)
        val cells = Tuner3dGeometry.projectCells(
            rows = 2,
            cols = 3,
            values = nonUniform,
            width = 600.0,
            height = 300.0,
        )

        val semantic = cells.associateBy { it.row to it.col }
        assertEquals(6, semantic.size)
        assertTrue(semantic.keys.contains(0 to 0))
        assertTrue(semantic.keys.contains(0 to 1))
        assertTrue(semantic.keys.contains(0 to 2))
        assertTrue(semantic.keys.contains(1 to 0))
        assertTrue(semantic.keys.contains(1 to 1))
        assertTrue(semantic.keys.contains(1 to 2))
    }

    @Test
    fun hitResultRoutesThroughTheSameSelectionReducerAs2d() {
        val cells = projectedCells()
        val target = cells.first { it.row == 1 && it.col == 1 }
        val hit = Tuner3dGeometry.hitTest(
            cells = cells,
            x = target.x,
            y = target.y,
            maxDistance = 20.0,
        ) ?: error("Expected exact 3D vertex hit")

        val routed = TunerCommandRouter.route(
            command = TunerCommand.SelectCell(hit.row, hit.col),
            state = TunerInteractionState(),
            rows = 2,
            cols = 3,
        )

        assertTrue(routed.consumed)
        assertEquals(
            TunerRectangleSelection(
                anchor = TunerCellPosition(row = 1, col = 1),
                focus = TunerCellPosition(row = 1, col = 1),
            ),
            routed.state.selection,
        )
        assertEquals(TunerCommandEffect.CancelPreview, routed.effect)
    }

    @Test
    fun invalidGeometryCannotProduceHitAuthority() {
        assertTrue(
            Tuner3dGeometry.projectCells(
                rows = 2,
                cols = 3,
                values = doubleArrayOf(1.0, 2.0),
                width = 600.0,
                height = 300.0,
            ).isEmpty(),
        )
        assertNull(Tuner3dGeometry.hitTest(emptyList(), 10.0, 10.0, maxDistance = 20.0))
        assertNull(Tuner3dGeometry.hitTest(projectedCells(), Double.NaN, 10.0, maxDistance = 20.0))
    }

    private fun projectedCells(): List<Tuner3dProjectedCell> = Tuner3dGeometry.projectCells(
        rows = 2,
        cols = 3,
        values = values,
        width = 600.0,
        height = 300.0,
    )
}
