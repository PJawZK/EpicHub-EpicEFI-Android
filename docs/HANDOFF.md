# EpicHub Current Handoff

## Continuation instruction

Read this file first, verify the actual branch/PR head, then follow the exact next action.

Repository: `PJawZK/EpicHub-EpicEFI-Android`

Do not reconstruct current architecture from EpicDash-JZ or other historical repositories when EpicHub documentation establishes a newer deliberate contract.

---

# Repository state

- integration branch: `main`
- Phase 1 PR: **#1 — Phase 1: architecture and responsibility inventory — merged**
- Phase 1 merge commit: `d98c5f8a6e3dcad0b479323e12d0e845d6d3fefb`
- Phase 1 review result: **46 / 46 accepted**
- Phase 1 accepted/closed: **2026-09-23**
- production Android/Rust implementation had not started before acceptance
- next development branch: `phase2/foundation-bootstrap`

Always verify the current branch/PR head when resuming. The Phase 1 merge SHA above is a historical accepted-baseline marker, not a claim that it remains the current `main` head after later documentation or implementation commits.

---

# Current phase

**Phase 2 — foundation bootstrap.**

Phase 1 architecture is now the accepted baseline. Do not reopen broad source archaeology without a concrete evidence gap. Historical-source review now happens narrowly at each inherited subsystem's implementation-entry gate under D-022.

Primary authority/read order:

1. `docs/architecture/README.md`
2. `docs/architecture/DECISIONS.md`
3. `docs/architecture/PHASE1_GATE_ASSESSMENT.md`
4. `docs/architecture/GAP_ANALYSIS.md`
5. `docs/architecture/SOURCE_REIMPLEMENTATION_METHOD.md`
6. `docs/architecture/ANDROID_BUILD_BASELINE.md`
7. domain contract/evidence docs when their subsystem enters implementation

---

# Mandatory implementation methodology

D-022 / `docs/architecture/SOURCE_REIMPLEMENTATION_METHOD.md` remains mandatory:

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

Never bulk-copy an EpicDash-JZ/plugin subsystem and clean it later.
