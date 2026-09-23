package com.epicefi.epichub.diagnostics

import java.util.ArrayDeque

class DiagnosticRing(
    private val capacity: Int = 512,
) {
    init {
        require(capacity > 0) { "Diagnostic ring capacity must be positive." }
    }

    private val events = ArrayDeque<DiagnosticEvent>(capacity)
    private var droppedTotal = 0L

    @Synchronized
    fun add(event: DiagnosticEvent): Boolean {
        val dropped = events.size == capacity
        if (dropped) {
            events.removeFirst()
            droppedTotal = if (droppedTotal == Long.MAX_VALUE) Long.MAX_VALUE else droppedTotal + 1
        }
        events.addLast(event)
        return dropped
    }

    @Synchronized
    fun snapshot(): List<DiagnosticEvent> = events.toList()

    @Synchronized
    fun snapshotSince(eventIdExclusive: Long): List<DiagnosticEvent> =
        events.filter { it.eventId > eventIdExclusive }

    @Synchronized
    fun size(): Int = events.size

    @Synchronized
    fun droppedTotal(): Long = droppedTotal

    @Synchronized
    fun newestEventId(): Long = events.peekLast()?.eventId ?: 0L
}
