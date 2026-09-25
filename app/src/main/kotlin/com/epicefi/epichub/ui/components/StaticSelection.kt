package com.epicefi.epichub.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

private val StaticSelectionShape = RoundedCornerShape(10.dp)

/**
 * Structural selection helper retained for provisional foundation surfaces.
 *
 * Horizontal text-only navigation now delegates to Material3 Tab. Physical qualification
 * showed that the custom selectable Surface path produced substantially more transition jank
 * than the previously proven Material navigation primitives. The custom flat path remains as
 * a fallback for vertical/glyph presentation until those provisional surfaces are retired.
 */
@Composable
fun StaticSelectionChoice(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    glyph: String? = null,
    vertical: Boolean = false,
) {
    if (!vertical && glyph == null) {
        Tab(
            selected = selected,
            onClick = {
                if (!selected) {
                    onClick()
                }
            },
            modifier = modifier,
            text = { Text(label) },
        )
        return
    }

    val interactionSource = remember { MutableInteractionSource() }
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        Color.Transparent
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        modifier = modifier.selectable(
            selected = selected,
            onClick = {
                if (!selected) {
                    onClick()
                }
            },
            role = Role.Tab,
            interactionSource = interactionSource,
            indication = null,
        ),
        color = containerColor,
        contentColor = contentColor,
        shape = StaticSelectionShape,
    ) {
        if (vertical) {
            Column(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 9.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                glyph?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelLarge,
                        textAlign = TextAlign.Center,
                    )
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                glyph?.let {
                    Text(text = it, style = MaterialTheme.typography.labelLarge)
                }
                Text(text = label, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
