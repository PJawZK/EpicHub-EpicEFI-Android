package com.epicefi.epichub.engine

import java.io.Closeable
import java.nio.ByteBuffer
import java.nio.ByteOrder
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

    @Synchronized
    fun readRuntimeSnapshot(): RuntimeSnapshot {
        val current = handle.get()
        check(current != 0L) { "Native engine is closed." }
        return snapshotBuffer.read(current)
    }

    @Synchronized
    fun drainDiagnosticEvents(): NativeDiagnosticBatch {
        val current = handle.get()
        check(current != 0L) { "Native engine is closed." }
        return diagnosticBuffer.read(current)
    }

    @Synchronized
    fun readOfflineTableProjection(): TunerTableProjection {
        val current = handle.get()
        check(current != 0L) { "Native engine is closed." }
        val size = NativeEngineBridge.offlineTableProjectionSize(current)
        check(size > 0) { "Offline table projection is unavailable." }
        val buffer = ByteBuffer.allocateDirect(size).order(ByteOrder.LITTLE_ENDIAN)
        val status = NativeEngineBridge.fillOfflineTableProjection(current, buffer)
        check(status == NATIVE_BUFFER_STATUS_OK) { "Offline table projection failed with status $status." }
        return TunerTableProjectionDecoder.decode(buffer)
    }

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
    external fun offlineTableProjectionSize(handle: Long): Int
    external fun fillOfflineTableProjection(handle: Long, buffer: ByteBuffer): Int
}
