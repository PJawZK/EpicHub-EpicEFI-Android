package com.epicefi.epichub.engine

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.zip.ZipInputStream

private const val PROFILE_PACKAGE_MANIFEST = "epichub-profile.properties"
private const val PROFILE_PACKAGE_FORMAT_VERSION = "1"
private const val MAX_PROFILE_PACKAGE_BYTES = 16 * 1024 * 1024
private const val MAX_PROFILE_PACKAGE_ENTRY_BYTES = 8 * 1024 * 1024
private const val MAX_PROFILE_PACKAGE_EXPANDED_BYTES = 32 * 1024 * 1024
private const val MAX_PROFILE_PACKAGE_ENTRIES = 1024
private const val MAX_PROFILE_PACKAGE_SYMBOLS = 256
private const val MAX_PROFILE_PACKAGE_INCLUDES = 512
private const val MAX_PROFILE_PACKAGE_SEMANTICS = 4096
private const val MAX_U32 = 0xffff_ffffL

class DefinitionBundlePackageException(message: String) : IllegalArgumentException(message)

/**
 * Version-1 portable EpicHub profile package decoder.
 *
 * The ZIP package carries only packaging metadata plus the exact definition inputs already owned by
 * [DefinitionBundleImport]. It does not resolve controller identity, compile protocol commands, or
 * grant runtime authority; those remain native responsibilities after catalog import.
 *
 * Required ZIP entry: `epichub-profile.properties`.
 * Required manifest keys:
 * - `format.version=1`
 * - `profile.name=<non-empty>`
 * - `profile.generation=<positive long>`
 * - `definition.root=<zip path>`
 * - `symbols.count=<n>` with `symbol.<i>=<symbol>`
 * - `includes.count=<n>` with `include.<i>.name=<definition include name>` and
 *   `include.<i>.path=<zip path>`
 * - `semantics.count=<n>` with `semantic.<i>.id=<u32>` and
 *   `semantic.<i>.table=<editor table id>`
 * Optional key: `profile.fingerprint=<text>`.
 */
