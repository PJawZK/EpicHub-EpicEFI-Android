package com.epicefi.epichub.ui.tuner

import com.epicefi.epichub.engine.TunerTableProjection

internal data class TunerAuthorityPresentation(
    val subtitle: String,
    val headline: String,
    val detail: String,
    val tableSourceLabel: String,
)

internal fun tunerAuthorityPresentation(
    projection: TunerTableProjection?,
): TunerAuthorityPresentation =
    when {
        projection == null ->
            TunerAuthorityPresentation(
                subtitle = "No active TuneSession",
                headline = "TUNE SOURCE UNAVAILABLE",
                detail = "Load or import a tune source before editing. No ECU RAM or persistence authority is implied.",
                tableSourceLabel = "source unavailable",
            )

        projection.isFixture ->
            TunerAuthorityPresentation(
                subtitle = "Offline fixture · local draft",
                headline = "FIXTURE SOURCE · LOCAL SEMANTIC DRAFT",
                detail = "Fixture data is local test authority only. It does not represent ECU RAM and cannot imply Burn/persistence.",
                tableSourceLabel = "fixture source",
            )

        projection.isOffline ->
            TunerAuthorityPresentation(
                subtitle = "Imported tune · local draft",
                headline = "IMPORTED TUNE · LOCAL SEMANTIC DRAFT",
                detail = "The active source was imported locally. Local Apply edits the semantic draft; no live ECU RAM authority is implied.",
                tableSourceLabel = "imported source",
            )

        else ->
            TunerAuthorityPresentation(
                subtitle = "Live controller tune · local draft",
                headline = "LIVE CONTROLLER SOURCE · LOCAL SEMANTIC DRAFT",
                detail = "The draft originates from a verified live-controller source. Local Apply still changes only the draft; Write to RAM and Burn remain separate actions.",
                tableSourceLabel = "live controller source",
            )
    }
