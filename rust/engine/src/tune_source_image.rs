#[derive(Clone, Debug, Eq, PartialEq)]
pub struct TuneSourcePageImage {
    pages: Vec<Vec<u8>>,
}

impl TuneSourcePageImage {
    pub fn from_pages(pages: &[&[u8]]) -> Self {
        Self {
            pages: pages.iter().map(|page| page.to_vec()).collect(),
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
    }

    #[test]
    fn zero_page_source_is_distinct_from_missing_source_authority() {
        let image = TuneSourcePageImage::from_pages(&[]);

        assert_eq!(image.page_count(), 0);
        assert!(image.pages().is_empty());
    }
}
