package com.epicefi.epichub.engine

import java.nio.ByteBuffer
import java.nio.ByteOrder

object OfflineTunerFixture {
    init {
        System.loadLibrary("epichub_engine")
    }

    fun readProjection(): TunerTableProjection {
        val size = projectionSize()
        check(size > 0) { "Offline Tuner fixture projection is unavailable." }
        val buffer = ByteBuffer.allocateDirect(size).order(ByteOrder.LITTLE_ENDIAN)
        val status = fillProjection(buffer)
        check(status == NATIVE_BUFFER_STATUS_OK) { "Offline Tuner fixture projection failed with status $status." }
        return TunerTableProjectionDecoder.decode(buffer)
    }

    private external fun projectionSize(): Int
    private external fun fillProjection(buffer: ByteBuffer): Int
}
