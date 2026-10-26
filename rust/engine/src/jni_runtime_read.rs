//! Version 1 internal full-frame bridge, callable only while Android's retained
//! USB owner holds EngineAuthoritySerialization. This module never owns a port.
use std::panic::{AssertUnwindSafe, catch_unwind};

use jni::JNIEnv;
use jni::objects::{JByteBuffer, JObject};
use jni::sys::{jint, jlong};

use crate::live_channel_store::LiveChannelBatch;
use crate::profile_registry_store::{
    RuntimeRegistryBindingError, disconnect_runtime_candidate_from_registry,
    finish_runtime_read_from_registry, prepare_runtime_read_from_registry,
};
use crate::runtime_acquisition_session::RuntimeSampleState;
use crate::runtime_bound_live_session::{BoundRuntimeError, BoundRuntimeIdentity};
use crate::runtime_channel_decode::MAX_RUNTIME_CHANNELS;

const ERR_ARGUMENT: i64 = -1;
const ERR_BUFFER: i64 = -2;
const ERR_AUTHORITY: i64 = -3;
const ERR_NATIVE: i64 = -4;
const ERR_PANIC: i64 = -255;
const REQUEST_HEADER: usize = 8;
const BATCH_HEADER: usize = 56;
const SAMPLE_BYTES: usize = 12;
const MAX_REQUEST_BYTES: usize = 64;
const MAX_RESPONSE_BYTES: usize = 65_536;
const MAX_BATCH_BYTES: usize = BATCH_HEADER + MAX_RUNTIME_CHANNELS * SAMPLE_BYTES;

fn ffi_guard<T: Copy>(fallback: T, op: impl FnOnce() -> T) -> T {
    catch_unwind(AssertUnwindSafe(op)).unwrap_or(fallback)
}
fn map_failure(err: RuntimeRegistryBindingError) -> i64 {
    match err {
        RuntimeRegistryBindingError::Bound(BoundRuntimeError::SessionClosed)
        | RuntimeRegistryBindingError::Bound(BoundRuntimeError::StaleAuthority)
        | RuntimeRegistryBindingError::Registry(_)
        | RuntimeRegistryBindingError::GenerationMismatch
        | RuntimeRegistryBindingError::ProfileMismatch => ERR_AUTHORITY,
        _ => ERR_NATIVE,
    }
}
fn freshness_code(state: RuntimeSampleState) -> u32 {
    match state {
        RuntimeSampleState::Unknown => 0,
        RuntimeSampleState::Fresh => 1,
        RuntimeSampleState::Stale => 2,
        RuntimeSampleState::Invalid => 3,
    }
}

