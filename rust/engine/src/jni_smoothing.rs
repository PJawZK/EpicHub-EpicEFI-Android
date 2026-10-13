use std::panic::{AssertUnwindSafe, catch_unwind};

use jni::JNIEnv;
use jni::objects::{JByteBuffer, JObject, JString};
use jni::sys::{jint, jlong};

use crate::tuning_fixture::{
    apply_offline_fixture_smoothing, preview_offline_fixture_smoothing_bytes,
};

const BUFFER_STATUS_OK: jint = 0;
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

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_epicefi_epichub_engine_OfflineTunerFixture_fillSmoothingPreviewNative(
    mut env: JNIEnv,
    _object: JObject,
    anchor_row: jint,
    anchor_col: jint,
    focus_row: jint,
    focus_col: jint,
    strength_text: JString,
    buffer: JByteBuffer,
) -> jint {
    ffi_guard(BUFFER_STATUS_PANIC, || {
        let Some((anchor_row, anchor_col, focus_row, focus_col)) =
            rectangle_args(anchor_row, anchor_col, focus_row, focus_col)
        else {
            return BUFFER_STATUS_EDIT_REJECTED;
        };
        let Some(strength_text) = string_arg(&mut env, &strength_text) else {
            return BUFFER_STATUS_EDIT_REJECTED;
        };
        let bytes = match preview_offline_fixture_smoothing_bytes(
            anchor_row,
            anchor_col,
            focus_row,
            focus_col,
            &strength_text,
        ) {
            Ok(bytes) => bytes,
            Err(_) => return BUFFER_STATUS_EDIT_REJECTED,
        };
        copy_to_direct_buffer(&env, &buffer, &bytes)
    })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_epicefi_epichub_engine_OfflineTunerFixture_applySmoothingNative(
    mut env: JNIEnv,
    _object: JObject,
    anchor_row: jint,
    anchor_col: jint,
    focus_row: jint,
    focus_col: jint,
    strength_text: JString,
    expected_revision: jlong,
) -> jlong {
    ffi_guard(EDIT_STATUS_REJECTED, || {
        let Some((anchor_row, anchor_col, focus_row, focus_col)) =
            rectangle_args(anchor_row, anchor_col, focus_row, focus_col)
        else {
            return EDIT_STATUS_REJECTED;
        };
        let Some(strength_text) = string_arg(&mut env, &strength_text) else {
            return EDIT_STATUS_REJECTED;
        };
        let Ok(expected_revision) = u64::try_from(expected_revision) else {
            return EDIT_STATUS_REJECTED;
        };
        apply_offline_fixture_smoothing(
            anchor_row,
            anchor_col,
            focus_row,
            focus_col,
            &strength_text,
            expected_revision,
        )
        .ok()
        .and_then(|revision| jlong::try_from(revision).ok())
        .unwrap_or(EDIT_STATUS_REJECTED)
    })
}
