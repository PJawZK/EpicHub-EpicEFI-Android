use std::panic::{AssertUnwindSafe, catch_unwind};

use jni::JNIEnv;
use jni::objects::{JByteBuffer, JObject};
use jni::sys::{JNI_FALSE, JNI_TRUE, jboolean, jdouble, jint, jlong};

use crate::diagnostics::DIAGNOSTIC_BATCH_SIZE;
use crate::engine::{
    active_engine_count, close_engine, create_engine, diagnostic_batch, engine_is_alive,
    offline_table_projection, runtime_snapshot,
};
use crate::runtime_snapshot::RUNTIME_SNAPSHOT_SIZE;
use crate::tuning_fixture::{
    apply_offline_fixture_cell_step, offline_fixture_projection_bytes,
    preview_offline_fixture_cell_step, redo_offline_fixture, undo_offline_fixture,
};

const BUFFER_STATUS_OK: jint = 0;
const BUFFER_STATUS_INVALID_HANDLE: jint = 1;
const BUFFER_STATUS_NOT_DIRECT: jint = 2;
const BUFFER_STATUS_TOO_SMALL: jint = 3;
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

fn edit_revision_or_rejected(result: Result<u64, crate::tuning::TuneError>) -> jlong {
    result
        .ok()
        .and_then(|revision| jlong::try_from(revision).ok())
        .unwrap_or(EDIT_STATUS_REJECTED)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_epicefi_epichub_engine_NativeEngineBridge_create(
    _env: JNIEnv,
    _object: JObject,
) -> jlong {
    ffi_guard(0, create_engine)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_epicefi_epichub_engine_NativeEngineBridge_close(
    _env: JNIEnv,
    _object: JObject,
    handle: jlong,
) {
    ffi_guard((), || close_engine(handle));
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_epicefi_epichub_engine_NativeEngineBridge_isAlive(
    _env: JNIEnv,
    _object: JObject,
    handle: jlong,
) -> jboolean {
    ffi_guard(JNI_FALSE, || {
        if engine_is_alive(handle) {
            JNI_TRUE
        } else {
            JNI_FALSE
        }
    })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_epicefi_epichub_engine_NativeEngineBridge_activeCount(
    _env: JNIEnv,
    _object: JObject,
) -> jint {
    ffi_guard(0, active_engine_count)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_epicefi_epichub_engine_NativeEngineBridge_fillRuntimeSnapshot(
    env: JNIEnv,
    _object: JObject,
    handle: jlong,
    buffer: JByteBuffer,
) -> jint {
    ffi_guard(BUFFER_STATUS_PANIC, || {
        let snapshot = match runtime_snapshot(handle) {
            Some(snapshot) => snapshot,
            None => return BUFFER_STATUS_INVALID_HANDLE,
        };
        debug_assert_eq!(snapshot.len(), RUNTIME_SNAPSHOT_SIZE);
        copy_to_direct_buffer(&env, &buffer, &snapshot)
    })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_epicefi_epichub_engine_NativeEngineBridge_fillDiagnosticEvents(
    env: JNIEnv,
    _object: JObject,
    handle: jlong,
    buffer: JByteBuffer,
) -> jint {
    ffi_guard(BUFFER_STATUS_PANIC, || {
        let batch = match diagnostic_batch(handle) {
            Some(batch) => batch,
            None => return BUFFER_STATUS_INVALID_HANDLE,
        };
        debug_assert_eq!(batch.len(), DIAGNOSTIC_BATCH_SIZE);
        copy_to_direct_buffer(&env, &buffer, &batch)
    })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_epicefi_epichub_engine_NativeEngineBridge_offlineTableProjectionSize(
    _env: JNIEnv,
    _object: JObject,
    handle: jlong,
) -> jint {
    ffi_guard(-1, || {
        offline_table_projection(handle)
            .and_then(|bytes| jint::try_from(bytes.len()).ok())
            .unwrap_or(-1)
    })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_epicefi_epichub_engine_NativeEngineBridge_fillOfflineTableProjection(
    env: JNIEnv,
    _object: JObject,
    handle: jlong,
    buffer: JByteBuffer,
) -> jint {
    ffi_guard(BUFFER_STATUS_PANIC, || {
        let bytes = match offline_table_projection(handle) {
            Some(bytes) => bytes,
            None => return BUFFER_STATUS_INVALID_HANDLE,
        };
        copy_to_direct_buffer(&env, &buffer, &bytes)
    })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_epicefi_epichub_engine_OfflineTunerFixture_projectionSize(
    _env: JNIEnv,
    _object: JObject,
) -> jint {
    ffi_guard(-1, || {
        jint::try_from(offline_fixture_projection_bytes().len()).unwrap_or(-1)
    })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_epicefi_epichub_engine_OfflineTunerFixture_fillProjection(
    env: JNIEnv,
    _object: JObject,
    buffer: JByteBuffer,
) -> jint {
    ffi_guard(BUFFER_STATUS_PANIC, || {
        let bytes = offline_fixture_projection_bytes();
        copy_to_direct_buffer(&env, &buffer, &bytes)
    })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_epicefi_epichub_engine_OfflineTunerFixture_previewCellStepNative(
    _env: JNIEnv,
    _object: JObject,
    row: jint,
    col: jint,
    direction: jint,
) -> jdouble {
    ffi_guard(f64::NAN, || {
        let Ok(row) = usize::try_from(row) else {
            return f64::NAN;
        };
        let Ok(col) = usize::try_from(col) else {
            return f64::NAN;
        };
        preview_offline_fixture_cell_step(row, col, direction).unwrap_or(f64::NAN)
    })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_epicefi_epichub_engine_OfflineTunerFixture_applyCellStepNative(
    _env: JNIEnv,
    _object: JObject,
    row: jint,
    col: jint,
    direction: jint,
    expected_revision: jlong,
) -> jlong {
    ffi_guard(EDIT_STATUS_REJECTED, || {
        let Ok(row) = usize::try_from(row) else {
            return EDIT_STATUS_REJECTED;
        };
        let Ok(col) = usize::try_from(col) else {
            return EDIT_STATUS_REJECTED;
        };
        let Ok(expected_revision) = u64::try_from(expected_revision) else {
            return EDIT_STATUS_REJECTED;
        };
        edit_revision_or_rejected(apply_offline_fixture_cell_step(
            row,
            col,
            direction,
            expected_revision,
        ))
    })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_epicefi_epichub_engine_OfflineTunerFixture_undoNative(
    _env: JNIEnv,
    _object: JObject,
) -> jlong {
    ffi_guard(EDIT_STATUS_REJECTED, || {
        edit_revision_or_rejected(undo_offline_fixture())
    })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_epicefi_epichub_engine_OfflineTunerFixture_redoNative(
    _env: JNIEnv,
    _object: JObject,
) -> jlong {
    ffi_guard(EDIT_STATUS_REJECTED, || {
        edit_revision_or_rejected(redo_offline_fixture())
    })
}
