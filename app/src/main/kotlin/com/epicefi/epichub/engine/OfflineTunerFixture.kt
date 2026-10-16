package com.epicefi.epichub.engine

import java.nio.ByteBuffer
import java.nio.ByteOrder

enum class TunerCellStep(val nativeCode: Int) {
    DECREMENT(-1),
    INCREMENT(1),
}

enum class TunerNumericOperation(val nativeCode: Int) {
    SET(0),
    ADD(1),
    SCALE(2),
}

enum class TunerInterpolationMode(val nativeCode: Int) {
    HORIZONTAL(0),
    VERTICAL(1),
    TWO_DIMENSIONAL(2),
}

enum class TunerBrushPullMode(val nativeCode: Int) {
    BRUSH_STROKE(0),
    PULL_DEFORM(1),
}

data class TunerRectangle(
    val anchorRow: Int,
    val anchorCol: Int,
    val focusRow: Int,
    val focusCol: Int,
)

data class TunerBrushPathPoint(
    val row: Int,
    val col: Int,
)

/**
 * Catalog-driven facade for the active engine-owned TuneSession.
 *
 * The catalog is the only source of table identity here. The first catalog entry is deterministic
 * because the native catalog encoder sorts by semantic table ID. This preserves the current
 * single-table Tuner screen while removing the old hardcoded offline table ID. A future table
 * selector can choose another catalog entry without changing any edit/preview semantics below.
 *
 * This object owns no tune state. Every operation resolves the current NativeEngine handle and
 * delegates to that handle-scoped TuneSession.
 */
object ActiveTunerTable {
    fun catalog(): TunerTableCatalog {
        val handle = NativeEngine.requireCurrentHandle()
        return TunerTableCatalogBuffer().read(handle)
    }

    fun activeEntry(): TunerTableCatalogEntry =
        catalog().tables.firstOrNull()
            ?: error("Active TuneSession does not expose any tunable tables.")

    private fun client(): TunerSessionClient = TunerSessionClient(
        handle = NativeEngine.requireCurrentHandle(),
        tableId = activeEntry().tableId,
    )

    fun readProjection(): TunerTableProjection = client().readProjection()

    fun previewRectangleStep(
        rectangle: TunerRectangle,
        step: TunerCellStep,
    ): TunerTableProjection = client().previewRectangleStep(rectangle, step)

    fun applyRectangleStep(
        rectangle: TunerRectangle,
        step: TunerCellStep,
        expectedRevision: Long,
    ): TunerTableProjection = client().applyRectangleStep(rectangle, step, expectedRevision)

    fun previewRectangleNumeric(
        rectangle: TunerRectangle,
        operation: TunerNumericOperation,
        operand: String,
    ): TunerTableProjection = client().previewRectangleNumeric(rectangle, operation, operand)

    fun applyRectangleNumeric(
        rectangle: TunerRectangle,
        operation: TunerNumericOperation,
        operand: String,
        expectedRevision: Long,
    ): TunerTableProjection =
        client().applyRectangleNumeric(rectangle, operation, operand, expectedRevision)

    fun previewInterpolation(
        rectangle: TunerRectangle,
        mode: TunerInterpolationMode,
    ): TunerTableProjection = client().previewInterpolation(rectangle, mode)

    fun applyInterpolation(
        rectangle: TunerRectangle,
        mode: TunerInterpolationMode,
        expectedRevision: Long,
    ): TunerTableProjection = client().applyInterpolation(rectangle, mode, expectedRevision)

    fun previewSmoothing(
        rectangle: TunerRectangle,
        strengthPercent: String,
    ): TunerTableProjection = client().previewSmoothing(rectangle, strengthPercent)

    fun applySmoothing(
        rectangle: TunerRectangle,
        strengthPercent: String,
        expectedRevision: Long,
    ): TunerTableProjection = client().applySmoothing(rectangle, strengthPercent, expectedRevision)

    fun previewBrushPull(
        rectangle: TunerRectangle,
        mode: TunerBrushPullMode,
        delta: String,
        radiusCells: String,
        strengthPercent: String,
        falloffExponent: String,
    ): TunerTableProjection = client().previewBrushPull(
        rectangle,
        mode,
        delta,
        radiusCells,
        strengthPercent,
        falloffExponent,
    )

