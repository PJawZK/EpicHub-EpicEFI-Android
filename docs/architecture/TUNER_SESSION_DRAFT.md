# Engine-owned TuneSession and TuneDraft

Status: implementation foundation.

## Decision

The Tuner draft is owned by the active native `EngineState` through a `TuneSession`.

The built-in offline table is now **source data only**. It initializes a normal `TuneSnapshot`, which initializes the session-owned `TuneDraft`. It is not a second editing authority.

```text
Offline source / future imported tune / future controller snapshot
                         ↓
                   TuneSnapshot
                         ↓
EngineState ─────── TuneSession
                         ↓
                    TuneDraft
                         ↓
               TableProjection / preview
                  ↙               ↘
                2D                 3D
```

The authority boundary remains:

```text
TuneDraft Apply
    != ECU RAM write
    != Burn / nonvolatile persistence
```

## Why this replaces the old fixture path

The earlier interaction slices intentionally used a process-global `LazyLock<Mutex<TuneDraft>>` behind `OfflineTunerFixture`. That was sufficient to prove table interaction semantics, but it was not acceptable production ownership because:

- the tune draft was not tied to the native engine/session lifecycle;
- two engine sessions could observe runtime data independently while editing one shared global draft;
- project/profile generation replacement could not atomically replace the corresponding draft;
- the static fixture JNI surface was becoming a de facto authority path.

PR #20 removes those production modules from `lib.rs`. The old source file can remain in repository history/reference, but it is no longer compiled as runtime authority.

## TuneSession

`TuneSession` owns:

- immutable baseline `TuneSnapshot`;
- mutable `TuneDraft`;
- profile generation inherited from the snapshot;
- semantic table lookup by table ID;
- preview generation;
- stale-revision guarded Apply;
- Undo / Redo history.

Every engine created by the Rust `EngineRegistry` receives its own session.

The same semantic operations already qualified in prior slices now operate on that session:

- Increment / Decrement;
- Set / Add / Scale;
- horizontal / vertical / 2D interpolation;
- smoothing;
- Brush / Pull;
- polyline Brush;
- Undo / Redo.

## Table identity

The native API is no longer intrinsically tied to the fixture's primary table. `TuneSession` operations accept a semantic `table_id` and `projection_bytes(table_id)`.

The current UI compatibility facade still selects built-in table ID 7 because the app does not yet have a project/profile table browser. That constant is now presentation/source selection, not native mutation authority.

The next real-profile slice can replace the source and table selection without changing the edit engine.

## Native engine lifecycle

```text
NativeEngine.create()
       ↓
EngineRegistry::create
       ↓
EngineState
  ├─ ChannelStore
  ├─ Diagnostics
  └─ TuneSession
        ├─ baseline TuneSnapshot
        └─ TuneDraft
```

Closing the native engine destroys the corresponding session and draft with it.

Rust tests explicitly verify that two registry engine states receive independent draft revisions. Editing one session cannot mutate another session.

## JNI contract

The previous JNI functions targeting `OfflineTunerFixture` are no longer compiled.

The production bridge is now:

```text
NativeEngineBridge
      + engine handle
      + semantic table ID
      + semantic edit parameters
                ↓
        EngineState TuneSession
```

No per-cell JNI loop was introduced. Preview continues to return one packed prospective table projection.

## Kotlin compatibility facade

The current `TunerScreen` still calls the historical `OfflineTunerFixture` Kotlin name so this ownership migration does not unnecessarily rewrite the large interaction screen.

That object is now only a compatibility facade:

- it owns no native draft;
- it declares no native methods;
- it resolves the one active app-level `NativeEngine`;
- it delegates every operation to `NativeEngineBridge` with that engine handle;
- its generic internal `TunerSessionClient` accepts both an engine handle and table ID.

This facade should disappear naturally when project/profile table selection is introduced and `TunerScreen` receives an explicit Tuner session/table presentation model.

## One app-level engine invariant

EpicHub currently has one ECU/runtime engine session. The Kotlin `NativeEngine` bridge now enforces that invariant instead of silently allowing two app-level engines to be created.

The lower-level Rust registry still supports multiple engines for isolation tests and future architecture work.

## Offline source

`offline_tune_source.rs` contains only immutable snapshot construction:

- profile generation 42;
- table ID 7;
- synthetic 4×5 VE table;
- one protected cell.

It has no mutex, history, preview state or mutation API.

The visible Tuner authority remains offline and non-controller-applied. A future UI wording pass should distinguish more precisely between **offline source** and **session draft**.

## Qualification requirements

This slice is not complete until CI proves:

1. source SHA qualification;
2. Rust formatting and Clippy;
3. all Rust tests;
4. engine-session isolation tests;
5. Android JVM compilation/tests;
6. debug and performance APK builds;
7. Macrobenchmark harness;
8. signatures and dual production ARM libraries;
9. artifact publication.

## Next step

After this ownership foundation is qualified, the next meaningful Tuner slice is to replace `offline_tune_source` with an actual resolved EpicEFI tune/profile source and expose real semantic tables through the same `TuneSession` contract.
