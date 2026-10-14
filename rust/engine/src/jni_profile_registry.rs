use std::panic::{AssertUnwindSafe, catch_unwind};

use jni::JNIEnv;
use jni::objects::{JByteBuffer, JObject};
use jni::sys::{JNI_FALSE, JNI_TRUE, jboolean, jint, jlong};

use crate::profile_registry_store::{
    RegistryStoreError, active_profile_registry_count, close_profile_registry, create_profile_registry,
    profile_registry_is_alive, select_profile_summary_from_runtime_response,
};
use crate::runtime_identity_probe::RuntimeProbeError;
use crate::runtime_profile_authority::RuntimeProfileAuthorityError;

const RESULT_INVALID_HANDLE: jlong = -1;
const RESULT_NOT_DIRECT: jlong = -2;
const RESULT_INVALID_LENGTH: jlong = -3;
const RESULT_INVALID_LIMIT: jlong = -4;
const RESULT_EMPTY_SIGNATURE: jlong = -5;
const RESULT_INVALID_UTF8: jlong = -6;
const RESULT_TOO_LARGE: jlong = -7;
const RESULT_UNKNOWN_CONTROLLER: jlong = -8;
const RESULT_RECOGNIZED_NO_PROFILE: jlong = -9;
const RESULT_REGISTRY_ERROR: jlong = -10;
const RESULT_PANIC: jlong = -255;

fn ffi_guard<T: Copy>(fallback: T, operation: impl FnOnce() -> T) -> T {
    catch_unwind(AssertUnwindSafe(operation)).unwrap_or(fallback)
}

fn map_store_error(error: RegistryStoreError) -> jlong {
    match error {
        RegistryStoreError::InvalidHandle => RESULT_INVALID_HANDLE,
        RegistryStoreError::Registry(_) => RESULT_REGISTRY_ERROR,
        RegistryStoreError::Authority(authority) => match authority {
            RuntimeProfileAuthorityError::UnknownController => RESULT_UNKNOWN_CONTROLLER,
            RuntimeProfileAuthorityError::RecognizedControllerWithoutProfile => {
                RESULT_RECOGNIZED_NO_PROFILE
            }
            RuntimeProfileAuthorityError::Identity(identity) => match identity {
                RuntimeProbeError::InvalidResponseLimit => RESULT_INVALID_LIMIT,
                RuntimeProbeError::EmptySignature => RESULT_EMPTY_SIGNATURE,
                RuntimeProbeError::InvalidUtf8 => RESULT_INVALID_UTF8,
                RuntimeProbeError::ResponseTooLarge { .. } => RESULT_TOO_LARGE,
                RuntimeProbeError::EmptyQuery | RuntimeProbeError::Transport(_) => RESULT_PANIC,
            },
        },
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_epicefi_epichub_engine_NativeProfileRegistryBridge_create(
    _env: JNIEnv,
    _object: JObject,
) -> jlong {
    ffi_guard(0, create_profile_registry)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_epicefi_epichub_engine_NativeProfileRegistryBridge_close(
    _env: JNIEnv,
    _object: JObject,
    handle: jlong,
) {
    ffi_guard((), || close_profile_registry(handle));
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_epicefi_epichub_engine_NativeProfileRegistryBridge_isAlive(
    _env: JNIEnv,
    _object: JObject,
    handle: jlong,
) -> jboolean {
    ffi_guard(JNI_FALSE, || {
        if profile_registry_is_alive(handle) {
            JNI_TRUE
        } else {
            JNI_FALSE
        }
    })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_epicefi_epichub_engine_NativeProfileRegistryBridge_activeCount(
    _env: JNIEnv,
    _object: JObject,
) -> jint {
    ffi_guard(0, active_profile_registry_count)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_epicefi_epichub_engine_NativeProfileRegistryBridge_selectRuntimeProfileGeneration(
    env: JNIEnv,
    _object: JObject,
    handle: jlong,
    buffer: JByteBuffer,
    response_length: jint,
    max_response_bytes: jint,
) -> jlong {
    ffi_guard(RESULT_PANIC, || {
        if !profile_registry_is_alive(handle) {
            return RESULT_INVALID_HANDLE;
        }
        let Ok(response_length) = usize::try_from(response_length) else {
            return RESULT_INVALID_LENGTH;
        };
        let Ok(max_response_bytes) = usize::try_from(max_response_bytes) else {
            return RESULT_INVALID_LIMIT;
        };
        if max_response_bytes == 0 {
            return RESULT_INVALID_LIMIT;
        }

        let capacity = match env.get_direct_buffer_capacity(&buffer) {
            Ok(capacity) => capacity,
            Err(_) => return RESULT_NOT_DIRECT,
        };
        if response_length > capacity {
            return RESULT_INVALID_LENGTH;
        }
        let address = match env.get_direct_buffer_address(&buffer) {
            Ok(address) => address,
            Err(_) => return RESULT_NOT_DIRECT,
        };
        let response = unsafe { std::slice::from_raw_parts(address, response_length) };

        match select_profile_summary_from_runtime_response(handle, response, max_response_bytes) {
            Ok(summary) => jlong::try_from(summary.profile_generation).unwrap_or(RESULT_REGISTRY_ERROR),
            Err(error) => map_store_error(error),
        }
    })
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::ecu_profile_registry::{ProfileRegistryError, ProfileSelectionError};

    #[test]
    fn store_error_mapping_preserves_authority_categories() {
        assert_eq!(map_store_error(RegistryStoreError::InvalidHandle), RESULT_INVALID_HANDLE);
        assert_eq!(
            map_store_error(RegistryStoreError::Registry(
                ProfileRegistryError::DuplicateProfileSignature("duplicate".to_string())
            )),
            RESULT_REGISTRY_ERROR
        );
        assert_eq!(
            map_store_error(RegistryStoreError::Authority(
                RuntimeProfileAuthorityError::UnknownController
            )),
            RESULT_UNKNOWN_CONTROLLER
        );
        assert_eq!(
            map_store_error(RegistryStoreError::Authority(
                RuntimeProfileAuthorityError::RecognizedControllerWithoutProfile
            )),
            RESULT_RECOGNIZED_NO_PROFILE
        );
        let _ = ProfileSelectionError::UnknownController;
    }

    #[test]
    fn identity_error_mapping_is_explicit() {
        assert_eq!(
            map_store_error(RegistryStoreError::Authority(
                RuntimeProfileAuthorityError::Identity(RuntimeProbeError::InvalidResponseLimit)
            )),
            RESULT_INVALID_LIMIT
        );
        assert_eq!(
            map_store_error(RegistryStoreError::Authority(
                RuntimeProfileAuthorityError::Identity(RuntimeProbeError::EmptySignature)
            )),
            RESULT_EMPTY_SIGNATURE
        );
        assert_eq!(
            map_store_error(RegistryStoreError::Authority(
                RuntimeProfileAuthorityError::Identity(RuntimeProbeError::InvalidUtf8)
            )),
            RESULT_INVALID_UTF8
        );
        assert_eq!(
            map_store_error(RegistryStoreError::Authority(
                RuntimeProfileAuthorityError::Identity(RuntimeProbeError::ResponseTooLarge {
                    limit: 1,
                    actual: 2,
                })
            )),
            RESULT_TOO_LARGE
        );
    }
}
