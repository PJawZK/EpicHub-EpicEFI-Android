package com.epicefi.epichub.engine

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineAuthoritySerializationTest {
    @Test
    fun competingAuthorityMutationCannotEnterWhileGuardedTransferOwnsGate() {
        val transferEntered = CountDownLatch(1)
        val releaseTransfer = CountDownLatch(1)
        val mutationAttempted = CountDownLatch(1)
        val mutationEntered = CountDownLatch(1)
        val workers = Executors.newFixedThreadPool(2)

        try {
            val transfer = workers.submit {
                EngineAuthoritySerialization.exclusive {
                    transferEntered.countDown()
                    assertTrue(releaseTransfer.await(5, TimeUnit.SECONDS))
                }
            }
            assertTrue(transferEntered.await(5, TimeUnit.SECONDS))

            val mutation = workers.submit {
                mutationAttempted.countDown()
                EngineAuthoritySerialization.exclusive {
                    mutationEntered.countDown()
                }
            }
            assertTrue(mutationAttempted.await(5, TimeUnit.SECONDS))
            assertEquals(1L, mutationEntered.count)

            releaseTransfer.countDown()
            assertTrue(mutationEntered.await(5, TimeUnit.SECONDS))
            transfer.get(5, TimeUnit.SECONDS)
            mutation.get(5, TimeUnit.SECONDS)
        } finally {
            releaseTransfer.countDown()
            workers.shutdownNow()
        }
    }

    @Test
    fun authorityGateIsReentrantForComposedMutationHelpers() {
        var entered = false

        EngineAuthoritySerialization.exclusive {
            EngineAuthoritySerialization.exclusive {
                entered = true
            }
        }

        assertTrue(entered)
    }
}
