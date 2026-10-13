package com.epicefi.epichub.ui.tuner

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.unit.dp
import com.epicefi.epichub.engine.OfflineTunerFixture
import com.epicefi.epichub.engine.TunerBrushPathPoint
import com.epicefi.epichub.engine.TunerBrushPullMode
import com.epicefi.epichub.engine.TunerCellStep
import com.epicefi.epichub.engine.TunerInterpolationMode
import com.epicefi.epichub.engine.TunerNumericOperation
import com.epicefi.epichub.engine.TunerTableProjection
import com.epicefi.epichub.ui.components.StructuralCard
import java.util.Locale
import kotlin.math.max

private const val CELL_WIDTH_DP = 72
private const val AXIS_WIDTH_DP = 64

private sealed interface PendingOperation {
    data class Step(val step: TunerCellStep) : PendingOperation

    data class Numeric(
        val operation: TunerNumericOperation,
        val operand: String,
    ) : PendingOperation

    data class Interpolation(val mode: TunerInterpolationMode) : PendingOperation

    data class Smoothing(val strengthPercent: String) : PendingOperation

    data class BrushPull(
        val mode: TunerBrushPullMode,
        val delta: String,
        val radiusCells: String,
        val strengthPercent: String,
        val falloffExponent: String,
    ) : PendingOperation

    data class BrushPath(val stroke: TunerBrushStrokeIntent) : PendingOperation
}

private data class PendingPreview(
    val selection: TunerRectangleSelection,
    val operation: PendingOperation,
    val projection: TunerTableProjection,
    val baseRevision: Long,
)

