package com.epicefi.epichub

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.epicefi.epichub.platform.diagnostics.UiFramePerformanceMonitor

class EpicHubActivity : ComponentActivity() {
    private lateinit var diagnosticsOwnerId: String
    private var nativeEngineGeneration: Long = 0
    private var uiFramePerformanceMonitor: UiFramePerformanceMonitor? = null

    private val epicHubApplication: EpicHubApplication
        get() = application as EpicHubApplication

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val activityGeneration = epicHubApplication.nextActivityGeneration()
        nativeEngineGeneration = epicHubApplication.nextNativeEngineGeneration()
        diagnosticsOwnerId = "activity-$activityGeneration"
        epicHubApplication.diagnostics.createLifecycleOwner(
            ownerId = diagnosticsOwnerId,
            ownerKind = "EpicHubActivity",
            generation = activityGeneration,
            initialState = "created",
        )

        enableEdgeToEdge()
        setContent {
            EpicHubApp(
                diagnostics = epicHubApplication.diagnostics,
                nativeEngineGeneration = nativeEngineGeneration,
            )
        }
        uiFramePerformanceMonitor = UiFramePerformanceMonitor(
            diagnostics = epicHubApplication.diagnostics,
        ).also { monitor ->
            monitor.start(window)
        }
    }

    override fun onStart() {
        super.onStart()
        epicHubApplication.diagnostics.updateLifecycleOwner(diagnosticsOwnerId, "started")
    }

    override fun onResume() {
        super.onResume()
        epicHubApplication.diagnostics.updateLifecycleOwner(diagnosticsOwnerId, "resumed")
    }

    override fun onPause() {
        epicHubApplication.diagnostics.updateLifecycleOwner(diagnosticsOwnerId, "paused")
        super.onPause()
    }

    override fun onStop() {
        epicHubApplication.diagnostics.updateLifecycleOwner(diagnosticsOwnerId, "stopped")
        super.onStop()
    }

    override fun onDestroy() {
        uiFramePerformanceMonitor?.stop()
        uiFramePerformanceMonitor = null
        epicHubApplication.diagnostics.updateLifecycleOwner(
            diagnosticsOwnerId,
            if (isChangingConfigurations) "destroying_configuration" else "destroying",
        )
        epicHubApplication.diagnostics.destroyLifecycleOwner(diagnosticsOwnerId)
        super.onDestroy()
    }
}
