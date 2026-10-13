package com.epicefi.epichub.ui.tuner

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.epicefi.epichub.engine.TunerTableProjection

private val TUNER_3D_HIT_RADIUS = 24.dp

@Composable
fun Tuner3dSurface(
    projection: TunerTableProjection,
    selection: TunerRectangleSelection?,
    previewProjection: TunerTableProjection?,
    interactionMode: Tuner3dInteractionMode,
    onCellSelected: (row: Int, col: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val lineColor = MaterialTheme.colorScheme.primary
    val protectedColor = MaterialTheme.colorScheme.error
    val selectedColor = MaterialTheme.colorScheme.secondary
    val previewColor = MaterialTheme.colorScheme.tertiary
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val hitRadiusPx = with(LocalDensity.current) { TUNER_3D_HIT_RADIUS.toPx() }.toDouble()
    val displayValues = previewProjection?.values ?: projection.values
    var camera by remember { mutableStateOf(Tuner3dCameraState.DEFAULT) }

    val pointerModifier = when (interactionMode) {
        Tuner3dInteractionMode.SELECT_EDIT -> Modifier.pointerInput(
            projection.tableId,
            projection.draftRevision,
            previewProjection?.draftRevision,
            displayValues.contentHashCode(),
            camera,
            hitRadiusPx,
        ) {
            detectTapGestures { tap ->
                val cells = Tuner3dGeometry.projectCells(
                    rows = projection.rows,
                    cols = projection.cols,
                    values = displayValues,
                    width = size.width.toDouble(),
                    height = size.height.toDouble(),
                    camera = camera,
                )
                Tuner3dGeometry.hitTest(
                    cells = cells,
                    x = tap.x.toDouble(),
                    y = tap.y.toDouble(),
                    maxDistance = hitRadiusPx,
                )?.let { hit ->
                    onCellSelected(hit.row, hit.col)
                }
            }
        }

        Tuner3dInteractionMode.INSPECT -> Modifier.pointerInput(projection.tableId) {
            detectTransformGestures { _, pan, zoom, _ ->
                camera = camera.applyGesture(
                    panX = pan.x.toDouble(),
                    panY = pan.y.toDouble(),
                    zoomFactor = zoom.toDouble(),
                )
            }
        }
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .then(pointerModifier),
        ) {
            val cells = Tuner3dGeometry.projectCells(
                rows = projection.rows,
                cols = projection.cols,
                values = displayValues,
                width = size.width.toDouble(),
                height = size.height.toDouble(),
                camera = camera,
            )
            if (cells.size != projection.rows * projection.cols) return@Canvas

            fun point(row: Int, col: Int) = cells[row * projection.cols + col]

            for (row in 0 until projection.rows) {
                for (col in 0 until projection.cols - 1) {
                    val from = point(row, col)
                    val to = point(row, col + 1)
                    drawLine(
                        color = lineColor,
                        start = androidx.compose.ui.geometry.Offset(from.x.toFloat(), from.y.toFloat()),
                        end = androidx.compose.ui.geometry.Offset(to.x.toFloat(), to.y.toFloat()),
                        strokeWidth = 3f,
                    )
                }
            }
            for (col in 0 until projection.cols) {
                for (row in 0 until projection.rows - 1) {
                    val from = point(row, col)
                    val to = point(row + 1, col)
                    drawLine(
                        color = lineColor,
                        start = androidx.compose.ui.geometry.Offset(from.x.toFloat(), from.y.toFloat()),
                        end = androidx.compose.ui.geometry.Offset(to.x.toFloat(), to.y.toFloat()),
                        strokeWidth = 3f,
                    )
                }
            }
            for (row in 0 until projection.rows) {
                for (col in 0 until projection.cols) {
                    val index = row * projection.cols + col
                    val isSelected = selection?.contains(row, col) == true
                    val isPreviewed = previewProjection?.let {
                        it.values[index] != projection.values[index]
                    } == true
                    val pointColor = when {
                        isPreviewed -> previewColor
                        isSelected -> selectedColor
                        projection.editable[index] -> lineColor
                        else -> protectedColor
                    }
                    val cell = point(row, col)
                    drawCircle(
                        color = pointColor,
                        radius = if (isSelected || isPreviewed) 8f else 5f,
                        center = androidx.compose.ui.geometry.Offset(cell.x.toFloat(), cell.y.toFloat()),
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (interactionMode == Tuner3dInteractionMode.INSPECT) {
                OutlinedButton(onClick = { camera = Tuner3dCameraState.DEFAULT }) {
                    Text("Reset 3D view")
                }
            }
        }
        Text(
            "Same table ${projection.tableId} · draft revision ${projection.draftRevision} · shared selection/preview · " +
                if (interactionMode == Tuner3dInteractionMode.SELECT_EDIT) {
                    "Select/Edit: taps resolve semantic cells; camera gestures disabled"
                } else {
                    "Inspect: drag orbits · pinch zooms · no tune commands"
                },
            color = labelColor,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
