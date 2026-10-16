package com.epicefi.epichub.engine

import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Serializes mutations of the active tuning/profile/source authority with guarded ECU RAM writes.
 *
 * A guarded write must hold this gate continuously from its just-in-time native authorization
 * through the physical USB transfer and verified-source reconciliation. Any operation that can
 * change the TuneSession, retained source image, or profile registry must use the same gate.
 *
 * The lock is deliberately process-local because EpicHub currently owns exactly one app-level
 * NativeEngine session. It is reentrant so authority helpers can safely compose without creating
 * lock-order inversions.
 */
internal object EngineAuthoritySerialization {
    private val lock = ReentrantLock(true)

    fun <T> exclusive(block: () -> T): T = lock.withLock(block)
}
