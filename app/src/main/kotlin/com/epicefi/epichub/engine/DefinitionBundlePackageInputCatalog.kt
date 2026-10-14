package com.epicefi.epichub.engine

import java.io.ByteArrayOutputStream
import java.io.InputStream

private const val MAX_PROFILE_DOCUMENT_BYTES = 16 * 1024 * 1024

class DefinitionBundlePackageReadException(message: String) : IllegalArgumentException(message)

/**
 * Explicit byte source for one user/app-selected profile package.
 *
 * The source owns only document/package acquisition. It grants no controller/profile authority.
 */
fun interface DefinitionBundlePackageInput {
    fun readPackageBytes(): ByteArray
}

/**
 * Adapts explicit package inputs to the qualified package catalog while preserving input order.
 * Read/package decode failures intentionally escape through [DefinitionBundleCatalog.bundles] so
 * the transactional registry owner can fail before creating/exposing profile authority.
 */
class DefinitionBundlePackageInputCatalog(inputs: List<DefinitionBundlePackageInput>) :
    DefinitionBundleCatalog {
    private val packageInputs = inputs.toList()

    override fun bundles(): List<DefinitionBundleImport> =
        DefinitionBundlePackageCatalog(
            packageInputs.map(DefinitionBundlePackageInput::readPackageBytes),
        ).bundles()
}

/** Reads one provider stream without allowing an unbounded document to be materialized in memory. */
internal fun readBoundedDefinitionBundlePackage(input: InputStream): ByteArray =
    input.use { stream ->
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var total = 0
        while (true) {
            val read = stream.read(buffer)
            if (read < 0) break
            if (read == 0) continue
            total += read
            if (total > MAX_PROFILE_DOCUMENT_BYTES) {
                throw DefinitionBundlePackageReadException(
                    "Profile package document exceeds the supported size limit.",
                )
            }
            output.write(buffer, 0, read)
        }
        output.toByteArray()
    }
