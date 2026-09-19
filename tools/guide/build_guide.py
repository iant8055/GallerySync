#!/usr/bin/env python3
"""
Builds the How To Guide, in every form it takes, from the one source in `content/`.

    python tools/guide/build_guide.py           write the outputs
    python tools/guide/build_guide.py --check   exit 1 if any output is out of date

Outputs
-------
  docs/how-to-guide.html                                  the guide as one long page (Privacy Policy style)
  docs/how-to-guide-accordion.html                        the same guide as expandable sections
  docs/setup-guide.html                                   the first-time setup chapter on its own, for the wizard
  app/src/main/res/values/help_topics.xml                 the text the (?) pop-ups show
  app/src/main/java/com/gallery/sync/ui/help/HelpTopic.kt the list of topics the app can open

Why one source
--------------
The (?) pop-ups in the app and the published guide must say the same thing. Written twice they would
drift, and a help screen that disagrees with the guide is worse than none. Everything is generated
from `content/`, and HowToGuideConsistencyTest fails if a pop-up says anything the guide does not.

Body format (the same in every topic)
-------------------------------------
  ## Heading            a section heading, on its own line
  a paragraph           consecutive lines join into one paragraph; a blank line ends it
  • one                 a paragraph whose every line starts "• " is a bullet list
  1. one                a paragraph whose every line starts "1. ", "2. " ... is a numbered list
  **bold**              inline emphasis
  [[topic-id]]          the title of another topic, in quotes (a link in the HTML)
  {{full: text}}        text that belongs to the full guide only. The setup page leaves it out, because
                        what it points at is not on that page and the wizard cannot reach it.
"""
from __future__ import annotations

import html
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(Path(__file__).resolve().parent))

from content import CHAPTERS, LAST_UPDATED  # noqa: E402
from model import Topic  # noqa: E402

OUT_FLAT = ROOT / "docs" / "how-to-guide.html"
OUT_ACCORDION = ROOT / "docs" / "how-to-guide-accordion.html"
OUT_SETUP = ROOT / "docs" / "setup-guide.html"
OUT_XML = ROOT / "app" / "src" / "main" / "res" / "values" / "help_topics.xml"
OUT_KOTLIN = ROOT / "app" / "src" / "main" / "java" / "com" / "gallery" / "sync" / "ui" / "help" / "HelpTopic.kt"


# ── Parsing ──────────────────────────────────────────────────────────────────

Block = tuple  # ("h"|"p", text) | ("ul"|"ol", [text, ...])


def parse(body: str) -> list[Block]:
    """Blank-line separated chunks, each split into runs: headings, paragraphs, bullets, numbers."""
    blocks: list[Block] = []
    for chunk in re.split(r"\n[ \t]*\n", body.strip()):
        kind: str | None = None
        buf: list[str] = []

        def flush() -> None:
            nonlocal kind, buf
            if buf:
                blocks.append(("p", " ".join(buf)) if kind == "p" else (kind, buf))
            kind, buf = None, []

        for raw in chunk.split("\n"):
            line = raw.strip()
            if not line:
                continue
            if line.startswith("## "):
                flush()
                blocks.append(("h", line[3:].strip()))
                continue
            if line.startswith("• "):
                this, text = "ul", line[2:].strip()
            elif re.match(r"\d+\. ", line):
                this, text = "ol", re.sub(r"^\d+\. ", "", line).strip()
            else:
                this, text = "p", line
            if this != kind:
                flush()
                kind = this
            buf.append(text)
        flush()
    return blocks


LINK = re.compile(r"\[\[([a-z0-9-]+)\]\]")
BOLD = re.compile(r"\*\*(.+?)\*\*")
FULL_ONLY = re.compile(r"[ ]?\{\{full:\s*(.*?)\}\}", re.S)


def for_guide(body: str) -> str:
    """The body as the full guide and the pop-ups have it: {{full: ...}} kept, markers dropped."""
    return FULL_ONLY.sub(lambda m: " " + m.group(1), body)


def for_setup(body: str) -> str:
    """The body as the standalone setup page has it: {{full: ...}} left out."""
    return FULL_ONLY.sub("", body)


def all_topics() -> dict[str, Topic]:
    found: dict[str, Topic] = {}
    for chapter in CHAPTERS:
        for t in chapter.topics:
            if t.id in found:
                raise SystemExit(f"duplicate topic id: {t.id}")
            if not re.fullmatch(r"[a-z0-9]+(-[a-z0-9]+)*", t.id):
                raise SystemExit(f"bad topic id (lowercase words joined by hyphens): {t.id}")
            t.chapter = chapter.id
            found[t.id] = t
    return found


