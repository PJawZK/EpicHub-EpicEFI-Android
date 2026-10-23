use std::panic::{AssertUnwindSafe, catch_unwind};

use jni::JNIEnv;
use jni::objects::{JByteBuffer, JObject};
use jni::sys::{jint, jlong};

use crate::engine::nonvolatile_page_snapshot;

const SIZE: usize = 48;
const OK: jint = 0;
const INVALID_HANDLE: jint = 1;
const NOT_DIRECT: jint = 2;
const TOO_SMALL: jint = 3;
const INVALID_ARGUMENT: jint = 4;
const PANIC: jint = 255;

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_epicefi_epichub_engine_NativeEngineBridge_fillNonvolatilePageState(
    env: JNIEnv,
    _object: JObject,
    handle: jlong,
    page: jint,
    signature: JByteBuffer,
    signature_length: jint,
    output: JByteBuffer,
) -> jint {
    catch_unwind(AssertUnwindSafe(|| {
        if page <= 0 || signature_length <= 0 || signature_length > 4096 {
            return INVALID_ARGUMENT;
        }
        let signature_capacity = match env.get_direct_buffer_capacity(&signature) {
            Ok(value) => value,
            Err(_) => return NOT_DIRECT,
        };
        let len = match usize::try_from(signature_length) {
            Ok(value) if value <= signature_capacity => value,
            _ => return INVALID_ARGUMENT,
        };
        let signature_address = match env.get_direct_buffer_address(&signature) {
            Ok(value) => value,
            Err(_) => return NOT_DIRECT,
        };
        let signature_bytes = unsafe { std::slice::from_raw_parts(signature_address, len) };
        let snapshot = match nonvolatile_page_snapshot(handle, page as usize, signature_bytes) {
            Some(snapshot) => snapshot,
            None => return INVALID_HANDLE,
        };
        let capacity = match env.get_direct_buffer_capacity(&output) {
            Ok(value) => value,
            Err(_) => return NOT_DIRECT,
        };
        if capacity < SIZE {
            return TOO_SMALL;
        }
        let address = match env.get_direct_buffer_address(&output) {
            Ok(value) => value,
            Err(_) => return NOT_DIRECT,
        };
        unsafe { std::ptr::copy_nonoverlapping(snapshot.as_ptr(), address, SIZE) };
        OK
    }))
    .unwrap_or(PANIC)
}
