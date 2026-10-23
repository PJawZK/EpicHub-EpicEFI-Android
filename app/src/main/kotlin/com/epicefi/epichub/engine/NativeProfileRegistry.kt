package com.epicefi.epichub.engine

import java.nio.ByteBuffer

private const val PROFILE_RESULT_INVALID_HANDLE = -1L
private const val PROFILE_RESULT_NOT_DIRECT = -2L
private const val PROFILE_RESULT_INVALID_LENGTH = -3L
private const val PROFILE_RESULT_INVALID_LIMIT = -4L
private const val PROFILE_RESULT_EMPTY_SIGNATURE = -5L
private const val PROFILE_RESULT_INVALID_UTF8 = -6L
private const val PROFILE_RESULT_TOO_LARGE = -7L
private const val PROFILE_RESULT_UNKNOWN_CONTROLLER = -8L
private const val PROFILE_RESULT_RECOGNIZED_NO_PROFILE = -9L
private const val PROFILE_RESULT_REGISTRY_ERROR = -10L

private const val PROFILE_IMPORT_OK = 0
private const val PROFILE_IMPORT_INVALID_HANDLE = -1
private const val PROFILE_IMPORT_INVALID_ARGUMENT = -2
private const val PROFILE_IMPORT_BINDING_LENGTH_MISMATCH = -3
private const val PROFILE_IMPORT_COMPILE_ERROR = -4
private const val PROFILE_IMPORT_REGISTRY_ERROR = -5
private const val PROFILE_IMPORT_INCLUDE_LENGTH_MISMATCH = -6

data class SemanticTableImportBinding(
    val semanticId: Long,
    val editorTableId: String,
)

data class CompiledProfileImport(
    val profileName: String,
    val profileGeneration: Long,
    val definitionFingerprint: String?,
    val resolvedDefinition: String,
    val semanticTables: List<SemanticTableImportBinding>,
)

data class DefinitionBundleImport(
    val profileName: String,
    val profileGeneration: Long,
    val definitionFingerprint: String?,
    val rootDefinition: String,
    val enabledSymbols: Set<String> = emptySet(),
    val includes: Map<String, String> = emptyMap(),
    val semanticTables: List<SemanticTableImportBinding>,
)

enum class CompiledProfileImportRejection {
    InvalidHandle,
    InvalidArgument,
    BindingLengthMismatch,
    CompileFailure,
    RegistryFailure,
    NativeFailure,
}

sealed interface CompiledProfileImportResult {
    data object Imported : CompiledProfileImportResult

    data class Rejected(
        val reason: CompiledProfileImportRejection,
    ) : CompiledProfileImportResult
}

enum class DefinitionBundleImportRejection {
    InvalidHandle,
    InvalidArgument,
    BindingLengthMismatch,
    IncludeLengthMismatch,
    CompileFailure,
    RegistryFailure,
    NativeFailure,
}

sealed interface DefinitionBundleImportResult {
    data object Imported : DefinitionBundleImportResult

    data class Rejected(
        val reason: DefinitionBundleImportRejection,
    ) : DefinitionBundleImportResult
}

enum class RuntimeProfileSelectionRejection {
    InvalidHandle,
    NotDirectBuffer,
    InvalidLength,
    InvalidResponseLimit,
    EmptySignature,
    InvalidUtf8,
    ResponseTooLarge,
    UnknownController,
    RecognizedControllerWithoutProfile,
    RegistryFailure,
    NativeFailure,
}

sealed interface RuntimeProfileSelectionResult {
    data class Selected(
        val profileGeneration: Long,
    ) : RuntimeProfileSelectionResult

    data class Rejected(
        val reason: RuntimeProfileSelectionRejection,
    ) : RuntimeProfileSelectionResult
}

internal interface NativeProfileRegistryOps {
    fun create(): Long

    fun close(handle: Long)

    fun isAlive(handle: Long): Boolean

    fun activeCount(): Int

