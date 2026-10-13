package com.epicefi.epichub.ui.tuner

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.nativeKeyEvent
import androidx.compose.ui.input.key.type

fun KeyEvent.toTunerCommand(): TunerCommand? {
    if (type != KeyEventType.KeyDown) return null

    val shortcutKey = when (nativeKeyEvent.keyCode) {
        AndroidKeyEvent.KEYCODE_DPAD_UP -> TunerShortcutKey.UP
        AndroidKeyEvent.KEYCODE_DPAD_DOWN -> TunerShortcutKey.DOWN
        AndroidKeyEvent.KEYCODE_DPAD_LEFT -> TunerShortcutKey.LEFT
        AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> TunerShortcutKey.RIGHT
        AndroidKeyEvent.KEYCODE_PLUS,
        AndroidKeyEvent.KEYCODE_EQUALS,
        AndroidKeyEvent.KEYCODE_NUMPAD_ADD,
        -> TunerShortcutKey.PLUS
        AndroidKeyEvent.KEYCODE_MINUS,
        AndroidKeyEvent.KEYCODE_NUMPAD_SUBTRACT,
        -> TunerShortcutKey.MINUS
        AndroidKeyEvent.KEYCODE_ENTER,
        AndroidKeyEvent.KEYCODE_NUMPAD_ENTER,
        -> TunerShortcutKey.ENTER
        AndroidKeyEvent.KEYCODE_ESCAPE -> TunerShortcutKey.ESCAPE
        AndroidKeyEvent.KEYCODE_Z -> TunerShortcutKey.Z
        AndroidKeyEvent.KEYCODE_Y -> TunerShortcutKey.Y
        else -> return null
    }

    return TunerShortcutRegistry.commandFor(
        TunerKeyStroke(
            key = shortcutKey,
            ctrl = isCtrlPressed,
            shift = isShiftPressed,
            alt = isAltPressed,
        ),
    )
}
