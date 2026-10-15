use std::panic::{AssertUnwindSafe, catch_unwind};

use jni::JNIEnv;
use jni::objects::{JByteBuffer, JObject};
use jni::sys::{jint, jlong};

use crate::profile_registry_store::{RegistryStoreError, with_selected_compiled_bundle};
use crate::runtime_identity_probe::RuntimeProbeError;
use crate::runtime_profile_authority::RuntimeProfileAuthorityError;
use crate::write_envelope_plan::WriteEnvelopePlanError;
use crate::write_reconciliation_plan::PageBytePatch;
use crate::write_reconciliation_transaction::{
    GuardedRamWriteTransaction, ReconciliationTransactionError,
    plan_guarded_ram_write_transactions, verify_precondition, verify_readback, verify_write_ack,
};

const WRITE_INVALID_HANDLE: jint = -1;
const WRITE_NOT_DIRECT: jint = -2;
const WRITE_INVALID_LENGTH: jint = -3;
const WRITE_INVALID_LIMIT: jint = -4;
const WRITE_EMPTY_SIGNATURE: jint = -5;
const WRITE_INVALID_UTF8: jint = -6;
const WRITE_TOO_LARGE: jint = -7;
const WRITE_UNKNOWN_CONTROLLER: jint = -8;
const WRITE_RECOGNIZED_NO_PROFILE: jint = -9;
const WRITE_MISSING_BUNDLE: jint = -10;
const WRITE_PLAN_ERROR: jint = -11;
const WRITE_ENVELOPE_ERROR: jint = -12;
const WRITE_PRECONDITION_FAILURE: jint = -13;
const WRITE_STALE_PRECONDITION: jint = -14;
const WRITE_ACK_FAILURE: jint = -15;
const WRITE_READBACK_FAILURE: jint = -16;
const WRITE_READBACK_MISMATCH: jint = -17;
const WRITE_OUTPUT_TOO_SMALL: jint = -18;
const WRITE_INVALID_ARGUMENT: jint = -19;
const WRITE_REGISTRY_ERROR: jint = -20;
const WRITE_PANIC: jint = -255;

const STAGE_PRECONDITION: jint = 0;
const STAGE_WRITE: jint = 1;
const STAGE_READBACK: jint = 2;
const MAX_PATCH_BYTES: jint = 65_536;
const MAX_RESPONSE_BYTES: jint = 65_536;

fn ffi_guard<T: Copy>(fallback: T, operation: impl FnOnce() -> T) -> T {
    catch_unwind(AssertUnwindSafe(operation)).unwrap_or(fallback)
}

fn valid_stage(stage: jint) -> bool {
    matches!(stage, STAGE_PRECONDITION | STAGE_WRITE | STAGE_READBACK)
}

fn map_store_error(error: RegistryStoreError) -> jint {
    match error {
        RegistryStoreError::InvalidHandle => WRITE_INVALID_HANDLE,
        RegistryStoreError::MissingCompiledBundle(_) => WRITE_MISSING_BUNDLE,
        RegistryStoreError::Authority(authority) => match authority {
            RuntimeProfileAuthorityError::UnknownController => WRITE_UNKNOWN_CONTROLLER,
            RuntimeProfileAuthorityError::RecognizedControllerWithoutProfile => {
                WRITE_RECOGNIZED_NO_PROFILE
            }
            RuntimeProfileAuthorityError::Identity(identity) => match identity {
                RuntimeProbeError::InvalidResponseLimit => WRITE_INVALID_LIMIT,
                RuntimeProbeError::EmptySignature => WRITE_EMPTY_SIGNATURE,
                RuntimeProbeError::InvalidUtf8 => WRITE_INVALID_UTF8,
                RuntimeProbeError::ResponseTooLarge { .. } => WRITE_TOO_LARGE,
                RuntimeProbeError::EmptyQuery | RuntimeProbeError::Transport(_) => WRITE_PANIC,
            },
        },
        RegistryStoreError::Compile(_)
        | RegistryStoreError::BundleCompile(_)
        | RegistryStoreError::Registry(_) => WRITE_REGISTRY_ERROR,
    }
}

