package com.epicefi.epichub.ui.analyzer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.epicefi.epichub.ui.components.StructuralCard

@Composable
fun AnalyzerScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Analyzer", style = MaterialTheme.typography.headlineMedium)
        StructuralCard(
            title = "Source / comparison",
            modifier = Modifier.fillMaxWidth(),
            subtitle = "Generic live/file measurement exploration.",
        ) {
            Text("No measurement file source is attached yet.")
        }
        StructuralCard(
            title = "Plot workspace",
            modifier = Modifier.fillMaxWidth(),
            subtitle = "Stacked/free-form plots, cursor, markers and viewport queries will live here.",
        ) {
            Text("Shared plotting infrastructure placeholder")
        }
        StructuralCard(
            title = "Inspector",
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Statistics, events, source identity and query results")
        }
    }
}
