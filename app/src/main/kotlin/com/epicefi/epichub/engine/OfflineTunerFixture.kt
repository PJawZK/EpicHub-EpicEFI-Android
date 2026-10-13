package com.epicefi.epichub.engine

import java.nio.ByteBuffer
import java.nio.ByteOrder

enum class TunerCellStep(val nativeCode: Int) {
    DECREMENT(-1),
    INCREMENT(1),
}

object OfflineTunerFixture {
    init {
        System.loadLibrary("epichub_engine")
    }

    fun readProjection(): TunerTableProjection {
        val size = projectionSize()
        check(size > 0) { "Offline Tuner fixture projection is unavailable." }
        val buffer = ByteBuffer.allocateDirect(size).order(ByteOrder.LITTLE_ENDIAN)
        val status = fillProjection(buffer)
        check(status == NATIVE_BUFFER_STATUS_OK) {
            "Offline Tuner fixture projection failed with status $status."
        }
        return TunerTableProjectionDecoder.decode(buffer)
    }

    fun previewCellStep(row: Int, col: Int, step: TunerCellStep): Double {
        val value = previewCellStepNative(row, col, step.nativeCode)
        check(value.isFinite()) { "Offline Tuner preview was rejected." }
        return value
    }

    fun applyCellStep(
        row: Int,
        col: Int,
        step: TunerCellStep,
        expectedRevision: Long,
    ): TunerTableProjection {
        val revision = applyCellStepNative(row, col, step.nativeCode, expectedRevision)
        check(revision >= 0L) { "Offline Tuner edit was rejected or stale." }
        return readProjection().also { projection ->
            check(projection.draftRevision == revision) {
                "Offline Tuner projection revision did not match the applied edit."
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

    private external fun projectionSize(): Int
    private external fun fillProjection(buffer: ByteBuffer): Int
    private external fun previewCellStepNative(row: Int, col: Int, direction: Int): Double
    private external fun applyCellStepNative(
        row: Int,
        col: Int,
        direction: Int,
        expectedRevision: Long,
    ): Long
    private external fun undoNative(): Long
    private external fun redoNative(): Long
}
