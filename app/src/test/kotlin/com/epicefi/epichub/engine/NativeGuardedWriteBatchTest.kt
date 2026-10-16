package com.epicefi.epichub.engine

import java.nio.ByteBuffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeGuardedWriteBatchTest {
    private val signature = "exact-signature".encodeToByteArray()

    @Test
    fun createOwnsOpaqueNativeHandleAndPublishesMetadata() {
        val ops = FakeOps()
        val result =
            NativeGuardedWriteBatch.createWithOps(
                engineHandleProvider = { 11L },
                registryHandleProvider = { 22L },
                signatureResponse = signature,
                signatureLimit = 64,
                expectedDraftRevision = 7,
                ops = ops,
            )

        assertTrue(result is GuardedWriteBatchCreateResult.Created)
        val batch = (result as GuardedWriteBatchCreateResult.Created).batch
        assertEquals(
            GuardedWriteBatchMetadata(
                profileGeneration = 91,
                draftRevision = 7,
                transactionCount = 2,
            ),
            batch.metadata,
        )
        assertTrue(batch.isAlive)
        assertEquals(11L, ops.lastEngineHandle)
        assertEquals(22L, ops.lastRegistryHandle)
        assertEquals(7L, ops.lastExpectedRevision)
        assertEquals(signature.toList(), ops.lastSignature.toList())

        batch.close()
        batch.close()
        assertFalse(batch.isAlive)
        assertEquals(1, ops.closeCalls)
    }

    @Test
    fun stagePlansComeOnlyFromOpaqueBatchHandleAndCurrentAuthorityInputs() {
        val ops = FakeOps()
        val batch = createdBatch(ops)

        val result = batch.stagePlan(1, GuardedRamWriteStage.Write)

        assertTrue(result is GuardedWriteBatchStageResult.Planned)
        result as GuardedWriteBatchStageResult.Planned
        assertEquals(listOf(0x42.toByte(), 0x43.toByte()), result.plan.request.toList())
        assertEquals(7, result.plan.expectedResponseBytes)
        assertEquals(41L, ops.lastBatchHandle)
        assertEquals(11L, ops.lastEngineHandle)
        assertEquals(22L, ops.lastRegistryHandle)
        assertEquals(1, ops.lastTransactionIndex)
        assertEquals(1, ops.lastStage)
        assertEquals(signature.toList(), ops.lastSignature.toList())

        batch.close()
    }

    @Test
    fun staleBatchAndSourceChangesRemainDistinctAtEveryStageBoundary() {
        val ops = FakeOps()
        val batch = createdBatch(ops)

        ops.fillStatus = -14L
        assertEquals(
            GuardedWriteBatchStageResult.Rejected(GuardedWriteBatchRejection.StaleDraftRevision),
            batch.stagePlan(0, GuardedRamWriteStage.Precondition),
        )

        ops.fillStatus = -16L
        assertEquals(
            GuardedWriteBatchStageResult.Rejected(GuardedWriteBatchRejection.SourceImageChanged),
            batch.stagePlan(0, GuardedRamWriteStage.Write),
        )

        ops.fillStatus = null
        ops.verifyStatus = -15
        assertEquals(
            GuardedWriteBatchVerifyResult.Rejected(
                GuardedWriteBatchRejection.StaleProfileGeneration,
            ),
            batch.verifyStage(0, GuardedRamWriteStage.Readback, byteArrayOf(1)),
        )

        batch.close()
    }

    @Test
    fun verificationKeepsPreconditionAckAndReadbackFailuresDistinct() {
        val ops = FakeOps()
        val batch = createdBatch(ops)

        ops.verifyStatus = -20
        assertEquals(
            GuardedWriteBatchVerifyResult.Rejected(GuardedWriteBatchRejection.StalePrecondition),
            batch.verifyStage(0, GuardedRamWriteStage.Precondition, byteArrayOf(1)),
        )

        ops.verifyStatus = -21
        assertEquals(
            GuardedWriteBatchVerifyResult.Rejected(
                GuardedWriteBatchRejection.WriteAcknowledgementFailure,
            ),
            batch.verifyStage(0, GuardedRamWriteStage.Write, byteArrayOf(1)),
        )

        ops.verifyStatus = -23
        assertEquals(
            GuardedWriteBatchVerifyResult.Rejected(GuardedWriteBatchRejection.ReadbackMismatch),
            batch.verifyStage(0, GuardedRamWriteStage.Readback, byteArrayOf(1)),
        )

        batch.close()
    }

    @Test
    fun verifiedPrefixReconciliationUsesOnlyOpaqueOwnerAuthority() {
        val ops = FakeOps()
        val batch = createdBatch(ops)

        assertEquals(
            GuardedWriteBatchReconciliationResult.Reconciled,
            batch.reconcileVerifiedPrefix(1),
        )
        assertEquals(1, ops.lastVerifiedTransactionCount)
        assertEquals(41L, ops.lastBatchHandle)
        assertEquals(11L, ops.lastEngineHandle)
        assertEquals(22L, ops.lastRegistryHandle)
        assertEquals(signature.toList(), ops.lastSignature.toList())

        ops.reconcileStatus = -28
        assertEquals(
            GuardedWriteBatchReconciliationResult.Rejected(
                GuardedWriteBatchReconciliationRejection.SourceReconciliationFailure,
            ),
            batch.reconcileVerifiedPrefix(1),
        )

        batch.close()
    }

    @Test
    fun invalidVerifiedPrefixFailsBeforeNativeReconciliation() {
        val ops = FakeOps()
        val batch = createdBatch(ops)

        assertEquals(
            GuardedWriteBatchReconciliationResult.Rejected(
                GuardedWriteBatchReconciliationRejection.InvalidVerifiedPrefix,
            ),
            batch.reconcileVerifiedPrefix(3),
        )
        assertEquals(0, ops.reconcileCalls)

        batch.close()
    }

    @Test
    fun invalidCreationAndClosedOwnerFailBeforeNativeStageWork() {
        val ops = FakeOps()
        assertEquals(
            GuardedWriteBatchCreateResult.Rejected(GuardedWriteBatchRejection.InvalidArgument),
            NativeGuardedWriteBatch.createWithOps(
                engineHandleProvider = { 11L },
                registryHandleProvider = { 22L },
                signatureResponse = byteArrayOf(),
                signatureLimit = 64,
                expectedDraftRevision = 0,
                ops = ops,
            ),
        )
        assertEquals(0, ops.createCalls)

        val batch = createdBatch(ops)
        batch.close()
        assertEquals(
            GuardedWriteBatchStageResult.Rejected(GuardedWriteBatchRejection.InvalidHandle),
            batch.stagePlan(0, GuardedRamWriteStage.Precondition),
        )
        assertEquals(0, ops.fillCalls)
    }

    private fun createdBatch(ops: FakeOps): NativeGuardedWriteBatch {
        val result =
            NativeGuardedWriteBatch.createWithOps(
                engineHandleProvider = { 11L },
                registryHandleProvider = { 22L },
                signatureResponse = signature,
                signatureLimit = 64,
                expectedDraftRevision = 7,
                ops = ops,
            )
        return (result as GuardedWriteBatchCreateResult.Created).batch
    }

    private class FakeOps : NativeGuardedWriteBatchOps {
        var createCalls = 0
        var closeCalls = 0
        var fillCalls = 0
        var reconcileCalls = 0
        var alive = true
        var fillStatus: Long? = null
        var verifyStatus = 0
        var reconcileStatus = 0
        var lastBatchHandle = -1L
        var lastEngineHandle = -1L
        var lastRegistryHandle = -1L
        var lastExpectedRevision = -1L
        var lastTransactionIndex = -1
        var lastVerifiedTransactionCount = -1
        var lastStage = -1
        var lastSignature = byteArrayOf()

        override fun create(
            engineHandle: Long,
            registryHandle: Long,
            signatureBuffer: ByteBuffer,
            signatureLength: Int,
            signatureLimit: Int,
            expectedDraftRevision: Long,
        ): Long {
            createCalls += 1
            lastEngineHandle = engineHandle
            lastRegistryHandle = registryHandle
            lastExpectedRevision = expectedDraftRevision
            lastSignature = copyBytes(signatureBuffer, signatureLength)
            alive = true
            return 41L
        }

        override fun close(handle: Long) {
            closeCalls += 1
            alive = false
        }

        override fun isAlive(handle: Long): Boolean = alive && handle == 41L

        override fun activeCount(): Int = if (alive) 1 else 0

        override fun profileGeneration(handle: Long): Long = 91L

        override fun draftRevision(handle: Long): Long = 7L

        override fun transactionCount(handle: Long): Int = 2

        override fun fillStage(
            batchHandle: Long,
            engineHandle: Long,
            registryHandle: Long,
            signatureBuffer: ByteBuffer,
            signatureLength: Int,
            signatureLimit: Int,
            transactionIndex: Int,
            stage: Int,
            requestOutput: ByteBuffer,
        ): Long {
            fillCalls += 1
            lastBatchHandle = batchHandle
            lastEngineHandle = engineHandle
            lastRegistryHandle = registryHandle
            lastTransactionIndex = transactionIndex
            lastStage = stage
            lastSignature = copyBytes(signatureBuffer, signatureLength)
            fillStatus?.let { return it }
            val request = byteArrayOf(0x42, 0x43)
            requestOutput.put(request)
            val expected = if (stage == 1) 7 else 9
            return (request.size.toLong() shl 32) or expected.toLong()
        }

        override fun verifyStage(
            batchHandle: Long,
            engineHandle: Long,
            registryHandle: Long,
            signatureBuffer: ByteBuffer,
            signatureLength: Int,
            signatureLimit: Int,
            transactionIndex: Int,
            stage: Int,
            responseBuffer: ByteBuffer,
            responseLength: Int,
        ): Int {
            lastBatchHandle = batchHandle
            lastEngineHandle = engineHandle
            lastRegistryHandle = registryHandle
            lastTransactionIndex = transactionIndex
            lastStage = stage
            lastSignature = copyBytes(signatureBuffer, signatureLength)
            return verifyStatus
        }

        override fun reconcileVerifiedPrefix(
            batchHandle: Long,
            engineHandle: Long,
            registryHandle: Long,
            signatureBuffer: ByteBuffer,
            signatureLength: Int,
            signatureLimit: Int,
            verifiedTransactionCount: Int,
        ): Int {
            reconcileCalls += 1
            lastBatchHandle = batchHandle
            lastEngineHandle = engineHandle
            lastRegistryHandle = registryHandle
            lastVerifiedTransactionCount = verifiedTransactionCount
            lastSignature = copyBytes(signatureBuffer, signatureLength)
            return reconcileStatus
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
