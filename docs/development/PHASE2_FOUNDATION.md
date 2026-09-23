# Phase 2 Foundation Bootstrap

## Status

Phase 1 was accepted and closed on 2026-09-23. This document tracks the first deliberately non-mutating production slice.

## Pinned initial toolchain

Validated against current upstream release guidance on 2026-09-23:

```text
Android Gradle Plugin      9.4.0
Gradle                     9.6.0
JDK                        17
Kotlin / Compose compiler  2.4.20
Compose BOM                2026.09.00
Activity Compose           1.12.1
compileSdk                 37
targetSdk                  36
minSdk                     26
Android 17 platform pkg    android-37.0 (Cinnamon Bun preview)
Android Build Tools        37.0.0
Android NDK                30.0.16248370 (r30 LTS)
Rust                       1.98.1
cargo-ndk                  4.1.2
Production Android ABIs    armeabi-v7a + arm64-v8a
```

Android 17 is still distributed as a preview SDK at this point. Its repository platform identifier is `android-37.0` even though Gradle intentionally uses `compileSdk = 37`. CI therefore installs the exact preview package and explicitly verifies `platforms/android-37.0/android.jar` before attempting the Android build.

AGP 9.x built-in Kotlin is used; `org.jetbrains.kotlin.android` is intentionally not applied. The Kotlin Gradle runtime is explicitly raised to 2.4.20 so the Kotlin compiler and `org.jetbrains.kotlin.plugin.compose` remain on the same release.

The toolchain is a build implementation choice, not a permanent architecture decision. Any update must be intentional, pinned and requalified as one complete set.

## First scaffold scope

This slice contains only:

- one Android `app` module;
- one Compose host Activity/surface;
- one Rust `cdylib`/`rlib` engine crate;
- JNI `NativeEngine` create/close/isAlive/activeCount lifecycle proof;
- dual-ARM Android native build wiring through pinned `cargo-ndk`;
- Rust unit tests for repeated/idempotent handle lifecycle;
- CI for Rust format/lint/tests plus Android debug assembly.

It intentionally does **not** contain:

- ECU transport/protocol;
- ECU mutation or Burn;
- RuntimeSnapshot;
- ChannelStore;
- MLG/MDF;
- Tuner migration;
- BLE/accessory bridge;
- analyzer/autotune work.

## Native lifecycle rule

The first JNI state uses generated integer handles stored in a Rust-owned registry rather than exposing raw pointer values to Kotlin. `close` removes registry state and is idempotent for zero/unknown handles. All current JNI exports catch Rust panics before the FFI boundary and return fail-closed fallback values.

This is deliberately small enough to qualify before snapshot/shared-memory complexity is introduced.

## Acceptance sequence

1. CI must pass Rust format/lint/unit tests and Android debug assembly.
2. Confirm the APK contains both `armeabi-v7a` and `arm64-v8a` copies of `libepichub_engine.so`.
3. Install on SM-A137F and SM-T500; record `Build.SUPPORTED_ABIS`.
4. Repeatedly create/destroy Activity/process sessions and confirm native handle count returns to baseline without crash/leak.
5. Only then add the versioned packed RuntimeSnapshot ABI and minimal ChannelStore/synthetic channels.

If CI exposes incompatibility in this exact toolchain/build wiring, correct the scaffold itself before adding features.