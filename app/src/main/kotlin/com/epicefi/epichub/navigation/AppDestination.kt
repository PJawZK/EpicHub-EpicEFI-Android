package com.epicefi.epichub.navigation

enum class PrimaryWorkspace(
    val id: String,
    val label: String,
    val compactLabel: String,
    val glyph: String,
) {
    DASHBOARD("dashboard", "Dashboard", "Dash", "D"),
    TUNER("tuner", "Tuner", "Tuner", "T"),
    LOGGER("logger", "Logger", "Log", "L"),
    DIAGNOSTICS("diagnostics", "Diagnostics", "Diag", "!"),
}

/**
 * A leaf application route. Routes remain distinct even when multiple routes are presented
 * inside one primary user workspace. Presentation grouping must never become domain ownership.
 */
enum class AppDestination(
    val id: String,
    val label: String,
    val compactLabel: String,
    val workspace: PrimaryWorkspace,
) {
    DASHBOARD("dashboard", "Dashboard", "Dashboard", PrimaryWorkspace.DASHBOARD),
    TUNER("tuner", "Tuner", "Tuner", PrimaryWorkspace.TUNER),
    TUNING_TOOLS("tuning-tools", "Tuning Tools", "Tools", PrimaryWorkspace.TUNER),
    LOGGER("logger", "Logger", "Logger", PrimaryWorkspace.LOGGER),
    ANALYZER("analyzer", "Analyzer", "Analyzer", PrimaryWorkspace.LOGGER),
    DIAGNOSTICS("diagnostics", "Diagnostics", "Diagnostics", PrimaryWorkspace.DIAGNOSTICS),
    ;

    companion object {
        fun fromId(id: String?): AppDestination = entries.firstOrNull { it.id == id } ?: DASHBOARD

        fun defaultFor(workspace: PrimaryWorkspace): AppDestination = when (workspace) {
            PrimaryWorkspace.DASHBOARD -> DASHBOARD
            PrimaryWorkspace.TUNER -> TUNER
            PrimaryWorkspace.LOGGER -> LOGGER
            PrimaryWorkspace.DIAGNOSTICS -> DIAGNOSTICS
        }

        fun routesFor(workspace: PrimaryWorkspace): List<AppDestination> =
            entries.filter { it.workspace == workspace }
    }
}
