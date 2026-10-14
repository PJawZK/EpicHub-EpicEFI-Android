use std::panic::{AssertUnwindSafe, catch_unwind};

use jni::JNIEnv;
use jni::objects::{JByteBuffer, JObject};
use jni::sys::{JNI_FALSE, JNI_TRUE, jboolean, jint, jlong};

use crate::diagnostics::DIAGNOSTIC_BATCH_SIZE;
use crate::engine::{
    active_engine_count, close_engine, create_engine, diagnostic_batch, engine_is_alive,
    runtime_snapshot,
};
use crate::resolved_profile_source::{
    MEGA144H7_PAGE_1_SIZE, MEGA144H7_PAGE_2_SIZE, Mega144h7ResolvedModes,
};
use crate::runtime_snapshot::RUNTIME_SNAPSHOT_SIZE;
use crate::tune_source_install::{
    TuneSourceInstallError, install_mega144h7_imported_pages, tune_catalog_bytes,
};

const BUFFER_STATUS_OK: jint = 0;
const BUFFER_STATUS_INVALID_HANDLE: jint = 1;
const BUFFER_STATUS_NOT_DIRECT: jint = 2;
const BUFFER_STATUS_TOO_SMALL: jint = 3;
const BUFFER_STATUS_SOURCE_REJECTED: jint = 4;
const BUFFER_STATUS_PANIC: jint = 255;

const SOURCE_RESULT_INVALID_HANDLE: jlong = -1;
const SOURCE_RESULT_NOT_DIRECT: jlong = -2;
const SOURCE_RESULT_INVALID_SIZE: jlong = -3;
const SOURCE_RESULT_REJECTED: jlong = -4;
const SOURCE_RESULT_PANIC: jlong = -255;

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

fn copy_from_exact_direct_buffer(
    env: &JNIEnv,
    buffer: &JByteBuffer,
    expected_size: usize,
) -> Result<Vec<u8>, jlong> {
    let capacity = env
        .get_direct_buffer_capacity(buffer)
        .map_err(|_| SOURCE_RESULT_NOT_DIRECT)?;
    if capacity != expected_size {
        return Err(SOURCE_RESULT_INVALID_SIZE);
    }
    let address = env
        .get_direct_buffer_address(buffer)
        .map_err(|_| SOURCE_RESULT_NOT_DIRECT)?;
    let mut bytes = vec![0; expected_size];
    unsafe {
        std::ptr::copy_nonoverlapping(address, bytes.as_mut_ptr(), expected_size);
    }
    Ok(bytes)
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
pub extern "system" fn Java_com_epicefi_epichub_engine_NativeEngineBridge_installMega144h7ImportedTunePages(
    env: JNIEnv,
    _object: JObject,
    handle: jlong,
    page_1: JByteBuffer,
    page_2: JByteBuffer,
    mode_flags: jint,
) -> jlong {
    ffi_guard(SOURCE_RESULT_PANIC, || {
        if !engine_is_alive(handle) {
            return SOURCE_RESULT_INVALID_HANDLE;
        }
        let page_1 = match copy_from_exact_direct_buffer(&env, &page_1, MEGA144H7_PAGE_1_SIZE) {
            Ok(bytes) => bytes,
            Err(status) => return status,
        };
        let page_2 = match copy_from_exact_direct_buffer(&env, &page_2, MEGA144H7_PAGE_2_SIZE) {
            Ok(bytes) => bytes,
            Err(status) => return status,
        };
        let modes = Mega144h7ResolvedModes {
            hundredth_ve: mode_flags & 0x1 != 0,
            hundredth_ignition: mode_flags & 0x2 != 0,
            lambda_targets: mode_flags & 0x4 != 0,
        };
        match install_mega144h7_imported_pages(handle, &page_1, &page_2, modes) {
            Ok(profile_generation) => jlong::try_from(profile_generation).unwrap_or(SOURCE_RESULT_REJECTED),
            Err(TuneSourceInstallError::InvalidHandle) => SOURCE_RESULT_INVALID_HANDLE,
            Err(TuneSourceInstallError::Resolve(_)) => SOURCE_RESULT_REJECTED,
        }
    })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_epicefi_epichub_engine_NativeEngineBridge_tuneCatalogSize(
    _env: JNIEnv,
    _object: JObject,
    handle: jlong,
) -> jint {
    ffi_guard(-jint::from(BUFFER_STATUS_PANIC), || {
        let Some(bytes) = tune_catalog_bytes(handle) else {
            return -jint::from(BUFFER_STATUS_INVALID_HANDLE);
        };
        jint::try_from(bytes.len()).unwrap_or(-jint::from(BUFFER_STATUS_TOO_SMALL))
    })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_epicefi_epichub_engine_NativeEngineBridge_fillTuneCatalog(
    env: JNIEnv,
    _object: JObject,
    handle: jlong,
    buffer: JByteBuffer,
) -> jint {
    ffi_guard(BUFFER_STATUS_PANIC, || {
        let Some(bytes) = tune_catalog_bytes(handle) else {
            return BUFFER_STATUS_INVALID_HANDLE;
        };
        let status = copy_to_direct_buffer(&env, &buffer, &bytes);
        if status == BUFFER_STATUS_OK {
            BUFFER_STATUS_OK
        } else if status == BUFFER_STATUS_NOT_DIRECT || status == BUFFER_STATUS_TOO_SMALL {
            status
        } else {
            BUFFER_STATUS_SOURCE_REJECTED
        }
    })
}