    fun applyBrushPull(
        rectangle: TunerRectangle,
        mode: TunerBrushPullMode,
        delta: String,
        radiusCells: String,
        strengthPercent: String,
        falloffExponent: String,
        expectedRevision: Long,
    ): TunerTableProjection = client().applyBrushPull(
        rectangle,
        mode,
        delta,
        radiusCells,
        strengthPercent,
        falloffExponent,
        expectedRevision,
    )

    fun previewBrushPath(
        path: List<TunerBrushPathPoint>,
        delta: String,
        radiusCells: String,
        strengthPercent: String,
        falloffExponent: String,
    ): TunerTableProjection = client().previewBrushPath(
        path,
        delta,
        radiusCells,
        strengthPercent,
        falloffExponent,
    )

    fun applyBrushPath(
        path: List<TunerBrushPathPoint>,
        delta: String,
        radiusCells: String,
        strengthPercent: String,
        falloffExponent: String,
        expectedRevision: Long,
    ): TunerTableProjection = client().applyBrushPath(
        path,
        delta,
        radiusCells,
        strengthPercent,
        falloffExponent,
        expectedRevision,
    )

    fun undoProjection(): TunerTableProjection = client().undoProjection()

    fun redoProjection(): TunerTableProjection = client().redoProjection()
}

/**
 * Compatibility name retained for callers/tests from the offline-only phase. It now delegates to
 * the catalog-driven active TuneSession rather than assuming semantic table ID 7.
 */
object OfflineTunerFixture {
    fun readProjection(): TunerTableProjection = ActiveTunerTable.readProjection()

    fun previewRectangleStep(
        rectangle: TunerRectangle,
        step: TunerCellStep,
    ): TunerTableProjection = ActiveTunerTable.previewRectangleStep(rectangle, step)

    fun applyRectangleStep(
        rectangle: TunerRectangle,
        step: TunerCellStep,
        expectedRevision: Long,
    ): TunerTableProjection = ActiveTunerTable.applyRectangleStep(rectangle, step, expectedRevision)

    fun previewRectangleNumeric(
        rectangle: TunerRectangle,
        operation: TunerNumericOperation,
        operand: String,
    ): TunerTableProjection = ActiveTunerTable.previewRectangleNumeric(rectangle, operation, operand)

    fun applyRectangleNumeric(
        rectangle: TunerRectangle,
        operation: TunerNumericOperation,
        operand: String,
        expectedRevision: Long,
    ): TunerTableProjection =
        ActiveTunerTable.applyRectangleNumeric(rectangle, operation, operand, expectedRevision)

    fun previewInterpolation(
        rectangle: TunerRectangle,
        mode: TunerInterpolationMode,
    ): TunerTableProjection = ActiveTunerTable.previewInterpolation(rectangle, mode)

    fun applyInterpolation(
        rectangle: TunerRectangle,
        mode: TunerInterpolationMode,
        expectedRevision: Long,
    ): TunerTableProjection = ActiveTunerTable.applyInterpolation(rectangle, mode, expectedRevision)

    fun previewSmoothing(
        rectangle: TunerRectangle,
        strengthPercent: String,
    ): TunerTableProjection = ActiveTunerTable.previewSmoothing(rectangle, strengthPercent)

    fun applySmoothing(
        rectangle: TunerRectangle,
        strengthPercent: String,
        expectedRevision: Long,
    ): TunerTableProjection =
        ActiveTunerTable.applySmoothing(rectangle, strengthPercent, expectedRevision)

    fun previewBrushPull(
        rectangle: TunerRectangle,
        mode: TunerBrushPullMode,
        delta: String,
        radiusCells: String,
        strengthPercent: String,
        falloffExponent: String,
    ): TunerTableProjection = ActiveTunerTable.previewBrushPull(
        rectangle,
        mode,
        delta,
        radiusCells,
        strengthPercent,
        falloffExponent,
    )

    fun applyBrushPull(
        rectangle: TunerRectangle,
        mode: TunerBrushPullMode,
        delta: String,
        radiusCells: String,
        strengthPercent: String,
        falloffExponent: String,
        expectedRevision: Long,
    ): TunerTableProjection = ActiveTunerTable.applyBrushPull(
        rectangle,
        mode,
        delta,
        radiusCells,
        strengthPercent,
        falloffExponent,
        expectedRevision,
    )

    fun previewBrushPath(
        path: List<TunerBrushPathPoint>,
        delta: String,
        radiusCells: String,
        strengthPercent: String,
        falloffExponent: String,
    ): TunerTableProjection = ActiveTunerTable.previewBrushPath(
        path,
        delta,
        radiusCells,
        strengthPercent,
        falloffExponent,
    )

