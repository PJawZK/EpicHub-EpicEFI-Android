package com.epicefi.epichub.engine

import java.nio.ByteBuffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeProfileRegistryTest {
    @Test
    fun positive_native_result_returns_selected_profile_generation() {
        val ops = FakeOps(createHandle = 41L, selectionResult = 2_273_317_132L)
        val registry = NativeProfileRegistry(ops)

        val result = registry.select("rusEFI exact.signature".toByteArray(), 256)

        assertEquals(
            RuntimeProfileSelectionResult.Selected(2_273_317_132L),
            result,
        )
        assertEquals(41L, ops.lastSelectionHandle)
        assertEquals(22, ops.lastResponseLength)
        assertEquals(256, ops.lastResponseLimit)
        assertTrue(ops.lastBufferDirect)
    }

    @Test
    fun authority_statuses_remain_distinct_and_fail_closed() {
        val cases =
            listOf(
                -1L to RuntimeProfileSelectionRejection.InvalidHandle,
                -2L to RuntimeProfileSelectionRejection.NotDirectBuffer,
                -3L to RuntimeProfileSelectionRejection.InvalidLength,
                -4L to RuntimeProfileSelectionRejection.InvalidResponseLimit,
                -5L to RuntimeProfileSelectionRejection.EmptySignature,
                -6L to RuntimeProfileSelectionRejection.InvalidUtf8,
                -7L to RuntimeProfileSelectionRejection.ResponseTooLarge,
                -8L to RuntimeProfileSelectionRejection.UnknownController,
                -9L to RuntimeProfileSelectionRejection.RecognizedControllerWithoutProfile,
                -10L to RuntimeProfileSelectionRejection.RegistryFailure,
                -255L to RuntimeProfileSelectionRejection.NativeFailure,
                0L to RuntimeProfileSelectionRejection.NativeFailure,
            )

        cases.forEach { (nativeResult, expected) ->
            val registry = NativeProfileRegistry(FakeOps(selectionResult = nativeResult))
            assertEquals(
                RuntimeProfileSelectionResult.Rejected(expected),
                registry.select("response".toByteArray(), 256),
            )
        }
    }

    @Test
    fun close_is_idempotent_and_prevents_future_selection() {
        val ops = FakeOps(createHandle = 77L, selectionResult = 123L)
        val registry = NativeProfileRegistry(ops)
        assertTrue(registry.isAlive)

        registry.close()
        registry.close()

        assertEquals(listOf(77L), ops.closedHandles)
        assertFalse(registry.isAlive)
        assertEquals(
            RuntimeProfileSelectionResult.Rejected(
                RuntimeProfileSelectionRejection.InvalidHandle,
            ),
            registry.select("response".toByteArray(), 256),
        )
        assertEquals(0, ops.selectionCalls)
    }

    private class FakeOps(
        private val createHandle: Long = 1L,
        private val selectionResult: Long = 1L,
    ) : NativeProfileRegistryOps {
        val closedHandles = mutableListOf<Long>()
        var selectionCalls = 0
        var lastSelectionHandle = -1L
        var lastResponseLength = -1
        var lastResponseLimit = -1
        var lastBufferDirect = false

        override fun create(): Long = createHandle

        override fun close(handle: Long) {
            closedHandles += handle
        }

        override fun isAlive(handle: Long): Boolean = handle == createHandle

        override fun activeCount(): Int = if (closedHandles.isEmpty()) 1 else 0

        override fun selectRuntimeProfileGeneration(
            handle: Long,
            buffer: ByteBuffer,
            responseLength: Int,
            maxResponseBytes: Int,
        ): Long {
            selectionCalls += 1
            lastSelectionHandle = handle
            lastResponseLength = responseLength
            lastResponseLimit = maxResponseBytes
            lastBufferDirect = buffer.isDirect
            return selectionResult
        }
    }
}
