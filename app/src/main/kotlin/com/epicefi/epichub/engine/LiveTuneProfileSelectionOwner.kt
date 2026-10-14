package com.epicefi.epichub.engine

sealed interface LiveTuneProfileSelectionResult {
    data class Selected(
        val importedBundleCount: Int,
        val replacedPreviousSelection: Boolean,
    ) : LiveTuneProfileSelectionResult

    data class BundleRejected(
        val bundleIndex: Int,
        val reason: DefinitionBundleImportRejection,
        val previousSelectionPreserved: Boolean,
    ) : LiveTuneProfileSelectionResult

    data class CatalogFailure(
        val reason: String,
        val previousSelectionPreserved: Boolean,
    ) : LiveTuneProfileSelectionResult

    data object OwnerClosed : LiveTuneProfileSelectionResult
}

internal fun interface LiveTuneConnectionOwnerFactory {
    fun open(catalog: DefinitionBundleCatalog): LiveTuneConnectionOwnerOpenResult
}

/**
 * Owns the currently selected profile catalog's live-connection authority.
 *
 * Replacement is transactional: a new [LiveTuneConnectionOwner] must open completely before the
 * previous selection is closed. Therefore a malformed/rejected replacement package cannot destroy
 * an already usable profile registry or its retained local TuneSession state.
 *
 * Selecting a profile catalog does not probe, connect to, or authorize any transport/controller.
 */
class LiveTuneProfileSelectionOwner internal constructor(
    private val factory: LiveTuneConnectionOwnerFactory,
) : AutoCloseable {
    constructor(engine: NativeEngine) : this(
        LiveTuneConnectionOwnerFactory { catalog ->
            LiveTuneConnectionOwner.open(engine, catalog)
        },
    )

    @Volatile
    private var closed = false

    private var selectedOwner: LiveTuneConnectionOwner? = null

    val isClosed: Boolean
        get() = closed

    val hasSelection: Boolean
        get() = !closed && selectedOwner != null

    val connectionOwner: LiveTuneConnectionOwner?
        get() = if (closed) null else selectedOwner

    val liveState: LiveTuneSourceLifecycleState?
        get() = connectionOwner?.state

    @Synchronized
    fun replace(catalog: DefinitionBundleCatalog): LiveTuneProfileSelectionResult {
        if (closed) {
            return LiveTuneProfileSelectionResult.OwnerClosed
        }

        val previous = selectedOwner
        return when (val opened = factory.open(catalog)) {
            is LiveTuneConnectionOwnerOpenResult.Opened -> {
                if (!opened.owner.isAlive) {
                    opened.owner.close()
                    LiveTuneProfileSelectionResult.CatalogFailure(
                        reason = "Selected profile catalog did not produce a live connection owner.",
                        previousSelectionPreserved = previous != null,
                    )
                } else {
                    selectedOwner = opened.owner
                    previous?.close()
                    LiveTuneProfileSelectionResult.Selected(
                        importedBundleCount = opened.importedBundleCount,
                        replacedPreviousSelection = previous != null,
                    )
                }
            }

            is LiveTuneConnectionOwnerOpenResult.BundleRejected ->
                LiveTuneProfileSelectionResult.BundleRejected(
                    bundleIndex = opened.bundleIndex,
                    reason = opened.reason,
                    previousSelectionPreserved = previous != null,
                )

            is LiveTuneConnectionOwnerOpenResult.CatalogFailure ->
                LiveTuneProfileSelectionResult.CatalogFailure(
                    reason = opened.reason,
                    previousSelectionPreserved = previous != null,
                )
        }
    }

    @Synchronized
    fun clear(): Boolean {
        if (closed) return false
        val previous = selectedOwner ?: return false
        selectedOwner = null
        previous.close()
        return true
    }

    @Synchronized
    override fun close() {
        if (closed) return
        selectedOwner?.close()
        selectedOwner = null
        closed = true
    }
}
