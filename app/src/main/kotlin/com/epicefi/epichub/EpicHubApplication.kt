package com.epicefi.epichub

import android.app.Application
import com.epicefi.epichub.diagnostics.DiagnosticDomain
import com.epicefi.epichub.diagnostics.DiagnosticEventCode
import com.epicefi.epichub.diagnostics.DiagnosticHub
import com.epicefi.epichub.diagnostics.DiagnosticSeverity
import java.util.concurrent.atomic.AtomicLong

class EpicHubApplication : Application() {
    val diagnostics = DiagnosticHub()

    private val nextActivityGeneration = AtomicLong(1)
    private val nextNativeEngineGeneration = AtomicLong(1)

    override fun onCreate() {
        super.onCreate()
        diagnostics.createLifecycleOwner(
            ownerId = PROCESS_OWNER_ID,
            ownerKind = "ApplicationProcess",
            generation = 1,
            initialState = "running",
        )
        diagnostics.record(
            severity = DiagnosticSeverity.INFO,
            domain = DiagnosticDomain.APP_UI,
            code = DiagnosticEventCode.APP_STARTED,
            correlationId = PROCESS_OWNER_ID,
        )
    }

    fun nextActivityGeneration(): Long = nextActivityGeneration.getAndIncrement()

    fun nextNativeEngineGeneration(): Long = nextNativeEngineGeneration.getAndIncrement()

    private companion object {
        const val PROCESS_OWNER_ID = "application-process"
    }
}
