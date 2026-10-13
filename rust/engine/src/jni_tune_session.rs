use std::panic::{AssertUnwindSafe, catch_unwind};

use jni::JNIEnv;
use jni::objects::{JByteBuffer, JIntArray, JObject, JString};
use jni::sys::{jint, jlong};

use crate::engine::with_tune_session;
use crate::tuning::CellCoord;
use crate::tuning_brush_pull::MAX_BRUSH_PATH_POINTS;

const BUFFER_STATUS_OK: jint = 0;
const BUFFER_STATUS_INVALID_HANDLE: jint = 1;
const BUFFER_STATUS_NOT_DIRECT: jint = 2;
const BUFFER_STATUS_TOO_SMALL: jint = 3;
const BUFFER_STATUS_EDIT_REJECTED: jint = 4;
const BUFFER_STATUS_PANIC: jint = 255;
const EDIT_STATUS_REJECTED: jlong = -1;

fn ffi_guard<T: Copy>(fallback: T, operation: impl FnOnce() -> T) -> T {
    catch_unwind(AssertUnwindSafe(operation)).unwrap_or(fallback)
}

fn copy_to_direct_buffer(env: &JNIEnv, buffer: &JByteBuffer, bytes: &[u8]) -> jint {
    let capacity = match env.get_direct_buffer_capacity(buffer) {
        Ok(capacity) => capacity,
        Err(_) => return BUFFER_STATUS_NOT_DIRECT,
    };
    if capacity < bytes.len() {
        return BUFFER_STATUS_TOO_SMALL;
    }
    let address = match env.get_direct_buffer_address(buffer) {
        Ok(address) => address,
        Err(_) => return BUFFER_STATUS_NOT_DIRECT,
    };
    unsafe {
        std::ptr::copy_nonoverlapping(bytes.as_ptr(), address, bytes.len());
    }
    BUFFER_STATUS_OK
}

fn table_id_arg(table_id: jint) -> Option<u32> {
    u32::try_from(table_id).ok()
}

fn rectangle_args(
    anchor_row: jint,
    anchor_col: jint,
    focus_row: jint,
    focus_col: jint,
) -> Option<(usize, usize, usize, usize)> {
    Some((
        usize::try_from(anchor_row).ok()?,
        usize::try_from(anchor_col).ok()?,
        usize::try_from(focus_row).ok()?,
        usize::try_from(focus_col).ok()?,
    ))
}

fn string_arg(env: &mut JNIEnv, value: &JString) -> Option<String> {
    env.get_string(value).ok().map(Into::into)
}

fn path_arg(env: &JNIEnv, path: &JIntArray) -> Option<Vec<CellCoord>> {
    let length = usize::try_from(env.get_array_length(path).ok()?).ok()?;
    if length == 0 || length % 2 != 0 || length / 2 > MAX_BRUSH_PATH_POINTS {
        return None;
    }
    let mut raw = vec![0; length];
    env.get_int_array_region(path, 0, &mut raw).ok()?;
    raw.as_chunks::<2>()
        .0
        .iter()
        .map(|pair| {
            Some(CellCoord {
                row: usize::try_from(pair[0]).ok()?,
                col: usize::try_from(pair[1]).ok()?,
            })
        })
        .collect()
}

fn revision_arg(expected_revision: jlong) -> Option<u64> {
    u64::try_from(expected_revision).ok()
}

fn revision_result(result: Option<Result<u64, crate::tuning::TuneError>>) -> jlong {
    result
        .and_then(Result::ok)
        .and_then(|revision| jlong::try_from(revision).ok())
        .unwrap_or(EDIT_STATUS_REJECTED)
}

