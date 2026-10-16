package com.epicefi.epichub.engine

import com.epicefi.epichub.platform.transport.usb.UsbReadOnlyTransactionChannel

internal sealed interface LiveTuneBurnResult {
    data class PersistedAndVerified(
        val metadata: GuardedBurnMetadata,
    ) : LiveTuneBurnResult

    data class PlanRejected(
        val reason: GuardedBurnRejection,
    ) : LiveTuneBurnResult

    data class ExecutionRejected(
        val metadata: GuardedBurnMetadata,
        val result: UsbGuardedBurnResult,
    ) : LiveTuneBurnResult
}

internal fun interface LiveTuneBurnOperation {
    fun burn(
        signatureResponse: ByteArray,
        signatureLimit: Int,
        page: Int,
        channel: UsbReadOnlyTransactionChannel,
        timeoutMs: Int,
    ): LiveTuneBurnResult
}

internal class NativeLiveTuneBurnOperation(
    private val engine: NativeEngine,
    private val registry: NativeProfileRegistry,
) : LiveTuneBurnOperation {
    override fun burn(
        signatureResponse: ByteArray,
        signatureLimit: Int,
        page: Int,
        channel: UsbReadOnlyTransactionChannel,
        timeoutMs: Int,
    ): LiveTuneBurnResult {
        if (signatureResponse.isEmpty() || signatureLimit <= 0 || page <= 0 || timeoutMs <= 0) {
            return LiveTuneBurnResult.PlanRejected(GuardedBurnRejection.InvalidArgument)
        }

        return when (
            val created =
                NativeGuardedBurn.create(
                    engine = engine,
                    registry = registry,
                    signatureResponse = signatureResponse,
                    signatureLimit = signatureLimit,
                    page = page,
                )
        ) {
            is GuardedBurnCreateResult.Rejected ->
                LiveTuneBurnResult.PlanRejected(created.reason)

            is GuardedBurnCreateResult.Created ->
                created.plan.use { plan ->
                    when (val executed = UsbGuardedBurnExecutor(plan).execute(channel, timeoutMs)) {
                        UsbGuardedBurnResult.PersistedAndVerified ->
                            LiveTuneBurnResult.PersistedAndVerified(plan.metadata)

                        else ->
                            LiveTuneBurnResult.ExecutionRejected(
                                metadata = plan.metadata,
                                result = executed,
                            )
                    }
                }
        }
    }
}
