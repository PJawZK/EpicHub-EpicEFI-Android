use crate::ram_source_provenance::RamSourceProvenance;

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct TuneSourcePageImage {
    pages: Vec<Vec<u8>>,
    provenance: Option<RamSourceProvenance>,
}

impl TuneSourcePageImage {
    pub fn from_pages(pages: &[&[u8]]) -> Self {
        Self {
            pages: pages.iter().map(|page| page.to_vec()).collect(),
            provenance: None,
        }
    }

    pub fn from_live_controller_pages(
        profile_generation: u64,
        draft_revision: u64,
        pages: &[&[u8]],
    ) -> Self {
        Self {
            pages: pages.iter().map(|page| page.to_vec()).collect(),
            provenance: Some(RamSourceProvenance::from_live_controller_read(
                profile_generation,
                draft_revision,
            )),
        }
    }

    pub fn pages(&self) -> &[Vec<u8>] {
        &self.pages
    }

    pub fn page(&self, index: usize) -> Option<&[u8]> {
        self.pages.get(index).map(Vec::as_slice)
    }

    pub fn page_count(&self) -> usize {
        self.pages.len()
    }

    pub fn provenance(&self) -> Option<&RamSourceProvenance> {
        self.provenance.as_ref()
    }

    pub fn with_pages_preserving_provenance(&self, pages: &[&[u8]]) -> Self {
        Self {
            pages: pages.iter().map(|page| page.to_vec()).collect(),
            provenance: self.provenance.clone(),
        }
    }

    pub fn with_provenance(mut self, provenance: RamSourceProvenance) -> Self {
        self.provenance = Some(provenance);
        self
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn image_owns_an_exact_copy_of_every_source_page() {
        let mut first = vec![0x11, 0x22, 0x33];
        let second = vec![0x44, 0x55];
        let image = TuneSourcePageImage::from_pages(&[&first, &second]);

        first[1] = 0x99;

        assert_eq!(image.page_count(), 2);
        assert_eq!(image.page(0), Some(&[0x11, 0x22, 0x33][..]));
        assert_eq!(image.page(1), Some(&[0x44, 0x55][..]));
        assert_eq!(image.page(2), None);
        assert_eq!(image.provenance(), None);
    }

    #[test]
    fn live_controller_image_carries_acquisition_provenance() {
        let image = TuneSourcePageImage::from_live_controller_pages(77, 0, &[&[1_u8, 2, 3]]);
        let provenance = image.provenance().unwrap();

        assert_eq!(provenance.profile_generation, 77);
        assert_eq!(provenance.confirmed_draft_revision, Some(0));
    }

    #[test]
    fn page_replacement_preserves_provenance_without_mutating_original() {
        let image = TuneSourcePageImage::from_live_controller_pages(77, 0, &[&[1_u8, 2, 3]]);
        let replaced = image.with_pages_preserving_provenance(&[&[4_u8, 5, 6]]);

        assert_eq!(image.page(0), Some(&[1, 2, 3][..]));
        assert_eq!(replaced.page(0), Some(&[4, 5, 6][..]));
        assert_eq!(replaced.provenance(), image.provenance());
    }

    #[test]
    fn zero_page_source_is_distinct_from_missing_source_authority() {
        let image = TuneSourcePageImage::from_pages(&[]);

        assert_eq!(image.page_count(), 0);
        assert!(image.pages().is_empty());
    }
}
