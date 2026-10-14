use std::panic::{AssertUnwindSafe, catch_unwind};

use jni::JNIEnv;
use jni::objects::{JByteBuffer, JObject};
use jni::sys::jint;

use crate::runtime_identity_probe::{RuntimeProbeError, runtime_identity_from_response};

const SIGNATURE_STATUS_OK: jint = 0;
const SIGNATURE_STATUS_NOT_DIRECT: jint = 1;
const SIGNATURE_STATUS_INVALID_LENGTH: jint = 2;
const SIGNATURE_STATUS_INVALID_LIMIT: jint = 3;
const SIGNATURE_STATUS_EMPTY: jint = 4;
const SIGNATURE_STATUS_INVALID_UTF8: jint = 5;
const SIGNATURE_STATUS_TOO_LARGE: jint = 6;
const SIGNATURE_STATUS_PANIC: jint = 255;

fn ffi_guard(operation: impl FnOnce() -> jint) -> jint {
    catch_unwind(AssertUnwindSafe(operation)).unwrap_or(SIGNATURE_STATUS_PANIC)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_epicefi_epichub_engine_NativeEngineBridge_validateRuntimeSignatureResponse(
    env: JNIEnv,
    _object: JObject,
    buffer: JByteBuffer,
    response_length: jint,
    max_response_bytes: jint,
) -> jint {
    ffi_guard(|| {
        let Ok(response_length) = usize::try_from(response_length) else {
            return SIGNATURE_STATUS_INVALID_LENGTH;
        };
        let Ok(max_response_bytes) = usize::try_from(max_response_bytes) else {
            return SIGNATURE_STATUS_INVALID_LIMIT;
        };
        if max_response_bytes == 0 {
            return SIGNATURE_STATUS_INVALID_LIMIT;
        }

        let capacity = match env.get_direct_buffer_capacity(&buffer) {
            Ok(capacity) => capacity,
            Err(_) => return SIGNATURE_STATUS_NOT_DIRECT,
        };
        if response_length > capacity {
            return SIGNATURE_STATUS_INVALID_LENGTH;
        }
        let address = match env.get_direct_buffer_address(&buffer) {
            Ok(address) => address,
            Err(_) => return SIGNATURE_STATUS_NOT_DIRECT,
        };
        let response = unsafe { std::slice::from_raw_parts(address, response_length) };

        match runtime_identity_from_response(response, max_response_bytes) {
            Ok(_) => SIGNATURE_STATUS_OK,
            Err(RuntimeProbeError::InvalidResponseLimit) => SIGNATURE_STATUS_INVALID_LIMIT,
            Err(RuntimeProbeError::EmptySignature) => SIGNATURE_STATUS_EMPTY,
            Err(RuntimeProbeError::InvalidUtf8) => SIGNATURE_STATUS_INVALID_UTF8,
            Err(RuntimeProbeError::ResponseTooLarge { .. }) => SIGNATURE_STATUS_TOO_LARGE,
            Err(RuntimeProbeError::EmptyQuery | RuntimeProbeError::Transport(_)) => {
                SIGNATURE_STATUS_PANIC
            }
        }
    })
}
