use std::panic::{AssertUnwindSafe, catch_unwind};

use jni::JNIEnv;
use jni::objects::{JByteBuffer, JObject};
use jni::sys::{jint, jlong};

use crate::ms_envelope_codec::EnvelopeCodecError;
use crate::profile_registry_store::{RegistryStoreError, with_selected_compiled_bundle};
use crate::readonly_page_acquisition::{
    ANDROID_READ_ONLY_RESPONSE_LIMIT_BYTES, FramedPageReadPlan, ReadOnlyPageAcquisitionError,
    plan_page_acquisition, verify_page_read_response,
};
use crate::runtime_identity_probe::RuntimeProbeError;
use crate::runtime_profile_authority::RuntimeProfileAuthorityError;

const ACQ_INVALID_HANDLE: jint = -1;
const ACQ_NOT_DIRECT: jint = -2;
const ACQ_INVALID_LENGTH: jint = -3;
const ACQ_INVALID_LIMIT: jint = -4;
const ACQ_EMPTY_SIGNATURE: jint = -5;
const ACQ_INVALID_UTF8: jint = -6;
const ACQ_TOO_LARGE: jint = -7;
const ACQ_UNKNOWN_CONTROLLER: jint = -8;
const ACQ_RECOGNIZED_NO_PROFILE: jint = -9;
const ACQ_MISSING_BUNDLE: jint = -10;
const ACQ_PLAN_ERROR: jint = -11;
const ACQ_ENVELOPE_ERROR: jint = -12;
const ACQ_NON_SUCCESS_STATUS: jint = -13;
const ACQ_PAYLOAD_MISMATCH: jint = -14;
const ACQ_OUTPUT_TOO_SMALL: jint = -15;
const ACQ_INVALID_ARGUMENT: jint = -16;
const ACQ_REGISTRY_ERROR: jint = -17;
const ACQ_PANIC: jint = -255;

fn ffi_guard<T: Copy>(fallback: T, operation: impl FnOnce() -> T) -> T {
    catch_unwind(AssertUnwindSafe(operation)).unwrap_or(fallback)
}

fn map_store_error(error: RegistryStoreError) -> jint {
    match error {
        RegistryStoreError::InvalidHandle => ACQ_INVALID_HANDLE,
        RegistryStoreError::MissingCompiledBundle(_) => ACQ_MISSING_BUNDLE,
        RegistryStoreError::Authority(authority) => match authority {
            RuntimeProfileAuthorityError::UnknownController => ACQ_UNKNOWN_CONTROLLER,
            RuntimeProfileAuthorityError::RecognizedControllerWithoutProfile => {
                ACQ_RECOGNIZED_NO_PROFILE
            }
            RuntimeProfileAuthorityError::Identity(identity) => match identity {
                RuntimeProbeError::InvalidResponseLimit => ACQ_INVALID_LIMIT,
                RuntimeProbeError::EmptySignature => ACQ_EMPTY_SIGNATURE,
                RuntimeProbeError::InvalidUtf8 => ACQ_INVALID_UTF8,
                RuntimeProbeError::ResponseTooLarge { .. } => ACQ_TOO_LARGE,
                RuntimeProbeError::EmptyQuery | RuntimeProbeError::Transport(_) => ACQ_PANIC,
            },
        },
        RegistryStoreError::Compile(_)
        | RegistryStoreError::BundleCompile(_)
        | RegistryStoreError::Registry(_) => ACQ_REGISTRY_ERROR,
    }
}

fn map_acquisition_error(error: ReadOnlyPageAcquisitionError) -> jint {
    match error {
        ReadOnlyPageAcquisitionError::InvalidResponseFrameLimit(_)
        | ReadOnlyPageAcquisitionError::Planner(_)
        | ReadOnlyPageAcquisitionError::ChunkPageMismatch { .. }
        | ReadOnlyPageAcquisitionError::ChunkOffsetMismatch { .. }
        | ReadOnlyPageAcquisitionError::PageIncomplete { .. } => ACQ_PLAN_ERROR,
        ReadOnlyPageAcquisitionError::Envelope(EnvelopeCodecError::UnsupportedEnvelope(_)) => {
            ACQ_PLAN_ERROR
        }
        ReadOnlyPageAcquisitionError::Envelope(_) => ACQ_ENVELOPE_ERROR,
        ReadOnlyPageAcquisitionError::NonSuccessStatus(_) => ACQ_NON_SUCCESS_STATUS,
        ReadOnlyPageAcquisitionError::PayloadLengthMismatch { .. } => ACQ_PAYLOAD_MISMATCH,
    }
}

