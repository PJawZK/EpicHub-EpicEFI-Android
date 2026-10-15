package com.epicefi.epichub.engine

import java.nio.ByteBuffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeGuardedRamWriteAuthorityTest {
    private val intent =
        GuardedRamWriteIntent(
            page = 2,
            offset = 12,
            before = byteArrayOf(1, 2),
            after = byteArrayOf(3, 4),
        )

    @Test
    fun nativePlanBecomesThreeOrderedAuthorityStages() {
        val ops = FakeOps()
        val authority = NativeGuardedRamWriteAuthority(handleProvider = { 77L }, ops = ops)

        val result = authority.plan("exact-signature".encodeToByteArray(), 64, intent)

        assertTrue(result is GuardedRamWritePlanResult.Planned)
        result as GuardedRamWritePlanResult.Planned
        assertEquals(1, result.transfers.size)
        val transfer = result.transfers.single()
        assertEquals(listOf(0x10.toByte()), transfer.precondition.request.toList())
        assertEquals(9, transfer.precondition.expectedResponseBytes)
        assertEquals(listOf(0x20.toByte()), transfer.write.request.toList())
        assertEquals(7, transfer.write.expectedResponseBytes)
        assertEquals(listOf(0x30.toByte()), transfer.readback.request.toList())
        assertEquals(9, transfer.readback.expectedResponseBytes)
        assertEquals(listOf(0, 1, 2), ops.filledStages)
        assertEquals(2, ops.lastPage)
        assertEquals(12, ops.lastOffset)
        assertEquals(listOf(1.toByte(), 2.toByte()), ops.lastBefore.toList())
        assertEquals(listOf(3.toByte(), 4.toByte()), ops.lastAfter.toList())
    }

    @Test
    fun verificationStatusesKeepStaleAckAndReadbackFailuresDistinct() {
        val ops = FakeOps()
        val authority = NativeGuardedRamWriteAuthority(handleProvider = { 77L }, ops = ops)
        val signature = "exact-signature".encodeToByteArray()

        ops.verifyStatus = -14
        assertEquals(
            GuardedRamWriteVerifyResult.Rejected(
                GuardedRamWriteAuthorityRejection.StalePrecondition,
            ),
            authority.verifyPrecondition(signature, 64, intent, 0, byteArrayOf(1)),
        )

        ops.verifyStatus = -15
        assertEquals(
            GuardedRamWriteVerifyResult.Rejected(
                GuardedRamWriteAuthorityRejection.WriteAcknowledgementFailure,
            ),
            authority.verifyWriteAcknowledgement(signature, 64, intent, 0, byteArrayOf(1)),
        )

        ops.verifyStatus = -17
        assertEquals(
            GuardedRamWriteVerifyResult.Rejected(
                GuardedRamWriteAuthorityRejection.ReadbackMismatch,
            ),
            authority.verifyReadback(signature, 64, intent, 0, byteArrayOf(1)),
        )
        assertEquals(listOf(0, 1, 2), ops.verifiedStages)
    }

    @Test
    fun invalidIntentFailsBeforeNativeCall() {
        val ops = FakeOps()
        val authority = NativeGuardedRamWriteAuthority(handleProvider = { 77L }, ops = ops)
        val invalid = intent.copy(before = byteArrayOf(1), after = byteArrayOf(2, 3))

        assertEquals(
            GuardedRamWritePlanResult.Rejected(
                GuardedRamWriteAuthorityRejection.InvalidArgument,
            ),
            authority.plan("exact-signature".encodeToByteArray(), 64, invalid),
        )
        assertEquals(0, ops.planCountCalls)
    }

    @Test
    fun invalidHandleAndNativePlanErrorsStayFailClosed() {
        val invalidHandleAuthority =
            NativeGuardedRamWriteAuthority(handleProvider = { 0L }, ops = FakeOps())
        assertEquals(
            GuardedRamWritePlanResult.Rejected(
                GuardedRamWriteAuthorityRejection.InvalidHandle,
            ),
            invalidHandleAuthority.plan("exact-signature".encodeToByteArray(), 64, intent),
        )

        val ops = FakeOps(planCountResult = -10)
        val authority = NativeGuardedRamWriteAuthority(handleProvider = { 77L }, ops = ops)
        assertEquals(
            GuardedRamWritePlanResult.Rejected(
                GuardedRamWriteAuthorityRejection.MissingCompiledBundle,
            ),
            authority.plan("exact-signature".encodeToByteArray(), 64, intent),
        )
    }

    private class FakeOps(
        private val planCountResult: Int = 1,
    ) : NativeGuardedRamWriteOps {
        var planCountCalls = 0
        var verifyStatus = 0
        val filledStages = mutableListOf<Int>()
        val verifiedStages = mutableListOf<Int>()
        var lastPage = -1
        var lastOffset = -1
        var lastBefore = byteArrayOf()
        var lastAfter = byteArrayOf()

        override fun guardedWritePlanCount(
            handle: Long,
            signatureBuffer: ByteBuffer,
            signatureLength: Int,
            signatureLimit: Int,
            page: Int,
            offset: Int,
            beforeBuffer: ByteBuffer,
            beforeLength: Int,
            afterBuffer: ByteBuffer,
            afterLength: Int,
        ): Int {
            planCountCalls += 1
            capture(page, offset, beforeBuffer, beforeLength, afterBuffer, afterLength)
            return planCountResult
        }

        override fun fillGuardedWriteStage(
            handle: Long,
            signatureBuffer: ByteBuffer,
            signatureLength: Int,
            signatureLimit: Int,
            page: Int,
            offset: Int,
            beforeBuffer: ByteBuffer,
            beforeLength: Int,
            afterBuffer: ByteBuffer,
            afterLength: Int,
            planIndex: Int,
            stage: Int,
            requestOutput: ByteBuffer,
        ): Long {
            capture(page, offset, beforeBuffer, beforeLength, afterBuffer, afterLength)
            filledStages += stage
            val request = byteArrayOf(((stage + 1) * 0x10).toByte())
            requestOutput.put(request)
            val expected = if (stage == 1) 7 else 9
            return (request.size.toLong() shl 32) or expected.toLong()
        }

        override fun verifyGuardedWriteStage(
            handle: Long,
            signatureBuffer: ByteBuffer,
            signatureLength: Int,
            signatureLimit: Int,
            page: Int,
            offset: Int,
            beforeBuffer: ByteBuffer,
            beforeLength: Int,
            afterBuffer: ByteBuffer,
            afterLength: Int,
            planIndex: Int,
            stage: Int,
            responseBuffer: ByteBuffer,
            responseLength: Int,
        ): Int {
            capture(page, offset, beforeBuffer, beforeLength, afterBuffer, afterLength)
            verifiedStages += stage
            return verifyStatus
        }

        private fun capture(
            page: Int,
            offset: Int,
            beforeBuffer: ByteBuffer,
            beforeLength: Int,
            afterBuffer: ByteBuffer,
            afterLength: Int,
        ) {
            lastPage = page
            lastOffset = offset
            lastBefore = copyBytes(beforeBuffer, beforeLength)
            lastAfter = copyBytes(afterBuffer, afterLength)
        }

        private fun copyBytes(
            buffer: ByteBuffer,
            length: Int,
        ): ByteArray {
            val duplicate = buffer.duplicate()
            duplicate.position(0)
            return ByteArray(length).also(duplicate::get)
        }
    }
}