def check_links(topics: dict[str, Topic]) -> None:
    for t in topics.values():
        for slug in LINK.findall(for_guide(t.body)):
            if slug not in topics:
                raise SystemExit(f"{t.id}: links to unknown topic [[{slug}]]")
    for chapter in CHAPTERS:
        for slug in LINK.findall(chapter.intro):
            if slug not in topics:
                raise SystemExit(f"chapter {chapter.id}: links to unknown topic [[{slug}]]")


def plain(text: str, topics: dict[str, Topic]) -> str:
    """Inline markup removed: what a reader of the pop-up sees, minus the bold."""
    text = LINK.sub(lambda m: f"“{topics[m.group(1)].title}”", text)
    return text


def inline_html(text: str, topics: dict[str, Topic]) -> str:
    text = html.escape(text, quote=False)
    text = BOLD.sub(r"<strong>\1</strong>", text)
    text = LINK.sub(
        lambda m: f'<a class="xref" href="#{m.group(1)}">“{html.escape(topics[m.group(1)].title, quote=False)}”</a>',
        text,
    )
    return text


def blocks_html(body: str, topics: dict[str, Topic], heading_tag: str) -> str:
    out: list[str] = []
    for kind, value in parse(body):
        if kind == "h":
            out.append(f"<{heading_tag}>{html.escape(value, quote=False)}</{heading_tag}>")
        elif kind == "p":
            out.append(f"<p>{inline_html(value, topics)}</p>")
        else:
            tag = "ol" if kind == "ol" else "ul"
            items = "".join(f"<li>{inline_html(item, topics)}</li>" for item in value)
            out.append(f"<{tag}>{items}</{tag}>")
    return "\n".join(out)


# ── HTML ─────────────────────────────────────────────────────────────────────

CSS = """
  :root {
    --bg: #ffffff;
    --ink: #1a1a1a;
    --ink2: #4a4a4a;
    --rule: #e2e2e2;
    --accent: #2b5fd9;
    --note-bg: #f4f6fb;
  }
  @media (prefers-color-scheme: dark) {
    :root {
      --bg: #14161a;
      --ink: #ececec;
      --ink2: #b3b3b3;
      --rule: #2c2f36;
      --accent: #7ba3ff;
      --note-bg: #1c1f26;
    }
  }
  * { box-sizing: border-box; }
  body {
    margin: 0;
    padding: 40px 20px 80px;
    background: var(--bg);
    color: var(--ink);
    font: 16px/1.65 -apple-system, BlinkMacSystemFont, "Segoe UI", system-ui, sans-serif;
  }
  main { max-width: 680px; margin: 0 auto; }
  h1 { font-size: 28px; line-height: 1.25; margin: 0 0 8px; }
  .sub { color: var(--ink2); margin: 0 0 32px; }
  h2 { font-size: 21px; margin: 44px 0 12px; padding-top: 20px; border-top: 1px solid var(--rule); scroll-margin-top: 12px; }
  h3 { font-size: 17px; margin: 30px 0 6px; scroll-margin-top: 12px; }
  h4 { font-size: 13px; margin: 14px 0 4px; color: var(--ink); text-transform: uppercase; letter-spacing: 0.05em; }
  p, li { color: var(--ink2); }
  p { margin: 0 0 12px; }
  strong { color: var(--ink); }
  ul, ol { padding-left: 22px; margin: 0 0 12px; }
  li { margin-bottom: 8px; }
  a { color: var(--accent); }
  .note {
    background: var(--note-bg);
    border-left: 3px solid var(--accent);
    padding: 14px 16px;
    margin: 20px 0;
    border-radius: 0 6px 6px 0;
  }
  .note p { margin: 0; }
  .chapter-intro { margin-bottom: 8px; }
  .toc { padding-left: 20px; }
  .toc li { margin-bottom: 4px; }
  .toc ul { margin: 4px 0 10px; }
  .toc ul li { margin-bottom: 2px; font-size: 15px; }
  .switch { font-size: 15px; margin: 0 0 20px; }
  details.topic { border-bottom: 1px solid var(--rule); }
  details.topic > summary {
    cursor: pointer;
    list-style: none;
    padding: 14px 28px 14px 0;
    position: relative;
    font-size: 16px;
    font-weight: 600;
    color: var(--ink);
  }
  details.topic > summary::-webkit-details-marker { display: none; }
  details.topic > summary::after {
    content: "+";
    position: absolute;
    right: 4px;
    top: 50%;
    transform: translateY(-50%);
    font-size: 22px;
    font-weight: 400;
    color: var(--accent);
  }
  details.topic[open] > summary::after { content: "\\2212"; }
  .topic-body { padding: 0 0 10px; scroll-margin-top: 96px; }
  .topic-body:target { outline: 2px solid var(--accent); outline-offset: 6px; border-radius: 4px; }
  footer { margin-top: 48px; padding-top: 20px; border-top: 1px solid var(--rule); font-size: 14px; color: var(--ink2); }
"""


