use std::collections::{BTreeMap, BTreeSet};

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum TuneSnapshotOrigin {
    OfflineFixture,
    ImportedFile,
}

#[derive(Clone, Debug, PartialEq)]
pub struct TuneSnapshot {
    pub profile_generation: u64,
    pub origin: TuneSnapshotOrigin,
    pub tables: BTreeMap<u32, TableSnapshot>,
}

impl TuneSnapshot {
    pub fn new(
        profile_generation: u64,
        origin: TuneSnapshotOrigin,
        tables: impl IntoIterator<Item = TableSnapshot>,
    ) -> Result<Self, TuneError> {
        let mut mapped = BTreeMap::new();
        for table in tables {
            table.validate_shape()?;
            if mapped.insert(table.descriptor.id, table).is_some() {
                return Err(TuneError::DuplicateTableId);
            }
        }
        Ok(Self {
            profile_generation,
            origin,
            tables: mapped,
        })
    }
}

#[derive(Clone, Debug, PartialEq)]
pub struct TableDescriptor {
    pub id: u32,
    pub name: String,
    pub rows: usize,
    pub cols: usize,
    pub minimum: f64,
    pub maximum: f64,
    pub quantum: f64,
    pub quantum_origin: f64,
    pub edit_increment: f64,
    pub display_decimals: u8,
}

impl TableDescriptor {
    fn validate(&self) -> Result<(), TuneError> {
        if self.rows == 0 || self.cols == 0 {
            return Err(TuneError::InvalidShape);
        }
        if !self.minimum.is_finite()
            || !self.maximum.is_finite()
            || !self.quantum.is_finite()
            || !self.quantum_origin.is_finite()
            || !self.edit_increment.is_finite()
            || self.minimum > self.maximum
            || self.quantum <= 0.0
            || self.edit_increment <= 0.0
            || self.display_decimals > 9
            || !is_quantized(self.minimum, self.quantum_origin, self.quantum)
            || !is_quantized(self.maximum, self.quantum_origin, self.quantum)
            || !is_multiple(self.edit_increment, self.quantum)
        {
            return Err(TuneError::InvalidDescriptor);
        }
        Ok(())
    }
}

#[derive(Clone, Debug, PartialEq)]
pub struct TableSnapshot {
    pub descriptor: TableDescriptor,
    pub x_axis: Vec<f64>,
    pub y_axis: Vec<f64>,
    pub values: Vec<f64>,
    pub editable: Vec<bool>,
}

impl TableSnapshot {
    fn validate_shape(&self) -> Result<(), TuneError> {
        self.descriptor.validate()?;
        let expected = self
            .descriptor
            .rows
            .checked_mul(self.descriptor.cols)
            .ok_or(TuneError::InvalidShape)?;
        if self.x_axis.len() != self.descriptor.cols
            || self.y_axis.len() != self.descriptor.rows
            || self.values.len() != expected
            || self.editable.len() != expected
        {
            return Err(TuneError::InvalidShape);
        }
        if self
            .x_axis
            .iter()
            .chain(self.y_axis.iter())
            .chain(self.values.iter())
            .any(|value| !value.is_finite())
        {
            return Err(TuneError::NonFiniteValue);
        }
        for value in &self.values {
            self.validate_canonical_value(*value)?;
        }
        Ok(())
    }
}

#[derive(Clone, Copy, Debug, Eq, Ord, PartialEq, PartialOrd)]
pub struct CellCoord {
    pub row: usize,
    pub col: usize,
}

#[derive(Clone, Copy, Debug, PartialEq)]
pub enum TableEditOperation {
    Set(f64),
    Add(f64),
    Scale(f64),
    Increment,
    Decrement,
}

#[derive(Clone, Debug, PartialEq)]
pub struct TableEditCommand {
    pub table_id: u32,
    pub cells: Vec<CellCoord>,
    pub operation: TableEditOperation,
}

#[derive(Clone, Debug, PartialEq)]
pub struct CellChange {
    pub coord: CellCoord,
    pub before: f64,
    pub after: f64,
}

