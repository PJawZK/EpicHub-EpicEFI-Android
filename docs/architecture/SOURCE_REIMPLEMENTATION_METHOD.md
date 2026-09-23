# EpicHub Source Reimplementation Method

## Purpose

EpicHub is a clean-slate application. Historical repositories exist to explain requirements, protocol reality, useful algorithms, prior failures and already-qualified behavior. They are **not** implementation templates.

The implementation methodology is therefore:

> Understand the old behavior piece-by-piece, classify what is actually required, identify accidental/junk/buggy behavior, design the clean responsibility and contract, implement it from scratch in EpicHub, then prove required parity with focused tests and measurements.

This rule applies to the entire project: Android platform integration, ECU transport/protocol, generated-profile parsing, runtime channels, tuning, BLE/accessory integration, logging, measurement formats, analyzer/autotune, diagnostics and UI.

---

# 1. What the Phase 1 source review has and has not done

Phase 1 has primarily performed **responsibility archaeology**:

- repository documentation and architecture review;
- targeted inspection of high-value implementation paths;
- tests and qualification evidence;
- firmware/protocol source verification;
- known large/problematic classes decomposed by responsibility;
- cross-repository comparison of behavior and algorithms.

It has **not** line-by-line certified every historical source file as correct or suitable for reuse.

Therefore no Phase 1 responsibility mapping means:

> copy the corresponding old class/function into EpicHub.

It means:

> this behavior/domain exists and now has a planned clean owner.

---

# 2. Per-subsystem implementation sequence

Before implementing any meaningful historical capability, use the following sequence.

## Step A — define the semantic responsibility

State the behavior without old filenames/classes.

Example:

```text
Wrong starting point:
"port UsbEcuManager"

Correct starting point:
"discover Android USB device → obtain permission → resolve CDC topology → establish one ECU session → query signature → bind exact profile → poll runtime data"
```

The responsibility must have one intended permanent owner.

## Step B — trace all relevant historical evidence

Read the old implementation **piece-by-piece around that responsibility**, including as applicable:

- implementation functions/classes;
- callers and call graph;
- state mutated/read;
- threading/coroutines/callbacks;
- lifecycle handling;
- error/retry paths;
- protocol framing;
- caches/persistence;
- tests;
- diagnostics;
- related commits/audits where they explain a known fix;
- firmware/protocol authority where available.

Do not assume the main class contains the whole behavior. Search for duplicate helpers, alternate paths, legacy fallbacks and side effects elsewhere in the repository.

## Step C — classify every discovered behavior

For the specific subsystem, classify behavior as:

- `REQUIRED` — must exist in EpicHub;
- `SAFETY_INVARIANT` — must be preserved exactly in effect;
- `COMPATIBILITY_REQUIREMENT` — kept only because external hardware/files/protocols require it;
- `USEFUL_TECHNIQUE` — concept worth reusing, implementation rewritten;
- `ACCIDENTAL_BEHAVIOR` — implementation artifact with no product requirement;
- `KNOWN_BUG` — must not be reproduced;
- `DEAD/OBSOLETE` — remove;
- `UNKNOWN` — requires evidence before implementation.

If a behavior cannot be justified by requirement, protocol, test, physical evidence or deliberate product choice, it is not automatically inherited.

## Step D — identify historical failure modes and technical debt

Before writing the replacement, explicitly ask:

- Why did this code become large or tangled?
- Which bugs were fixed repeatedly?
- Which responsibilities are coupled only because of historical growth?
- Where are there duplicated state owners?
- Where are retries/timeouts unbounded?
- Where are values guessed, defaulted or silently coerced?
- Where are thread/lifecycle races possible?
- Where does UI own domain state it should only display?
- Where are protocol details leaked upward?
- Where are allocations/serialization/callbacks on hot paths?
- Which fallback paths are no longer needed?
- Which tests exist because a previous regression occurred?

These observations become negative requirements for the new implementation.

## Step E — design the clean contract first

Define:

- input/output types;
- state owner;
- lifetime;
- thread/dispatcher ownership;
- failure states;
- timeouts/retry bounds;
- cancellation behavior;
- dependency direction;
- diagnostic events/metrics;
- test seams;
- performance constraints.

Only then create production files/functions.

## Step F — implement from scratch

Write the EpicHub implementation against the new contract and permanent topology.

Do not preserve old class boundaries, method names, object graphs or control flow unless there is a strong independent reason.

Direct code transplant is the exception, not the default. If a small proven algorithm/table/codec is considered for literal reuse, review:

1. provenance/licence;
2. correctness independently;
3. whether the old representation is still appropriate;
4. edge cases and tests;
5. whether rewriting would produce clearer ownership/performance.

## Step G — differential qualification

Compare the new implementation against **required behavior**, not against every historical output.

Use, in priority order:

