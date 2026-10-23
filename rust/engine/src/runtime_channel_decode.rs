//! Definition-neutral, bounded runtime frame decoding.
//! Descriptors are supplied by a future compiled ECU runtime definition; none
//! of the values or offsets here assume a particular board or firmware.
use std::collections::HashSet;

pub const MAX_RUNTIME_FRAME_BYTES: usize = 65_536;
pub const MAX_RUNTIME_CHANNELS: usize = 1_024;

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum RuntimeEndian {
    Little,
    Big,
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum RuntimeValueType {
    U8,
    S8,
    U16,
    S16,
    U32,
    S32,
    F32,
}

impl RuntimeValueType {
    const fn width(self) -> usize {
        match self {
            Self::U8 | Self::S8 => 1,
            Self::U16 | Self::S16 => 2,
            Self::U32 | Self::S32 | Self::F32 => 4,
        }
    }
}

#[derive(Clone, Debug, PartialEq)]
pub struct RuntimeChannelDescriptor {
    pub id: u32,
    pub offset: usize,
    pub value_type: RuntimeValueType,
    pub endian: RuntimeEndian,
    pub scale: f64,
    pub translate: f64,
}

#[derive(Clone, Debug, PartialEq)]
pub enum RuntimeDecodeError {
    EmptyCatalog,
    TooManyChannels,
    EmptyFrame,
    OversizeFrame,
    DuplicateId(u32),
    InvalidId,
    InvalidScaling(u32),
    OffsetOutsideFrame(u32),
}

#[derive(Clone, Copy, Debug, PartialEq)]
pub struct DecodedRuntimeSample {
    pub id: u32,
    pub value: f32,
    pub valid: bool,
}

#[derive(Clone, Debug)]
pub struct CompiledRuntimeChannelCatalog {
    frame_length: usize,
    channels: Vec<RuntimeChannelDescriptor>,
}

impl CompiledRuntimeChannelCatalog {
    pub fn compile(
        frame_length: usize,
        channels: Vec<RuntimeChannelDescriptor>,
    ) -> Result<Self, RuntimeDecodeError> {
        if frame_length == 0 {
            return Err(RuntimeDecodeError::EmptyFrame);
        }
        if frame_length > MAX_RUNTIME_FRAME_BYTES {
            return Err(RuntimeDecodeError::OversizeFrame);
        }
        if channels.is_empty() {
            return Err(RuntimeDecodeError::EmptyCatalog);
        }
        if channels.len() > MAX_RUNTIME_CHANNELS {
            return Err(RuntimeDecodeError::TooManyChannels);
        }
        let mut ids = HashSet::with_capacity(channels.len());
        for channel in &channels {
            if channel.id == 0 {
                return Err(RuntimeDecodeError::InvalidId);
            }
            if !ids.insert(channel.id) {
                return Err(RuntimeDecodeError::DuplicateId(channel.id));
            }
            if !channel.scale.is_finite() || !channel.translate.is_finite() {
                return Err(RuntimeDecodeError::InvalidScaling(channel.id));
            }
            if channel
                .offset
                .checked_add(channel.value_type.width())
                .is_none_or(|end| end > frame_length)
            {
                return Err(RuntimeDecodeError::OffsetOutsideFrame(channel.id));
            }
        }
        Ok(Self {
            frame_length,
            channels,
        })
    }

    pub fn frame_length(&self) -> usize {
        self.frame_length
    }

    pub fn channel_count(&self) -> usize {
        self.channels.len()
    }

    /// Reject short/long frames atomically; never reuse an earlier value.
    /// Individual non-finite decoded values are explicitly invalid.
    pub fn decode(&self, frame: &[u8]) -> Option<Vec<DecodedRuntimeSample>> {
        if frame.len() != self.frame_length {
            return None;
        }
        Some(
            self.channels
                .iter()
                .map(|channel| {
                    let bytes = &frame[channel.offset..channel.offset + channel.value_type.width()];
                    let raw = match channel.value_type {
                        RuntimeValueType::U8 => f64::from(bytes[0]),
                        RuntimeValueType::S8 => f64::from(bytes[0] as i8),
                        RuntimeValueType::U16 => f64::from(match channel.endian {
                            RuntimeEndian::Little => u16::from_le_bytes([bytes[0], bytes[1]]),
                            RuntimeEndian::Big => u16::from_be_bytes([bytes[0], bytes[1]]),
                        }),
                        RuntimeValueType::S16 => f64::from(match channel.endian {
                            RuntimeEndian::Little => i16::from_le_bytes([bytes[0], bytes[1]]),
                            RuntimeEndian::Big => i16::from_be_bytes([bytes[0], bytes[1]]),
                        }),
                        RuntimeValueType::U32 => f64::from(match channel.endian {
                            RuntimeEndian::Little => u32::from_le_bytes(bytes.try_into().unwrap()),
                            RuntimeEndian::Big => u32::from_be_bytes(bytes.try_into().unwrap()),
                        }),
                        RuntimeValueType::S32 => f64::from(match channel.endian {
                            RuntimeEndian::Little => i32::from_le_bytes(bytes.try_into().unwrap()),
                            RuntimeEndian::Big => i32::from_be_bytes(bytes.try_into().unwrap()),
                        }),
                        RuntimeValueType::F32 => f64::from(match channel.endian {
                            RuntimeEndian::Little => f32::from_le_bytes(bytes.try_into().unwrap()),
                            RuntimeEndian::Big => f32::from_be_bytes(bytes.try_into().unwrap()),
                        }),
                    };
                    let value = raw * channel.scale + channel.translate;
                    let converted = value as f32;
                    DecodedRuntimeSample {
                        id: channel.id,
                        value: if converted.is_finite() {
                            converted
                        } else {
                            0.0
                        },
                        valid: converted.is_finite(),
                    }
                })
                .collect(),
        )
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn descriptor(
        id: u32,
        offset: usize,
        value_type: RuntimeValueType,
    ) -> RuntimeChannelDescriptor {
        RuntimeChannelDescriptor {
            id,
            offset,
            value_type,
            endian: RuntimeEndian::Little,
            scale: 1.0,
            translate: 0.0,
        }
    }

    #[test]
    fn decodes_mixed_unsigned_signed_float_and_big_endian() {
        let mut signed = descriptor(2, 2, RuntimeValueType::S16);
        signed.scale = 0.5;
        signed.translate = 3.0;
        let mut big = descriptor(3, 4, RuntimeValueType::U32);
        big.endian = RuntimeEndian::Big;
        let catalog = CompiledRuntimeChannelCatalog::compile(
            12,
            vec![
                descriptor(1, 0, RuntimeValueType::U16),
                signed,
                big,
                descriptor(4, 8, RuntimeValueType::F32),
            ],
        )
        .unwrap();
        let frame = [0xE8, 0x03, 0xFC, 0xFF, 0, 0, 1, 0, 0, 0, 0x20, 0x41];
        let result = catalog.decode(&frame).unwrap();
        assert_eq!(
            result.iter().map(|sample| sample.value).collect::<Vec<_>>(),
            vec![1000.0, 1.0, 256.0, 10.0]
        );
        assert!(result.iter().all(|sample| sample.valid));
    }

    #[test]
    fn rejects_duplicate_ids_bad_offsets_and_invalid_scaling() {
        assert_eq!(
            CompiledRuntimeChannelCatalog::compile(2, vec![]).unwrap_err(),
            RuntimeDecodeError::EmptyCatalog
        );
        assert_eq!(
            CompiledRuntimeChannelCatalog::compile(0, vec![descriptor(1, 0, RuntimeValueType::U8)])
                .unwrap_err(),
            RuntimeDecodeError::EmptyFrame
        );
        assert_eq!(
            CompiledRuntimeChannelCatalog::compile(
                2,
                vec![
                    descriptor(1, 0, RuntimeValueType::U8),
                    descriptor(1, 1, RuntimeValueType::U8)
                ]
            )
            .unwrap_err(),
            RuntimeDecodeError::DuplicateId(1)
        );
        assert_eq!(
            CompiledRuntimeChannelCatalog::compile(
                2,
                vec![descriptor(1, usize::MAX, RuntimeValueType::U32)]
            )
            .unwrap_err(),
            RuntimeDecodeError::OffsetOutsideFrame(1)
        );
        let mut invalid = descriptor(1, 0, RuntimeValueType::U8);
        invalid.scale = f64::NAN;
        assert_eq!(
            CompiledRuntimeChannelCatalog::compile(2, vec![invalid]).unwrap_err(),
            RuntimeDecodeError::InvalidScaling(1)
        );
    }

    #[test]
    fn bad_frame_does_not_partially_reuse_samples_and_nan_is_invalid() {
        let catalog = CompiledRuntimeChannelCatalog::compile(
            4,
            vec![descriptor(77, 0, RuntimeValueType::F32)],
        )
        .unwrap();
        assert!(catalog.decode(&[0, 0]).is_none());
        assert!(catalog.decode(&[0, 0, 0, 0, 0]).is_none());
        let result = catalog.decode(&f32::NAN.to_le_bytes()).unwrap();
        assert_eq!(result[0].id, 77);
        assert!(!result[0].valid);
        assert_eq!(result[0].value, 0.0);
    }

    #[test]
    fn supports_more_than_legacy_four_channels_without_truncation() {
        let channels = (1..=32)
            .map(|id| descriptor(id, (id - 1) as usize, RuntimeValueType::U8))
            .collect();
        let catalog = CompiledRuntimeChannelCatalog::compile(32, channels).unwrap();
        let result = catalog.decode(&[12; 32]).unwrap();
        assert_eq!(result.len(), 32);
        assert_eq!(result[31].id, 32);
    }
}
