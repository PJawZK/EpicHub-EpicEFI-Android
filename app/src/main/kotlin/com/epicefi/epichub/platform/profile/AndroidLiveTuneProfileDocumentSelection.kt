package com.epicefi.epichub.platform.profile

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import com.epicefi.epichub.engine.LiveTuneConnectionOwner
import com.epicefi.epichub.engine.LiveTuneProfileDocumentClearResult
import com.epicefi.epichub.engine.LiveTuneProfileDocumentSelectionOwner
import com.epicefi.epichub.engine.LiveTuneProfileDocumentSelectionResult
import com.epicefi.epichub.engine.LiveTuneProfileSelectionOwner
import com.epicefi.epichub.engine.LiveTuneSourceLifecycleState
import com.epicefi.epichub.engine.NativeEngine
import com.epicefi.epichub.engine.PersistedProfileDocumentGrantStore
import com.epicefi.epichub.engine.ProfileDocumentCatalogFactory

private class AndroidPersistedProfileDocumentGrantStore(
    private val contentResolver: ContentResolver,
) : PersistedProfileDocumentGrantStore<Uri> {
    override fun persistRead(document: Uri): String? =
        try {
            contentResolver.takePersistableUriPermission(
                document,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
            null
        } catch (error: Exception) {
            "Could not persist read permission for $document: " +
                (error.message ?: error::class.java.simpleName)
        }

    override fun releaseRead(document: Uri): String? =
        try {
            contentResolver.releasePersistableUriPermission(
                document,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
            null
        } catch (error: Exception) {
            "Could not release read permission for $document: " +
                (error.message ?: error::class.java.simpleName)
        }
}

/**
 * Production Android owner for explicit SAF-selected EpicHub profile package documents.
 *
 * This class owns persisted read grants and the currently selected profile registry/connection
 * owner, but it never discovers or connects USB hardware. A later explicit controller action uses
 * [connectionOwner] after a successful package selection.
 */
class AndroidLiveTuneProfileDocumentSelectionOwner(
    engine: NativeEngine,
    private val contentResolver: ContentResolver,
) : AutoCloseable {
    private val delegate =
        LiveTuneProfileDocumentSelectionOwner(
            profileSelection = LiveTuneProfileSelectionOwner(engine),
            grantStore = AndroidPersistedProfileDocumentGrantStore(contentResolver),
            catalogFactory =
                ProfileDocumentCatalogFactory { uris ->
                    AndroidDefinitionBundleDocumentCatalog(contentResolver, uris)
                },
        )

    val isClosed: Boolean
        get() = delegate.isClosed

    val selectedUris: List<Uri>
        get() = delegate.selectedDocuments

    val connectionOwner: LiveTuneConnectionOwner?
        get() = delegate.connectionOwner

    val liveState: LiveTuneSourceLifecycleState?
        get() = delegate.liveState

    fun replace(uris: List<Uri>): LiveTuneProfileDocumentSelectionResult = delegate.replace(uris)

    fun clear(): LiveTuneProfileDocumentClearResult = delegate.clear()

    override fun close() {
        delegate.close()
    }
}
