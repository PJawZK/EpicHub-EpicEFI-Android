//! Offline-first authority coupling for a *candidate* live runtime session.
//!
//! This module does not prove Android USB ownership. Only the retained Android
//! connection owner may eventually call this broker through a guarded native
//! bridge. There is deliberately no JNI entrypoint or Dashboard consumer yet.
//!
//! The broker compiles the channel catalog and output command from exactly one
//! resolved definition, binds them to the exact selected profile and observed
//! signature, and owns acquisition plus the real-data-only batch store together.
//! No caller-chosen session number is accepted.
use crate::ecu_profile::ResolvedEcuProfile;
use crate::live_channel_store::{LiveChannelBatch, LiveChannelStore, LiveChannelStoreError};
use crate::runtime_acquisition_session::{
    RuntimeAcquisitionError, RuntimeAcquisitionSession, RuntimeRequestToken, RuntimeSampleState,
};
use crate::runtime_definition_catalog::{
    NamedRuntimeChannel, RuntimeDefinitionError, compile_runtime_output_channels,
};
use crate::runtime_output_read_plan::{RuntimeOutputReadError, RuntimeOutputReadPlan};

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct BoundRuntimeIdentity {
    endpoint_id: String,
    signature: String,
    profile_generation: u64,
    profile_fingerprint: String,
    definition_revision: u64,
    session_epoch: u64,
}

impl BoundRuntimeIdentity {
    pub fn endpoint_id(&self) -> &str { &self.endpoint_id }
    pub fn signature(&self) -> &str { &self.signature }
    pub fn profile_generation(&self) -> u64 { self.profile_generation }
    pub fn profile_fingerprint(&self) -> &str { &self.profile_fingerprint }
    /// Non-cryptographic revision marker for exact resolved-text coherence.
    /// This is NOT firmware authenticity or a replacement for an INI digest.
    pub fn definition_revision(&self) -> u64 { self.definition_revision }
    pub fn session_epoch(&self) -> u64 { self.session_epoch }
}

#[derive(Clone, Debug, PartialEq)]
pub enum BoundRuntimeError {
    InvalidEndpoint,
    UnverifiedProfile,
    DefinitionCommand,
    EpochExhausted,
    SessionClosed,
    StaleAuthority,
    NoPendingBatch,
    Definition(RuntimeDefinitionError),
    ReadPlan(RuntimeOutputReadError),
    Acquisition(RuntimeAcquisitionError),
    Store(LiveChannelStoreError),
}

struct ActiveRuntime {
    binding: BoundRuntimeIdentity,
    channels: Vec<NamedRuntimeChannel>,
    acquisition: RuntimeAcquisitionSession,
    store: LiveChannelStore,
}

/// Single-owner native candidate authority. A replacement always revokes the
/// preceding session first, even if the replacement definition is rejected.
#[derive(Default)]
pub struct RuntimeBoundLiveSession {
    last_epoch: u64,
    active: Option<ActiveRuntime>,
}

impl RuntimeBoundLiveSession {
    pub fn new() -> Self { Self::default() }