fn direct_bytes(
    env: &JNIEnv,
    buffer: &JByteBuffer,
    length: jint,
    max_length: jint,
) -> Result<Vec<u8>, jint> {
    let length = usize::try_from(length).map_err(|_| ACQ_INVALID_LENGTH)?;
    let max_length = usize::try_from(max_length).map_err(|_| ACQ_INVALID_LIMIT)?;
    if max_length == 0 {
        return Err(ACQ_INVALID_LIMIT);
    }
    if length > max_length {
        return Err(ACQ_TOO_LARGE);
    }
    let capacity = env
        .get_direct_buffer_capacity(buffer)
        .map_err(|_| ACQ_NOT_DIRECT)?;
    if length > capacity {
        return Err(ACQ_INVALID_LENGTH);
    }
    let address = env
        .get_direct_buffer_address(buffer)
        .map_err(|_| ACQ_NOT_DIRECT)?;
    Ok(unsafe { std::slice::from_raw_parts(address, length) }.to_vec())
}

fn output_buffer<'a>(
    env: &JNIEnv,
    buffer: &'a JByteBuffer<'a>,
) -> Result<(&'a mut [u8], usize), jint> {
    let capacity = env
        .get_direct_buffer_capacity(buffer)
        .map_err(|_| ACQ_NOT_DIRECT)?;
    let address = env
        .get_direct_buffer_address(buffer)
        .map_err(|_| ACQ_NOT_DIRECT)?;
    let slice = unsafe { std::slice::from_raw_parts_mut(address, capacity) };
    Ok((slice, capacity))
}

