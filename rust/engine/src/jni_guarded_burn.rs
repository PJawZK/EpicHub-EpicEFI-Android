use std::panic::{AssertUnwindSafe, catch_unwind};

use jni::JNIEnv;
use jni::objects::{JByteBuffer, JObject};
use jni::sys::{JNI_FALSE, JNI_TRUE, jboolean, jint, jlong};

use crate::engine_guarded_burn::EngineGuardedPageBurnError;
use crate::guarded_burn_plan_store::{
    GuardedBurnPlanStoreError, GuardedBurnStage, active_guarded_burn_plan_count,
    close_guarded_burn_plan, create_selected_engine_guarded_burn_plan, guarded_burn_plan_is_alive,
    guarded_burn_plan_summary, stage_guarded_burn_plan, verify_guarded_burn_ack,
    verify_guarded_burn_crc,
};
use crate::guarded_burn_transport::GuardedPageBurnTransportError;
use crate::profile_registry_store::RegistryStoreError;
use crate::runtime_identity_probe::RuntimeProbeError;
use crate::runtime_profile_authority::RuntimeProfileAuthorityError;

const BURN_INVALID_HANDLE: jint = -1;
const BURN_NOT_DIRECT: jint = -2;
const BURN_INVALID_LENGTH: jint = -3;
const BURN_INVALID_LIMIT: jint = -4;
const BURN_EMPTY_SIGNATURE: jint = -5;
const BURN_INVALID_UTF8: jint = -6;
const BURN_TOO_LARGE: jint = -7;
const BURN_UNKNOWN_CONTROLLER: jint = -8;
const BURN_RECOGNIZED_NO_PROFILE: jint = -9;
const BURN_MISSING_BUNDLE: jint = -10;
const BURN_INVALID_ENGINE: jint = -11;
const BURN_MISSING_SOURCE: jint = -12;
const BURN_NON_LIVE_SOURCE: jint = -13;
const BURN_RAM_NOT_CURRENT: jint = -14;
const BURN_STALE_PROFILE: jint = -15;
const BURN_SOURCE_CHANGED: jint = -16;
const BURN_PLAN_FAILURE: jint = -17;
const BURN_OUTPUT_TOO_SMALL: jint = -18;
const BURN_ACK_FAILURE: jint = -19;
const BURN_CRC_FAILURE: jint = -20;
const BURN_INVALID_ARGUMENT: jint = -21;
const BURN_REGISTRY_FAILURE: jint = -22;
const BURN_INVALID_PROOF_STAGE: jint = -23;
const BURN_ENGINE_STATE_CHANGED: jint = -24;
const BURN_PANIC: jint = -255;

const STAGE_BURN: jint = 0;
const STAGE_CRC: jint = 1;
const MAX_RESPONSE_BYTES: jint = 65_536;

fn ffi_guard<T: Copy>(fallback: T, operation: impl FnOnce() -> T) -> T {
    catch_unwind(AssertUnwindSafe(operation)).unwrap_or(fallback)
}

fn valid_stage(stage: jint) -> bool {
    matches!(stage, STAGE_BURN | STAGE_CRC)
}

fn map_registry_error(error: RegistryStoreError) -> jint {
    match error {
        RegistryStoreError::InvalidHandle => BURN_INVALID_HANDLE,
        RegistryStoreError::MissingCompiledBundle(_) => BURN_MISSING_BUNDLE,
        RegistryStoreError::Authority(authority) => match authority {
            RuntimeProfileAuthorityError::UnknownController => BURN_UNKNOWN_CONTROLLER,
            RuntimeProfileAuthorityError::RecognizedControllerWithoutProfile => {
                BURN_RECOGNIZED_NO_PROFILE
            }
            RuntimeProfileAuthorityError::Identity(identity) => match identity {
                RuntimeProbeError::InvalidResponseLimit => BURN_INVALID_LIMIT,
                RuntimeProbeError::EmptySignature => BURN_EMPTY_SIGNATURE,
                RuntimeProbeError::InvalidUtf8 => BURN_INVALID_UTF8,
                RuntimeProbeError::ResponseTooLarge { .. } => BURN_TOO_LARGE,
                RuntimeProbeError::EmptyQuery | RuntimeProbeError::Transport(_) => BURN_PANIC,
            },
        },
        RegistryStoreError::Compile(_)
        | RegistryStoreError::BundleCompile(_)
        | RegistryStoreError::Registry(_) => BURN_REGISTRY_FAILURE,
    }
}

