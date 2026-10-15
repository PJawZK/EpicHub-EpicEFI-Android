package com.epicefi.epichub.engine

/**
 * Supplies ECU definition bundles to the app without coupling registry ownership to any one ECU,
 * board family, transport identity, or packaging mechanism.
 *
 * Implementations may later load project files, app-managed profile packages, downloaded catalogs,
 * or other explicit user-selected sources. This boundary deliberately carries complete
 * [DefinitionBundleImport] values so exact signature/profile/protocol authority remains native.
 */
fun interface DefinitionBundleCatalog {
    fun bundles(): List<DefinitionBundleImport>
}

sealed interface DefinitionBundleCatalogOpenResult {
    data class Opened(
        val registry: DefinitionBundleRegistryOwner,
        val importedBundleCount: Int,
    ) : DefinitionBundleCatalogOpenResult

    data class Rejected(
        val bundleIndex: Int,
        val reason: DefinitionBundleImportRejection,
    ) : DefinitionBundleCatalogOpenResult

    data class CatalogFailure(
        val reason: String,
    ) : DefinitionBundleCatalogOpenResult
}

internal interface DefinitionBundleRegistry : AutoCloseable {
    val isAlive: Boolean

    fun importDefinitionBundle(import: DefinitionBundleImport): DefinitionBundleImportResult
}

internal fun interface DefinitionBundleRegistryFactory {
    fun create(): DefinitionBundleRegistry
}

class DefinitionBundleRegistryOwner private constructor(
    private val registry: DefinitionBundleRegistry,
    private val selectedBundles: List<DefinitionBundleImport>,
) : AutoCloseable {
    val isAlive: Boolean
        get() = registry.isAlive

    internal fun requireNativeRegistry(): NativeProfileRegistry =
        (registry as? NativeDefinitionBundleRegistry)?.registry
            ?: error("This definition-bundle registry owner is not backed by a native registry.")

    internal fun definitionBundles(): List<DefinitionBundleImport> = selectedBundles

    override fun close() {
        registry.close()
    }

    companion object {
        fun open(catalog: DefinitionBundleCatalog): DefinitionBundleCatalogOpenResult =
            openWithFactory(catalog, nativeRegistryFactory)

        internal fun openWithFactory(
            catalog: DefinitionBundleCatalog,
            factory: DefinitionBundleRegistryFactory,
        ): DefinitionBundleCatalogOpenResult {
            val bundles =
                try {
                    catalog.bundles().toList()
                } catch (error: Exception) {
                    return catalogFailure(error)
                }

            val candidate =
                try {
                    factory.create()
                } catch (error: Exception) {
                    return catalogFailure(error)
                }

            if (!candidate.isAlive) {
                candidate.close()
                return DefinitionBundleCatalogOpenResult.CatalogFailure(
                    "Profile registry creation did not produce a live registry.",
                )
            }

            bundles.forEachIndexed { index, bundle ->
                val imported =
                    try {
                        candidate.importDefinitionBundle(bundle)
                    } catch (error: Exception) {
                        candidate.close()
                        return catalogFailure(error)
                    }

                when (imported) {
                    DefinitionBundleImportResult.Imported -> Unit
                    is DefinitionBundleImportResult.Rejected -> {
                        candidate.close()
                        return DefinitionBundleCatalogOpenResult.Rejected(index, imported.reason)
                    }
                }
            }

            return DefinitionBundleCatalogOpenResult.Opened(
                registry = DefinitionBundleRegistryOwner(candidate, bundles),
                importedBundleCount = bundles.size,
            )
        }

        private fun catalogFailure(error: Exception) =
            DefinitionBundleCatalogOpenResult.CatalogFailure(
                error.message ?: error::class.java.simpleName,
            )

        private val nativeRegistryFactory = DefinitionBundleRegistryFactory {
            NativeDefinitionBundleRegistry(NativeProfileRegistry())
        }
    }
}

private class NativeDefinitionBundleRegistry(
    val registry: NativeProfileRegistry,
) : DefinitionBundleRegistry {
    override val isAlive: Boolean
        get() = registry.isAlive

    override fun importDefinitionBundle(import: DefinitionBundleImport): DefinitionBundleImportResult =
        registry.importDefinitionBundle(import)

    override fun close() {
        registry.close()
    }
}
