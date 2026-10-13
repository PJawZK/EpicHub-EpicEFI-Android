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
    private external fun undoNative(): Long
    private external fun redoNative(): Long
}
