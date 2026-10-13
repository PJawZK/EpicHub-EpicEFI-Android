package com.epicefi.epichub.ui.tuner

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.consume
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.floor

data class TunerBrushPointerBinding(
    val startRow: Int,
    val startCol: Int,
    val rows: Int,
    val cols: Int,
    val parameters: TunerBrushStrokeParameters,
    val onCommand: (TunerCommand) -> Boolean,
)

fun Modifier.tunerBrushPointerInput(binding: TunerBrushPointerBinding): Modifier =
    pointerInput(
        binding.startRow,
        binding.startCol,
        binding.rows,
        binding.cols,
        binding.parameters,
    ) {
        val colStridePx = (72.dp + 2.dp).toPx()
        val rowStridePx = (42.dp + 2.dp).toPx()

        detectDragGestures(
            onDragStart = {
                binding.onCommand(
                    TunerCommand.BeginBrushStroke(
                        row = binding.startRow,
                        col = binding.startCol,
                        parameters = binding.parameters,
                    ),
                )
            },
            onDragEnd = {
                binding.onCommand(TunerCommand.EndBrushStroke)
            },
            onDragCancel = {
                binding.onCommand(TunerCommand.CancelBrushStroke)
            },
        ) { change, _ ->
            val colOffset = floor(change.position.x / colStridePx).toInt()
            val visualRowOffset = floor(change.position.y / rowStridePx).toInt()
            val row = (binding.startRow - visualRowOffset).coerceIn(0, binding.rows - 1)
            val col = (binding.startCol + colOffset).coerceIn(0, binding.cols - 1)
            binding.onCommand(TunerCommand.ContinueBrushStroke(row = row, col = col))
            change.consume()
        }
    }
