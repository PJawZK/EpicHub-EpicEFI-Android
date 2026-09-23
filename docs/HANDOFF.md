# EpicHub Current Handoff

## Continuation instruction

Read this file first, verify the current `main` head and any active Phase 2 branch/PR, then follow the exact next action.

Repository: `PJawZK/EpicHub-EpicEFI-Android`

Do not reconstruct current architecture from EpicDash-JZ or other historical repositories when EpicHub documentation establishes a newer deliberate contract.

---

# Repository state

- integration branch: `main`
- Phase 1 PR #1: merged
- Phase 1 accepted/closed: **2026-09-23**
- Phase 2 foundation PR #2: **merged**
- foundation squash-merge commit: `e2c80f8f45272ec3ff1db740d5c553e9a0d0da32`
- qualified foundation content tree: `e918a7568ce46f263a06aed4d4c410cc547bca14`
- qualified foundation push run: **Foundation quality #36 / 35826507558 — PASS**

Always verify the actual current `main` head before starting new work. The merge SHA above is the accepted foundation milestone, not a promise that it remains the latest commit after later documentation updates.

---

# Accepted Phase 2 foundation

The following clean-slate foundation is now accepted on `main`:

- `com.epicefi.epichub` Android application namespace;
- Kotlin + Jetpack Compose application host;
- pinned Gradle/JDK/Android SDK/NDK/Kotlin/Compose/Rust/cargo-ndk toolchain;
- production native ABIs `armeabi-v7a` + `arm64-v8a`;
- coarse JNI `NativeEngine` lifecycle;
- fixed/versioned 96-byte little-endian RuntimeSnapshot ABI v1;
- reusable direct ByteBuffer snapshot transfer;
- engine-owned minimal Rust ChannelStore;
- reserved synthetic test ChannelIds;
- low-rate synthetic Rust → RuntimeSnapshot → Kotlin → Compose path;
- Rust panic containment at JNI boundary;
- serialized snapshot-read/close lifecycle;
- committed `Cargo.lock` and `--locked` Rust checks/builds;
- CI APK verification for both production native libraries.

No ECU transport, ECU mutation, Burn, BLE/accessory migration, logger/MDF4 implementation, full Tuner, or analyzer/autotune functionality is part of this accepted foundation.

---

# Qualification evidence

Automated qualification on the accepted content tree passed:

- rustfmt;
- Clippy with warnings-as-errors;
- Rust unit tests;
- Kotlin/JVM RuntimeSnapshot ABI tests;
- Android debug assembly;
- Rust build for both ARM ABIs;
- APK inspection proving both `libepichub_engine.so` ABIs;
- APK artifact publication.

Physical qualification of the exact qualified APK passed on both reference devices:

- Samsung **SM-T500** tablet;
- Samsung **SM-A137F** phone.

Observed on both: installation succeeds, app remains running, RuntimeSnapshot sequence advances, synthetic channel values remain valid/active, and no native/lifecycle crash was observed during the reported checks.

---

# Mandatory implementation methodology

D-022 / `docs/architecture/SOURCE_REIMPLEMENTATION_METHOD.md` remains mandatory for every inherited subsystem:

```text
semantic behavior slice
→ trace relevant old implementation/callers/state/lifecycle/errors/tests/bug history
→ classify required/safety/compatibility/useful/accidental/buggy/obsolete/unknown behavior
→ design a clean permanent owner/contract
→ implement from scratch
→ qualify against protocol truth/proven behavior/focused fixtures
→ regression-test old failure modes
→ benchmark hot paths
→ accept the narrow slice
```

Never bulk-copy an EpicDash-JZ subsystem and clean it later.

---

# Next Phase 2 slice

Proceed to the **read-only live ECU acquisition path**. This is the first inherited/high-risk subsystem and therefore begins with a D-022 source-understanding gate before implementation.

Required review scope before writing the new transport:

1. EpicDash-JZ Android USB discovery/permission/open/CDC endpoint selection;
2. EpicDash-JZ read-only ECU session/synchronization/signature/runtime-output path;
3. callers, lifecycle/reconnect/generation/cancellation/error handling and diagnostics around that path;
4. known historical USB/session bugs and their regression tests;
5. current EpicEFI/rusEFI firmware framing/query/runtime-output authority;
6. exact generated-INI runtime-channel geometry ownership;
7. separation between transport candidate identity (`0483:5740`) and exact firmware/profile authority.

The review must classify each observed old behavior under D-022 and explicitly identify junk/dead/accidental behavior that will not be recreated.

---

# Exact next action

1. create a new Phase 2 branch from current accepted `main` for the live acquisition path;
2. perform the focused source-understanding audit above and commit its evidence/design contract first;
3. design the smallest read-only live path from Android USB ownership into the existing Rust ChannelStore/RuntimeSnapshot foundation;
4. keep ECU writes, Burn, logger formats, BLE/accessory and analyzer work out of this slice;
5. only after the source-understanding gate is accepted, implement and benchmark the live path alternatives already defined in Phase 1.
