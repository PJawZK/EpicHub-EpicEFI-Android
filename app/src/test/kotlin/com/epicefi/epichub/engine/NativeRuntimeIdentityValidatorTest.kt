package com.epicefi.epichub.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeRuntimeIdentityValidatorTest {
    @Test
    fun preserves_exact_signature_bytes_after_native_success() {
        val signature = "rusEFI master.2026.08.26.MEGA144H7.2273317132"

        val result = runtimeIdentityValidationFromNativeStatus(0, signature.toByteArray())

        val valid = result as RuntimeIdentityValidationResult.Valid
        assertEquals(signature, valid.identity.signature)
    }

    @Test
    fun maps_native_rejection_statuses_without_creating_identity() {
        val cases =
            mapOf(
                1 to RuntimeIdentityValidationError.INVALID_RESPONSE_LIMIT,
                2 to RuntimeIdentityValidationError.RESPONSE_TOO_LARGE,
                3 to RuntimeIdentityValidationError.EMPTY_SIGNATURE,
                4 to RuntimeIdentityValidationError.INVALID_UTF8,
                5 to RuntimeIdentityValidationError.JNI_ERROR,
                6 to RuntimeIdentityValidationError.INTERNAL_ERROR,
                255 to RuntimeIdentityValidationError.NATIVE_PANIC,
                99 to RuntimeIdentityValidationError.UNKNOWN_STATUS,
            )

        cases.forEach { (status, expectedError) ->
            val result = runtimeIdentityValidationFromNativeStatus(status, byteArrayOf(1))
            assertTrue(result is RuntimeIdentityValidationResult.Rejected)
            val rejected = result as RuntimeIdentityValidationResult.Rejected
            assertEquals(expectedError, rejected.error)
            assertEquals(status, rejected.nativeStatus)
        }
    }
}
