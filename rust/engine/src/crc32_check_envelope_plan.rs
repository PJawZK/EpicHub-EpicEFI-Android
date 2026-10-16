use crate::compiled_profile_bundle::CompiledEcuProfileBundle;
use crate::ms_envelope_codec::{EnvelopeCodecError, crc32_ieee, decode_response, encode_request};
use crate::page_crc32_check_planner::PageCrc32CheckPlan;

pub const CRC32_CHECK_RESPONSE_FRAME_SIZE: usize = 11;
pub const CRC32_CHECK_OK_STATUS: u8 = 0x00;

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct FramedPageCrc32CheckPlan {
    /// TunerStudio definition page number. Definition pages are 1-based.
    pub page: usize,
    pub offset: usize,
    pub count: usize,
    pub request_frame: Vec<u8>,
    pub expected_response_size: usize,
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct VerifiedPageCrc32Check {
    pub page: usize,
    pub offset: usize,
    pub count: usize,
    pub checksum: u32,
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub enum Crc32CheckEnvelopeError {
    Envelope(EnvelopeCodecError),
    UnexpectedStatus(u8),
    UnexpectedPayloadLength { expected: usize, actual: usize },
    ExpectedBytesLengthMismatch { expected: usize, actual: usize },
    ChecksumMismatch { expected: u32, actual: u32 },
}

impl From<EnvelopeCodecError> for Crc32CheckEnvelopeError {
    fn from(value: EnvelopeCodecError) -> Self {
        Self::Envelope(value)
    }
}

pub fn frame_page_crc32_check(
    bundle: &CompiledEcuProfileBundle,
    plan: &PageCrc32CheckPlan,
) -> Result<FramedPageCrc32CheckPlan, Crc32CheckEnvelopeError> {
    let request_frame = encode_request(bundle, &plan.request_body)?;
    Ok(FramedPageCrc32CheckPlan {
        page: plan.page,
        offset: plan.offset,
        count: plan.count,
        request_frame,
        expected_response_size: CRC32_CHECK_RESPONSE_FRAME_SIZE,
    })
}

/// Verifies one CRC32-check response against the exact bytes the caller expects for the requested
/// range.
///
/// A successful result proves only that the ECU-reported checksum for this range matches those
/// bytes. It does not, by itself, prove that a Burn command completed or that nonvolatile storage
/// contains the same data.
pub fn verify_page_crc32_check_response(
    bundle: &CompiledEcuProfileBundle,
    plan: &PageCrc32CheckPlan,
    expected_bytes: &[u8],
    response_frame: &[u8],
) -> Result<VerifiedPageCrc32Check, Crc32CheckEnvelopeError> {
    if expected_bytes.len() != plan.count {
        return Err(Crc32CheckEnvelopeError::ExpectedBytesLengthMismatch {
            expected: plan.count,
            actual: expected_bytes.len(),
        });
    }

    let response = decode_response(bundle, response_frame)?;
    if response.status != CRC32_CHECK_OK_STATUS {
        return Err(Crc32CheckEnvelopeError::UnexpectedStatus(response.status));
    }
    if response.payload.len() != plan.expected_payload_bytes {
        return Err(Crc32CheckEnvelopeError::UnexpectedPayloadLength {
            expected: plan.expected_payload_bytes,
            actual: response.payload.len(),
        });
    }
    if response.payload.len() != 4 {
        return Err(Crc32CheckEnvelopeError::UnexpectedPayloadLength {
            expected: 4,
            actual: response.payload.len(),
        });
    }

    let actual = u32::from_be_bytes([
        response.payload[0],
        response.payload[1],
        response.payload[2],
        response.payload[3],
    ]);
    let expected = crc32_ieee(expected_bytes);
    if actual != expected {
        return Err(Crc32CheckEnvelopeError::ChecksumMismatch { expected, actual });
    }

    Ok(VerifiedPageCrc32Check {
        page: plan.page,
        offset: plan.offset,
        count: plan.count,
        checksum: actual,
    })
}

#[cfg(test)]
mod tests {
    use std::collections::{HashMap, HashSet};

    use super::*;
    use crate::compiled_profile_bundle::{OwnedDefinitionBundleImport, compile_definition_bundle};
    use crate::page_crc32_check_planner::plan_page_crc32_check;

    fn bundle(envelope: &str) -> CompiledEcuProfileBundle {
        let definition = format!(
            r#"
[TunerStudio]
queryCommand = "S"
signature = "rusEFI crc32-envelope.test.1"

[Constants]
messageEnvelopeFormat = {envelope}
endianness = little
nPages = 1
pageSize = 64
pageIdentifier = "\x00\x00"
pageReadCommand = "R%2o%2c"
crc32CheckCommand = "k%2o%2c"
"#,
        );
        compile_definition_bundle(&OwnedDefinitionBundleImport {
            profile_name: "crc32-envelope".to_string(),
            profile_generation: 1,
            definition_fingerprint: None,
            root_definition: definition,
            enabled_symbols: HashSet::new(),
            includes: HashMap::new(),
            semantic_tables: Vec::new(),
        })
        .unwrap()
    }

    fn plan(bundle: &CompiledEcuProfileBundle) -> PageCrc32CheckPlan {
        plan_page_crc32_check(bundle, 1, 8, 4).unwrap()
    }

    fn response_frame(status: u8, payload: &[u8]) -> Vec<u8> {
        let mut body = Vec::with_capacity(1 + payload.len());
        body.push(status);
        body.extend_from_slice(payload);
        let mut frame = Vec::with_capacity(2 + body.len() + 4);
        frame.extend_from_slice(&(body.len() as u16).to_be_bytes());
        frame.extend_from_slice(&body);
        frame.extend_from_slice(&crc32_ieee(&body).to_be_bytes());
        frame
    }

    #[test]
    fn crc_check_body_is_framed_by_selected_bundle_envelope() {
        let bundle = bundle("msEnvelope_1.0");
        let plan = plan(&bundle);
        let framed = frame_page_crc32_check(&bundle, &plan).unwrap();

        assert_eq!(framed.page, 1);
        assert_eq!(framed.offset, 8);
        assert_eq!(framed.count, 4);
        assert_eq!(
            framed.expected_response_size,
            CRC32_CHECK_RESPONSE_FRAME_SIZE
        );
        assert_eq!(&framed.request_frame[..2], &[0x00, 0x05]);
        assert_eq!(&framed.request_frame[2..7], plan.request_body.as_slice());
        assert_eq!(
            &framed.request_frame[7..],
            &crc32_ieee(&plan.request_body).to_be_bytes()
        );
    }

    #[test]
    fn matching_big_endian_crc_payload_verifies_exact_expected_bytes() {
        let bundle = bundle("msEnvelope_1.0");
        let plan = plan(&bundle);
        let expected_bytes = [0x10, 0x20, 0x30, 0x40];
        let checksum = crc32_ieee(&expected_bytes);
        let frame = response_frame(CRC32_CHECK_OK_STATUS, &checksum.to_be_bytes());

        assert_eq!(
            verify_page_crc32_check_response(&bundle, &plan, &expected_bytes, &frame),
            Ok(VerifiedPageCrc32Check {
                page: 1,
                offset: 8,
                count: 4,
                checksum,
            })
        );
    }

    #[test]
    fn mismatched_crc_never_verifies_range() {
        let bundle = bundle("msEnvelope_1.0");
        let plan = plan(&bundle);
        let expected_bytes = [0x10, 0x20, 0x30, 0x40];
        let expected = crc32_ieee(&expected_bytes);
        let actual = expected ^ 0x0000_0001;
        let frame = response_frame(CRC32_CHECK_OK_STATUS, &actual.to_be_bytes());

        assert_eq!(
            verify_page_crc32_check_response(&bundle, &plan, &expected_bytes, &frame),
            Err(Crc32CheckEnvelopeError::ChecksumMismatch { expected, actual })
        );
    }

    #[test]
    fn non_success_status_never_verifies_crc() {
        let bundle = bundle("msEnvelope_1.0");
        let plan = plan(&bundle);
        let expected_bytes = [0x10, 0x20, 0x30, 0x40];
        let checksum = crc32_ieee(&expected_bytes);
        let frame = response_frame(0x7f, &checksum.to_be_bytes());

        assert_eq!(
            verify_page_crc32_check_response(&bundle, &plan, &expected_bytes, &frame),
            Err(Crc32CheckEnvelopeError::UnexpectedStatus(0x7f))
        );
    }

    #[test]
    fn payload_must_be_exactly_four_crc_bytes() {
        let bundle = bundle("msEnvelope_1.0");
        let plan = plan(&bundle);
        let expected_bytes = [0x10, 0x20, 0x30, 0x40];
        let frame = response_frame(CRC32_CHECK_OK_STATUS, &[0x10, 0x20, 0x30]);

        assert_eq!(
            verify_page_crc32_check_response(&bundle, &plan, &expected_bytes, &frame),
            Err(Crc32CheckEnvelopeError::UnexpectedPayloadLength {
                expected: 4,
                actual: 3,
            })
        );
    }

    #[test]
    fn caller_must_supply_exact_requested_range_bytes() {
        let bundle = bundle("msEnvelope_1.0");
        let plan = plan(&bundle);
        let frame = response_frame(CRC32_CHECK_OK_STATUS, &[0, 0, 0, 0]);

        assert_eq!(
            verify_page_crc32_check_response(&bundle, &plan, &[0x10, 0x20], &frame),
            Err(Crc32CheckEnvelopeError::ExpectedBytesLengthMismatch {
                expected: 4,
                actual: 2,
            })
        );
    }

    #[test]
    fn envelope_crc_failure_is_preserved() {
        let bundle = bundle("msEnvelope_1.0");
        let plan = plan(&bundle);
        let expected_bytes = [0x10, 0x20, 0x30, 0x40];
        let checksum = crc32_ieee(&expected_bytes);
        let mut frame = response_frame(CRC32_CHECK_OK_STATUS, &checksum.to_be_bytes());
        *frame.last_mut().unwrap() ^= 0x01;

        assert!(matches!(
            verify_page_crc32_check_response(&bundle, &plan, &expected_bytes, &frame),
            Err(Crc32CheckEnvelopeError::Envelope(
                EnvelopeCodecError::CrcMismatch { .. }
            ))
        ));
    }

    #[test]
    fn unsupported_envelope_fails_before_crc_request_frame_exists() {
        let bundle = bundle("futureEnvelope_2.0");
        let plan = plan(&bundle);

        assert_eq!(
            frame_page_crc32_check(&bundle, &plan),
            Err(Crc32CheckEnvelopeError::Envelope(
                EnvelopeCodecError::UnsupportedEnvelope("futureEnvelope_2.0".to_string())
            ))
        );
    }
}
