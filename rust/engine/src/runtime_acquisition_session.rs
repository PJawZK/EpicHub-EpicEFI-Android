//! Session-fenced read-only runtime acquisition. No transport or JNI calls.
//! One atomic full-frame request is supported in this stage. Requests for
//! larger blocks must be handled by a later explicitly sequenced chunk reader.
use crate::runtime_channel_decode::{CompiledRuntimeChannelCatalog, DecodedRuntimeSample};
use crate::runtime_output_read_plan::{RuntimeOutputReadError, RuntimeOutputReadPlan};

#[derive(Clone, Debug, PartialEq, Eq)]
pub enum RuntimeAcquisitionError {
    SessionClosed,
    StaleSession,
    RequestAlreadyPending,
    NoPendingRequest,
    RequestTokenMismatch,
    RequestTimedOut,
    ClockRegression,
    FrameNotRepresentable,
    DecodeFailure,
    Protocol(RuntimeOutputReadError),
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub struct RuntimeRequestToken {
    pub session: u64,
    pub sequence: u64,
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum RuntimeSampleState {
    Unknown,
    Fresh,
    Stale,
    Invalid,
}

#[derive(Clone, Debug, PartialEq)]
pub struct RuntimePublishedSample {
    pub id: u32,
    pub value: f32,
    pub state: RuntimeSampleState,
}

#[derive(Clone, Debug, PartialEq)]
pub struct RuntimePublishedSnapshot {
    pub session: u64,
    pub sequence: u64,
    pub state: RuntimeSampleState,
    pub samples: Vec<RuntimePublishedSample>,
}

#[derive(Clone, Copy, Debug)]
struct PendingRequest {
    token: RuntimeRequestToken,
    started_ms: u64,
    timeout_ms: u64,
}

pub struct RuntimeAcquisitionSession {
    session: u64,
    sequence: u64,
    active: bool,
    pending: Option<PendingRequest>,
    catalog: CompiledRuntimeChannelCatalog,
    plan: RuntimeOutputReadPlan,
    published: RuntimePublishedSnapshot,
}

impl RuntimeAcquisitionSession {
    pub fn new(
        session: u64,
        catalog: CompiledRuntimeChannelCatalog,
        plan: RuntimeOutputReadPlan,
    ) -> Result<Self, RuntimeAcquisitionError> {
        if session == 0
            || catalog.frame_length() != plan.frame_length()
            || catalog.frame_length() > u16::MAX as usize
        {
            return Err(RuntimeAcquisitionError::FrameNotRepresentable);
        }
        Ok(Self {
            session,
            sequence: 0,
            active: true,
            pending: None,
            catalog,
            plan,
            published: RuntimePublishedSnapshot {
                session,
                sequence: 0,
                state: RuntimeSampleState::Unknown,
                samples: Vec::new(),
            },
        })
    }

    pub fn snapshot(&self) -> &RuntimePublishedSnapshot {
        &self.published
    }

    pub fn begin(
        &mut self,
        session: u64,
        now_ms: u64,
        timeout_ms: u64,
    ) -> Result<(RuntimeRequestToken, Vec<u8>), RuntimeAcquisitionError> {
        self.check_session(session)?;
        if self.pending.is_some() {
            return Err(RuntimeAcquisitionError::RequestAlreadyPending);
        }
        if timeout_ms == 0 || self.sequence == u64::MAX {
            return Err(RuntimeAcquisitionError::RequestTimedOut);
        }
        let request = self
            .plan
            .request(0, self.catalog.frame_length())
            .map_err(RuntimeAcquisitionError::Protocol)?;
        self.sequence += 1;
        let token = RuntimeRequestToken {
            session,
            sequence: self.sequence,
        };
        self.pending = Some(PendingRequest {
            token,
            started_ms: now_ms,
            timeout_ms,
        });
        Ok((token, request))
    }

    pub fn complete(
        &mut self,
        token: RuntimeRequestToken,
        now_ms: u64,
        response: &[u8],
    ) -> Result<&RuntimePublishedSnapshot, RuntimeAcquisitionError> {
        self.check_session(token.session)?;
        let pending = self
            .pending
            .ok_or(RuntimeAcquisitionError::NoPendingRequest)?;
        if pending.token != token {
            return Err(RuntimeAcquisitionError::RequestTokenMismatch);
        }
        self.pending = None;
        if now_ms < pending.started_ms {
            self.invalidate(RuntimeSampleState::Invalid);
            return Err(RuntimeAcquisitionError::ClockRegression);
        }
        if now_ms - pending.started_ms >= pending.timeout_ms {
            self.invalidate(RuntimeSampleState::Stale);
            return Err(RuntimeAcquisitionError::RequestTimedOut);
        }
        let frame = match self.plan.response(response, self.catalog.frame_length()) {
            Ok(frame) => frame,
            Err(error) => {
                self.invalidate(RuntimeSampleState::Invalid);
                return Err(RuntimeAcquisitionError::Protocol(error));
            }
        };
        let decoded = match self.catalog.decode(&frame) {
            Some(samples) => samples,
            None => {
                self.invalidate(RuntimeSampleState::Invalid);
                return Err(RuntimeAcquisitionError::DecodeFailure);
            }
        };
        self.publish(token.sequence, decoded);
        Ok(&self.published)
    }

    pub fn expire(&mut self, session: u64, now_ms: u64) -> Result<bool, RuntimeAcquisitionError> {
        self.check_session(session)?;
        let Some(pending) = self.pending else {
            return Ok(false);
        };
        if now_ms < pending.started_ms {
            self.pending = None;
            self.invalidate(RuntimeSampleState::Invalid);
            return Err(RuntimeAcquisitionError::ClockRegression);
        }
        if now_ms - pending.started_ms < pending.timeout_ms {
            return Ok(false);
        }
        self.pending = None;
        self.invalidate(RuntimeSampleState::Stale);
        Ok(true)
    }

    pub fn disconnect(&mut self) {
        self.active = false;
        self.pending = None;
        self.invalidate(RuntimeSampleState::Stale);
    }

    fn check_session(&self, session: u64) -> Result<(), RuntimeAcquisitionError> {
        if !self.active {
            return Err(RuntimeAcquisitionError::SessionClosed);
        }
        if session != self.session {
            return Err(RuntimeAcquisitionError::StaleSession);
        }
        Ok(())
    }

    fn invalidate(&mut self, state: RuntimeSampleState) {
        self.published.state = state;
        for sample in &mut self.published.samples {
            sample.state = state;
        }
    }

    fn publish(&mut self, sequence: u64, decoded: Vec<DecodedRuntimeSample>) {
        let all_valid = decoded.iter().all(|sample| sample.valid);
        self.published = RuntimePublishedSnapshot {
            session: self.session,
            sequence,
            state: if all_valid {
                RuntimeSampleState::Fresh
            } else {
                RuntimeSampleState::Invalid
            },
            samples: decoded
                .into_iter()
                .map(|sample| RuntimePublishedSample {
                    id: sample.id,
                    value: sample.value,
                    state: if sample.valid {
                        RuntimeSampleState::Fresh
                    } else {
                        RuntimeSampleState::Invalid
                    },
                })
                .collect(),
        };
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::ms_envelope_codec::crc32_ieee;
    use crate::runtime_channel_decode::{
        RuntimeChannelDescriptor, RuntimeEndian, RuntimeValueType,
    };

    fn session(epoch: u64) -> RuntimeAcquisitionSession {
        let catalog = CompiledRuntimeChannelCatalog::compile(
            4,
            vec![RuntimeChannelDescriptor {
                id: 101,
                offset: 0,
                value_type: RuntimeValueType::F32,
                endian: RuntimeEndian::Little,
                scale: 1.0,
                translate: 0.0,
            }],
        )
        .unwrap();
        let plan = RuntimeOutputReadPlan::compile("O%2o%2c", 4, "msEnvelope_1.0").unwrap();
        RuntimeAcquisitionSession::new(epoch, catalog, plan).unwrap()
    }

    fn response(data: &[u8]) -> Vec<u8> {
        let mut body = vec![0];
        body.extend_from_slice(data);
        let mut frame = Vec::new();
        frame.extend_from_slice(&(body.len() as u16).to_be_bytes());
        frame.extend_from_slice(&body);
        frame.extend_from_slice(&crc32_ieee(&body).to_be_bytes());
        frame
    }

    #[test]
    fn only_matching_request_publishes_fresh_data() {
        let mut reader = session(7);
        assert_eq!(reader.snapshot().state, RuntimeSampleState::Unknown);
        let (token, request) = reader.begin(7, 100, 50).unwrap();
        assert_eq!(&request[2..7], &[b'O', 0, 0, 4, 0]);
        assert_eq!(
            reader.begin(7, 101, 50),
            Err(RuntimeAcquisitionError::RequestAlreadyPending)
        );
        let wrong = RuntimeRequestToken {
            session: 7,
            sequence: token.sequence + 1,
        };
        assert_eq!(
            reader.complete(wrong, 120, &response(&800f32.to_le_bytes())),
            Err(RuntimeAcquisitionError::RequestTokenMismatch)
        );
        assert_eq!(reader.snapshot().state, RuntimeSampleState::Unknown);
        let result = reader
            .complete(token, 120, &response(&800f32.to_le_bytes()))
            .unwrap();
        assert_eq!(result.state, RuntimeSampleState::Fresh);
        assert_eq!(result.samples[0].value, 800.0);
    }

    #[test]
    fn timeout_and_late_response_never_restore_old_sample() {
        let mut reader = session(11);
        let (first, _) = reader.begin(11, 0, 50).unwrap();
        reader
            .complete(first, 1, &response(&600f32.to_le_bytes()))
            .unwrap();
        let (late, _) = reader.begin(11, 10, 50).unwrap();
        assert!(reader.expire(11, 60).unwrap());
        assert_eq!(reader.snapshot().state, RuntimeSampleState::Stale);
        assert_eq!(
            reader.complete(late, 61, &response(&900f32.to_le_bytes())),
            Err(RuntimeAcquisitionError::NoPendingRequest)
        );
        assert_eq!(reader.snapshot().state, RuntimeSampleState::Stale);
    }

    #[test]
    fn bad_crc_invalidates_and_can_recover_only_from_new_request() {
        let mut reader = session(3);
        let (first, _) = reader.begin(3, 0, 100).unwrap();
        let mut bad = response(&1f32.to_le_bytes());
        *bad.last_mut().unwrap() ^= 1;
        assert!(matches!(
            reader.complete(first, 1, &bad),
            Err(RuntimeAcquisitionError::Protocol(_))
        ));
        assert_eq!(reader.snapshot().state, RuntimeSampleState::Invalid);
        let (next, _) = reader.begin(3, 2, 100).unwrap();
        reader
            .complete(next, 3, &response(&15f32.to_le_bytes()))
            .unwrap();
        assert_eq!(reader.snapshot().state, RuntimeSampleState::Fresh);
    }

    #[test]
    fn reconnect_and_close_reject_old_tokens() {
        let mut reader = session(6);
        let (token, _) = reader.begin(6, 0, 50).unwrap();
        assert_eq!(
            reader.complete(
                RuntimeRequestToken {
                    session: 5,
                    sequence: token.sequence
                },
                1,
                &[]
            ),
            Err(RuntimeAcquisitionError::StaleSession)
        );
        reader.disconnect();
        assert_eq!(reader.snapshot().state, RuntimeSampleState::Stale);
        assert_eq!(
            reader.complete(token, 2, &response(&1f32.to_le_bytes())),
            Err(RuntimeAcquisitionError::SessionClosed)
        );
        let mut other = session(7);
        assert_eq!(
            other.complete(token, 2, &response(&1f32.to_le_bytes())),
            Err(RuntimeAcquisitionError::StaleSession)
        );
    }

    #[test]
    fn nan_data_is_explicitly_invalid_and_not_fresh() {
        let mut reader = session(1);
        let (token, _) = reader.begin(1, 0, 50).unwrap();
        let state = reader
            .complete(token, 1, &response(&f32::NAN.to_le_bytes()))
            .unwrap();
        assert_eq!(state.state, RuntimeSampleState::Invalid);
        assert_eq!(state.samples[0].state, RuntimeSampleState::Invalid);
    }
}