    fun registerCompiledProfile(
        handle: Long,
        profileName: String,
        profileGeneration: Long,
        definitionFingerprint: String?,
        resolvedDefinition: String,
        semanticIds: LongArray,
        editorTableIds: Array<String>,
    ): Int

    fun registerDefinitionBundle(
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
    ): Int

    fun selectRuntimeProfileGeneration(
        handle: Long,
        buffer: ByteBuffer,
        responseLength: Int,
        maxResponseBytes: Int,
    ): Long
}

class NativeProfileRegistry internal constructor(
    private val ops: NativeProfileRegistryOps,
) : AutoCloseable {
    constructor() : this(JniNativeProfileRegistryOps)

    private var handle: Long = ops.create()

    val isAlive: Boolean
        get() = handle != 0L && ops.isAlive(handle)

    fun importCompiledProfile(import: CompiledProfileImport): CompiledProfileImportResult =
        EngineAuthoritySerialization.exclusive {
            importCompiledProfileSerialized(import)
        }

    private fun importCompiledProfileSerialized(
        import: CompiledProfileImport,
    ): CompiledProfileImportResult {
        val currentHandle = handle
        if (currentHandle == 0L) {
            return CompiledProfileImportResult.Rejected(CompiledProfileImportRejection.InvalidHandle)
        }
        if (!validCommonImportArguments(
                import.profileName,
                import.profileGeneration,
                import.resolvedDefinition,
                import.semanticTables,
            )
        ) {
            return CompiledProfileImportResult.Rejected(CompiledProfileImportRejection.InvalidArgument)
        }

        val semanticIds = LongArray(import.semanticTables.size) { import.semanticTables[it].semanticId }
        val editorTableIds = Array(import.semanticTables.size) { import.semanticTables[it].editorTableId }
        return when (
            ops.registerCompiledProfile(
                currentHandle,
                import.profileName,
                import.profileGeneration,
                import.definitionFingerprint,
                import.resolvedDefinition,
                semanticIds,
                editorTableIds,
            )
        ) {
            PROFILE_IMPORT_OK -> CompiledProfileImportResult.Imported
            PROFILE_IMPORT_INVALID_HANDLE -> rejectedImport(CompiledProfileImportRejection.InvalidHandle)
            PROFILE_IMPORT_INVALID_ARGUMENT -> rejectedImport(CompiledProfileImportRejection.InvalidArgument)
            PROFILE_IMPORT_BINDING_LENGTH_MISMATCH -> rejectedImport(CompiledProfileImportRejection.BindingLengthMismatch)
            PROFILE_IMPORT_COMPILE_ERROR -> rejectedImport(CompiledProfileImportRejection.CompileFailure)
            PROFILE_IMPORT_REGISTRY_ERROR -> rejectedImport(CompiledProfileImportRejection.RegistryFailure)
            else -> rejectedImport(CompiledProfileImportRejection.NativeFailure)
        }
    }

    fun importDefinitionBundle(import: DefinitionBundleImport): DefinitionBundleImportResult =
        EngineAuthoritySerialization.exclusive {
            importDefinitionBundleSerialized(import)
        }

    private fun importDefinitionBundleSerialized(
        import: DefinitionBundleImport,
    ): DefinitionBundleImportResult {
        val currentHandle = handle
        if (currentHandle == 0L) {
            return rejectedBundleImport(DefinitionBundleImportRejection.InvalidHandle)
        }
        if (
            !validCommonImportArguments(
                import.profileName,
                import.profileGeneration,
                import.rootDefinition,
                import.semanticTables,
            ) ||
                import.enabledSymbols.any(String::isEmpty) ||
                import.includes.keys.any(String::isEmpty)
        ) {
            return rejectedBundleImport(DefinitionBundleImportRejection.InvalidArgument)
        }

        val enabledSymbols = import.enabledSymbols.sorted().toTypedArray()
        val includeEntries = import.includes.entries.sortedBy { it.key }
        val includeNames = Array(includeEntries.size) { includeEntries[it].key }
        val includeContents = Array(includeEntries.size) { includeEntries[it].value }
        val semanticIds = LongArray(import.semanticTables.size) { import.semanticTables[it].semanticId }
        val editorTableIds = Array(import.semanticTables.size) { import.semanticTables[it].editorTableId }

        return when (
            ops.registerDefinitionBundle(
                currentHandle,
                import.profileName,
                import.profileGeneration,
                import.definitionFingerprint,
                import.rootDefinition,
                enabledSymbols,
                includeNames,
                includeContents,
                semanticIds,
                editorTableIds,
            )
        ) {
            PROFILE_IMPORT_OK -> DefinitionBundleImportResult.Imported
            PROFILE_IMPORT_INVALID_HANDLE -> rejectedBundleImport(DefinitionBundleImportRejection.InvalidHandle)
            PROFILE_IMPORT_INVALID_ARGUMENT -> rejectedBundleImport(DefinitionBundleImportRejection.InvalidArgument)
            PROFILE_IMPORT_BINDING_LENGTH_MISMATCH -> rejectedBundleImport(DefinitionBundleImportRejection.BindingLengthMismatch)
            PROFILE_IMPORT_INCLUDE_LENGTH_MISMATCH -> rejectedBundleImport(DefinitionBundleImportRejection.IncludeLengthMismatch)
            PROFILE_IMPORT_COMPILE_ERROR -> rejectedBundleImport(DefinitionBundleImportRejection.CompileFailure)
            PROFILE_IMPORT_REGISTRY_ERROR -> rejectedBundleImport(DefinitionBundleImportRejection.RegistryFailure)
            else -> rejectedBundleImport(DefinitionBundleImportRejection.NativeFailure)
        }
    }

    fun select(
        response: ByteArray,
        maxResponseBytes: Int,
    ): RuntimeProfileSelectionResult {
        val currentHandle = handle
        if (currentHandle == 0L) {
            return RuntimeProfileSelectionResult.Rejected(
                RuntimeProfileSelectionRejection.InvalidHandle,
            )
        }
        val direct = ByteBuffer.allocateDirect(response.size).apply {
            put(response)
            rewind()
        }
        val nativeResult =
            ops.selectRuntimeProfileGeneration(
                currentHandle,
                direct,
                response.size,
                maxResponseBytes,
            )
        return when {
            nativeResult > 0L -> RuntimeProfileSelectionResult.Selected(nativeResult)
            nativeResult == PROFILE_RESULT_INVALID_HANDLE -> rejected(RuntimeProfileSelectionRejection.InvalidHandle)
            nativeResult == PROFILE_RESULT_NOT_DIRECT -> rejected(RuntimeProfileSelectionRejection.NotDirectBuffer)
            nativeResult == PROFILE_RESULT_INVALID_LENGTH -> rejected(RuntimeProfileSelectionRejection.InvalidLength)
            nativeResult == PROFILE_RESULT_INVALID_LIMIT -> rejected(RuntimeProfileSelectionRejection.InvalidResponseLimit)
            nativeResult == PROFILE_RESULT_EMPTY_SIGNATURE -> rejected(RuntimeProfileSelectionRejection.EmptySignature)
            nativeResult == PROFILE_RESULT_INVALID_UTF8 -> rejected(RuntimeProfileSelectionRejection.InvalidUtf8)
            nativeResult == PROFILE_RESULT_TOO_LARGE -> rejected(RuntimeProfileSelectionRejection.ResponseTooLarge)
            nativeResult == PROFILE_RESULT_UNKNOWN_CONTROLLER -> rejected(RuntimeProfileSelectionRejection.UnknownController)
            nativeResult == PROFILE_RESULT_RECOGNIZED_NO_PROFILE -> rejected(RuntimeProfileSelectionRejection.RecognizedControllerWithoutProfile)
            nativeResult == PROFILE_RESULT_REGISTRY_ERROR -> rejected(RuntimeProfileSelectionRejection.RegistryFailure)
            else -> rejected(RuntimeProfileSelectionRejection.NativeFailure)
        }
    }

    internal fun readOnlyRegistryHandle(): Long = handle

    override fun close() {
        EngineAuthoritySerialization.exclusive {
            val currentHandle = handle
            if (currentHandle != 0L) {
                ops.close(currentHandle)
                handle = 0L
            }
        }
    }

    private fun validCommonImportArguments(
        profileName: String,
        profileGeneration: Long,
        definition: String,
        semanticTables: List<SemanticTableImportBinding>,
    ): Boolean =
        profileName.isNotEmpty() &&
            profileGeneration > 0L &&
            definition.isNotEmpty() &&
            semanticTables.none {
                it.semanticId < 0L || it.semanticId > 0xffff_ffffL || it.editorTableId.isEmpty()
            }

    private fun rejected(reason: RuntimeProfileSelectionRejection) =
        RuntimeProfileSelectionResult.Rejected(reason)

    private fun rejectedImport(reason: CompiledProfileImportRejection) =
        CompiledProfileImportResult.Rejected(reason)

    private fun rejectedBundleImport(reason: DefinitionBundleImportRejection) =
        DefinitionBundleImportResult.Rejected(reason)
}

