package com.epicefi.epichub.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DefinitionBundleCatalogTest {
    @Test
    fun successfulCatalogImportsAllBundlesInProviderOrder() {
        val registry = FakeRegistry()
        val bundles = listOf(bundle("first", 1L), bundle("second", 2L))

        val result =
            DefinitionBundleRegistryOwner.openWithFactory(
                catalog = DefinitionBundleCatalog { bundles },
                factory = DefinitionBundleRegistryFactory { registry },
            )

        val opened = result as DefinitionBundleCatalogOpenResult.Opened
        assertEquals(2, opened.importedBundleCount)
        assertEquals(listOf("first", "second"), registry.imported.map { it.profileName })
        assertEquals(listOf("first", "second"), opened.registry.definitionBundles().map { it.profileName })
        assertTrue(opened.registry.isAlive)
        assertFalse(registry.closed)

        opened.registry.close()
        assertTrue(registry.closed)
    }

    @Test
    fun rejectedBundleClosesCandidateAndNeverImportsLaterBundles() {
        val registry =
            FakeRegistry(
                results =
                    ArrayDeque(
                        listOf(
                            DefinitionBundleImportResult.Imported,
                            DefinitionBundleImportResult.Rejected(
                                DefinitionBundleImportRejection.CompileFailure,
                            ),
                        ),
                    ),
            )
        val bundles = listOf(bundle("first", 1L), bundle("bad", 2L), bundle("never", 3L))

        val result =
            DefinitionBundleRegistryOwner.openWithFactory(
                catalog = DefinitionBundleCatalog { bundles },
                factory = DefinitionBundleRegistryFactory { registry },
            )

        assertEquals(
            DefinitionBundleCatalogOpenResult.Rejected(
                bundleIndex = 1,
                reason = DefinitionBundleImportRejection.CompileFailure,
            ),
            result,
        )
        assertEquals(listOf("first", "bad"), registry.imported.map { it.profileName })
        assertTrue(registry.closed)
    }

    @Test
    fun catalogFailureDoesNotCreateRegistry() {
        var factoryCalls = 0

        val result =
            DefinitionBundleRegistryOwner.openWithFactory(
                catalog = DefinitionBundleCatalog { error("catalog unavailable") },
                factory =
                    DefinitionBundleRegistryFactory {
                        factoryCalls += 1
                        FakeRegistry()
                    },
            )

        assertEquals(
            DefinitionBundleCatalogOpenResult.CatalogFailure("catalog unavailable"),
            result,
        )
        assertEquals(0, factoryCalls)
    }

    @Test
    fun deadCandidateRegistryIsClosedAndNeverReceivesImports() {
        val registry = FakeRegistry(alive = false)

        val result =
            DefinitionBundleRegistryOwner.openWithFactory(
                catalog = DefinitionBundleCatalog { listOf(bundle("unused", 1L)) },
                factory = DefinitionBundleRegistryFactory { registry },
            )

        assertTrue(result is DefinitionBundleCatalogOpenResult.CatalogFailure)
        assertTrue(registry.closed)
        assertTrue(registry.imported.isEmpty())
    }

    @Test
    fun importExceptionClosesCandidateAndDoesNotExposePartialRegistry() {
        val registry = FakeRegistry(throwOnImportIndex = 1)
        val bundles = listOf(bundle("first", 1L), bundle("throws", 2L), bundle("never", 3L))

        val result =
            DefinitionBundleRegistryOwner.openWithFactory(
                catalog = DefinitionBundleCatalog { bundles },
                factory = DefinitionBundleRegistryFactory { registry },
            )

        assertEquals(
            DefinitionBundleCatalogOpenResult.CatalogFailure("import exploded"),
            result,
        )
        assertEquals(listOf("first", "throws"), registry.imported.map { it.profileName })
        assertTrue(registry.closed)
    }

    @Test
    fun emptyCatalogCreatesLiveRegistryWithNoFabricatedProfiles() {
        val registry = FakeRegistry()

        val result =
            DefinitionBundleRegistryOwner.openWithFactory(
                catalog = DefinitionBundleCatalog { emptyList() },
                factory = DefinitionBundleRegistryFactory { registry },
            )

        val opened = result as DefinitionBundleCatalogOpenResult.Opened
        assertEquals(0, opened.importedBundleCount)
        assertTrue(registry.imported.isEmpty())
        assertTrue(opened.registry.definitionBundles().isEmpty())
        assertTrue(opened.registry.isAlive)
    }

    private fun bundle(name: String, generation: Long) =
        DefinitionBundleImport(
            profileName = name,
            profileGeneration = generation,
            definitionFingerprint = "fingerprint-$name",
            rootDefinition = "[TunerStudio]\nsignature = \"signature-$name\"",
            semanticTables =
                listOf(
                    SemanticTableImportBinding(
                        semanticId = generation,
                        editorTableId = "table-$name",
                    ),
                ),
        )

    private class FakeRegistry(
        private val alive: Boolean = true,
        private val results: ArrayDeque<DefinitionBundleImportResult> = ArrayDeque(),
        private val throwOnImportIndex: Int? = null,
    ) : DefinitionBundleRegistry {
        val imported = mutableListOf<DefinitionBundleImport>()
        var closed = false
            private set

        override val isAlive: Boolean
            get() = alive && !closed

        override fun importDefinitionBundle(import: DefinitionBundleImport): DefinitionBundleImportResult {
            imported += import
            if (throwOnImportIndex == imported.lastIndex) {
                error("import exploded")
            }
            return if (results.isEmpty()) {
                DefinitionBundleImportResult.Imported
            } else {
                results.removeFirst()
            }
        }

        override fun close() {
            closed = true
        }
    }
}
