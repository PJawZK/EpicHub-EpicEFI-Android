package com.epicefi.epichub.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppDestinationTest {
    @Test
    fun `four primary workspaces remain distinct`() {
        assertEquals(
            listOf("dashboard", "tuner", "logger", "diagnostics"),
            PrimaryWorkspace.entries.map { it.id },
        )
    }

    @Test
    fun `tuning tools are grouped under tuner without becoming tuner route`() {
        assertEquals(PrimaryWorkspace.TUNER, AppDestination.TUNING_TOOLS.workspace)
        assertTrue(AppDestination.TUNING_TOOLS != AppDestination.TUNER)
        assertEquals(
            listOf(AppDestination.TUNER, AppDestination.TUNING_TOOLS),
            AppDestination.routesFor(PrimaryWorkspace.TUNER),
        )
    }

    @Test
    fun `analyzer is grouped under logger without becoming logger route`() {
        assertEquals(PrimaryWorkspace.LOGGER, AppDestination.ANALYZER.workspace)
        assertTrue(AppDestination.ANALYZER != AppDestination.LOGGER)
        assertEquals(
            listOf(AppDestination.LOGGER, AppDestination.ANALYZER),
            AppDestination.routesFor(PrimaryWorkspace.LOGGER),
        )
    }

    @Test
    fun `each workspace has a stable default leaf route`() {
        assertEquals(AppDestination.DASHBOARD, AppDestination.defaultFor(PrimaryWorkspace.DASHBOARD))
        assertEquals(AppDestination.TUNER, AppDestination.defaultFor(PrimaryWorkspace.TUNER))
        assertEquals(AppDestination.LOGGER, AppDestination.defaultFor(PrimaryWorkspace.LOGGER))
        assertEquals(AppDestination.DIAGNOSTICS, AppDestination.defaultFor(PrimaryWorkspace.DIAGNOSTICS))
    }
}
