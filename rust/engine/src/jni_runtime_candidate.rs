//! Versioned, native-only candidate registration for Android's retained USB owner.
//! No JNI method here performs ECU IO or publishes a live Dashboard sample.
use std::panic::{AssertUnwindSafe, catch_unwind};

use jni::JNIEnv;
use jni::objects::{JByteBuffer, JObject, JString};
use jni::sys::{jint, jlong};

use crate::profile_registry_store::{
    RegistryStoreError, RuntimeRegistryBindingError, bind_runtime_candidate_from_registry,
    disconnect_runtime_candidate_from_registry,
};
use crate::runtime_bound_live_session::BoundRuntimeError;

const CANDIDATE_INVALID_HANDLE: jlong = -1;
const CANDIDATE_INVALID_ARGUMENT: jlong = -2;
const CANDIDATE_NOT_DIRECT: jlong = -3;
const CANDIDATE_INVALID_LENGTH: jlong = -4;
const CANDIDATE_REGISTRY_REJECTED: jlong = -5;
const CANDIDATE_GENERATION_MISMATCH: jlong = -6;
const CANDIDATE_DEFINITION_UNSUPPORTED: jlong = -7;
const CANDIDATE_OVERFLOW: jlong = -8;
const CANDIDATE_PANIC: jlong = -255;

fn ffi_guard<T: Copy>(fallback: T, operation: impl FnOnce() -> T) -> T {
    catch_unwind(AssertUnwindSafe(operation)).unwrap_or(fallback)
}

fn mapped(error: RuntimeRegistryBindingError) -> jlong {
    match error {
        RuntimeRegistryBindingError::Registry(RegistryStoreError::InvalidHandle) => {
            CANDIDATE_INVALID_HANDLE
        }
        RuntimeRegistryBindingError::Registry(_) | RuntimeRegistryBindingError::ProfileMismatch => {
            CANDIDATE_REGISTRY_REJECTED
        }
        RuntimeRegistryBindingError::GenerationMismatch => CANDIDATE_GENERATION_MISMATCH,
        RuntimeRegistryBindingError::Bound(BoundRuntimeError::EpochExhausted) => {
            CANDIDATE_OVERFLOW
        }
        RuntimeRegistryBindingError::Bound(_) => CANDIDATE_DEFINITION_UNSUPPORTED,
    }
}

/// Positive value is a native candidate epoch, *not* live-data proof.
/// JNI method is deliberately not a public transport API.
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_epicefi_epichub_engine_NativeProfileRegistryBridge_bindRuntimeCandidate(
    mut env: JNIEnv,
    _object: JObject,
    handle: jlong,
    endpoint: JString,
    signature: JByteBuffer,
    signature_length: jint,
    max_response_bytes: jint,
    expected_generation: jlong,
) -> jlong {
    ffi_guard(CANDIDATE_PANIC, || {
        // Fail closed before inspecting the signature's direct buffer.
        if endpoint.is_null() || expected_generation <= 0 || max_response_bytes <= 0 {
            return CANDIDATE_INVALID_ARGUMENT;
        }
        let Ok(endpoint): Result<String, _> = env.get_string(&endpoint).map(Into::into) else {
            return CANDIDATE_INVALID_ARGUMENT;
        };
        if endpoint.trim().is_empty() {
            return CANDIDATE_INVALID_ARGUMENT;
        }
        let Ok(length) = usize::try_from(signature_length) else {
            return CANDIDATE_INVALID_LENGTH;
        };
        let capacity = match env.get_direct_buffer_capacity(&signature) {
            Ok(value) => value,
            Err(_) => return CANDIDATE_NOT_DIRECT,
        };
        if length == 0 || length > capacity {
            return CANDIDATE_INVALID_LENGTH;
        }
        let Ok(pointer) = env.get_direct_buffer_address(&signature) else {
            return CANDIDATE_NOT_DIRECT;
        };
        let response = unsafe { std::slice::from_raw_parts(pointer, length) };
        match bind_runtime_candidate_from_registry(
            handle,
            &endpoint,
            response,
            max_response_bytes as usize,
            expected_generation as u64,
        ) {
            Ok(binding) => match jlong::try_from(binding.session_epoch()) {
                Ok(epoch) if epoch > 0 => epoch,
                _ => {
                    let _ = disconnect_runtime_candidate_from_registry(handle);
                    CANDIDATE_OVERFLOW
                }
            },
            Err(error) => mapped(error),
        }
    })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_epicefi_epichub_engine_NativeProfileRegistryBridge_disconnectRuntimeCandidate(
    _env: JNIEnv,
    _object: JObject,
    handle: jlong,
) -> jint {
    ffi_guard(CANDIDATE_PANIC as jint, || {
        match disconnect_runtime_candidate_from_registry(handle) {
            Ok(()) => 0,
            Err(RegistryStoreError::InvalidHandle) => CANDIDATE_INVALID_HANDLE as jint,
            Err(_) => CANDIDATE_REGISTRY_REJECTED as jint,
        }
    })
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn candidate_error_mapping_distinguishes_unsupported_and_authority_failure() {
        assert_eq!(
            mapped(RuntimeRegistryBindingError::GenerationMismatch),
            CANDIDATE_GENERATION_MISMATCH
        );
        assert_eq!(
            mapped(RuntimeRegistryBindingError::Registry(RegistryStoreError::InvalidHandle)),
            CANDIDATE_INVALID_HANDLE
        );
        assert_eq!(
            mapped(RuntimeRegistryBindingError::Bound(BoundRuntimeError::DefinitionCommand)),
            CANDIDATE_DEFINITION_UNSUPPORTED
        );
        assert_eq!(
            mapped(RuntimeRegistryBindingError::Bound(BoundRuntimeError::EpochExhausted)),
            CANDIDATE_OVERFLOW
        );
    }
}