@Composable
fun TunerScreen(modifier: Modifier = Modifier) {
    val initialProjection = remember { runCatching { OfflineTunerFixture.readProjection() } }
    val tableFocusRequester = remember { FocusRequester() }
    var projection by remember { mutableStateOf(initialProjection.getOrNull()) }
    var errorMessage by remember { mutableStateOf(initialProjection.exceptionOrNull()?.message) }
    var show3d by remember { mutableStateOf(false) }
    var interactionState by remember { mutableStateOf(TunerInteractionState()) }
    var pendingPreview by remember { mutableStateOf<PendingPreview?>(null) }
    var numericOperation by remember { mutableStateOf(TunerNumericOperation.SET) }
    var numericOperand by remember { mutableStateOf("") }
    var smoothingStrength by remember { mutableStateOf("50") }
    var brushPullMode by remember { mutableStateOf(TunerBrushPullMode.BRUSH_STROKE) }
    var brushPullDelta by remember { mutableStateOf("5") }
    var brushPullRadius by remember { mutableStateOf("1") }
    var brushPullStrength by remember { mutableStateOf("100") }
    var brushPullFalloff by remember { mutableStateOf("1") }
    var brushGestureEnabled by remember { mutableStateOf(false) }

    fun brushStrokeParameters() = TunerBrushStrokeParameters(
        delta = brushPullDelta,
        radiusCells = brushPullRadius,
        strengthPercent = brushPullStrength,
        falloffExponent = brushPullFalloff,
    )

    fun validatePreview(
        current: TunerTableProjection,
        preview: TunerTableProjection,
    ) {
        check(preview.tableId == current.tableId)
        check(preview.profileGeneration == current.profileGeneration)
        check(preview.draftRevision == current.draftRevision) {
            "Preview revision changed the committed draft."
        }
    }

    fun executeEffect(effect: TunerCommandEffect) {
        when (effect) {
            TunerCommandEffect.None -> Unit
            TunerCommandEffect.CancelPreview -> {
                pendingPreview = null
                errorMessage = null
            }

            is TunerCommandEffect.PreviewStep -> {
                val selected = interactionState.selection ?: return
                val current = projection ?: return
                runCatching {
                    OfflineTunerFixture.previewRectangleStep(
                        selected.toNative(),
                        effect.step,
                    ).also { preview -> validatePreview(current, preview) }
                }.onSuccess { previewProjection ->
                    pendingPreview = PendingPreview(
                        selection = selected,
                        operation = PendingOperation.Step(effect.step),
                        projection = previewProjection,
                        baseRevision = current.draftRevision,
                    )
                    errorMessage = null
                }.onFailure { error ->
                    pendingPreview = null
                    errorMessage = error.message ?: "Preview rejected."
                }
            }

            is TunerCommandEffect.PreviewNumeric -> {
                val selected = interactionState.selection ?: return
                val current = projection ?: return
                runCatching {
                    OfflineTunerFixture.previewRectangleNumeric(
                        rectangle = selected.toNative(),
                        operation = effect.operation,
                        operand = effect.operand,
                    ).also { preview -> validatePreview(current, preview) }
                }.onSuccess { previewProjection ->
                    pendingPreview = PendingPreview(
                        selection = selected,
                        operation = PendingOperation.Numeric(effect.operation, effect.operand),
                        projection = previewProjection,
                        baseRevision = current.draftRevision,
                    )
                    errorMessage = null
                }.onFailure { error ->
                    pendingPreview = null
                    errorMessage = error.message ?: "Numeric preview rejected."
                }
            }

            is TunerCommandEffect.PreviewInterpolation -> {
                val selected = interactionState.selection ?: return
                val current = projection ?: return
                runCatching {
                    OfflineTunerFixture.previewInterpolation(
                        rectangle = selected.toNative(),
                        mode = effect.mode,
                    ).also { preview -> validatePreview(current, preview) }
                }.onSuccess { previewProjection ->
                    pendingPreview = PendingPreview(
                        selection = selected,
                        operation = PendingOperation.Interpolation(effect.mode),
                        projection = previewProjection,
                        baseRevision = current.draftRevision,
                    )
                    errorMessage = null
                }.onFailure { error ->
                    pendingPreview = null
                    errorMessage = error.message ?: "Interpolation preview rejected."
                }
            }

            is TunerCommandEffect.PreviewSmoothing -> {
                val selected = interactionState.selection ?: return
                val current = projection ?: return
                runCatching {
                    OfflineTunerFixture.previewSmoothing(
                        rectangle = selected.toNative(),
                        strengthPercent = effect.strengthPercent,
                    ).also { preview -> validatePreview(current, preview) }
                }.onSuccess { previewProjection ->
                    pendingPreview = PendingPreview(
                        selection = selected,
                        operation = PendingOperation.Smoothing(effect.strengthPercent),
                        projection = previewProjection,
                        baseRevision = current.draftRevision,
                    )
                    errorMessage = null
                }.onFailure { error ->
                    pendingPreview = null
                    errorMessage = error.message ?: "Smoothing preview rejected."
                }
            }

            is TunerCommandEffect.PreviewBrushStroke -> {
                val selected = interactionState.selection ?: return
                val current = projection ?: return
                val parameters = effect.stroke.parameters
                runCatching {
                    OfflineTunerFixture.previewBrushPath(
                        path = effect.stroke.points.map { point ->
                            TunerBrushPathPoint(row = point.row, col = point.col)
                        },
                        delta = parameters.delta,
                        radiusCells = parameters.radiusCells,
                        strengthPercent = parameters.strengthPercent,
                        falloffExponent = parameters.falloffExponent,
                    ).also { preview -> validatePreview(current, preview) }
                }.onSuccess { previewProjection ->
                    pendingPreview = PendingPreview(
                        selection = selected,
                        operation = PendingOperation.BrushPath(effect.stroke),
                        projection = previewProjection,
                        baseRevision = current.draftRevision,
                    )
                    errorMessage = null
                }.onFailure { error ->
                    pendingPreview = null
                    errorMessage = error.message ?: "Brush stroke preview rejected."
                }
            }

            is TunerCommandEffect.PreviewBrushPull -> {
                val selected = interactionState.selection ?: return
                val current = projection ?: return
                runCatching {
                    OfflineTunerFixture.previewBrushPull(
                        rectangle = selected.toNative(),
                        mode = effect.mode,
                        delta = effect.delta,
                        radiusCells = effect.radiusCells,
                        strengthPercent = effect.strengthPercent,
                        falloffExponent = effect.falloffExponent,
                    ).also { preview -> validatePreview(current, preview) }
                }.onSuccess { previewProjection ->
                    pendingPreview = PendingPreview(
                        selection = selected,
                        operation = PendingOperation.BrushPull(
                            mode = effect.mode,
                            delta = effect.delta,
                            radiusCells = effect.radiusCells,
                            strengthPercent = effect.strengthPercent,
                            falloffExponent = effect.falloffExponent,
                        ),
                        projection = previewProjection,
                        baseRevision = current.draftRevision,
                    )
                    errorMessage = null
                }.onFailure { error ->
                    pendingPreview = null
                    errorMessage = error.message ?: "Brush/Pull preview rejected."
                }
            }

            TunerCommandEffect.ApplyPreview -> {
                val preview = pendingPreview ?: return
                runCatching {
                    when (val operation = preview.operation) {
                        is PendingOperation.Step -> OfflineTunerFixture.applyRectangleStep(
                            rectangle = preview.selection.toNative(),
                            step = operation.step,
                            expectedRevision = preview.baseRevision,
                        )

                        is PendingOperation.Numeric -> OfflineTunerFixture.applyRectangleNumeric(
                            rectangle = preview.selection.toNative(),
                            operation = operation.operation,
                            operand = operation.operand,
                            expectedRevision = preview.baseRevision,
                        )

                        is PendingOperation.Interpolation -> OfflineTunerFixture.applyInterpolation(
                            rectangle = preview.selection.toNative(),
                            mode = operation.mode,
                            expectedRevision = preview.baseRevision,
                        )

                        is PendingOperation.Smoothing -> OfflineTunerFixture.applySmoothing(
                            rectangle = preview.selection.toNative(),
                            strengthPercent = operation.strengthPercent,
                            expectedRevision = preview.baseRevision,
                        )

                        is PendingOperation.BrushPull -> OfflineTunerFixture.applyBrushPull(
                            rectangle = preview.selection.toNative(),
                            mode = operation.mode,
                            delta = operation.delta,
                            radiusCells = operation.radiusCells,
                            strengthPercent = operation.strengthPercent,
                            falloffExponent = operation.falloffExponent,
                            expectedRevision = preview.baseRevision,
                        )

                        is PendingOperation.BrushPath -> {
                            val parameters = operation.stroke.parameters
                            OfflineTunerFixture.applyBrushPath(
                                path = operation.stroke.points.map { point ->
                                    TunerBrushPathPoint(row = point.row, col = point.col)
                                },
                                delta = parameters.delta,
                                radiusCells = parameters.radiusCells,
                                strengthPercent = parameters.strengthPercent,
                                falloffExponent = parameters.falloffExponent,
                                expectedRevision = preview.baseRevision,
                            )
                        }
                    }
                }.onSuccess { next ->
                    projection = next
                    pendingPreview = null
                    errorMessage = null
                }.onFailure { error ->
                    pendingPreview = null
                    errorMessage = error.message ?: "Apply rejected."
                }
            }

            TunerCommandEffect.Undo -> {
                runCatching { OfflineTunerFixture.undoProjection() }
                    .onSuccess { next ->
                        projection = next
                        pendingPreview = null
                        errorMessage = null
                    }
                    .onFailure { error ->
                        errorMessage = error.message ?: "Nothing to undo."
                    }
            }

            TunerCommandEffect.Redo -> {
                runCatching { OfflineTunerFixture.redoProjection() }
                    .onSuccess { next ->
                        projection = next
                        pendingPreview = null
                        errorMessage = null
                    }
                    .onFailure { error ->
                        errorMessage = error.message ?: "Nothing to redo."
                    }
            }
        }
    }

    fun dispatch(command: TunerCommand): Boolean {
        val current = projection ?: return false
        val routed = TunerCommandRouter.route(
            command = command,
            state = interactionState,
            rows = current.rows,
            cols = current.cols,
        )
        if (!routed.consumed) return false
        interactionState = routed.state
        executeEffect(routed.effect)
        return true
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Tuner", style = MaterialTheme.typography.headlineMedium)

        StructuralCard(
            title = "Authority",
            modifier = Modifier.fillMaxWidth(),
            subtitle = "Offline semantic draft only",
        ) {
            if (projection == null) {
                Text("OFFLINE FIXTURE · projection unavailable")
                errorMessage?.let { Text(it) }
            } else {
                Text("OFFLINE FIXTURE · NOT ECU APPLIED")
                Text(
                    "Profile generation ${projection?.profileGeneration} · Draft revision ${projection?.draftRevision}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        projection?.let { current ->
            val selection = interactionState.selection
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(
                    onClick = { show3d = false },
                    label = { Text(if (!show3d) "2D · active" else "2D") },
                )
                AssistChip(
                    onClick = { show3d = true },
                    label = { Text(if (show3d) "3D · active" else "3D") },
                )
                AssistChip(
                    onClick = { dispatch(TunerCommand.ToggleExtendSelection) },
                    label = {
                        Text(
                            if (interactionState.persistentExtendSelection) {
                                "Extend · active"
                            } else {
                                "Extend selection"
                            },
                        )
                    },
                )
                AssistChip(
                    onClick = {
                        brushGestureEnabled = !brushGestureEnabled
                        dispatch(TunerCommand.CancelBrushStroke)
                    },
                    label = {
                        Text(if (brushGestureEnabled) "Brush draw · active" else "Brush draw")
                    },
                )
            }

            StructuralCard(
                title = current.name,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(tableFocusRequester)
                    .focusable()
                    .onKeyEvent { event ->
                        event.toTunerCommand()?.let(::dispatch) ?: false
                    },
                subtitle = "Table ${current.tableId} · ${current.rows}×${current.cols} · offline draft",
            ) {
                if (show3d) {
                    Table3d(
                        projection = current,
                        selection = selection,
                        preview = pendingPreview,
                    )
                } else {
                    Table2d(
                        projection = current,
                        selection = selection,
                        preview = pendingPreview,
                        brushGestureEnabled = brushGestureEnabled &&
                            brushPullMode == TunerBrushPullMode.BRUSH_STROKE,
                        brushParameters = brushStrokeParameters(),
                        onCommand = ::dispatch,
                        onCellSelected = { row, col ->
                            tableFocusRequester.requestFocus()
                            dispatch(TunerCommand.SelectCell(row, col))
                        },
                    )
                }
                Text(
                    if (brushGestureEnabled && !show3d) {
                        "Brush draw: drag across the 2D cells to build one previewed stroke. Release keeps the preview; Apply commits it as one history transaction."
                    } else {
                        "Keyboard: arrows move · Shift+arrows extend · +/− preview step · Enter apply · Esc cancel · Ctrl+Z undo · Ctrl+Y or Ctrl+Shift+Z redo"
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            StructuralCard(
                title = "Selection and preview",
                modifier = Modifier.fillMaxWidth(),
                subtitle = "One command router · shared selection · Rust preview/history",
            ) {
                val selected = selection
                if (selected == null) {
                    Text("Tap a cell to start a selection. Arrow keys also create a deterministic starting selection. Enable Extend selection, Shift+Arrow, or Brush draw for routed pointer input.")
                } else {
                    val protectedCount = countProtectedCells(current, selected)
                    Text(
                        "Rows ${selected.firstRow + 1}–${selected.lastRow + 1} · " +
                            "Cols ${selected.firstCol + 1}–${selected.lastCol + 1} · " +
                            "${selected.cellCount} cell${if (selected.cellCount == 1) "" else "s"}",
                    )
                    Text(
                        "Anchor r${selected.anchor.row + 1} c${selected.anchor.col + 1} · " +
                            "Focus r${selected.focus.row + 1} c${selected.focus.col + 1}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    interactionState.activeBrushStroke?.let { stroke ->
                        Text(
                            "Active Brush gesture · ${stroke.points.size} coalesced path points",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (protectedCount > 0) {
                        Text(
                            "$protectedCount protected cell${if (protectedCount == 1) "" else "s"} inside the rectangle. Rectangle transforms reject protected targets; Brush/Pull keeps pinned cells fixed.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    pendingPreview?.let { preview ->
                        Text(
                            "Preview generated by Rust · ${changedCellCount(current, preview.projection)} changed cells · " +
                                "${operationLabel(preview.operation)} · base revision ${preview.baseRevision}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                dispatch(TunerCommand.PreviewStep(TunerCellStep.DECREMENT))
                            },
                        ) {
                            Text("−${formatValue(current.editIncrement, current.displayDecimals)}")
                        }
                        OutlinedButton(
                            onClick = {
                                dispatch(TunerCommand.PreviewStep(TunerCellStep.INCREMENT))
                            },
                        ) {
                            Text("+${formatValue(current.editIncrement, current.displayDecimals)}")
                        }
                        Button(
                            enabled = pendingPreview != null,
                            onClick = { dispatch(TunerCommand.ApplyPreview) },
                        ) {
                            Text("Apply preview")
                        }
                    }

                    Text("Numeric operation", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TunerNumericOperation.entries.forEach { operation ->
                            AssistChip(
                                onClick = {
                                    numericOperation = operation
                                    dispatch(TunerCommand.CancelPreview)
                                },
                                label = {
                                    Text(
                                        if (numericOperation == operation) {
                                            "${numericOperationLabel(operation)} · active"
                                        } else {
                                            numericOperationLabel(operation)
                                        },
                                    )
                                },
                            )
                        }
                    }
                    OutlinedTextField(
                        value = numericOperand,
                        onValueChange = {
                            numericOperand = it
                            dispatch(TunerCommand.CancelPreview)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = {
                            Text(
                                if (numericOperation == TunerNumericOperation.SCALE) {
                                    "Scale factor"
                                } else {
                                    "Value"
                                },
                            )
                        },
                        supportingText = {
                            Text("Text is parsed and validated by Rust before preview. Decimal point uses '.'.")
                        },
                    )
                    OutlinedButton(
                        enabled = numericOperand.isNotBlank(),
                        onClick = {
                            dispatch(
                                TunerCommand.PreviewNumeric(
                                    operation = numericOperation,
                                    operand = numericOperand,
                                ),
                            )
                        },
                    ) {
                        Text("Preview ${numericOperationLabel(numericOperation)}")
                    }

                    Text("Interpolation", style = MaterialTheme.typography.labelLarge)
                    Text(
                        "Uses actual X/Y bin coordinates; the selected boundary cells define the interpolation endpoints/corners.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TunerInterpolationMode.entries.forEach { mode ->
                            OutlinedButton(
                                onClick = {
                                    dispatch(TunerCommand.PreviewInterpolation(mode))
                                },
                            ) {
                                Text(interpolationModeLabel(mode))
                            }
                        }
                    }

                    Text("Smoothing", style = MaterialTheme.typography.labelLarge)
                    Text(
                        "3×3 weighted smoothing uses the committed table as one immutable source surface. Neighbor samples may extend one cell outside the selection; table edges are renormalized.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedTextField(
                        value = smoothingStrength,
                        onValueChange = {
                            smoothingStrength = it
                            dispatch(TunerCommand.CancelPreview)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Smoothing strength %") },
                        supportingText = {
                            Text("Rust accepts >0 through 100. The result is blended before table-value quantization.")
                        },
                    )
                    OutlinedButton(
                        enabled = smoothingStrength.isNotBlank(),
                        onClick = {
                            dispatch(TunerCommand.PreviewSmoothing(smoothingStrength))
                        },
                    ) {
                        Text("Preview smoothing")
                    }

                    Text("Brush / Pull", style = MaterialTheme.typography.labelLarge)
                    Text(
                        "Brush sweeps through the routed path; Pull is a rubber-sheet deformation centered on focus. Radius and falloff are grid-space edit weights, not ECU lookup weights.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TunerBrushPullMode.entries.forEach { mode ->
                            AssistChip(
                                onClick = {
                                    brushPullMode = mode
                                    if (mode != TunerBrushPullMode.BRUSH_STROKE) {
                                        brushGestureEnabled = false
                                    }
                                    dispatch(TunerCommand.CancelPreview)
                                },
                                label = {
                                    Text(
                                        if (brushPullMode == mode) {
                                            "${brushPullModeLabel(mode)} · active"
                                        } else {
                                            brushPullModeLabel(mode)
                                        },
                                    )
                                },
                            )
                        }
                    }
                    OutlinedTextField(
                        value = brushPullDelta,
                        onValueChange = {
                            brushPullDelta = it
                            dispatch(TunerCommand.CancelPreview)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Value displacement") },
                        supportingText = { Text("Signed delta; parsed and quantized by Rust.") },
                    )
                    OutlinedTextField(
                        value = brushPullRadius,
                        onValueChange = {
                            brushPullRadius = it
                            dispatch(TunerCommand.CancelPreview)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Radius · cells") },
                        supportingText = { Text("Inclusive Euclidean radius in table index space; 0 affects only the stroke/center.") },
                    )
                    OutlinedTextField(
                        value = brushPullStrength,
                        onValueChange = {
                            brushPullStrength = it
                            dispatch(TunerCommand.CancelPreview)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Strength %") },
                        supportingText = { Text("Rust accepts >0 through 100.") },
                    )
                    OutlinedTextField(
                        value = brushPullFalloff,
                        onValueChange = {
                            brushPullFalloff = it
                            dispatch(TunerCommand.CancelPreview)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Falloff exponent") },
                        supportingText = { Text("0 = hard, 1 = linear, 2 = softer edge; Rust accepts 0 through 8.") },
                    )
                    OutlinedButton(
                        enabled = brushPullDelta.isNotBlank() &&
                            brushPullRadius.isNotBlank() &&
                            brushPullStrength.isNotBlank() &&
                            brushPullFalloff.isNotBlank(),
                        onClick = {
                            dispatch(
                                TunerCommand.PreviewBrushPull(
                                    mode = brushPullMode,
                                    delta = brushPullDelta,
                                    radiusCells = brushPullRadius,
                                    strengthPercent = brushPullStrength,
                                    falloffExponent = brushPullFalloff,
                                ),
                            )
                        },
                    ) {
                        Text("Preview ${brushPullModeLabel(brushPullMode)}")
                    }

                    OutlinedButton(
                        onClick = { dispatch(TunerCommand.ClearSelection) },
                    ) {
                        Text("Clear selection")
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { dispatch(TunerCommand.Undo) }) { Text("Undo") }
                    OutlinedButton(onClick = { dispatch(TunerCommand.Redo) }) { Text("Redo") }
                    if (pendingPreview != null) {
                        OutlinedButton(onClick = { dispatch(TunerCommand.CancelPreview) }) {
                            Text("Cancel preview")
                        }
                    }
                }
                errorMessage?.let { message ->
                    Text(message, color = MaterialTheme.colorScheme.error)
                }
            }

            StructuralCard(
                title = "Semantic value domain",
                modifier = Modifier.fillMaxWidth(),
                subtitle = "Formatting is not storage resolution",
            ) {
                Text(
                    "Range ${formatValue(current.minimum, current.displayDecimals)}–${formatValue(current.maximum, current.displayDecimals)} · " +
                        "quantum ${formatValue(current.quantum, current.displayDecimals)} · " +
                        "edit increment ${formatValue(current.editIncrement, current.displayDecimals)}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun Table2d(
    projection: TunerTableProjection,
    selection: TunerRectangleSelection?,
    preview: PendingPreview?,
    brushGestureEnabled: Boolean,
    brushParameters: TunerBrushStrokeParameters,
    onCommand: (TunerCommand) -> Boolean,
    onCellSelected: (Int, Int) -> Unit,
) {
    val scroll = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scroll),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            TableCell("Y / X", AXIS_WIDTH_DP, header = true)
            projection.xAxis.forEach { x ->
                TableCell(formatAxis(x), CELL_WIDTH_DP, header = true)
            }
        }
        for (displayRow in projection.rows - 1 downTo 0) {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                TableCell(formatAxis(projection.yAxis[displayRow]), AXIS_WIDTH_DP, header = true)
                for (col in 0 until projection.cols) {
                    val index = displayRow * projection.cols + col
                    val selected = selection?.contains(displayRow, col) == true
                    val displayProjection = preview?.projection ?: projection
                    val previewed = preview?.let {
                        it.projection.values[index] != projection.values[index]
                    } == true
                    TableCell(
                        text = formatValue(
                            displayProjection.values[index],
                            projection.displayDecimals,
                        ),
                        widthDp = CELL_WIDTH_DP,
                        header = false,
                        protected = !projection.editable[index],
                        selected = selected,
                        previewed = previewed,
                        brushBinding = if (brushGestureEnabled) {
                            TunerBrushPointerBinding(
                                startRow = displayRow,
                                startCol = col,
                                rows = projection.rows,
                                cols = projection.cols,
                                parameters = brushParameters,
                                onCommand = onCommand,
                            )
                        } else {
                            null
                        },
                        onClick = { onCellSelected(displayRow, col) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TableCell(
    text: String,
    widthDp: Int,
    header: Boolean,
    protected: Boolean = false,
    selected: Boolean = false,
    previewed: Boolean = false,
    brushBinding: TunerBrushPointerBinding? = null,
    onClick: (() -> Unit)? = null,
) {
    val background = when {
        previewed -> MaterialTheme.colorScheme.tertiaryContainer
        selected -> MaterialTheme.colorScheme.secondaryContainer
        protected -> MaterialTheme.colorScheme.surfaceVariant
        header -> MaterialTheme.colorScheme.surfaceContainerHigh
        else -> MaterialTheme.colorScheme.surfaceContainerLow
    }
    val inputModifier = when {
        brushBinding != null -> Modifier.tunerBrushPointerInput(brushBinding)
        onClick != null -> Modifier.clickable(onClick = onClick)
        else -> Modifier
    }
    Box(
        modifier = Modifier
            .width(widthDp.dp)
            .height(42.dp)
            .background(background)
            .then(inputModifier)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (protected) "$text ·" else text,
            style = if (header) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun Table3d(
    projection: TunerTableProjection,
    selection: TunerRectangleSelection?,
    preview: PendingPreview?,
) {
    val lineColor = MaterialTheme.colorScheme.primary
    val protectedColor = MaterialTheme.colorScheme.error
    val selectedColor = MaterialTheme.colorScheme.secondary
    val previewColor = MaterialTheme.colorScheme.tertiary
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp),
        ) {
            val displayValues = preview?.projection?.values ?: projection.values
            val minValue = displayValues.minOrNull() ?: 0.0
            val maxValue = displayValues.maxOrNull() ?: minValue
            val span = max(maxValue - minValue, 1e-9)
            val xStep = size.width * 0.68f / max(projection.cols - 1, 1)
            val yStep = size.height * 0.34f / max(projection.rows - 1, 1)
            val zScale = size.height * 0.34f
            val origin = Offset(size.width * 0.12f, size.height * 0.76f)

            fun point(row: Int, col: Int): Offset {
                val index = row * projection.cols + col
                val normalized = ((displayValues[index] - minValue) / span).toFloat()
                return Offset(
                    x = origin.x + col * xStep + row * xStep * 0.22f,
                    y = origin.y - row * yStep - normalized * zScale,
                )
            }

            for (row in 0 until projection.rows) {
                for (col in 0 until projection.cols - 1) {
                    drawLine(lineColor, point(row, col), point(row, col + 1), strokeWidth = 3f)
                }
            }
            for (col in 0 until projection.cols) {
                for (row in 0 until projection.rows - 1) {
                    drawLine(lineColor, point(row, col), point(row + 1, col), strokeWidth = 3f)
                }
            }
            for (row in 0 until projection.rows) {
                for (col in 0 until projection.cols) {
                    val index = row * projection.cols + col
                    val isSelected = selection?.contains(row, col) == true
                    val isPreviewed = preview?.let {
                        it.projection.values[index] != projection.values[index]
                    } == true
                    val pointColor = when {
                        isPreviewed -> previewColor
                        isSelected -> selectedColor
                        projection.editable[index] -> lineColor
                        else -> protectedColor
                    }
                    drawCircle(
                        color = pointColor,
                        radius = if (isSelected || isPreviewed) 8f else 5f,
                        center = point(row, col),
                    )
                }
            }
        }
        Text(
            "Same table ${projection.tableId} · draft revision ${projection.draftRevision} · shared selection/preview · 3D inspection only",
            color = labelColor,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

private fun countProtectedCells(
    projection: TunerTableProjection,
    selection: TunerRectangleSelection,
): Int {
    var count = 0
    for (row in selection.firstRow..selection.lastRow) {
        for (col in selection.firstCol..selection.lastCol) {
            if (!projection.editable[row * projection.cols + col]) {
                count += 1
            }
        }
    }
    return count
}

private fun changedCellCount(
    committed: TunerTableProjection,
    preview: TunerTableProjection,
): Int = committed.values.indices.count { index ->
    committed.values[index] != preview.values[index]
}

private fun operationLabel(operation: PendingOperation): String = when (operation) {
    is PendingOperation.Step -> when (operation.step) {
        TunerCellStep.DECREMENT -> "Decrement"
        TunerCellStep.INCREMENT -> "Increment"
    }

    is PendingOperation.Numeric ->
        "${numericOperationLabel(operation.operation)} ${operation.operand.trim()}"

    is PendingOperation.Interpolation -> interpolationModeLabel(operation.mode)

    is PendingOperation.Smoothing -> "Smooth ${operation.strengthPercent.trim()}%"

    is PendingOperation.BrushPull ->
        "${brushPullModeLabel(operation.mode)} Δ${operation.delta.trim()} r${operation.radiusCells.trim()} " +
            "${operation.strengthPercent.trim()}% f${operation.falloffExponent.trim()}"

    is PendingOperation.BrushPath ->
        "Brush path · ${operation.stroke.points.size} points · Δ${operation.stroke.parameters.delta.trim()}"
}

private fun numericOperationLabel(operation: TunerNumericOperation): String = when (operation) {
    TunerNumericOperation.SET -> "Set"
    TunerNumericOperation.ADD -> "Add"
    TunerNumericOperation.SCALE -> "Scale"
}

private fun interpolationModeLabel(mode: TunerInterpolationMode): String = when (mode) {
    TunerInterpolationMode.HORIZONTAL -> "Interpolate H"
    TunerInterpolationMode.VERTICAL -> "Interpolate V"
    TunerInterpolationMode.TWO_DIMENSIONAL -> "Interpolate 2D"
}

private fun brushPullModeLabel(mode: TunerBrushPullMode): String = when (mode) {
    TunerBrushPullMode.BRUSH_STROKE -> "Brush"
    TunerBrushPullMode.PULL_DEFORM -> "Pull"
}

private fun formatValue(value: Double, decimals: Int): String =
    String.format(Locale.US, "%.${decimals}f", value)

private fun formatAxis(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else String.format(Locale.US, "%.2f", value)
