//! Read-only, definition-authorized output-channel request framing.
//! This produces protocol bytes only; it never opens a transport or polls an ECU.
use crate::ms_envelope_codec::{
    EnvelopeCodecError, decode_ms_envelope_response, encode_ms_envelope_request,
};
use crate::runtime_channel_decode::MAX_RUNTIME_FRAME_BYTES;

#[derive(Clone, Debug, Eq, PartialEq)]
pub enum RuntimeOutputReadError {
    UnsupportedCommand,
    InvalidFrameLength,
    InvalidRange,
    UnexpectedResponseStatus(u8),
    WrongPayloadLength { expected: usize, actual: usize },
    Envelope(EnvelopeCodecError),
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct RuntimeOutputReadPlan {
    command: u8,
    frame_length: usize,
}

impl RuntimeOutputReadPlan {
    /// Accept only the supported literal command template from a resolved
    /// [OutputChannels] definition. Arbitrary placeholders or expressions
    /// must not acquire runtime IO authority.
    pub fn compile(
        och_get_command: &str,
        frame_length: usize,
        envelope: &str,
    ) -> Result<Self, RuntimeOutputReadError> {
        if envelope != "msEnvelope_1.0" || och_get_command != "O%2o%2c" {
            return Err(RuntimeOutputReadError::UnsupportedCommand);
        }
        if frame_length == 0 || frame_length > MAX_RUNTIME_FRAME_BYTES {
            return Err(RuntimeOutputReadError::InvalidFrameLength);
        }
        Ok(Self {
            command: b'O',
            frame_length,
        })
    }

    pub fn frame_length(&self) -> usize {
        self.frame_length
    }

    pub fn request(&self, offset: usize, count: usize) -> Result<Vec<u8>, RuntimeOutputReadError> {
        if count == 0
            || count > u16::MAX as usize
            || offset > u16::MAX as usize
            || offset
                .checked_add(count)
                .is_none_or(|end| end > self.frame_length)
        {
            return Err(RuntimeOutputReadError::InvalidRange);
        }
        let mut body = Vec::with_capacity(5);
        body.push(self.command);
        body.extend_from_slice(&(offset as u16).to_le_bytes());
        body.extend_from_slice(&(count as u16).to_le_bytes());
        encode_ms_envelope_request(&body).map_err(RuntimeOutputReadError::Envelope)
    }

    /// Validate status and exact requested byte count; no partial channel data
    /// is published when an ECU returns an error or malformed frame.
    pub fn response(
        &self,
        frame: &[u8],
        expected_count: usize,
    ) -> Result<Vec<u8>, RuntimeOutputReadError> {
        if expected_count == 0 || expected_count > self.frame_length {
            return Err(RuntimeOutputReadError::InvalidRange);
        }
        let decoded =
            decode_ms_envelope_response(frame).map_err(RuntimeOutputReadError::Envelope)?;
        if decoded.status != 0 {
            return Err(RuntimeOutputReadError::UnexpectedResponseStatus(
                decoded.status,
            ));
        }
        if decoded.payload.len() != expected_count {
            return Err(RuntimeOutputReadError::WrongPayloadLength {
                expected: expected_count,
                actual: decoded.payload.len(),
            });
        }
        Ok(decoded.payload)
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::ms_envelope_codec::crc32_ieee;

    fn fixture_response(status: u8, payload: &[u8]) -> Vec<u8> {
        let mut body = vec![status];
        body.extend_from_slice(payload);
        let mut frame = Vec::new();
        frame.extend_from_slice(&(body.len() as u16).to_be_bytes());
        frame.extend_from_slice(&body);
        frame.extend_from_slice(&crc32_ieee(&body).to_be_bytes());
        frame
    }

    #[test]
    fn firmwares_offset_and_count_command_is_little_endian_in_ms_envelope() {
        let plan = RuntimeOutputReadPlan::compile("O%2o%2c", 4096, "msEnvelope_1.0").unwrap();
        let encoded = plan.request(0x123, 0x203).unwrap();
        assert_eq!(&encoded[..2], &[0, 5]);
        assert_eq!(&encoded[2..7], &[b'O', 0x23, 0x01, 0x03, 0x02]);
        assert_eq!(&encoded[7..], &crc32_ieee(&encoded[2..7]).to_be_bytes());
    }

    #[test]
    fn rejects_range_overflow_oversize_and_unsupported_templates() {
        let plan = RuntimeOutputReadPlan::compile("O%2o%2c", 80, "msEnvelope_1.0").unwrap();
        assert_eq!(
            plan.request(79, 2),
            Err(RuntimeOutputReadError::InvalidRange)
        );
        assert_eq!(
            plan.request(0, 0),
            Err(RuntimeOutputReadError::InvalidRange)
        );
        assert_eq!(
            plan.request(usize::MAX, 1),
            Err(RuntimeOutputReadError::InvalidRange)
        );
        assert_eq!(
            RuntimeOutputReadPlan::compile("O", 80, "msEnvelope_1.0"),
            Err(RuntimeOutputReadError::UnsupportedCommand)
        );
        assert_eq!(
            RuntimeOutputReadPlan::compile("O%2o%2c", 80, "other"),
            Err(RuntimeOutputReadError::UnsupportedCommand)
        );
    }

    #[test]
    fn rejects_bad_crc_status_and_mismatched_payload_length() {
        let plan = RuntimeOutputReadPlan::compile("O%2o%2c", 80, "msEnvelope_1.0").unwrap();
        assert_eq!(
            plan.response(&fixture_response(0, &[3, 4]), 2).unwrap(),
            vec![3, 4]
        );
        assert_eq!(
            plan.response(&fixture_response(0x82, &[3, 4]), 2),
            Err(RuntimeOutputReadError::UnexpectedResponseStatus(0x82))
        );
        assert_eq!(
            plan.response(&fixture_response(0, &[3]), 2),
            Err(RuntimeOutputReadError::WrongPayloadLength {
                expected: 2,
                actual: 1
            })
        );
        let mut bad_crc = fixture_response(0, &[3, 4]);
        *bad_crc.last_mut().unwrap() ^= 1;
        assert!(matches!(
            plan.response(&bad_crc, 2),
            Err(RuntimeOutputReadError::Envelope(
                EnvelopeCodecError::CrcMismatch { .. }
            ))
        ));
    }
}
