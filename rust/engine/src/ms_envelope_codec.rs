use crate::compiled_profile_bundle::CompiledEcuProfileBundle;

const MS_ENVELOPE_1_0: &str = "msEnvelope_1.0";

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct DecodedResponse {
    pub status: u8,
    pub payload: Vec<u8>,
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub enum EnvelopeCodecError {
    UnsupportedEnvelope(String),
    EmptyRequestBody,
    RequestBodyTooLarge(usize),
    ResponseTooShort(usize),
    ResponseLengthMismatch { declared: usize, actual: usize },
    ResponseBodyTooLarge(usize),
    ResponseMissingStatus,
    CrcMismatch { expected: u32, actual: u32 },
}

pub fn encode_request(
    bundle: &CompiledEcuProfileBundle,
    request_body: &[u8],
) -> Result<Vec<u8>, EnvelopeCodecError> {
    require_ms_envelope(bundle)?;
    encode_ms_envelope_request(request_body)
}

pub fn decode_response(
    bundle: &CompiledEcuProfileBundle,
    frame: &[u8],
) -> Result<DecodedResponse, EnvelopeCodecError> {
    require_ms_envelope(bundle)?;
    decode_ms_envelope_response(frame)
}

pub fn encode_ms_envelope_request(request_body: &[u8]) -> Result<Vec<u8>, EnvelopeCodecError> {
    if request_body.is_empty() {
        return Err(EnvelopeCodecError::EmptyRequestBody);
    }
    let body_len = u16::try_from(request_body.len())
        .map_err(|_| EnvelopeCodecError::RequestBodyTooLarge(request_body.len()))?;
    let crc = crc32_ieee(request_body);

    let mut frame = Vec::with_capacity(2 + request_body.len() + 4);
    frame.extend_from_slice(&body_len.to_be_bytes());
    frame.extend_from_slice(request_body);
    frame.extend_from_slice(&crc.to_be_bytes());
    Ok(frame)
}

pub fn decode_ms_envelope_response(frame: &[u8]) -> Result<DecodedResponse, EnvelopeCodecError> {
    if frame.len() < 7 {
        return Err(EnvelopeCodecError::ResponseTooShort(frame.len()));
    }

    let declared = u16::from_be_bytes([frame[0], frame[1]]) as usize;
    if declared == 0 {
        return Err(EnvelopeCodecError::ResponseMissingStatus);
    }
    let expected_total = 2usize
        .checked_add(declared)
        .and_then(|size| size.checked_add(4))
        .ok_or(EnvelopeCodecError::ResponseBodyTooLarge(declared))?;
    if frame.len() != expected_total {
        return Err(EnvelopeCodecError::ResponseLengthMismatch {
            declared,
            actual: frame.len().saturating_sub(6),
        });
    }

    let body = &frame[2..2 + declared];
    let crc_offset = 2 + declared;
    let expected_crc = u32::from_be_bytes([
        frame[crc_offset],
        frame[crc_offset + 1],
        frame[crc_offset + 2],
        frame[crc_offset + 3],
    ]);
    let actual_crc = crc32_ieee(body);
    if actual_crc != expected_crc {
        return Err(EnvelopeCodecError::CrcMismatch {
            expected: expected_crc,
            actual: actual_crc,
        });
    }

    Ok(DecodedResponse {
        status: body[0],
        payload: body[1..].to_vec(),
    })
}

fn require_ms_envelope(bundle: &CompiledEcuProfileBundle) -> Result<(), EnvelopeCodecError> {
    let envelope = &bundle.profile.definition().message_envelope_format;
    if envelope != MS_ENVELOPE_1_0 {
        return Err(EnvelopeCodecError::UnsupportedEnvelope(envelope.clone()));
    }
    Ok(())
}

pub fn crc32_ieee(bytes: &[u8]) -> u32 {
    let mut crc = 0xffff_ffffu32;
    for byte in bytes {
        crc ^= u32::from(*byte);
        for _ in 0..8 {
            let mask = (crc & 1).wrapping_neg();
            crc = (crc >> 1) ^ (0xedb8_8320 & mask);
        }
    }
    !crc
}

#[cfg(test)]
mod tests {
    use std::collections::{HashMap, HashSet};

    use super::*;
    use crate::compiled_profile_bundle::{OwnedDefinitionBundleImport, compile_definition_bundle};

    fn bundle(envelope: &str) -> CompiledEcuProfileBundle {
        let definition = format!(
            r#"
[TunerStudio]
queryCommand = "S"
signature = "rusEFI envelope-codec.test.1"

[Constants]
messageEnvelopeFormat = {envelope}
endianness = little
nPages = 1
pageSize = 64
pageIdentifier = "\x00\x00"
pageReadCommand = "R%2o%2c"
"#,
        );
        compile_definition_bundle(&OwnedDefinitionBundleImport {
            profile_name: "envelope-codec".to_string(),
            profile_generation: 1,
            definition_fingerprint: None,
            root_definition: definition,
            enabled_symbols: HashSet::new(),
            includes: HashMap::new(),
            semantic_tables: Vec::new(),
        })
        .unwrap()
    }

    #[test]
    fn crc32_matches_standard_ieee_vector() {
        assert_eq!(crc32_ieee(b"123456789"), 0xcbf4_3926);
        assert_eq!(crc32_ieee(b""), 0x0000_0000);
    }

    #[test]
    fn request_frame_uses_big_endian_length_and_crc_over_body_only() {
        let body = [b'R', 0x00, 0x01, 0x02, 0x03];
        let frame = encode_ms_envelope_request(&body).unwrap();

        assert_eq!(&frame[..2], &[0x00, 0x05]);
        assert_eq!(&frame[2..7], &body);
        assert_eq!(
            &frame[7..],
            &crc32_ieee(&body).to_be_bytes(),
            "CRC must cover request body only"
        );
    }

    #[test]
    fn response_decoder_returns_status_separately_from_payload() {
        let body = [0x00, 0x10, 0x20, 0x30];
        let mut frame = Vec::new();
        frame.extend_from_slice(&(body.len() as u16).to_be_bytes());
        frame.extend_from_slice(&body);
        frame.extend_from_slice(&crc32_ieee(&body).to_be_bytes());

        assert_eq!(
            decode_ms_envelope_response(&frame).unwrap(),
            DecodedResponse {
                status: 0x00,
                payload: vec![0x10, 0x20, 0x30],
            }
        );
    }

    #[test]
    fn response_crc_includes_status_and_payload_but_not_length() {
        let body = [0x7f, 0xaa, 0xbb];
        let mut frame = Vec::new();
        frame.extend_from_slice(&(body.len() as u16).to_be_bytes());
        frame.extend_from_slice(&body);
        frame.extend_from_slice(&crc32_ieee(&body).to_be_bytes());
        *frame.last_mut().unwrap() ^= 0x01;

        assert!(matches!(
            decode_ms_envelope_response(&frame),
            Err(EnvelopeCodecError::CrcMismatch { .. })
        ));
    }

    #[test]
    fn malformed_response_length_fails_closed() {
        let body = [0x00, 0x10, 0x20];
        let mut frame = Vec::new();
        frame.extend_from_slice(&4u16.to_be_bytes());
        frame.extend_from_slice(&body);
        frame.extend_from_slice(&crc32_ieee(&body).to_be_bytes());

        assert_eq!(
            decode_ms_envelope_response(&frame),
            Err(EnvelopeCodecError::ResponseLengthMismatch {
                declared: 4,
                actual: 3,
            })
        );
    }

    #[test]
    fn zero_length_response_cannot_hide_missing_status() {
        let mut frame = Vec::new();
        frame.extend_from_slice(&0u16.to_be_bytes());
        frame.extend_from_slice(&0u32.to_be_bytes());
        frame.push(0);

        assert_eq!(
            decode_ms_envelope_response(&frame),
            Err(EnvelopeCodecError::ResponseMissingStatus)
        );
    }

    #[test]
    fn empty_or_oversized_request_bodies_are_rejected() {
        assert_eq!(
            encode_ms_envelope_request(&[]),
            Err(EnvelopeCodecError::EmptyRequestBody)
        );
        let oversized = vec![0u8; u16::MAX as usize + 1];
        assert_eq!(
            encode_ms_envelope_request(&oversized),
            Err(EnvelopeCodecError::RequestBodyTooLarge(65_536))
        );
    }

    #[test]
    fn bundle_envelope_name_is_authority() {
        let supported = bundle("msEnvelope_1.0");
        assert!(encode_request(&supported, b"S").is_ok());

        let unsupported = bundle("futureEnvelope_2.0");
        assert_eq!(
            encode_request(&unsupported, b"S"),
            Err(EnvelopeCodecError::UnsupportedEnvelope(
                "futureEnvelope_2.0".to_string()
            ))
        );
    }
}
