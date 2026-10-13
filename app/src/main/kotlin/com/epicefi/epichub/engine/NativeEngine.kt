package com.epicefi.epichub.engine

import java.io.Closeable
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicLong

class NativeEngine private constructor(initialHandle: Long) : Closeable {
    private val handle = AtomicLong(initialHandle)
    private val snapshotBuffer = RuntimeSnapshotBuffer()
    private val diagnosticBuffer = NativeDiagnosticBatchBuffer()

    val isAlive: Boolean
        get() {
            val current = handle.get()
            return current != 0L && NativeEngineBridge.isAlive(current)
        }

    internal fun requireNativeHandle(): Long {
        val current = handle.get()
        check(current != 0L) { "Native engine is closed." }
        return current
    }

    @Synchronized
    fun readRuntimeSnapshot(): RuntimeSnapshot = snapshotBuffer.read(requireNativeHandle())

    @Synchronized
    fun drainDiagnosticEvents(): NativeDiagnosticBatch = diagnosticBuffer.read(requireNativeHandle())

    @Synchronized
    override fun close() {
        val current = handle.getAndSet(0L)
        if (current != 0L) {
            NativeEngineBridge.close(current)
        }
    }

    companion object {
        fun create(): NativeEngine {
            val handle = NativeEngineBridge.create()
            check(handle != 0L) { "Native engine creation failed." }
            return NativeEngine(handle)
        }

        fun activeCount(): Int = NativeEngineBridge.activeCount()
    }
}

object NativeEngineBridge {
    init {
        System.loadLibrary("epichub_engine")
    }

    external fun create(): Long
    external fun close(handle: Long)
    external fun isAlive(handle: Long): Boolean
    external fun activeCount(): Int
    external fun fillRuntimeSnapshot(handle: Long, buffer: ByteBuffer): Int
    external fun fillDiagnosticEvents(handle: Long, buffer: ByteBuffer): Int

    external fun tuneProjectionSize(handle: Long, tableId: Int): Int
    external fun fillTuneProjection(handle: Long, tableId: Int, buffer: ByteBuffer): Int

    external fun fillTuneRectangleStepPreview(
        handle: Long,
        tableId: Int,
        anchorRow: Int,
        anchorCol: Int,
        focusRow: Int,
        focusCol: Int,
        direction: Int,
        buffer: ByteBuffer,
    ): Int

    external fun applyTuneRectangleStep(
        handle: Long,
        tableId: Int,
        anchorRow: Int,
        anchorCol: Int,
        focusRow: Int,
        focusCol: Int,
        direction: Int,
        expectedRevision: Long,
    ): Long

    external fun fillTuneRectangleNumericPreview(
        handle: Long,
        tableId: Int,
        anchorRow: Int,
        anchorCol: Int,
        focusRow: Int,
        focusCol: Int,
        operationCode: Int,
        operand: String,
        buffer: ByteBuffer,
    ): Int

    external fun applyTuneRectangleNumeric(
        handle: Long,
        tableId: Int,
        anchorRow: Int,
        anchorCol: Int,
        focusRow: Int,
        focusCol: Int,
        operationCode: Int,
        operand: String,
        expectedRevision: Long,
    ): Long

    external fun fillTuneInterpolationPreview(
        handle: Long,
        tableId: Int,
        anchorRow: Int,
        anchorCol: Int,
        focusRow: Int,
        focusCol: Int,
        modeCode: Int,
        buffer: ByteBuffer,
    ): Int

    external fun applyTuneInterpolation(
        handle: Long,
        tableId: Int,
        anchorRow: Int,
        anchorCol: Int,
        focusRow: Int,
        focusCol: Int,
        modeCode: Int,
        expectedRevision: Long,
    ): Long

    external fun fillTuneSmoothingPreview(
        handle: Long,
        tableId: Int,
        anchorRow: Int,
        anchorCol: Int,
        focusRow: Int,
        focusCol: Int,
        strengthPercent: String,
        buffer: ByteBuffer,
    ): Int

    external fun applyTuneSmoothing(
        handle: Long,
        tableId: Int,
        anchorRow: Int,
        anchorCol: Int,
        focusRow: Int,
        focusCol: Int,
        strengthPercent: String,
        expectedRevision: Long,
    ): Long

    external fun fillTuneBrushPullPreview(
        handle: Long,
        tableId: Int,
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

    external fun applyTuneBrushPull(
        handle: Long,
        tableId: Int,
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

    external fun fillTuneBrushPathPreview(
        handle: Long,
        tableId: Int,
        path: IntArray,
        delta: String,
        radiusCells: String,
        strengthPercent: String,
        falloffExponent: String,
        buffer: ByteBuffer,
    ): Int

    external fun applyTuneBrushPath(
        handle: Long,
        tableId: Int,
        path: IntArray,
        delta: String,
        radiusCells: String,
        strengthPercent: String,
        falloffExponent: String,
        expectedRevision: Long,
    ): Long

    external fun undoTune(handle: Long): Long
    external fun redoTune(handle: Long): Long
}
