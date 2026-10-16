use std::panic::{AssertUnwindSafe, catch_unwind};

use jni::JNIEnv;
use jni::objects::{JByteBuffer, JObject};
use jni::sys::{jint, jlong};

use crate::ram_source_provenance::{
    RAM_SOURCE_STATE_SNAPSHOT_SIZE, engine_ram_source_state_snapshot,
};

const STATE_STATUS_OK: jint = 0;
const STATE_STATUS_INVALID_HANDLE: jint = 1;
const STATE_STATUS_NOT_DIRECT: jint = 2;
const STATE_STATUS_TOO_SMALL: jint = 3;
const STATE_STATUS_UNAVAILABLE: jint = 4;
const STATE_STATUS_PANIC: jint = 255;

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_epicefi_epichub_engine_NativeEngineBridge_fillRamSourceState(
    env: JNIEnv,
    _object: JObject,
    handle: jlong,
    buffer: JByteBuffer,
) -> jint {
    catch_unwind(AssertUnwindSafe(|| {
        let snapshot = match engine_ram_source_state_snapshot(handle) {
            None => return STATE_STATUS_INVALID_HANDLE,
            Some(None) => return STATE_STATUS_UNAVAILABLE,
            Some(Some(snapshot)) => snapshot,
        };

        let capacity = match env.get_direct_buffer_capacity(&buffer) {
            Ok(capacity) => capacity,
            Err(_) => return STATE_STATUS_NOT_DIRECT,
        };
        if capacity < RAM_SOURCE_STATE_SNAPSHOT_SIZE {
            return STATE_STATUS_TOO_SMALL;
        }
        let address = match env.get_direct_buffer_address(&buffer) {
            Ok(address) => address,
            Err(_) => return STATE_STATUS_NOT_DIRECT,
        };
        unsafe {
            std::ptr::copy_nonoverlapping(snapshot.as_ptr(), address, snapshot.len());
        }
        STATE_STATUS_OK
    }))
    .unwrap_or(STATE_STATUS_PANIC)
}