1. protocol/specification truth;
2. firmware behavior;
3. physically qualified old behavior;
4. golden fixtures/logs;
5. focused old/new differential tests;
6. new clean tests for cases the old implementation handled badly or not at all.

A difference from old code is acceptable—and often desirable—when the old behavior is classified as accidental, buggy, obsolete or inefficient.

## Step H — benchmark where architecture depends on performance

For hot paths, correctness parity is insufficient.

Measure relevant quantities such as:

- latency;
- throughput;
- CPU;
- memory;
- allocations/GC;
- JNI crossings;
- frame time/jank;
- storage size;
- I/O rate;
- cancellation responsiveness;
- reconnect/recovery time.

Do not inherit an old implementation merely because it already works if the clean design can be measurably simpler/faster/more robust.

## Step I — accept one narrow slice before expanding

A replacement subsystem is accepted only after:

- required behavior is mapped;
- negative/bug behavior is explicitly excluded;
- tests pass;
- diagnostics exist;
- benchmark/physical qualification passes where required;
- no duplicate old/new authority remains.

Then move to the next slice.

---

# 3. No bulk-port rule

The following workflow is prohibited:

```text
copy large old subsystem
→ make it compile
→ rename classes/packages
→ gradually clean it up later
```

That recreates hidden dependencies and bugs before they are understood.

The required workflow is:

```text
behavior slice
→ evidence trace
→ classification
→ clean contract
→ focused implementation
→ tests/benchmark
→ accept
→ next slice
```

This applies even when the old subsystem is known to work physically.

Physical qualification proves behavior; it does not prove the old structure is good architecture.

---

# 4. Bugs are evidence too

Historical bugs/fixes are valuable because they reveal hidden requirements.

Example pattern:

```text
old bug:
stale generation accepted after reconnect

Do not copy:
old reconnect/controller structure

Do preserve:
a stale-generation result must never mutate current session state
```

Likewise:

```text
old bug:
unrepresentable requested PID target retried forever

Do not copy:
old experiment loop

Do preserve:
experiment values must be resolved through actual controller quantization before bounded acquisition begins
```

The new code should encode the invariant directly and test it independently.

---

# 5. Tests from old repositories are specifications only after review

Old tests may contain historical assumptions or reproduce implementation details.

For each transferred test:

1. identify the real requirement it proves;
2. remove implementation-specific assumptions;
3. turn it into a format/protocol/behavior fixture where possible;
4. add cases for the known old failure mode;
5. run it against the new owner.

Do not create a test suite whose only goal is byte-for-byte reproduction of junk behavior.

---

# 6. Piece-by-piece review depth

The depth of historical review depends on the subsystem risk.

## Highest scrutiny

Read implementation/callers/tests/failure history very deeply before replacement:

- ECU transport/session/protocol;
- profile parsing/conditions;
- tune Read/Write/Burn;
- project/recovery journals;
- accessory physical-action paths;
- logging writer/recovery;
- analyzer recommendations/active experiments.

## Medium scrutiny

Behavior + performance + lifecycle detail:

- ChannelStore/runtime processing;
- BLE bridge integration;
- GPS/location;
- diagnostics;
- file import/export;
- analyzer plots/search/statistics.

## UI/presentation

Use approved behavior/visual references, but do not inherit WebView/DOM/Swing/React implementation structure.

Compose implementation is re-derived from the accepted interaction and responsive requirements.

---

# 7. Review record for each implementation slice

A substantial implementation PR should be able to answer:

```text
Historical evidence reviewed:
- files/classes/functions/tests/firmware paths

Required behavior:
- ...

Known old bugs/technical debt intentionally not reproduced:
- ...

Clean owner/contract:
- ...

Tests/goldens:
- ...

Performance/physical evidence:
- ...

Remaining deliberate differences from old implementation:
- ...
```

This can live in the PR description or a subsystem qualification document rather than creating permanent archaeology files for every tiny change.

---

# 8. Same methodology for non-EpicDash sources

The rule applies equally to:

- original EpicDash;
- JZ firmware fork;
- Tune Viewer;
- Diagnostics-Core;
- AE/PID/TCharge/Injection Angle plugins;
- UXUI experiments;
- third-party libraries/examples.

A useful algorithm or architecture idea is extracted from its original shell, independently reviewed, adapted to EpicHub's ownership model and tested in the new environment.

No reference repository gets architectural privilege merely because it already implements a desired feature.

---

# 9. Definition of successful migration

EpicHub succeeds when it preserves the **valuable knowledge** accumulated by EpicDash-JZ and related projects without inheriting their accidental structure.

The desired relationship is:

```text
old repositories
= requirements + protocol truth + lessons + failures + fixtures

EpicHub
= new ownership + new code + optimized paths + explicit contracts + independent qualification
```

not:

```text
EpicHub
= renamed/refactored EpicDash-JZ
```

This methodology remains in force throughout implementation, not only during the first migration phase.