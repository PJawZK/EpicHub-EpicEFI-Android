use std::panic::{AssertUnwindSafe, catch_unwind};

use jni::JNIEnv;
use jni::objects::{JByteBuffer, JObject};
use jni::sys::{JNI_FALSE, JNI_TRUE, jboolean, jint, jlong};

use crate::engine::{
    active_engine_count, close_engine, create_engine, engine_is_alive, runtime_snapshot,
};
use crate::runtime_snapshot::RUNTIME_SNAPSHOT_SIZE;

const SNAPSHOT_STATUS_OK: jint = 0;
const SNAPSHOT_STATUS_INVALID_HANDLE: jint = 1;
const SNAPSHOT_STATUS_NOT_DIRECT: jint = 2;
const SNAPSHOT_STATUS_BUFFER_TOO_SMALL: jint = 3;
const SNAPSHOT_STATUS_PANIC: jint = 255;

fn ffi_guard<T: Copy>(fallback: T, operation: impl FnOnce() -> T) -> T {
    catch_unwind(AssertUnwindSafe(operation)).unwrap_or(fallback)
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
    ffi_guard(SNAPSHOT_STATUS_PANIC, || {
        let snapshot = match runtime_snapshot(handle) {
            Some(snapshot) => snapshot,
            None => return SNAPSHOT_STATUS_INVALID_HANDLE,
        };

        let capacity = match env.get_direct_buffer_capacity(&buffer) {
            Ok(capacity) => capacity,
            Err(_) => return SNAPSHOT_STATUS_NOT_DIRECT,
        };
        if capacity < RUNTIME_SNAPSHOT_SIZE {
            return SNAPSHOT_STATUS_BUFFER_TOO_SMALL;
        }

        let address = match env.get_direct_buffer_address(&buffer) {
            Ok(address) => address,
            Err(_) => return SNAPSHOT_STATUS_NOT_DIRECT,
        };

        unsafe {
            std::ptr::copy_nonoverlapping(snapshot.as_ptr(), address, RUNTIME_SNAPSHOT_SIZE);
        }
        SNAPSHOT_STATUS_OK
    })
}
