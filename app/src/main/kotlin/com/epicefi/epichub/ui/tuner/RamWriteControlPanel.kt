package com.epicefi.epichub.ui.tuner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.epicefi.epichub.engine.RamSynchronizationStatus
import com.epicefi.epichub.ui.components.StructuralCard

@Composable
fun RamWriteControlPanel(
    state: RamWriteControlState,
    onWriteToRam: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val synchronization = state.synchronization
    StructuralCard(
        title = "ECU RAM",
        subtitle = synchronization?.status?.displayLabel() ?: "No verified live RAM authority",
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (synchronization == null) {
                Text(
                    text = "Connect, exact-authorize, and load the controller before RAM synchronization can be claimed.",
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                Text(
                    text = buildString {
                        append("Draft revision ${synchronization.currentDraftRevision}")
                        synchronization.confirmedDraftRevision?.let { revision ->
                            append(" · RAM confirmed revision $revision")
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
                synchronization.lastReconciliation?.let { evidence ->
                    Text(
                        text =
                            "Last verified prefix ${evidence.verifiedTransactionCount}/${evidence.transactionCount}" +
                                if (evidence.complete) " · complete" else " · incomplete",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            Button(
                enabled = state.actionEnabled,
                onClick = onWriteToRam,
            ) {
                Text(state.actionLabel)
            }

            Text(
                text = "Volatile ECU RAM only · Burn/persistence is separate and is not performed here.",
                style = MaterialTheme.typography.bodySmall,
            )
            state.message?.let { message ->
                Text(message, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private fun RamSynchronizationStatus.displayLabel(): String =
    when (this) {
        RamSynchronizationStatus.FreshLiveControllerConfirmation ->
            "Fresh controller RAM confirmed"
        RamSynchronizationStatus.CurrentDraftConfirmedInRam ->
            "Current draft verified in RAM"
        RamSynchronizationStatus.LocalDraftAheadOfConfirmedRam ->
            "Local draft ahead of RAM"
        RamSynchronizationStatus.PartiallyVerifiedInterruptedWrite ->
            "Interrupted write · partial RAM verification"
        RamSynchronizationStatus.RamUnconfirmedAfterInterruptedWrite ->
            "Interrupted write · RAM unconfirmed"
    }
