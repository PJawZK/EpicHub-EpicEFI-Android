package com.epicefi.epichub.engine

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DefinitionBundlePackageTest {
    @Test
    fun decodesCompleteTargetNeutralPackage() {
        val packageBytes =
            profilePackage(
                manifest =
                    """
                    format.version=1
                    profile.name=generic-profile
                    profile.generation=42
                    profile.fingerprint=fingerprint-42
                    definition.root=definition/mainController.ini
                    symbols.count=2
                    symbol.0=LAMBDA
                    symbol.1=HUNDREDTH_VE
                    includes.count=1
                    include.0.name=inc/extra.inc
                    include.0.path=definition/includes/extra.inc
                    semantics.count=2
                    semantic.0.id=1001
                    semantic.0.table=veTable
                    semantic.1.id=4294967295
                    semantic.1.table=targetTable
                    """.trimIndent(),
                files =
                    mapOf(
                        "definition/mainController.ini" to
                            "[TunerStudio]\nsignature = \"controller.signature\"".encodeToByteArray(),
                        "definition/includes/extra.inc" to
                            "pageReadCommand = \"R%2i%2o%2c\"".encodeToByteArray(),
                    ),
            )

        val decoded = DefinitionBundlePackageDecoder.decode(packageBytes)

        assertEquals("generic-profile", decoded.profileName)
        assertEquals(42L, decoded.profileGeneration)
        assertEquals("fingerprint-42", decoded.definitionFingerprint)
        assertEquals(setOf("LAMBDA", "HUNDREDTH_VE"), decoded.enabledSymbols)
        assertEquals(
            "pageReadCommand = \"R%2i%2o%2c\"",
            decoded.includes.getValue("inc/extra.inc"),
        )
        assertEquals(
            listOf(
                SemanticTableImportBinding(1001L, "veTable"),
                SemanticTableImportBinding(0xffff_ffffL, "targetTable"),
            ),
            decoded.semanticTables,
        )
        assertTrue(decoded.rootDefinition.contains("controller.signature"))
    }

    @Test
    fun optionalFingerprintCanBeAbsent() {
        val decoded =
            DefinitionBundlePackageDecoder.decode(
                validPackage(profileName = "no-fingerprint", fingerprintLine = ""),
            )

        assertNull(decoded.definitionFingerprint)
    }

    @Test
    fun packageCatalogPreservesExplicitPackageOrder() {
        val catalog =
            DefinitionBundlePackageCatalog(
                listOf(
                    validPackage(profileName = "first", generation = 1),
                    validPackage(profileName = "second", generation = 2),
                ),
            )

        assertEquals(listOf("first", "second"), catalog.bundles().map { it.profileName })
    }

    @Test
    fun rejectsTraversalZipEntry() {
        val error =
            assertThrows(DefinitionBundlePackageException::class.java) {
                DefinitionBundlePackageDecoder.decode(
                    profilePackage(
                        manifest = minimalManifest(rootPath = "../mainController.ini"),
                        files = mapOf("../mainController.ini" to "root".encodeToByteArray()),
                    ),
                )
            }

        assertTrue(error.message.orEmpty().contains("Unsafe ZIP entry path"))
    }

    @Test
    fun rejectsMissingReferencedInclude() {
        val error =
            assertThrows(DefinitionBundlePackageException::class.java) {
                DefinitionBundlePackageDecoder.decode(
                    profilePackage(
                        manifest =
                            """
                            format.version=1
                            profile.name=missing-include
                            profile.generation=1
                            definition.root=mainController.ini
                            symbols.count=0
                            includes.count=1
                            include.0.name=inc/missing.inc
                            include.0.path=missing.inc
                            semantics.count=0
                            """.trimIndent(),
                        files = mapOf("mainController.ini" to "root".encodeToByteArray()),
                    ),
                )
            }

        assertTrue(error.message.orEmpty().contains("Referenced include entry is missing"))
    }

    @Test
    fun rejectsDuplicateManifestKey() {
        val manifest =
            minimalManifest() + "\nprofile.name=duplicate"

        val error =
            assertThrows(DefinitionBundlePackageException::class.java) {
                DefinitionBundlePackageDecoder.decode(
                    profilePackage(
                        manifest = manifest,
                        files = mapOf("mainController.ini" to "root".encodeToByteArray()),
                    ),
                )
            }

        assertTrue(error.message.orEmpty().contains("Duplicate or empty manifest key"))
    }

    @Test
    fun rejectsSparseOrUnsupportedIndexedManifestKey() {
        val manifest =
            minimalManifest() + "\nsymbol.0=UNDECLARED"

        val error =
            assertThrows(DefinitionBundlePackageException::class.java) {
                DefinitionBundlePackageDecoder.decode(
                    profilePackage(
                        manifest = manifest,
                        files = mapOf("mainController.ini" to "root".encodeToByteArray()),
                    ),
                )
            }

        assertTrue(error.message.orEmpty().contains("Unsupported or sparse manifest keys"))
    }

    @Test
    fun rejectsSemanticIdOutsideU32Range() {
        val error =
            assertThrows(DefinitionBundlePackageException::class.java) {
                DefinitionBundlePackageDecoder.decode(
                    profilePackage(
                        manifest =
                            """
                            format.version=1
                            profile.name=bad-semantic
                            profile.generation=1
                            definition.root=mainController.ini
                            symbols.count=0
                            includes.count=0
                            semantics.count=1
                            semantic.0.id=4294967296
                            semantic.0.table=table
                            """.trimIndent(),
                        files = mapOf("mainController.ini" to "root".encodeToByteArray()),
                    ),
                )
            }

        assertTrue(error.message.orEmpty().contains("outside the u32 range"))
    }

    @Test
    fun rejectsInvalidUtf8DefinitionContent() {
        val error =
            assertThrows(DefinitionBundlePackageException::class.java) {
                DefinitionBundlePackageDecoder.decode(
                    profilePackage(
                        manifest = minimalManifest(),
                        files = mapOf("mainController.ini" to byteArrayOf(0xc3.toByte(), 0x28)),
                    ),
                )
            }

        assertTrue(error.message.orEmpty().contains("not valid UTF-8"))
    }

    @Test
    fun rejectsUnreferencedPackageContent() {
        val error =
            assertThrows(DefinitionBundlePackageException::class.java) {
                DefinitionBundlePackageDecoder.decode(
                    profilePackage(
                        manifest = minimalManifest(),
                        files =
                            mapOf(
                                "mainController.ini" to "root".encodeToByteArray(),
                                "unused.txt" to "unused".encodeToByteArray(),
                            ),
                    ),
                )
            }

        assertTrue(error.message.orEmpty().contains("unreferenced entries"))
    }

    @Test
    fun malformedPackageFailsBeforeTransactionalRegistryCreation() {
        var registryFactoryCalls = 0
        val catalog =
            DefinitionBundlePackageCatalog(
                listOf(
                    profilePackage(
                        manifest = minimalManifest(rootPath = "missing.ini"),
                        files = emptyMap(),
                    ),
                ),
            )

        val result =
            DefinitionBundleRegistryOwner.openWithFactory(
                catalog = catalog,
                factory =
                    DefinitionBundleRegistryFactory {
                        registryFactoryCalls += 1
                        error("registry must not be created")
                    },
            )

        assertTrue(result is DefinitionBundleCatalogOpenResult.CatalogFailure)
        assertEquals(0, registryFactoryCalls)
    }

    private fun validPackage(
        profileName: String,
        generation: Long = 7,
        fingerprintLine: String = "profile.fingerprint=fingerprint-$profileName",
    ): ByteArray =
        profilePackage(
            manifest =
                """
                format.version=1
                profile.name=$profileName
                profile.generation=$generation
                $fingerprintLine
                definition.root=mainController.ini
                symbols.count=0
                includes.count=0
                semantics.count=1
                semantic.0.id=$generation
                semantic.0.table=table-$profileName
                """.trimIndent(),
            files =
                mapOf(
                    "mainController.ini" to
                        "[TunerStudio]\nsignature = \"signature-$profileName\"".encodeToByteArray(),
                ),
        )

    private fun minimalManifest(rootPath: String = "mainController.ini") =
        """
        format.version=1
        profile.name=minimal
        profile.generation=1
        definition.root=$rootPath
        symbols.count=0
        includes.count=0
        semantics.count=0
        """.trimIndent()

    private fun profilePackage(
        manifest: String,
        files: Map<String, ByteArray>,
    ): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            writeEntry(zip, "epichub-profile.properties", manifest.encodeToByteArray())
            files.forEach { (path, bytes) -> writeEntry(zip, path, bytes) }
        }
        return output.toByteArray()
    }

    private fun writeEntry(
        zip: ZipOutputStream,
        name: String,
        bytes: ByteArray,
    ) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(bytes)
        zip.closeEntry()
    }
}