    /// Call only after exact runtime identity and profile selection under the
    /// same serialized USB owner. This is not itself evidence of a live device.
    pub fn bind(
        &mut self,
        endpoint_id: &str,
        observed_signature: &str,
        selected_profile: &dyn ResolvedEcuProfile,
        resolved_definition: &str,
    ) -> Result<BoundRuntimeIdentity, BoundRuntimeError> {
        self.disconnect();
        let epoch = self.last_epoch.checked_add(1).ok_or(BoundRuntimeError::EpochExhausted)?;
        self.last_epoch = epoch;
        let profile = selected_profile.identity();
        let fingerprint = profile.definition_fingerprint.filter(|value| !value.trim().is_empty());
        if endpoint_id.trim().is_empty() {
            return Err(BoundRuntimeError::InvalidEndpoint);
        }
        if observed_signature.is_empty()
            || !profile.exact_signature_matches(observed_signature)
            || profile.profile_generation == 0
            || fingerprint.is_none()
        {
            return Err(BoundRuntimeError::UnverifiedProfile);
        }

        // A single source string creates both decoder and wire protocol. The
        // selected profile fingerprint is retained separately from the local
        // exact-text revision; the two are not assumed interchangeable.
        let compiled = compile_runtime_output_channels(resolved_definition)
            .map_err(BoundRuntimeError::Definition)?;
        let (command, envelope) = resolved_output_wire_contract(resolved_definition)?;
        let plan = RuntimeOutputReadPlan::compile(&command, compiled.decoder.frame_length(), &envelope)
            .map_err(BoundRuntimeError::ReadPlan)?;
        let acquisition = RuntimeAcquisitionSession::new(epoch, compiled.decoder, plan)
            .map_err(BoundRuntimeError::Acquisition)?;
        let store = LiveChannelStore::new(epoch).ok_or(BoundRuntimeError::EpochExhausted)?;

        let binding = BoundRuntimeIdentity {
            endpoint_id: endpoint_id.to_owned(),
            signature: observed_signature.to_owned(),
            profile_generation: profile.profile_generation,
            profile_fingerprint: fingerprint.unwrap().to_owned(),
            definition_revision: revision(resolved_definition.as_bytes()),
            session_epoch: epoch,
        };
        self.active = Some(ActiveRuntime {
            binding: binding.clone(),
            channels: compiled.channel_info,
            acquisition,
            store,
        });
        Ok(binding)
    }

    pub fn channel_info(
        &self,
        binding: &BoundRuntimeIdentity,
    ) -> Result<&[NamedRuntimeChannel], BoundRuntimeError> {
        Ok(&self.authorized(binding)?.channels)
    }

    pub fn begin(
        &mut self,
        binding: &BoundRuntimeIdentity,
        now_ms: u64,
        timeout_ms: u64,
    ) -> Result<(RuntimeRequestToken, Vec<u8>), BoundRuntimeError> {
        let active = self.authorized_mut(binding)?;
        active.acquisition.begin(binding.session_epoch, now_ms, timeout_ms)
            .map_err(BoundRuntimeError::Acquisition)
    }

    /// Full frame is accepted only when the same bound session issued the
    /// matching request. Any CRC/status/length/decode failure revokes freshness.
    pub fn complete(
        &mut self,
        binding: &BoundRuntimeIdentity,
        token: RuntimeRequestToken,
        now_ms: u64,
        response: &[u8],
    ) -> Result<&LiveChannelBatch, BoundRuntimeError> {
        let active = self.authorized_mut(binding)?;
        let acquisition_result = active.acquisition.complete(token, now_ms, response);
        match acquisition_result {
            Ok(snapshot) => {
                if snapshot.state != RuntimeSampleState::Fresh {
                    active.store.mark_invalid();
                    return Err(BoundRuntimeError::Store(LiveChannelStoreError::NotFresh));
                }
                // End the mutable publication borrow before any invalidation,
                // then fetch the accepted batch from the same bounded store.
                if let Err(error) = active.store.publish(snapshot, now_ms).map(|_| ()) {
                    active.store.mark_invalid();
                    return Err(BoundRuntimeError::Store(error));
                }
                Ok(active.store.latest().expect("accepted publication must retain a batch"))
            }
            Err(error) => {
                match error {
                    RuntimeAcquisitionError::ClockRegression
                    | RuntimeAcquisitionError::DecodeFailure
                    | RuntimeAcquisitionError::Protocol(_) => active.store.mark_invalid(),
                    RuntimeAcquisitionError::RequestTimedOut => active.store.mark_stale(),
                    _ => {}
                }
                Err(BoundRuntimeError::Acquisition(error))
            }
        }
    }

