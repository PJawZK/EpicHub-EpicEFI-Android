package com.epicefi.epichub.engine

import java.nio.ByteBuffer

private const val MAX_DEFINITION_PROBE_QUERY_BYTES = 1024
private const val PROBE_QUERY_NOT_DIRECT = -1
private const val PROBE_QUERY_INVALID_ARGUMENT = -2
private const val PROBE_QUERY_BINDING_LENGTH_MISMATCH = -3
private const val PROBE_QUERY_INCLUDE_LENGTH_MISMATCH = -4
private const val PROBE_QUERY_COMPILE_ERROR = -5
private const val PROBE_QUERY_INVALID_QUERY = -6

enum class DefinitionProbeQueryRejection {
    NotDirectBuffer,
    InvalidArgument,
    BindingLengthMismatch,
    IncludeLengthMismatch,
    CompileFailure,
    InvalidQuery,
    NativeFailure,
}

sealed interface DefinitionProbeQueryCompileResult {
    data class Compiled(
        val query: ByteArray,
    ) : DefinitionProbeQueryCompileResult

    data class Rejected(
        val reason: DefinitionProbeQueryRejection,
    ) : DefinitionProbeQueryCompileResult
}

internal fun interface DefinitionProbePlanNativeOps {
    fun compileDefinitionProbeQuery(
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
    ): Int
}

class DefinitionDrivenRuntimeProbePlanCompiler internal constructor(
    private val ops: DefinitionProbePlanNativeOps,
) {
    constructor() : this(
        DefinitionProbePlanNativeOps { profileName,
            profileGeneration,
            definitionFingerprint,
            rootDefinition,
            enabledSymbols,
            includeNames,
            includeContents,
            semanticIds,
            editorTableIds,
            output ->
            NativeDefinitionProbePlanBridge.compileDefinitionProbeQuery(
                profileName,
                profileGeneration,
                definitionFingerprint,
                rootDefinition,
                enabledSymbols,
                includeNames,
                includeContents,
                semanticIds,
                editorTableIds,
                output,
            )
        },
    )

    fun compile(import: DefinitionBundleImport): DefinitionProbeQueryCompileResult {
        if (
            import.profileName.isEmpty() ||
            import.profileGeneration <= 0L ||
            import.rootDefinition.isEmpty() ||
            import.enabledSymbols.any(String::isEmpty) ||
            import.includes.keys.any(String::isEmpty) ||
            import.semanticTables.any {
                it.semanticId < 0L ||
                    it.semanticId > 0xffff_ffffL ||
                    it.editorTableId.isEmpty()
            }
        ) {
            return rejected(DefinitionProbeQueryRejection.InvalidArgument)
        }

        val enabledSymbols = import.enabledSymbols.sorted().toTypedArray()
        val includeEntries = import.includes.entries.sortedBy { it.key }
        val includeNames = Array(includeEntries.size) { includeEntries[it].key }
        val includeContents = Array(includeEntries.size) { includeEntries[it].value }
        val semanticIds = LongArray(import.semanticTables.size) { import.semanticTables[it].semanticId }
        val editorTableIds = Array(import.semanticTables.size) { import.semanticTables[it].editorTableId }
        val output = ByteBuffer.allocateDirect(MAX_DEFINITION_PROBE_QUERY_BYTES)

        val result =
            ops.compileDefinitionProbeQuery(
                import.profileName,
                import.profileGeneration,
                import.definitionFingerprint,
                import.rootDefinition,
                enabledSymbols,
                includeNames,
                includeContents,
                semanticIds,
                editorTableIds,
                output,
            )
        if (result > 0 && result <= output.capacity()) {
            val query = ByteArray(result)
            output.rewind()
            output.get(query)
            return DefinitionProbeQueryCompileResult.Compiled(query)
        }

        return when (result) {
            PROBE_QUERY_NOT_DIRECT -> rejected(DefinitionProbeQueryRejection.NotDirectBuffer)
            PROBE_QUERY_INVALID_ARGUMENT -> rejected(DefinitionProbeQueryRejection.InvalidArgument)
            PROBE_QUERY_BINDING_LENGTH_MISMATCH ->
                rejected(DefinitionProbeQueryRejection.BindingLengthMismatch)
            PROBE_QUERY_INCLUDE_LENGTH_MISMATCH ->
                rejected(DefinitionProbeQueryRejection.IncludeLengthMismatch)
            PROBE_QUERY_COMPILE_ERROR -> rejected(DefinitionProbeQueryRejection.CompileFailure)
            PROBE_QUERY_INVALID_QUERY -> rejected(DefinitionProbeQueryRejection.InvalidQuery)
            else -> rejected(DefinitionProbeQueryRejection.NativeFailure)
        }
    }

    private fun rejected(reason: DefinitionProbeQueryRejection) =
        DefinitionProbeQueryCompileResult.Rejected(reason)
}

object NativeDefinitionProbePlanBridge {
    init {
        System.loadLibrary("epichub_engine")
    }

    external fun compileDefinitionProbeQuery(
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
    ): Int
}
