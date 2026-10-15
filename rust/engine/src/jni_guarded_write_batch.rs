use std::panic::{AssertUnwindSafe, catch_unwind};

use jni::JNIEnv;
use jni::objects::{JByteBuffer, JObject};
use jni::sys::{JNI_FALSE, JNI_TRUE, jboolean, jint, jlong};

use crate::engine_guarded_write_batch::EngineGuardedRamWriteBatchError;
use crate::guarded_write_batch_store::{
    GuardedWriteBatchStoreError, active_guarded_write_batch_count, close_guarded_write_batch,
    create_selected_engine_guarded_write_batch, guarded_write_batch_is_alive,
    guarded_write_batch_summary, guarded_write_transaction, require_guarded_write_batch_current,
};
use crate::profile_registry_store::{RegistryStoreError, with_selected_compiled_bundle};
use crate::runtime_identity_probe::RuntimeProbeError;
use crate::runtime_profile_authority::RuntimeProfileAuthorityError;
use crate::write_envelope_plan::WriteEnvelopePlanError;
use crate::write_reconciliation_transaction::{
    GuardedRamWriteTransaction, ReconciliationTransactionError, verify_precondition,
    verify_readback, verify_write_ack,
};

const BATCH_INVALID_HANDLE: jint = -1;
const BATCH_NOT_DIRECT: jint = -2;
const BATCH_INVALID_LENGTH: jint = -3;
const BATCH_INVALID_LIMIT: jint = -4;
const BATCH_EMPTY_SIGNATURE: jint = -5;
const BATCH_INVALID_UTF8: jint = -6;
const BATCH_TOO_LARGE: jint = -7;
const BATCH_UNKNOWN_CONTROLLER: jint = -8;
const BATCH_RECOGNIZED_NO_PROFILE: jint = -9;
const BATCH_MISSING_BUNDLE: jint = -10;
const BATCH_INVALID_ENGINE: jint = -11;
const BATCH_MISSING_SOURCE: jint = -12;
const BATCH_NON_LIVE_SOURCE: jint = -13;
const BATCH_STALE_REVISION: jint = -14;
const BATCH_STALE_PROFILE: jint = -15;
const BATCH_SOURCE_CHANGED: jint = -16;
const BATCH_PLAN_FAILURE: jint = -17;
const BATCH_INVALID_TRANSACTION_INDEX: jint = -18;
const BATCH_OUTPUT_TOO_SMALL: jint = -19;
const BATCH_STALE_PRECONDITION: jint = -20;
const BATCH_WRITE_ACK_FAILURE: jint = -21;
const BATCH_READBACK_FAILURE: jint = -22;
const BATCH_READBACK_MISMATCH: jint = -23;
const BATCH_ENVELOPE_FAILURE: jint = -24;
const BATCH_INVALID_ARGUMENT: jint = -25;
const BATCH_REGISTRY_FAILURE: jint = -26;
const BATCH_PANIC: jint = -255;

const STAGE_PRECONDITION: jint = 0;
const STAGE_WRITE: jint = 1;
const STAGE_READBACK: jint = 2;
const MAX_RESPONSE_BYTES: jint = 65_536;

fn ffi_guard<T: Copy>(fallback: T, operation: impl FnOnce() -> T) -> T {
    catch_unwind(AssertUnwindSafe(operation)).unwrap_or(fallback)
}

fn valid_stage(stage: jint) -> bool {
    matches!(stage, STAGE_PRECONDITION | STAGE_WRITE | STAGE_READBACK)
}

fn map_registry_error(error: RegistryStoreError) -> jint {
    match error {
        RegistryStoreError::InvalidHandle => BATCH_INVALID_HANDLE,
        RegistryStoreError::MissingCompiledBundle(_) => BATCH_MISSING_BUNDLE,
        RegistryStoreError::Authority(authority) => match authority {
            RuntimeProfileAuthorityError::UnknownController => BATCH_UNKNOWN_CONTROLLER,
            RuntimeProfileAuthorityError::RecognizedControllerWithoutProfile => {
                BATCH_RECOGNIZED_NO_PROFILE
            }
            RuntimeProfileAuthorityError::Identity(identity) => match identity {
                RuntimeProbeError::InvalidResponseLimit => BATCH_INVALID_LIMIT,
                RuntimeProbeError::EmptySignature => BATCH_EMPTY_SIGNATURE,
                RuntimeProbeError::InvalidUtf8 => BATCH_INVALID_UTF8,
                RuntimeProbeError::ResponseTooLarge { .. } => BATCH_TOO_LARGE,
                RuntimeProbeError::EmptyQuery | RuntimeProbeError::Transport(_) => BATCH_PANIC,
            },
        },
        RegistryStoreError::Compile(_)
        | RegistryStoreError::BundleCompile(_)
        | RegistryStoreError::Registry(_) => BATCH_REGISTRY_FAILURE,
    }
}