fn plan_for_index(
    handle: jlong,
    signature: &[u8],
    signature_limit: usize,
    page_index: usize,
    max_response_frame_bytes: usize,
    plan_index: usize,
) -> Result<FramedPageReadPlan, jint> {
    let plans = with_selected_compiled_bundle(handle, signature, signature_limit, |bundle| {
        plan_page_acquisition(bundle, page_index, max_response_frame_bytes)
    })
    .map_err(map_store_error)?
    .map_err(map_acquisition_error)?;
    plans
        .get(plan_index)
        .cloned()
        .ok_or(ACQ_INVALID_ARGUMENT)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_epicefi_epichub_engine_NativeReadOnlyPageBridge_pageReadPlanCount(
    env: JNIEnv,
    _object: JObject,
    handle: jlong,
    signature_buffer: JByteBuffer,
    signature_length: jint,
    signature_limit: jint,
    page_index: jint,
    max_response_frame_bytes: jint,
) -> jint {
    ffi_guard(ACQ_PANIC, || {
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
            return ACQ_INVALID_LIMIT;
        };
        let Ok(page_index) = usize::try_from(page_index) else {
            return ACQ_INVALID_ARGUMENT;
        };
        let Ok(max_response_frame_bytes) = usize::try_from(max_response_frame_bytes) else {
            return ACQ_INVALID_LIMIT;
        };
        if max_response_frame_bytes == 0
            || max_response_frame_bytes > ANDROID_READ_ONLY_RESPONSE_LIMIT_BYTES
        {
            return ACQ_INVALID_LIMIT;
        }

        match with_selected_compiled_bundle(handle, &signature, signature_limit, |bundle| {
            plan_page_acquisition(bundle, page_index, max_response_frame_bytes)
        }) {
            Ok(Ok(plans)) => jint::try_from(plans.len()).unwrap_or(ACQ_PLAN_ERROR),
            Ok(Err(error)) => map_acquisition_error(error),
            Err(error) => map_store_error(error),
        }
    })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_epicefi_epichub_engine_NativeReadOnlyPageBridge_fillPageReadPlan(
    env: JNIEnv,
    _object: JObject,
    handle: jlong,
    signature_buffer: JByteBuffer,
    signature_length: jint,
    signature_limit: jint,
    page_index: jint,
    max_response_frame_bytes: jint,
    plan_index: jint,
    request_output: JByteBuffer,
) -> jlong {
    ffi_guard(i64::from(ACQ_PANIC), || {
        let signature = match direct_bytes(
            &env,
            &signature_buffer,
            signature_length,
            signature_limit,
        ) {
            Ok(value) => value,
            Err(status) => return i64::from(status),
        };
        let Ok(signature_limit) = usize::try_from(signature_limit) else {
            return i64::from(ACQ_INVALID_LIMIT);
        };
        let Ok(page_index) = usize::try_from(page_index) else {
            return i64::from(ACQ_INVALID_ARGUMENT);
        };
        let Ok(plan_index) = usize::try_from(plan_index) else {
            return i64::from(ACQ_INVALID_ARGUMENT);
        };
        let Ok(max_response_frame_bytes) = usize::try_from(max_response_frame_bytes) else {
            return i64::from(ACQ_INVALID_LIMIT);
        };
        if max_response_frame_bytes == 0
            || max_response_frame_bytes > ANDROID_READ_ONLY_RESPONSE_LIMIT_BYTES
        {
            return i64::from(ACQ_INVALID_LIMIT);
        }

        let plan = match plan_for_index(
            handle,
            &signature,
            signature_limit,
            page_index,
            max_response_frame_bytes,
            plan_index,
        ) {
            Ok(plan) => plan,
            Err(status) => return i64::from(status),
        };
        let (output, capacity) = match output_buffer(&env, &request_output) {
            Ok(value) => value,
            Err(status) => return i64::from(status),
        };
        if plan.request_frame.len() > capacity {
            return i64::from(ACQ_OUTPUT_TOO_SMALL);
        }
        output[..plan.request_frame.len()].copy_from_slice(&plan.request_frame);

        let Ok(request_length) = u32::try_from(plan.request_frame.len()) else {
            return i64::from(ACQ_PLAN_ERROR);
        };
        let Ok(expected_response_length) = u32::try_from(plan.expected_response_frame_bytes) else {
            return i64::from(ACQ_PLAN_ERROR);
        };
        ((u64::from(request_length) << 32) | u64::from(expected_response_length)) as jlong
    })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_epicefi_epichub_engine_NativeReadOnlyPageBridge_verifyPageReadResponse(
    env: JNIEnv,
    _object: JObject,
    handle: jlong,
    signature_buffer: JByteBuffer,
    signature_length: jint,
    signature_limit: jint,
    page_index: jint,
    max_response_frame_bytes: jint,
    plan_index: jint,
    response_buffer: JByteBuffer,
    response_length: jint,
    payload_output: JByteBuffer,
) -> jint {
    ffi_guard(ACQ_PANIC, || {
        let signature = match direct_bytes(
            &env,
            &signature_buffer,
            signature_length,
            signature_limit,
        ) {
            Ok(value) => value,
            Err(status) => return status,
        };
        let response = match direct_bytes(
            &env,
            &response_buffer,
            response_length,
            max_response_frame_bytes,
        ) {
            Ok(value) => value,
            Err(status) => return status,
        };
        let Ok(signature_limit) = usize::try_from(signature_limit) else {
            return ACQ_INVALID_LIMIT;
        };
        let Ok(page_index) = usize::try_from(page_index) else {
            return ACQ_INVALID_ARGUMENT;
        };
        let Ok(plan_index) = usize::try_from(plan_index) else {
            return ACQ_INVALID_ARGUMENT;
        };
        let Ok(max_response_frame_bytes) = usize::try_from(max_response_frame_bytes) else {
            return ACQ_INVALID_LIMIT;
        };
        if max_response_frame_bytes == 0
            || max_response_frame_bytes > ANDROID_READ_ONLY_RESPONSE_LIMIT_BYTES
        {
            return ACQ_INVALID_LIMIT;
        }

        let plan = match plan_for_index(
            handle,
            &signature,
            signature_limit,
            page_index,
            max_response_frame_bytes,
            plan_index,
        ) {
            Ok(plan) => plan,
            Err(status) => return status,
        };
        if response.len() != plan.expected_response_frame_bytes {
            return ACQ_INVALID_LENGTH;
        }
        let verified = match with_selected_compiled_bundle(
            handle,
            &signature,
            signature_limit,
            |bundle| verify_page_read_response(bundle, &plan, &response),
        ) {
            Ok(Ok(chunk)) => chunk,
            Ok(Err(error)) => return map_acquisition_error(error),
            Err(error) => return map_store_error(error),
        };

        let (output, capacity) = match output_buffer(&env, &payload_output) {
            Ok(value) => value,
            Err(status) => return status,
        };
        if verified.payload.len() > capacity {
            return ACQ_OUTPUT_TOO_SMALL;
        }
        output[..verified.payload.len()].copy_from_slice(&verified.payload);
        jint::try_from(verified.payload.len()).unwrap_or(ACQ_PLAN_ERROR)
    })
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::ecu_profile_registry::{ProfileRegistryError, ProfileSelectionError};

    #[test]
    fn store_error_mapping_keeps_missing_bundle_distinct() {
        assert_eq!(
            map_store_error(RegistryStoreError::InvalidHandle),
            ACQ_INVALID_HANDLE
        );
        assert_eq!(
            map_store_error(RegistryStoreError::MissingCompiledBundle("sig".to_string())),
            ACQ_MISSING_BUNDLE
        );
        assert_eq!(
            map_store_error(RegistryStoreError::Registry(
                ProfileRegistryError::DuplicateProfileSignature("sig".to_string())
            )),
            ACQ_REGISTRY_ERROR
        );
        let _ = ProfileSelectionError::UnknownController;
    }

    #[test]
    fn acquisition_error_mapping_preserves_wire_failures() {
        assert_eq!(
            map_acquisition_error(ReadOnlyPageAcquisitionError::NonSuccessStatus(0x84)),
            ACQ_NON_SUCCESS_STATUS
        );
        assert_eq!(
            map_acquisition_error(ReadOnlyPageAcquisitionError::PayloadLengthMismatch {
                expected: 4,
                actual: 3,
            }),
            ACQ_PAYLOAD_MISMATCH
        );
        assert_eq!(
            map_acquisition_error(ReadOnlyPageAcquisitionError::Envelope(
                EnvelopeCodecError::CrcMismatch {
                    expected: 1,
                    actual: 2,
                }
            )),
            ACQ_ENVELOPE_ERROR
        );
    }
}
