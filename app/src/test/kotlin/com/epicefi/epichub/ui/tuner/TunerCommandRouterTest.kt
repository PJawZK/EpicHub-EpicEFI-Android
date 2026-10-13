package com.epicefi.epichub.ui.tuner

import com.epicefi.epichub.engine.TunerCellStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TunerCommandRouterTest {
    @Test
    fun selectCell_collapsesSelectionByDefault() {
        val initial = TunerInteractionState(
            selection = TunerRectangleSelection(
                TunerCellPosition(1, 1),
                TunerCellPosition(2, 2),
            ),
        )

        val result = TunerCommandRouter.route(
            TunerCommand.SelectCell(row = 3, col = 4),
            initial,
            rows = 5,
            cols = 6,
        )

        assertEquals(
            TunerRectangleSelection(TunerCellPosition(3, 4), TunerCellPosition(3, 4)),
            result.state.selection,
        )
        assertTrue(result.effect is TunerCommandEffect.CancelPreview)
    }

    @Test
    fun persistentExtend_keepsAnchorAndMovesFocus() {
        val initial = TunerInteractionState(
            selection = TunerRectangleSelection(
                TunerCellPosition(1, 1),
                TunerCellPosition(1, 1),
            ),
            persistentExtendSelection = true,
        )

        val result = TunerCommandRouter.route(
            TunerCommand.SelectCell(row = 3, col = 4),
            initial,
            rows = 5,
            cols = 6,
        )

        assertEquals(
            TunerRectangleSelection(TunerCellPosition(1, 1), TunerCellPosition(3, 4)),
            result.state.selection,
        )
    }

    @Test
    fun arrowMovement_matchesDisplayedYAxisAndClampsAtBounds() {
        var state = TunerInteractionState(
            selection = TunerRectangleSelection(
                TunerCellPosition(1, 1),
                TunerCellPosition(1, 1),
            ),
        )

        state = TunerCommandRouter.route(
            TunerCommand.MoveSelection(TunerMoveDirection.UP),
            state,
            rows = 3,
            cols = 3,
        ).state
        assertEquals(TunerCellPosition(2, 1), state.selection?.focus)

        state = TunerCommandRouter.route(
            TunerCommand.MoveSelection(TunerMoveDirection.UP),
            state,
            rows = 3,
            cols = 3,
        ).state
        assertEquals(TunerCellPosition(2, 1), state.selection?.focus)

        state = TunerCommandRouter.route(
            TunerCommand.MoveSelection(TunerMoveDirection.LEFT),
            state,
            rows = 3,
            cols = 3,
        ).state
        assertEquals(TunerCellPosition(2, 0), state.selection?.focus)
    }

    @Test
    fun shiftArrowExtendsFromOriginalAnchor() {
        val initial = TunerInteractionState(
            selection = TunerRectangleSelection(
                TunerCellPosition(1, 1),
                TunerCellPosition(1, 1),
            ),
        )

        val result = TunerCommandRouter.route(
            TunerCommand.MoveSelection(TunerMoveDirection.RIGHT, extend = true),
            initial,
            rows = 4,
            cols = 4,
        )

        assertEquals(TunerCellPosition(1, 1), result.state.selection?.anchor)
        assertEquals(TunerCellPosition(1, 2), result.state.selection?.focus)
    }

    @Test
    fun movementWithoutExtendCollapsesExistingRectangle() {
        val initial = TunerInteractionState(
            selection = TunerRectangleSelection(
                TunerCellPosition(0, 0),
                TunerCellPosition(2, 2),
            ),
        )

        val result = TunerCommandRouter.route(
            TunerCommand.MoveSelection(TunerMoveDirection.LEFT),
            initial,
            rows = 4,
            cols = 4,
        )

        assertEquals(
            TunerRectangleSelection(TunerCellPosition(2, 1), TunerCellPosition(2, 1)),
            result.state.selection,
        )
    }

    @Test
    fun movementWithoutSelectionCreatesDeterministicOriginSelection() {
        val result = TunerCommandRouter.route(
            TunerCommand.MoveSelection(TunerMoveDirection.RIGHT),
            TunerInteractionState(),
            rows = 4,
            cols = 4,
        )

        assertEquals(
            TunerRectangleSelection(TunerCellPosition(0, 0), TunerCellPosition(0, 0)),
            result.state.selection,
        )
    }

    @Test
    fun invalidPointerSelectionIsNotConsumed() {
        val initial = TunerInteractionState()
        val result = TunerCommandRouter.route(
            TunerCommand.SelectCell(row = 99, col = 0),
            initial,
            rows = 4,
            cols = 4,
        )

        assertFalse(result.consumed)
        assertEquals(initial, result.state)
    }

    @Test
    fun brushStrokeBeginsAsPreviewIntentAndTracksSelection() {
        val parameters = TunerBrushStrokeParameters("5", "1", "100", "1")
        val result = TunerCommandRouter.route(
            TunerCommand.BeginBrushStroke(1, 2, parameters),
            TunerInteractionState(),
            rows = 4,
            cols = 5,
        )

        val stroke = result.state.activeBrushStroke
        assertEquals(listOf(TunerCellPosition(1, 2)), stroke?.points)
        assertEquals(parameters, stroke?.parameters)
        assertEquals(
            TunerRectangleSelection(TunerCellPosition(1, 2), TunerCellPosition(1, 2)),
            result.state.selection,
        )
        assertEquals(TunerCommandEffect.PreviewBrushStroke(stroke!!), result.effect)
    }

    @Test
    fun brushStrokeDropsDuplicateSamplesAndCollapsesForwardCollinearRuns() {
        val parameters = TunerBrushStrokeParameters("5", "1", "100", "1")
        var state = TunerCommandRouter.route(
            TunerCommand.BeginBrushStroke(1, 0, parameters),
            TunerInteractionState(),
            rows = 5,
            cols = 5,
        ).state

        state = TunerCommandRouter.route(
            TunerCommand.ContinueBrushStroke(1, 1),
            state,
            rows = 5,
            cols = 5,
        ).state
        state = TunerCommandRouter.route(
            TunerCommand.ContinueBrushStroke(1, 2),
            state,
            rows = 5,
            cols = 5,
        ).state
        val duplicate = TunerCommandRouter.route(
            TunerCommand.ContinueBrushStroke(1, 2),
            state,
            rows = 5,
            cols = 5,
        )

        assertEquals(
            listOf(TunerCellPosition(1, 0), TunerCellPosition(1, 2)),
            state.activeBrushStroke?.points,
        )
        assertEquals(state, duplicate.state)
        assertEquals(TunerCommandEffect.None, duplicate.effect)
    }

    @Test
    fun brushStrokePreservesTurnsAndEndsWithoutApplying() {
        val parameters = TunerBrushStrokeParameters("5", "1", "100", "1")
        var state = TunerCommandRouter.route(
            TunerCommand.BeginBrushStroke(0, 0, parameters),
            TunerInteractionState(),
            rows = 5,
            cols = 5,
        ).state
        val turn = TunerCommandRouter.route(
            TunerCommand.ContinueBrushStroke(0, 3),
            state,
            rows = 5,
            cols = 5,
        )
        state = turn.state
        val vertical = TunerCommandRouter.route(
            TunerCommand.ContinueBrushStroke(2, 3),
            state,
            rows = 5,
            cols = 5,
        )
        state = vertical.state

        assertEquals(
            listOf(
                TunerCellPosition(0, 0),
                TunerCellPosition(0, 3),
                TunerCellPosition(2, 3),
            ),
            state.activeBrushStroke?.points,
        )
        assertTrue(vertical.effect is TunerCommandEffect.PreviewBrushStroke)

        val ended = TunerCommandRouter.route(
            TunerCommand.EndBrushStroke,
            state,
            rows = 5,
            cols = 5,
        )
        assertNull(ended.state.activeBrushStroke)
        assertEquals(TunerCommandEffect.None, ended.effect)
    }

    @Test
    fun cancelBrushStrokeClearsActiveGestureAndPreview() {
        val parameters = TunerBrushStrokeParameters("5", "1", "100", "1")
        val active = TunerCommandRouter.route(
            TunerCommand.BeginBrushStroke(0, 0, parameters),
            TunerInteractionState(),
            rows = 3,
            cols = 3,
        ).state
        val cancelled = TunerCommandRouter.route(
            TunerCommand.CancelBrushStroke,
            active,
            rows = 3,
            cols = 3,
        )

        assertNull(cancelled.state.activeBrushStroke)
        assertEquals(TunerCommandEffect.CancelPreview, cancelled.effect)
    }

    @Test
    fun editCommandsBecomeEffectsWithoutMutatingSelectionState() {
        val initial = TunerInteractionState(
            selection = TunerRectangleSelection(
                TunerCellPosition(1, 1),
                TunerCellPosition(2, 2),
            ),
        )
        val result = TunerCommandRouter.route(
            TunerCommand.PreviewStep(TunerCellStep.INCREMENT),
            initial,
            rows = 4,
            cols = 4,
        )

        assertEquals(initial, result.state)
        assertEquals(TunerCommandEffect.PreviewStep(TunerCellStep.INCREMENT), result.effect)
    }

    @Test
    fun shortcutRegistryMapsNavigationAndHistoryToSharedCommands() {
        assertEquals(
            TunerCommand.MoveSelection(TunerMoveDirection.UP, extend = false),
            TunerShortcutRegistry.commandFor(TunerKeyStroke(TunerShortcutKey.UP)),
        )
        assertEquals(
            TunerCommand.MoveSelection(TunerMoveDirection.UP, extend = true),
            TunerShortcutRegistry.commandFor(
                TunerKeyStroke(TunerShortcutKey.UP, shift = true),
            ),
        )
        assertEquals(
            TunerCommand.Undo,
            TunerShortcutRegistry.commandFor(TunerKeyStroke(TunerShortcutKey.Z, ctrl = true)),
        )
        assertEquals(
            TunerCommand.Redo,
            TunerShortcutRegistry.commandFor(
                TunerKeyStroke(TunerShortcutKey.Z, ctrl = true, shift = true),
            ),
        )
        assertEquals(
            TunerCommand.Redo,
            TunerShortcutRegistry.commandFor(TunerKeyStroke(TunerShortcutKey.Y, ctrl = true)),
        )
    }

    @Test
    fun shortcutRegistryMapsEditPreviewAndApply() {
        assertEquals(
            TunerCommand.PreviewStep(TunerCellStep.INCREMENT),
            TunerShortcutRegistry.commandFor(TunerKeyStroke(TunerShortcutKey.PLUS)),
        )
        assertEquals(
            TunerCommand.PreviewStep(TunerCellStep.DECREMENT),
            TunerShortcutRegistry.commandFor(TunerKeyStroke(TunerShortcutKey.MINUS)),
        )
        assertEquals(
            TunerCommand.ApplyPreview,
            TunerShortcutRegistry.commandFor(TunerKeyStroke(TunerShortcutKey.ENTER)),
        )
        assertEquals(
            TunerCommand.CancelPreview,
            TunerShortcutRegistry.commandFor(TunerKeyStroke(TunerShortcutKey.ESCAPE)),
        )
    }

    @Test
    fun modifiedUnknownShortcutsAreIgnored() {
        assertNull(
            TunerShortcutRegistry.commandFor(
                TunerKeyStroke(TunerShortcutKey.PLUS, ctrl = true),
            ),
        )
        assertNull(
            TunerShortcutRegistry.commandFor(
                TunerKeyStroke(TunerShortcutKey.UP, alt = true),
            ),
        )
    }

    @Test
    fun clearSelectionAlsoDisablesPersistentExtendAndCancelsPreview() {
        val initial = TunerInteractionState(
            selection = TunerRectangleSelection(
                TunerCellPosition(1, 1),
                TunerCellPosition(2, 2),
            ),
            persistentExtendSelection = true,
        )
        val result = TunerCommandRouter.route(
            TunerCommand.ClearSelection,
            initial,
            rows = 4,
            cols = 4,
        )

        assertNull(result.state.selection)
        assertFalse(result.state.persistentExtendSelection)
        assertNull(result.state.activeBrushStroke)
        assertTrue(result.effect is TunerCommandEffect.CancelPreview)
    }

    @Test
    fun commandRegistryIsUniqueAndDocumentsEveryKeyboardCommand() {
        val ids = TunerShortcutRegistry.definitions.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(TunerShortcutRegistry.definitions.all { it.shortcuts.isNotEmpty() })
    }
}
