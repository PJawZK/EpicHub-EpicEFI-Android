package com.epicefi.epichub.diagnostics

import java.util.ArrayDeque

class DiagnosticRing(
    private val capacity: Int = 512,
) {
    init {
        require(capacity > 0) { "Diagnostic ring capacity must be positive." }
    }

    private val events = ArrayDeque<DiagnosticEvent>(capacity)

    @Synchronized
    fun add(event: DiagnosticEvent) {
        if (events.size == capacity) {
            events.removeFirst()
        }
        events.addLast(event)
    }

    @Synchronized
    fun snapshot(): List<DiagnosticEvent> = events.toList()

    @Synchronized
    fun snapshotSince(eventIdExclusive: Long): List<DiagnosticEvent> =
        events.filter { it.eventId > eventIdExclusive }

    @Synchronized
    fun size(): Int = events.size

    @Synchronized
    fun newestEventId(): Long = events.peekLast()?.eventId ?: 0L
}
