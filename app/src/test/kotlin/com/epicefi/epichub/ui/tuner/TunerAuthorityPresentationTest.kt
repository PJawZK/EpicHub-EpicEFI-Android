package com.epicefi.epichub.ui.tuner

import com.epicefi.epichub.engine.TABLE_PROJECTION_FLAG_FIXTURE
import com.epicefi.epichub.engine.TABLE_PROJECTION_FLAG_OFFLINE
import com.epicefi.epichub.engine.TunerTableProjection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TunerAuthorityPresentationTest {
    @Test
    fun live_controller_source_is_presented_as_live_but_draft_apply_remains_local() {
        val presentation = tunerAuthorityPresentation(projection(flags = 0))

        assertEquals("Live controller tune · local draft", presentation.subtitle)
        assertEquals("LIVE CONTROLLER SOURCE · LOCAL SEMANTIC DRAFT", presentation.headline)
        assertEquals("live controller source", presentation.tableSourceLabel)
        assertTrue(presentation.detail.contains("Local Apply"))
        assertTrue(presentation.detail.contains("Write to RAM"))
        assertTrue(presentation.detail.contains("Burn"))
    }

    @Test
    fun imported_source_is_presented_as_offline_without_claiming_ram_authority() {
        val presentation = tunerAuthorityPresentation(
            projection(flags = TABLE_PROJECTION_FLAG_OFFLINE),
        )

        assertEquals("Imported tune · local draft", presentation.subtitle)
        assertEquals("IMPORTED TUNE · LOCAL SEMANTIC DRAFT", presentation.headline)
        assertEquals("imported source", presentation.tableSourceLabel)
        assertTrue(presentation.detail.contains("no live ECU RAM authority", ignoreCase = true))
    }

    @Test
    fun fixture_flag_takes_precedence_over_generic_offline_flag() {
        val presentation = tunerAuthorityPresentation(
            projection(flags = TABLE_PROJECTION_FLAG_OFFLINE or TABLE_PROJECTION_FLAG_FIXTURE),
        )

        assertEquals("Offline fixture · local draft", presentation.subtitle)
        assertEquals("FIXTURE SOURCE · LOCAL SEMANTIC DRAFT", presentation.headline)
        assertEquals("fixture source", presentation.tableSourceLabel)
    }

    @Test
    fun missing_projection_fails_closed() {
        val presentation = tunerAuthorityPresentation(null)

        assertEquals("No active TuneSession", presentation.subtitle)
        assertEquals("TUNE SOURCE UNAVAILABLE", presentation.headline)
        assertEquals("source unavailable", presentation.tableSourceLabel)
    }

    private fun projection(flags: Int): TunerTableProjection =
        TunerTableProjection(
            profileGeneration = 7,
            draftRevision = 3,
            originCode = when {
                flags and TABLE_PROJECTION_FLAG_FIXTURE != 0 -> 1
                flags and TABLE_PROJECTION_FLAG_OFFLINE != 0 -> 2
                else -> 3
            },
            tableId = 11,
            name = "Test table",
            rows = 1,
            cols = 1,
            displayDecimals = 1,
            flags = flags,
            minimum = 0.0,
            maximum = 100.0,
            quantum = 0.1,
            quantumOrigin = 0.0,
            editIncrement = 0.1,
            xAxis = doubleArrayOf(1000.0),
            yAxis = doubleArrayOf(50.0),
            values = doubleArrayOf(42.0),
            editable = booleanArrayOf(true),
        )
}