fn map_plan_error(error: ReconciliationTransactionError) -> jint {
    match error {
        ReconciliationTransactionError::WritePlanner(_)
        | ReconciliationTransactionError::ReadPlanner(_)
        | ReconciliationTransactionError::RangeOverflow => WRITE_PLAN_ERROR,
        ReconciliationTransactionError::WriteEnvelope(WriteEnvelopePlanError::Envelope(_))
        | ReconciliationTransactionError::Envelope(_) => WRITE_ENVELOPE_ERROR,
        ReconciliationTransactionError::WriteEnvelope(_) => WRITE_PLAN_ERROR,
        ReconciliationTransactionError::PreconditionStatus(_)
        | ReconciliationTransactionError::PreconditionLengthMismatch { .. } => {
            WRITE_PRECONDITION_FAILURE
        }
        ReconciliationTransactionError::PreconditionMismatch { .. } => WRITE_STALE_PRECONDITION,
        ReconciliationTransactionError::ReadbackStatus(_)
        | ReconciliationTransactionError::ReadbackLengthMismatch { .. } => WRITE_READBACK_FAILURE,
        ReconciliationTransactionError::ReadbackMismatch { .. } => WRITE_READBACK_MISMATCH,
    }
}

fn map_verify_error(stage: jint, error: ReconciliationTransactionError) -> jint {
    match stage {
        STAGE_PRECONDITION => match error {
            ReconciliationTransactionError::PreconditionMismatch { .. } => WRITE_STALE_PRECONDITION,
            ReconciliationTransactionError::Envelope(_)
            | ReconciliationTransactionError::PreconditionStatus(_)
            | ReconciliationTransactionError::PreconditionLengthMismatch { .. } => {
                WRITE_PRECONDITION_FAILURE
            }
            other => map_plan_error(other),
        },
        STAGE_WRITE => match error {
            ReconciliationTransactionError::WriteEnvelope(_) => WRITE_ACK_FAILURE,
            other => map_plan_error(other),
        },
        STAGE_READBACK => match error {
            ReconciliationTransactionError::ReadbackMismatch { .. } => WRITE_READBACK_MISMATCH,
            ReconciliationTransactionError::Envelope(_)
            | ReconciliationTransactionError::ReadbackStatus(_)
            | ReconciliationTransactionError::ReadbackLengthMismatch { .. } => {
                WRITE_READBACK_FAILURE
            }
            other => map_plan_error(other),
        },
        _ => WRITE_INVALID_ARGUMENT,
    }
}

fn direct_bytes(
    env: &JNIEnv,
    buffer: &JByteBuffer,
    length: jint,
    max_length: jint,
    invalid_max_status: jint,
) -> Result<Vec<u8>, jint> {
    let length = usize::try_from(length).map_err(|_| WRITE_INVALID_LENGTH)?;
    let max_length = usize::try_from(max_length).map_err(|_| invalid_max_status)?;
    if max_length == 0 {
        return Err(invalid_max_status);
    }
    if length > max_length {
        return Err(WRITE_TOO_LARGE);
    }
    let capacity = env
        .get_direct_buffer_capacity(buffer)
        .map_err(|_| WRITE_NOT_DIRECT)?;
    if length > capacity {
        return Err(WRITE_INVALID_LENGTH);
    }
    let address = env
        .get_direct_buffer_address(buffer)
        .map_err(|_| WRITE_NOT_DIRECT)?;
    Ok(unsafe { std::slice::from_raw_parts(address, length) }.to_vec())
}

fn output_buffer<'a>(
    env: &JNIEnv,
    buffer: &'a JByteBuffer<'a>,
) -> Result<(&'a mut [u8], usize), jint> {
    let capacity = env
        .get_direct_buffer_capacity(buffer)
        .map_err(|_| WRITE_NOT_DIRECT)?;
    let address = env
        .get_direct_buffer_address(buffer)
        .map_err(|_| WRITE_NOT_DIRECT)?;
    let slice = unsafe { std::slice::from_raw_parts_mut(address, capacity) };
    Ok((slice, capacity))
}

#[allow(clippy::too_many_arguments)]
fn patch_from_buffers(
    env: &JNIEnv,
    page: jint,
    offset: jint,
    before_buffer: &JByteBuffer,
    before_length: jint,
    after_buffer: &JByteBuffer,
    after_length: jint,
) -> Result<PageBytePatch, jint> {
    let page = usize::try_from(page).map_err(|_| WRITE_INVALID_ARGUMENT)?;
    if page == 0 {
        return Err(WRITE_INVALID_ARGUMENT);
    }
    let offset = usize::try_from(offset).map_err(|_| WRITE_INVALID_ARGUMENT)?;
    let before = direct_bytes(
        env,
        before_buffer,
        before_length,
        MAX_PATCH_BYTES,
        WRITE_INVALID_ARGUMENT,
    )?;
    let after = direct_bytes(
        env,
        after_buffer,
        after_length,
        MAX_PATCH_BYTES,
        WRITE_INVALID_ARGUMENT,
    )?;
    if before.is_empty() || before.len() != after.len() {
        return Err(WRITE_INVALID_ARGUMENT);
    }
    Ok(PageBytePatch {
        page,
        offset,
        before,
        after,
    })
}

