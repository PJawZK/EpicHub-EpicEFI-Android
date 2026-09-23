# Phase 1 Architecture Review Checklist

Use this checklist before declaring Phase 1 complete.

Current assessment after the dedicated documentation-consistency audit: **45 / 46 items satisfied**. Production scaffolding remains blocked only by explicit user acceptance.

See `PHASE1_GATE_ASSESSMENT.md` for the reconciliation findings and the distinction between accepted architecture, Phase 2 measured PoCs, feature-entry prerequisites and deliberately deferred product decisions.

## Source archaeology

- [x] EpicDash-JZ major responsibilities inventoried by domain, not merely by file name; D-022 requires deeper piece-by-piece source/bug review at each subsystem's implementation-entry gate.
- [x] Original EpicDash Android behaviour/protocol intent inventoried.
- [x] Original ESP32 firmware behaviour/protocol/failsafe **limits** inventoried; legacy watchdog/liveness is not misrepresented as direct-output failsafe behavior.
- [x] EpicEFI firmware transport/signature/runtime/read/write/Burn authorities verified directly where practical.
- [x] Generated INI/profile authority and representative geometry differences recorded.
- [x] Relevant UXUI/Tune Viewer/diagnostics/plugin repositories reviewed for reusable behaviours/algorithms.
- [x] Provenance/licensing recorded for material reference sources/fixtures.

## Responsibility completeness

- [x] Every known required responsibility is classified Preserve/Redesign/Merge/Split/Reference/Remove; individual historical behaviors are additionally classified under D-022 before implementation.
- [x] Every retained/redesigned responsibility has one planned owner.
- [x] Broad historical classes have been decomposed; no major hidden responsibility remains unmapped.
- [x] New EpicHub-only responsibilities appear in Gap Analysis or explicit supplemental responsibility/gate documents.

## Topology

- [x] Kotlin package/file destinations cover Android/platform/Compose/bridge/settings responsibilities and keep any future direct-output capability separate.
- [x] Rust destination domains cover ECU/profile/channels/tuning/measurement/logging/MLG-MDF formats/analysis/plotting/diagnostics/interoperability without making MDF a prerequisite for logging.
- [x] Permanent names are descriptive and contain no development-stage labels.
- [x] No obvious future catch-all `Manager`/`Utils`/`DataHub` is planned.

## Authority and state

- [x] ECU USB candidate identity is separate from firmware/profile authority.
- [x] Exact live signature compatibility and exact selected profile-source/fingerprint authority are unambiguous.
- [x] One ECU session/transport-protocol owner is planned regardless of which sustained-I/O PoC wins.
- [x] One runtime ChannelStore authority is planned.
- [x] One complete verified TuneSnapshot authority is planned.
- [x] Legacy bridge transport/request state vs any future physical ESP32 output authority is explicit.
- [x] Logging/file/analyzer caches and semantic tune drafts cannot become competing verified source truth.

## Boundary/performance

- [x] Kotlin/Rust boundary contains no high-rate ECU per-channel/per-sample JNI ping-pong; low-rate discrete Android-source exceptions are explicitly bounded and do not redefine the streaming model.
- [x] runtime snapshot ABI plan is bounded/versioned.
- [x] plot/analysis queries return viewport-sized results.
- [x] external Android-source ingestion is coarse; burst/high-rate sources are batched.
- [x] USB runtime-I/O alternatives have a defined three-path benchmark/PoC plan.
- [x] acquisition/logging/UI/analysis can run independently without unbounded queues.

## Safety

- [x] UI cannot submit raw ECU memory writes.
- [x] tune RAM write verification contract is preserved.
- [x] fail-closed uncertainty/no automatic replay is preserved.
- [x] Burn remains separate and explicit.
- [x] ESP32 owns physical output mapping/interlocks/failsafes only for a deliberately introduced direct-output capability; no such authority is invented for the legacy bridge.
- [x] warning evaluation/diagnostics/custom math cannot bypass semantic ActionPolicy/tuning safety; any warning-driven automation is a separate explicit authority path.

## Testing

- [x] pure policies/codecs/parsers have unit/golden-vector plans plus D-022 regression coverage for known historical failure modes.
- [x] generated INI/profile corpus strategy is documented.
- [x] tune fault-injection sequence is documented.
- [x] MLG ecosystem interoperability and later independent MDF interoperability plans are documented in the locked implementation order.
- [x] JNI lifecycle/ABI tests are planned.
- [x] USB physical lifecycle/performance comparison is planned.
- [x] legacy BLE/ESP32 bridge tests are separated from future direct-output physical/failsafe tests.
- [x] Compose and logging/analyzer performance/replay tests are planned.

## Workflow

- [x] `docs/HANDOFF.md` is the only active handoff file.
- [x] architecture docs agree with current repository decisions after a dedicated literal/cross-contract consistency audit and reconciliation.
- [x] unresolved decisions are explicit, classified by when they become required, and have evidence-gathering plans.
- [ ] user accepts Phase 1 result before production scaffold begins.

## Closure rule

Do not mark the final item or begin production Android/Rust scaffolding unless the user explicitly accepts the reconciled Phase 1 result.

When accepted:

1. mark the final item;
2. record Phase 1 acceptance in `PHASE1_PROGRESS.md` / `docs/HANDOFF.md`;
3. merge/clean PR #1 as deliberately directed;
4. begin the small non-mutating Phase 2 foundation slice described in `PHASE1_GATE_ASSESSMENT.md`, not a broad feature rewrite;
5. apply `SOURCE_REIMPLEMENTATION_METHOD.md` whenever inherited behavior enters substantive implementation.