private object JniNativeProfileRegistryOps : NativeProfileRegistryOps {
    override fun create(): Long = NativeProfileRegistryBridge.create()

    override fun close(handle: Long) = NativeProfileRegistryBridge.close(handle)

    override fun isAlive(handle: Long): Boolean = NativeProfileRegistryBridge.isAlive(handle)

    override fun activeCount(): Int = NativeProfileRegistryBridge.activeCount()

    override fun registerCompiledProfile(
        handle: Long,
        profileName: String,
        profileGeneration: Long,
        definitionFingerprint: String?,
        resolvedDefinition: String,
        semanticIds: LongArray,
        editorTableIds: Array<String>,
    ): Int =
        NativeProfileRegistryBridge.registerCompiledProfile(
            handle,
            profileName,
            profileGeneration,
            definitionFingerprint,
            resolvedDefinition,
            semanticIds,
            editorTableIds,
        )

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
    ): Int =
        NativeProfileRegistryBridge.registerDefinitionBundle(
            handle,
            profileName,
            profileGeneration,
            definitionFingerprint,
            rootDefinition,
            enabledSymbols,
            includeNames,
            includeContents,
            semanticIds,
            editorTableIds,
        )

    override fun selectRuntimeProfileGeneration(
        handle: Long,
        buffer: ByteBuffer,
        responseLength: Int,
        maxResponseBytes: Int,
    ): Long =
        NativeProfileRegistryBridge.selectRuntimeProfileGeneration(
            handle,
            buffer,
            responseLength,
            maxResponseBytes,
        )
}

object NativeProfileRegistryBridge {
    init {
        System.loadLibrary("epichub_engine")
    }

    /** Returns a positive registry-owned candidate epoch, not live telemetry. */
    external fun bindRuntimeCandidate(
        handle: Long,
        endpointId: String,
        signature: ByteBuffer,
        signatureLength: Int,
        signatureLimit: Int,
        expectedGeneration: Long,
    ): Long

    /** Revokes the candidate even when no USB runtime reads have occurred. */
    external fun disconnectRuntimeCandidate(handle: Long): Int

    external fun create(): Long

    external fun close(handle: Long)

    external fun isAlive(handle: Long): Boolean

    external fun activeCount(): Int

    external fun registerCompiledProfile(
        handle: Long,
        profileName: String,
        profileGeneration: Long,
        definitionFingerprint: String?,
        resolvedDefinition: String,
        semanticIds: LongArray,
        editorTableIds: Array<String>,
    ): Int

    external fun registerDefinitionBundle(
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
    ): Int

    external fun selectRuntimeProfileGeneration(
        handle: Long,
        buffer: ByteBuffer,
        responseLength: Int,
        maxResponseBytes: Int,
    ): Long
}
