package com.epicefi.epichub.diagnostics

import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

internal object DiagnosticCaptureArchive {
    val canonicalEntryNames: Set<String> = setOf("capture.cbor", "report.txt")

    fun write(
        output: OutputStream,
        cbor: ByteArray,
        report: ByteArray,
    ) {
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("capture.cbor"))
            zip.write(cbor)
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("report.txt"))
            zip.write(report)
            zip.closeEntry()
        }
    }
}