object DefinitionBundlePackageDecoder {
    fun decode(packageBytes: ByteArray): DefinitionBundleImport {
        if (packageBytes.isEmpty()) {
            fail("Profile package is empty.")
        }
        if (packageBytes.size > MAX_PROFILE_PACKAGE_BYTES) {
            fail("Profile package exceeds the compressed-size limit.")
        }

        val entries = readZipEntries(packageBytes)
        val manifestBytes = entries[PROFILE_PACKAGE_MANIFEST]
            ?: fail("Profile package is missing $PROFILE_PACKAGE_MANIFEST.")
        val manifest = parseManifest(decodeUtf8(PROFILE_PACKAGE_MANIFEST, manifestBytes))

        if (manifest["format.version"] != PROFILE_PACKAGE_FORMAT_VERSION) {
            fail("Unsupported profile package format version.")
        }

        val profileName = manifest.required("profile.name")
        val profileGeneration = manifest.requiredPositiveLong("profile.generation")
        val fingerprint = manifest["profile.fingerprint"]?.takeIf(String::isNotEmpty)
        val rootPath = manifest.requiredSafePath("definition.root")
        if (rootPath == PROFILE_PACKAGE_MANIFEST) {
            fail("Root definition cannot be the package manifest.")
        }

        val symbolCount = manifest.requiredCount("symbols.count", MAX_PROFILE_PACKAGE_SYMBOLS)
        val symbols = LinkedHashSet<String>(symbolCount)
        repeat(symbolCount) { index ->
            val symbol = manifest.required("symbol.$index")
            if (!symbols.add(symbol)) {
                fail("Duplicate enabled symbol: $symbol")
            }
        }

        val includeCount = manifest.requiredCount("includes.count", MAX_PROFILE_PACKAGE_INCLUDES)
        val includes = LinkedHashMap<String, String>(includeCount)
        val referencedPaths = LinkedHashSet<String>()
        referencedPaths += rootPath
        repeat(includeCount) { index ->
            val includeName = manifest.required("include.$index.name")
            val includePath = manifest.requiredSafePath("include.$index.path")
            if (includePath == PROFILE_PACKAGE_MANIFEST) {
                fail("Include $includeName cannot reference the package manifest.")
            }
            if (includes.containsKey(includeName)) {
                fail("Duplicate include name: $includeName")
            }
            val bytes = entries[includePath]
                ?: fail("Referenced include entry is missing: $includePath")
            includes[includeName] = decodeUtf8(includePath, bytes)
            referencedPaths += includePath
        }

        val semanticCount = manifest.requiredCount("semantics.count", MAX_PROFILE_PACKAGE_SEMANTICS)
        val semanticIds = HashSet<Long>(semanticCount)
        val editorTableIds = HashSet<String>(semanticCount)
        val semantics = ArrayList<SemanticTableImportBinding>(semanticCount)
        repeat(semanticCount) { index ->
            val semanticId = manifest.requiredU32("semantic.$index.id")
            val editorTableId = manifest.required("semantic.$index.table")
            if (!semanticIds.add(semanticId)) {
                fail("Duplicate semantic table id: $semanticId")
            }
            if (!editorTableIds.add(editorTableId)) {
                fail("Duplicate editor table id: $editorTableId")
            }
            semantics += SemanticTableImportBinding(semanticId, editorTableId)
        }

        validateManifestKeys(manifest, symbolCount, includeCount, semanticCount)

        val rootBytes = entries[rootPath]
            ?: fail("Referenced root definition entry is missing: $rootPath")
        val allowedEntries = referencedPaths + PROFILE_PACKAGE_MANIFEST
        val unreferenced = entries.keys - allowedEntries
        if (unreferenced.isNotEmpty()) {
            fail("Profile package contains unreferenced entries: ${unreferenced.sorted().joinToString()}")
        }

        return DefinitionBundleImport(
            profileName = profileName,
            profileGeneration = profileGeneration,
            definitionFingerprint = fingerprint,
            rootDefinition = decodeUtf8(rootPath, rootBytes),
            enabledSymbols = symbols,
            includes = includes,
            semanticTables = semantics,
        )
    }

    private fun readZipEntries(packageBytes: ByteArray): LinkedHashMap<String, ByteArray> {
        val entries = LinkedHashMap<String, ByteArray>()
        var expandedBytes = 0
        var entryCount = 0

        try {
            ZipInputStream(ByteArrayInputStream(packageBytes)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    entryCount += 1
                    if (entryCount > MAX_PROFILE_PACKAGE_ENTRIES) {
                        fail("Profile package contains too many ZIP entries.")
                    }

                    val entryName = validateZipEntryName(entry.name, entry.isDirectory)
                    if (!entry.isDirectory) {
                        if (entries.containsKey(entryName)) {
                            fail("Duplicate ZIP entry: $entryName")
                        }
                        val bytes = readEntry(zip, entryName)
                        expandedBytes += bytes.size
                        if (expandedBytes > MAX_PROFILE_PACKAGE_EXPANDED_BYTES) {
                            fail("Profile package exceeds the expanded-size limit.")
                        }
                        entries[entryName] = bytes
                    }
                    zip.closeEntry()
                }
            }
        } catch (error: DefinitionBundlePackageException) {
            throw error
        } catch (error: Exception) {
            fail("Invalid profile package ZIP: ${error.message ?: error::class.java.simpleName}")
        }

