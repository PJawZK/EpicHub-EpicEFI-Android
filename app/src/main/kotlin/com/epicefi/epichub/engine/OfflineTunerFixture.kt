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

object OfflineTunerFixture {
    init {
        System.loadLibrary("epichub_engine")
    }

    fun readProjection(): TunerTableProjection {
        val buffer = projectionBuffer()
        val status = fillProjection(buffer)
        check(status == NATIVE_BUFFER_STATUS_OK) {
            "Offline Tuner fixture projection failed with status $status."
        }
        return TunerTableProjectionDecoder.decode(buffer)
    }

    fun previewRectangleStep(
        rectangle: TunerRectangle,
        step: TunerCellStep,
    ): TunerTableProjection {
        val buffer = projectionBuffer()
        val status = fillRectanglePreviewNative(
            rectangle.anchorRow,
            rectangle.anchorCol,
            rectangle.focusRow,
            rectangle.focusCol,
            step.nativeCode,
            buffer,
        )
        check(status == NATIVE_BUFFER_STATUS_OK) {
            "Offline Tuner rectangle preview was rejected with status $status."
        }
        return TunerTableProjectionDecoder.decode(buffer)
    }

    fun applyRectangleStep(
        rectangle: TunerRectangle,
        step: TunerCellStep,
        expectedRevision: Long,
    ): TunerTableProjection {
        val revision = applyRectangleStepNative(
            rectangle.anchorRow,
            rectangle.anchorCol,
            rectangle.focusRow,
            rectangle.focusCol,
            step.nativeCode,
            expectedRevision,
        )
        check(revision >= 0L) { "Offline Tuner rectangle edit was rejected or stale." }
        return readProjection().also { projection ->
            check(projection.draftRevision == revision) {
                "Offline Tuner projection revision did not match the applied rectangle edit."
            }
        }
    }

    fun previewRectangleNumeric(
        rectangle: TunerRectangle,
        operation: TunerNumericOperation,
        operand: String,
    ): TunerTableProjection {
        val buffer = projectionBuffer()
        val status = fillRectangleNumericPreviewNative(
            rectangle.anchorRow,
            rectangle.anchorCol,
            rectangle.focusRow,
            rectangle.focusCol,
            operation.nativeCode,
            operand,
            buffer,
        )
        check(status == NATIVE_BUFFER_STATUS_OK) {
            "Offline Tuner numeric preview was rejected with status $status."
        }
        return TunerTableProjectionDecoder.decode(buffer)
    }

    fun applyRectangleNumeric(
        rectangle: TunerRectangle,
        operation: TunerNumericOperation,
        operand: String,
        expectedRevision: Long,
    ): TunerTableProjection {
        val revision = applyRectangleNumericNative(
            rectangle.anchorRow,
            rectangle.anchorCol,
            rectangle.focusRow,
            rectangle.focusCol,
            operation.nativeCode,
            operand,
            expectedRevision,
        )
        check(revision >= 0L) { "Offline Tuner numeric edit was rejected or stale." }
        return readProjection().also { projection ->
            check(projection.draftRevision == revision) {
                "Offline Tuner projection revision did not match the applied numeric edit."
            }
        }
    }

    fun previewInterpolation(
        rectangle: TunerRectangle,
        mode: TunerInterpolationMode,
    ): TunerTableProjection {
        val buffer = projectionBuffer()
        val status = fillInterpolationPreviewNative(
            rectangle.anchorRow,
            rectangle.anchorCol,
            rectangle.focusRow,
            rectangle.focusCol,
            mode.nativeCode,
            buffer,
        )
        check(status == NATIVE_BUFFER_STATUS_OK) {
            "Offline Tuner interpolation preview was rejected with status $status."
        }
        return TunerTableProjectionDecoder.decode(buffer)
    }

    fun applyInterpolation(
        rectangle: TunerRectangle,
        mode: TunerInterpolationMode,
        expectedRevision: Long,
    ): TunerTableProjection {
        val revision = applyInterpolationNative(
            rectangle.anchorRow,
            rectangle.anchorCol,
            rectangle.focusRow,
            rectangle.focusCol,
            mode.nativeCode,
            expectedRevision,
        )
        check(revision >= 0L) { "Offline Tuner interpolation was rejected or stale." }
        return readProjection().also { projection ->
            check(projection.draftRevision == revision) {
                "Offline Tuner projection revision did not match the applied interpolation."
            }
        }
    }

    fun previewSmoothing(
        rectangle: TunerRectangle,
        strengthPercent: String,
    ): TunerTableProjection {
        val buffer = projectionBuffer()
        val status = fillSmoothingPreviewNative(
            rectangle.anchorRow,
            rectangle.anchorCol,
            rectangle.focusRow,
            rectangle.focusCol,
            strengthPercent,
            buffer,
        )
        check(status == NATIVE_BUFFER_STATUS_OK) {
            "Offline Tuner smoothing preview was rejected with status $status."
        }
        return TunerTableProjectionDecoder.decode(buffer)
    }

