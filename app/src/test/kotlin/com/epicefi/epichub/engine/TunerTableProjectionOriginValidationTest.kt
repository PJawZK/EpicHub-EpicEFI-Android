package com.epicefi.epichub.engine

import org.junit.Assert.assertThrows
import org.junit.Test

class TunerTableProjectionOriginValidationTest {
    @Test
    fun known_origin_and_flag_combinations_are_accepted() {
        validateTunerProjectionOrigin(
            originCode = 1,
            flags = TABLE_PROJECTION_FLAG_OFFLINE or TABLE_PROJECTION_FLAG_FIXTURE,
        )
        validateTunerProjectionOrigin(
            originCode = 2,
            flags = TABLE_PROJECTION_FLAG_OFFLINE,
        )
        validateTunerProjectionOrigin(
            originCode = 3,
            flags = 0,
        )
    }

    @Test
    fun unknown_origin_is_rejected() {
        assertThrows(IllegalArgumentException::class.java) {
            validateTunerProjectionOrigin(originCode = 4, flags = 0)
        }
    }

    @Test
    fun unknown_flag_bit_is_rejected() {
        assertThrows(IllegalArgumentException::class.java) {
            validateTunerProjectionOrigin(originCode = 3, flags = 1 shl 7)
        }
    }

    @Test
    fun live_origin_cannot_claim_offline_flags() {
        assertThrows(IllegalArgumentException::class.java) {
            validateTunerProjectionOrigin(
                originCode = 3,
                flags = TABLE_PROJECTION_FLAG_OFFLINE,
            )
        }
    }

    @Test
    fun fixture_origin_requires_both_offline_and_fixture_flags() {
        assertThrows(IllegalArgumentException::class.java) {
            validateTunerProjectionOrigin(
                originCode = 1,
                flags = TABLE_PROJECTION_FLAG_FIXTURE,
            )
        }
    }
}
