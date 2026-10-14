package com.epicefi.epichub.ui.settings

import com.epicefi.epichub.engine.LiveTuneProfileDocumentClearResult
import com.epicefi.epichub.engine.LiveTuneProfileDocumentSelectionResult
import com.epicefi.epichub.engine.LiveTuneProfileSelectionResult

data class ProfilePackageSettingsState(
    val selectedDocumentCount: Int = 0,
    val busy: Boolean = false,
    val message: String? = null,
) {
    val hasSelection: Boolean
        get() = selectedDocumentCount > 0

    fun beginOperation(): ProfilePackageSettingsState = copy(busy = true, message = null)

    fun completeSelection(
        result: LiveTuneProfileDocumentSelectionResult,
        selectedDocumentCount: Int,
    ): ProfilePackageSettingsState =
        ProfilePackageSettingsState(
            selectedDocumentCount = selectedDocumentCount,
            busy = false,
            message = selectionMessage(result, selectedDocumentCount),
        )

    fun completeClear(result: LiveTuneProfileDocumentClearResult): ProfilePackageSettingsState =
        ProfilePackageSettingsState(
            selectedDocumentCount = 0,
            busy = false,
            message =
                when {
                    result.grantReleaseFailures.isNotEmpty() ->
                        "Profile selection cleared, but ${result.grantReleaseFailures.size} persisted document grant(s) could not be released."
                    result.clearedSelection -> "Profile package selection cleared."
                    else -> "No profile package selection was active."
                },
        )
}

private fun selectionMessage(
    result: LiveTuneProfileDocumentSelectionResult,
    selectedDocumentCount: Int,
): String =
    when (result) {
        is LiveTuneProfileDocumentSelectionResult.Selected -> {
            val action = if (result.replacedPreviousSelection) "Replaced" else "Selected"
            val cleanup =
                if (result.obsoleteGrantReleaseFailures.isEmpty()) {
                    ""
                } else {
                    " ${result.obsoleteGrantReleaseFailures.size} obsolete document grant(s) could not be released."
                }
            "$action $selectedDocumentCount profile document(s); imported ${result.importedBundleCount} profile bundle(s).$cleanup"
        }

        is LiveTuneProfileDocumentSelectionResult.PermissionRejected ->
            preservedPrefix(result.previousSelectionPreserved) +
                "Could not retain access to selected document ${result.documentIndex + 1}: ${result.reason}" +
                rollbackSuffix(result.rollbackReleaseFailures.size)

        is LiveTuneProfileDocumentSelectionResult.ProfileRejected ->
            preservedPrefix(result.previousSelectionPreserved) +
                profileRejectionMessage(result.result) +
                rollbackSuffix(result.rollbackReleaseFailures.size)

        is LiveTuneProfileDocumentSelectionResult.InvalidSelection ->
            preservedPrefix(result.previousSelectionPreserved) + result.reason

        LiveTuneProfileDocumentSelectionResult.OwnerClosed ->
            "Profile package selection is unavailable because its owner is closed."
    }

private fun profileRejectionMessage(result: LiveTuneProfileSelectionResult): String =
    when (result) {
        is LiveTuneProfileSelectionResult.BundleRejected ->
            "Profile bundle ${result.bundleIndex + 1} was rejected: ${result.reason}."
        is LiveTuneProfileSelectionResult.CatalogFailure ->
            "Profile package could not be opened: ${result.reason}"
        is LiveTuneProfileSelectionResult.Selected ->
            "Profile replacement returned an unexpected selected result."
        LiveTuneProfileSelectionResult.OwnerClosed ->
            "Profile package selection is unavailable because its owner is closed."
    }

private fun preservedPrefix(preserved: Boolean): String =
    if (preserved) "Previous profile selection preserved. " else ""

private fun rollbackSuffix(failureCount: Int): String =
    if (failureCount == 0) "" else " $failureCount rollback grant release(s) also failed."