fn plan_for_index(
    handle: jlong,
    signature: &[u8],
    signature_limit: usize,
    patch: &PageBytePatch,
    plan_index: usize,
) -> Result<GuardedRamWriteTransaction, jint> {
    let transactions =
        with_selected_compiled_bundle(handle, signature, signature_limit, |bundle| {
            plan_guarded_ram_write_transactions(bundle, patch)
        })
        .map_err(map_store_error)?
        .map_err(map_plan_error)?;
    transactions
        .get(plan_index)
        .cloned()
        .ok_or(WRITE_INVALID_ARGUMENT)
}

fn stage_request(
    transaction: &GuardedRamWriteTransaction,
    stage: jint,
) -> Result<(&[u8], usize), jint> {
    match stage {
        STAGE_PRECONDITION => Ok((
            &transaction.precondition_read_request_frame,
            transaction.precondition_expected_response_size,
        )),
        STAGE_WRITE => Ok((
            &transaction.write.request_frame,
            transaction.write.expected_response_size,
        )),
        STAGE_READBACK => Ok((
            &transaction.readback_request_frame,
            transaction.readback_expected_response_size,
        )),
        _ => Err(WRITE_INVALID_ARGUMENT),
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_epicefi_epichub_engine_NativeGuardedRamWriteBridge_guardedWritePlanCount(
    env: JNIEnv,
    _object: JObject,
    handle: jlong,
    signature_buffer: JByteBuffer,
    signature_length: jint,
    signature_limit: jint,
    page: jint,
    offset: jint,
    before_buffer: JByteBuffer,
    before_length: jint,
    after_buffer: JByteBuffer,
    after_length: jint,
) -> jint {
    ffi_guard(WRITE_PANIC, || {
        let signature = match direct_bytes(
            &env,
            &signature_buffer,
            signature_length,
            signature_limit,
            WRITE_INVALID_LIMIT,
        ) {
            Ok(value) => value,
            Err(status) => return status,
        };
        let Ok(signature_limit) = usize::try_from(signature_limit) else {
            return WRITE_INVALID_LIMIT;
        };
        let patch = match patch_from_buffers(
            &env,
            page,
            offset,
            &before_buffer,
            before_length,
            &after_buffer,
            after_length,
        ) {
            Ok(value) => value,
            Err(status) => return status,
        };

        match with_selected_compiled_bundle(handle, &signature, signature_limit, |bundle| {
            plan_guarded_ram_write_transactions(bundle, &patch)
        }) {
            Ok(Ok(plans)) => jint::try_from(plans.len()).unwrap_or(WRITE_PLAN_ERROR),
            Ok(Err(error)) => map_plan_error(error),
            Err(error) => map_store_error(error),
        }
    })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_epicefi_epichub_engine_NativeGuardedRamWriteBridge_fillGuardedWriteStage(
    env: JNIEnv,
    _object: JObject,
    handle: jlong,
    signature_buffer: JByteBuffer,
    signature_length: jint,
    signature_limit: jint,
    page: jint,
    offset: jint,
    before_buffer: JByteBuffer,
    before_length: jint,
    after_buffer: JByteBuffer,
    after_length: jint,
    plan_index: jint,
    stage: jint,
    request_output: JByteBuffer,
) -> jlong {
    ffi_guard(i64::from(WRITE_PANIC), || {
        if !valid_stage(stage) {
            return i64::from(WRITE_INVALID_ARGUMENT);
        }
        let signature = match direct_bytes(
            &env,
            &signature_buffer,
            signature_length,
            signature_limit,
            WRITE_INVALID_LIMIT,
        ) {
            Ok(value) => value,
            Err(status) => return i64::from(status),
        };
        let Ok(signature_limit) = usize::try_from(signature_limit) else {
            return i64::from(WRITE_INVALID_LIMIT);
        };
        let patch = match patch_from_buffers(
            &env,
            page,
            offset,
            &before_buffer,
            before_length,
            &after_buffer,
            after_length,
        ) {
            Ok(value) => value,
            Err(status) => return i64::from(status),
        };
        let Ok(plan_index) = usize::try_from(plan_index) else {
            return i64::from(WRITE_INVALID_ARGUMENT);
        };
        let transaction =
            match plan_for_index(handle, &signature, signature_limit, &patch, plan_index) {
                Ok(value) => value,
                Err(status) => return i64::from(status),
            };
        let (request, expected_response_size) = match stage_request(&transaction, stage) {
            Ok(value) => value,
            Err(status) => return i64::from(status),
        };
        let (output, capacity) = match output_buffer(&env, &request_output) {
            Ok(value) => value,
            Err(status) => return i64::from(status),
        };
        if request.len() > capacity {
            return i64::from(WRITE_OUTPUT_TOO_SMALL);
        }
        output[..request.len()].copy_from_slice(request);

        let Ok(request_length) = u32::try_from(request.len()) else {
            return i64::from(WRITE_PLAN_ERROR);
        };
        let Ok(expected_response_length) = u32::try_from(expected_response_size) else {
            return i64::from(WRITE_PLAN_ERROR);
        };
        ((u64::from(request_length) << 32) | u64::from(expected_response_length)) as jlong
    })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_epicefi_epichub_engine_NativeGuardedRamWriteBridge_verifyGuardedWriteStage(
    env: JNIEnv,
    _object: JObject,
    handle: jlong,
    signature_buffer: JByteBuffer,
    signature_length: jint,
    signature_limit: jint,
    page: jint,
    offset: jint,
    before_buffer: JByteBuffer,
    before_length: jint,
    after_buffer: JByteBuffer,
    after_length: jint,
    plan_index: jint,
    stage: jint,
    response_buffer: JByteBuffer,
    response_length: jint,
) -> jint {
    ffi_guard(WRITE_PANIC, || {
        if !valid_stage(stage) {
            return WRITE_INVALID_ARGUMENT;
        }
        let signature = match direct_bytes(
            &env,
            &signature_buffer,
            signature_length,
            signature_limit,
            WRITE_INVALID_LIMIT,
        ) {
            Ok(value) => value,
            Err(status) => return status,
        };
        let Ok(signature_limit) = usize::try_from(signature_limit) else {
            return WRITE_INVALID_LIMIT;
        };
        let patch = match patch_from_buffers(
            &env,
            page,
            offset,
            &before_buffer,
            before_length,
            &after_buffer,
            after_length,
        ) {
            Ok(value) => value,
            Err(status) => return status,
        };
        let Ok(plan_index) = usize::try_from(plan_index) else {
            return WRITE_INVALID_ARGUMENT;
        };
        let response = match direct_bytes(
            &env,
            &response_buffer,
            response_length,
            MAX_RESPONSE_BYTES,
            WRITE_INVALID_LENGTH,
        ) {
            Ok(value) => value,
            Err(status) => return status,
        };
        let transaction =
            match plan_for_index(handle, &signature, signature_limit, &patch, plan_index) {
                Ok(value) => value,
                Err(status) => return status,
            };

        let result =
            with_selected_compiled_bundle(
                handle,
                &signature,
                signature_limit,
                |bundle| match stage {
                    STAGE_PRECONDITION => verify_precondition(bundle, &transaction, &response),
                    STAGE_WRITE => verify_write_ack(bundle, &transaction, &response),
                    STAGE_READBACK => verify_readback(bundle, &transaction, &response),
                    _ => unreachable!(),
                },
            );
        match result {
            Ok(Ok(())) => 0,
            Ok(Err(error)) => map_verify_error(stage, error),
            Err(error) => map_store_error(error),
        }
    })
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn verification_error_mapping_keeps_safety_categories_distinct() {
        assert_eq!(
            map_verify_error(
                STAGE_PRECONDITION,
                ReconciliationTransactionError::PreconditionMismatch {
                    expected: vec![1],
                    actual: vec![2],
                },
            ),
            WRITE_STALE_PRECONDITION
        );
        assert_eq!(
            map_verify_error(
                STAGE_WRITE,
                ReconciliationTransactionError::WriteEnvelope(
                    WriteEnvelopePlanError::UnexpectedStatus(0x84),
                ),
            ),
            WRITE_ACK_FAILURE
        );
        assert_eq!(
            map_verify_error(
                STAGE_READBACK,
                ReconciliationTransactionError::ReadbackMismatch {
                    expected: vec![3],
                    actual: vec![4],
                },
            ),
            WRITE_READBACK_MISMATCH
        );
    }
}
