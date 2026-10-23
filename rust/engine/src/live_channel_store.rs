//! Bounded live-channel store, deliberately separate from synthetic dashboard data.
//! Publication accepts only verified acquisition snapshots from one selected
//! session and monotonically increasing request sequence and sample time.
use crate::runtime_acquisition_session::{
    RuntimePublishedSnapshot, RuntimeSampleState,
};
use crate::runtime_channel_decode::MAX_RUNTIME_CHANNELS;

#[derive(Clone, Debug, PartialEq)]
pub struct LiveChannelBatch {
    pub session: u64,
    pub sequence: u64,
    pub received_at_ms: u64,
    pub validity: RuntimeSampleState,
    pub samples: Vec<LiveChannelReading>,
}

#[derive(Clone, Copy, Debug, PartialEq)]
pub struct LiveChannelReading {
    pub id: u32,
    pub value: f32,
    pub validity: RuntimeSampleState,
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum LiveChannelStoreError {
    SessionClosed,
    SessionMismatch,
    NotFresh,
    DuplicateSequence,
    ClockRegression,
    TooManyChannels,
    DuplicateChannel,
}

pub struct LiveChannelStore {
    session: u64,
    closed: bool,
    latest: Option<LiveChannelBatch>,
}

impl LiveChannelStore {
    pub fn new(session: u64) -> Option<Self> {
        (session != 0).then_some(Self {
            session,
            closed: false,
            latest: None,
        })
    }

    pub fn latest(&self) -> Option<&LiveChannelBatch> {
        self.latest.as_ref()
    }

    pub fn publish(
        &mut self,
        source: &RuntimePublishedSnapshot,
        received_at_ms: u64,
    ) -> Result<&LiveChannelBatch, LiveChannelStoreError> {
        if self.closed {
            return Err(LiveChannelStoreError::SessionClosed);
        }
        if source.session != self.session {
            return Err(LiveChannelStoreError::SessionMismatch);
        }
        if source.state != RuntimeSampleState::Fresh
            || source.samples.is_empty()
            || source.samples.iter().any(|sample| {
                sample.state != RuntimeSampleState::Fresh
                    || sample.id == 0
                    || !sample.value.is_finite()
            })
        {
            return Err(LiveChannelStoreError::NotFresh);
        }
        if source.samples.len() > MAX_RUNTIME_CHANNELS {
            return Err(LiveChannelStoreError::TooManyChannels);
        }
        let mut ids = std::collections::HashSet::with_capacity(source.samples.len());
        if source.samples.iter().any(|sample| !ids.insert(sample.id)) {
            return Err(LiveChannelStoreError::DuplicateChannel);
        }
        if let Some(previous) = &self.latest {
            if source.sequence <= previous.sequence {
                return Err(LiveChannelStoreError::DuplicateSequence);
            }
            if received_at_ms < previous.received_at_ms {
                return Err(LiveChannelStoreError::ClockRegression);
            }
        } else if source.sequence == 0 {
            return Err(LiveChannelStoreError::DuplicateSequence);
        }
        self.latest = Some(LiveChannelBatch {
            session: self.session,
            sequence: source.sequence,
            received_at_ms,
            validity: RuntimeSampleState::Fresh,
            samples: source
                .samples
                .iter()
                .map(|sample| LiveChannelReading {
                    id: sample.id,
                    value: sample.value,
                    validity: RuntimeSampleState::Fresh,
                })
                .collect(),
        });
        Ok(self.latest.as_ref().unwrap())
    }

    /// Remove freshness immediately; preserve last numbers only as non-live history.
    pub fn mark_stale(&mut self) {
        if let Some(batch) = self.latest.as_mut() {
            batch.validity = RuntimeSampleState::Stale;
            for sample in &mut batch.samples {
                sample.validity = RuntimeSampleState::Stale;
            }
        }
    }

