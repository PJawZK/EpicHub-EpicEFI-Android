use std::collections::{HashMap, HashSet};
use std::panic::{AssertUnwindSafe, catch_unwind};

use jni::JNIEnv;
use jni::objects::{JByteBuffer, JLongArray, JObject, JObjectArray, JString};
use jni::sys::{jint, jlong};

use crate::compiled_profile_bundle::{OwnedDefinitionBundleImport, compile_definition_bundle};
use crate::profile_import::OwnedSemanticTableBindingSpec;

const RESULT_NOT_DIRECT: jint = -1;
const RESULT_INVALID_ARGUMENT: jint = -2;
const RESULT_BINDING_LENGTH_MISMATCH: jint = -3;
const RESULT_INCLUDE_LENGTH_MISMATCH: jint = -4;
const RESULT_COMPILE_ERROR: jint = -5;
const RESULT_INVALID_QUERY: jint = -6;
const RESULT_PANIC: jint = -255;

fn ffi_guard(operation: impl FnOnce() -> jint) -> jint {
    catch_unwind(AssertUnwindSafe(operation)).unwrap_or(RESULT_PANIC)
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
        result.push(required_string(env, JString::from(object))?);
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
        .map_err(|_| RESULT_INVALID_ARGUMENT)?;
    let editor_count = env
        .get_array_length(editor_table_ids)
        .map_err(|_| RESULT_INVALID_ARGUMENT)?;
    if semantic_count != editor_count {
        return Err(RESULT_BINDING_LENGTH_MISMATCH);
    }

    let mut semantic_values = vec![0_i64; semantic_count as usize];
    env.get_long_array_region(semantic_ids, 0, &mut semantic_values)
        .map_err(|_| RESULT_INVALID_ARGUMENT)?;

    let mut result = Vec::with_capacity(semantic_count as usize);
    for (index, semantic_id) in semantic_values.into_iter().enumerate() {
        let semantic_id = u32::try_from(semantic_id).map_err(|_| RESULT_INVALID_ARGUMENT)?;
        let editor_object = env
            .get_object_array_element(editor_table_ids, index as i32)
            .map_err(|_| RESULT_INVALID_ARGUMENT)?;
        let editor_table_id = required_string(env, JString::from(editor_object))
            .map_err(|_| RESULT_INVALID_ARGUMENT)?;
        if editor_table_id.is_empty() {
            return Err(RESULT_INVALID_ARGUMENT);
        }
        result.push(OwnedSemanticTableBindingSpec {
            semantic_id,
            editor_table_id,
        });
    }
    Ok(result)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_epicefi_epichub_engine_NativeDefinitionProbePlanBridge_compileDefinitionProbeQuery(
    mut env: JNIEnv,
    _object: JObject,
    profile_name: JString,
    profile_generation: jlong,
    definition_fingerprint: JString,
    root_definition: JString,
    enabled_symbols: JObjectArray,
    include_names: JObjectArray,
    include_contents: JObjectArray,
    semantic_ids: JLongArray,
    editor_table_ids: JObjectArray,
    output: JByteBuffer,
) -> jint {
    ffi_guard(|| {
        let Ok(profile_generation) = u64::try_from(profile_generation) else {
            return RESULT_INVALID_ARGUMENT;
        };
        if profile_generation == 0 {
            return RESULT_INVALID_ARGUMENT;
        }
        let Ok(profile_name) = required_string(&mut env, profile_name) else {
            return RESULT_INVALID_ARGUMENT;
        };
        let Ok(definition_fingerprint) = optional_string(&mut env, definition_fingerprint) else {
            return RESULT_INVALID_ARGUMENT;
        };
        let Ok(root_definition) = required_string(&mut env, root_definition) else {
            return RESULT_INVALID_ARGUMENT;
        };
        if profile_name.is_empty() || root_definition.is_empty() {
            return RESULT_INVALID_ARGUMENT;
        }

        let Ok(symbol_values) = required_string_array(&mut env, &enabled_symbols) else {
            return RESULT_INVALID_ARGUMENT;
        };
        if symbol_values.iter().any(String::is_empty) {
            return RESULT_INVALID_ARGUMENT;
        }
        let enabled_symbols = symbol_values.into_iter().collect::<HashSet<_>>();

        let Ok(include_name_values) = required_string_array(&mut env, &include_names) else {
            return RESULT_INVALID_ARGUMENT;
        };
        let Ok(include_content_values) = required_string_array(&mut env, &include_contents) else {
            return RESULT_INVALID_ARGUMENT;
        };
        if include_name_values.len() != include_content_values.len() {
            return RESULT_INCLUDE_LENGTH_MISMATCH;
        }
        let mut includes = HashMap::with_capacity(include_name_values.len());
        for (name, content) in include_name_values.into_iter().zip(include_content_values) {
            if name.is_empty() || includes.insert(name, content).is_some() {
                return RESULT_INVALID_ARGUMENT;
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
        let bundle = match compile_definition_bundle(&import) {
            Ok(bundle) => bundle,
            Err(_) => return RESULT_COMPILE_ERROR,
        };
        let query = bundle.profile.definition().query_command.as_bytes();
        if query.is_empty() {
            return RESULT_INVALID_QUERY;
        }

        let capacity = match env.get_direct_buffer_capacity(&output) {
            Ok(capacity) => capacity,
            Err(_) => return RESULT_NOT_DIRECT,
        };
        if query.len() > capacity || query.len() > jint::MAX as usize {
            return RESULT_INVALID_QUERY;
        }
        let address = match env.get_direct_buffer_address(&output) {
            Ok(address) => address,
            Err(_) => return RESULT_NOT_DIRECT,
        };
        unsafe {
            std::ptr::copy_nonoverlapping(query.as_ptr(), address, query.len());
        }
        query.len() as jint
    })
}
