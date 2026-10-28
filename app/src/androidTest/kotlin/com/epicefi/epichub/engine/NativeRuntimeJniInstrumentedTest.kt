package com.epicefi.epichub.engine

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Gate B real-JNI smoke coverage. Unlike JVM unit tests, these calls cross into
 * the packaged Rust .so on a real Android runtime. No ECU or USB access is needed.
 *
 * This is only the first instrumentation checkpoint; retained fake-USB end-to-end
 * acquisition, permission/detach and concurrency coverage remain open.
 */
@RunWith(AndroidJUnit4::class)
class NativeRuntimeJniInstrumentedTest {
    @Test
    fun registryLifecycleAndStaleHandleAreEnforcedByLoadedNativeLibrary() {
        val registry = NativeProfileRegistryBridge.create()
        assertTrue("JNI registry creation failed", registry > 0L)
        try {
            assertTrue(NativeProfileRegistryBridge.isAlive(registry))
            val metadata = ByteBuffer.allocateDirect(131_072).order(ByteOrder.LITTLE_ENDIAN)
            assertTrue(
                "Unbound runtime epoch must not expose catalog metadata",
                NativeProfileRegistryBridge.runtimeChannelCatalog(registry, 1L, metadata) < 0,
            )
            val request = ByteBuffer.allocateDirect(65_536).order(ByteOrder.LITTLE_ENDIAN)
            assertTrue(
                "Unbound runtime epoch must not prepare a runtime transaction",
                NativeProfileRegistryBridge.prepareRuntimeRead(registry, 1L, 10L, 100L, request) < 0L,
            )
        } finally {
            NativeProfileRegistryBridge.close(registry)
        }
        assertFalse("Closed registry must be invalid", NativeProfileRegistryBridge.isAlive(registry))
        assertTrue(
            "Closed registry must reject runtime catalog access",
            NativeProfileRegistryBridge.runtimeChannelCatalog(
                registry, 1L, ByteBuffer.allocateDirect(131_072),
            ) < 0,
        )
    }

    @Test
    fun nullRegistryHandleCannotAcquireNativeRuntimeAuthority() {
        assertFalse(NativeProfileRegistryBridge.isAlive(0L))
        assertTrue(
            NativeProfileRegistryBridge.runtimeChannelCatalog(
                0L, 1L, ByteBuffer.allocateDirect(131_072),
            ) < 0,
        )
        assertTrue(
            NativeProfileRegistryBridge.bindRuntimeCandidate(
                0L,
                "usb:fake:1",
                ByteBuffer.allocateDirect(4),
                4,
                64,
                1L,
            ) < 0L,
        )
        assertTrue(
            NativeProfileRegistryBridge.prepareRuntimeRead(
                0L, 1L, 10L, 100L, ByteBuffer.allocateDirect(65_536),
            ) < 0L,
        )
    }
    @Test
    fun concurrentRegistryInstancesCannotBorrowOneAnothersRuntimeAuthority() {
        val first = NativeProfileRegistryBridge.create()
        val second = NativeProfileRegistryBridge.create()
        assertTrue(first > 0L)
        assertTrue(second > 0L)
        assertTrue(first != second)
        try {
            assertTrue(NativeProfileRegistryBridge.isAlive(first))
            assertTrue(NativeProfileRegistryBridge.isAlive(second))
            NativeProfileRegistryBridge.close(first)
            assertFalse(NativeProfileRegistryBridge.isAlive(first))
            assertTrue("Second registry must remain valid", NativeProfileRegistryBridge.isAlive(second))
            assertTrue(
                NativeProfileRegistryBridge.runtimeChannelCatalog(
                    first, 1L, ByteBuffer.allocateDirect(131_072),
                ) < 0,
            )
            assertTrue(
                NativeProfileRegistryBridge.runtimeChannelCatalog(
                    second, 1L, ByteBuffer.allocateDirect(131_072),
                ) < 0,
            )
            assertTrue(
                NativeProfileRegistryBridge.prepareRuntimeRead(
                    first, 1L, 20L, 100L, ByteBuffer.allocateDirect(72),
                ) < 0L,
            )
        } finally {
            NativeProfileRegistryBridge.close(first)
            NativeProfileRegistryBridge.close(second)
        }
        assertFalse(NativeProfileRegistryBridge.isAlive(second))
    }

    @Test
    fun nativeRuntimeCompletionRejectsUnboundAndClosedSessions() {
        val handle = NativeProfileRegistryBridge.create()
        assertTrue(handle > 0L)
        val response = ByteBuffer.allocateDirect(8)
        val output = ByteBuffer.allocateDirect(12_344)
        try {
            assertTrue(
                NativeProfileRegistryBridge.finishRuntimeRead(
                    handle, 1L, 1L, 25L, response, 8, output,
                ) < 0,
            )
            assertTrue(
                NativeProfileRegistryBridge.disconnectRuntimeCandidate(handle) == 0,
            )
        } finally {
            NativeProfileRegistryBridge.close(handle)
        }
        assertTrue(
            NativeProfileRegistryBridge.finishRuntimeRead(
                handle, 1L, 1L, 30L, response, 8, output,
            ) < 0,
        )
    }

}
