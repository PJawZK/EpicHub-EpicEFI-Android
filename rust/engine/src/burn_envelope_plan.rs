use crate::compiled_profile_bundle::CompiledEcuProfileBundle;
use crate::ms_envelope_codec::{EnvelopeCodecError, decode_response, encode_request};
use crate::page_burn_planner::PageBurnPlan;

/// EpicEFI/rusEFI firmware returns one status byte and no payload for a successful Burn.
/// The MS envelope adds a 2-byte length prefix and 4-byte CRC footer.
pub const BURN_RESPONSE_FRAME_SIZE: usize = 7;
/// `TS_RESPONSE_BURN_OK` from the firmware protocol definition.
pub const BURN_OK_STATUS: u8 = 0x04;

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct FramedPageBurnPlan {
    /// TunerStudio definition page number. Definition pages are 1-based.
    pub page: usize,
    pub request_frame: Vec<u8>,
    pub expected_response_size: usize,
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct VerifiedPageBurnAck {
    pub page: usize,
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub enum BurnEnvelopeError {
    Envelope(EnvelopeCodecError),
    ResponseFrameSizeMismatch { expected: usize, actual: usize },
    UnexpectedStatus(u8),
    UnexpectedPayloadLength { expected: usize, actual: usize },
}

impl From<EnvelopeCodecError> for BurnEnvelopeError {
    fn from(value: EnvelopeCodecError) -> Self {
        Self::Envelope(value)
    }
}

pub fn frame_page_burn(
    bundle: &CompiledEcuProfileBundle,
    plan: &PageBurnPlan,
) -> Result<FramedPageBurnPlan, BurnEnvelopeError> {
    let request_frame = encode_request(bundle, &plan.request_body)?;
    Ok(FramedPageBurnPlan {
        page: plan.page,
        request_frame,
        expected_response_size: BURN_RESPONSE_FRAME_SIZE,
    })
}

/// Verifies only that the ECU accepted the Burn command using the firmware's Burn-specific status.
///
/// This acknowledgement is deliberately not persistence proof. A caller must still perform the
/// definition-owned post-Burn CRC32 check against the exact guarded source-page bytes before it may
/// claim that nonvolatile storage matches the intended page.
pub fn verify_page_burn_response(
    bundle: &CompiledEcuProfileBundle,
    plan: &FramedPageBurnPlan,
    response_frame: &[u8],
) -> Result<VerifiedPageBurnAck, BurnEnvelopeError> {
    if response_frame.len() != plan.expected_response_size {
        return Err(BurnEnvelopeError::ResponseFrameSizeMismatch {
            expected: plan.expected_response_size,
            actual: response_frame.len(),
        });
    }

    let response = decode_response(bundle, response_frame)?;
    if response.status != BURN_OK_STATUS {
        return Err(BurnEnvelopeError::UnexpectedStatus(response.status));
    }
    if !response.payload.is_empty() {
        return Err(BurnEnvelopeError::UnexpectedPayloadLength {
            expected: 0,
            actual: response.payload.len(),
        });
    }

    Ok(VerifiedPageBurnAck { page: plan.page })
}

#[cfg(test)]
mod tests {
    use std::collections::{HashMap, HashSet};

    use super::*;
    use crate::compiled_profile_bundle::{OwnedDefinitionBundleImport, compile_definition_bundle};
    use crate::ms_envelope_codec::crc32_ieee;
    use crate::page_burn_planner::plan_page_burn;

    fn bundle(envelope: &str) -> CompiledEcuProfileBundle {
        let definition = format!(
            r#"
[TunerStudio]
queryCommand = "S"
signature = "rusEFI burn-envelope.test.1"

[Constants]
messageEnvelopeFormat = {envelope}
endianness = little
nPages = 1
pageSize = 64
pageIdentifier = "\x00\x00"
pageReadCommand = "R%2o%2c"
burnCommand = "B"
"#,
        );
        compile_definition_bundle(&OwnedDefinitionBundleImport {
            profile_name: "burn-envelope".to_string(),
            profile_generation: 1,
            definition_fingerprint: None,
            root_definition: definition,
            enabled_symbols: HashSet::new(),
            includes: HashMap::new(),
            semantic_tables: Vec::new(),
        })
        .unwrap()
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
    fn burn_body_is_framed_by_selected_bundle_envelope() {
        let bundle = bundle("msEnvelope_1.0");
        let burn = plan_page_burn(&bundle, 1).unwrap();
        let framed = frame_page_burn(&bundle, &burn).unwrap();

        assert_eq!(framed.page, 1);
        assert_eq!(framed.expected_response_size, BURN_RESPONSE_FRAME_SIZE);
        assert_eq!(
            &framed.request_frame[..2],
            &(burn.request_body.len() as u16).to_be_bytes()
        );
        assert_eq!(
            &framed.request_frame[2..2 + burn.request_body.len()],
            burn.request_body.as_slice()
        );
        assert_eq!(
            &framed.request_frame[2 + burn.request_body.len()..],
            &crc32_ieee(&burn.request_body).to_be_bytes()
        );
    }

    #[test]
    fn firmware_burn_ok_status_with_empty_payload_verifies_ack_only() {
        let bundle = bundle("msEnvelope_1.0");
        let burn = plan_page_burn(&bundle, 1).unwrap();
        let framed = frame_page_burn(&bundle, &burn).unwrap();
        let response = response_frame(BURN_OK_STATUS, &[]);

        assert_eq!(
            verify_page_burn_response(&bundle, &framed, &response),
            Ok(VerifiedPageBurnAck { page: 1 })
        );
    }

    #[test]
    fn normal_command_ok_is_not_burn_success() {
        let bundle = bundle("msEnvelope_1.0");
        let burn = plan_page_burn(&bundle, 1).unwrap();
        let framed = frame_page_burn(&bundle, &burn).unwrap();
        let response = response_frame(0x00, &[]);

        assert_eq!(
            verify_page_burn_response(&bundle, &framed, &response),
            Err(BurnEnvelopeError::UnexpectedStatus(0x00))
        );
    }

    #[test]
    fn burn_success_payload_must_be_empty() {
        let bundle = bundle("msEnvelope_1.0");
        let burn = plan_page_burn(&bundle, 1).unwrap();
        let framed = frame_page_burn(&bundle, &burn).unwrap();
        let response = response_frame(BURN_OK_STATUS, &[0x55]);

        assert_eq!(
            verify_page_burn_response(&bundle, &framed, &response),
            Err(BurnEnvelopeError::ResponseFrameSizeMismatch {
                expected: BURN_RESPONSE_FRAME_SIZE,
                actual: BURN_RESPONSE_FRAME_SIZE + 1,
            })
        );
    }

    #[test]
    fn malformed_or_crc_bad_response_fails_closed() {
        let bundle = bundle("msEnvelope_1.0");
        let burn = plan_page_burn(&bundle, 1).unwrap();
        let framed = frame_page_burn(&bundle, &burn).unwrap();
        let mut response = response_frame(BURN_OK_STATUS, &[]);
        *response.last_mut().unwrap() ^= 0x01;

        assert!(matches!(
            verify_page_burn_response(&bundle, &framed, &response),
            Err(BurnEnvelopeError::Envelope(
                EnvelopeCodecError::CrcMismatch { .. }
            ))
        ));
    }

    #[test]
    fn unsupported_envelope_fails_before_burn_request_frame_exists() {
        let bundle = bundle("futureEnvelope_2.0");
        let burn = plan_page_burn(&bundle, 1).unwrap();

        assert_eq!(
            frame_page_burn(&bundle, &burn),
            Err(BurnEnvelopeError::Envelope(
                EnvelopeCodecError::UnsupportedEnvelope("futureEnvelope_2.0".to_string())
            ))
        );
    }
}