    fun applyBrushPath(
        path: List<TunerBrushPathPoint>,
        delta: String,
        radiusCells: String,
        strengthPercent: String,
        falloffExponent: String,
        expectedRevision: Long,
    ): TunerTableProjection = ActiveTunerTable.applyBrushPath(
        path,
        delta,
        radiusCells,
        strengthPercent,
        falloffExponent,
        expectedRevision,
    )

    fun undoProjection(): TunerTableProjection = ActiveTunerTable.undoProjection()

    fun redoProjection(): TunerTableProjection = ActiveTunerTable.redoProjection()
}

internal class TunerSessionClient(
    private val handle: Long,
    private val tableId: Int,
) {
    fun readProjection(): TunerTableProjection {
        val buffer = projectionBuffer()
        val status = NativeEngineBridge.fillTuneProjection(handle, tableId, buffer)
        check(status == NATIVE_BUFFER_STATUS_OK) {
            "TuneSession projection failed with status $status."
        }
        return TunerTableProjectionDecoder.decode(buffer)
    }

    fun previewRectangleStep(
        rectangle: TunerRectangle,
        step: TunerCellStep,
    ): TunerTableProjection = preview { buffer ->
        NativeEngineBridge.fillTuneRectangleStepPreview(
            handle,
            tableId,
            rectangle.anchorRow,
            rectangle.anchorCol,
            rectangle.focusRow,
            rectangle.focusCol,
            step.nativeCode,
            buffer,
        )
    }

    fun applyRectangleStep(
        rectangle: TunerRectangle,
        step: TunerCellStep,
        expectedRevision: Long,
    ): TunerTableProjection = mutate("rectangle edit") {
        NativeEngineBridge.applyTuneRectangleStep(
            handle,
            tableId,
            rectangle.anchorRow,
            rectangle.anchorCol,
            rectangle.focusRow,
            rectangle.focusCol,
            step.nativeCode,
            expectedRevision,
        )
    }

    fun previewRectangleNumeric(
        rectangle: TunerRectangle,
        operation: TunerNumericOperation,
        operand: String,
    ): TunerTableProjection = preview { buffer ->
        NativeEngineBridge.fillTuneRectangleNumericPreview(
            handle,
            tableId,
            rectangle.anchorRow,
            rectangle.anchorCol,
            rectangle.focusRow,
            rectangle.focusCol,
            operation.nativeCode,
            operand,
            buffer,
        )
    }

    fun applyRectangleNumeric(
        rectangle: TunerRectangle,
        operation: TunerNumericOperation,
        operand: String,
        expectedRevision: Long,
    ): TunerTableProjection = mutate("numeric edit") {
        NativeEngineBridge.applyTuneRectangleNumeric(
            handle,
            tableId,
            rectangle.anchorRow,
            rectangle.anchorCol,
            rectangle.focusRow,
            rectangle.focusCol,
            operation.nativeCode,
            operand,
            expectedRevision,
        )
    }

    fun previewInterpolation(
        rectangle: TunerRectangle,
        mode: TunerInterpolationMode,
    ): TunerTableProjection = preview { buffer ->
        NativeEngineBridge.fillTuneInterpolationPreview(
            handle,
            tableId,
            rectangle.anchorRow,
            rectangle.anchorCol,
            rectangle.focusRow,
            rectangle.focusCol,
            mode.nativeCode,
            buffer,
        )
    }

    fun applyInterpolation(
        rectangle: TunerRectangle,
        mode: TunerInterpolationMode,
        expectedRevision: Long,
    ): TunerTableProjection = mutate("interpolation") {
        NativeEngineBridge.applyTuneInterpolation(
            handle,
            tableId,
            rectangle.anchorRow,
            rectangle.anchorCol,
            rectangle.focusRow,
            rectangle.focusCol,
            mode.nativeCode,
            expectedRevision,
        )
    }

    fun previewSmoothing(
        rectangle: TunerRectangle,
        strengthPercent: String,
    ): TunerTableProjection = preview { buffer ->
        NativeEngineBridge.fillTuneSmoothingPreview(
            handle,
            tableId,
            rectangle.anchorRow,
            rectangle.anchorCol,
            rectangle.focusRow,
            rectangle.focusCol,
            strengthPercent,
            buffer,
        )
    }

    fun applySmoothing(
        rectangle: TunerRectangle,
        strengthPercent: String,
        expectedRevision: Long,
    ): TunerTableProjection = mutate("smoothing") {
        NativeEngineBridge.applyTuneSmoothing(
            handle,
            tableId,
            rectangle.anchorRow,
            rectangle.anchorCol,
            rectangle.focusRow,
            rectangle.focusCol,
            strengthPercent,
            expectedRevision,
        )
    }

    fun previewBrushPull(
        rectangle: TunerRectangle,
        mode: TunerBrushPullMode,
        delta: String,
        radiusCells: String,
        strengthPercent: String,
        falloffExponent: String,
    ): TunerTableProjection = preview { buffer ->
        NativeEngineBridge.fillTuneBrushPullPreview(
            handle,
            tableId,
            rectangle.anchorRow,
            rectangle.anchorCol,
            rectangle.focusRow,
            rectangle.focusCol,
            mode.nativeCode,
            delta,
            radiusCells,
            strengthPercent,
            falloffExponent,
            buffer,
        )
    }

    fun applyBrushPull(
        rectangle: TunerRectangle,
        mode: TunerBrushPullMode,
        delta: String,
        radiusCells: String,
        strengthPercent: String,
        falloffExponent: String,
        expectedRevision: Long,
    ): TunerTableProjection = mutate("Brush/Pull edit") {
        NativeEngineBridge.applyTuneBrushPull(
            handle,
            tableId,
            rectangle.anchorRow,
            rectangle.anchorCol,
            rectangle.focusRow,
            rectangle.focusCol,
            mode.nativeCode,
            delta,
            radiusCells,
            strengthPercent,
            falloffExponent,
            expectedRevision,
        )
    }

    fun previewBrushPath(
        path: List<TunerBrushPathPoint>,
        delta: String,
        radiusCells: String,
        strengthPercent: String,
        falloffExponent: String,
    ): TunerTableProjection = preview { buffer ->
        NativeEngineBridge.fillTuneBrushPathPreview(
            handle,
            tableId,
            encodeBrushPath(path),
            delta,
            radiusCells,
            strengthPercent,
            falloffExponent,
            buffer,
        )
    }

    fun applyBrushPath(
        path: List<TunerBrushPathPoint>,
        delta: String,
        radiusCells: String,
        strengthPercent: String,
        falloffExponent: String,
        expectedRevision: Long,
    ): TunerTableProjection = mutate("Brush path edit") {
        NativeEngineBridge.applyTuneBrushPath(
            handle,
            tableId,
            encodeBrushPath(path),
            delta,
            radiusCells,
            strengthPercent,
            falloffExponent,
            expectedRevision,
        )
    }

    fun undoProjection(): TunerTableProjection = mutate("undo") {
        NativeEngineBridge.undoTune(handle)
    }

    fun redoProjection(): TunerTableProjection = mutate("redo") {
        NativeEngineBridge.redoTune(handle)
    }

    private fun preview(nativeCall: (ByteBuffer) -> Int): TunerTableProjection {
        val buffer = projectionBuffer()
        val status = nativeCall(buffer)
        check(status == NATIVE_BUFFER_STATUS_OK) {
            "TuneSession preview was rejected with status $status."
        }
        return TunerTableProjectionDecoder.decode(buffer)
    }

    private fun mutate(
        operation: String,
        nativeCall: () -> Long,
    ): TunerTableProjection =
        EngineAuthoritySerialization.exclusive {
            applied(nativeCall(), operation)
        }

    private fun applied(revision: Long, operation: String): TunerTableProjection {
        check(revision >= 0L) { "TuneSession $operation was rejected or stale." }
        return readProjection().also { projection ->
            check(projection.draftRevision == revision) {
                "TuneSession projection revision did not match $operation."
            }
        }
    }

    private fun encodeBrushPath(path: List<TunerBrushPathPoint>): IntArray {
        require(path.isNotEmpty()) { "Brush path must contain at least one cell." }
        return IntArray(path.size * 2).also { packed ->
            path.forEachIndexed { index, point ->
                packed[index * 2] = point.row
                packed[index * 2 + 1] = point.col
            }
        }
    }

    private fun projectionBuffer(): ByteBuffer {
        val size = NativeEngineBridge.tuneProjectionSize(handle, tableId)
        check(size > 0) { "TuneSession table $tableId is unavailable." }
        return ByteBuffer.allocateDirect(size).order(ByteOrder.LITTLE_ENDIAN)
    }
}
