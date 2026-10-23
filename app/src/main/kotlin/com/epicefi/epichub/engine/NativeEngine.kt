package com.epicefi.epichub.engine

import java.io.Closeable
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicLong

data class Mega144h7TuneSourceModes(
    val hundredthVe: Boolean = true,
    val hundredthIgnition: Boolean = true,
    val lambdaTargets: Boolean = true,
) {
    internal fun flags(): Int =
        (if (hundredthVe) 0x1 else 0) or
            (if (hundredthIgnition) 0x2 else 0) or
            (if (lambdaTargets) 0x4 else 0)
}

class NativeEngine private constructor(initialHandle: Long) : Closeable {
    private val handle = AtomicLong(initialHandle)
    private val snapshotBuffer = RuntimeSnapshotBuffer()
    private val diagnosticBuffer = NativeDiagnosticBatchBuffer()
    private val tuneCatalogBuffer = TunerTableCatalogBuffer()
    private val ramSynchronizationStateBuffer = NativeRamSynchronizationStateBuffer()
    private val nonvolatilePageStateBuffer = NativeNonvolatilePageStateBuffer()

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
    fun readTuneCatalog(): TunerTableCatalog = tuneCatalogBuffer.read(requireNativeHandle())

    @Synchronized
    fun readTuneSessionEpoch(): Long {
        val epoch = NativeEngineBridge.tuneSessionEpoch(requireNativeHandle())
        check(epoch > 0L) { "Native TuneSession epoch is unavailable." }
        return epoch
    }

    @Synchronized
    fun readRamSynchronizationState(): RamSynchronizationState? =
        ramSynchronizationStateBuffer.read(requireNativeHandle())

    @Synchronized
    fun readNonvolatilePageState(page: Int, verifiedSignature: ByteArray): NonvolatilePageState =
        nonvolatilePageStateBuffer.read(requireNativeHandle(), page, verifiedSignature)

    @Synchronized
    fun installMega144h7ImportedTunePages(
        page1: ByteArray,
        page2: ByteArray,
        modes: Mega144h7TuneSourceModes = Mega144h7TuneSourceModes(),
    ): Long =
        EngineAuthoritySerialization.exclusive {
            require(page1.size == MEGA144H7_PAGE_1_SIZE) {
                "Mega144H7 page 1 must be exactly $MEGA144H7_PAGE_1_SIZE bytes."
            }
            require(page2.size == MEGA144H7_PAGE_2_SIZE) {
                "Mega144H7 page 2 must be exactly $MEGA144H7_PAGE_2_SIZE bytes."
            }
            val page1Buffer = ByteBuffer.allocateDirect(page1.size).apply {
                put(page1)
                rewind()
            }
            val page2Buffer = ByteBuffer.allocateDirect(page2.size).apply {
                put(page2)
                rewind()
            }
            val result = NativeEngineBridge.installMega144h7ImportedTunePages(
                requireNativeHandle(),
                page1Buffer,
                page2Buffer,
                modes.flags(),
            )
            check(result > 0L) { "Mega144H7 tune source install failed with status $result." }
            result
        }

    @Synchronized
    override fun close() {
        EngineAuthoritySerialization.exclusive {
            val current = handle.getAndSet(0L)
            if (current != 0L) {
                NativeEngineBridge.close(current)
                currentEngineHandle.compareAndSet(current, 0L)
            }
        }
    }

    companion object {
        const val MEGA144H7_PAGE_1_SIZE = 55_764
        const val MEGA144H7_PAGE_2_SIZE = 5_000

        private val currentEngineHandle = AtomicLong(0L)

        fun create(): NativeEngine {
            val nativeHandle = NativeEngineBridge.create()
            check(nativeHandle != 0L) { "Native engine creation failed." }
            if (!currentEngineHandle.compareAndSet(0L, nativeHandle)) {
                NativeEngineBridge.close(nativeHandle)
                error("EpicHub supports one app-level NativeEngine session.")
            }
            return NativeEngine(nativeHandle)
        }

        internal fun requireCurrentHandle(): Long {
            val current = currentEngineHandle.get()
            check(current != 0L) { "No active NativeEngine session." }
            return current
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
    external fun fillRamSourceState(handle: Long, buffer: ByteBuffer): Int
    external fun fillNonvolatilePageState(
        handle: Long,
        page: Int,
        signature: ByteBuffer,
        signatureLength: Int,
        output: ByteBuffer,
    ): Int
    external fun tuneSessionEpoch(handle: Long): Long

    external fun installMega144h7ImportedTunePages(
        handle: Long,
        page1: ByteBuffer,
        page2: ByteBuffer,
        modeFlags: Int,
    ): Long

    external fun tuneCatalogSize(handle: Long): Int
    external fun fillTuneCatalog(handle: Long, buffer: ByteBuffer): Int

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
