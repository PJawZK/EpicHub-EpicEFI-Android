package com.epicefi.epichub.ui.tuner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Tuner3dDirectEditTest {
    @Test
    fun upwardDragProducesPositiveEditIncrementSteps() {
        assertEquals(
            1.0,
            Tuner3dDirectEdit.pullDeltaForDrag(
                startY = 100.0,
                currentY = 64.0,
                pixelsPerStep = 18.0,
                editIncrement = 0.5,
            )!!,
            0.0,
        )
    }

    @Test
    fun downwardDragProducesNegativeEditIncrementSteps() {
        assertEquals(
            -1.5,
            Tuner3dDirectEdit.pullDeltaForDrag(
                startY = 100.0,
                currentY = 154.0,
                pixelsPerStep = 18.0,
                editIncrement = 0.5,
            )!!,
            0.0,
        )
    }

    @Test
    fun subStepMovementProducesNoPreviewDelta() {
        assertNull(
            Tuner3dDirectEdit.pullDeltaForDrag(
                startY = 100.0,
                currentY = 94.0,
                pixelsPerStep = 18.0,
                editIncrement = 0.5,
            ),
        )
    }

    @Test
    fun hugeDragIsBoundedToPreventUnboundedGestureRequests() {
        assertEquals(
            32.0,
            Tuner3dDirectEdit.pullDeltaForDrag(
                startY = 1000.0,
                currentY = -10000.0,
                pixelsPerStep = 18.0,
                editIncrement = 0.5,
            )!!,
            0.0,
        )
    }

    @Test
    fun invalidPresentationInputsDoNotAcquireEditAuthority() {
        assertNull(Tuner3dDirectEdit.pullDeltaForDrag(Double.NaN, 0.0, 18.0, 0.5))
        assertNull(Tuner3dDirectEdit.pullDeltaForDrag(0.0, Double.POSITIVE_INFINITY, 18.0, 0.5))
        assertNull(Tuner3dDirectEdit.pullDeltaForDrag(0.0, 10.0, 0.0, 0.5))
        assertNull(Tuner3dDirectEdit.pullDeltaForDrag(0.0, 10.0, 18.0, 0.0))
    }
}
