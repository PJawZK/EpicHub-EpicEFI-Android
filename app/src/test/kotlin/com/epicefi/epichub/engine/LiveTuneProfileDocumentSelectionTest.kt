package com.epicefi.epichub.engine

import com.epicefi.epichub.platform.transport.usb.UsbReadOnlyTransactionChannel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveTuneProfileDocumentSelectionTest {
    @Test
    fun firstSelectionPersistsDocumentsBeforeOpeningProfileAuthority() {
        val events = mutableListOf<String>()
        val grantStore = RecordingGrantStore(events)
        val connection = fakeConnectionOwner(events, "first")
        val owner =
            selectionOwner(
                events = events,
                grantStore = grantStore,
                openings =
                    ArrayDeque(
                        listOf(
                            LiveTuneConnectionOwnerOpenResult.Opened(connection.owner, 2),
                        ),
                    ),
            )

        val result = owner.replace(listOf("a", "b"))

        assertEquals(
            LiveTuneProfileDocumentSelectionResult.Selected(
                importedBundleCount = 2,
                replacedPreviousSelection = false,
            ),
            result,
        )
        assertEquals(
            listOf("persist:a", "persist:b", "catalog:a,b", "open-profile"),
            events,
        )
        assertEquals(listOf("a", "b"), owner.selectedDocuments)
        assertSame(connection.owner, owner.connectionOwner)
        assertEquals(LiveTuneSourceLifecycleState.Disconnected(), owner.liveState)
    }

    @Test
    fun replacementAcquiresOnlyNewGrantThenReleasesOnlyObsoleteGrantAfterSwap() {
        val events = mutableListOf<String>()
        val grantStore = RecordingGrantStore(events)
        val first = fakeConnectionOwner(events, "first")
        val second = fakeConnectionOwner(events, "second")
        val owner =
            selectionOwner(
                events = events,
                grantStore = grantStore,
                openings =
                    ArrayDeque(
                        listOf(
                            LiveTuneConnectionOwnerOpenResult.Opened(first.owner, 1),
                            LiveTuneConnectionOwnerOpenResult.Opened(second.owner, 3),
                        ),
                    ),
            )
        owner.replace(listOf("a", "b"))
        events.clear()

        val result = owner.replace(listOf("b", "c"))

        assertEquals(
            LiveTuneProfileDocumentSelectionResult.Selected(
                importedBundleCount = 3,
                replacedPreviousSelection = true,
            ),
            result,
        )
        assertEquals(
            listOf(
                "persist:c",
                "catalog:b,c",
                "open-profile",
                "close:first",
                "release:a",
            ),
            events,
        )
        assertEquals(listOf("b", "c"), owner.selectedDocuments)
        assertTrue(first.closed)
        assertFalse(second.closed)
    }

    @Test
    fun permissionFailureRollsBackOnlyGrantsFromCurrentAttemptAndPreservesPreviousSelection() {
        val events = mutableListOf<String>()
        val grantStore = RecordingGrantStore(events, persistFailures = mapOf("d" to "grant denied"))
        val first = fakeConnectionOwner(events, "first")
        val owner =
            selectionOwner(
                events = events,
                grantStore = grantStore,
                openings =
                    ArrayDeque(
                        listOf(LiveTuneConnectionOwnerOpenResult.Opened(first.owner, 1)),
                    ),
            )
        owner.replace(listOf("a", "b"))
        events.clear()

        val result = owner.replace(listOf("b", "c", "d"))

        assertEquals(
            LiveTuneProfileDocumentSelectionResult.PermissionRejected(
                documentIndex = 2,
                reason = "grant denied",
                previousSelectionPreserved = true,
            ),
            result,
        )
        assertEquals(listOf("persist:c", "persist:d", "release:c"), events)
        assertEquals(listOf("a", "b"), owner.selectedDocuments)
        assertSame(first.owner, owner.connectionOwner)
        assertFalse(first.closed)
    }

    @Test
    fun profileRejectionRollsBackNewGrantsAndPreservesPreviousDocuments() {
        val events = mutableListOf<String>()
        val grantStore = RecordingGrantStore(events)
        val first = fakeConnectionOwner(events, "first")
        val owner =
            selectionOwner(
                events = events,
                grantStore = grantStore,
                openings =
                    ArrayDeque(
                        listOf(
                            LiveTuneConnectionOwnerOpenResult.Opened(first.owner, 1),
                            LiveTuneConnectionOwnerOpenResult.BundleRejected(
                                0,
                                DefinitionBundleImportRejection.CompileFailure,
                            ),
                        ),
                    ),
            )
        owner.replace(listOf("a"))
        events.clear()

        val result = owner.replace(listOf("a", "c"))

        assertTrue(result is LiveTuneProfileDocumentSelectionResult.ProfileRejected)
        val rejected = result as LiveTuneProfileDocumentSelectionResult.ProfileRejected
        assertTrue(rejected.previousSelectionPreserved)
        assertTrue(rejected.rollbackReleaseFailures.isEmpty())
        assertTrue(rejected.result is LiveTuneProfileSelectionResult.BundleRejected)
        assertEquals(
            listOf("persist:c", "catalog:a,c", "open-profile", "release:c"),
            events,
        )
        assertEquals(listOf("a"), owner.selectedDocuments)
        assertSame(first.owner, owner.connectionOwner)
        assertFalse(first.closed)
    }

    @Test
    fun successfulReplacementReportsObsoleteGrantReleaseFailureWithoutRevertingAuthority() {
        val events = mutableListOf<String>()
        val grantStore = RecordingGrantStore(events, releaseFailures = mapOf("a" to "release denied"))
        val first = fakeConnectionOwner(events, "first")
        val second = fakeConnectionOwner(events, "second")
        val owner =
            selectionOwner(
                events = events,
                grantStore = grantStore,
                openings =
                    ArrayDeque(
                        listOf(
                            LiveTuneConnectionOwnerOpenResult.Opened(first.owner, 1),
                            LiveTuneConnectionOwnerOpenResult.Opened(second.owner, 1),
                        ),
                    ),
            )
        owner.replace(listOf("a"))
        events.clear()

        val result = owner.replace(listOf("b"))

        assertEquals(
            LiveTuneProfileDocumentSelectionResult.Selected(
                importedBundleCount = 1,
                replacedPreviousSelection = true,
                obsoleteGrantReleaseFailures = listOf("release denied"),
            ),
            result,
        )
        assertEquals(listOf("b"), owner.selectedDocuments)
        assertSame(second.owner, owner.connectionOwner)
        assertTrue(first.closed)
    }

    @Test
    fun duplicateAndEmptySelectionsFailBeforePermissionsOrProfileOpen() {
        val events = mutableListOf<String>()
        val owner =
            selectionOwner(
                events = events,
                grantStore = RecordingGrantStore(events),
                openings = ArrayDeque(),
            )

        val empty = owner.replace(emptyList())
        val duplicate = owner.replace(listOf("a", "a"))

        assertTrue(empty is LiveTuneProfileDocumentSelectionResult.InvalidSelection)
        assertTrue(duplicate is LiveTuneProfileDocumentSelectionResult.InvalidSelection)
        assertTrue(events.isEmpty())
        assertTrue(owner.selectedDocuments.isEmpty())
    }

    @Test
    fun clearClosesProfileAuthorityBeforeReleasingSelectedGrants() {
        val events = mutableListOf<String>()
        val connection = fakeConnectionOwner(events, "current")
        val owner =
            selectionOwner(
                events = events,
                grantStore = RecordingGrantStore(events),
                openings =
                    ArrayDeque(
                        listOf(LiveTuneConnectionOwnerOpenResult.Opened(connection.owner, 1)),
                    ),
            )
        owner.replace(listOf("a", "b"))
        events.clear()

        val result = owner.clear()

        assertEquals(LiveTuneProfileDocumentClearResult(true, emptyList()), result)
        assertEquals(listOf("close:current", "release:a", "release:b"), events)
        assertTrue(owner.selectedDocuments.isEmpty())
        assertTrue(connection.closed)
    }

    @Test
    fun closeIsIdempotentAndReleasesCurrentDocumentsOnce() {
        val events = mutableListOf<String>()
        val connection = fakeConnectionOwner(events, "current")
        val owner =
            selectionOwner(
                events = events,
                grantStore = RecordingGrantStore(events),
                openings =
                    ArrayDeque(
                        listOf(LiveTuneConnectionOwnerOpenResult.Opened(connection.owner, 1)),
                    ),
            )
        owner.replace(listOf("a"))
        events.clear()

        owner.close()
        owner.close()
        val result = owner.replace(listOf("b"))

        assertEquals(listOf("close:current", "release:a"), events)
        assertTrue(owner.isClosed)
        assertEquals(LiveTuneProfileDocumentSelectionResult.OwnerClosed, result)
    }

    private fun selectionOwner(
        events: MutableList<String>,
        grantStore: PersistedProfileDocumentGrantStore<String>,
        openings: ArrayDeque<LiveTuneConnectionOwnerOpenResult>,
    ): LiveTuneProfileDocumentSelectionOwner<String> {
        val profileSelection =
            LiveTuneProfileSelectionOwner(
                LiveTuneConnectionOwnerFactory {
                    events += "open-profile"
                    openings.removeFirst()
                },
            )
        return LiveTuneProfileDocumentSelectionOwner(
            profileSelection = profileSelection,
            grantStore = grantStore,
            catalogFactory =
                ProfileDocumentCatalogFactory { documents ->
                    events += "catalog:${documents.joinToString(",")}"
                    DefinitionBundleCatalog { emptyList() }
                },
        )
    }

    private class RecordingGrantStore(
        private val events: MutableList<String>,
        private val persistFailures: Map<String, String> = emptyMap(),
        private val releaseFailures: Map<String, String> = emptyMap(),
    ) : PersistedProfileDocumentGrantStore<String> {
        override fun persistRead(document: String): String? {
            events += "persist:$document"
            return persistFailures[document]
        }

        override fun releaseRead(document: String): String? {
            events += "release:$document"
            return releaseFailures[document]
        }
    }

    private data class FakeConnection(
        val owner: LiveTuneConnectionOwner,
        val closeState: CloseState,
    ) {
        val closed: Boolean
            get() = closeState.closed
    }

    private class CloseState {
        var closed = false
    }

    private fun fakeConnectionOwner(
        events: MutableList<String>,
        name: String,
    ): FakeConnection {
        val closeState = CloseState()
        val lifecycle = FakeLifecycle()
        val owner =
            LiveTuneConnectionOwner(
                registryOwner =
                    AutoCloseable {
                        events += "close:$name"
                        closeState.closed = true
                    },
                registryAlive = { !closeState.closed },
                lifecycle = lifecycle,
                identityProbe = RuntimeIdentityProbeOperation { _, _ ->
                    error("Document/profile selection must not probe a controller.")
                },
            )
        return FakeConnection(owner, closeState)
    }

    private class FakeLifecycle : LiveTuneLifecycleControl {
        override var state: LiveTuneSourceLifecycleState = LiveTuneSourceLifecycleState.Disconnected()
            private set

        override fun onConnected(identity: ValidatedRuntimeIdentity) {
            state = LiveTuneSourceLifecycleState.Connected(identity)
        }

        override fun onDisconnected() {
            state = LiveTuneSourceLifecycleState.Disconnected()
        }

        override fun loadCurrentController(
            signatureLimit: Int,
            channel: UsbReadOnlyTransactionChannel,
            timeoutMs: Int,
        ): UsbReadOnlyTuneLoadResult? = error("Document/profile selection must not load a controller.")
    }
}
