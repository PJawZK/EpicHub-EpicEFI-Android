package com.epicefi.epichub.platform.transport

enum class TransportKind {
    USB,
    SERIAL,
    TCP,
    CAN,
}

data class TransportCandidate(
    val kind: TransportKind,
    val endpointId: String,
    val displayName: String?,
    val attributes: Map<String, String> = emptyMap(),
)
