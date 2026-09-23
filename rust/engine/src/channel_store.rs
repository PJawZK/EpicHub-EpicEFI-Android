#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub struct ChannelId(pub u32);

pub const SYNTHETIC_ENGINE_SPEED: ChannelId = ChannelId(0xFFFF_0001);
pub const SYNTHETIC_MANIFOLD_PRESSURE: ChannelId = ChannelId(0xFFFF_0002);
pub const SYNTHETIC_AFR: ChannelId = ChannelId(0xFFFF_0003);

#[repr(u32)]
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
#[expect(
    dead_code,
    reason = "ABI v1 reserves Invalid/Stale now; the synthetic foundation source emits only Valid samples"
)]
pub enum ChannelValidity {
    Invalid = 0,
    Valid = 1,
    Stale = 2,
}

#[derive(Clone, Copy, Debug, PartialEq)]
pub struct ChannelSample {
    pub id: ChannelId,
    pub validity: ChannelValidity,
    pub value: f32,
}

pub struct ChannelStore {
    samples: [ChannelSample; 3],
}

impl ChannelStore {
    pub fn new_synthetic() -> Self {
        Self {
            samples: [
                ChannelSample {
                    id: SYNTHETIC_ENGINE_SPEED,
                    validity: ChannelValidity::Valid,
                    value: 800.0,
                },
                ChannelSample {
                    id: SYNTHETIC_MANIFOLD_PRESSURE,
                    validity: ChannelValidity::Valid,
                    value: 50.0,
                },
                ChannelSample {
                    id: SYNTHETIC_AFR,
                    validity: ChannelValidity::Valid,
                    value: 14.7,
                },
            ],
        }
    }

    pub fn update_synthetic(&mut self, elapsed_seconds: f32) {
        let phase = elapsed_seconds * 0.9;
        self.samples[0].value = 950.0 + (phase.sin() * 150.0);
        self.samples[1].value = 52.0 + ((phase * 0.7).sin() * 7.0);
        self.samples[2].value = 14.7 + ((phase * 1.3).sin() * 0.35);
    }

    pub fn samples(&self) -> &[ChannelSample] {
        &self.samples
    }
}

impl Default for ChannelStore {
    fn default() -> Self {
        Self::new_synthetic()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn synthetic_store_has_stable_ids_and_valid_samples() {
        let store = ChannelStore::new_synthetic();
        let samples = store.samples();

        assert_eq!(samples.len(), 3);
        assert_eq!(samples[0].id, SYNTHETIC_ENGINE_SPEED);
        assert_eq!(samples[1].id, SYNTHETIC_MANIFOLD_PRESSURE);
        assert_eq!(samples[2].id, SYNTHETIC_AFR);
        assert!(
            samples
                .iter()
                .all(|sample| sample.validity == ChannelValidity::Valid)
        );
    }

    #[test]
    fn synthetic_update_changes_values_without_changing_identity() {
        let mut store = ChannelStore::new_synthetic();
        let before = store.samples().to_vec();

        store.update_synthetic(1.0);
        let after = store.samples();

        assert_eq!(before[0].id, after[0].id);
        assert_eq!(before[1].id, after[1].id);
        assert_eq!(before[2].id, after[2].id);
        assert_ne!(before[0].value, after[0].value);
    }
}
