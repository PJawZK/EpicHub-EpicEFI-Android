package com.epicefi.epichub.ui.tuner

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TunerSessionLifecycleTest {
    @Test
    fun `same session epoch does not invalidate presentation`() {
        assertFalse(tuneSessionAuthorityChanged(previousEpoch = 7, currentEpoch = 7))
    }

    @Test
    fun `genuine session replacement invalidates presentation`() {
        assertTrue(tuneSessionAuthorityChanged(previousEpoch = 7, currentEpoch = 8))
    }

    @Test
    fun `missing initial presentation requires one coherent refresh`() {
        assertTrue(tuneSessionAuthorityChanged(previousEpoch = null, currentEpoch = 1))
    }
}
