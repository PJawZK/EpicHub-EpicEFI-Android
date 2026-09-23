use crate::channel_store::{ChannelSample, ChannelStore};

pub const RUNTIME_SNAPSHOT_ABI_VERSION: u32 = 1;
pub const RUNTIME_SNAPSHOT_MAX_CHANNELS: usize = 4;
pub const RUNTIME_SNAPSHOT_HEADER_SIZE: usize = 48;
pub const RUNTIME_SNAPSHOT_CHANNEL_SIZE: usize = 12;
pub const RUNTIME_SNAPSHOT_SIZE: usize =
    RUNTIME_SNAPSHOT_HEADER_SIZE + (RUNTIME_SNAPSHOT_CHANNEL_SIZE * RUNTIME_SNAPSHOT_MAX_CHANNELS);

pub const RUNTIME_SOURCE_SYNTHETIC: u32 = 1;
pub const RUNTIME_FLAG_SOURCE_VALID: u32 = 1 << 0;

#[derive(Debug, Clone, PartialEq)]
pub struct RuntimeSnapshot<'a> {
    pub sequence: u64,
    pub monotonic_nanos: u64,
    pub session_id: u64,
    pub source_kind: u32,
    pub flags: u32,
    pub channels: &'a [ChannelSample],
}

impl<'a> RuntimeSnapshot<'a> {
    pub fn from_channel_store(
        sequence: u64,
        monotonic_nanos: u64,
        session_id: u64,
        store: &'a ChannelStore,
    ) -> Self {
        Self {
            sequence,
            monotonic_nanos,
            session_id,
            source_kind: RUNTIME_SOURCE_SYNTHETIC,
            flags: RUNTIME_FLAG_SOURCE_VALID,
            channels: store.samples(),
        }
    }

    pub fn encode(&self) -> [u8; RUNTIME_SNAPSHOT_SIZE] {
        let mut bytes = [0_u8; RUNTIME_SNAPSHOT_SIZE];
        let channel_count = self.channels.len().min(RUNTIME_SNAPSHOT_MAX_CHANNELS);

        write_u32(&mut bytes, 0, RUNTIME_SNAPSHOT_ABI_VERSION);
        write_u32(&mut bytes, 4, RUNTIME_SNAPSHOT_SIZE as u32);
        write_u64(&mut bytes, 8, self.sequence);
        write_u64(&mut bytes, 16, self.monotonic_nanos);
        write_u64(&mut bytes, 24, self.session_id);
        write_u32(&mut bytes, 32, self.source_kind);
        write_u32(&mut bytes, 36, self.flags);
        write_u32(&mut bytes, 40, channel_count as u32);
        write_u32(&mut bytes, 44, 0);

        for (index, sample) in self.channels.iter().take(channel_count).enumerate() {
            let offset = RUNTIME_SNAPSHOT_HEADER_SIZE + (index * RUNTIME_SNAPSHOT_CHANNEL_SIZE);
            write_u32(&mut bytes, offset, sample.id.0);
            write_u32(&mut bytes, offset + 4, sample.validity as u32);
            write_u32(&mut bytes, offset + 8, sample.value.to_bits());
        }

        bytes
    }
}

fn write_u32(target: &mut [u8], offset: usize, value: u32) {
    target[offset..offset + 4].copy_from_slice(&value.to_le_bytes());
}

fn write_u64(target: &mut [u8], offset: usize, value: u64) {
    target[offset..offset + 8].copy_from_slice(&value.to_le_bytes());
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::channel_store::{
        SYNTHETIC_AFR, SYNTHETIC_ENGINE_SPEED, SYNTHETIC_MANIFOLD_PRESSURE,
    };

    fn read_u32(bytes: &[u8], offset: usize) -> u32 {
        u32::from_le_bytes(bytes[offset..offset + 4].try_into().unwrap())
    }

    fn read_u64(bytes: &[u8], offset: usize) -> u64 {
        u64::from_le_bytes(bytes[offset..offset + 8].try_into().unwrap())
    }

    #[test]
    fn snapshot_layout_is_versioned_little_endian_and_fixed_size() {
        let store = ChannelStore::new_synthetic();
        let snapshot = RuntimeSnapshot::from_channel_store(7, 11, 13, &store);
        let bytes = snapshot.encode();

        assert_eq!(bytes.len(), RUNTIME_SNAPSHOT_SIZE);
        assert_eq!(read_u32(&bytes, 0), RUNTIME_SNAPSHOT_ABI_VERSION);
        assert_eq!(read_u32(&bytes, 4), RUNTIME_SNAPSHOT_SIZE as u32);
        assert_eq!(read_u64(&bytes, 8), 7);
        assert_eq!(read_u64(&bytes, 16), 11);
        assert_eq!(read_u64(&bytes, 24), 13);
        assert_eq!(read_u32(&bytes, 32), RUNTIME_SOURCE_SYNTHETIC);
        assert_eq!(read_u32(&bytes, 36), RUNTIME_FLAG_SOURCE_VALID);
        assert_eq!(read_u32(&bytes, 40), 3);
        assert_eq!(read_u32(&bytes, 48), SYNTHETIC_ENGINE_SPEED.0);
        assert_eq!(read_u32(&bytes, 60), SYNTHETIC_MANIFOLD_PRESSURE.0);
        assert_eq!(read_u32(&bytes, 72), SYNTHETIC_AFR.0);
    }

    #[test]
    fn unused_channel_slot_is_zero_filled() {
        let store = ChannelStore::new_synthetic();
        let snapshot = RuntimeSnapshot::from_channel_store(1, 2, 3, &store);
        let bytes = snapshot.encode();
        let unused_offset = RUNTIME_SNAPSHOT_HEADER_SIZE + (3 * RUNTIME_SNAPSHOT_CHANNEL_SIZE);

        assert!(bytes[unused_offset..].iter().all(|byte| *byte == 0));
    }
}
