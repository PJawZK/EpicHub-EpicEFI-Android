package com.epicefi.epichub.ui.tuner

import com.epicefi.epichub.engine.TunerBrushPullMode
import com.epicefi.epichub.engine.TunerCellStep
import com.epicefi.epichub.engine.TunerInterpolationMode
import com.epicefi.epichub.engine.TunerNumericOperation
import com.epicefi.epichub.engine.TunerRectangle

data class TunerCellPosition(
    val row: Int,
    val col: Int,
)

data class TunerRectangleSelection(
    val anchor: TunerCellPosition,
    val focus: TunerCellPosition,
) {
    val firstRow: Int get() = minOf(anchor.row, focus.row)
    val lastRow: Int get() = maxOf(anchor.row, focus.row)
    val firstCol: Int get() = minOf(anchor.col, focus.col)
    val lastCol: Int get() = maxOf(anchor.col, focus.col)
    val cellCount: Int get() = (lastRow - firstRow + 1) * (lastCol - firstCol + 1)

    fun contains(row: Int, col: Int): Boolean =
        row in firstRow..lastRow && col in firstCol..lastCol

    fun toNative(): TunerRectangle = TunerRectangle(
        anchorRow = anchor.row,
        anchorCol = anchor.col,
        focusRow = focus.row,
        focusCol = focus.col,
    )
}

data class TunerInteractionState(
    val selection: TunerRectangleSelection? = null,
    val persistentExtendSelection: Boolean = false,
)

enum class TunerMoveDirection {
    UP,
    DOWN,
    LEFT,
    RIGHT,
}

sealed interface TunerCommand {
    data class SelectCell(
        val row: Int,
        val col: Int,
        val extend: Boolean? = null,
    ) : TunerCommand

    data class MoveSelection(
        val direction: TunerMoveDirection,
        val extend: Boolean = false,
    ) : TunerCommand

    data object ToggleExtendSelection : TunerCommand
    data object ClearSelection : TunerCommand

    data class PreviewStep(val step: TunerCellStep) : TunerCommand

    data class PreviewNumeric(
        val operation: TunerNumericOperation,
        val operand: String,
    ) : TunerCommand

    data class PreviewInterpolation(val mode: TunerInterpolationMode) : TunerCommand
    data class PreviewSmoothing(val strengthPercent: String) : TunerCommand

    data class PreviewBrushPull(
        val mode: TunerBrushPullMode,
        val delta: String,
        val radiusCells: String,
        val strengthPercent: String,
        val falloffExponent: String,
    ) : TunerCommand

    data object ApplyPreview : TunerCommand
    data object CancelPreview : TunerCommand
    data object Undo : TunerCommand
    data object Redo : TunerCommand
}

sealed interface TunerCommandEffect {
    data object None : TunerCommandEffect
    data object CancelPreview : TunerCommandEffect
    data object ApplyPreview : TunerCommandEffect
    data object Undo : TunerCommandEffect
    data object Redo : TunerCommandEffect

    data class PreviewStep(val step: TunerCellStep) : TunerCommandEffect

    data class PreviewNumeric(
        val operation: TunerNumericOperation,
        val operand: String,
    ) : TunerCommandEffect

    data class PreviewInterpolation(val mode: TunerInterpolationMode) : TunerCommandEffect
    data class PreviewSmoothing(val strengthPercent: String) : TunerCommandEffect

    data class PreviewBrushPull(
        val mode: TunerBrushPullMode,
        val delta: String,
        val radiusCells: String,
        val strengthPercent: String,
        val falloffExponent: String,
    ) : TunerCommandEffect
}

data class TunerRouteResult(
    val state: TunerInteractionState,
    val effect: TunerCommandEffect = TunerCommandEffect.None,
    val consumed: Boolean = true,
)

object TunerCommandRouter {
    fun route(
        command: TunerCommand,
        state: TunerInteractionState,
        rows: Int,
        cols: Int,
    ): TunerRouteResult = when (command) {
        is TunerCommand.SelectCell -> selectCell(command, state, rows, cols)
        is TunerCommand.MoveSelection -> moveSelection(command, state, rows, cols)
        TunerCommand.ToggleExtendSelection -> TunerRouteResult(
            state = state.copy(
                persistentExtendSelection = !state.persistentExtendSelection,
            ),
            effect = TunerCommandEffect.CancelPreview,
        )

        TunerCommand.ClearSelection -> TunerRouteResult(
            state = TunerInteractionState(),
            effect = TunerCommandEffect.CancelPreview,
        )

        is TunerCommand.PreviewStep -> TunerRouteResult(
            state,
            TunerCommandEffect.PreviewStep(command.step),
        )

        is TunerCommand.PreviewNumeric -> TunerRouteResult(
            state,
            TunerCommandEffect.PreviewNumeric(command.operation, command.operand),
        )

        is TunerCommand.PreviewInterpolation -> TunerRouteResult(
            state,
            TunerCommandEffect.PreviewInterpolation(command.mode),
        )

        is TunerCommand.PreviewSmoothing -> TunerRouteResult(
            state,
            TunerCommandEffect.PreviewSmoothing(command.strengthPercent),
        )

        is TunerCommand.PreviewBrushPull -> TunerRouteResult(
            state,
            TunerCommandEffect.PreviewBrushPull(
                mode = command.mode,
                delta = command.delta,
                radiusCells = command.radiusCells,
                strengthPercent = command.strengthPercent,
                falloffExponent = command.falloffExponent,
            ),
        )

        TunerCommand.ApplyPreview -> TunerRouteResult(state, TunerCommandEffect.ApplyPreview)
        TunerCommand.CancelPreview -> TunerRouteResult(state, TunerCommandEffect.CancelPreview)
        TunerCommand.Undo -> TunerRouteResult(state, TunerCommandEffect.Undo)
        TunerCommand.Redo -> TunerRouteResult(state, TunerCommandEffect.Redo)
    }

