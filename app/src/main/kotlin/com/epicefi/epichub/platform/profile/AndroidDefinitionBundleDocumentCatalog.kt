package com.epicefi.epichub.platform.profile

import android.content.ContentResolver
import android.net.Uri
import com.epicefi.epichub.engine.DefinitionBundleCatalog
import com.epicefi.epichub.engine.DefinitionBundleImport
import com.epicefi.epichub.engine.DefinitionBundlePackageInput
import com.epicefi.epichub.engine.DefinitionBundlePackageInputCatalog
import com.epicefi.epichub.engine.DefinitionBundlePackageReadException
import com.epicefi.epichub.engine.readBoundedDefinitionBundlePackage

const val EPICHUB_PROFILE_MIME_TYPE = "application/vnd.epichub.profile+zip"

/**
 * Android document-provider adapter for explicitly selected EpicHub profile-package URIs.
 *
 * URI order is preserved. This class does not persist permissions, discover profiles, inspect USB
 * metadata, or select a controller. It only opens the exact documents supplied by the caller and
 * delegates bounded bytes to the qualified generic package/catalog pipeline.
 */
class AndroidDefinitionBundleDocumentCatalog(
    contentResolver: ContentResolver,
    uris: List<Uri>,
) : DefinitionBundleCatalog {
    private val delegate =
        DefinitionBundlePackageInputCatalog(
            uris.toList().map { uri ->
                DefinitionBundlePackageInput {
                    val input = contentResolver.openInputStream(uri)
                        ?: throw DefinitionBundlePackageReadException(
                            "Profile package document could not be opened: $uri",
                        )
                    try {
                        readBoundedDefinitionBundlePackage(input)
                    } catch (error: DefinitionBundlePackageReadException) {
                        throw error
                    } catch (error: Exception) {
                        throw DefinitionBundlePackageReadException(
                            "Profile package document read failed: ${error.message ?: error::class.java.simpleName}",
                        )
                    }
                }
            },
        )

    override fun bundles(): List<DefinitionBundleImport> = delegate.bundles()
}
