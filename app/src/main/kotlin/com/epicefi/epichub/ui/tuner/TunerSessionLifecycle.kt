package com.epicefi.epichub.ui.tuner

internal fun tuneSessionAuthorityChanged(
    previousEpoch: Long?,
    currentEpoch: Long,
): Boolean = previousEpoch == null || previousEpoch != currentEpoch