    fun applySmoothing(
        rectangle: TunerRectangle,
        strengthPercent: String,
        expectedRevision: Long,
    ): TunerTableProjection {
        val revision = applySmoothingNative(
            rectangle.anchorRow,
            rectangle.anchorCol,
            rectangle.focusRow,
            rectangle.focusCol,
            strengthPercent,
            expectedRevision,
        )
        check(revision >= 0L) { "Offline Tuner smoothing was rejected or stale." }
        return readProjection().also { projection ->
            check(projection.draftRevision == revision) {
                "Offline Tuner projection revision did not match the applied smoothing."
            }
        }
    }

    fun previewBrushPull(
        rectangle: TunerRectangle,
        mode: TunerBrushPullMode,
        delta: String,
        radiusCells: String,
        strengthPercent: String,
        falloffExponent: String,
    ): TunerTableProjection {
        val buffer = projectionBuffer()
        val status = fillBrushPullPreviewNative(
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
        check(status == NATIVE_BUFFER_STATUS_OK) {
            "Offline Tuner Brush/Pull preview was rejected with status $status."
        }
        return TunerTableProjectionDecoder.decode(buffer)
    }

    fun applyBrushPull(
        rectangle: TunerRectangle,
        mode: TunerBrushPullMode,
        delta: String,
        radiusCells: String,
        strengthPercent: String,
        falloffExponent: String,
        expectedRevision: Long,
    ): TunerTableProjection {
        val revision = applyBrushPullNative(
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
        check(revision >= 0L) { "Offline Tuner Brush/Pull edit was rejected or stale." }
        return readProjection().also { projection ->
            check(projection.draftRevision == revision) {
                "Offline Tuner projection revision did not match the applied Brush/Pull edit."
            }
        }
    }

    fun undoProjection(): TunerTableProjection {
        val revision = undoNative()
        check(revision >= 0L) { "Offline Tuner undo is unavailable." }
        return readProjection().also { projection ->
            check(projection.draftRevision == revision)
        }
    }

    fun redoProjection(): TunerTableProjection {
        val revision = redoNative()
        check(revision >= 0L) { "Offline Tuner redo is unavailable." }
        return readProjection().also { projection ->
            check(projection.draftRevision == revision)
        }
    }

    private fun projectionBuffer(): ByteBuffer {
        val size = projectionSize()
        check(size > 0) { "Offline Tuner fixture projection is unavailable." }
        return ByteBuffer.allocateDirect(size).order(ByteOrder.LITTLE_ENDIAN)
    }

    private external fun projectionSize(): Int
    private external fun fillProjection(buffer: ByteBuffer): Int
    private external fun fillRectanglePreviewNative(
        anchorRow: Int,
        anchorCol: Int,
        focusRow: Int,
        focusCol: Int,
        direction: Int,
        buffer: ByteBuffer,
    ): Int
    private external fun applyRectangleStepNative(
        anchorRow: Int,
        anchorCol: Int,
        focusRow: Int,
        focusCol: Int,
        direction: Int,
        expectedRevision: Long,
    ): Long
    private external fun fillRectangleNumericPreviewNative(
        anchorRow: Int,
        anchorCol: Int,
        focusRow: Int,
        focusCol: Int,
        operationCode: Int,
        operand: String,
        buffer: ByteBuffer,
    ): Int
    private external fun applyRectangleNumericNative(
        anchorRow: Int,
        anchorCol: Int,
        focusRow: Int,
        focusCol: Int,
        operationCode: Int,
        operand: String,
        expectedRevision: Long,
    ): Long
    private external fun fillInterpolationPreviewNative(
        anchorRow: Int,
        anchorCol: Int,
        focusRow: Int,
        focusCol: Int,
        modeCode: Int,
        buffer: ByteBuffer,
    ): Int
    private external fun applyInterpolationNative(
        anchorRow: Int,
        anchorCol: Int,
        focusRow: Int,
        focusCol: Int,
        modeCode: Int,
        expectedRevision: Long,
    ): Long
    private external fun fillSmoothingPreviewNative(
        anchorRow: Int,
        anchorCol: Int,
        focusRow: Int,
        focusCol: Int,
        strengthPercent: String,
        buffer: ByteBuffer,
    ): Int
    private external fun applySmoothingNative(
        anchorRow: Int,
        anchorCol: Int,
        focusRow: Int,
        focusCol: Int,
        strengthPercent: String,
        expectedRevision: Long,
    ): Long
    private external fun fillBrushPullPreviewNative(
        anchorRow: Int,
        anchorCol: Int,
        focusRow: Int,
        focusCol: Int,
        modeCode: Int,
        delta: String,
        radiusCells: String,
        strengthPercent: String,
        falloffExponent: String,
        buffer: ByteBuffer,
    ): Int
    private external fun applyBrushPullNative(
        anchorRow: Int,
        anchorCol: Int,
        focusRow: Int,
        focusCol: Int,
        modeCode: Int,
        delta: String,
        radiusCells: String,
        strengthPercent: String,
        falloffExponent: String,
        expectedRevision: Long,
    ): Long
    private external fun undoNative(): Long
    private external fun redoNative(): Long
}
