package com.epicefi.epichub.ui.tuner

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.type

fun KeyEvent.toTunerCommand(): TunerCommand? {
    if (type != KeyEventType.KeyDown) return null

    val keyCode = key.nativeKeyCode
    val shortcutKey = when {
        keyCode == AndroidKeyEvent.KEYCODE_DPAD_UP -> TunerShortcutKey.UP
        keyCode == AndroidKeyEvent.KEYCODE_DPAD_DOWN -> TunerShortcutKey.DOWN
        keyCode == AndroidKeyEvent.KEYCODE_DPAD_LEFT -> TunerShortcutKey.LEFT
        keyCode == AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> TunerShortcutKey.RIGHT
        keyCode == AndroidKeyEvent.KEYCODE_PLUS -> TunerShortcutKey.PLUS
        keyCode == AndroidKeyEvent.KEYCODE_NUMPAD_ADD -> TunerShortcutKey.PLUS
        keyCode == AndroidKeyEvent.KEYCODE_EQUALS && isShiftPressed -> TunerShortcutKey.PLUS
        keyCode == AndroidKeyEvent.KEYCODE_MINUS -> TunerShortcutKey.MINUS
        keyCode == AndroidKeyEvent.KEYCODE_NUMPAD_SUBTRACT -> TunerShortcutKey.MINUS
        keyCode == AndroidKeyEvent.KEYCODE_ENTER -> TunerShortcutKey.ENTER
        keyCode == AndroidKeyEvent.KEYCODE_NUMPAD_ENTER -> TunerShortcutKey.ENTER
        keyCode == AndroidKeyEvent.KEYCODE_ESCAPE -> TunerShortcutKey.ESCAPE
        keyCode == AndroidKeyEvent.KEYCODE_Z -> TunerShortcutKey.Z
        keyCode == AndroidKeyEvent.KEYCODE_Y -> TunerShortcutKey.Y
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
