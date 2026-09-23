package com.epicefi.epichub.navigation

enum class AppDestination(
    val id: String,
    val label: String,
    val compactLabel: String,
    val glyph: String,
) {
    DASHBOARD("dashboard", "Dashboard", "Dash", "D"),
    TUNER("tuner", "Tuner", "Tuner", "T"),
    TUNING_TOOLS("tuning-tools", "Tuning Tools", "Tools", "TT"),
    LOGGER("logger", "Logger", "Log", "L"),
    ANALYZER("analyzer", "Analyzer", "Analyze", "A"),
    DIAGNOSTICS("diagnostics", "Diagnostics", "Diag", "!"),
    ;

    companion object {
        fun fromId(id: String?): AppDestination = entries.firstOrNull { it.id == id } ?: DASHBOARD
    }
}
