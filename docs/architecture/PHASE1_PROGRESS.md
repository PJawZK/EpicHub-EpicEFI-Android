# Phase 1 Progress

## Status

Phase 1 is active on `phase1/architecture-inventory`.

The repository-backed architecture foundation has been established. Production Android/Rust scaffolding has not started.

## Initial completed work

- clean-slate development workflow and handoff rules;
- durable architecture decision log;
- permanent naming rules;
- initial cross-repository source inventory;
- initial normalized responsibility inventory;
- initial protocol-authority map;
- gap analysis;
- detailed provisional codebase topology;
- initial responsibility-to-destination mapping;
- dependency/state/data/threading/FFI/action/safety contracts;
- test strategy and Phase 1 review checklist.

## Source evidence already sampled

- EpicDash-JZ current project/architecture/tuning/ECU-recognition evidence and repository tree;
- original `AKCore/EpicEFIVirtualDash` Android/ESP32 topology and BLE/CAN protocol behaviour;
- direct EpicEFI firmware fork USB descriptor, generated signature and TunerStudio protocol source.

## Next inventory tranche

Primary next target: EpicDash-JZ ECU/profile/runtime path.

- decompose `UsbEcuManager.kt` fully by responsibility;
- inventory `UsbTunerStudioProfile.kt` and associated generated-INI compilation/condition logic;
- verify firmware framing/read/write/Burn source paths;
- refine destinations/topology based on concrete findings.

Subsequent tranches:

- ESP32/accessory firmware and BLE lifecycle/protocol completeness;
- tune project persistence/recovery;
- dashboard/editor/tuner interaction inventory;
- logging/playback/interoperability;
- diagnostics/performance;
- UXUI/Tune Viewer and tuning-plugin future features.