fn map_transaction_error(error: ReconciliationTransactionError) -> jint {
    match error {
        ReconciliationTransactionError::PreconditionMismatch { .. } => BATCH_STALE_PRECONDITION,
        ReconciliationTransactionError::ReadbackMismatch { .. } => BATCH_READBACK_MISMATCH,
        ReconciliationTransactionError::ReadbackStatus(_)
        | ReconciliationTransactionError::ReadbackLengthMismatch { .. } => BATCH_READBACK_FAILURE,
        ReconciliationTransactionError::WriteEnvelope(WriteEnvelopePlanError::Envelope(_))
        | ReconciliationTransactionError::Envelope(_) => BATCH_ENVELOPE_FAILURE,
        ReconciliationTransactionError::WriteEnvelope(_) => BATCH_WRITE_ACK_FAILURE,
        ReconciliationTransactionError::PreconditionStatus(_)
        | ReconciliationTransactionError::PreconditionLengthMismatch { .. }
        | ReconciliationTransactionError::WritePlanner(_)
        | ReconciliationTransactionError::ReadPlanner(_)
        | ReconciliationTransactionError::RangeOverflow => BATCH_PLAN_FAILURE,
    }
}

fn map_authority_error(error: EngineGuardedRamWriteBatchError) -> jint {
    match error {
        EngineGuardedRamWriteBatchError::ProfileAuthority(error) => map_registry_error(error),
        EngineGuardedRamWriteBatchError::InvalidEngineHandle => BATCH_INVALID_ENGINE,
        EngineGuardedRamWriteBatchError::MissingSourceImage => BATCH_MISSING_SOURCE,
        EngineGuardedRamWriteBatchError::NonLiveControllerSource(_) => BATCH_NON_LIVE_SOURCE,
        EngineGuardedRamWriteBatchError::StaleDraftRevision { .. } => BATCH_STALE_REVISION,
        EngineGuardedRamWriteBatchError::StaleProfileGeneration { .. } => BATCH_STALE_PROFILE,
        EngineGuardedRamWriteBatchError::SourceImageChanged => BATCH_SOURCE_CHANGED,
        EngineGuardedRamWriteBatchError::Intent(_) => BATCH_PLAN_FAILURE,
        EngineGuardedRamWriteBatchError::Transaction(error) => map_transaction_error(error),
    }
}

fn map_store_error(error: GuardedWriteBatchStoreError) -> jint {
    match error {
        GuardedWriteBatchStoreError::InvalidHandle => BATCH_INVALID_HANDLE,
        GuardedWriteBatchStoreError::InvalidTransactionIndex { .. } => {
            BATCH_INVALID_TRANSACTION_INDEX
        }
        GuardedWriteBatchStoreError::Authority(error) => map_authority_error(error),
    }
}

fn direct_bytes(
    env: &JNIEnv,
    buffer: &JByteBuffer,
    length: jint,
    max_length: jint,
    invalid_max_status: jint,
) -> Result<Vec<u8>, jint> {
    let length = usize::try_from(length).map_err(|_| BATCH_INVALID_LENGTH)?;
    let max_length = usize::try_from(max_length).map_err(|_| invalid_max_status)?;
    if max_length == 0 {
        return Err(invalid_max_status);
    }
    if length > max_length {
        return Err(BATCH_TOO_LARGE);
    }
    let capacity = env
        .get_direct_buffer_capacity(buffer)
        .map_err(|_| BATCH_NOT_DIRECT)?;
    if length > capacity {
        return Err(BATCH_INVALID_LENGTH);
    }
    let address = env
        .get_direct_buffer_address(buffer)
        .map_err(|_| BATCH_NOT_DIRECT)?;
    Ok(unsafe { std::slice::from_raw_parts(address, length) }.to_vec())
}

fn output_buffer<'a>(
    env: &JNIEnv,
    buffer: &'a JByteBuffer<'a>,
) -> Result<(&'a mut [u8], usize), jint> {
    let capacity = env
        .get_direct_buffer_capacity(buffer)
        .map_err(|_| BATCH_NOT_DIRECT)?;
    let address = env
        .get_direct_buffer_address(buffer)
        .map_err(|_| BATCH_NOT_DIRECT)?;
    Ok((
        unsafe { std::slice::from_raw_parts_mut(address, capacity) },
        capacity,
    ))
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
        _ => Err(BATCH_INVALID_ARGUMENT),
    }
}