fn map_authority_error(error: EngineGuardedPageBurnError) -> jint {
    match error {
        EngineGuardedPageBurnError::ProfileAuthority(error) => map_registry_error(error),
        EngineGuardedPageBurnError::InvalidEngineHandle => BURN_INVALID_ENGINE,
        EngineGuardedPageBurnError::MissingSourceImage => BURN_MISSING_SOURCE,
        EngineGuardedPageBurnError::NonLiveControllerSource(_) => BURN_NON_LIVE_SOURCE,
        EngineGuardedPageBurnError::StaleProfileGeneration { .. }
        | EngineGuardedPageBurnError::RamProvenanceGenerationMismatch { .. } => BURN_STALE_PROFILE,
        EngineGuardedPageBurnError::RamNotConfirmedForCurrentDraft { .. } => BURN_RAM_NOT_CURRENT,
        EngineGuardedPageBurnError::SourceImageChanged => BURN_SOURCE_CHANGED,
        EngineGuardedPageBurnError::StaleSessionEpoch { .. } => BURN_SOURCE_CHANGED,
        EngineGuardedPageBurnError::MissingSourcePage { .. }
        | EngineGuardedPageBurnError::SourcePageSizeMismatch { .. }
        | EngineGuardedPageBurnError::BurnPlan(_)
        | EngineGuardedPageBurnError::CrcPlan(_)
        | EngineGuardedPageBurnError::CrcEnvelope(_) => BURN_PLAN_FAILURE,
    }
}

fn map_transport_error(error: GuardedPageBurnTransportError) -> jint {
    match error {
        GuardedPageBurnTransportError::Authority(error) => map_authority_error(error),
        GuardedPageBurnTransportError::ProfileAuthority(error) => map_registry_error(error),
        GuardedPageBurnTransportError::BurnEnvelope(_) => BURN_ACK_FAILURE,
        GuardedPageBurnTransportError::CrcEnvelope(_) => BURN_CRC_FAILURE,
    }
}

fn map_store_error(error: GuardedBurnPlanStoreError) -> jint {
    match error {
        GuardedBurnPlanStoreError::InvalidHandle => BURN_INVALID_HANDLE,
        GuardedBurnPlanStoreError::InvalidProofStage => BURN_INVALID_PROOF_STAGE,
        GuardedBurnPlanStoreError::EngineStateChanged => BURN_ENGINE_STATE_CHANGED,
        GuardedBurnPlanStoreError::Authority(error) => map_authority_error(error),
        GuardedBurnPlanStoreError::Transport(error) => map_transport_error(error),
    }
}

fn direct_bytes(
    env: &JNIEnv,
    buffer: &JByteBuffer,
    length: jint,
    max_length: jint,
    invalid_max_status: jint,
) -> Result<Vec<u8>, jint> {
    let length = usize::try_from(length).map_err(|_| BURN_INVALID_LENGTH)?;
    let max_length = usize::try_from(max_length).map_err(|_| invalid_max_status)?;
    if max_length == 0 {
        return Err(invalid_max_status);
    }
    if length > max_length {
        return Err(BURN_TOO_LARGE);
    }
    let capacity = env
        .get_direct_buffer_capacity(buffer)
        .map_err(|_| BURN_NOT_DIRECT)?;
    if length > capacity {
        return Err(BURN_INVALID_LENGTH);
    }
    let address = env
        .get_direct_buffer_address(buffer)
        .map_err(|_| BURN_NOT_DIRECT)?;
    Ok(unsafe { std::slice::from_raw_parts(address, length) }.to_vec())
}

