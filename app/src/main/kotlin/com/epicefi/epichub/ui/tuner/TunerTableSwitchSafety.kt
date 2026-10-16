package com.epicefi.epichub.ui.tuner

internal fun resetInteractionForTableSwitch(
    @Suppress("UNUSED_PARAMETER") previous: TunerInteractionState,
): TunerInteractionState = TunerInteractionState()

internal fun previewBelongsToSelectedTable(
    previewTableId: Int,
    selectedTableId: Int,
): Boolean = previewTableId == selectedTableId
