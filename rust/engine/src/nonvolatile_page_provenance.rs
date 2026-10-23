//! Session-scoped evidence of a completed guarded Burn followed by an exact page CRC.
//! This is deliberately independent of RAM confirmation and never inferred from page reads.
use crate::ms_envelope_codec::crc32_ieee;

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct NonvolatilePageProof {
    pub profile_generation: u64,
    pub draft_revision: u64,
    /// One-based ECU definition page number.
    pub page: usize,
    pub expected_page_crc32: u32,
    /// Fingerprint of the full selected runtime signature response.
    pub signature_crc32: u32,
}

impl NonvolatilePageProof {
    pub fn from_guarded_burn(
        profile_generation: u64,
        draft_revision: u64,
        page: usize,
        expected_page_crc32: u32,
        signature_response: &[u8],
    ) -> Self {
        Self {
            profile_generation,
            draft_revision,
            page,
            expected_page_crc32,
            signature_crc32: crc32_ieee(signature_response),
        }
    }

    pub fn matches(
        &self,
        profile_generation: u64,
        draft_revision: u64,
        page: usize,
        page_bytes: &[u8],
        signature_response: &[u8],
    ) -> bool {
        self.profile_generation == profile_generation
            && self.draft_revision == draft_revision
            && self.page == page
            && self.expected_page_crc32 == crc32_ieee(page_bytes)
            && self.signature_crc32 == crc32_ieee(signature_response)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn proof_is_bound_to_exact_generation_revision_page_crc_and_signature() {
        let proof = NonvolatilePageProof::from_guarded_burn(3, 7, 2, crc32_ieee(b"page"), b"ecu-A");
        assert!(proof.matches(3, 7, 2, b"page", b"ecu-A"));
        assert!(!proof.matches(4, 7, 2, b"page", b"ecu-A"));
        assert!(!proof.matches(3, 8, 2, b"page", b"ecu-A"));
        assert!(!proof.matches(3, 7, 1, b"page", b"ecu-A"));
        assert!(!proof.matches(3, 7, 2, b"other", b"ecu-A"));
        assert!(!proof.matches(3, 7, 2, b"page", b"ecu-B"));
    }
}
