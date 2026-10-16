package com.epicefi.epichub.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class TunerTableSelectionOwnerTest {
    private val catalog = TunerTableCatalog(
        profileGeneration = 42,
        draftRevision = 7,
        origin = TuneSourceOrigin.LIVE_CONTROLLER,
        tables = listOf(
            TunerTableCatalogEntry(
                tableId = 11,
                name = "Fuel VE",
                rows = 16,
                columns = 16,
                displayDecimals = 1,
            ),
            TunerTableCatalogEntry(
                tableId = 23,
                name = "Ignition",
                rows = 16,
                columns = 16,
                displayDecimals = 1,
            ),
        ),
    )

    @Test
    fun `missing selection falls back to native catalog default`() {
        assertEquals(11, resolveTunerTableSelection(catalog, null).tableId)
        assertEquals(11, resolveTunerTableSelection(catalog, 999).tableId)
    }

    @Test
    fun `existing semantic table selection is preserved`() {
        val selected = resolveTunerTableSelection(catalog, 23)

        assertEquals(23, selected.tableId)
        assertEquals("Ignition", selected.name)
    }

    @Test
    fun `explicit selection rejects table ids outside current native catalog`() {
        val error = assertThrows(IllegalStateException::class.java) {
            requireTunerTableSelection(catalog, 999)
        }

        assertEquals(
            "Tune table 999 is not present in the active native catalog.",
            error.message,
        )
    }

    @Test
    fun `selection remains semantic when catalog order changes`() {
        val reordered = catalog.copy(tables = catalog.tables.reversed())

        assertEquals(23, resolveTunerTableSelection(reordered, 23).tableId)
    }

    @Test
    fun `selection falls back when a replacement session removes the selected table`() {
        val replacement = catalog.copy(
            profileGeneration = 43,
            draftRevision = 0,
            tables = listOf(catalog.tables.first()),
        )

        assertEquals(11, resolveTunerTableSelection(replacement, 23).tableId)
    }
}
