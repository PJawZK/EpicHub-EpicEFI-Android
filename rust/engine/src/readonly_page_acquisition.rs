use crate::compiled_profile_bundle::CompiledEcuProfileBundle;
use crate::ecu_profile::ResolvedEcuProfile;
use crate::ms_envelope_codec::{EnvelopeCodecError, decode_response, encode_request};
use crate::page_read_planner::{PageReadPlan, PageReadPlanError, plan_full_page_reads};

pub const MS_RESPONSE_OK: u8 = 0x00;
pub const MS_ENVELOPE_RESPONSE_OVERHEAD_BYTES: usize = 7;
pub const MAX_MS_ENVELOPE_RESPONSE_PAYLOAD_BYTES: usize = u16::MAX as usize - 1;
pub const ANDROID_READ_ONLY_RESPONSE_LIMIT_BYTES: usize = 64 * 1024;
pub const MAX_ANDROID_PAGE_READ_PAYLOAD_BYTES: usize =
    ANDROID_READ_ONLY_RESPONSE_LIMIT_BYTES - MS_ENVELOPE_RESPONSE_OVERHEAD_BYTES;

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct FramedPageReadPlan {
    pub page_index: usize,
    pub offset: usize,
    pub count: usize,
    pub request_frame: Vec<u8>,
    pub expected_response_frame_bytes: usize,
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct VerifiedPageChunk {
    pub page_index: usize,
    pub offset: usize,
    pub payload: Vec<u8>,
}

#[derive(Clone, Debug, PartialEq)]
pub enum ReadOnlyPageAcquisitionError {
    InvalidResponseFrameLimit(usize),
    Planner(PageReadPlanError),
    Envelope(EnvelopeCodecError),
    NonSuccessStatus(u8),
    PayloadLengthMismatch { expected: usize, actual: usize },
    ChunkPageMismatch { expected: usize, actual: usize },
    ChunkOffsetMismatch { expected: usize, actual: usize },
    PageIncomplete { expected: usize, actual: usize },
}

impl From<PageReadPlanError> for ReadOnlyPageAcquisitionError {
    fn from(value: PageReadPlanError) -> Self {
        Self::Planner(value)
    }
}

impl From<EnvelopeCodecError> for ReadOnlyPageAcquisitionError {
    fn from(value: EnvelopeCodecError) -> Self {
        Self::Envelope(value)
    }
}

pub fn plan_page_acquisition(
    bundle: &CompiledEcuProfileBundle,
    page_index: usize,
    max_response_frame_bytes: usize,
) -> Result<Vec<FramedPageReadPlan>, ReadOnlyPageAcquisitionError> {
    if max_response_frame_bytes <= MS_ENVELOPE_RESPONSE_OVERHEAD_BYTES {
        return Err(ReadOnlyPageAcquisitionError::InvalidResponseFrameLimit(
            max_response_frame_bytes,
        ));
    }

    let payload_limit = (max_response_frame_bytes - MS_ENVELOPE_RESPONSE_OVERHEAD_BYTES)
        .min(MAX_MS_ENVELOPE_RESPONSE_PAYLOAD_BYTES);
    if payload_limit == 0 {
        return Err(ReadOnlyPageAcquisitionError::InvalidResponseFrameLimit(
            max_response_frame_bytes,
        ));
    }

    plan_full_page_reads(bundle, page_index, payload_limit)?
        .into_iter()
        .map(|plan| frame_plan(bundle, plan))
        .collect()
}

pub fn verify_page_read_response(
    bundle: &CompiledEcuProfileBundle,
    plan: &FramedPageReadPlan,
    response_frame: &[u8],
) -> Result<VerifiedPageChunk, ReadOnlyPageAcquisitionError> {
    let decoded = decode_response(bundle, response_frame)?;
    if decoded.status != MS_RESPONSE_OK {
        return Err(ReadOnlyPageAcquisitionError::NonSuccessStatus(
            decoded.status,
        ));
    }
    if decoded.payload.len() != plan.count {
        return Err(ReadOnlyPageAcquisitionError::PayloadLengthMismatch {
            expected: plan.count,
            actual: decoded.payload.len(),
        });
    }

    Ok(VerifiedPageChunk {
        page_index: plan.page_index,
        offset: plan.offset,
        payload: decoded.payload,
    })
}

pub fn assemble_complete_page(
    bundle: &CompiledEcuProfileBundle,
    page_index: usize,
    chunks: &[VerifiedPageChunk],
) -> Result<Vec<u8>, ReadOnlyPageAcquisitionError> {
    let page_sizes = bundle.profile.page_sizes();
    let page_size = *page_sizes
        .get(page_index)
        .ok_or(ReadOnlyPageAcquisitionError::Planner(
            PageReadPlanError::PageOutOfRange {
                page_index,
                page_count: page_sizes.len(),
            },
        ))?;

    let mut page = Vec::with_capacity(page_size);
    let mut expected_offset = 0usize;
    for chunk in chunks {
        if chunk.page_index != page_index {
            return Err(ReadOnlyPageAcquisitionError::ChunkPageMismatch {
                expected: page_index,
                actual: chunk.page_index,
            });
        }
        if chunk.offset != expected_offset {
            return Err(ReadOnlyPageAcquisitionError::ChunkOffsetMismatch {
                expected: expected_offset,
                actual: chunk.offset,
            });
        }
        page.extend_from_slice(&chunk.payload);
        expected_offset += chunk.payload.len();
        if expected_offset > page_size {
            return Err(ReadOnlyPageAcquisitionError::PageIncomplete {
                expected: page_size,
                actual: expected_offset,
            });
        }
    }

    if page.len() != page_size {
        return Err(ReadOnlyPageAcquisitionError::PageIncomplete {
            expected: page_size,
            actual: page.len(),
        });
    }
    Ok(page)
}

fn frame_plan(
    bundle: &CompiledEcuProfileBundle,
    plan: PageReadPlan,
) -> Result<FramedPageReadPlan, ReadOnlyPageAcquisitionError> {
    let request_frame = encode_request(bundle, &plan.request_body)?;
    let expected_response_frame_bytes = plan
        .expected_payload_bytes
        .checked_add(MS_ENVELOPE_RESPONSE_OVERHEAD_BYTES)
        .ok_or(ReadOnlyPageAcquisitionError::InvalidResponseFrameLimit(
            usize::MAX,
        ))?;
    Ok(FramedPageReadPlan {
        page_index: plan.page_index,
        offset: plan.offset,
        count: plan.count,
        request_frame,
        expected_response_frame_bytes,
    })
}

#[cfg(test)]
mod tests {
    use std::collections::{HashMap, HashSet};

    use super::*;
    use crate::compiled_profile_bundle::{OwnedDefinitionBundleImport, compile_definition_bundle};
    use crate::ms_envelope_codec::{crc32_ieee, decode_ms_envelope_response};

    fn bundle(page_size: usize) -> CompiledEcuProfileBundle {
        let definition = format!(
            r#"
[TunerStudio]
queryCommand = "S"
signature = "rusEFI acquisition.test.1"

[Constants]
messageEnvelopeFormat = msEnvelope_1.0
endianness = little
nPages = 1
pageSize = {page_size}
pageIdentifier = "\x00\x00"
pageReadCommand = "R%2i%2o%2c"
"#,
        );
        compile_definition_bundle(&OwnedDefinitionBundleImport {
            profile_name: "acquisition".to_string(),
            profile_generation: 1,
            definition_fingerprint: None,
            root_definition: definition,
            enabled_symbols: HashSet::new(),
            includes: HashMap::new(),
            semantic_tables: Vec::new(),
        })
        .unwrap()
    }

    fn response(status: u8, payload: &[u8]) -> Vec<u8> {
        let mut body = Vec::with_capacity(payload.len() + 1);
        body.push(status);
        body.extend_from_slice(payload);
        let mut frame = Vec::with_capacity(body.len() + 6);
        frame.extend_from_slice(&(body.len() as u16).to_be_bytes());
        frame.extend_from_slice(&body);
        frame.extend_from_slice(&crc32_ieee(&body).to_be_bytes());
        frame
    }

    #[test]
    fn android_frame_limit_reduces_payload_chunk_to_65529_bytes() {
        let bundle = bundle(65_536);
        let plans =
            plan_page_acquisition(&bundle, 0, ANDROID_READ_ONLY_RESPONSE_LIMIT_BYTES).unwrap();

        assert_eq!(plans.len(), 2);
        assert_eq!((plans[0].offset, plans[0].count), (0, 65_529));
        assert_eq!((plans[1].offset, plans[1].count), (65_529, 7));
        assert_eq!(plans[0].expected_response_frame_bytes, 65_536);
        assert_eq!(plans[1].expected_response_frame_bytes, 14);
    }

    #[test]
    fn ms_envelope_status_byte_also_caps_payload_even_with_larger_transport_frame() {
        let bundle = bundle(65_536);
        let plans = plan_page_acquisition(&bundle, 0, 70_000).unwrap();

        assert_eq!(plans.len(), 2);
        assert_eq!(plans[0].count, MAX_MS_ENVELOPE_RESPONSE_PAYLOAD_BYTES);
        assert_eq!(plans[1].count, 2);
    }

    #[test]
    fn framed_plan_contains_crc_request_and_exact_expected_response_size() {
        let bundle = bundle(64);
        let plan = plan_page_acquisition(&bundle, 0, 64).unwrap().remove(0);

        assert_eq!(plan.count, 57);
        assert_eq!(plan.expected_response_frame_bytes, 64);
        assert!(plan.request_frame.len() > 7);
        let body_len = u16::from_be_bytes([plan.request_frame[0], plan.request_frame[1]]) as usize;
        assert_eq!(plan.request_frame.len(), body_len + 6);
    }

    #[test]
    fn verified_response_requires_success_status_and_exact_payload_length() {
        let bundle = bundle(8);
        let plan = plan_page_acquisition(&bundle, 0, 64).unwrap().remove(0);
        let good = response(MS_RESPONSE_OK, &[1, 2, 3, 4, 5, 6, 7, 8]);

        let verified = verify_page_read_response(&bundle, &plan, &good).unwrap();
        assert_eq!(verified.payload, vec![1, 2, 3, 4, 5, 6, 7, 8]);

        assert_eq!(
            verify_page_read_response(&bundle, &plan, &response(0x84, &[0; 8])),
            Err(ReadOnlyPageAcquisitionError::NonSuccessStatus(0x84))
        );
        assert_eq!(
            verify_page_read_response(&bundle, &plan, &response(MS_RESPONSE_OK, &[0; 7])),
            Err(ReadOnlyPageAcquisitionError::PayloadLengthMismatch {
                expected: 8,
                actual: 7,
            })
        );
    }

    #[test]
    fn response_crc_failure_is_never_exposed_as_page_bytes() {
        let bundle = bundle(4);
        let plan = plan_page_acquisition(&bundle, 0, 64).unwrap().remove(0);
        let mut frame = response(MS_RESPONSE_OK, &[1, 2, 3, 4]);
        *frame.last_mut().unwrap() ^= 1;

        assert!(matches!(
            verify_page_read_response(&bundle, &plan, &frame),
            Err(ReadOnlyPageAcquisitionError::Envelope(
                EnvelopeCodecError::CrcMismatch { .. }
            ))
        ));
    }

    #[test]
    fn complete_page_assembly_requires_contiguous_verified_chunks() {
        let bundle = bundle(6);
        let chunks = vec![
            VerifiedPageChunk {
                page_index: 0,
                offset: 0,
                payload: vec![1, 2, 3],
            },
            VerifiedPageChunk {
                page_index: 0,
                offset: 3,
                payload: vec![4, 5, 6],
            },
        ];
        assert_eq!(
            assemble_complete_page(&bundle, 0, &chunks).unwrap(),
            vec![1, 2, 3, 4, 5, 6]
        );

        let gap = vec![
            VerifiedPageChunk {
                page_index: 0,
                offset: 0,
                payload: vec![1, 2],
            },
            VerifiedPageChunk {
                page_index: 0,
                offset: 3,
                payload: vec![4, 5, 6],
            },
        ];
        assert_eq!(
            assemble_complete_page(&bundle, 0, &gap),
            Err(ReadOnlyPageAcquisitionError::ChunkOffsetMismatch {
                expected: 2,
                actual: 3,
            })
        );
    }

    #[test]
    fn invalid_transport_frame_limit_fails_before_planning() {
        let bundle = bundle(8);
        assert_eq!(
            plan_page_acquisition(&bundle, 0, MS_ENVELOPE_RESPONSE_OVERHEAD_BYTES),
            Err(ReadOnlyPageAcquisitionError::InvalidResponseFrameLimit(7))
        );
    }

    #[test]
    fn helper_response_frame_is_valid_ms_envelope() {
        let frame = response(MS_RESPONSE_OK, &[1, 2, 3]);
        let decoded = decode_ms_envelope_response(&frame).unwrap();
        assert_eq!(decoded.status, MS_RESPONSE_OK);
        assert_eq!(decoded.payload, vec![1, 2, 3]);
    }
}