def page(title: str, sub: str, body: str, heading: str = "How To Guide", name: str = "How To Guide") -> str:
    return f"""<!DOCTYPE html>
<!-- GENERATED by tools/guide/build_guide.py from tools/guide/content/. Do not edit this file: change the source and rebuild. -->
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="color-scheme" content="light dark">
<title>{html.escape(title)}</title>
<style>{CSS}</style>
</head>
<body>
<main>

  <h1>{html.escape(heading)}</h1>
  <p class="sub">{html.escape(sub)}</p>

{body}

  <footer>
    Gallery Sync — {html.escape(name)}. Last updated {LAST_UPDATED}.
  </footer>

</main>
</body>
</html>
"""


def intro_note(switch_html: str) -> str:
    return f"""  <div class="note">
    <p><strong>Every screen, every line.</strong> This guide explains each part of Gallery Sync in plain
    language &mdash; what you are looking at, and where the information comes from. Inside the app, the
    <strong>(?)</strong> buttons open the same explanations as a pop-up.</p>
  </div>
  <p class="switch">{switch_html}</p>"""


def toc(topics: dict[str, Topic], with_topics: bool) -> str:
    rows: list[str] = []
    for chapter in CHAPTERS:
        row = f'<li><a href="#ch-{chapter.id}">{html.escape(chapter.title)}</a>'
        if with_topics:
            items = "".join(
                f'<li><a href="#{t.id}">{html.escape(t.title)}</a></li>' for t in chapter.topics
            )
            row += f"<ul>{items}</ul>"
        rows.append(row + "</li>")
    return '  <h2 id="contents" style="border-top:0;margin-top:24px">Contents</h2>\n  <ul class="toc">' + "".join(rows) + "</ul>"


def build_flat(topics: dict[str, Topic]) -> str:
    parts = [
        intro_note('Prefer collapsible sections? <a href="how-to-guide-accordion.html">Open the expandable version.</a>'),
        toc(topics, with_topics=True),
    ]
    for chapter in CHAPTERS:
        parts.append(f'  <h2 id="ch-{chapter.id}">{html.escape(chapter.title)}</h2>')
        parts.append(f'  <p class="chapter-intro">{inline_html(chapter.intro, topics)}</p>')
        for t in chapter.topics:
            parts.append(f'  <h3 id="{t.id}">{html.escape(t.title)}</h3>')
            parts.append(blocks_html(for_guide(t.body), topics, "h4"))
    return page("How To Guide — Gallery Sync", f"Gallery Sync — last updated {LAST_UPDATED}", "\n".join(parts))


def build_accordion(topics: dict[str, Topic]) -> str:
    parts = [
        intro_note('Prefer one long page? <a href="how-to-guide.html">Open the single-page version.</a> '
                   "Tap a heading to open it."),
        toc(topics, with_topics=False),
    ]
    for chapter in CHAPTERS:
        parts.append(f'  <h2 id="ch-{chapter.id}">{html.escape(chapter.title)}</h2>')
        parts.append(f'  <p class="chapter-intro">{inline_html(chapter.intro, topics)}</p>')
        for t in chapter.topics:
            # The id sits on the body, not on <details>: a link to something *inside* a closed
            # <details> opens it, where a link to the <details> element itself does not.
            parts.append(
                f'  <details class="topic"><summary>{html.escape(t.title)}</summary>\n'
                f'  <div class="topic-body" id="{t.id}">\n{blocks_html(for_guide(t.body), topics, "h4")}\n  </div></details>'
            )
    return page("How To Guide — Gallery Sync", f"Gallery Sync — last updated {LAST_UPDATED}", "\n".join(parts))


def build_setup(topics: dict[str, Topic]) -> str:
    """The first-time setup chapter as a page of its own.

    The wizard links to this and to nothing else. Someone part-way through setup has no use for the
    rest of the guide, and a page that led into it would let them wander off into chapters about tabs
    they cannot open yet. So every link on it must stay on it: a reference to a topic outside the
    chapter is a build error rather than a dead link, and `{{full: ...}}` is how the source marks the
    sentences that only make sense in the full guide.
    """
    chapter = next(c for c in CHAPTERS if c.id == "setup")
    ids = {t.id for t in chapter.topics}

    def stray(text: str) -> list[str]:
        return [slug for slug in LINK.findall(text) if slug not in ids]

    if stray(chapter.intro):
        raise SystemExit(f"setup page: the intro links outside the chapter: {stray(chapter.intro)}")

    parts = [
        """  <div class="note">
    <p><strong>Every card, in order.</strong> This page explains each step of Gallery Sync's first-time
    setup in plain language: what a card is for, what its buttons do, and the messages you might meet.
    Tap a heading to open it.</p>
  </div>""",
        f'  <p class="chapter-intro">{inline_html(chapter.intro, topics)}</p>',
    ]
    for t in chapter.topics:
        body = for_setup(t.body)
        if stray(body):
            raise SystemExit(
                f"setup page: {t.id} links outside the chapter: {stray(body)}. "
                "Wrap that sentence in {{full: ...}} so only the full guide has it."
            )
        parts.append(
            f'  <details class="topic"><summary>{html.escape(t.title)}</summary>\n'
            f'  <div class="topic-body" id="{t.id}">\n{blocks_html(body, topics, "h4")}\n  </div></details>'
        )
    return page(
        "First-Time Setup — Gallery Sync",
        f"Gallery Sync — last updated {LAST_UPDATED}",
        "\n".join(parts),
        heading="First-Time Setup",
        name="First-Time Setup",
    )


