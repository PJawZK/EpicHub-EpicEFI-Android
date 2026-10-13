package com.epicefi.epichub.ui.tuner

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.material3.MaterialTheme
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
import com.epicefi.epichub.engine.TunerTableProjection
import com.epicefi.epichub.ui.components.StructuralCard
import java.util.Locale
import kotlin.math.max

private const val CELL_WIDTH_DP = 72
private const val AXIS_WIDTH_DP = 64

@Composable
fun TunerScreen(modifier: Modifier = Modifier) {
    val projectionResult = remember { runCatching { OfflineTunerFixture.readProjection() } }
    val projection = projectionResult.getOrNull()
    var show3d by remember { mutableStateOf(false) }

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
            subtitle = "Offline semantic projection only",
        ) {
            if (projection == null) {
                Text("OFFLINE FIXTURE · projection unavailable")
                projectionResult.exceptionOrNull()?.message?.let { Text(it) }
            } else {
                Text("OFFLINE FIXTURE · NOT ECU APPLIED")
                Text(
                    "Profile generation ${projection.profileGeneration} · Draft revision ${projection.draftRevision}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        if (projection != null) {
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
                title = projection.name,
                modifier = Modifier.fillMaxWidth(),
                subtitle = "Table ${projection.tableId} · ${projection.rows}×${projection.cols} · read-only",
            ) {
                if (show3d) {
                    ReadOnlyTable3d(projection)
                } else {
                    ReadOnlyTable2d(projection)
                }
            }

            StructuralCard(
                title = "Semantic value domain",
                modifier = Modifier.fillMaxWidth(),
                subtitle = "Formatting is not storage resolution",
            ) {
                Text(
                    "Range ${formatValue(projection.minimum, projection.displayDecimals)}–${formatValue(projection.maximum, projection.displayDecimals)} · " +
                        "quantum ${formatValue(projection.quantum, projection.displayDecimals)} · " +
                        "edit increment ${formatValue(projection.editIncrement, projection.displayDecimals)}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun ReadOnlyTable2d(projection: TunerTableProjection) {
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
                    TableCell(
                        text = formatValue(projection.values[index], projection.displayDecimals),
                        widthDp = CELL_WIDTH_DP,
                        header = false,
                        protected = !projection.editable[index],
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
) {
    val background = when {
        protected -> MaterialTheme.colorScheme.surfaceVariant
        header -> MaterialTheme.colorScheme.surfaceContainerHigh
        else -> MaterialTheme.colorScheme.surfaceContainerLow
    }
    Box(
        modifier = Modifier
            .width(widthDp.dp)
            .height(42.dp)
            .background(background)
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
private fun ReadOnlyTable3d(projection: TunerTableProjection) {
    val lineColor = MaterialTheme.colorScheme.primary
    val protectedColor = MaterialTheme.colorScheme.error
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp),
        ) {
            val minValue = projection.values.minOrNull() ?: 0.0
            val maxValue = projection.values.maxOrNull() ?: minValue
            val span = max(maxValue - minValue, 1e-9)
            val xStep = size.width * 0.68f / max(projection.cols - 1, 1)
            val yStep = size.height * 0.34f / max(projection.rows - 1, 1)
            val zScale = size.height * 0.34f
            val origin = Offset(size.width * 0.12f, size.height * 0.76f)

            fun point(row: Int, col: Int): Offset {
                val index = row * projection.cols + col
                val normalized = ((projection.values[index] - minValue) / span).toFloat()
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
                    drawCircle(
                        color = if (projection.editable[index]) lineColor else protectedColor,
                        radius = 5f,
                        center = point(row, col),
                    )
                }
            }
        }
        Text(
            "Same table ID ${projection.tableId} · same draft revision ${projection.draftRevision} · read-only 3D projection",
            color = labelColor,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

private fun formatValue(value: Double, decimals: Int): String =
    String.format(Locale.US, "%.${decimals}f", value)

private fun formatAxis(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else String.format(Locale.US, "%.2f", value)
