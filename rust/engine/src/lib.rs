use std::collections::HashSet;
use std::ffi::c_void;
use std::panic::{AssertUnwindSafe, catch_unwind};
use std::sync::atomic::{AtomicI64, Ordering};
use std::sync::{LazyLock, Mutex};

static NEXT_HANDLE: AtomicI64 = AtomicI64::new(1);
static ACTIVE_ENGINES: LazyLock<Mutex<HashSet<i64>>> = LazyLock::new(|| Mutex::new(HashSet::new()));

fn with_active_engines<T>(operation: impl FnOnce(&mut HashSet<i64>) -> T) -> T {
    let mut guard = ACTIVE_ENGINES
        .lock()
        .unwrap_or_else(|poisoned| poisoned.into_inner());
    operation(&mut guard)
}

fn create_engine() -> i64 {
    let handle = NEXT_HANDLE.fetch_add(1, Ordering::Relaxed);
    if handle <= 0 {
        return 0;
    }
    with_active_engines(|engines| {
        engines.insert(handle);
    });
    handle
}

fn close_engine(handle: i64) {
    if handle == 0 {
        return;
    }
    with_active_engines(|engines| {
        engines.remove(&handle);
    });
}

fn engine_is_alive(handle: i64) -> bool {
    handle != 0 && with_active_engines(|engines| engines.contains(&handle))
}

fn active_engine_count() -> i32 {
    with_active_engines(|engines| i32::try_from(engines.len()).unwrap_or(i32::MAX))
}

fn ffi_guard<T>(fallback: T, operation: impl FnOnce() -> T) -> T {
    catch_unwind(AssertUnwindSafe(operation)).unwrap_or(fallback)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_epicefi_epichub_engine_NativeEngineBridge_create(
    _env: *mut c_void,
    _object: *mut c_void,
) -> i64 {
    ffi_guard(0, create_engine)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_epicefi_epichub_engine_NativeEngineBridge_close(
    _env: *mut c_void,
    _object: *mut c_void,
    handle: i64,
) {
    ffi_guard((), || close_engine(handle));
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_epicefi_epichub_engine_NativeEngineBridge_isAlive(
    _env: *mut c_void,
    _object: *mut c_void,
    handle: i64,
) -> u8 {
    ffi_guard(0, || u8::from(engine_is_alive(handle)))
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_epicefi_epichub_engine_NativeEngineBridge_activeCount(
    _env: *mut c_void,
    _object: *mut c_void,
) -> i32 {
    ffi_guard(0, active_engine_count)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn repeated_create_close_returns_to_baseline() {
        let baseline = active_engine_count();
        for _ in 0..1_000 {
            let handle = create_engine();
            assert_ne!(handle, 0);
            assert!(engine_is_alive(handle));
            close_engine(handle);
            assert!(!engine_is_alive(handle));
        }
        assert_eq!(active_engine_count(), baseline);
    }

    #[test]
    fn close_is_idempotent_for_unknown_or_zero_handle() {
        let baseline = active_engine_count();
        close_engine(0);
        close_engine(i64::MAX);
        assert_eq!(active_engine_count(), baseline);
    }
}