        return entries
    }

    private fun readEntry(zip: ZipInputStream, entryName: String): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var total = 0
        while (true) {
            val read = zip.read(buffer)
            if (read < 0) break
            if (read == 0) continue
            total += read
            if (total > MAX_PROFILE_PACKAGE_ENTRY_BYTES) {
                fail("ZIP entry exceeds the per-entry size limit: $entryName")
            }
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private fun validateZipEntryName(rawName: String, directory: Boolean): String {
        if (rawName.indexOf('\u0000') >= 0 || rawName.indexOf('\\') >= 0 || rawName.startsWith('/')) {
            fail("Unsafe ZIP entry path: $rawName")
        }
        val name = if (directory) rawName.removeSuffix("/") else rawName
        if (name.isEmpty()) {
            fail("ZIP entry path is empty.")
        }
        val segments = name.split('/')
        if (segments.any { it.isEmpty() || it == "." || it == ".." }) {
            fail("Unsafe ZIP entry path: $rawName")
        }
        return name
    }

    private fun parseManifest(text: String): LinkedHashMap<String, String> {
        val values = LinkedHashMap<String, String>()
        text.lineSequence().forEachIndexed { index, rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith('#')) return@forEachIndexed
            val separator = line.indexOf('=')
            if (separator <= 0) {
                fail("Malformed manifest line ${index + 1}.")
            }
            val key = line.substring(0, separator).trim()
            val value = line.substring(separator + 1).trim()
            if (key.isEmpty() || values.putIfAbsent(key, value) != null) {
                fail("Duplicate or empty manifest key on line ${index + 1}.")
            }
        }
        return values
    }

    private fun validateManifestKeys(
        manifest: Map<String, String>,
        symbolCount: Int,
        includeCount: Int,
        semanticCount: Int,
    ) {
        val expected = LinkedHashSet<String>()
        expected += listOf(
            "format.version",
            "profile.name",
            "profile.generation",
            "definition.root",
            "symbols.count",
            "includes.count",
            "semantics.count",
        )
        if (manifest.containsKey("profile.fingerprint")) expected += "profile.fingerprint"
        repeat(symbolCount) { expected += "symbol.$it" }
        repeat(includeCount) {
            expected += "include.$it.name"
            expected += "include.$it.path"
        }
        repeat(semanticCount) {
            expected += "semantic.$it.id"
            expected += "semantic.$it.table"
        }

        val unexpected = manifest.keys - expected
        if (unexpected.isNotEmpty()) {
            fail("Unsupported or sparse manifest keys: ${unexpected.sorted().joinToString()}")
        }
    }

    private fun decodeUtf8(name: String, bytes: ByteArray): String =
        try {
            Charsets.UTF_8
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (_: Exception) {
            fail("ZIP entry is not valid UTF-8: $name")
        }

    private fun Map<String, String>.required(key: String): String =
        this[key]?.takeIf(String::isNotEmpty) ?: fail("Missing or empty manifest key: $key")

    private fun Map<String, String>.requiredPositiveLong(key: String): Long {
        val value = required(key).toLongOrNull() ?: fail("Manifest key $key is not a valid integer.")
        if (value <= 0L) fail("Manifest key $key must be positive.")
        return value
    }

    private fun Map<String, String>.requiredCount(key: String, maximum: Int): Int {
        val value = required(key).toIntOrNull() ?: fail("Manifest key $key is not a valid count.")
        if (value < 0 || value > maximum) fail("Manifest key $key is outside the supported range.")
        return value
    }

    private fun Map<String, String>.requiredU32(key: String): Long {
        val value = required(key).toLongOrNull() ?: fail("Manifest key $key is not a valid semantic id.")
        if (value < 0L || value > MAX_U32) fail("Manifest key $key is outside the u32 range.")
        return value
    }

    private fun Map<String, String>.requiredSafePath(key: String): String {
        val value = required(key)
        return validateZipEntryName(value, directory = false)
    }

    private fun fail(message: String): Nothing = throw DefinitionBundlePackageException(message)
}

/**
 * Catalog adapter for explicitly supplied profile-package bytes. Package order is preserved so the
 * transactional registry owner retains deterministic import/rejection indexing.
 */
class DefinitionBundlePackageCatalog(packages: List<ByteArray>) : DefinitionBundleCatalog {
    private val packageCopies = packages.map { it.copyOf() }

    override fun bundles(): List<DefinitionBundleImport> =
        packageCopies.map(DefinitionBundlePackageDecoder::decode)
}
