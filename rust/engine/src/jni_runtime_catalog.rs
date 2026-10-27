//! Versioned, registry-owned runtime metadata ABI. No transport access or UI publication.
use std::collections::HashSet;
use std::panic::{AssertUnwindSafe, catch_unwind};

use jni::JNIEnv;
use jni::objects::{JByteBuffer, JObject};
use jni::sys::{jint, jlong};

use crate::profile_registry_store::runtime_catalog_from_registry;
use crate::runtime_bound_live_session::BoundRuntimeIdentity;
use crate::runtime_channel_decode::{MAX_RUNTIME_CHANNELS, RuntimeValueType};
use crate::runtime_definition_catalog::NamedRuntimeChannel;

const CATALOG_CAPACITY: usize = 131_072;
const HEADER_BYTES: usize = 40;
const ENTRY_BYTES: usize = 12;
const MAX_NAME_BYTES: usize = 96;
const MAX_UNITS_BYTES: usize = 64;
const ERR_ARGUMENT: jint = -1;
const ERR_DIRECT_BUFFER: jint = -2;
const ERR_AUTHORITY: jint = -3;
const ERR_UNSUPPORTED: jint = -4;
const ERR_PANIC: jint = -255;

fn type_metadata(value_type: RuntimeValueType) -> (u8, u8, u8) {
    match value_type {
        RuntimeValueType::U8 => (1, 0, 0),
        RuntimeValueType::S8 => (2, 0, 0),
        RuntimeValueType::U16 => (3, 0, 0),
        RuntimeValueType::S16 => (4, 0, 0),
        RuntimeValueType::U32 => (5, 0, 0),
        RuntimeValueType::S32 => (6, 0, 0),
        RuntimeValueType::F32 => (7, 0, 0),
        RuntimeValueType::BitsU8 { first, last } => (8, first, last),
        RuntimeValueType::BitsU16 { first, last } => (9, first, last),
        RuntimeValueType::BitsU32 { first, last } => (10, first, last),
    }
}

fn encode_catalog(
    binding: &BoundRuntimeIdentity,
    channels: &[NamedRuntimeChannel],
) -> Option<Vec<u8>> {
    encode_catalog_data(
        binding.session_epoch(),
        binding.profile_generation(),
        binding.definition_revision(),
        channels,
    )
}

