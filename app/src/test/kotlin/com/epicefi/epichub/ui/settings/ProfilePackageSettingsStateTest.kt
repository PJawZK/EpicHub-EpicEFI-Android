package com.epicefi.epichub.ui.settings

import com.epicefi.epichub.engine.DefinitionBundleImportRejection
import com.epicefi.epichub.engine.LiveTuneProfileDocumentClearResult
import com.epicefi.epichub.engine.LiveTuneProfileDocumentSelectionResult
import com.epicefi.epichub.engine.LiveTuneProfileSelectionResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfilePackageSettingsStateTest {
    @Test
    fun successfulReplacementReportsCountsAndCleanupWarning() {
        val state =
            ProfilePackageSettingsState(selectedDocumentCount = 1)
                .beginOperation()
                .completeSelection(
                    result =
                        LiveTuneProfileDocumentSelectionResult.Selected(
                            importedBundleCount = 3,
                            replacedPreviousSelection = true,
                            obsoleteGrantReleaseFailures = listOf("release failed"),
                        ),
                    selectedDocumentCount = 2,
                )

        assertEquals(2, state.selectedDocumentCount)
        assertTrue(state.hasSelection)
        assertFalse(state.busy)
        assertTrue(state.message.orEmpty().contains("Replaced 2 profile document(s)"))
        assertTrue(state.message.orEmpty().contains("3 profile bundle(s)"))
        assertTrue(state.message.orEmpty().contains("1 obsolete document grant(s)"))
    }

    @Test
    fun failedReplacementKeepsVisiblePreviousSelectionCount() {
        val state =
            ProfilePackageSettingsState(selectedDocumentCount = 2)
                .beginOperation()
                .completeSelection(
                    result =
                        LiveTuneProfileDocumentSelectionResult.ProfileRejected(
                            result =
                                LiveTuneProfileSelectionResult.BundleRejected(
                                    bundleIndex = 1,
                                    reason = DefinitionBundleImportRejection.CompileFailure,
                                    previousSelectionPreserved = true,
                                ),
                            previousSelectionPreserved = true,
                        ),
                    selectedDocumentCount = 2,
                )

        assertEquals(2, state.selectedDocumentCount)
        assertTrue(state.hasSelection)
        assertTrue(state.message.orEmpty().startsWith("Previous profile selection preserved."))
        assertTrue(state.message.orEmpty().contains("Profile bundle 2 was rejected"))
    }

    @Test
    fun clearDropsSelectionAndSurfacesGrantCleanupFailure() {
        val state =
            ProfilePackageSettingsState(selectedDocumentCount = 2)
                .completeClear(
                    LiveTuneProfileDocumentClearResult(
                        clearedSelection = true,
                        grantReleaseFailures = listOf("a", "b"),
                    ),
                )

        assertEquals(0, state.selectedDocumentCount)
        assertFalse(state.hasSelection)
        assertFalse(state.busy)
        assertTrue(state.message.orEmpty().contains("2 persisted document grant(s)"))
    }

    @Test
    fun beginOperationPreservesCurrentSelectionWhileMarkingBusy() {
        val state =
            ProfilePackageSettingsState(
                selectedDocumentCount = 4,
                message = "old",
            ).beginOperation()

        assertEquals(4, state.selectedDocumentCount)
        assertTrue(state.hasSelection)
        assertTrue(state.busy)
        assertEquals(null, state.message)
    }
}