fn bytes_status(
    env: &JNIEnv,
    buffer: &JByteBuffer,
    result: Option<Result<Vec<u8>, crate::tuning::TuneError>>,
) -> jint {
    match result {
        None => BUFFER_STATUS_INVALID_HANDLE,
        Some(Err(_)) => BUFFER_STATUS_EDIT_REJECTED,
        Some(Ok(bytes)) => copy_to_direct_buffer(env, buffer, &bytes),
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_epicefi_epichub_engine_NativeEngineBridge_tuneProjectionSize(
    _env: JNIEnv,
    _object: JObject,
    handle: jlong,
    table_id: jint,
) -> jint {
    ffi_guard(-1, || {
        let Some(table_id) = table_id_arg(table_id) else {
            return -1;
        };
        with_tune_session(handle, |session| session.projection_bytes(table_id))
            .and_then(Result::ok)
            .and_then(|bytes| jint::try_from(bytes.len()).ok())
            .unwrap_or(-1)
    })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_epicefi_epichub_engine_NativeEngineBridge_fillTuneProjection(
    env: JNIEnv,
    _object: JObject,
    handle: jlong,
    table_id: jint,
    buffer: JByteBuffer,
) -> jint {
    ffi_guard(BUFFER_STATUS_PANIC, || {
        let Some(table_id) = table_id_arg(table_id) else {
            return BUFFER_STATUS_EDIT_REJECTED;
        };
        bytes_status(
            &env,
            &buffer,
            with_tune_session(handle, |session| session.projection_bytes(table_id)),
        )
    })
}

#[unsafe(no_mangle)]
#[allow(clippy::too_many_arguments)]
pub extern "system" fn Java_com_epicefi_epichub_engine_NativeEngineBridge_fillTuneRectangleStepPreview(
    env: JNIEnv,
    _object: JObject,
    handle: jlong,
    table_id: jint,
    anchor_row: jint,
    anchor_col: jint,
    focus_row: jint,
    focus_col: jint,
    direction: jint,
    buffer: JByteBuffer,
) -> jint {
    ffi_guard(BUFFER_STATUS_PANIC, || {
        let Some(table_id) = table_id_arg(table_id) else {
            return BUFFER_STATUS_EDIT_REJECTED;
        };
        let Some((anchor_row, anchor_col, focus_row, focus_col)) =
            rectangle_args(anchor_row, anchor_col, focus_row, focus_col)
        else {
            return BUFFER_STATUS_EDIT_REJECTED;
        };
        bytes_status(
            &env,
            &buffer,
            with_tune_session(handle, |session| {
                session.preview_rectangle_step_bytes(
                    table_id, anchor_row, anchor_col, focus_row, focus_col, direction,
                )
            }),
        )
    })
}

#[unsafe(no_mangle)]
#[allow(clippy::too_many_arguments)]
pub extern "system" fn Java_com_epicefi_epichub_engine_NativeEngineBridge_applyTuneRectangleStep(
    _env: JNIEnv,
    _object: JObject,
    handle: jlong,
    table_id: jint,
    anchor_row: jint,
    anchor_col: jint,
    focus_row: jint,
    focus_col: jint,
    direction: jint,
    expected_revision: jlong,
) -> jlong {
    ffi_guard(EDIT_STATUS_REJECTED, || {
        let Some(table_id) = table_id_arg(table_id) else {
            return EDIT_STATUS_REJECTED;
        };
        let Some((anchor_row, anchor_col, focus_row, focus_col)) =
            rectangle_args(anchor_row, anchor_col, focus_row, focus_col)
        else {
            return EDIT_STATUS_REJECTED;
        };
        let Some(expected_revision) = revision_arg(expected_revision) else {
            return EDIT_STATUS_REJECTED;
        };
        revision_result(with_tune_session(handle, |session| {
            session.apply_rectangle_step(
                table_id,
                anchor_row,
                anchor_col,
                focus_row,
                focus_col,
                direction,
                expected_revision,
            )
        }))
    })
}

#[unsafe(no_mangle)]
#[allow(clippy::too_many_arguments)]
pub extern "system" fn Java_com_epicefi_epichub_engine_NativeEngineBridge_fillTuneRectangleNumericPreview(
    mut env: JNIEnv,
    _object: JObject,
    handle: jlong,
    table_id: jint,
    anchor_row: jint,
    anchor_col: jint,
    focus_row: jint,
    focus_col: jint,
    operation_code: jint,
    operand: JString,
    buffer: JByteBuffer,
) -> jint {
    ffi_guard(BUFFER_STATUS_PANIC, || {
        let Some(table_id) = table_id_arg(table_id) else {
            return BUFFER_STATUS_EDIT_REJECTED;
        };
        let Some((anchor_row, anchor_col, focus_row, focus_col)) =
            rectangle_args(anchor_row, anchor_col, focus_row, focus_col)
        else {
            return BUFFER_STATUS_EDIT_REJECTED;
        };
        let Some(operand) = string_arg(&mut env, &operand) else {
            return BUFFER_STATUS_EDIT_REJECTED;
        };
        bytes_status(
            &env,
            &buffer,
            with_tune_session(handle, |session| {
                session.preview_rectangle_numeric_bytes(
                    table_id,
                    anchor_row,
                    anchor_col,
                    focus_row,
                    focus_col,
                    operation_code,
                    &operand,
                )
            }),
        )
    })
}

#[unsafe(no_mangle)]
#[allow(clippy::too_many_arguments)]
pub extern "system" fn Java_com_epicefi_epichub_engine_NativeEngineBridge_applyTuneRectangleNumeric(
    mut env: JNIEnv,
    _object: JObject,
    handle: jlong,
    table_id: jint,
    anchor_row: jint,
    anchor_col: jint,
    focus_row: jint,
    focus_col: jint,
    operation_code: jint,
    operand: JString,
    expected_revision: jlong,
) -> jlong {
    ffi_guard(EDIT_STATUS_REJECTED, || {
        let Some(table_id) = table_id_arg(table_id) else {
            return EDIT_STATUS_REJECTED;
        };
        let Some((anchor_row, anchor_col, focus_row, focus_col)) =
            rectangle_args(anchor_row, anchor_col, focus_row, focus_col)
        else {
            return EDIT_STATUS_REJECTED;
        };
        let Some(operand) = string_arg(&mut env, &operand) else {
            return EDIT_STATUS_REJECTED;
        };
        let Some(expected_revision) = revision_arg(expected_revision) else {
            return EDIT_STATUS_REJECTED;
        };
        revision_result(with_tune_session(handle, |session| {
            session.apply_rectangle_numeric(
                table_id,
                anchor_row,
                anchor_col,
                focus_row,
                focus_col,
                operation_code,
                &operand,
                expected_revision,
            )
        }))
    })
}

#[unsafe(no_mangle)]
#[allow(clippy::too_many_arguments)]
pub extern "system" fn Java_com_epicefi_epichub_engine_NativeEngineBridge_fillTuneInterpolationPreview(
    env: JNIEnv,
    _object: JObject,
    handle: jlong,
    table_id: jint,
    anchor_row: jint,
    anchor_col: jint,
    focus_row: jint,
    focus_col: jint,
    mode_code: jint,
    buffer: JByteBuffer,
) -> jint {
    ffi_guard(BUFFER_STATUS_PANIC, || {
        let Some(table_id) = table_id_arg(table_id) else {
            return BUFFER_STATUS_EDIT_REJECTED;
        };
        let Some((anchor_row, anchor_col, focus_row, focus_col)) =
            rectangle_args(anchor_row, anchor_col, focus_row, focus_col)
        else {
            return BUFFER_STATUS_EDIT_REJECTED;
        };
        bytes_status(
            &env,
            &buffer,
            with_tune_session(handle, |session| {
                session.preview_interpolation_bytes(
                    table_id, anchor_row, anchor_col, focus_row, focus_col, mode_code,
                )
            }),
        )
    })
}

#[unsafe(no_mangle)]
#[allow(clippy::too_many_arguments)]
pub extern "system" fn Java_com_epicefi_epichub_engine_NativeEngineBridge_applyTuneInterpolation(
    _env: JNIEnv,
    _object: JObject,
    handle: jlong,
    table_id: jint,
    anchor_row: jint,
    anchor_col: jint,
    focus_row: jint,
    focus_col: jint,
    mode_code: jint,
    expected_revision: jlong,
) -> jlong {
    ffi_guard(EDIT_STATUS_REJECTED, || {
        let Some(table_id) = table_id_arg(table_id) else {
            return EDIT_STATUS_REJECTED;
        };
        let Some((anchor_row, anchor_col, focus_row, focus_col)) =
            rectangle_args(anchor_row, anchor_col, focus_row, focus_col)
        else {
            return EDIT_STATUS_REJECTED;
        };
        let Some(expected_revision) = revision_arg(expected_revision) else {
            return EDIT_STATUS_REJECTED;
        };
        revision_result(with_tune_session(handle, |session| {
            session.apply_interpolation(
                table_id,
                anchor_row,
                anchor_col,
                focus_row,
                focus_col,
                mode_code,
                expected_revision,
            )
        }))
    })
}

#[unsafe(no_mangle)]
#[allow(clippy::too_many_arguments)]
pub extern "system" fn Java_com_epicefi_epichub_engine_NativeEngineBridge_fillTuneSmoothingPreview(
    mut env: JNIEnv,
    _object: JObject,
    handle: jlong,
    table_id: jint,
    anchor_row: jint,
    anchor_col: jint,
    focus_row: jint,
    focus_col: jint,
    strength_text: JString,
    buffer: JByteBuffer,
) -> jint {
    ffi_guard(BUFFER_STATUS_PANIC, || {
        let Some(table_id) = table_id_arg(table_id) else {
            return BUFFER_STATUS_EDIT_REJECTED;
        };
        let Some((anchor_row, anchor_col, focus_row, focus_col)) =
            rectangle_args(anchor_row, anchor_col, focus_row, focus_col)
        else {
            return BUFFER_STATUS_EDIT_REJECTED;
        };
        let Some(strength_text) = string_arg(&mut env, &strength_text) else {
            return BUFFER_STATUS_EDIT_REJECTED;
        };
        bytes_status(
            &env,
            &buffer,
            with_tune_session(handle, |session| {
                session.preview_smoothing_bytes(
                    table_id,
                    anchor_row,
                    anchor_col,
                    focus_row,
                    focus_col,
                    &strength_text,
                )
            }),
        )
    })
}

#[unsafe(no_mangle)]
#[allow(clippy::too_many_arguments)]
pub extern "system" fn Java_com_epicefi_epichub_engine_NativeEngineBridge_applyTuneSmoothing(
    mut env: JNIEnv,
    _object: JObject,
    handle: jlong,
    table_id: jint,
    anchor_row: jint,
    anchor_col: jint,
    focus_row: jint,
    focus_col: jint,
    strength_text: JString,
    expected_revision: jlong,
) -> jlong {
    ffi_guard(EDIT_STATUS_REJECTED, || {
        let Some(table_id) = table_id_arg(table_id) else {
            return EDIT_STATUS_REJECTED;
        };
        let Some((anchor_row, anchor_col, focus_row, focus_col)) =
            rectangle_args(anchor_row, anchor_col, focus_row, focus_col)
        else {
            return EDIT_STATUS_REJECTED;
        };
        let Some(strength_text) = string_arg(&mut env, &strength_text) else {
            return EDIT_STATUS_REJECTED;
        };
        let Some(expected_revision) = revision_arg(expected_revision) else {
            return EDIT_STATUS_REJECTED;
        };
        revision_result(with_tune_session(handle, |session| {
            session.apply_smoothing(
                table_id,
                anchor_row,
                anchor_col,
                focus_row,
                focus_col,
                &strength_text,
                expected_revision,
            )
        }))
    })
}

#[unsafe(no_mangle)]
#[allow(clippy::too_many_arguments)]
pub extern "system" fn Java_com_epicefi_epichub_engine_NativeEngineBridge_fillTuneBrushPullPreview(
    mut env: JNIEnv,
    _object: JObject,
    handle: jlong,
    table_id: jint,
    anchor_row: jint,
    anchor_col: jint,
    focus_row: jint,
    focus_col: jint,
    mode_code: jint,
    delta_text: JString,
    radius_text: JString,
    strength_text: JString,
    falloff_text: JString,
    buffer: JByteBuffer,
) -> jint {
    ffi_guard(BUFFER_STATUS_PANIC, || {
        let Some(table_id) = table_id_arg(table_id) else {
            return BUFFER_STATUS_EDIT_REJECTED;
        };
        let Some((anchor_row, anchor_col, focus_row, focus_col)) =
            rectangle_args(anchor_row, anchor_col, focus_row, focus_col)
        else {
            return BUFFER_STATUS_EDIT_REJECTED;
        };
        let Some(delta_text) = string_arg(&mut env, &delta_text) else {
            return BUFFER_STATUS_EDIT_REJECTED;
        };
        let Some(radius_text) = string_arg(&mut env, &radius_text) else {
            return BUFFER_STATUS_EDIT_REJECTED;
        };
        let Some(strength_text) = string_arg(&mut env, &strength_text) else {
            return BUFFER_STATUS_EDIT_REJECTED;
        };
        let Some(falloff_text) = string_arg(&mut env, &falloff_text) else {
            return BUFFER_STATUS_EDIT_REJECTED;
        };
        bytes_status(
            &env,
            &buffer,
            with_tune_session(handle, |session| {
                session.preview_brush_pull_bytes(
                    table_id,
                    anchor_row,
                    anchor_col,
                    focus_row,
                    focus_col,
                    mode_code,
                    &delta_text,
                    &radius_text,
                    &strength_text,
                    &falloff_text,
                )
            }),
        )
    })
}

#[unsafe(no_mangle)]
#[allow(clippy::too_many_arguments)]
pub extern "system" fn Java_com_epicefi_epichub_engine_NativeEngineBridge_applyTuneBrushPull(
    mut env: JNIEnv,
    _object: JObject,
    handle: jlong,
    table_id: jint,
    anchor_row: jint,
    anchor_col: jint,
    focus_row: jint,
    focus_col: jint,
    mode_code: jint,
    delta_text: JString,
    radius_text: JString,
    strength_text: JString,
    falloff_text: JString,
    expected_revision: jlong,
) -> jlong {
    ffi_guard(EDIT_STATUS_REJECTED, || {
        let Some(table_id) = table_id_arg(table_id) else {
            return EDIT_STATUS_REJECTED;
        };
        let Some((anchor_row, anchor_col, focus_row, focus_col)) =
            rectangle_args(anchor_row, anchor_col, focus_row, focus_col)
        else {
            return EDIT_STATUS_REJECTED;
        };
        let Some(delta_text) = string_arg(&mut env, &delta_text) else {
            return EDIT_STATUS_REJECTED;
        };
        let Some(radius_text) = string_arg(&mut env, &radius_text) else {
            return EDIT_STATUS_REJECTED;
        };
        let Some(strength_text) = string_arg(&mut env, &strength_text) else {
            return EDIT_STATUS_REJECTED;
        };
        let Some(falloff_text) = string_arg(&mut env, &falloff_text) else {
            return EDIT_STATUS_REJECTED;
        };
        let Some(expected_revision) = revision_arg(expected_revision) else {
            return EDIT_STATUS_REJECTED;
        };
        revision_result(with_tune_session(handle, |session| {
            session.apply_brush_pull(
                table_id,
                anchor_row,
                anchor_col,
                focus_row,
                focus_col,
                mode_code,
                &delta_text,
                &radius_text,
                &strength_text,
                &falloff_text,
                expected_revision,
            )
        }))
    })
}

#[unsafe(no_mangle)]
#[allow(clippy::too_many_arguments)]
pub extern "system" fn Java_com_epicefi_epichub_engine_NativeEngineBridge_fillTuneBrushPathPreview(
    mut env: JNIEnv,
    _object: JObject,
    handle: jlong,
    table_id: jint,
    path: JIntArray,
    delta_text: JString,
    radius_text: JString,
    strength_text: JString,
    falloff_text: JString,
    buffer: JByteBuffer,
) -> jint {
    ffi_guard(BUFFER_STATUS_PANIC, || {
        let Some(table_id) = table_id_arg(table_id) else {
            return BUFFER_STATUS_EDIT_REJECTED;
        };
        let Some(path) = path_arg(&env, &path) else {
            return BUFFER_STATUS_EDIT_REJECTED;
        };
        let Some(delta_text) = string_arg(&mut env, &delta_text) else {
            return BUFFER_STATUS_EDIT_REJECTED;
        };
        let Some(radius_text) = string_arg(&mut env, &radius_text) else {
            return BUFFER_STATUS_EDIT_REJECTED;
        };
        let Some(strength_text) = string_arg(&mut env, &strength_text) else {
            return BUFFER_STATUS_EDIT_REJECTED;
        };
        let Some(falloff_text) = string_arg(&mut env, &falloff_text) else {
            return BUFFER_STATUS_EDIT_REJECTED;
        };
        bytes_status(
            &env,
            &buffer,
            with_tune_session(handle, |session| {
                session.preview_brush_path_bytes(
                    table_id,
                    &path,
                    &delta_text,
                    &radius_text,
                    &strength_text,
                    &falloff_text,
                )
            }),
        )
    })
}

#[unsafe(no_mangle)]
#[allow(clippy::too_many_arguments)]
pub extern "system" fn Java_com_epicefi_epichub_engine_NativeEngineBridge_applyTuneBrushPath(
    mut env: JNIEnv,
    _object: JObject,
    handle: jlong,
    table_id: jint,
    path: JIntArray,
    delta_text: JString,
    radius_text: JString,
    strength_text: JString,
    falloff_text: JString,
    expected_revision: jlong,
) -> jlong {
    ffi_guard(EDIT_STATUS_REJECTED, || {
        let Some(table_id) = table_id_arg(table_id) else {
            return EDIT_STATUS_REJECTED;
        };
        let Some(path) = path_arg(&env, &path) else {
            return EDIT_STATUS_REJECTED;
        };
        let Some(delta_text) = string_arg(&mut env, &delta_text) else {
            return EDIT_STATUS_REJECTED;
        };
        let Some(radius_text) = string_arg(&mut env, &radius_text) else {
            return EDIT_STATUS_REJECTED;
        };
        let Some(strength_text) = string_arg(&mut env, &strength_text) else {
            return EDIT_STATUS_REJECTED;
        };
        let Some(falloff_text) = string_arg(&mut env, &falloff_text) else {
            return EDIT_STATUS_REJECTED;
        };
        let Some(expected_revision) = revision_arg(expected_revision) else {
            return EDIT_STATUS_REJECTED;
        };
        revision_result(with_tune_session(handle, |session| {
            session.apply_brush_path(
                table_id,
                &path,
                &delta_text,
                &radius_text,
                &strength_text,
                &falloff_text,
                expected_revision,
            )
        }))
    })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_epicefi_epichub_engine_NativeEngineBridge_undoTune(
    _env: JNIEnv,
    _object: JObject,
    handle: jlong,
) -> jlong {
    ffi_guard(EDIT_STATUS_REJECTED, || {
        revision_result(with_tune_session(handle, |session| session.undo()))
    })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_epicefi_epichub_engine_NativeEngineBridge_redoTune(
    _env: JNIEnv,
    _object: JObject,
    handle: jlong,
) -> jlong {
    ffi_guard(EDIT_STATUS_REJECTED, || {
        revision_result(with_tune_session(handle, |session| session.redo()))
    })
}