fn encode_catalog_data(
    epoch: u64,
    generation: u64,
    revision: u64,
    channels: &[NamedRuntimeChannel],
) -> Option<Vec<u8>> {
    if epoch == 0
        || generation == 0
        || channels.is_empty()
        || channels.len() > MAX_RUNTIME_CHANNELS
    {
        return None;
    }

    let mut result = Vec::with_capacity(HEADER_BYTES + channels.len() * (ENTRY_BYTES + 32));
    result.extend_from_slice(b"EPCT");
    result.extend_from_slice(&1_u32.to_le_bytes());
    result.extend_from_slice(&epoch.to_le_bytes());
    result.extend_from_slice(&generation.to_le_bytes());
    result.extend_from_slice(&revision.to_le_bytes());
    result.extend_from_slice(&(channels.len() as u32).to_le_bytes());
    result.extend_from_slice(&0_u32.to_le_bytes()); // total length, backfilled

    let mut ids = HashSet::with_capacity(channels.len());
    let mut names = HashSet::with_capacity(channels.len());
    for channel in channels {
        let name = channel.name.as_bytes();
        let units = channel.units.as_bytes();
        if channel.id == 0 || !ids.insert(channel.id)
            || name.is_empty() || name.len() > MAX_NAME_BYTES || units.len() > MAX_UNITS_BYTES
            || !names.insert(channel.name.as_str())
            || !name.iter().all(|byte| byte.is_ascii_alphanumeric() || *byte == b'_')
            || channel.units.chars().any(char::is_control)
        {
            return None;
        }
        let (kind, first, last) = type_metadata(channel.value_type);
        let total = result.len().checked_add(ENTRY_BYTES + name.len() + units.len())?;
        if total > CATALOG_CAPACITY {
            return None;
        }
        result.extend_from_slice(&channel.id.to_le_bytes());
        result.push(kind);
        result.push(first);
        result.push(last);
        result.push(0); // reserved
        result.extend_from_slice(&(name.len() as u16).to_le_bytes());
        result.extend_from_slice(&(units.len() as u16).to_le_bytes());
        result.extend_from_slice(name);
        result.extend_from_slice(units);
    }
    let length = u32::try_from(result.len()).ok()?;
    result[36..40].copy_from_slice(&length.to_le_bytes());
    Some(result)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_epicefi_epichub_engine_NativeProfileRegistryBridge_runtimeChannelCatalog(
    env: JNIEnv,
    _object: JObject,
    handle: jlong,
    epoch: jlong,
    output: JByteBuffer,
) -> jint {
    catch_unwind(AssertUnwindSafe(|| {
        if handle <= 0 || epoch <= 0 {
            return ERR_ARGUMENT;
        }
        let capacity = match env.get_direct_buffer_capacity(&output) {
            Ok(capacity) if capacity >= CATALOG_CAPACITY => capacity,
            _ => return ERR_DIRECT_BUFFER,
        };
        let pointer = match env.get_direct_buffer_address(&output) {
            Ok(pointer) => pointer,
            Err(_) => return ERR_DIRECT_BUFFER,
        };
        let (binding, channels) = match runtime_catalog_from_registry(handle, epoch as u64) {
            Ok(value) => value,
            Err(_) => return ERR_AUTHORITY,
        };
        let encoded = match encode_catalog(&binding, &channels) {
            Some(value) => value,
            None => return ERR_UNSUPPORTED,
        };
        if encoded.len() > capacity {
            return ERR_DIRECT_BUFFER;
        }
        unsafe { std::slice::from_raw_parts_mut(pointer, encoded.len()).copy_from_slice(&encoded) };
        jint::try_from(encoded.len()).unwrap_or(ERR_UNSUPPORTED)
    }))
    .unwrap_or(ERR_PANIC)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn encode_exact_definition_metadata_in_bounded_epct_v1() {
        let entries = vec![
            NamedRuntimeChannel { id: 7, name: "rpm".into(), units: "RPM".into(),
                value_type: RuntimeValueType::U16 },
            NamedRuntimeChannel { id: 8, name: "clt".into(), units: "°C".into(),
                value_type: RuntimeValueType::S16 },
            NamedRuntimeChannel { id: 9, name: "limp".into(), units: String::new(),
                value_type: RuntimeValueType::BitsU8 { first: 2, last: 4 } },
        ];
        let encoded = encode_catalog_data(44, 800, 27, &entries).unwrap();
        assert_eq!(&encoded[..4], b"EPCT");
        assert_eq!(u32::from_le_bytes(encoded[4..8].try_into().unwrap()), 1);
        assert_eq!(u64::from_le_bytes(encoded[8..16].try_into().unwrap()), 44);
        assert_eq!(u64::from_le_bytes(encoded[16..24].try_into().unwrap()), 800);
        assert_eq!(u64::from_le_bytes(encoded[24..32].try_into().unwrap()), 27);
        assert_eq!(u32::from_le_bytes(encoded[32..36].try_into().unwrap()), 3);
        assert_eq!(u32::from_le_bytes(encoded[36..40].try_into().unwrap()) as usize, encoded.len());
        assert_eq!(&encoded[40..44], &7_u32.to_le_bytes());
        assert_eq!(encoded[44], 3); // U16
        assert_eq!(encoded[52..55], *b"rpm");
        assert!(encoded.windows(3).any(|chunk| chunk == "°C".as_bytes()));
        assert!(encoded.len() < CATALOG_CAPACITY);
    }

    #[test]
    fn invalid_metadata_never_yields_a_partial_catalog() {
        let good = NamedRuntimeChannel {
            id: 7, name: "rpm".into(), units: "RPM".into(),
            value_type: RuntimeValueType::U16,
        };
        assert!(encode_catalog_data(0, 800, 42, &[good.clone()]).is_none());
        assert!(encode_catalog_data(1, 0, 42, &[good.clone()]).is_none());
        assert!(encode_catalog_data(1, 800, 42, &[]).is_none());
        assert!(encode_catalog_data(1, 800, 42, &[good.clone(), good.clone()]).is_none());
        assert!(encode_catalog_data(1, 800, 42, &[NamedRuntimeChannel {
            name: "rpm".into(), id: 8, ..good.clone()
        }, good.clone()]).is_none());
        assert!(encode_catalog_data(1, 800, 42, &[NamedRuntimeChannel {
            name: "x".repeat(97), ..good.clone()
        }]).is_none());
        assert!(encode_catalog_data(1, 800, 42, &[NamedRuntimeChannel {
            units: "b\u{0000}ad".into(), ..good.clone()
        }]).is_none());
        assert!(encode_catalog_data(1, 800, 42, &[NamedRuntimeChannel {
            units: "u".repeat(65), ..good.clone()
        }]).is_none());
    }

    #[test]
    fn type_codes_preserve_signed_unsigned_float_and_bit_spans() {
        assert_eq!(type_metadata(RuntimeValueType::U16), (3, 0, 0));
        assert_eq!(type_metadata(RuntimeValueType::S32), (6, 0, 0));
        assert_eq!(type_metadata(RuntimeValueType::F32), (7, 0, 0));
        assert_eq!(
            type_metadata(RuntimeValueType::BitsU16 { first: 3, last: 5 }),
            (9, 3, 5)
        );
    }
}
