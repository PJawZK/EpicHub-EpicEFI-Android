use std::collections::VecDeque;

pub const DIAGNOSTIC_BATCH_ABI_VERSION: u32 = 1;
pub const DIAGNOSTIC_BATCH_HEADER_SIZE: usize = 16;
pub const DIAGNOSTIC_EVENT_SIZE: usize = 40;
pub const DIAGNOSTIC_BATCH_MAX_EVENTS: usize = 16;
pub const DIAGNOSTIC_BATCH_SIZE: usize =
    DIAGNOSTIC_BATCH_HEADER_SIZE + (DIAGNOSTIC_EVENT_SIZE * DIAGNOSTIC_BATCH_MAX_EVENTS);
const DIAGNOSTIC_RING_CAPACITY: usize = 128;

pub const DIAGNOSTIC_CODE_ENGINE_CREATED: u32 = 1;
pub const DIAGNOSTIC_CODE_RUNTIME_SNAPSHOT_PUBLISHED: u32 = 2;

pub const DIAGNOSTIC_SEVERITY_TRACE: u32 = 0;
pub const DIAGNOSTIC_SEVERITY_INFO: u32 = 1;

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct NativeDiagnosticEvent {
    pub event_id: u64,
    pub elapsed_nanos: u64,
    pub code: u32,
    pub severity: u32,
    pub arg0: u64,
    pub arg1: u64,
}

pub struct EngineDiagnostics {
    next_event_id: u64,
    dropped_total: u64,
    events: VecDeque<NativeDiagnosticEvent>,
}

impl EngineDiagnostics {
    pub fn new(engine_handle: u64) -> Self {
        let mut diagnostics = Self {
            next_event_id: 1,
            dropped_total: 0,
            events: VecDeque::with_capacity(DIAGNOSTIC_RING_CAPACITY),
        };
        diagnostics.record(
            0,
            DIAGNOSTIC_CODE_ENGINE_CREATED,
            DIAGNOSTIC_SEVERITY_INFO,
            engine_handle,
            0,
        );
        diagnostics
    }

    pub fn record(
        &mut self,
        elapsed_nanos: u64,
        code: u32,
        severity: u32,
        arg0: u64,
        arg1: u64,
    ) {
        if self.events.len() == DIAGNOSTIC_RING_CAPACITY {
            self.events.pop_front();
            self.dropped_total = self.dropped_total.saturating_add(1);
        }

        let event = NativeDiagnosticEvent {
            event_id: self.next_event_id,
            elapsed_nanos,
            code,
            severity,
            arg0,
            arg1,
        };
        self.next_event_id = self.next_event_id.saturating_add(1);
        self.events.push_back(event);
    }

    pub fn drain_batch(&mut self) -> [u8; DIAGNOSTIC_BATCH_SIZE] {
        let mut output = [0_u8; DIAGNOSTIC_BATCH_SIZE];
        let event_count = self.events.len().min(DIAGNOSTIC_BATCH_MAX_EVENTS);

        write_u32(&mut output, 0, DIAGNOSTIC_BATCH_ABI_VERSION);
        write_u32(&mut output, 4, DIAGNOSTIC_BATCH_SIZE as u32);
        write_u32(&mut output, 8, event_count as u32);
        write_u32(
            &mut output,
            12,
            u32::try_from(self.dropped_total).unwrap_or(u32::MAX),
        );

        for index in 0..event_count {
            let event = self
                .events
                .pop_front()
                .expect("event count was bounded by queue length");
            let offset = DIAGNOSTIC_BATCH_HEADER_SIZE + (index * DIAGNOSTIC_EVENT_SIZE);
            write_u64(&mut output, offset, event.event_id);
            write_u64(&mut output, offset + 8, event.elapsed_nanos);
            write_u32(&mut output, offset + 16, event.code);
            write_u32(&mut output, offset + 20, event.severity);
            write_u64(&mut output, offset + 24, event.arg0);
            write_u64(&mut output, offset + 32, event.arg1);
        }

        output
    }
}

fn write_u32(target: &mut [u8], offset: usize, value: u32) {
    target[offset..offset + 4].copy_from_slice(&value.to_le_bytes());
}

fn write_u64(target: &mut [u8], offset: usize, value: u64) {
    target[offset..offset + 8].copy_from_slice(&value.to_le_bytes());
}

#[cfg(test)]
mod tests {
    use super::*;

    fn read_u32(bytes: &[u8], offset: usize) -> u32 {
        u32::from_le_bytes(bytes[offset..offset + 4].try_into().unwrap())
    }

    fn read_u64(bytes: &[u8], offset: usize) -> u64 {
        u64::from_le_bytes(bytes[offset..offset + 8].try_into().unwrap())
    }

    #[test]
    fn batch_layout_is_fixed_versioned_and_little_endian() {
        let mut diagnostics = EngineDiagnostics::new(0x1122_3344_5566_7788);
        let batch = diagnostics.drain_batch();

        assert_eq!(batch.len(), DIAGNOSTIC_BATCH_SIZE);
        assert_eq!(read_u32(&batch, 0), DIAGNOSTIC_BATCH_ABI_VERSION);
        assert_eq!(read_u32(&batch, 4), DIAGNOSTIC_BATCH_SIZE as u32);
        assert_eq!(read_u32(&batch, 8), 1);
        assert_eq!(read_u32(&batch, 12), 0);
        assert_eq!(read_u64(&batch, 16), 1);
        assert_eq!(read_u32(&batch, 32), DIAGNOSTIC_CODE_ENGINE_CREATED);
        assert_eq!(read_u32(&batch, 36), DIAGNOSTIC_SEVERITY_INFO);
        assert_eq!(read_u64(&batch, 40), 0x1122_3344_5566_7788);
    }

    #[test]
    fn drain_is_bounded_and_removes_only_published_events() {
        let mut diagnostics = EngineDiagnostics::new(7);
        for sequence in 1..=20 {
            diagnostics.record(
                sequence,
                DIAGNOSTIC_CODE_RUNTIME_SNAPSHOT_PUBLISHED,
                DIAGNOSTIC_SEVERITY_TRACE,
                sequence,
                3,
            );
        }

        let first = diagnostics.drain_batch();
        let second = diagnostics.drain_batch();
        let third = diagnostics.drain_batch();

        assert_eq!(read_u32(&first, 8), 16);
        assert_eq!(read_u32(&second, 8), 5);
        assert_eq!(read_u32(&third, 8), 0);
        assert_eq!(read_u64(&first, 16), 1);
        assert_eq!(read_u64(&second, 16), 17);
    }

    #[test]
    fn ring_overflow_drops_oldest_and_reports_total() {
        let mut diagnostics = EngineDiagnostics::new(1);
        for sequence in 1..=(DIAGNOSTIC_RING_CAPACITY as u64 + 10) {
            diagnostics.record(
                sequence,
                DIAGNOSTIC_CODE_RUNTIME_SNAPSHOT_PUBLISHED,
                DIAGNOSTIC_SEVERITY_TRACE,
                sequence,
                0,
            );
        }

        let batch = diagnostics.drain_batch();
        assert_eq!(read_u32(&batch, 12), 11);
        assert_eq!(read_u64(&batch, 16), 12);
    }
}
