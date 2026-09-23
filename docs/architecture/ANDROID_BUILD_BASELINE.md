# EpicHub Android Build Baseline

## Purpose

This document locks the supported Android/API/ABI envelope and records the first physically qualified Kotlin/Rust foundation baseline.

The support contract is architecture; exact toolchain patch versions are reproducible implementation pins and may be maintained without reopening the architecture unless support behavior changes.

---

# Application identity

Permanent Android application ID and Kotlin namespace:

```text
com.epicefi.epichub
```

EpicHub is a clean-slate application with no package/update continuity requirement from `com.buttonbox.ble.jz`.

---

# Android API baseline

```text
minSdk      = 26
targetSdk   = 36
compileSdk  = 37
```

`minSdk 26` is a deliberate EpicHub support/maintenance baseline rather than a Compose limitation.

Target/compile SDK values may advance with Android distribution/platform requirements; they must not be silently lowered in a way that breaks the accepted support/distribution contract.

---

# Native ABI baseline

Production ARM ABIs:

```text
armeabi-v7a
arm64-v8a
```

Corresponding Rust targets:

```text
armv7-linux-androideabi
aarch64-linux-android
```

Arm64-only remains rejected for the initial product because the SM-A137F reference phone must remain supported.

No x86 ABI is part of the production-device contract. A test-only x86_64 build may later be added if useful for CI/emulators, but it does not redefine release support.

---

# Accepted foundation toolchain

The first Phase-2 foundation established and CI-qualified a reproducible toolchain including:

- Android Gradle Plugin 9.4.0;
- Gradle 9.6;
- JDK 17;
- Kotlin/Compose compiler 2.4.20;
- Compose BOM 2026.09.00;
- Android API 37 preview platform/build tools required by the selected Compose/AndroidX stack;
- Android NDK r30 / `30.0.16248370`;
- Rust 1.98.1;
- `cargo-ndk` 4.1.2;
- committed Cargo.lock with Rust builds/checks using `--locked`.

Exact pins remain source-controlled build facts. Later compatible maintenance updates are normal Phase-2 work and do not require an architecture decision unless they change API/ABI/device support or runtime ownership.

---

# Packaging and application-size rule

Preferred user packaging remains ABI-split where distribution permits it:

```text
Google Play / bundle distribution
→ Android App Bundle ABI split

Direct APK distribution
→ ABI-specific APKs where practical
```

A universal dual-ABI APK is acceptable for bench/internal qualification.

This APK-size concern is separate from D-015's much larger measurement-log storage requirement.

---

# Physical support matrix

Current reference devices:

```text
SM-A137F
- phone
- portrait + landscape target
- USB host/OTG ECU workflow target
- BLE/GPS/lifecycle/logger performance target

SM-T500
- tablet
- portrait + landscape target
- USB host/OTG ECU workflow target
- BLE/GPS/lifecycle/logger/analyzer performance target
```

Additional devices/head units may expand the matrix later without changing the base architecture.

---

# Foundation qualification status

The first Kotlin/Rust foundation is **qualified**, not pending.

Automated qualification proved:

- Rust format/lint/tests;
- Kotlin/JVM RuntimeSnapshot ABI tests;
- Android assembly;
- Rust build for both production ARM ABIs;
- APK inspection containing both `libepichub_engine.so` variants;
- reproducible locked Rust dependency graph.

Physical execution of the same qualified foundation APK passed on:

- Samsung SM-A137F phone;
- Samsung SM-T500 tablet.

Observed on both: installation succeeds, the app remains running, RuntimeSnapshot sequence advances, synthetic channel values remain valid/active and no native/lifecycle crash was observed during the reported checks.

Future UI/diagnostic/USB/logger qualification extends this baseline; it does not make the already-qualified foundation "unimplemented" again.

---

# Current use of the baseline

The active Phase-2 UI/diagnostics slice must continue to build and run under both production ABIs and on both reference devices.

The next inherited ECU/USB slice must not alter the API/ABI support contract merely to simplify native transport implementation.

D-023 also applies to all new foundation work: Kotlin/Compose + Rust remain the production runtime languages, and serialized document formats are restricted to explicit persistence/export/interoperability boundaries.

## Summary

> EpicHub currently targets `com.epicefi.epichub`, Android API 26+, target API 36, compile API 37, and dual `armeabi-v7a` + `arm64-v8a` production support. The first Kotlin/Rust foundation is already CI- and device-qualified on the SM-A137F and SM-T500.