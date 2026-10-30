#!/usr/bin/env python3
"""
Render the project's Markdown reports as Word documents.

Produces, in docs/docx/:
  - one .docx per task report
  - Attendance-Portal-Full-Report.docx, the fifteen combined

Two things this does that a bare `pandoc report.md -o report.docx` does not:

  1. Mermaid diagrams are rendered to images. Pandoc passes a ```mermaid
     fence through as a code block, so an architecture diagram arrives in
     the Word file as its own source text - accurate, unreadable.

  2. Repository-relative links are rewritten to absolute GitHub URLs. A
     link to ../proofs/stage-10/failed-pipeline.log resolves against the
     docs/ directory and means nothing once the text is a .docx, so every
     evidence link in the report would be dead on arrival.

Requires: pandoc, mermaid-cli (mmdc), a Chromium for it to drive.
"""
import datetime
import os
import re
import subprocess
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
DOCS = REPO / "docs"
OUT = DOCS / "docx"
WORK = Path(os.environ.get("DOCX_WORK", "/tmp/docx-build"))
BLOB = ("https://github.com/Swaroop192005/"
        "CI-CD-Pipeline-for-a-Student-Attendance-Management/blob/main")

MMDC = os.environ.get("MMDC", "mmdc")
# mermaid-cli drives a headless Chromium, which refuses to start as root
# without --no-sandbox. Point this at a JSON file holding
#   { "args": ["--no-sandbox", "--disable-gpu", "--disable-dev-shm-usage"] }
# Without it every diagram fails and the script falls back to printing the
# mermaid source, which still produces a report -- just one with no
# diagrams in it.
PUPPETEER_CFG = os.environ.get("PUPPETEER_CFG", "")


def sh(cmd, **kw):
    return subprocess.run(cmd, check=True, capture_output=True, text=True, **kw)


def render_mermaid(md: str, stem: str, img_dir: Path) -> str:
    """Replace each ```mermaid fence with an image of the rendered diagram."""
    img_dir.mkdir(parents=True, exist_ok=True)
    blocks = []

    def take(m):
        blocks.append(m.group(1))
        return f"@@MERMAID{len(blocks) - 1}@@"

    md = re.sub(r"```mermaid\n(.*?)\n```", take, md, flags=re.S)

    for i, src in enumerate(blocks):
        mmd = img_dir / f"{stem}-{i}.mmd"
        png = img_dir / f"{stem}-{i}.png"
        mmd.write_text(src)
        # -s 3 is about resolution, not size on the page. A wide diagram
        # still lands at text width and reads small there; rendering it at
        # roughly 385 dpi means it stays sharp when the reader zooms in or
        # prints, instead of turning to mush.
        cmd = [MMDC, "-i", str(mmd), "-o", str(png), "-b", "white", "-s", "3"]
        if PUPPETEER_CFG:
            cmd += ["-p", PUPPETEER_CFG]
        try:
            sh(cmd)
            # A diagram that overflows the text column is worse than one
            # that is slightly small, so cap the width rather than let
            # Word scale it to the page and clip.
            repl = f"![]({png.as_posix()}){{width=15.5cm}}"
        except subprocess.CalledProcessError as e:
            print(f"  ! diagram {i} in {stem} did not render, keeping source",
                  file=sys.stderr)
            print(f"    {e.stderr.strip().splitlines()[-1:]}", file=sys.stderr)
            repl = "```\n" + src + "\n```"
        md = md.replace(f"@@MERMAID{i}@@", repl)
    return md


def absolutise_links(md: str) -> str:
    """Point every repository-relative link at GitHub so it still works."""
    def fix(m):
        text, target = m.group(1), m.group(2)
        if re.match(r"^(https?:|#|mailto:)", target):
            return m.group(0)
        clean = target.split("#")[0]
        if clean.startswith("../"):
            path = clean[3:]
        elif clean.startswith("./"):
            path = "docs/" + clean[2:]
        else:
            path = "docs/" + clean
        return f"[{text}]({BLOB}/{path})"

    # (?<!!) so this never touches an image. Without it the rewriter also
    # matches the [](...) inside ![](...) and turns a rendered diagram's
    # local path into a GitHub URL - which pandoc then fetches, receives a
    # 404 page for, and embeds as a 190 KB .shtml "image".
    return re.sub(r"(?<!!)\[([^\]]*)\]\(([^)]+)\)", fix, md)


PAGE_BREAK = ("```{=openxml}\n"
              "<w:p><w:r><w:br w:type=\"page\"/></w:r></w:p>\n"
              "```\n")


def paginate_chapters(md: str) -> str:
    """Start the body, and each chapter, on a fresh page.

    Only for the combined document: in a 166-page submission a chapter
    that begins halfway down the page someone else's report ended on
    reads as a continuation of it.
    """
    # Track fenced code blocks. '# 1. Clone' inside a bash snippet is a
    # shell comment, not a heading, and injecting a page break there puts
    # a ``` fence inside the fence - which closes it early and turns the
    # rest of the snippet, and every later '#' line, into prose.
    lines = md.splitlines(keepends=True)
    out, seen_h1, fence = [], False, None
    for line in lines:
        stripped = line.lstrip()
        if fence is None:
            m = re.match(r"(`{3,}|~{3,})", stripped)
            if m:
                fence = m.group(1)[0] * 3
        elif re.match(r"(`{3,}|~{3,})\s*$", stripped) and stripped[0] == fence[0]:
            fence = None

        if fence is None and line.startswith("# ") and not line.startswith("##"):
            if seen_h1:
                out.append("\n" + PAGE_BREAK + "\n")
            seen_h1 = True
        out.append(line)
    # And one before everything, so the contents page stands alone.
    return PAGE_BREAK + "\n" + "".join(out)


