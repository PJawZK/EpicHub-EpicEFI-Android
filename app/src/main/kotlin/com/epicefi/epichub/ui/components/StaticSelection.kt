package com.epicefi.epichub.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Lightweight immediate selection control for provisional high-frequency navigation.
 *
 * Physical qualification showed two undesirable extremes:
 * - Material navigation primitives reduced transition misses but brought back noticeable
 *   animation/allocation/CPU pressure.
 * - The earlier custom per-item Surface path was cheap at steady state but produced heavy
 *   transition drawing/jank.
 *
 * This implementation deliberately keeps only the essentials: rectangular background draw,
 * selection semantics and text. There is no per-item Surface, clip/shape, ripple or selection
 * animation. Selected-item taps are ignored before dispatching navigation work.
 *
 * [automationDescription] is a stable accessibility description used by the external
 * Macrobenchmark/UI Automator harness. Samsung physical qualification showed that a description
 * merged after selectable can still surface as a non-clickable accessibility node. The explicit
 * semantics click action below therefore mirrors the real selectable action so the description-
 * bearing node remains a valid automation target without changing the touch implementation.
 */
@Composable
fun StaticSelectionChoice(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    glyph: String? = null,
    vertical: Boolean = false,
    automationDescription: String? = null,
) {
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

    fun dispatchSelection() {
        if (!selected) {
            onClick()
        }
    }

    var selectionModifier = modifier
        .background(containerColor)
        .selectable(
            selected = selected,
            onClick = ::dispatchSelection,
            role = Role.Tab,
            interactionSource = interactionSource,
            indication = null,
        )

    if (automationDescription != null) {
        selectionModifier = selectionModifier.semantics(mergeDescendants = true) {
            contentDescription = automationDescription
            this.selected = selected
            role = Role.Tab
            onClick {
                dispatchSelection()
                true
            }
        }
    }

    if (vertical) {
        Column(
            modifier = selectionModifier.padding(horizontal = 8.dp, vertical = 9.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            glyph?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelLarge,
                    color = contentColor,
                    textAlign = TextAlign.Center,
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = contentColor,
                textAlign = TextAlign.Center,
            )
        }
    } else {
        Row(
            modifier = selectionModifier.padding(horizontal = 12.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            glyph?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelLarge,
                    color = contentColor,
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = contentColor,
            )
        }
    }
}
