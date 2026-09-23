# EpicHub Initial Android Build Baseline

## Purpose

This document locks the product/build assumptions needed to begin Phase 2 without turning toolchain versions into permanent architecture.

Checked against current Android platform guidance and the known physical EpicHub test-device family on 2026-09-22.

---

# Application identity

Initial permanent Android application ID and Kotlin namespace:

```text
com.epicefi.epichub
```

Rationale:

- EpicHub is a clean-slate application with no package/update continuity requirement from `com.buttonbox.ble.jz`;
- the selected public product name is EpicEFI – EpicHub / EpicHub;
- no current public search collision for the exact application ID was found during Phase 1 review;
- changing `applicationId` after public distribution creates a new Android application identity, so it is better to lock this before production scaffolding.

`namespace` should initially match `applicationId` unless a concrete build reason later requires otherwise.

---

# Android API baseline

Initial baseline:

```text
minSdk      = 26   # Android 8.0+
targetSdk   = 36   # Android 16 behaviour target / current Play submission baseline
compileSdk  = 37   # current Compose/AndroidX compilation requirement baseline
```

## Why minSdk 26

Compose itself can support older Android versions, so API 26 is a deliberate EpicHub product/maintenance baseline rather than a Compose limitation.

It keeps support substantially broader than the current physical test devices while avoiding carrying compatibility complexity for very old Android generations that are not part of the intended in-car hardware matrix.

Raising `minSdk` later is possible but removes installability for older devices; lowering it later requires actual compatibility work. Therefore API 26 is treated as the initial support contract and is tested rather than assumed.

## Why targetSdk 36

As of 2026-09-22, Google Play requires new apps and updates submitted from 2026-08-31 onward to target Android 16 / API 36 or higher for ordinary phone/tablet apps.

Target API is an evolving platform/distribution requirement, not a permanent architecture constant. Phase 2 may raise it after qualification; it must not be silently lowered below current distribution requirements.

## Why compileSdk 37

Current stable Compose 1.12.x adopted API 37 compilation requirements. `compileSdk` is independent from `targetSdk`, so EpicHub can compile against API 37 while deliberately targeting the qualified API-36 behaviour contract.

The exact Compose/AndroidX/AGP versions are pinned in the repository when Phase 2 creates the build; architecture only requires using mutually compatible current stable versions and keeping those pins reproducible.

---

# Native ABI baseline

Production ARM ABIs initially supported:

```text
armeabi-v7a
arm64-v8a
```

Corresponding Rust targets:

```text
armv7-linux-androideabi
aarch64-linux-android
```

Do **not** begin as arm64-only.

## Physical-device reason

The actual EpicHub reference family includes:

- Samsung SM-A137F phone;
- Samsung SM-T500 tablet.

Phase 1 review found stock-firmware evidence that SM-A137F variants may expose a 32-bit Android userspace / `armeabi-v7a` despite a 64-bit-capable SoC. The SM-T500 has evidence exposing `arm64-v8a` and `armeabi-v7a`.

Therefore an arm64-only Rust `.so` would risk making the clean EpicHub app uninstallable on the existing SM-A137F physical test phone.

The first Phase 2 diagnostic build must record the real `Build.SUPPORTED_ABIS` from each physical target and retain it with qualification evidence. Until the 32-bit A13 requirement is deliberately retired, both ARM ABIs remain supported.

## x86/x86_64

No x86 ABI is part of the production-device support contract.

An emulator/test-only x86_64 native build may be added if it materially improves CI/development, but it must not be confused with the production ABI matrix or impose unnecessary release size.

---

# Packaging and application-size rule

Because EpicHub contains Rust native libraries for two ARM ABIs, do not force every direct-install user to carry both copies unnecessarily.

Preferred release packaging:

```text
Google Play / bundle distribution
→ Android App Bundle ABI split

Direct APK distribution
→ ABI-specific APKs:
   EpicHub-armeabi-v7a.apk
   EpicHub-arm64-v8a.apk
```

A universal dual-ABI APK may exist for bench/internal convenience, but it is not the preferred end-user artifact when size matters.

This application packaging decision is separate from the much more important measurement-log file-size requirement in D-015.

---

# Toolchain policy

Phase 2 should pin exact reproducible versions in source control rather than depend on developer-machine defaults.

Current 2026-09-22 reference points include:

- Android Gradle Plugin 9.4 stable family;
- JDK 17 compatibility;
- API 37 compilation support;
- Android NDK r30 as the current LTS NDK;
- both `armeabi-v7a` and `arm64-v8a` remain Android NDK-supported ABIs.

The exact NDK/Rust/cargo-ndk combination is an implementation compatibility decision. It may be pinned to the newest combination that passes both ARM builds and JNI tests; selecting that exact patch version does not require reopening Phase 1 architecture.

---

# Initial physical support matrix

Minimum Phase 2 physical matrix:

```text
SM-A137F
- phone
- 32-bit ARM Android userspace must be assumed until measured otherwise
- portrait + landscape
- USB host/OTG ECU workflow
- BLE/GPS/lifecycle/logger performance

SM-T500
- tablet
- arm64-v8a-capable Android userspace
- portrait + landscape
- USB host/OTG ECU workflow
- BLE/GPS/lifecycle/logger/analyzer performance
```

Additional devices/head units can expand the matrix later without changing the base architecture.

---

# Phase 2 acceptance checks for this baseline

Before declaring the scaffold baseline qualified:

1. both production ARM native libraries build in CI;
2. ABI-specific APK/AAB contents are inspected and contain the intended `.so` only;
3. each physical reference device reports and records `SUPPORTED_ABIS`;
4. both devices install/launch the correct ABI build;
5. JNI engine create/close/recreate smoke tests pass on both;
6. foreground/background and orientation recreation do not duplicate native engine ownership;
7. Compose renders correctly at the physical display sizes/densities;
8. USB/BLE permissions can be exercised under each device's current Android permission model.

## Phase 1 conclusion

The build identity and initial Android support envelope are sufficiently locked to begin scaffolding after Phase 1 acceptance:

> `com.epicefi.epichub`, Android API 26+, current target API 36, compile API 37, and dual ARM `armeabi-v7a` + `arm64-v8a` production support with ABI-split release artifacts.

Future target/compile SDK increases and toolchain patch updates are normal maintenance, not architecture changes.