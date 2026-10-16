package com.epicefi.epichub.engine

/**
 * Presentation-level semantic table selection over one engine-owned TuneSession.
 *
 * Selection never creates ECU/tune authority. Every selected table ID must still exist in the
 * current Rust-owned catalog for the current NativeEngine handle before a projection or mutation is
 * routed to [TunerSessionClient]. If a newly installed TuneSession no longer contains the previous
 * selection, selection falls back deterministically to the native catalog's default entry.
 */
class TunerTableSelectionOwner {
    private var selectedTableId: Int? = null

    @Synchronized
    fun currentSelection(): TunerTableSelection = resolveSelection()

    @Synchronized
    fun sessionEpoch(): Long = readSessionEpoch(NativeEngine.requireCurrentHandle())

    /**
     * Reads epoch, catalog selection and projection as one presentation snapshot. A TuneSession may
     * be replaced between read-only JNI calls, so the epoch is sampled before and after. Mixed
     * authority is rejected and retried rather than surfaced to Compose.
     */
    @Synchronized
    fun readPresentationSnapshot(): TunerSessionPresentationSnapshot {
        repeat(MAX_PRESENTATION_SNAPSHOT_ATTEMPTS) {
            val handle = NativeEngine.requireCurrentHandle()
            val beforeEpoch = readSessionEpoch(handle)
            val selection = resolveSelection(handle)
            val projection = TunerSessionClient(handle, selection.entry.tableId).readProjection()
            val afterEpoch = readSessionEpoch(handle)
            if (
                beforeEpoch == afterEpoch &&
                projection.tableId == selection.entry.tableId &&
                projection.profileGeneration == selection.catalog.profileGeneration
            ) {
                return TunerSessionPresentationSnapshot(
                    sessionEpoch = afterEpoch,
                    selection = selection,
                    projection = projection,
                )
            }
        }
        error("TuneSession changed while reading Tuner presentation authority.")
    }

    @Synchronized
    fun select(tableId: Int): TunerTableSelection {
        val handle = NativeEngine.requireCurrentHandle()
        val catalog = TunerTableCatalogBuffer().read(handle)
        val entry = requireTunerTableSelection(catalog, tableId)
        selectedTableId = entry.tableId
        return TunerTableSelection(catalog = catalog, entry = entry)
    }

    @Synchronized
    fun readProjection(): TunerTableProjection = client().readProjection()

    @Synchronized
    fun previewRectangleStep(
        rectangle: TunerRectangle,
        step: TunerCellStep,
    ): TunerTableProjection = client().previewRectangleStep(rectangle, step)

    @Synchronized
    fun applyRectangleStep(
        rectangle: TunerRectangle,
        step: TunerCellStep,
        expectedRevision: Long,
    ): TunerTableProjection = client().applyRectangleStep(rectangle, step, expectedRevision)

    @Synchronized
    fun previewRectangleNumeric(
        rectangle: TunerRectangle,
        operation: TunerNumericOperation,
        operand: String,
    ): TunerTableProjection = client().previewRectangleNumeric(rectangle, operation, operand)

    @Synchronized
    fun applyRectangleNumeric(
        rectangle: TunerRectangle,
        operation: TunerNumericOperation,
        operand: String,
        expectedRevision: Long,
    ): TunerTableProjection =
        client().applyRectangleNumeric(rectangle, operation, operand, expectedRevision)

    @Synchronized
    fun previewInterpolation(
        rectangle: TunerRectangle,
        mode: TunerInterpolationMode,
    ): TunerTableProjection = client().previewInterpolation(rectangle, mode)

    @Synchronized
    fun applyInterpolation(
        rectangle: TunerRectangle,
        mode: TunerInterpolationMode,
        expectedRevision: Long,
    ): TunerTableProjection = client().applyInterpolation(rectangle, mode, expectedRevision)

    @Synchronized
    fun previewSmoothing(
        rectangle: TunerRectangle,
        strengthPercent: String,
    ): TunerTableProjection = client().previewSmoothing(rectangle, strengthPercent)

    @Synchronized
    fun applySmoothing(
        rectangle: TunerRectangle,
        strengthPercent: String,
        expectedRevision: Long,
    ): TunerTableProjection = client().applySmoothing(rectangle, strengthPercent, expectedRevision)

    @Synchronized
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

    @Synchronized
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

    @Synchronized
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

    @Synchronized
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

    @Synchronized
    fun undoProjection(): TunerTableProjection = client().undoProjection()

    @Synchronized
    fun redoProjection(): TunerTableProjection = client().redoProjection()

    private fun client(): TunerSessionClient {
        val handle = NativeEngine.requireCurrentHandle()
        val selection = resolveSelection(handle)
        return TunerSessionClient(handle = handle, tableId = selection.entry.tableId)
    }

    private fun resolveSelection(
        handle: Long = NativeEngine.requireCurrentHandle(),
    ): TunerTableSelection {
        val catalog = TunerTableCatalogBuffer().read(handle)
        val entry = resolveTunerTableSelection(catalog, selectedTableId)
        selectedTableId = entry.tableId
        return TunerTableSelection(catalog = catalog, entry = entry)
    }

    private fun readSessionEpoch(handle: Long): Long {
        val epoch = NativeEngineBridge.tuneSessionEpoch(handle)
        check(epoch > 0L) { "Native TuneSession epoch is unavailable." }
        return epoch
    }

    private companion object {
        const val MAX_PRESENTATION_SNAPSHOT_ATTEMPTS = 3
    }
}

data class TunerTableSelection(
    val catalog: TunerTableCatalog,
    val entry: TunerTableCatalogEntry,
)

data class TunerSessionPresentationSnapshot(
    val sessionEpoch: Long,
    val selection: TunerTableSelection,
    val projection: TunerTableProjection,
)

/** Pure catalog selection rules used by the owner and JVM tests without native state. */
internal fun resolveTunerTableSelection(
    catalog: TunerTableCatalog,
    requestedTableId: Int?,
): TunerTableCatalogEntry =
    requestedTableId?.let { tableId -> catalog.tables.firstOrNull { it.tableId == tableId } }
        ?: catalog.defaultTable()

internal fun requireTunerTableSelection(
    catalog: TunerTableCatalog,
    tableId: Int,
): TunerTableCatalogEntry =
    catalog.tables.firstOrNull { it.tableId == tableId }
        ?: error("Tune table $tableId is not present in the active native catalog.")
