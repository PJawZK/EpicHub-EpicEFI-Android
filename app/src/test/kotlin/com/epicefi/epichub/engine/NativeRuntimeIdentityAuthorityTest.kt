package com.epicefi.epichub.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeRuntimeIdentityAuthorityTest {
    @Test
    fun accepted_native_status_returns_exact_signature_without_normalization() {
        val signature = "rusEFI master.2026.08.26.MEGA144H7.2273317132"
        val authority = authorityReturning(RUNTIME_SIGNATURE_STATUS_OK)

        val result = authority.validate(signature.toByteArray(), 256)

        assertEquals(
            RuntimeIdentityValidationResult.Accepted(
                ValidatedRuntimeIdentity(signature),
            ),
            result,
        )
    }

    @Test
    fun rejection_statuses_remain_explicit_and_fail_closed() {
        val cases =
            listOf(
                RUNTIME_SIGNATURE_STATUS_NOT_DIRECT to RuntimeIdentityRejection.NotDirectBuffer,
                RUNTIME_SIGNATURE_STATUS_INVALID_LENGTH to RuntimeIdentityRejection.InvalidLength,
                RUNTIME_SIGNATURE_STATUS_INVALID_LIMIT to RuntimeIdentityRejection.InvalidResponseLimit,
                RUNTIME_SIGNATURE_STATUS_EMPTY to RuntimeIdentityRejection.EmptySignature,
                RUNTIME_SIGNATURE_STATUS_INVALID_UTF8 to RuntimeIdentityRejection.InvalidUtf8,
                RUNTIME_SIGNATURE_STATUS_TOO_LARGE to RuntimeIdentityRejection.ResponseTooLarge,
                RUNTIME_SIGNATURE_STATUS_PANIC to RuntimeIdentityRejection.NativeFailure,
                999 to RuntimeIdentityRejection.NativeFailure,
            )

        cases.forEach { (status, expected) ->
            assertEquals(
                RuntimeIdentityValidationResult.Rejected(expected),
                authorityReturning(status).validate("response".toByteArray(), 256),
            )
        }
    }

    @Test
    fun forwards_exact_response_length_and_declared_bound_to_native_validator() {
        var observedLength = -1
        var observedLimit = -1
        var observedDirect = false
        val authority =
            NativeRuntimeIdentityAuthority(
                RuntimeSignatureValidator { buffer, responseLength, maxResponseBytes ->
                    observedLength = responseLength
                    observedLimit = maxResponseBytes
                    observedDirect = buffer.isDirect
                    RUNTIME_SIGNATURE_STATUS_OK
                },
            )

        val result = authority.validate("abc".toByteArray(), 17)

        assertTrue(result is RuntimeIdentityValidationResult.Accepted)
        assertEquals(3, observedLength)
        assertEquals(17, observedLimit)
        assertTrue(observedDirect)
    }

    private fun authorityReturning(status: Int) =
        NativeRuntimeIdentityAuthority(
            RuntimeSignatureValidator { _, _, _ -> status },
        )
}
