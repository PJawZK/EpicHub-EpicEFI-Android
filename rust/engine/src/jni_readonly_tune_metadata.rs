use std::panic::{AssertUnwindSafe, catch_unwind};

use jni::JNIEnv;
use jni::objects::{JByteBuffer, JObject};
use jni::sys::jint;

use crate::ecu_profile::ResolvedEcuProfile;
use crate::profile_registry_store::{RegistryStoreError, with_selected_compiled_bundle};
use crate::runtime_identity_probe::RuntimeProbeError;
use crate::runtime_profile_authority::RuntimeProfileAuthorityError;

const META_INVALID_HANDLE: jint = -1;
const META_NOT_DIRECT: jint = -2;
const META_INVALID_LENGTH: jint = -3;
const META_INVALID_LIMIT: jint = -4;
const META_EMPTY_SIGNATURE: jint = -5;
const META_INVALID_UTF8: jint = -6;
const META_TOO_LARGE: jint = -7;
const META_UNKNOWN_CONTROLLER: jint = -8;
const META_RECOGNIZED_NO_PROFILE: jint = -9;
const META_MISSING_BUNDLE: jint = -10;
const META_REGISTRY_ERROR: jint = -17;
const META_PANIC: jint = -255;

fn ffi_guard<T: Copy>(fallback: T, operation: impl FnOnce() -> T) -> T {
    catch_unwind(AssertUnwindSafe(operation)).unwrap_or(fallback)
}

fn map_store_error(error: RegistryStoreError) -> jint {
    match error {
        RegistryStoreError::InvalidHandle => META_INVALID_HANDLE,
        RegistryStoreError::MissingCompiledBundle(_) => META_MISSING_BUNDLE,
        RegistryStoreError::Authority(authority) => match authority {
            RuntimeProfileAuthorityError::UnknownController => META_UNKNOWN_CONTROLLER,
            RuntimeProfileAuthorityError::RecognizedControllerWithoutProfile => {
                META_RECOGNIZED_NO_PROFILE
            }
            RuntimeProfileAuthorityError::Identity(identity) => match identity {
                RuntimeProbeError::InvalidResponseLimit => META_INVALID_LIMIT,
                RuntimeProbeError::EmptySignature => META_EMPTY_SIGNATURE,
                RuntimeProbeError::InvalidUtf8 => META_INVALID_UTF8,
                RuntimeProbeError::ResponseTooLarge { .. } => META_TOO_LARGE,
                RuntimeProbeError::EmptyQuery | RuntimeProbeError::Transport(_) => META_PANIC,
            },
        },
        RegistryStoreError::Compile(_)
        | RegistryStoreError::BundleCompile(_)
        | RegistryStoreError::Registry(_) => META_REGISTRY_ERROR,
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_epicefi_epichub_engine_NativeReadOnlyTuneMetadataBridge_pageCount(
    env: JNIEnv,
    _object: JObject,
    handle: i64,
    signature_buffer: JByteBuffer,
    signature_length: jint,
    signature_limit: jint,
) -> jint {
    ffi_guard(META_PANIC, || {
        let Ok(signature_length) = usize::try_from(signature_length) else {
            return META_INVALID_LENGTH;
        };
        let Ok(signature_limit) = usize::try_from(signature_limit) else {
            return META_INVALID_LIMIT;
        };
        if signature_limit == 0 {
            return META_INVALID_LIMIT;
        }
        if signature_length > signature_limit {
            return META_TOO_LARGE;
        }
        let capacity = match env.get_direct_buffer_capacity(&signature_buffer) {
            Ok(value) => value,
            Err(_) => return META_NOT_DIRECT,
        };
        if signature_length > capacity {
            return META_INVALID_LENGTH;
        }
        let address = match env.get_direct_buffer_address(&signature_buffer) {
            Ok(value) => value,
            Err(_) => return META_NOT_DIRECT,
        };
        let signature = unsafe { std::slice::from_raw_parts(address, signature_length) };

        match with_selected_compiled_bundle(handle, signature, signature_limit, |bundle| {
            bundle.profile.page_sizes().len()
        }) {
            Ok(count) if count > 0 => jint::try_from(count).unwrap_or(META_REGISTRY_ERROR),
            Ok(_) => META_REGISTRY_ERROR,
            Err(error) => map_store_error(error),
        }
    })
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn authority_categories_remain_explicit() {
        assert_eq!(map_store_error(RegistryStoreError::InvalidHandle), META_INVALID_HANDLE);
        assert_eq!(
            map_store_error(RegistryStoreError::MissingCompiledBundle("sig".to_string())),
            META_MISSING_BUNDLE
        );
        assert_eq!(
            map_store_error(RegistryStoreError::Authority(
                RuntimeProfileAuthorityError::UnknownController
            )),
            META_UNKNOWN_CONTROLLER
        );
    }
}
