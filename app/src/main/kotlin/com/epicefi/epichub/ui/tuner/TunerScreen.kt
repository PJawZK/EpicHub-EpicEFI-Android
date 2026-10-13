package com.epicefi.epichub.ui.tuner

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import com.epicefi.epichub.engine.OfflineTunerFixture
import com.epicefi.epichub.engine.TunerCellStep
import com.epicefi.epichub.engine.TunerTableProjection
import com.epicefi.epichub.ui.components.StructuralCard
import java.util.Locale
import kotlin.math.max

private const val CELL_WIDTH_DP = 72
private const val AXIS_WIDTH_DP = 64

private data class CellSelection(
    val row: Int,
    val col: Int,
)

private data class PendingPreview(
    val selection: CellSelection,
    val step: TunerCellStep,
    val value: Double,
    val baseRevision: Long,
)

@Composable
fun TunerScreen(modifier: Modifier = Modifier) {
    val initialProjection = remember { runCatching { OfflineTunerFixture.readProjection() } }
    var projection by remember { mutableStateOf(initialProjection.getOrNull()) }
    var errorMessage by remember { mutableStateOf(initialProjection.exceptionOrNull()?.message) }
    var show3d by remember { mutableStateOf(false) }
    var selection by remember { mutableStateOf<CellSelection?>(null) }
    var pendingPreview by remember { mutableStateOf<PendingPreview?>(null) }

    fun selectCell(row: Int, col: Int) {
        selection = CellSelection(row, col)
        pendingPreview = null
        errorMessage = null
    }

    fun previewStep(step: TunerCellStep) {
        val selected = selection ?: return
        val current = projection ?: return
        runCatching {
            OfflineTunerFixture.previewCellStep(selected.row, selected.col, step)
        }.onSuccess { value ->
            pendingPreview = PendingPreview(
                selection = selected,
                step = step,
                value = value,
                baseRevision = current.draftRevision,
            )
            errorMessage = null
        }.onFailure { error ->
            pendingPreview = null
            errorMessage = error.message ?: "Preview rejected."
        }
    }

    fun applyPreview() {
        val preview = pendingPreview ?: return
        runCatching {
            OfflineTunerFixture.applyCellStep(
                row = preview.selection.row,
                col = preview.selection.col,
                step = preview.step,
                expectedRevision = preview.baseRevision,
            )
        }.onSuccess { next ->
            projection = next
            pendingPreview = null
            errorMessage = null
        }.onFailure { error ->
            pendingPreview = null
            errorMessage = error.message ?: "Apply rejected."
        }
    }

    fun undo() {
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

    fun redo() {
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(
                    onClick = { show3d = false },
                    label = { Text(if (!show3d) "2D · active" else "2D") },
                )
                AssistChip(
                    onClick = { show3d = true },
                    label = { Text(if (show3d) "3D · active" else "3D") },
                )
            }

            StructuralCard(
                title = current.name,
                modifier = Modifier.fillMaxWidth(),
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
                        onCellSelected = ::selectCell,
                    )
                }
            }

            StructuralCard(
                title = "Selection and preview",
                modifier = Modifier.fillMaxWidth(),
                subtitle = "Preview is semantic; Apply changes only the offline draft",
            ) {
                val selected = selection
                if (selected == null) {
                    Text("Select an editable cell in 2D to begin.")
                } else {
                    val index = selected.row * current.cols + selected.col
                    val committedValue = current.values[index]
                    Text(
                        "Cell r${selected.row + 1} c${selected.col + 1} · committed ${formatValue(committedValue, current.displayDecimals)}",
                    )
                    pendingPreview?.let { preview ->
                        Text(
                            "Preview ${formatValue(preview.value, current.displayDecimals)} · base revision ${preview.baseRevision}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { previewStep(TunerCellStep.DECREMENT) }) {
                            Text("−${formatValue(current.editIncrement, current.displayDecimals)}")
                        }
                        OutlinedButton(onClick = { previewStep(TunerCellStep.INCREMENT) }) {
                            Text("+${formatValue(current.editIncrement, current.displayDecimals)}")
                        }
                        Button(
                            enabled = pendingPreview != null,
                            onClick = ::applyPreview,
                        ) {
                            Text("Apply preview")
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = ::undo) { Text("Undo") }
                    OutlinedButton(onClick = ::redo) { Text("Redo") }
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
    selection: CellSelection?,
    preview: PendingPreview?,
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
                    val selected = selection?.row == displayRow && selection.col == col
                    val previewValue = preview
                        ?.takeIf { it.selection.row == displayRow && it.selection.col == col }
                        ?.value
                    TableCell(
                        text = formatValue(
                            previewValue ?: projection.values[index],
                            projection.displayDecimals,
                        ),
                        widthDp = CELL_WIDTH_DP,
                        header = false,
                        protected = !projection.editable[index],
                        selected = selected,
                        previewed = previewValue != null,
                        onClick = if (projection.editable[index]) {
                            { onCellSelected(displayRow, col) }
                        } else {
                            null
                        },
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
    onClick: (() -> Unit)? = null,
) {
    val background = when {
        protected -> MaterialTheme.colorScheme.surfaceVariant
        previewed -> MaterialTheme.colorScheme.tertiaryContainer
        selected -> MaterialTheme.colorScheme.secondaryContainer
        header -> MaterialTheme.colorScheme.surfaceContainerHigh
        else -> MaterialTheme.colorScheme.surfaceContainerLow
    }
    Box(
        modifier = Modifier
            .width(widthDp.dp)
            .height(42.dp)
            .background(background)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
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
    selection: CellSelection?,
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
            val displayValues = projection.values.copyOf()
            preview?.let { pending ->
                val index = pending.selection.row * projection.cols + pending.selection.col
                if (index in displayValues.indices) {
                    displayValues[index] = pending.value
                }
            }
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
                    val isSelected = selection?.row == row && selection.col == col
                    val isPreviewed = preview?.selection?.row == row && preview.selection.col == col
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
            "Same table ${projection.tableId} · draft revision ${projection.draftRevision} · shared selection · 3D inspection only",
            color = labelColor,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

private fun formatValue(value: Double, decimals: Int): String =
    String.format(Locale.US, "%.${decimals}f", value)

private fun formatAxis(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else String.format(Locale.US, "%.2f", value)