fn output_buffer<'a>(
    env: &JNIEnv,
    buffer: &'a JByteBuffer<'a>,
) -> Result<(&'a mut [u8], usize), jint> {
    let capacity = env
        .get_direct_buffer_capacity(buffer)
        .map_err(|_| BURN_NOT_DIRECT)?;
    let address = env
        .get_direct_buffer_address(buffer)
        .map_err(|_| BURN_NOT_DIRECT)?;
    Ok((
        unsafe { std::slice::from_raw_parts_mut(address, capacity) },
        capacity,
    ))
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_epicefi_epichub_engine_NativeGuardedBurnBridge_create(
    env: JNIEnv,
    _object: JObject,
    engine_handle: jlong,
    registry_handle: jlong,
    signature_buffer: JByteBuffer,
    signature_length: jint,
    signature_limit: jint,
    page: jint,
) -> jlong {
    ffi_guard(i64::from(BURN_PANIC), || {
        if engine_handle <= 0 || registry_handle <= 0 {
            return i64::from(BURN_INVALID_HANDLE);
        }
        let signature = match direct_bytes(
            &env,
            &signature_buffer,
            signature_length,
            signature_limit,
            BURN_INVALID_LIMIT,
        ) {
            Ok(value) => value,
            Err(status) => return i64::from(status),
        };
        let Ok(signature_limit) = usize::try_from(signature_limit) else {
            return i64::from(BURN_INVALID_LIMIT);
        };
        let Ok(page) = usize::try_from(page) else {
            return i64::from(BURN_INVALID_ARGUMENT);
        };
        if page == 0 {
            return i64::from(BURN_INVALID_ARGUMENT);
        }

        match create_selected_engine_guarded_burn_plan(
            engine_handle,
            registry_handle,
            &signature,
            signature_limit,
            page,
        ) {
            Ok(handle) => handle,
            Err(error) => i64::from(map_store_error(error)),
        }
    })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_epicefi_epichub_engine_NativeGuardedBurnBridge_close(
    _env: JNIEnv,
    _object: JObject,
    handle: jlong,
) {
    let _ = catch_unwind(AssertUnwindSafe(|| close_guarded_burn_plan(handle)));
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_epicefi_epichub_engine_NativeGuardedBurnBridge_isAlive(
    _env: JNIEnv,
    _object: JObject,
    handle: jlong,
) -> jboolean {
    ffi_guard(JNI_FALSE, || {
        if guarded_burn_plan_is_alive(handle) {
            JNI_TRUE
        } else {
            JNI_FALSE
        }
    })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_epicefi_epichub_engine_NativeGuardedBurnBridge_activeCount(
    _env: JNIEnv,
    _object: JObject,
) -> jint {
    ffi_guard(BURN_PANIC, active_guarded_burn_plan_count)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_epicefi_epichub_engine_NativeGuardedBurnBridge_profileGeneration(
    _env: JNIEnv,
    _object: JObject,
    handle: jlong,
) -> jlong {
    ffi_guard(i64::from(BURN_PANIC), || {
        match guarded_burn_plan_summary(handle) {
            Ok(summary) => jlong::try_from(summary.profile_generation)
                .unwrap_or(i64::from(BURN_INVALID_ARGUMENT)),
            Err(error) => i64::from(map_store_error(error)),
        }
    })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_epicefi_epichub_engine_NativeGuardedBurnBridge_draftRevision(
    _env: JNIEnv,
    _object: JObject,
    handle: jlong,
) -> jlong {
    ffi_guard(i64::from(BURN_PANIC), || {
        match guarded_burn_plan_summary(handle) {
            Ok(summary) => {
                jlong::try_from(summary.draft_revision).unwrap_or(i64::from(BURN_INVALID_ARGUMENT))
            }
            Err(error) => i64::from(map_store_error(error)),
        }
    })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_epicefi_epichub_engine_NativeGuardedBurnBridge_page(
    _env: JNIEnv,
    _object: JObject,
    handle: jlong,
) -> jint {
    ffi_guard(BURN_PANIC, || match guarded_burn_plan_summary(handle) {
        Ok(summary) => jint::try_from(summary.page).unwrap_or(BURN_PLAN_FAILURE),
        Err(error) => map_store_error(error),
    })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_epicefi_epichub_engine_NativeGuardedBurnBridge_fillStage(
    env: JNIEnv,
    _object: JObject,
    plan_handle: jlong,
    engine_handle: jlong,
    registry_handle: jlong,
    signature_buffer: JByteBuffer,
    signature_length: jint,
    signature_limit: jint,
    stage: jint,
    request_output: JByteBuffer,
) -> jlong {
    ffi_guard(i64::from(BURN_PANIC), || {
        if !valid_stage(stage) {
            return i64::from(BURN_INVALID_ARGUMENT);
        }
        let signature = match direct_bytes(
            &env,
            &signature_buffer,
            signature_length,
            signature_limit,
            BURN_INVALID_LIMIT,
        ) {
            Ok(value) => value,
            Err(status) => return i64::from(status),
        };
        let Ok(signature_limit) = usize::try_from(signature_limit) else {
            return i64::from(BURN_INVALID_LIMIT);
        };
        let staged = match stage_guarded_burn_plan(
            plan_handle,
            engine_handle,
            registry_handle,
            &signature,
            signature_limit,
            if stage == STAGE_BURN {
                GuardedBurnStage::Burn
            } else {
                GuardedBurnStage::Crc
            },
        ) {
            Ok(value) => value,
            Err(error) => return i64::from(map_store_error(error)),
        };
        let (request, expected_response_size) = match stage {
            STAGE_BURN => (
                &staged.burn.request_frame,
                staged.burn.expected_response_size,
            ),
            STAGE_CRC => (
                &staged.crc_check.request_frame,
                staged.crc_check.expected_response_size,
            ),
            _ => unreachable!(),
        };
        let (output, capacity) = match output_buffer(&env, &request_output) {
            Ok(value) => value,
            Err(status) => return i64::from(status),
        };
        if request.len() > capacity {
            return i64::from(BURN_OUTPUT_TOO_SMALL);
        }
        output[..request.len()].copy_from_slice(request);

        let Ok(request_length) = u32::try_from(request.len()) else {
            return i64::from(BURN_PLAN_FAILURE);
        };
        let Ok(expected_response_length) = u32::try_from(expected_response_size) else {
            return i64::from(BURN_PLAN_FAILURE);
        };
        ((u64::from(request_length) << 32) | u64::from(expected_response_length)) as jlong
    })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_epicefi_epichub_engine_NativeGuardedBurnBridge_verifyStage(
    env: JNIEnv,
    _object: JObject,
    plan_handle: jlong,
    engine_handle: jlong,
    registry_handle: jlong,
    signature_buffer: JByteBuffer,
    signature_length: jint,
    signature_limit: jint,
    stage: jint,
    response_buffer: JByteBuffer,
    response_length: jint,
) -> jint {
    ffi_guard(BURN_PANIC, || {
        if !valid_stage(stage) {
            return BURN_INVALID_ARGUMENT;
        }
        let signature = match direct_bytes(
            &env,
            &signature_buffer,
            signature_length,
            signature_limit,
            BURN_INVALID_LIMIT,
        ) {
            Ok(value) => value,
            Err(status) => return status,
        };
        let Ok(signature_limit) = usize::try_from(signature_limit) else {
            return BURN_INVALID_LIMIT;
        };
        let response = match direct_bytes(
            &env,
            &response_buffer,
            response_length,
            MAX_RESPONSE_BYTES,
            BURN_INVALID_LENGTH,
        ) {
            Ok(value) => value,
            Err(status) => return status,
        };

        let result = match stage {
            STAGE_BURN => verify_guarded_burn_ack(
                plan_handle,
                engine_handle,
                registry_handle,
                &signature,
                signature_limit,
                &response,
            ),
            STAGE_CRC => verify_guarded_burn_crc(
                plan_handle,
                engine_handle,
                registry_handle,
                &signature,
                signature_limit,
                &response,
            ),
            _ => unreachable!(),
        };
        match result {
            Ok(()) => 0,
            Err(error) => map_store_error(error),
        }
    })
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn burn_status_mapping_preserves_ram_and_profile_staleness() {
        assert_eq!(
            map_authority_error(EngineGuardedPageBurnError::RamNotConfirmedForCurrentDraft {
                draft_revision: 2,
                confirmed_revision: Some(1),
            }),
            BURN_RAM_NOT_CURRENT
        );
        assert_eq!(
            map_authority_error(EngineGuardedPageBurnError::StaleProfileGeneration {
                expected: 7,
                actual: 8,
            }),
            BURN_STALE_PROFILE
        );
        assert_eq!(
            map_authority_error(EngineGuardedPageBurnError::SourceImageChanged),
            BURN_SOURCE_CHANGED
        );
    }
}