    pub fn mark_invalid(&mut self) {
        if let Some(batch) = self.latest.as_mut() {
            batch.validity = RuntimeSampleState::Invalid;
            for sample in &mut batch.samples {
                sample.validity = RuntimeSampleState::Invalid;
            }
        }
    }

    pub fn disconnect(&mut self) {
        self.closed = true;
        self.mark_stale();
    }

    /// Monotonic-time aging is explicit; consumer reads never resurrect a stale value.
    pub fn expire(&mut self, now_ms: u64, max_age_ms: u64) -> bool {
        let Some(batch) = &self.latest else {
            return false;
        };
        if self.closed || max_age_ms == 0 || now_ms < batch.received_at_ms
            || now_ms - batch.received_at_ms >= max_age_ms
        {
            self.mark_stale();
            return true;
        }
        false
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::runtime_acquisition_session::RuntimePublishedSample;

    fn snapshot(session: u64, sequence: u64, state: RuntimeSampleState) -> RuntimePublishedSnapshot {
        RuntimePublishedSnapshot {
            session,
            sequence,
            state,
            samples: vec![RuntimePublishedSample { id: 101, value: 800.0, state }],
        }
    }

    #[test]
    fn stores_only_fresh_same_session_monotonic_samples() {
        let mut store = LiveChannelStore::new(9).unwrap();
        assert!(store.latest().is_none());
        assert_eq!(store.publish(&snapshot(8, 1, RuntimeSampleState::Fresh), 10), Err(LiveChannelStoreError::SessionMismatch));
        assert_eq!(store.publish(&snapshot(9, 1, RuntimeSampleState::Stale), 10), Err(LiveChannelStoreError::NotFresh));
        assert_eq!(store.publish(&snapshot(9, 1, RuntimeSampleState::Fresh), 10).unwrap().samples[0].value, 800.0);
        assert_eq!(store.publish(&snapshot(9, 1, RuntimeSampleState::Fresh), 11), Err(LiveChannelStoreError::DuplicateSequence));
        assert_eq!(store.publish(&snapshot(9, 2, RuntimeSampleState::Fresh), 9), Err(LiveChannelStoreError::ClockRegression));
        assert_eq!(store.latest().unwrap().sequence, 1);
    }

    #[test]
    fn timeout_and_disconnect_never_keep_valid_live_readings() {
        let mut store = LiveChannelStore::new(3).unwrap();
        store.publish(&snapshot(3, 1, RuntimeSampleState::Fresh), 100).unwrap();
        assert!(!store.expire(109, 10));
        assert!(store.expire(110, 10));
        assert_eq!(store.latest().unwrap().validity, RuntimeSampleState::Stale);
        store.publish(&snapshot(3, 2, RuntimeSampleState::Fresh), 120).unwrap();
        store.disconnect();
        assert_eq!(store.latest().unwrap().samples[0].validity, RuntimeSampleState::Stale);
        assert_eq!(store.publish(&snapshot(3, 3, RuntimeSampleState::Fresh), 130), Err(LiveChannelStoreError::SessionClosed));
    }

    #[test]
    fn invalid_and_duplicate_data_fail_without_overwriting_last_frame() {
        let mut store = LiveChannelStore::new(1).unwrap();
        store.publish(&snapshot(1, 1, RuntimeSampleState::Fresh), 10).unwrap();
        let mut bad = snapshot(1, 2, RuntimeSampleState::Fresh);
        bad.samples.push(bad.samples[0].clone());
        assert_eq!(store.publish(&bad, 11), Err(LiveChannelStoreError::DuplicateChannel));
        bad.samples.pop();
        bad.samples[0].value = f32::NAN;
        assert_eq!(store.publish(&bad, 11), Err(LiveChannelStoreError::NotFresh));
        assert_eq!(store.latest().unwrap().sequence, 1);
        store.mark_invalid();
        assert_eq!(store.latest().unwrap().validity, RuntimeSampleState::Invalid);
    }
}
