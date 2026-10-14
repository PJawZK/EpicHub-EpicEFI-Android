use crate::ecu_profile_registry::RuntimeControllerIdentity;

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct TransportCandidate {
    pub transport_kind: String,
    pub endpoint_id: String,
    pub description: Option<String>,
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct SignatureProbePlan {
    pub query: Vec<u8>,
    pub max_response_bytes: usize,
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub enum RuntimeProbeError {
    EmptyQuery,
    InvalidResponseLimit,
    Transport(String),
    ResponseTooLarge { limit: usize, actual: usize },
    EmptySignature,
    InvalidUtf8,
}

pub trait ReadOnlyProbeChannel {
    fn transact_read_only(
        &mut self,
        candidate: &TransportCandidate,
        request: &[u8],
        max_response_bytes: usize,
    ) -> Result<Vec<u8>, String>;
}

pub fn runtime_identity_from_probe_response(
    response: &[u8],
    max_response_bytes: usize,
) -> Result<RuntimeControllerIdentity, RuntimeProbeError> {
    if max_response_bytes == 0 {
        return Err(RuntimeProbeError::InvalidResponseLimit);
    }
    if response.len() > max_response_bytes {
        return Err(RuntimeProbeError::ResponseTooLarge {
            limit: max_response_bytes,
            actual: response.len(),
        });
    }
    if response.is_empty() {
        return Err(RuntimeProbeError::EmptySignature);
    }
    let signature = std::str::from_utf8(response).map_err(|_| RuntimeProbeError::InvalidUtf8)?;
    if signature.is_empty() {
        return Err(RuntimeProbeError::EmptySignature);
    }

    Ok(RuntimeControllerIdentity::from_signature(signature))
}

pub fn probe_runtime_identity(
    channel: &mut dyn ReadOnlyProbeChannel,
    candidate: &TransportCandidate,
    plan: &SignatureProbePlan,
) -> Result<RuntimeControllerIdentity, RuntimeProbeError> {
    if plan.query.is_empty() {
        return Err(RuntimeProbeError::EmptyQuery);
    }
    if plan.max_response_bytes == 0 {
        return Err(RuntimeProbeError::InvalidResponseLimit);
    }

    let response = channel
        .transact_read_only(candidate, &plan.query, plan.max_response_bytes)
        .map_err(RuntimeProbeError::Transport)?;
    runtime_identity_from_probe_response(&response, plan.max_response_bytes)
}

#[cfg(test)]
mod tests {
    use super::*;

    struct FakeReadOnlyChannel {
        response: Result<Vec<u8>, String>,
        requests: Vec<(TransportCandidate, Vec<u8>, usize)>,
    }

    impl Default for FakeReadOnlyChannel {
        fn default() -> Self {
            Self {
                response: Ok(Vec::new()),
                requests: Vec::new(),
            }
        }
    }

    impl ReadOnlyProbeChannel for FakeReadOnlyChannel {
        fn transact_read_only(
            &mut self,
            candidate: &TransportCandidate,
            request: &[u8],
            max_response_bytes: usize,
        ) -> Result<Vec<u8>, String> {
            self.requests
                .push((candidate.clone(), request.to_vec(), max_response_bytes));
            self.response.clone()
        }
    }

    fn candidate() -> TransportCandidate {
        TransportCandidate {
            transport_kind: "usb-serial".to_string(),
            endpoint_id: "candidate-1".to_string(),
            description: Some("descriptive adapter metadata".to_string()),
        }
    }

    fn plan() -> SignatureProbePlan {
        SignatureProbePlan {
            query: b"S".to_vec(),
            max_response_bytes: 256,
        }
    }

    #[test]
    fn emits_runtime_identity_from_exact_read_only_response_bytes() {
        let signature = "rusEFI master.2026.08.26.MEGA144H7.2273317132";
        let mut channel = FakeReadOnlyChannel {
            response: Ok(signature.as_bytes().to_vec()),
            ..Default::default()
        };

        let identity = probe_runtime_identity(&mut channel, &candidate(), &plan()).unwrap();
        assert_eq!(identity.signature, signature);
        assert_eq!(identity.firmware_info, None);
        assert_eq!(identity.board_info, None);
        assert_eq!(channel.requests.len(), 1);
        assert_eq!(channel.requests[0].1, b"S");
        assert_eq!(channel.requests[0].2, 256);
    }

    #[test]
    fn validates_transport_independent_response_bytes_with_the_same_rules() {
        let signature = "rusEFI master.2026.08.26.MEGA144H7.2273317132";
        let identity = runtime_identity_from_probe_response(signature.as_bytes(), 256).unwrap();
        assert_eq!(identity.signature, signature);

        assert_eq!(
            runtime_identity_from_probe_response(b"", 256),
            Err(RuntimeProbeError::EmptySignature)
        );
        assert_eq!(
            runtime_identity_from_probe_response(&[0xff], 256),
            Err(RuntimeProbeError::InvalidUtf8)
        );
        assert_eq!(
            runtime_identity_from_probe_response(b"ABCD", 3),
            Err(RuntimeProbeError::ResponseTooLarge {
                limit: 3,
                actual: 4,
            })
        );
        assert_eq!(
            runtime_identity_from_probe_response(b"A", 0),
            Err(RuntimeProbeError::InvalidResponseLimit)
        );
    }

    #[test]
    fn rejects_empty_or_invalid_probe_configuration_before_transport_io() {
        let mut channel = FakeReadOnlyChannel::default();
        let mut empty_query = plan();
        empty_query.query.clear();
        assert_eq!(
            probe_runtime_identity(&mut channel, &candidate(), &empty_query),
            Err(RuntimeProbeError::EmptyQuery)
        );
        let mut no_capacity = plan();
        no_capacity.max_response_bytes = 0;
        assert_eq!(
            probe_runtime_identity(&mut channel, &candidate(), &no_capacity),
            Err(RuntimeProbeError::InvalidResponseLimit)
        );
        assert!(channel.requests.is_empty());
    }

    #[test]
    fn rejects_transport_failure_empty_signature_invalid_utf8_and_oversize_response() {
        let mut transport_failure = FakeReadOnlyChannel {
            response: Err("probe failed".to_string()),
            ..Default::default()
        };
        assert_eq!(
            probe_runtime_identity(&mut transport_failure, &candidate(), &plan()),
            Err(RuntimeProbeError::Transport("probe failed".to_string()))
        );

        let mut empty = FakeReadOnlyChannel::default();
        assert_eq!(
            probe_runtime_identity(&mut empty, &candidate(), &plan()),
            Err(RuntimeProbeError::EmptySignature)
        );

        let mut invalid_utf8 = FakeReadOnlyChannel {
            response: Ok(vec![0xff]),
            ..Default::default()
        };
        assert_eq!(
            probe_runtime_identity(&mut invalid_utf8, &candidate(), &plan()),
            Err(RuntimeProbeError::InvalidUtf8)
        );

        let mut oversize = FakeReadOnlyChannel {
            response: Ok(vec![b'A'; 4]),
            ..Default::default()
        };
        let short_plan = SignatureProbePlan {
            query: b"S".to_vec(),
            max_response_bytes: 3,
        };
        assert_eq!(
            probe_runtime_identity(&mut oversize, &candidate(), &short_plan),
            Err(RuntimeProbeError::ResponseTooLarge {
                limit: 3,
                actual: 4,
            })
        );
    }
}
