use std::panic::{AssertUnwindSafe, catch_unwind};

use jni::JNIEnv;
use jni::objects::{JByteBuffer, JObject};
use jni::sys::{jint, jlong};

use crate::engine_guarded_write_batch::{
    EngineGuardedRamWriteBatchError, EngineGuardedSourceReconciliationError,
};
use crate::guarded_write_batch_store::{
    GuardedWriteBatchReconciliationStoreError, reconcile_guarded_write_batch_verified_prefix,
};
use crate::profile_registry_store::RegistryStoreError;
use crate::runtime_identity_probe::RuntimeProbeError;
use crate::runtime_profile_authority::RuntimeProfileAuthorityError;
use crate::verified_source_reconciliation::VerifiedSourceReconciliationError;

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
const BATCH_INVALID_ARGUMENT: jint = -25;
const BATCH_REGISTRY_FAILURE: jint = -26;
const BATCH_INVALID_VERIFIED_PREFIX: jint = -27;
const BATCH_SOURCE_RECONCILIATION_FAILURE: jint = -28;
const BATCH_PANIC: jint = -255;

fn ffi_guard<T: Copy>(fallback: T, operation: impl FnOnce() -> T) -> T {
    catch_unwind(AssertUnwindSafe(operation)).unwrap_or(fallback)
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

fn map_guarded_batch_error(error: EngineGuardedRamWriteBatchError) -> jint {
    match error {
        EngineGuardedRamWriteBatchError::ProfileAuthority(error) => map_registry_error(error),
        EngineGuardedRamWriteBatchError::InvalidEngineHandle => BATCH_INVALID_ENGINE,
        EngineGuardedRamWriteBatchError::MissingSourceImage => BATCH_MISSING_SOURCE,
        EngineGuardedRamWriteBatchError::NonLiveControllerSource(_) => BATCH_NON_LIVE_SOURCE,
        EngineGuardedRamWriteBatchError::StaleDraftRevision { .. } => BATCH_STALE_REVISION,
        EngineGuardedRamWriteBatchError::StaleProfileGeneration { .. } => BATCH_STALE_PROFILE,
        EngineGuardedRamWriteBatchError::SourceImageChanged => BATCH_SOURCE_CHANGED,
        EngineGuardedRamWriteBatchError::Intent(_)
        | EngineGuardedRamWriteBatchError::Transaction(_) => BATCH_PLAN_FAILURE,
    }
}

fn map_reconciliation_error(error: GuardedWriteBatchReconciliationStoreError) -> jint {
    match error {
        GuardedWriteBatchReconciliationStoreError::InvalidHandle => BATCH_INVALID_HANDLE,
        GuardedWriteBatchReconciliationStoreError::Reconciliation(error) => match error {
            EngineGuardedSourceReconciliationError::GuardedBatch(error) => {
                map_guarded_batch_error(error)
            }
            EngineGuardedSourceReconciliationError::VerifiedSource(
                VerifiedSourceReconciliationError::VerifiedPrefixTooLarge { .. },
            ) => BATCH_INVALID_VERIFIED_PREFIX,
            EngineGuardedSourceReconciliationError::VerifiedSource(_) => {
                BATCH_SOURCE_RECONCILIATION_FAILURE
            }
        },
    }
}

fn direct_bytes(
    env: &JNIEnv,
    buffer: &JByteBuffer,
    length: jint,
    max_length: jint,
) -> Result<Vec<u8>, jint> {
    let length = usize::try_from(length).map_err(|_| BATCH_INVALID_LENGTH)?;
    let max_length = usize::try_from(max_length).map_err(|_| BATCH_INVALID_LIMIT)?;
    if max_length == 0 {
        return Err(BATCH_INVALID_LIMIT);
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

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_epicefi_epichub_engine_NativeGuardedWriteBatchBridge_reconcileVerifiedPrefix(
    env: JNIEnv,
    _object: JObject,
    batch_handle: jlong,
    engine_handle: jlong,
    registry_handle: jlong,
    signature_buffer: JByteBuffer,
    signature_length: jint,
    signature_limit: jint,
    verified_transaction_count: jint,
) -> jint {
    ffi_guard(BATCH_PANIC, || {
        if batch_handle <= 0 || engine_handle <= 0 || registry_handle <= 0 {
            return BATCH_INVALID_HANDLE;
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
            return BATCH_INVALID_LIMIT;
        };
        let Ok(verified_transaction_count) = usize::try_from(verified_transaction_count) else {
            return BATCH_INVALID_ARGUMENT;
        };

        match reconcile_guarded_write_batch_verified_prefix(
            batch_handle,
            engine_handle,
            registry_handle,
            &signature,
            signature_limit,
            verified_transaction_count,
        ) {
            Ok(()) => 0,
            Err(error) => map_reconciliation_error(error),
        }
    })
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn verified_prefix_error_has_reconciliation_specific_status() {
        assert_eq!(
            map_reconciliation_error(
                GuardedWriteBatchReconciliationStoreError::Reconciliation(
                    EngineGuardedSourceReconciliationError::VerifiedSource(
                        VerifiedSourceReconciliationError::VerifiedPrefixTooLarge {
                            verified: 2,
                            available: 1,
                        },
                    ),
                ),
            ),
            BATCH_INVALID_VERIFIED_PREFIX
        );
    }

    #[test]
    fn retained_source_failure_has_reconciliation_specific_status() {
        assert_eq!(
            map_reconciliation_error(
                GuardedWriteBatchReconciliationStoreError::Reconciliation(
                    EngineGuardedSourceReconciliationError::VerifiedSource(
                        VerifiedSourceReconciliationError::EmptyPatch,
                    ),
                ),
            ),
            BATCH_SOURCE_RECONCILIATION_FAILURE
        );
    }
}
