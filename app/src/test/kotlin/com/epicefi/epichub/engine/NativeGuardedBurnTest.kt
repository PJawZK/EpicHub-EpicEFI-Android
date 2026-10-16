package com.epicefi.epichub.engine

import java.nio.ByteBuffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeGuardedBurnTest {
    private val signature = "exact-burn-signature".encodeToByteArray()

    @Test
    fun createOwnsOpaqueNativeHandleAndPublishesMetadata() {
        val ops = FakeOps()
        val result =
            NativeGuardedBurn.createWithOps(
                engineHandleProvider = { 11L },
                registryHandleProvider = { 22L },
                signatureResponse = signature,
                signatureLimit = 64,
                page = 3,
                ops = ops,
            )

        assertTrue(result is GuardedBurnCreateResult.Created)
        val plan = (result as GuardedBurnCreateResult.Created).plan
        assertEquals(
            GuardedBurnMetadata(
                profileGeneration = 91,
                draftRevision = 7,
                page = 3,
            ),
            plan.metadata,
        )
        assertTrue(plan.isAlive)
        assertEquals(11L, ops.lastEngineHandle)
        assertEquals(22L, ops.lastRegistryHandle)
        assertEquals(3, ops.lastPage)
        assertEquals(signature.toList(), ops.lastSignature.toList())

        plan.close()
        plan.close()
        assertFalse(plan.isAlive)
        assertEquals(1, ops.closeCalls)
    }

    @Test
    fun burnAndCrcStagesComeOnlyFromOpaqueHandleAndCurrentAuthority() {
        val ops = FakeOps()
        val plan = createdPlan(ops)

        val burn = plan.stagePlan(GuardedBurnStage.Burn)
        assertTrue(burn is GuardedBurnStageResult.Planned)
        burn as GuardedBurnStageResult.Planned
        assertEquals(listOf(0x42.toByte(), 0x43.toByte()), burn.plan.request.toList())
        assertEquals(7, burn.plan.expectedResponseBytes)
        assertEquals(0, ops.lastStage)
        assertEquals(41L, ops.lastPlanHandle)
        assertEquals(11L, ops.lastEngineHandle)
        assertEquals(22L, ops.lastRegistryHandle)
        assertEquals(signature.toList(), ops.lastSignature.toList())

        ops.request = byteArrayOf(0x4b, 0x00, 0x01)
        ops.expectedResponseBytes = 11
        val crc = plan.stagePlan(GuardedBurnStage.Crc)
        assertTrue(crc is GuardedBurnStageResult.Planned)
        crc as GuardedBurnStageResult.Planned
        assertEquals(ops.request.toList(), crc.plan.request.toList())
        assertEquals(11, crc.plan.expectedResponseBytes)
        assertEquals(1, ops.lastStage)

        plan.close()
    }

    @Test
    fun authorityAndProofFailuresRemainDistinct() {
        val ops = FakeOps()
        val plan = createdPlan(ops)

        ops.fillStatus = -14L
        assertEquals(
            GuardedBurnStageResult.Rejected(
                GuardedBurnRejection.RamNotConfirmedForCurrentDraft,
            ),
            plan.stagePlan(GuardedBurnStage.Burn),
        )

        ops.fillStatus = -15L
        assertEquals(
            GuardedBurnStageResult.Rejected(GuardedBurnRejection.StaleProfileGeneration),
            plan.stagePlan(GuardedBurnStage.Burn),
        )

        ops.fillStatus = -16L
        assertEquals(
            GuardedBurnStageResult.Rejected(GuardedBurnRejection.SourceImageChanged),
            plan.stagePlan(GuardedBurnStage.Crc),
        )

        ops.fillStatus = null
        ops.verifyStatus = -19
        assertEquals(
            GuardedBurnVerifyResult.Rejected(GuardedBurnRejection.BurnProtocolFailure),
            plan.verifyStage(GuardedBurnStage.Burn, byteArrayOf(1)),
        )

        ops.verifyStatus = -20
        assertEquals(
            GuardedBurnVerifyResult.Rejected(GuardedBurnRejection.CrcVerificationFailure),
            plan.verifyStage(GuardedBurnStage.Crc, byteArrayOf(1)),
        )

        plan.close()
    }

    @Test
    fun verificationForwardsOnlyOpaqueOwnerAuthorityAndResponse() {
        val ops = FakeOps()
        val plan = createdPlan(ops)
        val response = byteArrayOf(7, 8, 9)

        assertEquals(
            GuardedBurnVerifyResult.Verified,
            plan.verifyStage(GuardedBurnStage.Burn, response),
        )
        assertEquals(0, ops.lastStage)
        assertEquals(41L, ops.lastPlanHandle)
        assertEquals(11L, ops.lastEngineHandle)
        assertEquals(22L, ops.lastRegistryHandle)
        assertEquals(signature.toList(), ops.lastSignature.toList())
        assertEquals(response.toList(), ops.lastResponse.toList())

        plan.close()
    }

    @Test
    fun invalidCreationAndClosedOwnerFailBeforeNativeStageWork() {
        val ops = FakeOps()
        assertEquals(
            GuardedBurnCreateResult.Rejected(GuardedBurnRejection.InvalidArgument),
            NativeGuardedBurn.createWithOps(
                engineHandleProvider = { 11L },
                registryHandleProvider = { 22L },
                signatureResponse = byteArrayOf(),
                signatureLimit = 64,
                page = 1,
                ops = ops,
            ),
        )
        assertEquals(0, ops.createCalls)

        assertEquals(
            GuardedBurnCreateResult.Rejected(GuardedBurnRejection.InvalidArgument),
            NativeGuardedBurn.createWithOps(
                engineHandleProvider = { 11L },
                registryHandleProvider = { 22L },
                signatureResponse = signature,
                signatureLimit = 64,
                page = 0,
                ops = ops,
            ),
        )
        assertEquals(0, ops.createCalls)

        val plan = createdPlan(ops)
        plan.close()
        assertEquals(
            GuardedBurnStageResult.Rejected(GuardedBurnRejection.InvalidHandle),
            plan.stagePlan(GuardedBurnStage.Burn),
        )
        assertEquals(0, ops.fillCalls)
    }

    @Test
    fun emptyOrOversizedVerificationResponseFailsBeforeNativeVerifier() {
        val ops = FakeOps()
        val plan = createdPlan(ops)

        assertEquals(
            GuardedBurnVerifyResult.Rejected(GuardedBurnRejection.InvalidArgument),
            plan.verifyStage(GuardedBurnStage.Burn, byteArrayOf()),
        )
        assertEquals(0, ops.verifyCalls)

        plan.close()
    }

    private fun createdPlan(ops: FakeOps): NativeGuardedBurn {
        val result =
            NativeGuardedBurn.createWithOps(
                engineHandleProvider = { 11L },
                registryHandleProvider = { 22L },
                signatureResponse = signature,
                signatureLimit = 64,
                page = 3,
                ops = ops,
            )
        return (result as GuardedBurnCreateResult.Created).plan
    }

    private class FakeOps : NativeGuardedBurnOps {
        var createCalls = 0
        var closeCalls = 0
        var fillCalls = 0
        var verifyCalls = 0
        var alive = true
        var fillStatus: Long? = null
        var verifyStatus = 0
        var request = byteArrayOf(0x42, 0x43)
        var expectedResponseBytes = 7
        var lastPlanHandle = -1L
        var lastEngineHandle = -1L
        var lastRegistryHandle = -1L
        var lastPage = -1
        var lastStage = -1
        var lastSignature = byteArrayOf()
        var lastResponse = byteArrayOf()

        override fun create(
            engineHandle: Long,
            registryHandle: Long,
            signatureBuffer: ByteBuffer,
            signatureLength: Int,
            signatureLimit: Int,
            page: Int,
        ): Long {
            createCalls += 1
            lastEngineHandle = engineHandle
            lastRegistryHandle = registryHandle
            lastPage = page
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

        override fun page(handle: Long): Int = 3

        override fun fillStage(
            planHandle: Long,
            engineHandle: Long,
            registryHandle: Long,
            signatureBuffer: ByteBuffer,
            signatureLength: Int,
            signatureLimit: Int,
            stage: Int,
            requestOutput: ByteBuffer,
        ): Long {
            fillCalls += 1
            lastPlanHandle = planHandle
            lastEngineHandle = engineHandle
            lastRegistryHandle = registryHandle
            lastStage = stage
            lastSignature = copyBytes(signatureBuffer, signatureLength)
            fillStatus?.let { return it }
            requestOutput.put(request)
            return (request.size.toLong() shl 32) or expectedResponseBytes.toLong()
        }

        override fun verifyStage(
            planHandle: Long,
            engineHandle: Long,
            registryHandle: Long,
            signatureBuffer: ByteBuffer,
            signatureLength: Int,
            signatureLimit: Int,
            stage: Int,
            responseBuffer: ByteBuffer,
            responseLength: Int,
        ): Int {
            verifyCalls += 1
            lastPlanHandle = planHandle
            lastEngineHandle = engineHandle
            lastRegistryHandle = registryHandle
            lastStage = stage
            lastSignature = copyBytes(signatureBuffer, signatureLength)
            lastResponse = copyBytes(responseBuffer, responseLength)
            return verifyStatus
        }

        private fun copyBytes(buffer: ByteBuffer, length: Int): ByteArray {
            val copy = buffer.duplicate()
            copy.position(0)
            val bytes = ByteArray(length)
            copy.get(bytes)
            return bytes
        }
    }
}