    private fun selectCell(
        command: TunerCommand.SelectCell,
        state: TunerInteractionState,
        rows: Int,
        cols: Int,
    ): TunerRouteResult {
        if (!isValidCell(command.row, command.col, rows, cols)) {
            return TunerRouteResult(state = state, consumed = false)
        }

        val cell = TunerCellPosition(command.row, command.col)
        val extend = command.extend ?: state.persistentExtendSelection
        val selection = if (extend && state.selection != null) {
            state.selection.copy(focus = cell)
        } else {
            TunerRectangleSelection(anchor = cell, focus = cell)
        }
        return TunerRouteResult(
            state = state.copy(selection = selection),
            effect = TunerCommandEffect.CancelPreview,
        )
    }

    private fun moveSelection(
        command: TunerCommand.MoveSelection,
        state: TunerInteractionState,
        rows: Int,
        cols: Int,
    ): TunerRouteResult {
        if (rows <= 0 || cols <= 0) {
            return TunerRouteResult(state = state, consumed = false)
        }

        if (state.selection == null) {
            val initial = TunerCellPosition(row = 0, col = 0)
            return TunerRouteResult(
                state = state.copy(
                    selection = TunerRectangleSelection(initial, initial),
                ),
                effect = TunerCommandEffect.CancelPreview,
            )
        }

        val current = state.selection.focus
        val moved = when (command.direction) {
            TunerMoveDirection.UP -> current.copy(row = (current.row + 1).coerceAtMost(rows - 1))
            TunerMoveDirection.DOWN -> current.copy(row = (current.row - 1).coerceAtLeast(0))
            TunerMoveDirection.LEFT -> current.copy(col = (current.col - 1).coerceAtLeast(0))
            TunerMoveDirection.RIGHT -> current.copy(col = (current.col + 1).coerceAtMost(cols - 1))
        }
        val extend = command.extend || state.persistentExtendSelection
        val selection = if (extend) {
            state.selection.copy(focus = moved)
        } else {
            TunerRectangleSelection(anchor = moved, focus = moved)
        }
        return TunerRouteResult(
            state = state.copy(selection = selection),
            effect = TunerCommandEffect.CancelPreview,
        )
    }

    private fun isValidCell(row: Int, col: Int, rows: Int, cols: Int): Boolean =
        row in 0 until rows && col in 0 until cols
}

enum class TunerShortcutKey {
    UP,
    DOWN,
    LEFT,
    RIGHT,
    PLUS,
    MINUS,
    ENTER,
    ESCAPE,
    Z,
    Y,
}

data class TunerKeyStroke(
    val key: TunerShortcutKey,
    val ctrl: Boolean = false,
    val shift: Boolean = false,
    val alt: Boolean = false,
)

data class TunerCommandDefinition(
    val id: String,
    val label: String,
    val shortcuts: List<String>,
)

object TunerShortcutRegistry {
    val definitions: List<TunerCommandDefinition> = listOf(
        TunerCommandDefinition("move-up", "Move selection up", listOf("↑", "Shift+↑ extend")),
        TunerCommandDefinition("move-down", "Move selection down", listOf("↓", "Shift+↓ extend")),
        TunerCommandDefinition("move-left", "Move selection left", listOf("←", "Shift+← extend")),
        TunerCommandDefinition("move-right", "Move selection right", listOf("→", "Shift+→ extend")),
        TunerCommandDefinition("increment", "Preview increment", listOf("+")),
        TunerCommandDefinition("decrement", "Preview decrement", listOf("-")),
        TunerCommandDefinition("apply-preview", "Apply preview", listOf("Enter")),
        TunerCommandDefinition("cancel-preview", "Cancel preview", listOf("Esc")),
        TunerCommandDefinition("undo", "Undo", listOf("Ctrl+Z")),
        TunerCommandDefinition("redo", "Redo", listOf("Ctrl+Y", "Ctrl+Shift+Z")),
    )

    fun commandFor(stroke: TunerKeyStroke): TunerCommand? {
        if (stroke.alt) return null

        return when {
            stroke.ctrl && stroke.key == TunerShortcutKey.Z && stroke.shift -> TunerCommand.Redo
            stroke.ctrl && stroke.key == TunerShortcutKey.Z -> TunerCommand.Undo
            stroke.ctrl && stroke.key == TunerShortcutKey.Y -> TunerCommand.Redo
            stroke.ctrl -> null
            stroke.key == TunerShortcutKey.UP -> TunerCommand.MoveSelection(
                TunerMoveDirection.UP,
                extend = stroke.shift,
            )
            stroke.key == TunerShortcutKey.DOWN -> TunerCommand.MoveSelection(
                TunerMoveDirection.DOWN,
                extend = stroke.shift,
            )
            stroke.key == TunerShortcutKey.LEFT -> TunerCommand.MoveSelection(
                TunerMoveDirection.LEFT,
                extend = stroke.shift,
            )
            stroke.key == TunerShortcutKey.RIGHT -> TunerCommand.MoveSelection(
                TunerMoveDirection.RIGHT,
                extend = stroke.shift,
            )
            stroke.key == TunerShortcutKey.PLUS -> TunerCommand.PreviewStep(TunerCellStep.INCREMENT)
            stroke.key == TunerShortcutKey.MINUS -> TunerCommand.PreviewStep(TunerCellStep.DECREMENT)
            stroke.key == TunerShortcutKey.ENTER -> TunerCommand.ApplyPreview
            stroke.key == TunerShortcutKey.ESCAPE -> TunerCommand.CancelPreview
            else -> null
        }
    }
}
