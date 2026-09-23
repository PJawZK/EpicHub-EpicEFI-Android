package com.epicefi.epichub.engine

import java.io.Closeable
import java.util.concurrent.atomic.AtomicLong

class NativeEngine private constructor(initialHandle: Long) : Closeable {
    private val handle = AtomicLong(initialHandle)

    val isAlive: Boolean
        get() {
            val current = handle.get()
            return current != 0L && NativeEngineBridge.isAlive(current)
        }

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
}