#[derive(Clone, Debug, PartialEq)]
pub struct TablePreview {
    pub table_id: u32,
    pub base_revision: u64,
    pub changes: Vec<CellChange>,
}

#[derive(Clone, Debug, PartialEq)]
pub struct TableProjection {
    pub descriptor: TableDescriptor,
    pub x_axis: Vec<f64>,
    pub y_axis: Vec<f64>,
    pub values: Vec<f64>,
    pub editable: Vec<bool>,
    pub draft_revision: u64,
}

#[derive(Clone, Debug, PartialEq)]
struct HistoryEntry {
    table_id: u32,
    changes: Vec<CellChange>,
}

#[derive(Clone, Debug, PartialEq)]
pub struct TuneDraft {
    profile_generation: u64,
    origin: TuneSnapshotOrigin,
    revision: u64,
    tables: BTreeMap<u32, TableSnapshot>,
    undo: Vec<HistoryEntry>,
    redo: Vec<HistoryEntry>,
}

impl TuneDraft {
    pub fn from_snapshot(snapshot: &TuneSnapshot) -> Self {
        Self {
            profile_generation: snapshot.profile_generation,
            origin: snapshot.origin,
            revision: 0,
            tables: snapshot.tables.clone(),
            undo: Vec::new(),
            redo: Vec::new(),
        }
    }

    pub fn profile_generation(&self) -> u64 {
        self.profile_generation
    }

    pub fn origin(&self) -> TuneSnapshotOrigin {
        self.origin
    }

    pub fn revision(&self) -> u64 {
        self.revision
    }

    pub fn can_undo(&self) -> bool {
        !self.undo.is_empty()
    }

    pub fn can_redo(&self) -> bool {
        !self.redo.is_empty()
    }

    pub fn table_projection(&self, table_id: u32) -> Result<TableProjection, TuneError> {
        let table = self.tables.get(&table_id).ok_or(TuneError::UnknownTable)?;
        Ok(TableProjection {
            descriptor: table.descriptor.clone(),
            x_axis: table.x_axis.clone(),
            y_axis: table.y_axis.clone(),
            values: table.values.clone(),
            editable: table.editable.clone(),
            draft_revision: self.revision,
        })
    }

    pub fn preview(&self, command: &TableEditCommand) -> Result<TablePreview, TuneError> {
        let table = self
            .tables
            .get(&command.table_id)
            .ok_or(TuneError::UnknownTable)?;
        let cells = unique_cells(&command.cells)?;
        if cells.is_empty() {
            return Err(TuneError::EmptySelection);
        }

        let mut changes = Vec::with_capacity(cells.len());
        for coord in cells {
            let index = table.index(coord)?;
            if !table.editable[index] {
                return Err(TuneError::ProtectedCell);
            }
            let before = table.values[index];
            let after = table.apply_operation(before, command.operation)?;
            changes.push(CellChange {
                coord,
                before,
                after,
            });
        }

        Ok(TablePreview {
            table_id: command.table_id,
            base_revision: self.revision,
            changes,
        })
    }

    pub fn apply(&mut self, command: &TableEditCommand) -> Result<u64, TuneError> {
        let preview = self.preview(command)?;
        self.commit_preview(preview)
    }

    pub fn commit_preview(&mut self, preview: TablePreview) -> Result<u64, TuneError> {
        if preview.base_revision != self.revision {
            return Err(TuneError::StalePreview);
        }
        if preview.changes.is_empty() {
            return Err(TuneError::EmptySelection);
        }

        let table = self
            .tables
            .get(&preview.table_id)
            .ok_or(TuneError::UnknownTable)?;

        let mut seen = BTreeSet::new();
        for change in &preview.changes {
            if !seen.insert(change.coord) {
                return Err(TuneError::DuplicateCell);
            }
            let index = table.index(change.coord)?;
            if !table.editable[index] {
                return Err(TuneError::ProtectedCell);
            }
            if table.values[index] != change.before {
                return Err(TuneError::StalePreview);
            }
            table.validate_canonical_value(change.after)?;
        }

        let table = self
            .tables
            .get_mut(&preview.table_id)
            .ok_or(TuneError::UnknownTable)?;
        for change in &preview.changes {
            let index = table.index(change.coord)?;
            table.values[index] = change.after;
        }

        self.undo.push(HistoryEntry {
            table_id: preview.table_id,
            changes: preview.changes,
        });
        self.redo.clear();
        self.advance_revision();
        Ok(self.revision)
    }

