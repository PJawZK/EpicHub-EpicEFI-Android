package com.epicefi.epichub.engine

import java.nio.ByteBuffer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeProfileRegistryTest {
    @Test
    fun compiled_profile_import_forwards_owned_definition_and_semantic_bindings() {
        val ops = FakeOps(createHandle = 41L, importResult = 0)
        val registry = NativeProfileRegistry(ops)
        val import =
            CompiledProfileImport(
                profileName = "generic-profile",
                profileGeneration = 700L,
                definitionFingerprint = "fingerprint",
                resolvedDefinition = "[TunerStudio]\nsignature = \"exact\"",
                semanticTables =
                    listOf(
                        SemanticTableImportBinding(42_700L, "tableA"),
                        SemanticTableImportBinding(0xffff_ffffL, "tableB"),
                    ),
            )

        assertEquals(CompiledProfileImportResult.Imported, registry.importCompiledProfile(import))
        assertEquals(41L, ops.lastImportHandle)
        assertEquals("generic-profile", ops.lastProfileName)
        assertEquals(700L, ops.lastProfileGeneration)
        assertEquals("fingerprint", ops.lastFingerprint)
        assertEquals(import.resolvedDefinition, ops.lastResolvedDefinition)
        assertArrayEquals(longArrayOf(42_700L, 0xffff_ffffL), ops.lastSemanticIds)
        assertArrayEquals(arrayOf("tableA", "tableB"), ops.lastEditorTableIds)
    }

    @Test
    fun definition_bundle_import_forwards_root_symbols_includes_and_bindings_deterministically() {
        val ops = FakeOps(createHandle = 51L, bundleImportResult = 0)
        val registry = NativeProfileRegistry(ops)
        val import =
            DefinitionBundleImport(
                profileName = "bundle-profile",
                profileGeneration = 800L,
                definitionFingerprint = "bundle-fingerprint",
                rootDefinition = "#include \"a.inc\"\n#include \"z.inc\"",
                enabledSymbols = setOf("ZETA", "ALPHA"),
                includes = mapOf("z.inc" to "z-content", "a.inc" to "a-content"),
                semanticTables =
                    listOf(
                        SemanticTableImportBinding(42_800L, "bundleTable"),
                        SemanticTableImportBinding(0xffff_ffffL, "maxTable"),
                    ),
            )

        assertEquals(DefinitionBundleImportResult.Imported, registry.importDefinitionBundle(import))
        assertEquals(51L, ops.lastBundleImportHandle)
        assertEquals("bundle-profile", ops.lastBundleProfileName)
        assertEquals(800L, ops.lastBundleProfileGeneration)
        assertEquals("bundle-fingerprint", ops.lastBundleFingerprint)
        assertEquals(import.rootDefinition, ops.lastRootDefinition)
        assertArrayEquals(arrayOf("ALPHA", "ZETA"), ops.lastEnabledSymbols)
        assertArrayEquals(arrayOf("a.inc", "z.inc"), ops.lastIncludeNames)
        assertArrayEquals(arrayOf("a-content", "z-content"), ops.lastIncludeContents)
        assertArrayEquals(longArrayOf(42_800L, 0xffff_ffffL), ops.lastBundleSemanticIds)
        assertArrayEquals(arrayOf("bundleTable", "maxTable"), ops.lastBundleEditorTableIds)
    }

    @Test
    fun import_statuses_remain_explicit_and_invalid_input_fails_before_native() {
        val cases =
            listOf(
                -1 to CompiledProfileImportRejection.InvalidHandle,
                -2 to CompiledProfileImportRejection.InvalidArgument,
                -3 to CompiledProfileImportRejection.BindingLengthMismatch,
                -4 to CompiledProfileImportRejection.CompileFailure,
                -5 to CompiledProfileImportRejection.RegistryFailure,
                -255 to CompiledProfileImportRejection.NativeFailure,
            )
        val valid =
            CompiledProfileImport(
                profileName = "profile",
                profileGeneration = 1L,
                definitionFingerprint = null,
                resolvedDefinition = "definition",
                semanticTables = emptyList(),
            )

        cases.forEach { (status, expected) ->
            val registry = NativeProfileRegistry(FakeOps(importResult = status))
            assertEquals(
                CompiledProfileImportResult.Rejected(expected),
                registry.importCompiledProfile(valid),
            )
        }

        val ops = FakeOps(importResult = 0)
        val registry = NativeProfileRegistry(ops)
        assertEquals(
            CompiledProfileImportResult.Rejected(CompiledProfileImportRejection.InvalidArgument),
            registry.importCompiledProfile(valid.copy(profileName = "")),
        )
        assertEquals(
            CompiledProfileImportResult.Rejected(CompiledProfileImportRejection.InvalidArgument),
            registry.importCompiledProfile(valid.copy(profileGeneration = 0L)),
        )
        assertEquals(
            CompiledProfileImportResult.Rejected(CompiledProfileImportRejection.InvalidArgument),
            registry.importCompiledProfile(
                valid.copy(
                    semanticTables = listOf(SemanticTableImportBinding(0x1_0000_0000L, "tooWide")),
                ),
            ),
        )
        assertEquals(0, ops.importCalls)
    }

    @Test
    fun definition_bundle_statuses_are_explicit_and_invalid_input_fails_before_native() {
        val cases =
            listOf(
                -1 to DefinitionBundleImportRejection.InvalidHandle,
                -2 to DefinitionBundleImportRejection.InvalidArgument,
                -3 to DefinitionBundleImportRejection.BindingLengthMismatch,
                -4 to DefinitionBundleImportRejection.CompileFailure,
                -5 to DefinitionBundleImportRejection.RegistryFailure,
                -6 to DefinitionBundleImportRejection.IncludeLengthMismatch,
                -255 to DefinitionBundleImportRejection.NativeFailure,
            )
        val valid =
            DefinitionBundleImport(
                profileName = "bundle",
                profileGeneration = 2L,
                definitionFingerprint = null,
                rootDefinition = "root",
                enabledSymbols = setOf("FEATURE"),
                includes = mapOf("constants.inc" to "content"),
                semanticTables = emptyList(),
            )

        cases.forEach { (status, expected) ->
            val registry = NativeProfileRegistry(FakeOps(bundleImportResult = status))
            assertEquals(
                DefinitionBundleImportResult.Rejected(expected),
                registry.importDefinitionBundle(valid),
            )
        }

        val ops = FakeOps(bundleImportResult = 0)
        val registry = NativeProfileRegistry(ops)
        assertEquals(
            DefinitionBundleImportResult.Rejected(DefinitionBundleImportRejection.InvalidArgument),
            registry.importDefinitionBundle(valid.copy(rootDefinition = "")),
        )
        assertEquals(
            DefinitionBundleImportResult.Rejected(DefinitionBundleImportRejection.InvalidArgument),
            registry.importDefinitionBundle(valid.copy(enabledSymbols = setOf(""))),
        )
        assertEquals(
            DefinitionBundleImportResult.Rejected(DefinitionBundleImportRejection.InvalidArgument),
            registry.importDefinitionBundle(valid.copy(includes = mapOf("" to "content"))),
        )
        assertEquals(
            DefinitionBundleImportResult.Rejected(DefinitionBundleImportRejection.InvalidArgument),
            registry.importDefinitionBundle(
                valid.copy(
                    semanticTables = listOf(SemanticTableImportBinding(0x1_0000_0000L, "tooWide")),
                ),
            ),
        )
        assertEquals(0, ops.bundleImportCalls)
    }

    @Test
    fun positive_native_result_returns_selected_profile_generation() {
        val ops = FakeOps(createHandle = 41L, selectionResult = 700L)
        val registry = NativeProfileRegistry(ops)

        val result = registry.select("rusEFI exact.signature".toByteArray(), 256)

        assertEquals(RuntimeProfileSelectionResult.Selected(700L), result)
        assertEquals(41L, ops.lastSelectionHandle)
        assertEquals(22, ops.lastResponseLength)
        assertEquals(256, ops.lastResponseLimit)
        assertTrue(ops.lastBufferDirect)
    }

    @Test
    fun authority_statuses_remain_distinct_and_fail_closed() {
        val cases =
            listOf(
                -1L to RuntimeProfileSelectionRejection.InvalidHandle,
                -2L to RuntimeProfileSelectionRejection.NotDirectBuffer,
                -3L to RuntimeProfileSelectionRejection.InvalidLength,
                -4L to RuntimeProfileSelectionRejection.InvalidResponseLimit,
                -5L to RuntimeProfileSelectionRejection.EmptySignature,
                -6L to RuntimeProfileSelectionRejection.InvalidUtf8,
                -7L to RuntimeProfileSelectionRejection.ResponseTooLarge,
                -8L to RuntimeProfileSelectionRejection.UnknownController,
                -9L to RuntimeProfileSelectionRejection.RecognizedControllerWithoutProfile,
                -10L to RuntimeProfileSelectionRejection.RegistryFailure,
                -255L to RuntimeProfileSelectionRejection.NativeFailure,
                0L to RuntimeProfileSelectionRejection.NativeFailure,
            )

        cases.forEach { (nativeResult, expected) ->
            val registry = NativeProfileRegistry(FakeOps(selectionResult = nativeResult))
            assertEquals(
                RuntimeProfileSelectionResult.Rejected(expected),
                registry.select("response".toByteArray(), 256),
            )
        }
    }

    @Test
    fun close_is_idempotent_and_prevents_future_selection_or_import() {
        val ops = FakeOps(createHandle = 77L, selectionResult = 123L)
        val registry = NativeProfileRegistry(ops)
        assertTrue(registry.isAlive)

        registry.close()
        registry.close()

        assertEquals(listOf(77L), ops.closedHandles)
        assertFalse(registry.isAlive)
        assertEquals(
            RuntimeProfileSelectionResult.Rejected(
                RuntimeProfileSelectionRejection.InvalidHandle,
            ),
            registry.select("response".toByteArray(), 256),
        )
        assertEquals(
            CompiledProfileImportResult.Rejected(CompiledProfileImportRejection.InvalidHandle),
            registry.importCompiledProfile(
                CompiledProfileImport("profile", 1, null, "definition", emptyList()),
            ),
        )
        assertEquals(
            DefinitionBundleImportResult.Rejected(DefinitionBundleImportRejection.InvalidHandle),
            registry.importDefinitionBundle(
                DefinitionBundleImport("bundle", 1, null, "root", semanticTables = emptyList()),
            ),
        )
        assertEquals(0, ops.selectionCalls)
        assertEquals(0, ops.importCalls)
        assertEquals(0, ops.bundleImportCalls)
    }

    private class FakeOps(
        private val createHandle: Long = 1L,
        private val selectionResult: Long = 1L,
        private val importResult: Int = 0,
        private val bundleImportResult: Int = 0,
    ) : NativeProfileRegistryOps {
        val closedHandles = mutableListOf<Long>()
        var selectionCalls = 0
        var importCalls = 0
        var bundleImportCalls = 0
        var lastSelectionHandle = -1L
        var lastResponseLength = -1
        var lastResponseLimit = -1
        var lastBufferDirect = false
        var lastImportHandle = -1L
        var lastProfileName: String? = null
        var lastProfileGeneration = -1L
        var lastFingerprint: String? = null
        var lastResolvedDefinition: String? = null
        var lastSemanticIds = longArrayOf()
        var lastEditorTableIds = arrayOf<String>()
        var lastBundleImportHandle = -1L
        var lastBundleProfileName: String? = null
        var lastBundleProfileGeneration = -1L
        var lastBundleFingerprint: String? = null
        var lastRootDefinition: String? = null
        var lastEnabledSymbols = arrayOf<String>()
        var lastIncludeNames = arrayOf<String>()
        var lastIncludeContents = arrayOf<String>()
        var lastBundleSemanticIds = longArrayOf()
        var lastBundleEditorTableIds = arrayOf<String>()

        override fun create(): Long = createHandle

        override fun close(handle: Long) {
            closedHandles += handle
        }

        override fun isAlive(handle: Long): Boolean = handle == createHandle

        override fun activeCount(): Int = if (closedHandles.isEmpty()) 1 else 0

        override fun registerCompiledProfile(
            handle: Long,
            profileName: String,
            profileGeneration: Long,
            definitionFingerprint: String?,
            resolvedDefinition: String,
            semanticIds: LongArray,
            editorTableIds: Array<String>,
        ): Int {
            importCalls += 1
            lastImportHandle = handle
            lastProfileName = profileName
            lastProfileGeneration = profileGeneration
            lastFingerprint = definitionFingerprint
            lastResolvedDefinition = resolvedDefinition
            lastSemanticIds = semanticIds.copyOf()
            lastEditorTableIds = editorTableIds.copyOf()
            return importResult
        }

        override fun registerDefinitionBundle(
            handle: Long,
            profileName: String,
            profileGeneration: Long,
            definitionFingerprint: String?,
            rootDefinition: String,
            enabledSymbols: Array<String>,
            includeNames: Array<String>,
            includeContents: Array<String>,
            semanticIds: LongArray,
            editorTableIds: Array<String>,
        ): Int {
            bundleImportCalls += 1
            lastBundleImportHandle = handle
            lastBundleProfileName = profileName
            lastBundleProfileGeneration = profileGeneration
            lastBundleFingerprint = definitionFingerprint
            lastRootDefinition = rootDefinition
            lastEnabledSymbols = enabledSymbols.copyOf()
            lastIncludeNames = includeNames.copyOf()
            lastIncludeContents = includeContents.copyOf()
            lastBundleSemanticIds = semanticIds.copyOf()
            lastBundleEditorTableIds = editorTableIds.copyOf()
            return bundleImportResult
        }

        override fun selectRuntimeProfileGeneration(
            handle: Long,
            buffer: ByteBuffer,
            responseLength: Int,
            maxResponseBytes: Int,
        ): Long {
            selectionCalls += 1
            lastSelectionHandle = handle
            lastResponseLength = responseLength
            lastResponseLimit = maxResponseBytes
            lastBufferDirect = buffer.isDirect
            return selectionResult
        }
    }
}
