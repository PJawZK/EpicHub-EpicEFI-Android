use std::panic::{AssertUnwindSafe, catch_unwind};

use jni::JNIEnv;
use jni::objects::JObject;
use jni::sys::jlong;

use crate::engine::tune_session_epoch;

const INVALID_TUNE_SESSION_EPOCH: jlong = -1;

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_epicefi_epichub_engine_NativeEngineBridge_tuneSessionEpoch(
    _env: JNIEnv,
    _object: JObject,
    handle: jlong,
) -> jlong {
    catch_unwind(AssertUnwindSafe(|| {
        tune_session_epoch(handle)
            .and_then(|epoch| i64::try_from(epoch).ok())
            .filter(|epoch| *epoch > 0)
            .unwrap_or(INVALID_TUNE_SESSION_EPOCH)
    }))
    .unwrap_or(INVALID_TUNE_SESSION_EPOCH)
}