    pub fn undo(&mut self) -> Result<u64, TuneError> {
        let entry = self
            .undo
            .last()
            .cloned()
            .ok_or(TuneError::NothingToUndo)?;
        self.apply_history(&entry, false)?;
        self.undo.pop();
        self.redo.push(entry);
        self.advance_revision();
        Ok(self.revision)
    }

    pub fn redo(&mut self) -> Result<u64, TuneError> {
        let entry = self
            .redo
            .last()
            .cloned()
            .ok_or(TuneError::NothingToRedo)?;
        self.apply_history(&entry, true)?;
        self.redo.pop();
        self.undo.push(entry);
        self.advance_revision();
        Ok(self.revision)
    }

    fn apply_history(&mut self, entry: &HistoryEntry, forward: bool) -> Result<(), TuneError> {
        let table = self
            .tables
            .get(&entry.table_id)
            .ok_or(TuneError::UnknownTable)?;
        for change in &entry.changes {
            let index = table.index(change.coord)?;
            let expected = if forward { change.before } else { change.after };
            if table.values[index] != expected {
                return Err(TuneError::HistoryConflict);
            }
        }

        let table = self
            .tables
            .get_mut(&entry.table_id)
            .ok_or(TuneError::UnknownTable)?;
        for change in &entry.changes {
            let index = table.index(change.coord)?;
            table.values[index] = if forward { change.after } else { change.before };
        }
        Ok(())
    }

    fn advance_revision(&mut self) {
        self.revision = self.revision.saturating_add(1);
    }
}

impl TableSnapshot {
    fn index(&self, coord: CellCoord) -> Result<usize, TuneError> {
        if coord.row >= self.descriptor.rows || coord.col >= self.descriptor.cols {
            return Err(TuneError::CellOutOfBounds);
        }
        coord
            .row
            .checked_mul(self.descriptor.cols)
            .and_then(|base| base.checked_add(coord.col))
            .ok_or(TuneError::CellOutOfBounds)
    }

    fn validate_canonical_value(&self, value: f64) -> Result<(), TuneError> {
        if !value.is_finite() {
            return Err(TuneError::NonFiniteValue);
        }
        if value < self.descriptor.minimum || value > self.descriptor.maximum {
            return Err(TuneError::ValueOutOfRange);
        }
        if !is_quantized(
            value,
            self.descriptor.quantum_origin,
            self.descriptor.quantum,
        ) {
            return Err(TuneError::UnrepresentableValue);
        }
        Ok(())
    }

    fn normalize_edit_value(&self, value: f64) -> Result<f64, TuneError> {
        if !value.is_finite() {
            return Err(TuneError::NonFiniteValue);
        }
        let steps = ((value - self.descriptor.quantum_origin) / self.descriptor.quantum).round();
        let normalized = self.descriptor.quantum_origin + steps * self.descriptor.quantum;
        self.validate_canonical_value(normalized)?;
        Ok(normalized)
    }

    fn apply_operation(
        &self,
        current: f64,
        operation: TableEditOperation,
    ) -> Result<f64, TuneError> {
        let raw = match operation {
            TableEditOperation::Set(value) => value,
            TableEditOperation::Add(value) => current + value,
            TableEditOperation::Scale(value) => current * value,
            TableEditOperation::Increment => current + self.descriptor.edit_increment,
            TableEditOperation::Decrement => current - self.descriptor.edit_increment,
        };
        self.normalize_edit_value(raw)
    }
}

fn unique_cells(cells: &[CellCoord]) -> Result<Vec<CellCoord>, TuneError> {
    let mut seen = BTreeSet::new();
    let mut unique = Vec::with_capacity(cells.len());
    for coord in cells {
        if !seen.insert(*coord) {
            return Err(TuneError::DuplicateCell);
        }
        unique.push(*coord);
    }
    Ok(unique)
}