fn require_current(
    batch_handle: jlong,
    engine_handle: jlong,
    registry_handle: jlong,
    signature: &[u8],
    signature_limit: usize,
) -> Result<(), jint> {
    require_guarded_write_batch_current(
        batch_handle,
        engine_handle,
        registry_handle,
        signature,
        signature_limit,
    )
    .map_err(map_store_error)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_epicefi_epichub_engine_NativeGuardedWriteBatchBridge_create(
    env: JNIEnv,
    _object: JObject,
    engine_handle: jlong,
    registry_handle: jlong,
    signature_buffer: JByteBuffer,
    signature_length: jint,
    signature_limit: jint,
    expected_draft_revision: jlong,
) -> jlong {
    ffi_guard(i64::from(BATCH_PANIC), || {
        if engine_handle <= 0 || registry_handle <= 0 {
            return i64::from(BATCH_INVALID_HANDLE);
        }
        let signature = match direct_bytes(
            &env,
            &signature_buffer,
            signature_length,
            signature_limit,
            BATCH_INVALID_LIMIT,
        ) {
            Ok(value) => value,
            Err(status) => return i64::from(status),
        };
        let Ok(signature_limit) = usize::try_from(signature_limit) else {
            return i64::from(BATCH_INVALID_LIMIT);
        };
        let Ok(expected_draft_revision) = u64::try_from(expected_draft_revision) else {
            return i64::from(BATCH_INVALID_ARGUMENT);
        };

        match create_selected_engine_guarded_write_batch(
            engine_handle,
            registry_handle,
            &signature,
            signature_limit,
            expected_draft_revision,
        ) {
            Ok(handle) => handle,
            Err(error) => i64::from(map_store_error(error)),
        }
    })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_epicefi_epichub_engine_NativeGuardedWriteBatchBridge_close(
    _env: JNIEnv,
    _object: JObject,
    handle: jlong,
) {
    let _ = catch_unwind(AssertUnwindSafe(|| close_guarded_write_batch(handle)));
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_epicefi_epichub_engine_NativeGuardedWriteBatchBridge_isAlive(
    _env: JNIEnv,
    _object: JObject,
    handle: jlong,
) -> jboolean {
    ffi_guard(JNI_FALSE, || {
        if guarded_write_batch_is_alive(handle) {
            JNI_TRUE
        } else {
            JNI_FALSE
        }
    })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_epicefi_epichub_engine_NativeGuardedWriteBatchBridge_activeCount(
    _env: JNIEnv,
    _object: JObject,
) -> jint {
    ffi_guard(BATCH_PANIC, active_guarded_write_batch_count)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_epicefi_epichub_engine_NativeGuardedWriteBatchBridge_profileGeneration(
    _env: JNIEnv,
    _object: JObject,
    handle: jlong,
) -> jlong {
    ffi_guard(i64::from(BATCH_PANIC), || {
        match guarded_write_batch_summary(handle) {
            Ok(summary) => jlong::try_from(summary.profile_generation)
                .unwrap_or(i64::from(BATCH_INVALID_ARGUMENT)),
            Err(error) => i64::from(map_store_error(error)),
        }
    })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_epicefi_epichub_engine_NativeGuardedWriteBatchBridge_draftRevision(
    _env: JNIEnv,
    _object: JObject,
    handle: jlong,
) -> jlong {
    ffi_guard(i64::from(BATCH_PANIC), || {
        match guarded_write_batch_summary(handle) {
            Ok(summary) => jlong::try_from(summary.draft_revision)
                .unwrap_or(i64::from(BATCH_INVALID_ARGUMENT)),
            Err(error) => i64::from(map_store_error(error)),
        }
    })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_epicefi_epichub_engine_NativeGuardedWriteBatchBridge_transactionCount(
    _env: JNIEnv,
    _object: JObject,
    handle: jlong,
) -> jint {
    ffi_guard(BATCH_PANIC, || match guarded_write_batch_summary(handle) {
        Ok(summary) => jint::try_from(summary.transaction_count).unwrap_or(BATCH_PLAN_FAILURE),
        Err(error) => map_store_error(error),
    })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_epicefi_epichub_engine_NativeGuardedWriteBatchBridge_fillStage(
    env: JNIEnv,
    _object: JObject,
    batch_handle: jlong,
    engine_handle: jlong,
    registry_handle: jlong,
    signature_buffer: JByteBuffer,
    signature_length: jint,
    signature_limit: jint,
    transaction_index: jint,
    stage: jint,
    request_output: JByteBuffer,
) -> jlong {
    ffi_guard(i64::from(BATCH_PANIC), || {
        if !valid_stage(stage) {
            return i64::from(BATCH_INVALID_ARGUMENT);
        }
        let signature = match direct_bytes(
            &env,
            &signature_buffer,
            signature_length,
            signature_limit,
            BATCH_INVALID_LIMIT,
        ) {
            Ok(value) => value,
            Err(status) => return i64::from(status),
        };
        let Ok(signature_limit) = usize::try_from(signature_limit) else {
            return i64::from(BATCH_INVALID_LIMIT);
        };
        let Ok(transaction_index) = usize::try_from(transaction_index) else {
            return i64::from(BATCH_INVALID_ARGUMENT);
        };
        if let Err(status) = require_current(
            batch_handle,
            engine_handle,
            registry_handle,
            &signature,
            signature_limit,
        ) {
            return i64::from(status);
        }
        let transaction = match guarded_write_transaction(batch_handle, transaction_index) {
            Ok(value) => value,
            Err(error) => return i64::from(map_store_error(error)),
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
            return i64::from(BATCH_OUTPUT_TOO_SMALL);
        }
        output[..request.len()].copy_from_slice(request);

        let Ok(request_length) = u32::try_from(request.len()) else {
            return i64::from(BATCH_PLAN_FAILURE);
        };
        let Ok(expected_response_length) = u32::try_from(expected_response_size) else {
            return i64::from(BATCH_PLAN_FAILURE);
        };
        ((u64::from(request_length) << 32) | u64::from(expected_response_length)) as jlong
    })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_epicefi_epichub_engine_NativeGuardedWriteBatchBridge_verifyStage(
    env: JNIEnv,
    _object: JObject,
    batch_handle: jlong,
    engine_handle: jlong,
    registry_handle: jlong,
    signature_buffer: JByteBuffer,
    signature_length: jint,
    signature_limit: jint,
    transaction_index: jint,
    stage: jint,
    response_buffer: JByteBuffer,
    response_length: jint,
) -> jint {
    ffi_guard(BATCH_PANIC, || {
        if !valid_stage(stage) {
            return BATCH_INVALID_ARGUMENT;
        }
        let signature = match direct_bytes(
            &env,
            &signature_buffer,
            signature_length,
            signature_limit,
            BATCH_INVALID_LIMIT,
        ) {
            Ok(value) => value,
            Err(status) => return status,
        };
        let Ok(signature_limit) = usize::try_from(signature_limit) else {
            return BATCH_INVALID_LIMIT;
        };
        let Ok(transaction_index) = usize::try_from(transaction_index) else {
            return BATCH_INVALID_ARGUMENT;
        };
        let response = match direct_bytes(
            &env,
            &response_buffer,
            response_length,
            MAX_RESPONSE_BYTES,
            BATCH_INVALID_LENGTH,
        ) {
            Ok(value) => value,
            Err(status) => return status,
        };
        if let Err(status) = require_current(
            batch_handle,
            engine_handle,
            registry_handle,
            &signature,
            signature_limit,
        ) {
            return status;
        }
        let transaction = match guarded_write_transaction(batch_handle, transaction_index) {
            Ok(value) => value,
            Err(error) => return map_store_error(error),
        };
        match with_selected_compiled_bundle(
            registry_handle,
            &signature,
            signature_limit,
            |bundle| match stage {
                STAGE_PRECONDITION => verify_precondition(bundle, &transaction, &response),
                STAGE_WRITE => verify_write_ack(bundle, &transaction, &response),
                STAGE_READBACK => verify_readback(bundle, &transaction, &response),
                _ => unreachable!(),
            },
        ) {
            Ok(Ok(())) => 0,
            Ok(Err(error)) => map_transaction_error(error),
            Err(error) => map_registry_error(error),
        }
    })
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn batch_status_mapping_preserves_stale_categories() {
        assert_eq!(
            map_authority_error(EngineGuardedRamWriteBatchError::StaleDraftRevision {
                expected: 1,
                actual: 2,
            }),
            BATCH_STALE_REVISION
        );
        assert_eq!(
            map_authority_error(EngineGuardedRamWriteBatchError::StaleProfileGeneration {
                expected: 7,
                actual: 8,
            }),
            BATCH_STALE_PROFILE
        );
        assert_eq!(
            map_authority_error(EngineGuardedRamWriteBatchError::SourceImageChanged),
            BATCH_SOURCE_CHANGED
        );
    }

    #[test]
    fn verification_status_mapping_preserves_ram_safety_failures() {
        assert_eq!(
            map_transaction_error(ReconciliationTransactionError::PreconditionMismatch {
                expected: vec![1],
                actual: vec![2],
            }),
            BATCH_STALE_PRECONDITION
        );
        assert_eq!(
            map_transaction_error(ReconciliationTransactionError::ReadbackMismatch {
                expected: vec![3],
                actual: vec![4],
            }),
            BATCH_READBACK_MISMATCH
        );
    }
}
