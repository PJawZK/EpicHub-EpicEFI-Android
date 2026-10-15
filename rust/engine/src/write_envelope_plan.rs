use crate::compiled_profile_bundle::CompiledEcuProfileBundle;
use crate::ms_envelope_codec::{EnvelopeCodecError, decode_response, encode_request};
use crate::page_write_planner::PageWritePlan;

pub const WRITE_ACK_FRAME_SIZE: usize = 7;
pub const WRITE_OK_STATUS: u8 = 0x00;

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct FramedPageWritePlan {
    /// TunerStudio definition page number. Definition pages are 1-based.
    pub page: usize,
    pub offset: usize,
    pub before: Vec<u8>,
    pub after: Vec<u8>,
    pub request_frame: Vec<u8>,
    pub expected_response_size: usize,
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub enum WriteEnvelopePlanError {
    Envelope(EnvelopeCodecError),
    UnexpectedStatus(u8),
    UnexpectedPayload(Vec<u8>),
}

impl From<EnvelopeCodecError> for WriteEnvelopePlanError {
    fn from(value: EnvelopeCodecError) -> Self {
        Self::Envelope(value)
    }
}

pub fn frame_page_write(
    bundle: &CompiledEcuProfileBundle,
    plan: &PageWritePlan,
) -> Result<FramedPageWritePlan, WriteEnvelopePlanError> {
    let request_frame = encode_request(bundle, &plan.request_body)?;
    Ok(FramedPageWritePlan {
        page: plan.page,
        offset: plan.offset,
        before: plan.before.clone(),
        after: plan.after.clone(),
        request_frame,
        expected_response_size: WRITE_ACK_FRAME_SIZE,
    })
}

/// Verify the firmware's normal chunk-write acknowledgement.
///
/// Direct firmware evidence shows `handleWriteChunkCommand()` calls `sendOkResponse()` after a
/// successful RAM write. `sendOkResponse()` sends a CRC response with zero payload, and
/// `TsChannelBase::sendResponse(TS_CRC, ...)` uses `TS_RESPONSE_OK` as the response status. Burn
/// uses its own distinct response code and is intentionally outside this contract.
pub fn verify_page_write_ack(
    bundle: &CompiledEcuProfileBundle,
    response_frame: &[u8],
) -> Result<(), WriteEnvelopePlanError> {
    let response = decode_response(bundle, response_frame)?;
    if response.status != WRITE_OK_STATUS {
        return Err(WriteEnvelopePlanError::UnexpectedStatus(response.status));
    }
    if !response.payload.is_empty() {
        return Err(WriteEnvelopePlanError::UnexpectedPayload(response.payload));
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use std::collections::{HashMap, HashSet};

    use super::*;
    use crate::compiled_profile_bundle::{OwnedDefinitionBundleImport, compile_definition_bundle};
    use crate::ms_envelope_codec::crc32_ieee;

    fn bundle(envelope: &str) -> CompiledEcuProfileBundle {
        let definition = format!(
            r#"
[TunerStudio]
queryCommand = "S"
signature = "rusEFI write-envelope.test.1"

[Constants]
messageEnvelopeFormat = {envelope}
endianness = little
nPages = 1
pageSize = 64
pageIdentifier = "\x00\x00"
pageReadCommand = "R%2o%2c"
pageChunkWrite = "C%2o%2c%v"
"#,
        );
        compile_definition_bundle(&OwnedDefinitionBundleImport {
            profile_name: "write-envelope".to_string(),
            profile_generation: 1,
            definition_fingerprint: None,
            root_definition: definition,
            enabled_symbols: HashSet::new(),
            includes: HashMap::new(),
            semantic_tables: Vec::new(),
        })
        .unwrap()
    }

    fn plan() -> PageWritePlan {
        PageWritePlan {
            page: 1,
            offset: 12,
            before: vec![0x10, 0x20],
            after: vec![0xaa, 0xbb],
            request_body: vec![b'C', 0x0c, 0x00, 0x02, 0x00, 0xaa, 0xbb],
        }
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
    fn write_body_is_framed_by_the_selected_bundle_envelope() {
        let bundle = bundle("msEnvelope_1.0");
        let plan = plan();
        let framed = frame_page_write(&bundle, &plan).unwrap();

        assert_eq!(framed.page, 1);
        assert_eq!(framed.offset, 12);
        assert_eq!(framed.before, vec![0x10, 0x20]);
        assert_eq!(framed.after, vec![0xaa, 0xbb]);
        assert_eq!(framed.expected_response_size, WRITE_ACK_FRAME_SIZE);
        assert_eq!(&framed.request_frame[..2], &[0x00, 0x07]);
        assert_eq!(&framed.request_frame[2..9], plan.request_body.as_slice());
        assert_eq!(
            &framed.request_frame[9..],
            &crc32_ieee(&plan.request_body).to_be_bytes()
        );
    }

    #[test]
    fn normal_status_only_crc_response_is_the_only_success_ack() {
        let bundle = bundle("msEnvelope_1.0");
        let frame = response_frame(WRITE_OK_STATUS, &[]);

        assert_eq!(frame.len(), WRITE_ACK_FRAME_SIZE);
        assert_eq!(verify_page_write_ack(&bundle, &frame), Ok(()));
    }

    #[test]
    fn non_success_status_never_counts_as_ram_write_ack() {
        let bundle = bundle("msEnvelope_1.0");
        let frame = response_frame(0x7f, &[]);

        assert_eq!(
            verify_page_write_ack(&bundle, &frame),
            Err(WriteEnvelopePlanError::UnexpectedStatus(0x7f))
        );
    }

    #[test]
    fn unexpected_payload_never_counts_as_ram_write_ack() {
        let bundle = bundle("msEnvelope_1.0");
        let frame = response_frame(WRITE_OK_STATUS, &[0xaa]);

        assert_eq!(
            verify_page_write_ack(&bundle, &frame),
            Err(WriteEnvelopePlanError::UnexpectedPayload(vec![0xaa]))
        );
    }

    #[test]
    fn crc_or_length_failure_is_preserved_from_envelope_codec() {
        let bundle = bundle("msEnvelope_1.0");
        let mut frame = response_frame(WRITE_OK_STATUS, &[]);
        *frame.last_mut().unwrap() ^= 0x01;

        assert!(matches!(
            verify_page_write_ack(&bundle, &frame),
            Err(WriteEnvelopePlanError::Envelope(
                EnvelopeCodecError::CrcMismatch { .. }
            ))
        ));
    }

    #[test]
    fn unsupported_envelope_fails_before_a_write_frame_exists() {
        let bundle = bundle("futureEnvelope_2.0");
        assert_eq!(
            frame_page_write(&bundle, &plan()),
            Err(WriteEnvelopePlanError::Envelope(
                EnvelopeCodecError::UnsupportedEnvelope("futureEnvelope_2.0".to_string())
            ))
        );
    }
}