fn is_quantized(value: f64, origin: f64, quantum: f64) -> bool {
    let steps = (value - origin) / quantum;
    (steps - steps.round()).abs() <= 1e-9
}

fn is_multiple(value: f64, quantum: f64) -> bool {
    let steps = value / quantum;
    (steps - steps.round()).abs() <= 1e-9
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum TuneError {
    DuplicateTableId,
    InvalidDescriptor,
    InvalidShape,
    UnknownTable,
    EmptySelection,
    DuplicateCell,
    CellOutOfBounds,
    ProtectedCell,
    NonFiniteValue,
    ValueOutOfRange,
    UnrepresentableValue,
    StalePreview,
    NothingToUndo,
    NothingToRedo,
    HistoryConflict,
}

#[cfg(test)]
mod tests {
    use super::*;

    fn fixture_snapshot() -> TuneSnapshot {
        TuneSnapshot::new(
            42,
            TuneSnapshotOrigin::OfflineFixture,
            [TableSnapshot {
                descriptor: TableDescriptor {
                    id: 7,
                    name: "VE Table 1".to_string(),
                    rows: 2,
                    cols: 3,
                    minimum: 0.0,
                    maximum: 200.0,
                    quantum: 0.25,
                    quantum_origin: 0.0,
                    edit_increment: 0.5,
                    display_decimals: 2,
                },
                x_axis: vec![1000.0, 2000.0, 3000.0],
                y_axis: vec![40.0, 100.0],
                values: vec![50.0, 55.0, 60.0, 70.0, 75.0, 80.0],
                editable: vec![true, true, true, true, false, true],
            }],
        )
        .unwrap()
    }

    #[test]
    fn draft_is_independent_from_immutable_baseline() {
        let baseline = fixture_snapshot();
        let mut draft = TuneDraft::from_snapshot(&baseline);

        draft
            .apply(&TableEditCommand {
                table_id: 7,
                cells: vec![CellCoord { row: 0, col: 0 }],
                operation: TableEditOperation::Set(99.75),
            })
            .unwrap();

        assert_eq!(baseline.tables[&7].values[0], 50.0);
        assert_eq!(draft.table_projection(7).unwrap().values[0], 99.75);
        assert_eq!(draft.origin(), TuneSnapshotOrigin::OfflineFixture);
        assert_eq!(draft.profile_generation(), 42);
    }

    #[test]
    fn multi_cell_edit_is_atomic_when_any_cell_is_protected() {
        let mut draft = TuneDraft::from_snapshot(&fixture_snapshot());
        let before = draft.table_projection(7).unwrap();

        let result = draft.apply(&TableEditCommand {
            table_id: 7,
            cells: vec![CellCoord { row: 0, col: 0 }, CellCoord { row: 1, col: 1 }],
            operation: TableEditOperation::Add(5.0),
        });

        assert_eq!(result, Err(TuneError::ProtectedCell));
        assert_eq!(draft.table_projection(7).unwrap(), before);
        assert_eq!(draft.revision(), 0);
        assert!(!draft.can_undo());
    }

    #[test]
    fn multi_cell_edit_is_one_history_transaction() {
        let mut draft = TuneDraft::from_snapshot(&fixture_snapshot());
        let revision = draft
            .apply(&TableEditCommand {
                table_id: 7,
                cells: vec![CellCoord { row: 0, col: 0 }, CellCoord { row: 0, col: 1 }],
                operation: TableEditOperation::Scale(1.1),
            })
            .unwrap();

        assert_eq!(revision, 1);
        assert_eq!(
            &draft.table_projection(7).unwrap().values[..2],
            &[55.0, 60.5]
        );
        assert!(draft.can_undo());

        draft.undo().unwrap();
        assert_eq!(
            &draft.table_projection(7).unwrap().values[..2],
            &[50.0, 55.0]
        );
        assert!(!draft.can_undo());
        assert!(draft.can_redo());

        draft.redo().unwrap();
        assert_eq!(
            &draft.table_projection(7).unwrap().values[..2],
            &[55.0, 60.5]
        );
    }

    #[test]
    fn failed_range_validation_does_not_partially_mutate() {
        let mut draft = TuneDraft::from_snapshot(&fixture_snapshot());
        let before = draft.table_projection(7).unwrap();

        let result = draft.apply(&TableEditCommand {
            table_id: 7,
            cells: vec![CellCoord { row: 0, col: 0 }, CellCoord { row: 1, col: 2 }],
            operation: TableEditOperation::Add(150.0),
        });

        assert_eq!(result, Err(TuneError::ValueOutOfRange));
        assert_eq!(draft.table_projection(7).unwrap(), before);
    }

    #[test]
    fn increment_is_independent_from_display_precision() {
        let mut draft = TuneDraft::from_snapshot(&fixture_snapshot());
        let cell = CellCoord { row: 0, col: 0 };

        draft
            .apply(&TableEditCommand {
                table_id: 7,
                cells: vec![cell],
                operation: TableEditOperation::Increment,
            })
            .unwrap();
        assert_eq!(draft.table_projection(7).unwrap().values[0], 50.5);

        draft
            .apply(&TableEditCommand {
                table_id: 7,
                cells: vec![cell],
                operation: TableEditOperation::Decrement,
            })
            .unwrap();
        assert_eq!(draft.table_projection(7).unwrap().values[0], 50.0);
        assert_eq!(draft.table_projection(7).unwrap().descriptor.display_decimals, 2);
    }

    #[test]
    fn arbitrary_edit_is_quantized_to_profile_value_domain() {
        let mut draft = TuneDraft::from_snapshot(&fixture_snapshot());

        draft
            .apply(&TableEditCommand {
                table_id: 7,
                cells: vec![CellCoord { row: 0, col: 0 }],
                operation: TableEditOperation::Set(50.37),
            })
            .unwrap();

        assert_eq!(draft.table_projection(7).unwrap().values[0], 50.25);
    }

    #[test]
    fn preview_is_non_mutating_and_stale_preview_is_rejected() {
        let mut draft = TuneDraft::from_snapshot(&fixture_snapshot());
        let preview = draft
            .preview(&TableEditCommand {
                table_id: 7,
                cells: vec![CellCoord { row: 0, col: 0 }],
                operation: TableEditOperation::Set(66.75),
            })
            .unwrap();

        assert_eq!(draft.table_projection(7).unwrap().values[0], 50.0);
        assert_eq!(preview.base_revision, 0);
        assert_eq!(preview.changes[0].after, 66.75);

        draft
            .apply(&TableEditCommand {
                table_id: 7,
                cells: vec![CellCoord { row: 0, col: 1 }],
                operation: TableEditOperation::Set(77.7),
            })
            .unwrap();

        assert_eq!(draft.commit_preview(preview), Err(TuneError::StalePreview));
        assert_eq!(draft.table_projection(7).unwrap().values[0], 50.0);
    }

    #[test]
    fn history_conflict_does_not_drop_the_undo_entry() {
        let mut draft = TuneDraft::from_snapshot(&fixture_snapshot());
        draft
            .apply(&TableEditCommand {
                table_id: 7,
                cells: vec![CellCoord { row: 0, col: 0 }],
                operation: TableEditOperation::Set(60.0),
            })
            .unwrap();

        draft.tables.get_mut(&7).unwrap().values[0] = 61.0;
        assert_eq!(draft.undo(), Err(TuneError::HistoryConflict));
        assert!(draft.can_undo());
    }

    #[test]
    fn projection_is_representation_neutral_for_2d_and_3d_consumers() {
        let mut draft = TuneDraft::from_snapshot(&fixture_snapshot());
        let initial = draft.table_projection(7).unwrap();
        assert_eq!(initial.draft_revision, 0);
        assert_eq!(initial.descriptor.rows, 2);
        assert_eq!(initial.descriptor.cols, 3);

        draft
            .apply(&TableEditCommand {
                table_id: 7,
                cells: vec![CellCoord { row: 1, col: 2 }],
                operation: TableEditOperation::Add(2.5),
            })
            .unwrap();

        let changed = draft.table_projection(7).unwrap();
        assert_eq!(changed.draft_revision, 1);
        assert_eq!(changed.values[5], 82.5);
    }
}
