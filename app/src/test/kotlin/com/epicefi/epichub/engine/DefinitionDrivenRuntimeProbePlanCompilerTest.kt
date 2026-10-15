package com.epicefi.epichub.engine

import java.nio.ByteBuffer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DefinitionDrivenRuntimeProbePlanCompilerTest {
    @Test
    fun compilesQueryAndMarshalsDefinitionInputsDeterministically() {
        val ops = FakeOps(result = 1, output = byteArrayOf('S'.code.toByte()))
        val compiler = DefinitionDrivenRuntimeProbePlanCompiler(ops)
        val import =
            DefinitionBundleImport(
                profileName = "generic",
                profileGeneration = 42,
                definitionFingerprint = "fingerprint",
                rootDefinition = "root",
                enabledSymbols = linkedSetOf("ZETA", "ALPHA"),
                includes = linkedMapOf("z.inc" to "z", "a.inc" to "a"),
                semanticTables =
                    listOf(
                        SemanticTableImportBinding(0xffff_ffffL, "table-z"),
                        SemanticTableImportBinding(7L, "table-a"),
                    ),
            )

        val result = compiler.compile(import)

        assertTrue(result is DefinitionProbeQueryCompileResult.Compiled)
        assertArrayEquals(
            byteArrayOf('S'.code.toByte()),
            (result as DefinitionProbeQueryCompileResult.Compiled).query,
        )
        assertEquals(listOf("ALPHA", "ZETA"), ops.enabledSymbols?.toList())
        assertEquals(listOf("a.inc", "z.inc"), ops.includeNames?.toList())
        assertEquals(listOf("a", "z"), ops.includeContents?.toList())
        assertEquals(listOf(0xffff_ffffL, 7L), ops.semanticIds?.toList())
        assertEquals(listOf("table-z", "table-a"), ops.editorTableIds?.toList())
    }

    @Test
    fun invalidArgumentsFailBeforeNativeCall() {
        val ops = FakeOps(result = 1, output = byteArrayOf('S'.code.toByte()))
        val compiler = DefinitionDrivenRuntimeProbePlanCompiler(ops)

        val result =
            compiler.compile(
                DefinitionBundleImport(
                    profileName = "",
                    profileGeneration = 42,
                    definitionFingerprint = null,
                    rootDefinition = "root",
                    semanticTables = emptyList(),
                ),
            )

        assertEquals(
            DefinitionProbeQueryCompileResult.Rejected(DefinitionProbeQueryRejection.InvalidArgument),
            result,
        )
        assertEquals(0, ops.calls)
    }

    @Test
    fun nativeStatusesRemainExplicit() {
        val import = validImport()
        val cases =
            listOf(
                -1 to DefinitionProbeQueryRejection.NotDirectBuffer,
                -2 to DefinitionProbeQueryRejection.InvalidArgument,
                -3 to DefinitionProbeQueryRejection.BindingLengthMismatch,
                -4 to DefinitionProbeQueryRejection.IncludeLengthMismatch,
                -5 to DefinitionProbeQueryRejection.CompileFailure,
                -6 to DefinitionProbeQueryRejection.InvalidQuery,
                -255 to DefinitionProbeQueryRejection.NativeFailure,
            )

        cases.forEach { (status, expected) ->
            val result = DefinitionDrivenRuntimeProbePlanCompiler(FakeOps(status)).compile(import)
            assertEquals(DefinitionProbeQueryCompileResult.Rejected(expected), result)
        }
    }

    @Test
    fun invalidPositiveNativeLengthFailsClosed() {
        val result =
            DefinitionDrivenRuntimeProbePlanCompiler(FakeOps(2048))
                .compile(validImport())

        assertEquals(
            DefinitionProbeQueryCompileResult.Rejected(DefinitionProbeQueryRejection.NativeFailure),
            result,
        )
    }

    private fun validImport() =
        DefinitionBundleImport(
            profileName = "generic",
            profileGeneration = 42,
            definitionFingerprint = null,
            rootDefinition = "root",
            semanticTables = emptyList(),
        )

    private class FakeOps(
        private val result: Int,
        private val output: ByteArray = byteArrayOf(),
    ) : DefinitionProbePlanNativeOps {
        var calls = 0
            private set
        var enabledSymbols: Array<String>? = null
            private set
        var includeNames: Array<String>? = null
            private set
        var includeContents: Array<String>? = null
            private set
        var semanticIds: LongArray? = null
            private set
        var editorTableIds: Array<String>? = null
            private set

        override fun compileDefinitionProbeQuery(
            profileName: String,
            profileGeneration: Long,
            definitionFingerprint: String?,
            rootDefinition: String,
            enabledSymbols: Array<String>,
            includeNames: Array<String>,
            includeContents: Array<String>,
            semanticIds: LongArray,
            editorTableIds: Array<String>,
            output: ByteBuffer,
        ): Int {
            calls += 1
            this.enabledSymbols = enabledSymbols
            this.includeNames = includeNames
            this.includeContents = includeContents
            this.semanticIds = semanticIds
            this.editorTableIds = editorTableIds
            if (result > 0 && this.output.isNotEmpty()) {
                output.put(this.output)
                output.rewind()
            }
            return result
        }
    }
}
