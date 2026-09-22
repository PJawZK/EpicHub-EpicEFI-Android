# ECU Protocol Direct Verification

## Purpose

This document closes the Phase 1 review-gate requirement to verify the key EpicEFI/rusEFI ECU protocol authorities directly in the current firmware fork where practical.

It supplements `PROTOCOL_AUTHORITIES.md`; that document remains the normalized protocol contract for EpicHub.

## Reviewed firmware state

Repository: `PJawZK/epicefi_fw_jzfork`

Reviewed current head: `6a300295650ed131e57d8881afb2f00fd5291d47`.

Targeted source review only; this is not a full firmware audit.

Reviewed files:

- `firmware/hw_layer/ports/stm32/serial_over_usb/usbcfg.cpp`;
- `firmware/console/binary/signature.cpp`;
- `firmware/console/binary/tunerstudio.cpp`;
- `firmware/console/binary/tunerstudio_io.cpp`.

---

# 1. Shared STM32 USB transport identity directly confirmed

`usbcfg.cpp` directly confirms:

- device class/subclass/protocol `0xEF / 0x02 / 0x01`;
- VID `0x0483`;
- PID `0x5740`;
- CDC communications interface class `0x02`;
- CDC data interface class `0x0A`;
- CDC Union and Call Management descriptors bind control/data interfaces;
- bulk OUT/IN data endpoints;
- optional USB mass-storage interface shifts CDC interface numbers.

### Permanent implication

`0483:5740` plus expected CDC topology is a transport candidate only. Interface numbers must be descriptor-resolved, and USB identity alone never grants board/profile/runtime/tune authority.

---

# 2. Generated TunerStudio signature authority directly confirmed

`signature.cpp` directly includes a generated board-specific signature header and implements:

```text
getTsSignature() → TS_SIGNATURE
```

`tunerstudio.cpp`'s query handler sends `getTsSignature()` as the query response.

### Permanent implication

The complete live TunerStudio signature remains firmware identity/compatibility evidence. Parsed board-name fragments are diagnostic only.

---

# 3. Plain and CRC query behavior directly confirmed

`tunerstudio.cpp` directly confirms:

- plain startup/detection query is accepted;
- `Q` is explicitly interpreted as a query during ECU detection;
- CRC-framed query/hello also reaches the same signature response path.

### Permanent implication

Session synchronization/probe policy may use the proven plain/framed query behavior, while semantic authority still requires exact profile identity matching.

---

# 4. Request framing and synchronization directly confirmed

`tsProcessOne()` directly reads:

```text
first two bytes → BE packet/body length
body beginning with command
trailing 4-byte CRC
```

and computes CRC32 over the incoming body only, excluding the two-byte length.

The channel is considered synchronized only after a recognized command with valid length and CRC; malformed size/CRC/unsupported commands can clear `in_sync` and return explicit protocol errors once synchronized.

### Permanent implication

EpicHub keeps request encoding and session synchronization as separate responsibilities. The request codec does not own retry/probe policy.

---

# 5. Response framing directly confirmed to be asymmetric

`tunerstudio_io.cpp` directly builds CRC responses as:

```text
BE u16 length(status + payload)
status byte
payload
BE u32 CRC32(status + payload)
```

The CRC excludes the length field. Large responses may stream header/body/tail separately while retaining the same logical frame.

### Permanent implication

Request and response framing must not be collapsed into one symmetric envelope abstraction. A decoded response explicitly returns status plus payload.

---

# 6. Calibration write/read/Burn command dispatch directly confirmed

`tunerstudio.cpp` directly dispatches:

- `TS_CHUNK_WRITE_COMMAND` → page/offset/count bounded chunk write;
- `TS_READ_COMMAND` → page/offset/count page read;
- `TS_BURN_COMMAND` → page Burn handler;
- output/query commands through the same protocol dispatcher.

The write path validates page/range, checks user-lock/preset-refresh restrictions where applicable, applies board overrides and returns a CRC response.

The Burn handler validates the requested page and returns the distinct `TS_RESPONSE_BURN_OK` response after the firmware Burn request path.

### Permanent implication

EpicHub's existing `C/R/B` safety model is grounded in current firmware behavior:

- ranges/pages are real protocol constraints;
- RAM write acknowledgement is not Burn;
- Burn remains a distinct operation and result;
- application-side exact readback/full-snapshot verification remains necessary because firmware ACK alone does not prove the higher-level expected semantic tune image.

---

# 7. Source licensing/provenance observation

The directly reviewed firmware sources contain upstream licence headers, including GPL terms in the TunerStudio protocol implementation and Apache terms in the ChibiOS-derived USB descriptor file.

EpicHub Phase 1 uses these sources to verify wire/protocol behavior. The clean-slate implementation must not mechanically transplant source code; any actual dependency/source reuse requires explicit licence review at adoption time.

---

# Scope limits

This direct verification does **not** claim that every future firmware version or every board has identical page geometry/runtime layout. Those remain generated-profile-specific.

It also does not replace:

- executable ECU protocol golden vectors;
- real-device Android USB lifecycle/performance qualification;
- representative generated-INI corpus testing;
- exact live signature/profile matching.

## Phase 1 conclusion

The protocol-authority claims needed to start EpicHub's clean transport/profile architecture are directly corroborated against the current firmware fork head.

Remaining firmware work is qualification fixtures and change-detection/regression testing, not discovery of the fundamental USB/signature/framing/read-write-Burn ownership model.