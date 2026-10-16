package com.epicefi.epichub.ui.tuner

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TunerTableSwitchSafetyTest {
    @Test
    fun `table switch clears selection extend mode and active brush`() {
        val previous = TunerInteractionState(
            selection = TunerRectangleSelection(
                anchor = TunerCellPosition(1, 2),
                focus = TunerCellPosition(3, 4),
            ),
            persistentExtendSelection = true,
            activeBrushStroke = TunerBrushStrokeIntent(
                points = listOf(TunerCellPosition(1, 2), TunerCellPosition(1, 3)),
                parameters = TunerBrushStrokeParameters(
                    delta = "5",
                    radiusCells = "1",
                    strengthPercent = "100",
                    falloffExponent = "1",
                ),
            ),
        )

        val reset = resetInteractionForTableSwitch(previous)

        assertNull(reset.selection)
        assertFalse(reset.persistentExtendSelection)
        assertNull(reset.activeBrushStroke)
    }

    @Test
    fun `preview may only apply to the semantic table that produced it`() {
        assertTrue(previewBelongsToSelectedTable(previewTableId = 11, selectedTableId = 11))
        assertFalse(previewBelongsToSelectedTable(previewTableId = 11, selectedTableId = 23))
    }
}