# ── Android resources and Kotlin ─────────────────────────────────────────────

def xml_escape(text: str) -> str:
    text = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    text = text.replace("\\", "\\\\").replace("'", "\\'").replace('"', '\\"')
    text = text.replace("\n", "\\n")
    if text[:1] in ("@", "?"):
        text = "\\" + text
    return text


def popup_body(t: Topic, topics: dict[str, Topic]) -> str:
    """The body as a pop-up shows it: same blocks, links resolved to titles, still with ** for bold."""
    out: list[str] = []
    for kind, value in parse(for_guide(t.body)):
        if kind == "h":
            out.append(f"## {value}")
        elif kind == "p":
            out.append(plain(value, topics))
        else:
            out.append("\n".join("• " + plain(item, topics) for item in value))
    return "\n\n".join(out)


def slug_to_name(slug: str) -> str:
    return slug.replace("-", "_")


def build_xml(topics: dict[str, Topic]) -> str:
    lines = [
        "<?xml version=\"1.0\" encoding=\"utf-8\"?>",
        "<!--",
        "    GENERATED by tools/guide/build_guide.py from tools/guide/content/. Do not edit.",
        "    The text the (?) pop-ups show. Change the guide's source and rebuild, or the two drift.",
        "-->",
        "<resources>",
    ]
    for t in topics.values():
        if not t.ui:
            continue
        name = slug_to_name(t.id)
        lines.append(f'    <string name="help_{name}_title" formatted="false">{xml_escape(t.title)}</string>')
        lines.append(f'    <string name="help_{name}_body" formatted="false">{xml_escape(popup_body(t, topics))}</string>')
    lines.append("</resources>")
    return "\n".join(lines) + "\n"


def build_kotlin(topics: dict[str, Topic]) -> str:
    entries = []
    for t in topics.values():
        if not t.ui:
            continue
        name = slug_to_name(t.id)
        entries.append(
            f'    {name.upper()}("{t.id}", R.string.help_{name}_title, R.string.help_{name}_body)'
        )
    return (
        "package com.gallery.sync.ui.help\n\n"
        "import androidx.annotation.StringRes\n"
        "import com.gallery.sync.R\n\n"
        "/**\n"
        " * Every explanation a (?) button can open.\n"
        " *\n"
        " * GENERATED by `tools/guide/build_guide.py` from `tools/guide/content/`. Do not edit: change the\n"
        " * guide's source and rebuild, so the pop-up, the published guide and this list cannot disagree.\n"
        " *\n"
        " * [slug] is the anchor in both published pages, which is how \"Read more\" lands on the right entry.\n"
        " */\n"
        "enum class HelpTopic(\n"
        "    val slug: String,\n"
        "    @param:StringRes val title: Int,\n"
        "    @param:StringRes val body: Int\n"
        ") {\n" + ",\n".join(entries) + ";\n}\n"
    )


# ── Driver ───────────────────────────────────────────────────────────────────

def outputs() -> dict[Path, str]:
    topics = all_topics()
    check_links(topics)
    return {
        OUT_FLAT: build_flat(topics),
        OUT_ACCORDION: build_accordion(topics),
        OUT_SETUP: build_setup(topics),
        OUT_XML: build_xml(topics),
        OUT_KOTLIN: build_kotlin(topics),
    }


def main() -> int:
    check_only = "--check" in sys.argv
    stale: list[Path] = []
    for path, text in outputs().items():
        current = path.read_text(encoding="utf-8") if path.exists() else None
        if current != text:
            stale.append(path)
            if not check_only:
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(text, encoding="utf-8", newline="\n")
    topics = all_topics()
    ui = sum(1 for t in topics.values() if t.ui)
    if check_only:
        for path in stale:
            print(f"out of date: {path.relative_to(ROOT)}")
        print(f"{len(topics)} topics ({ui} with a (?) button), {len(stale)} output(s) out of date")
        return 1 if stale else 0
    print(f"{len(topics)} topics ({ui} with a (?) button); wrote {len(stale)} changed file(s)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
