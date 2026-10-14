package com.epicefi.epichub.engine

sealed interface LiveTuneProfileDocumentSelectionResult {
    data class Selected(
        val importedBundleCount: Int,
        val replacedPreviousSelection: Boolean,
        val obsoleteGrantReleaseFailures: List<String> = emptyList(),
    ) : LiveTuneProfileDocumentSelectionResult

    data class PermissionRejected(
        val documentIndex: Int,
        val reason: String,
        val previousSelectionPreserved: Boolean,
        val rollbackReleaseFailures: List<String> = emptyList(),
    ) : LiveTuneProfileDocumentSelectionResult

    data class ProfileRejected(
        val result: LiveTuneProfileSelectionResult,
        val previousSelectionPreserved: Boolean,
        val rollbackReleaseFailures: List<String> = emptyList(),
    ) : LiveTuneProfileDocumentSelectionResult

    data class InvalidSelection(
        val reason: String,
        val previousSelectionPreserved: Boolean,
    ) : LiveTuneProfileDocumentSelectionResult

    data object OwnerClosed : LiveTuneProfileDocumentSelectionResult
}

data class LiveTuneProfileDocumentClearResult(
    val clearedSelection: Boolean,
    val grantReleaseFailures: List<String>,
)

internal interface PersistedProfileDocumentGrantStore<Document> {
    /** Returns null on success or a user-facing failure reason. */
    fun persistRead(document: Document): String?

    /** Returns null on success or a user-facing failure reason. */
    fun releaseRead(document: Document): String?
}

internal fun interface ProfileDocumentCatalogFactory<Document> {
    fun create(documents: List<Document>): DefinitionBundleCatalog
}

/**
 * Couples persisted document-read grants with [LiveTuneProfileSelectionOwner] replacement semantics.
 *
 * The ordering is intentionally transactional:
 * 1. acquire grants that are new relative to the current selection;
 * 2. build/open/validate the replacement profile catalog;
 * 3. only after profile authority swaps successfully, release grants no longer referenced.
 *
 * Failed permission/catalog/profile replacement therefore preserves both the previous native profile
 * authority and its existing persisted document grants.
 */
class LiveTuneProfileDocumentSelectionOwner<Document> internal constructor(
    private val profileSelection: LiveTuneProfileSelectionOwner,
    private val grantStore: PersistedProfileDocumentGrantStore<Document>,
    private val catalogFactory: ProfileDocumentCatalogFactory<Document>,
) : AutoCloseable {
    @Volatile
    private var closed = false

    private var documents: List<Document> = emptyList()

    val isClosed: Boolean
        get() = closed

    val selectedDocuments: List<Document>
        get() = synchronized(this) { documents.toList() }

    val connectionOwner: LiveTuneConnectionOwner?
        get() = if (closed) null else profileSelection.connectionOwner

    val liveState: LiveTuneSourceLifecycleState?
        get() = if (closed) null else profileSelection.liveState

    @Synchronized
    fun replace(nextDocuments: List<Document>): LiveTuneProfileDocumentSelectionResult {
        if (closed) return LiveTuneProfileDocumentSelectionResult.OwnerClosed

        if (nextDocuments.isEmpty()) {
            return LiveTuneProfileDocumentSelectionResult.InvalidSelection(
                reason = "At least one profile package document must be selected.",
                previousSelectionPreserved = documents.isNotEmpty(),
            )
        }

        val orderedDistinct = LinkedHashSet<Document>()
        nextDocuments.forEach { document ->
            if (!orderedDistinct.add(document)) {
                return LiveTuneProfileDocumentSelectionResult.InvalidSelection(
                    reason = "Profile package document selection contains duplicates.",
                    previousSelectionPreserved = documents.isNotEmpty(),
                )
            }
        }

        val previousDocuments = documents
        val previousSet = previousDocuments.toHashSet()
        val nextSet = nextDocuments.toHashSet()
        val newlyGranted = ArrayList<Document>()

        nextDocuments.forEachIndexed { index, document ->
            if (document !in previousSet) {
                val failure = grantFailure { grantStore.persistRead(document) }
                if (failure != null) {
                    return LiveTuneProfileDocumentSelectionResult.PermissionRejected(
                        documentIndex = index,
                        reason = failure,
                        previousSelectionPreserved = previousDocuments.isNotEmpty(),
                        rollbackReleaseFailures = releaseAll(newlyGranted.asReversed()),
                    )
                }
                newlyGranted += document
            }
        }

        val catalog =
            try {
                catalogFactory.create(nextDocuments.toList())
            } catch (error: Exception) {
                val reason = error.message ?: error::class.java.simpleName
                return LiveTuneProfileDocumentSelectionResult.ProfileRejected(
                    result =
                        LiveTuneProfileSelectionResult.CatalogFailure(
                            reason = reason,
                            previousSelectionPreserved = profileSelection.hasSelection,
                        ),
                    previousSelectionPreserved = previousDocuments.isNotEmpty(),
                    rollbackReleaseFailures = releaseAll(newlyGranted.asReversed()),
                )
            }

        return when (val selected = profileSelection.replace(catalog)) {
            is LiveTuneProfileSelectionResult.Selected -> {
                documents = nextDocuments.toList()
                val obsolete = previousDocuments.filter { it !in nextSet }
                LiveTuneProfileDocumentSelectionResult.Selected(
                    importedBundleCount = selected.importedBundleCount,
                    replacedPreviousSelection = selected.replacedPreviousSelection,
                    obsoleteGrantReleaseFailures = releaseAll(obsolete),
                )
            }

            else ->
                LiveTuneProfileDocumentSelectionResult.ProfileRejected(
                    result = selected,
                    previousSelectionPreserved = previousDocuments.isNotEmpty(),
                    rollbackReleaseFailures = releaseAll(newlyGranted.asReversed()),
                )
        }
    }

    @Synchronized
    fun clear(): LiveTuneProfileDocumentClearResult {
        if (closed) {
            return LiveTuneProfileDocumentClearResult(
                clearedSelection = false,
                grantReleaseFailures = emptyList(),
            )
        }

        val previous = documents
        val cleared = profileSelection.clear()
        documents = emptyList()
        return LiveTuneProfileDocumentClearResult(
            clearedSelection = cleared,
            grantReleaseFailures = releaseAll(previous),
        )
    }

    @Synchronized
    override fun close() {
        if (closed) return
        val previous = documents
        profileSelection.close()
        documents = emptyList()
        releaseAll(previous)
        closed = true
    }

    private fun releaseAll(items: List<Document>): List<String> {
        if (items.isEmpty()) return emptyList()
        val failures = ArrayList<String>()
        items.forEach { document ->
            val failure = grantFailure { grantStore.releaseRead(document) }
            if (failure != null) failures += failure
        }
        return failures
    }

    private fun grantFailure(action: () -> String?): String? =
        try {
            action()
        } catch (error: Exception) {
            error.message ?: error::class.java.simpleName
        }
}
