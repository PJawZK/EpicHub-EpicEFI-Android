package com.epicefi.epichub.diagnostics

class DiagnosticLifecycleRegistry(
    private val retainedDestroyedCapacity: Int = 128,
) {
    init {
        require(retainedDestroyedCapacity > 0) {
            "Retained destroyed lifecycle capacity must be positive."
        }
    }

    private data class MutableOwner(
        val ownerId: String,
        val ownerKind: String,
        val generation: Long,
        val createdAtNanos: Long,
        var currentState: String,
        var destroyedAtNanos: Long?,
        var lateEventCount: Long,
    )

    private val owners = linkedMapOf<String, MutableOwner>()

    @Synchronized
    fun create(
        ownerId: String,
        ownerKind: String,
        generation: Long,
        createdAtNanos: Long,
        initialState: String,
    ): DiagnosticLifecycleOwner {
        val owner = MutableOwner(
            ownerId = ownerId,
            ownerKind = ownerKind,
            generation = generation,
            createdAtNanos = createdAtNanos,
            currentState = initialState,
            destroyedAtNanos = null,
            lateEventCount = 0,
        )
        owners[ownerId] = owner
        return owner.toImmutable()
    }

    @Synchronized
    fun updateState(ownerId: String, state: String): DiagnosticLifecycleOwner? {
        val owner = owners[ownerId] ?: return null
        if (owner.destroyedAtNanos != null) {
            owner.lateEventCount += 1
            return owner.toImmutable()
        }
        owner.currentState = state
        return owner.toImmutable()
    }

    @Synchronized
    fun destroy(ownerId: String, destroyedAtNanos: Long): DiagnosticLifecycleOwner? {
        val owner = owners[ownerId] ?: return null
        if (owner.destroyedAtNanos == null) {
            owner.destroyedAtNanos = destroyedAtNanos
            owner.currentState = "destroyed"
        }
        val result = owner.toImmutable()
        trimDestroyedHistory()
        return result
    }

    @Synchronized
    fun recordLateEvent(ownerId: String): DiagnosticLifecycleOwner? {
        val owner = owners[ownerId] ?: return null
        owner.lateEventCount += 1
        return owner.toImmutable()
    }

    @Synchronized
    fun snapshot(): List<DiagnosticLifecycleOwner> = owners.values.map { it.toImmutable() }

    private fun trimDestroyedHistory() {
        var destroyedCount = owners.values.count { it.destroyedAtNanos != null }
        if (destroyedCount <= retainedDestroyedCapacity) {
            return
        }

        val iterator = owners.entries.iterator()
        while (iterator.hasNext() && destroyedCount > retainedDestroyedCapacity) {
            val entry = iterator.next()
            if (entry.value.destroyedAtNanos != null) {
                iterator.remove()
                destroyedCount -= 1
            }
        }
    }

    private fun MutableOwner.toImmutable() = DiagnosticLifecycleOwner(
        ownerId = ownerId,
        ownerKind = ownerKind,
        generation = generation,
        createdAtNanos = createdAtNanos,
        currentState = currentState,
        destroyedAtNanos = destroyedAtNanos,
        lateEventCount = lateEventCount,
    )
}
