package com.epicefi.epichub.diagnostics

import android.os.SystemClock

interface DiagnosticClock {
    fun monotonicNanos(): Long
    fun wallClockMillis(): Long
}

object SystemDiagnosticClock : DiagnosticClock {
    override fun monotonicNanos(): Long = SystemClock.elapsedRealtimeNanos()

    override fun wallClockMillis(): Long = System.currentTimeMillis()
}
