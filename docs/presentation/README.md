# Presentation and poster

**Swaroop Naik** · Roll 23102A0001

| File | What it is |
|---|---|
| `CI-CD-Project-Presentation.pptx` | 15-slide deck, 16:9, roughly 25 minutes with questions |
| `CI-CD-Project-Poster.pdf` | A2 portrait poster, one sheet, prints down to A3 |
| `CI-CD-Project-Poster.png` | The same poster at 150 dpi, for screens and quick printing |
| `build-deck.js` | The pptxgenjs generator the deck is built from |
| `poster-source.html` | The poster's source, rendered to PDF by headless Chromium |

Both are generated from source rather than hand-placed, so a corrected
figure means editing one line and rebuilding, not nudging a text box.

## Rebuilding

```bash
# deck
cd docs/presentation && npm install pptxgenjs && node build-deck.js

# poster (any headless Chromium)
chromium --headless --no-pdf-header-footer \
  --print-to-pdf=CI-CD-Project-Poster.pdf poster-source.html
```

## Deck structure

| # | Slide | Point |
|---|---|---|
| 1 | Title | |
| 2 | The problem | 9–14 day delay against immediate visibility |
| 3 | Technology stack | versions as the build resolves them |
| 4–5 | The pipeline | eleven stages; the gate sits at six, before the image |
| 6 | What each tool is for | Docker, Jenkins, Selenium, Ansible, Puppet, Maven |
| 7–9 | Verification | 129 tests, three bugs they caught, coverage read honestly |
| 10–11 | Delivery | one artefact promoted; idempotence |
| 12 | Results | the measured figures |
| 13 | Limitations | what the project does not solve |
| 14 | Close | every concept answers a "how do you know" question |

Speaker notes are on the slides that need them.

## A note on the figures

Every number on both artefacts is taken from the repository, not recalled:
129 tests from the sources, 67.9% and 85.8% from the JaCoCo report, the
image tag from the registry listing. If an examiner checks one against the
code, it matches.
