package com.epicefi.epichub.engine

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DefinitionBundlePackageInputCatalogTest {
    @Test
    fun inputCatalogPreservesExplicitDocumentOrder() {
        val catalog =
            DefinitionBundlePackageInputCatalog(
                listOf(
                    DefinitionBundlePackageInput { packageBytes("first", 1) },
                    DefinitionBundlePackageInput { packageBytes("second", 2) },
                ),
            )

        assertEquals(listOf("first", "second"), catalog.bundles().map { it.profileName })
    }

    @Test
    fun boundedReaderClosesProviderStream() {
        val input = TrackingInputStream("package".encodeToByteArray())

        val bytes = readBoundedDefinitionBundlePackage(input)

        assertEquals("package", bytes.decodeToString())
        assertTrue(input.closed)
    }

    @Test
    fun boundedReaderRejectsOversizeStreamWithoutMaterializingWholeDocument() {
        val input = GeneratedInputStream(totalBytes = 16 * 1024 * 1024 + 1)

        val error =
            assertThrows(DefinitionBundlePackageReadException::class.java) {
                readBoundedDefinitionBundlePackage(input)
            }

        assertTrue(error.message.orEmpty().contains("exceeds the supported size limit"))
        assertTrue(input.closed)
        assertTrue(input.bytesServed <= 16 * 1024 * 1024 + 8192)
    }

    @Test
    fun documentReadFailurePreventsTransactionalRegistryCreation() {
        var factoryCalls = 0
        val catalog =
            DefinitionBundlePackageInputCatalog(
                listOf(
                    DefinitionBundlePackageInput {
                        throw DefinitionBundlePackageReadException("provider read failed")
                    },
                ),
            )

        val result =
            DefinitionBundleRegistryOwner.openWithFactory(
                catalog = catalog,
                factory =
                    DefinitionBundleRegistryFactory {
                        factoryCalls += 1
                        error("registry must not be created")
                    },
            )

        assertEquals(
            DefinitionBundleCatalogOpenResult.CatalogFailure("provider read failed"),
            result,
        )
        assertEquals(0, factoryCalls)
    }

    @Test
    fun packageBytesAreDecodedOnlyAfterExplicitInputRead() {
        var reads = 0
        val catalog =
            DefinitionBundlePackageInputCatalog(
                listOf(
                    DefinitionBundlePackageInput {
                        reads += 1
                        packageBytes("deferred", 9)
                    },
                ),
            )

        assertEquals(0, reads)
        assertEquals("deferred", catalog.bundles().single().profileName)
        assertEquals(1, reads)
    }

    private fun packageBytes(name: String, generation: Long): ByteArray {
        val manifest =
            """
            format.version=1
            profile.name=$name
            profile.generation=$generation
            definition.root=mainController.ini
            symbols.count=0
            includes.count=0
            semantics.count=1
            semantic.0.id=$generation
            semantic.0.table=table-$name
            """.trimIndent()
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("epichub-profile.properties"))
            zip.write(manifest.encodeToByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("mainController.ini"))
            zip.write(
                "[TunerStudio]\nsignature = \"signature-$name\"".encodeToByteArray(),
            )
            zip.closeEntry()
        }
        return output.toByteArray()
    }

    private class TrackingInputStream(
        private val bytes: ByteArray,
    ) : InputStream() {
        private var offset = 0
        var closed = false
            private set

        override fun read(): Int =
            if (offset >= bytes.size) -1 else bytes[offset++].toInt() and 0xff

        override fun read(buffer: ByteArray, off: Int, len: Int): Int {
            if (offset >= bytes.size) return -1
            val count = minOf(len, bytes.size - offset)
            bytes.copyInto(buffer, off, offset, offset + count)
            offset += count
            return count
        }

        override fun close() {
            closed = true
        }
    }

    private class GeneratedInputStream(
        private val totalBytes: Int,
    ) : InputStream() {
        var bytesServed = 0
            private set
        var closed = false
            private set

        override fun read(): Int {
            if (bytesServed >= totalBytes) return -1
            bytesServed += 1
            return 0
        }

        override fun read(buffer: ByteArray, off: Int, len: Int): Int {
            if (bytesServed >= totalBytes) return -1
            val count = minOf(len, totalBytes - bytesServed)
            buffer.fill(0, off, off + count)
            bytesServed += count
            return count
        }

        override fun close() {
            closed = true
        }
    }
}