    pub fn expire(
        &mut self,
        binding: &BoundRuntimeIdentity,
        now_ms: u64,
        max_age_ms: u64,
    ) -> Result<bool, BoundRuntimeError> {
        let active = self.authorized_mut(binding)?;
        let pending_expired = match active.acquisition.expire(binding.session_epoch, now_ms) {
            Ok(expired) => expired,
            Err(error) => {
                active.store.mark_invalid();
                return Err(BoundRuntimeError::Acquisition(error));
            }
        };
        if pending_expired {
            active.store.mark_stale();
        }
        Ok(active.store.expire(now_ms, max_age_ms) || pending_expired)
    }

    /// Read-side aging is mandatory: callers cannot fetch a once-fresh sample
    /// forever without checking its elapsed monotonic age.
    pub fn latest(
        &mut self,
        binding: &BoundRuntimeIdentity,
        now_ms: u64,
        max_age_ms: u64,
    ) -> Result<Option<&LiveChannelBatch>, BoundRuntimeError> {
        self.expire(binding, now_ms, max_age_ms)?;
        Ok(self.authorized(binding)?.store.latest())
    }

    /// USB I/O, permission or ownership failure must call this synchronously.
    pub fn disconnect(&mut self) {
        if let Some(mut active) = self.active.take() {
            active.acquisition.disconnect();
            active.store.disconnect();
        }
    }

    fn authorized(&self, binding: &BoundRuntimeIdentity) -> Result<&ActiveRuntime, BoundRuntimeError> {
        let active = self.active.as_ref().ok_or(BoundRuntimeError::SessionClosed)?;
        if active.binding != *binding {
            return Err(BoundRuntimeError::StaleAuthority);
        }
        Ok(active)
    }

    fn authorized_mut(
        &mut self,
        binding: &BoundRuntimeIdentity,
    ) -> Result<&mut ActiveRuntime, BoundRuntimeError> {
        let active = self.active.as_mut().ok_or(BoundRuntimeError::SessionClosed)?;
        if active.binding != *binding {
            return Err(BoundRuntimeError::StaleAuthority);
        }
        Ok(active)
    }
}

/// Strictly require both declarations in the same resolved definition.
/// Unsupported command templates are rejected by RuntimeOutputReadPlan.
fn resolved_output_wire_contract(text: &str) -> Result<(String, String), BoundRuntimeError> {
    let mut section = "";
    let mut command: Option<String> = None;
    let mut envelope: Option<String> = None;
    for raw in text.lines() {
        let line = raw.split(';').next().unwrap_or("").trim();
        if line.starts_with('[') && line.ends_with(']') {
            section = &line[1..line.len() - 1];
            continue;
        }
        let Some((key, value)) = line.split_once('=') else { continue };
        let key = key.trim();
        let value = value.trim();
        if section.eq_ignore_ascii_case("OutputChannels") && key == "ochGetCommand" {
            if command.replace(value.trim_matches('"').to_owned()).is_some() {
                return Err(BoundRuntimeError::DefinitionCommand);
            }
        }
        if section.eq_ignore_ascii_case("Constants") && key == "messageEnvelopeFormat" {
            if envelope.replace(value.to_owned()).is_some() {
                return Err(BoundRuntimeError::DefinitionCommand);
            }
        }
    }
    match (command, envelope) {
        (Some(command), Some(envelope)) => Ok((command, envelope)),
        _ => Err(BoundRuntimeError::DefinitionCommand),
    }
}

