use std::panic::{AssertUnwindSafe, catch_unwind};

use jni::JNIEnv;
use jni::objects::{JByteBuffer, JObject, JObjectArray};
use jni::sys::{jint, jlong};

use crate::engine::engine_is_alive;
use crate::live_tune_install::{LiveTuneInstallError, install_selected_live_pages};
use crate::profile_registry_store::RegistryStoreError;
use crate::runtime_identity_probe::RuntimeProbeError;
use crate::runtime_profile_authority::RuntimeProfileAuthorityError;
use crate::tune_source_install::TuneSourceInstallError;

const INSTALL_INVALID_ENGINE_HANDLE: jlong = -1;
const INSTALL_INVALID_REGISTRY_HANDLE: jlong = -2;
const INSTALL_NOT_DIRECT: jlong = -3;
const INSTALL_INVALID_LENGTH: jlong = -4;
const INSTALL_INVALID_LIMIT: jlong = -5;
const INSTALL_EMPTY_SIGNATURE: jlong = -6;
const INSTALL_INVALID_UTF8: jlong = -7;
const INSTALL_TOO_LARGE: jlong = -8;
const INSTALL_UNKNOWN_CONTROLLER: jlong = -9;
const INSTALL_RECOGNIZED_NO_PROFILE: jlong = -10;
const INSTALL_MISSING_BUNDLE: jlong = -11;
const INSTALL_INVALID_PAGES: jlong = -12;
const INSTALL_DECODE_FAILURE: jlong = -13;
const INSTALL_REGISTRY_FAILURE: jlong = -14;
const INSTALL_PANIC: jlong = -255;

fn ffi_guard<T: Copy>(fallback: T, operation: impl FnOnce() -> T) -> T {
    catch_unwind(AssertUnwindSafe(operation)).unwrap_or(fallback)
}

fn direct_bytes(
    env: &JNIEnv,
    buffer: &JByteBuffer,
    length: jint,
    max_length: jint,
) -> Result<Vec<u8>, jlong> {
    let length = usize::try_from(length).map_err(|_| INSTALL_INVALID_LENGTH)?;
    let max_length = usize::try_from(max_length).map_err(|_| INSTALL_INVALID_LIMIT)?;
    if max_length == 0 {
        return Err(INSTALL_INVALID_LIMIT);
    }
    if length > max_length {
        return Err(INSTALL_TOO_LARGE);
    }
    let capacity = env
        .get_direct_buffer_capacity(buffer)
        .map_err(|_| INSTALL_NOT_DIRECT)?;
    if length > capacity {
        return Err(INSTALL_INVALID_LENGTH);
    }
    let address = env
        .get_direct_buffer_address(buffer)
        .map_err(|_| INSTALL_NOT_DIRECT)?;
    Ok(unsafe { std::slice::from_raw_parts(address, length) }.to_vec())
}

fn direct_page_array(env: &mut JNIEnv, pages: &JObjectArray) -> Result<Vec<Vec<u8>>, jlong> {
    let count = env
        .get_array_length(pages)
        .map_err(|_| INSTALL_INVALID_PAGES)?;
    let mut result = Vec::with_capacity(count as usize);
    for index in 0..count {
        let object = env
            .get_object_array_element(pages, index)
            .map_err(|_| INSTALL_INVALID_PAGES)?;
        if object.is_null() {
            return Err(INSTALL_INVALID_PAGES);
        }
        let buffer = JByteBuffer::from(object);
        let capacity = env
            .get_direct_buffer_capacity(&buffer)
            .map_err(|_| INSTALL_NOT_DIRECT)?;
        let address = env
            .get_direct_buffer_address(&buffer)
            .map_err(|_| INSTALL_NOT_DIRECT)?;
        result.push(unsafe { std::slice::from_raw_parts(address, capacity) }.to_vec());
    }
    Ok(result)
}

