export interface FeatureIntroOptions {
  title: string;
  purpose: string;
  next: readonly string[];
}

export function renderFeatureIntro(options: FeatureIntroOptions): HTMLElement {
  const section = document.createElement("section");
  section.className = "feature-intro";

  const title = document.createElement("h1");
  title.textContent = options.title;

  const purpose = document.createElement("p");
  purpose.textContent = options.purpose;

  const list = document.createElement("ul");
  for (const item of options.next) {
    const entry = document.createElement("li");
    entry.textContent = item;
    list.append(entry);
  }

  section.append(title, purpose, list);
  return section;
}
