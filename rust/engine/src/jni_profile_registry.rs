use std::collections::{HashMap, HashSet};
use std::panic::{AssertUnwindSafe, catch_unwind};

use jni::JNIEnv;
use jni::objects::{JByteBuffer, JLongArray, JObject, JObjectArray, JString};
use jni::sys::{JNI_FALSE, JNI_TRUE, jboolean, jint, jlong};

use crate::compiled_profile_bundle::OwnedDefinitionBundleImport;
use crate::profile_import::{OwnedCompiledProfileImport, OwnedSemanticTableBindingSpec};
use crate::profile_registry_store::{
    RegistryStoreError, active_profile_registry_count, close_profile_registry,
    create_profile_registry, profile_registry_is_alive, register_compiled_profile_import,
    register_definition_bundle_import, select_profile_summary_from_runtime_response,
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

const IMPORT_OK: jint = 0;
const IMPORT_INVALID_HANDLE: jint = -1;
const IMPORT_INVALID_ARGUMENT: jint = -2;
const IMPORT_BINDING_LENGTH_MISMATCH: jint = -3;
const IMPORT_COMPILE_ERROR: jint = -4;
const IMPORT_REGISTRY_ERROR: jint = -5;
const IMPORT_INCLUDE_LENGTH_MISMATCH: jint = -6;
const IMPORT_PANIC: jint = -255;

fn ffi_guard<T: Copy>(fallback: T, operation: impl FnOnce() -> T) -> T {
    catch_unwind(AssertUnwindSafe(operation)).unwrap_or(fallback)
}

fn map_store_error(error: RegistryStoreError) -> jlong {
    match error {
        RegistryStoreError::InvalidHandle => RESULT_INVALID_HANDLE,
        RegistryStoreError::Compile(_)
        | RegistryStoreError::BundleCompile(_)
        | RegistryStoreError::Registry(_)
        | RegistryStoreError::MissingCompiledBundle(_) => RESULT_REGISTRY_ERROR,
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

fn map_import_error(error: RegistryStoreError) -> jint {
    match error {
        RegistryStoreError::InvalidHandle => IMPORT_INVALID_HANDLE,
        RegistryStoreError::Compile(_) | RegistryStoreError::BundleCompile(_) => {
            IMPORT_COMPILE_ERROR
        }
        RegistryStoreError::Registry(_)
        | RegistryStoreError::Authority(_)
        | RegistryStoreError::MissingCompiledBundle(_) => IMPORT_REGISTRY_ERROR,
    }
}

fn required_string(env: &mut JNIEnv, value: JString) -> Result<String, ()> {
    if value.is_null() {
        return Err(());
    }
    env.get_string(&value).map(Into::into).map_err(|_| ())
}

fn optional_string(env: &mut JNIEnv, value: JString) -> Result<Option<String>, ()> {
    if value.is_null() {
        Ok(None)
    } else {
        required_string(env, value).map(Some)
    }
}

fn required_string_array(env: &mut JNIEnv, values: &JObjectArray) -> Result<Vec<String>, ()> {
    let count = env.get_array_length(values).map_err(|_| ())?;
    let mut result = Vec::with_capacity(count as usize);
    for index in 0..count {
        let object = env
            .get_object_array_element(values, index)
            .map_err(|_| ())?;
        let value = required_string(env, JString::from(object))?;
        result.push(value);
    }
    Ok(result)
}

fn semantic_bindings(
    env: &mut JNIEnv,
    semantic_ids: &JLongArray,
    editor_table_ids: &JObjectArray,
) -> Result<Vec<OwnedSemanticTableBindingSpec>, jint> {
    let semantic_count = env
        .get_array_length(semantic_ids)
        .map_err(|_| IMPORT_INVALID_ARGUMENT)?;
    let editor_count = env
        .get_array_length(editor_table_ids)
        .map_err(|_| IMPORT_INVALID_ARGUMENT)?;
    if semantic_count != editor_count {
        return Err(IMPORT_BINDING_LENGTH_MISMATCH);
    }

    let mut semantic_values = vec![0_i64; semantic_count as usize];
    env.get_long_array_region(semantic_ids, 0, &mut semantic_values)
        .map_err(|_| IMPORT_INVALID_ARGUMENT)?;

    let mut semantic_tables = Vec::with_capacity(semantic_count as usize);
    for (index, semantic_id) in semantic_values.into_iter().enumerate() {
        let semantic_id = u32::try_from(semantic_id).map_err(|_| IMPORT_INVALID_ARGUMENT)?;
        let editor_object = env
            .get_object_array_element(editor_table_ids, index as i32)
            .map_err(|_| IMPORT_INVALID_ARGUMENT)?;
        let editor_table_id = required_string(env, JString::from(editor_object))
            .map_err(|_| IMPORT_INVALID_ARGUMENT)?;
        if editor_table_id.is_empty() {
            return Err(IMPORT_INVALID_ARGUMENT);
        }
        semantic_tables.push(OwnedSemanticTableBindingSpec {
            semantic_id,
            editor_table_id,
        });
    }
    Ok(semantic_tables)
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
pub extern "system" fn Java_com_epicefi_epichub_engine_NativeProfileRegistryBridge_registerCompiledProfile(
    mut env: JNIEnv,
    _object: JObject,
    handle: jlong,
    profile_name: JString,
    profile_generation: jlong,
    definition_fingerprint: JString,
    resolved_definition: JString,
    semantic_ids: JLongArray,
    editor_table_ids: JObjectArray,
) -> jint {
    ffi_guard(IMPORT_PANIC, || {
        if !profile_registry_is_alive(handle) {
            return IMPORT_INVALID_HANDLE;
        }
        let Ok(profile_generation) = u64::try_from(profile_generation) else {
            return IMPORT_INVALID_ARGUMENT;
        };
        if profile_generation == 0 {
            return IMPORT_INVALID_ARGUMENT;
        }
        let Ok(profile_name) = required_string(&mut env, profile_name) else {
            return IMPORT_INVALID_ARGUMENT;
        };
        let Ok(definition_fingerprint) = optional_string(&mut env, definition_fingerprint) else {
            return IMPORT_INVALID_ARGUMENT;
        };
        let Ok(resolved_definition) = required_string(&mut env, resolved_definition) else {
            return IMPORT_INVALID_ARGUMENT;
        };
        if profile_name.is_empty() || resolved_definition.is_empty() {
            return IMPORT_INVALID_ARGUMENT;
        }

        let semantic_tables = match semantic_bindings(&mut env, &semantic_ids, &editor_table_ids) {
            Ok(bindings) => bindings,
            Err(status) => return status,
        };

        let import = OwnedCompiledProfileImport {
            profile_name,
            profile_generation,
            definition_fingerprint,
            resolved_definition,
            semantic_tables,
        };
        match register_compiled_profile_import(handle, &import) {
            Ok(()) => IMPORT_OK,
            Err(error) => map_import_error(error),
        }
    })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_epicefi_epichub_engine_NativeProfileRegistryBridge_registerDefinitionBundle(
    mut env: JNIEnv,
    _object: JObject,
    handle: jlong,
    profile_name: JString,
    profile_generation: jlong,
    definition_fingerprint: JString,
    root_definition: JString,
    enabled_symbols: JObjectArray,
    include_names: JObjectArray,
    include_contents: JObjectArray,
    semantic_ids: JLongArray,
    editor_table_ids: JObjectArray,
) -> jint {
    ffi_guard(IMPORT_PANIC, || {
        if !profile_registry_is_alive(handle) {
            return IMPORT_INVALID_HANDLE;
        }
        let Ok(profile_generation) = u64::try_from(profile_generation) else {
            return IMPORT_INVALID_ARGUMENT;
        };
        if profile_generation == 0 {
            return IMPORT_INVALID_ARGUMENT;
        }
        let Ok(profile_name) = required_string(&mut env, profile_name) else {
            return IMPORT_INVALID_ARGUMENT;
        };
        let Ok(definition_fingerprint) = optional_string(&mut env, definition_fingerprint) else {
            return IMPORT_INVALID_ARGUMENT;
        };
        let Ok(root_definition) = required_string(&mut env, root_definition) else {
            return IMPORT_INVALID_ARGUMENT;
        };
        if profile_name.is_empty() || root_definition.is_empty() {
            return IMPORT_INVALID_ARGUMENT;
        }

        let Ok(symbol_values) = required_string_array(&mut env, &enabled_symbols) else {
            return IMPORT_INVALID_ARGUMENT;
        };
        if symbol_values.iter().any(String::is_empty) {
            return IMPORT_INVALID_ARGUMENT;
        }
        let enabled_symbols = symbol_values.into_iter().collect::<HashSet<_>>();

        let Ok(include_name_values) = required_string_array(&mut env, &include_names) else {
            return IMPORT_INVALID_ARGUMENT;
        };
        let Ok(include_content_values) = required_string_array(&mut env, &include_contents) else {
            return IMPORT_INVALID_ARGUMENT;
        };
        if include_name_values.len() != include_content_values.len() {
            return IMPORT_INCLUDE_LENGTH_MISMATCH;
        }
        let mut includes = HashMap::with_capacity(include_name_values.len());
        for (name, content) in include_name_values
            .into_iter()
            .zip(include_content_values)
        {
            if name.is_empty() || includes.insert(name, content).is_some() {
                return IMPORT_INVALID_ARGUMENT;
            }
        }

        let semantic_tables = match semantic_bindings(&mut env, &semantic_ids, &editor_table_ids) {
            Ok(bindings) => bindings,
            Err(status) => return status,
        };

        let import = OwnedDefinitionBundleImport {
            profile_name,
            profile_generation,
            definition_fingerprint,
            root_definition,
            enabled_symbols,
            includes,
            semantic_tables,
        };
        match register_definition_bundle_import(handle, &import) {
            Ok(()) => IMPORT_OK,
            Err(error) => map_import_error(error),
        }
    })
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
            Ok(summary) => {
                jlong::try_from(summary.profile_generation).unwrap_or(RESULT_REGISTRY_ERROR)
            }
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
        assert_eq!(
            map_store_error(RegistryStoreError::InvalidHandle),
            RESULT_INVALID_HANDLE
        );
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
    fn import_error_mapping_distinguishes_compile_and_registry_failures() {
        assert_eq!(
            map_import_error(RegistryStoreError::InvalidHandle),
            IMPORT_INVALID_HANDLE
        );
        assert_eq!(
            map_import_error(RegistryStoreError::Registry(
                ProfileRegistryError::DuplicateProfileSignature("duplicate".to_string())
            )),
            IMPORT_REGISTRY_ERROR
        );
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