fn revision(bytes: &[u8]) -> u64 {
    let mut hash: u64 = 0xcbf29ce484222325;
    for byte in bytes {
        hash = (hash ^ u64::from(*byte)).wrapping_mul(0x100000001b3);
    }
    hash
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::ecu_profile::{EcuProfileResolveError, ProfileIdentity};
    use crate::ms_envelope_codec::crc32_ieee;
    use crate::tuning::{TuneSnapshot, TuneSnapshotOrigin};

    const SOURCE: &str = r#"
[TunerStudio]
signature = "runtime.test.1"
[Constants]
messageEnvelopeFormat = msEnvelope_1.0
endianness = little
[OutputChannels]
ochBlockSize = 4
ochGetCommand = "O%2o%2c"
rpm = scalar, F32, 0, "RPM", 1, 0
"#;
    struct Profile;
    impl ResolvedEcuProfile for Profile {
        fn identity(&self) -> ProfileIdentity<'_> {
            ProfileIdentity {
                profile_name: "generic",
                signature: "runtime.test.1",
                profile_generation: 42,
                definition_fingerprint: Some("profile-42"),
            }
        }
        fn page_sizes(&self) -> &[usize] { &[] }
        fn resolve_pages(
            &self,
            _pages: &[&[u8]],
            _origin: TuneSnapshotOrigin,
        ) -> Result<TuneSnapshot, EcuProfileResolveError> {
            unreachable!("runtime-only tests do not load tune pages")
        }
    }
    fn frame(value: f32) -> Vec<u8> {
        let mut body = vec![0];
        body.extend_from_slice(&value.to_le_bytes());
        let mut data = Vec::new();
        data.extend_from_slice(&(body.len() as u16).to_be_bytes());
        data.extend_from_slice(&body);
        data.extend_from_slice(&crc32_ieee(&body).to_be_bytes());
        data
    }
    fn bind(broker: &mut RuntimeBoundLiveSession) -> BoundRuntimeIdentity {
        broker.bind("usb:test", "runtime.test.1", &Profile, SOURCE).unwrap()
    }

    #[test]
    fn one_exact_definition_supplies_catalog_plan_and_live_batch() {
        let mut broker = RuntimeBoundLiveSession::new();
        let lease = bind(&mut broker);
        assert_eq!(lease.profile_generation(), 42);
        assert_eq!(lease.profile_fingerprint(), "profile-42");
        assert_eq!(broker.channel_info(&lease).unwrap()[0].name, "rpm");
        let (token, request) = broker.begin(&lease, 10, 100).unwrap();
        assert_eq!(&request[2..7], &[b'O', 0, 0, 4, 0]);
        let batch = broker.complete(&lease, token, 20, &frame(820.0)).unwrap();
        assert_eq!(batch.samples.len(), 1);
        assert_eq!(batch.samples[0].value, 820.0);
        assert_eq!(batch.validity, RuntimeSampleState::Fresh);
        assert_eq!(broker.latest(&lease, 21, 100).unwrap().unwrap().sequence, 1);
    }

    #[test]
    fn rejects_unknown_signature_and_missing_or_mismatched_wire_contract() {
        let mut broker = RuntimeBoundLiveSession::new();
        assert_eq!(
            broker.bind("usb:wrong", "runtime.test.2", &Profile, SOURCE),
            Err(BoundRuntimeError::UnverifiedProfile)
        );
        assert_eq!(
            broker.bind("usb:test", "runtime.test.1", &Profile, &SOURCE.replace("O%2o%2c", "P")),
            Err(BoundRuntimeError::ReadPlan(RuntimeOutputReadError::UnsupportedCommand))
        );
        assert_eq!(
            broker.bind("usb:test", "runtime.test.1", &Profile, &SOURCE.replace("messageEnvelopeFormat = msEnvelope_1.0", "")),
            Err(BoundRuntimeError::DefinitionCommand)
        );
        assert_eq!(
            broker.bind("usb:test", "runtime.test.1", &Profile, &SOURCE.replace("rpm = scalar, F32, 0, \"RPM\", 1, 0", "rpm = { 100 + 1 }")),
            Err(BoundRuntimeError::Definition(RuntimeDefinitionError::UnsupportedChannel("rpm".into())))
        );
    }

    #[test]
    fn a_failed_rebind_revokes_old_authority_and_uses_a_new_epoch() {
        let mut broker = RuntimeBoundLiveSession::new();
        let old = bind(&mut broker);
        let (old_token, _) = broker.begin(&old, 1, 100).unwrap();
        assert_eq!(
            broker.bind("usb:test", "mismatch", &Profile, SOURCE),
            Err(BoundRuntimeError::UnverifiedProfile)
        );
        assert_eq!(broker.complete(&old, old_token, 2, &frame(50.0)), Err(BoundRuntimeError::SessionClosed));
        let next = bind(&mut broker);
        assert!(next.session_epoch() > old.session_epoch());
        assert_eq!(broker.begin(&old, 3, 100), Err(BoundRuntimeError::StaleAuthority));
        assert_eq!(broker.complete(&old, old_token, 3, &frame(50.0)), Err(BoundRuntimeError::StaleAuthority));
        let (new_token, _) = broker.begin(&next, 4, 100).unwrap();
        assert_eq!(broker.complete(&next, new_token, 5, &frame(900.0)).unwrap().sequence, 1);
    }

    #[test]
    fn corrupted_crc_and_nonfinite_values_invalidate_prior_live_batch() {
        let mut broker = RuntimeBoundLiveSession::new();
        let lease = bind(&mut broker);
        let (first, _) = broker.begin(&lease, 0, 100).unwrap();
        broker.complete(&lease, first, 1, &frame(1000.0)).unwrap();

        let (bad, _) = broker.begin(&lease, 2, 100).unwrap();
        let mut corrupted = frame(1100.0);
        *corrupted.last_mut().unwrap() ^= 1;
        assert!(matches!(
            broker.complete(&lease, bad, 3, &corrupted),
            Err(BoundRuntimeError::Acquisition(RuntimeAcquisitionError::Protocol(_)))
        ));
        assert_eq!(broker.latest(&lease, 4, 100).unwrap().unwrap().validity, RuntimeSampleState::Invalid);

        let (nan, _) = broker.begin(&lease, 5, 100).unwrap();
        assert_eq!(
            broker.complete(&lease, nan, 6, &frame(f32::NAN)),
            Err(BoundRuntimeError::Store(LiveChannelStoreError::NotFresh))
        );
        assert_eq!(broker.latest(&lease, 7, 100).unwrap().unwrap().validity, RuntimeSampleState::Invalid);
        let (recovery, _) = broker.begin(&lease, 8, 100).unwrap();
        assert_eq!(broker.complete(&lease, recovery, 9, &frame(850.0)).unwrap().validity, RuntimeSampleState::Fresh);
    }

    #[test]
    fn expires_and_disconnects_without_resurrecting_live_samples() {
        let mut broker = RuntimeBoundLiveSession::new();
        let lease = bind(&mut broker);
        let (token, _) = broker.begin(&lease, 10, 20).unwrap();
        broker.complete(&lease, token, 15, &frame(800.0)).unwrap();
        assert_eq!(broker.latest(&lease, 24, 10).unwrap().unwrap().validity, RuntimeSampleState::Fresh);
        assert_eq!(broker.latest(&lease, 25, 10).unwrap().unwrap().validity, RuntimeSampleState::Stale);
        let (late, _) = broker.begin(&lease, 30, 10).unwrap();
        assert!(broker.expire(&lease, 40, 100).unwrap());
        assert_eq!(
            broker.complete(&lease, late, 41, &frame(990.0)),
            Err(BoundRuntimeError::Acquisition(RuntimeAcquisitionError::NoPendingRequest))
        );
        broker.disconnect();
        assert_eq!(broker.latest(&lease, 42, 100), Err(BoundRuntimeError::SessionClosed));
        assert_eq!(broker.begin(&lease, 43, 100), Err(BoundRuntimeError::SessionClosed));
    }

    #[test]
    fn forged_binding_fields_never_authorize_requests() {
        let mut broker = RuntimeBoundLiveSession::new();
        let lease = bind(&mut broker);
        let mut forged = lease.clone();
        forged.profile_generation += 1;
        assert_eq!(broker.begin(&forged, 1, 100), Err(BoundRuntimeError::StaleAuthority));
        let mut forged = lease.clone();
        forged.definition_revision ^= 1;
        assert_eq!(broker.channel_info(&forged), Err(BoundRuntimeError::StaleAuthority));
        let mut forged = lease.clone();
        forged.endpoint_id = "usb:other".to_owned();
        assert_eq!(broker.latest(&forged, 1, 100), Err(BoundRuntimeError::StaleAuthority));
    }
}