fn map_registry_error(error: RegistryStoreError) -> jlong {
    match error {
        RegistryStoreError::InvalidHandle => INSTALL_INVALID_REGISTRY_HANDLE,
        RegistryStoreError::MissingCompiledBundle(_) => INSTALL_MISSING_BUNDLE,
        RegistryStoreError::Authority(authority) => match authority {
            RuntimeProfileAuthorityError::UnknownController => INSTALL_UNKNOWN_CONTROLLER,
            RuntimeProfileAuthorityError::RecognizedControllerWithoutProfile => {
                INSTALL_RECOGNIZED_NO_PROFILE
            }
            RuntimeProfileAuthorityError::Identity(identity) => match identity {
                RuntimeProbeError::InvalidResponseLimit => INSTALL_INVALID_LIMIT,
                RuntimeProbeError::EmptySignature => INSTALL_EMPTY_SIGNATURE,
                RuntimeProbeError::InvalidUtf8 => INSTALL_INVALID_UTF8,
                RuntimeProbeError::ResponseTooLarge { .. } => INSTALL_TOO_LARGE,
                RuntimeProbeError::EmptyQuery | RuntimeProbeError::Transport(_) => INSTALL_PANIC,
            },
        },
        RegistryStoreError::Compile(_)
        | RegistryStoreError::BundleCompile(_)
        | RegistryStoreError::Registry(_) => INSTALL_REGISTRY_FAILURE,
    }
}

fn map_install_error(error: LiveTuneInstallError) -> jlong {
    match error {
        LiveTuneInstallError::ProfileAuthority(error) => map_registry_error(error),
        LiveTuneInstallError::Install(TuneSourceInstallError::InvalidHandle) => {
            INSTALL_INVALID_ENGINE_HANDLE
        }
        LiveTuneInstallError::Install(TuneSourceInstallError::Resolve(_)) => INSTALL_DECODE_FAILURE,
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_epicefi_epichub_engine_NativeLiveTuneInstallBridge_installLivePages(
    mut env: JNIEnv,
    _object: JObject,
    engine_handle: jlong,
    registry_handle: jlong,
    signature_buffer: JByteBuffer,
    signature_length: jint,
    signature_limit: jint,
    pages: JObjectArray,
) -> jlong {
    ffi_guard(INSTALL_PANIC, || {
        if !engine_is_alive(engine_handle) {
            return INSTALL_INVALID_ENGINE_HANDLE;
        }
        let signature = match direct_bytes(
            &env,
            &signature_buffer,
            signature_length,
            signature_limit,
        ) {
            Ok(value) => value,
            Err(status) => return status,
        };
        let Ok(signature_limit) = usize::try_from(signature_limit) else {
            return INSTALL_INVALID_LIMIT;
        };
        let page_storage = match direct_page_array(&mut env, &pages) {
            Ok(value) => value,
            Err(status) => return status,
        };
        let page_refs = page_storage.iter().map(Vec::as_slice).collect::<Vec<_>>();

        match install_selected_live_pages(
            engine_handle,
            registry_handle,
            &signature,
            signature_limit,
            &page_refs,
        ) {
            Ok(generation) => jlong::try_from(generation).unwrap_or(INSTALL_REGISTRY_FAILURE),
            Err(error) => map_install_error(error),
        }
    })
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::ecu_profile_registry::{ProfileRegistryError, ProfileSelectionError};

    #[test]
    fn registry_authority_failures_stay_distinct() {
        assert_eq!(
            map_registry_error(RegistryStoreError::InvalidHandle),
            INSTALL_INVALID_REGISTRY_HANDLE
        );
        assert_eq!(
            map_registry_error(RegistryStoreError::MissingCompiledBundle("sig".to_string())),
            INSTALL_MISSING_BUNDLE
        );
        assert_eq!(
            map_registry_error(RegistryStoreError::Authority(
                RuntimeProfileAuthorityError::UnknownController
            )),
            INSTALL_UNKNOWN_CONTROLLER
        );
        assert_eq!(
            map_registry_error(RegistryStoreError::Registry(
                ProfileRegistryError::DuplicateProfileSignature("sig".to_string())
            )),
            INSTALL_REGISTRY_FAILURE
        );
        let _ = ProfileSelectionError::UnknownController;
    }

    #[test]
    fn decode_and_engine_failures_stay_distinct() {
        assert_eq!(
            map_install_error(LiveTuneInstallError::Install(
                TuneSourceInstallError::InvalidHandle
            )),
            INSTALL_INVALID_ENGINE_HANDLE
        );
    }
}
