package com.epicefi.epichub.ui.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.epicefi.epichub.ui.components.KeyValueRow
import com.epicefi.epichub.ui.components.StructuralCard

private data class ToolCard(
    val title: String,
    val status: String,
    val purpose: String,
)

private val tools = listOf(
    ToolCard(
        title = "Acceleration Enrichment",
        status = "Evidence review",
        purpose = "Guided AE capture, evidence and recommendation workflow.",
    ),
    ToolCard(
        title = "PID Controller",
        status = "Evidence review",
        purpose = "Controller characterization and tuning assistance.",
    ),
    ToolCard(
        title = "TCharge",
        status = "Evidence review",
        purpose = "Charge-temperature evidence/model workflow.",
    ),
    ToolCard(
        title = "Injection Angle",
        status = "Evidence review",
        purpose = "Injection-phasing analysis and experiment workflow.",
    ),
)

@Composable
fun TuningToolsScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Tuning Tools", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Native EpicHub tools — not runtime plugins. Shared Rust analysis infrastructure, one tuning authority.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        tools.forEach { tool ->
            StructuralCard(
                title = tool.title,
                modifier = Modifier.fillMaxWidth(),
                subtitle = tool.purpose,
            ) {
                KeyValueRow("Maturity", tool.status)
            }
        }
    }
}