/// Fixed little-endian v1 record. Explicit source magic prevents consumers
/// mistaking this for the existing synthetic four-channel RuntimeSnapshot.
/// Header: magic EPIC, version, epoch, sequence, timestamp, generation,
/// definition revision, count, validity. Samples: id, f32, validity (12 B).
fn encode_batch(binding: &BoundRuntimeIdentity, batch: &LiveChannelBatch) -> Option<Vec<u8>> {
    if batch.session != binding.session_epoch()
        || batch.validity != RuntimeSampleState::Fresh
        || batch.samples.is_empty()
        || batch.samples.len() > MAX_RUNTIME_CHANNELS
    {
        return None;
    }
    let mut output = Vec::with_capacity(BATCH_HEADER + SAMPLE_BYTES * batch.samples.len());
    output.extend_from_slice(b"EPIC");
    output.extend_from_slice(&1_u32.to_le_bytes());
    output.extend_from_slice(&batch.session.to_le_bytes());
    output.extend_from_slice(&batch.sequence.to_le_bytes());
    output.extend_from_slice(&batch.received_at_ms.to_le_bytes());
    output.extend_from_slice(&binding.profile_generation().to_le_bytes());
    output.extend_from_slice(&binding.definition_revision().to_le_bytes());
    output.extend_from_slice(&(batch.samples.len() as u32).to_le_bytes());
    output.extend_from_slice(&freshness_code(batch.validity).to_le_bytes());
    for channel in &batch.samples {
        if channel.id == 0
            || !channel.value.is_finite()
            || channel.validity != RuntimeSampleState::Fresh
        {
            return None;
        }
        output.extend_from_slice(&channel.id.to_le_bytes());
        output.extend_from_slice(&channel.value.to_le_bytes());
        output.extend_from_slice(&freshness_code(channel.validity).to_le_bytes());
    }
    Some(output)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_epicefi_epichub_engine_NativeProfileRegistryBridge_prepareRuntimeRead(
    env: JNIEnv,
    _object: JObject,
    handle: jlong,
    epoch: jlong,
    now_ms: jlong,
    timeout_ms: jlong,
    output: JByteBuffer,
) -> jlong {
    ffi_guard(ERR_PANIC, || {
        if handle <= 0 || epoch <= 0 || now_ms < 0 || timeout_ms <= 0 {
            return ERR_ARGUMENT;
        }
        let capacity = match env.get_direct_buffer_capacity(&output) {
            Ok(capacity) if capacity >= REQUEST_HEADER + MAX_REQUEST_BYTES => capacity,
            _ => return ERR_BUFFER,
        };
        let pointer = match env.get_direct_buffer_address(&output) {
            Ok(pointer) => pointer,
            Err(_) => return ERR_BUFFER,
        };
        let result = prepare_runtime_read_from_registry(
            handle,
            epoch as u64,
            now_ms as u64,
            timeout_ms as u64,
        );
        let (token, request, expected_response) = match result {
            Ok(value) => value,
            Err(error) => return map_failure(error),
        };
        if request.len() > MAX_REQUEST_BYTES
            || request.is_empty()
            || expected_response > MAX_RESPONSE_BYTES
            || expected_response == 0
            || capacity < REQUEST_HEADER + request.len()
        {
            let _ = disconnect_runtime_candidate_from_registry(handle);
            return ERR_NATIVE;
        }
        let mut encoded = Vec::with_capacity(REQUEST_HEADER + request.len());
        encoded.extend_from_slice(&(expected_response as u32).to_le_bytes());
        encoded.extend_from_slice(&(request.len() as u32).to_le_bytes());
        encoded.extend_from_slice(&request);
        unsafe { std::slice::from_raw_parts_mut(pointer, encoded.len()).copy_from_slice(&encoded) };
        jlong::try_from(token.sequence).unwrap_or(ERR_NATIVE)
    })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_epicefi_epichub_engine_NativeProfileRegistryBridge_finishRuntimeRead(
    env: JNIEnv,
    _object: JObject,
    handle: jlong,
    epoch: jlong,
    sequence: jlong,
    now_ms: jlong,
    response: JByteBuffer,
    response_length: jint,
    output: JByteBuffer,
) -> jint {
    ffi_guard(ERR_PANIC as jint, || {
        if handle <= 0 || epoch <= 0 || sequence <= 0 || now_ms < 0 {
            return ERR_ARGUMENT as jint;
        }
        let len = match usize::try_from(response_length) {
            Ok(len) if len > 0 && len <= MAX_RESPONSE_BYTES => len,
            _ => return ERR_ARGUMENT as jint,
        };
        let response_capacity = match env.get_direct_buffer_capacity(&response) {
            Ok(value) if value >= len => value,
            _ => return ERR_BUFFER as jint,
        };
        let output_capacity = match env.get_direct_buffer_capacity(&output) {
            Ok(value) if value >= MAX_BATCH_BYTES => value,
            _ => return ERR_BUFFER as jint,
        };
        let input_ptr = match env.get_direct_buffer_address(&response) {
            Ok(value) => value,
            Err(_) => return ERR_BUFFER as jint,
        };
        let output_ptr = match env.get_direct_buffer_address(&output) {
            Ok(value) => value,
            Err(_) => return ERR_BUFFER as jint,
        };
        let _ = (response_capacity, output_capacity);
        let response_bytes = unsafe { std::slice::from_raw_parts(input_ptr, len) };
        let (binding, batch) = match finish_runtime_read_from_registry(
            handle,
            epoch as u64,
            sequence as u64,
            now_ms as u64,
            response_bytes,
        ) {
            Ok(result) => result,
            Err(error) => return map_failure(error) as jint,
        };
        let encoded = match encode_batch(&binding, &batch) {
            Some(encoded) => encoded,
            None => return ERR_NATIVE as jint,
        };
        unsafe {
            std::slice::from_raw_parts_mut(output_ptr, encoded.len()).copy_from_slice(&encoded)
        };
        jint::try_from(encoded.len()).unwrap_or(ERR_NATIVE as jint)
    })
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn bridge_sizes_bound_one_full_frame_and_channel_batch() {
        assert_eq!(REQUEST_HEADER, 8);
        assert_eq!(BATCH_HEADER, 56);
        assert!(MAX_BATCH_BYTES < MAX_RESPONSE_BYTES);
        assert_eq!(freshness_code(RuntimeSampleState::Fresh), 1);
        assert_eq!(freshness_code(RuntimeSampleState::Invalid), 3);
    }
}
