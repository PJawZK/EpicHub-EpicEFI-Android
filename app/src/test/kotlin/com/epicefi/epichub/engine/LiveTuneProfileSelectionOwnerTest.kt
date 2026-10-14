package com.epicefi.epichub.engine

import com.epicefi.epichub.platform.transport.usb.UsbReadOnlyTransactionChannel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveTuneProfileSelectionOwnerTest {
    @Test
    fun firstSuccessfulSelectionBecomesCurrentWithoutConnectingController() {
        val candidate = fakeConnectionOwner()
        val selection =
            LiveTuneProfileSelectionOwner(
                LiveTuneConnectionOwnerFactory {
                    LiveTuneConnectionOwnerOpenResult.Opened(candidate.owner, 2)
                },
            )

        val result = selection.replace(emptyCatalog())

        assertEquals(
            LiveTuneProfileSelectionResult.Selected(
                importedBundleCount = 2,
                replacedPreviousSelection = false,
            ),
            result,
        )
        assertTrue(selection.hasSelection)
        assertSame(candidate.owner, selection.connectionOwner)
        assertEquals(LiveTuneSourceLifecycleState.Disconnected(), selection.liveState)
        assertFalse(candidate.closed)
    }

    @Test
    fun successfulReplacementOpensCandidateBeforeClosingPrevious() {
        val first = fakeConnectionOwner()
        val second = fakeConnectionOwner()
        var openCount = 0
        var firstWasOpenWhenSecondOpened = false
        val selection =
            LiveTuneProfileSelectionOwner(
                LiveTuneConnectionOwnerFactory {
                    openCount += 1
                    if (openCount == 1) {
                        LiveTuneConnectionOwnerOpenResult.Opened(first.owner, 1)
                    } else {
                        firstWasOpenWhenSecondOpened = first.owner.isAlive
                        LiveTuneConnectionOwnerOpenResult.Opened(second.owner, 3)
                    }
                },
            )

        selection.replace(emptyCatalog())
        val result = selection.replace(emptyCatalog())

        assertTrue(firstWasOpenWhenSecondOpened)
        assertTrue(first.closed)
        assertFalse(second.closed)
        assertSame(second.owner, selection.connectionOwner)
        assertEquals(
            LiveTuneProfileSelectionResult.Selected(
                importedBundleCount = 3,
                replacedPreviousSelection = true,
            ),
            result,
        )
    }

    @Test
    fun rejectedReplacementPreservesPreviousSelection() {
        val first = fakeConnectionOwner()
        var call = 0
        val selection =
            LiveTuneProfileSelectionOwner(
                LiveTuneConnectionOwnerFactory {
                    call += 1
                    if (call == 1) {
                        LiveTuneConnectionOwnerOpenResult.Opened(first.owner, 1)
                    } else {
                        LiveTuneConnectionOwnerOpenResult.BundleRejected(
                            bundleIndex = 4,
                            reason = DefinitionBundleImportRejection.CompileFailure,
                        )
                    }
                },
            )
        selection.replace(emptyCatalog())

        val result = selection.replace(emptyCatalog())

        assertEquals(
            LiveTuneProfileSelectionResult.BundleRejected(
                bundleIndex = 4,
                reason = DefinitionBundleImportRejection.CompileFailure,
                previousSelectionPreserved = true,
            ),
            result,
        )
        assertSame(first.owner, selection.connectionOwner)
        assertFalse(first.closed)
    }

    @Test
    fun catalogFailurePreservesPreviousSelection() {
        val first = fakeConnectionOwner()
        var call = 0
        val selection =
            LiveTuneProfileSelectionOwner(
                LiveTuneConnectionOwnerFactory {
                    call += 1
                    if (call == 1) {
                        LiveTuneConnectionOwnerOpenResult.Opened(first.owner, 1)
                    } else {
                        LiveTuneConnectionOwnerOpenResult.CatalogFailure("bad package")
                    }
                },
            )
        selection.replace(emptyCatalog())

        val result = selection.replace(emptyCatalog())

        assertEquals(
            LiveTuneProfileSelectionResult.CatalogFailure(
                reason = "bad package",
                previousSelectionPreserved = true,
            ),
            result,
        )
        assertSame(first.owner, selection.connectionOwner)
        assertFalse(first.closed)
    }

    @Test
    fun deadCandidateIsClosedAndDoesNotReplacePreviousSelection() {
        val first = fakeConnectionOwner()
        val dead = fakeConnectionOwner(alive = false)
        var call = 0
        val selection =
            LiveTuneProfileSelectionOwner(
                LiveTuneConnectionOwnerFactory {
                    call += 1
                    if (call == 1) {
                        LiveTuneConnectionOwnerOpenResult.Opened(first.owner, 1)
                    } else {
                        LiveTuneConnectionOwnerOpenResult.Opened(dead.owner, 1)
                    }
                },
            )
        selection.replace(emptyCatalog())

        val result = selection.replace(emptyCatalog())

        assertTrue(dead.closed)
        assertFalse(first.closed)
        assertSame(first.owner, selection.connectionOwner)
        assertEquals(
            LiveTuneProfileSelectionResult.CatalogFailure(
                reason = "Selected profile catalog did not produce a live connection owner.",
                previousSelectionPreserved = true,
            ),
            result,
        )
    }

    @Test
    fun clearClosesCurrentSelectionAndLeavesNoAuthority() {
        val candidate = fakeConnectionOwner()
        val selection =
            LiveTuneProfileSelectionOwner(
                LiveTuneConnectionOwnerFactory {
                    LiveTuneConnectionOwnerOpenResult.Opened(candidate.owner, 1)
                },
            )
        selection.replace(emptyCatalog())

        assertTrue(selection.clear())

        assertTrue(candidate.closed)
        assertFalse(selection.hasSelection)
        assertNull(selection.connectionOwner)
        assertNull(selection.liveState)
        assertFalse(selection.clear())
    }

    @Test
    fun closeIsIdempotentAndRejectsFutureSelection() {
        val candidate = fakeConnectionOwner()
        var factoryCalls = 0
        val selection =
            LiveTuneProfileSelectionOwner(
                LiveTuneConnectionOwnerFactory {
                    factoryCalls += 1
                    LiveTuneConnectionOwnerOpenResult.Opened(candidate.owner, 1)
                },
            )
        selection.replace(emptyCatalog())

        selection.close()
        selection.close()
        val result = selection.replace(emptyCatalog())

        assertTrue(candidate.closed)
        assertTrue(selection.isClosed)
        assertFalse(selection.hasSelection)
        assertNull(selection.connectionOwner)
        assertEquals(LiveTuneProfileSelectionResult.OwnerClosed, result)
        assertEquals(1, factoryCalls)
    }

    private fun emptyCatalog() = DefinitionBundleCatalog { emptyList() }

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

    private fun fakeConnectionOwner(alive: Boolean = true): FakeConnection {
        val closeState = CloseState()
        val lifecycle = FakeLifecycle()
        val owner =
            LiveTuneConnectionOwner(
                registryOwner = AutoCloseable { closeState.closed = true },
                registryAlive = { alive && !closeState.closed },
                lifecycle = lifecycle,
                identityProbe = RuntimeIdentityProbeOperation { _, _ ->
                    error("Profile selection must not probe a controller.")
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
        ): UsbReadOnlyTuneLoadResult? = error("Profile selection must not load a controller.")
    }
}
