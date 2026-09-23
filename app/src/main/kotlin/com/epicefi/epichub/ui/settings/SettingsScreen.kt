package com.epicefi.epichub.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.epicefi.epichub.ui.components.StructuralCard

private val groups = listOf(
    "General",
    "Units & formatting",
    "Dashboard presentation",
    "Tuner presentation",
    "Logging & storage",
    "Connections",
    "Accessories",
    "Diagnostics & privacy",
    "Appearance",
    "About / build information",
)

@Composable
fun SettingsScreen(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Settings", style = MaterialTheme.typography.headlineMedium)
            Button(onClick = onClose) {
                Text("Back to app")
            }
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(groups) { group ->
                StructuralCard(
                    title = group,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Settings surface reserved for this responsibility group.")
                }
            }
        }
    }
}
