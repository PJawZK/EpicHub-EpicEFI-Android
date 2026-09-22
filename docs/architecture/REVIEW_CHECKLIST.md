# Phase 1 Architecture Review Checklist

Use this checklist before declaring Phase 1 complete.

Current assessment: **45 / 46 items satisfied**. Architecture/source/test review is complete enough for explicit user acceptance. Production scaffolding remains blocked only by the final acceptance item below.

See `PHASE1_GATE_ASSESSMENT.md` for rationale and the distinction between accepted architecture, Phase 2 measured PoCs, feature-entry prerequisites and deliberately deferred product decisions.

## Source archaeology

- [x] EpicDash-JZ major responsibilities inventoried by domain, not merely by file name.
- [x] Original EpicDash Android behaviour/protocol intent inventoried.
- [x] Original ESP32 firmware behaviour/protocol/failsafe limits inventoried.
- [x] EpicEFI firmware transport/signature/runtime/read/write/Burn authorities verified directly where practical.
- [x] Generated INI/profile authority and representative geometry differences recorded.
- [x] Relevant UXUI/Tune Viewer/diagnostics/plugin repositories reviewed for reusable behaviours/algorithms.
- [x] Provenance/licensing recorded for material reference sources/fixtures.

## Responsibility completeness

- [x] Every known required behaviour is classified Preserve/Redesign/Merge/Split/Reference/Remove.
- [x] Every retained/redesigned responsibility has one planned owner.
- [x] Broad historical classes have been decomposed; no major hidden responsibility remains unmapped.
- [x] New EpicHub-only responsibilities appear in Gap Analysis or its explicit supplemental responsibility/gate documents.

## Topology

- [x] Kotlin package/file destinations cover Android/platform/Compose/accessory responsibilities.
- [x] Rust crate/module/file destinations cover ECU/profile/channels/tuning/MDF/logging/analysis/plotting/diagnostics/interoperability.
- [x] Permanent names are descriptive and contain no development-stage labels.
- [x] No obvious future catch-all `Manager`/`Utils`/`DataHub` is planned.

## Authority and state

- [x] ECU USB candidate identity is separate from firmware/profile authority.
- [x] Exact generated profile/INI authority is unambiguous.
- [x] One ECU session/transport owner is planned.
- [x] One runtime ChannelStore authority is planned.
- [x] One complete verified TuneSnapshot authority is planned.
- [x] Accessory app state vs physical ESP32 output authority is explicit.
- [x] Logging/file/analyzer caches cannot become competing source truth.

## Boundary/performance

- [x] Kotlin/Rust boundary contains no expected per-channel/per-sample JNI ping-pong.
- [x] runtime snapshot ABI plan is bounded/versioned.
- [x] plot/analysis queries return viewport-sized results.
- [x] external Android-source ingestion is batched/coarse.
- [x] USB runtime-I/O alternatives have a defined benchmark/PoC plan.
- [x] acquisition/logging/UI/analysis can run independently without unbounded queues.

## Safety

- [x] UI cannot submit raw ECU memory writes.
- [x] tune RAM write verification contract is preserved.
- [x] fail-closed uncertainty/no automatic replay is preserved.
- [x] Burn remains separate and explicit.
- [x] ESP32 owns physical output failsafes for any future direct-output capability.
- [x] automation/custom math cannot bypass semantic action policies.

## Testing

- [x] pure policies/codecs/parsers have unit/golden-vector plans.
- [x] generated INI/profile corpus strategy is documented.
- [x] tune fault-injection sequence is documented.
- [x] MDF independent interoperability plan is documented.
- [x] JNI lifecycle/ABI tests are planned.
- [x] USB physical lifecycle/performance comparison is planned.
- [x] BLE/ESP32 physical output/failsafe tests are planned where the corresponding capability exists/is introduced.
- [x] Compose and logging/analyzer performance tests are planned.

## Workflow

- [x] `docs/HANDOFF.md` is the only active handoff file.
- [x] architecture docs agree with current repository decisions after the Phase 1 gate reconciliation batch.
- [x] unresolved decisions are explicit and have evidence-gathering plans.
- [ ] user accepts Phase 1 result before production scaffold begins.

## Closure rule

Do not mark the final item or begin production Android/Rust scaffolding unless the user explicitly accepts the Phase 1 result.

When accepted:

1. mark the final item;
2. record Phase 1 acceptance in `PHASE1_PROGRESS.md` / `docs/HANDOFF.md`;
3. merge/clean PR #1 as deliberately directed;
4. begin the small non-mutating Phase 2 foundation slice described in `PHASE1_GATE_ASSESSMENT.md`, not a broad feature rewrite.