# Phase 1 Architecture Review Checklist

Use this checklist before declaring Phase 1 complete.

## Source archaeology

- [ ] EpicDash-JZ major responsibilities inventoried by domain, not merely by file name.
- [ ] Original EpicDash Android behaviour/protocol intent inventoried.
- [ ] Original ESP32 firmware behaviour/protocol/failsafe limits inventoried.
- [ ] EpicEFI firmware transport/signature/runtime/read/write/Burn authorities verified directly where practical.
- [ ] Generated INI/profile authority and representative geometry differences recorded.
- [ ] Relevant UXUI/Tune Viewer/diagnostics/plugin repositories reviewed for reusable behaviours/algorithms.
- [ ] Provenance/licensing recorded for material reference sources/fixtures.

## Responsibility completeness

- [ ] Every known required behaviour is classified Preserve/Redesign/Merge/Split/Reference/Remove.
- [ ] Every retained/redesigned responsibility has one planned owner.
- [ ] Broad historical classes have been decomposed; no major hidden responsibility remains unmapped.
- [ ] New EpicHub-only responsibilities appear in Gap Analysis.

## Topology

- [ ] Kotlin package/file destinations cover Android/platform/Compose/accessory responsibilities.
- [ ] Rust crate/module/file destinations cover ECU/profile/channels/tuning/MDF/logging/analysis/plotting/diagnostics/interoperability.
- [ ] Permanent names are descriptive and contain no development-stage labels.
- [ ] No obvious future catch-all `Manager`/`Utils`/`DataHub` is planned.

## Authority and state

- [ ] ECU USB candidate identity is separate from firmware/profile authority.
- [ ] Exact generated profile/INI authority is unambiguous.
- [ ] One ECU session/transport owner is planned.
- [ ] One runtime ChannelStore authority is planned.
- [ ] One complete verified TuneSnapshot authority is planned.
- [ ] Accessory app state vs physical ESP32 output authority is explicit.
- [ ] Logging/file/analyzer caches cannot become competing source truth.

## Boundary/performance

- [ ] Kotlin/Rust boundary contains no expected per-channel/per-sample JNI ping-pong.
- [ ] runtime snapshot ABI plan is bounded/versioned.
- [ ] plot/analysis queries return viewport-sized results.
- [ ] external Android-source ingestion is batched/coarse.
- [ ] USB runtime-I/O alternatives have a defined benchmark/PoC plan.
- [ ] acquisition/logging/UI/analysis can run independently without unbounded queues.

## Safety

- [ ] UI cannot submit raw ECU memory writes.
- [ ] tune RAM write verification contract is preserved.
- [ ] fail-closed uncertainty/no automatic replay is preserved.
- [ ] Burn remains separate and explicit.
- [ ] ESP32 owns physical output failsafes.
- [ ] automation/custom math cannot bypass semantic action policies.

## Testing

- [ ] pure policies/codecs/parsers have unit/golden-vector plans.
- [ ] generated INI/profile corpus strategy is documented.
- [ ] tune fault-injection sequence is documented.
- [ ] MDF independent interoperability plan is documented.
- [ ] JNI lifecycle/ABI tests are planned.
- [ ] USB physical lifecycle/performance comparison is planned.
- [ ] BLE/ESP32 physical output/failsafe tests are planned.
- [ ] Compose and logging/analyzer performance tests are planned.

## Workflow

- [ ] `docs/HANDOFF.md` is the only active handoff file.
- [ ] architecture docs agree with current repository decisions.
- [ ] unresolved decisions are explicit and have evidence-gathering plans.
- [ ] user accepts Phase 1 result before production scaffold begins.