def prepare(src: Path, img_dir: Path, paginate: bool = False) -> Path:
    md = src.read_text()
    md = absolutise_links(md)
    md = render_mermaid(md, src.stem, img_dir)
    if paginate:
        md = paginate_chapters(md)
    dst = WORK / f"{src.stem}.prepared.md"
    dst.write_text(md)
    return dst


def to_docx(inputs, out: Path, title=None, toc=False):
    cmd = ["pandoc", *[str(i) for i in inputs], "-o", str(out),
           "--from", "markdown+pipe_tables+backtick_code_blocks",
           "--resource-path", f"{WORK}:{REPO}:{DOCS}",
           "--highlight-style", "tango"]
    if toc:
        cmd += ["--toc", "--toc-depth=2"]
    if title:
        cmd += ["--metadata", f"title={title}"]
    sh(cmd)

    # pandoc reports a missing image as a warning and carries on, so check
    # the result rather than the exit code: every embedded medium must be
    # a real image, not an error page fetched over the network.
    listing = sh(["unzip", "-l", str(out)]).stdout
    bad = [l.split()[-1] for l in listing.splitlines()
           if "word/media/" in l
           and not l.rstrip().endswith((".png", ".jpg", ".jpeg", ".gif", ".svg"))]
    if bad:
        raise SystemExit(f"{out.name}: embedded non-image media {bad}")


# Long command lines and long table cells are everywhere in these
# reports, and LaTeX does not wrap inside a verbatim environment: an
# unwrapped line runs off the page edge and is simply lost. fvextra
# makes code wrap; the rest is the running header and a little space
# around the code blocks.
PDF_HEADER = r"""
\usepackage{fvextra}
\DefineVerbatimEnvironment{Highlighting}{Verbatim}{breaklines,breakanywhere,commandchars=\\\{\}}
\usepackage{fancyhdr}
\pagestyle{fancy}
\fancyhf{}
\fancyhead[L]{\footnotesize\nouppercase{\leftmark}}
\fancyfoot[C]{\thepage}
\renewcommand{\headrulewidth}{0.4pt}
\usepackage{etoolbox}
\AtBeginEnvironment{Shaded}{\small}
\setlength{\emergencystretch}{3em}
"""


def to_pdf(src: Path, out: Path, title: str, subtitle: str,
           author: str, date: str):
    cmd = [
        "pandoc", str(src), "-o", str(out),
        "--pdf-engine", "xelatex",
        "--from", "markdown+pipe_tables+backtick_code_blocks",
        "--resource-path", f"{WORK}:{REPO}:{DOCS}",
        "--toc", "--toc-depth=2",
        "--highlight-style", "tango",
        "-V", "documentclass=report",
        "-V", "geometry:a4paper,margin=2.4cm",
        # DejaVu throughout: it is the only family installed here that
        # covers the box-drawing characters the directory trees are made
        # of, and a missing glyph in a 180-page PDF is easy to miss.
        "-V", "mainfont=DejaVu Serif",
        "-V", "sansfont=DejaVu Sans",
        "-V", "monofont=DejaVu Sans Mono",
        "-V", "fontsize=10pt",
        "-V", "linkcolor=blue", "-V", "urlcolor=blue", "-V", "toccolor=black",
        "-V", "colorlinks=true",
        "-V", f"header-includes={PDF_HEADER}",
        "-M", f"title={title}", "-M", f"subtitle={subtitle}",
        "-M", f"author={author}", "-M", f"date={date}",
    ]
    sh(cmd)


def main():
    WORK.mkdir(parents=True, exist_ok=True)
    OUT.mkdir(parents=True, exist_ok=True)
    img_dir = WORK / "img"

    reports = sorted(DOCS.glob("stage-*.md"))
    env = DOCS / "00-environment-prerequisites.md"
    if env.exists():
        reports.insert(0, env)

    print(f"Converting {len(reports)} reports")
    for src in reports:
        prepared = prepare(src, img_dir)
        out = OUT / f"{src.stem}.docx"
        to_docx([prepared], out)
        print(f"  ✓ {out.name}  ({out.stat().st_size // 1024} KB)")

    # The combined document is built from docs/FINAL-REPORT.md, which is the
    # curated concatenation the project already maintains - not a re-stitch
    # of the files above, which would drift from it.
    combined = DOCS / "FINAL-REPORT.md"
    if combined.exists():
        print("Combining")
        prepared = prepare(combined, img_dir, paginate=True)
        out = OUT / "Attendance-Portal-Full-Report.docx"
        to_docx([prepared], out,
                title="Student Attendance Management Portal - CI/CD Pipeline",
                toc=True)
        print(f"  ✓ {out.name}  ({out.stat().st_size // 1024} KB)")

        if os.environ.get("SKIP_PDF") != "1":
            print("Typesetting the PDF")
            pdf = OUT / "Attendance-Portal-Full-Report.pdf"
            to_pdf(prepared, pdf,
                   "Student Attendance Management Portal",
                   "Continuous Integration and Delivery Pipeline",
                   "Swaroop Naik — Roll 23102A0001",
                   datetime.date.today().strftime("%d %B %Y"))
            print(f"  ✓ {pdf.name}  ({pdf.stat().st_size // 1024} KB)")

    print(f"\nWritten to {OUT.relative_to(REPO)}/")


if __name__ == "__main__":
    main()
