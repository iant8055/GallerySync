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

from content import CHAPTERS, CHROME_EN, CHROME_LOCALES, LAST_UPDATED, LAST_UPDATED_BY_LOCALE, LOCALES  # noqa: E402
from model import Topic, english_chrome_hash  # noqa: E402

OUT_FLAT = ROOT / "docs" / "how-to-guide.html"
OUT_ACCORDION = ROOT / "docs" / "how-to-guide-accordion.html"
OUT_SETUP = ROOT / "docs" / "setup-guide.html"
OUT_XML = ROOT / "app" / "src" / "main" / "res" / "values" / "help_topics.xml"
OUT_KOTLIN = ROOT / "app" / "src" / "main" / "java" / "com" / "gallery" / "sync" / "ui" / "help" / "HelpTopic.kt"

OUT_RES_VALUES = ROOT / "app" / "src" / "main" / "res"
# Translated help_topics.xml files land under app/src/main/res/values-<loc>/, so
# Android's resource system serves the (?) pop-ups in the user's chosen language
# through HelpDialog (Ian, 14 Oct 2026 — "the entire point of making the how to
# guide multilingual was to get those little pop-up cards from the (?) in
# different languages"). This reverses the earlier choice to keep translated XML
# outside the Android source tree: Ian has decided a multilingual (?) is the
# intended behaviour. The surrounding `values-<loc>/` folders already exist and
# are shared with the wizard's own translated `strings.xml`, so the orphan
# cleanup here is scoped to `help_topics.xml` alone — the folder and anything
# else inside it belong to the wizard translation and must not be touched.

# What a reader of the language picker sees for each locale — in its own language, so a
# Spanish reader finds "Español" rather than "Spanish". English stays "English".
LOCALE_DISPLAY_NAMES: dict[str, str] = {
    "en": "English",
    "es": "Español",
    "pt-BR": "Português (Brasil)",
    "fr": "Français",
    "de": "Deutsch",
    "ja": "日本語",
    "hi": "हिन्दी",
}


def android_folder(locale: str) -> str:
    """BCP-47 to Android resource qualifier: `pt-BR` → `pt-rBR`, `es` → `es`.

    Android splits region with `-r` while BCP-47 uses a plain `-`. Only the region
    segment is affected; a locale without a region is unchanged.
    """
    if "-" not in locale:
        return locale
    language, region = locale.split("-", 1)
    return f"{language}-r{region}"


def last_updated(locale: str) -> str:
    """The LAST_UPDATED date rendered for a locale, falling back to English.

    A locale that has not been added to ``LAST_UPDATED_BY_LOCALE`` gets the
    English string; the completeness check does not block a locale on this
    because the fallback keeps the page readable, but the next release should
    add the proper rendering so a user does not see an English date on a
    translated page.
    """
    return LAST_UPDATED_BY_LOCALE.get(locale, LAST_UPDATED)


def chrome(key: str, locale: str = "en") -> str:
    """A chrome string in the asked locale, falling back to English.

    Chrome is the Guide's own framing text (page titles, "Contents", intro notes,
    the flat/accordion switch, the footer). English lives in ``content/_chrome.py``;
    translations live in ``content/<locale>/_chrome.py``. A locale that has not
    translated a particular key is served the English value for that one key —
    but the completeness check holds the whole locale back from publication
    until every key is translated and the chrome's ``EN_HASH`` matches, so this
    fallback is never reached in a published build.
    """
    if locale != "en":
        locale_strings = CHROME_LOCALES.get(locale, {}).get("strings")
        if isinstance(locale_strings, dict) and key in locale_strings:
            return locale_strings[key]
    return CHROME_EN[key]


def chrome_is_fresh(locale: str) -> bool:
    """True iff a locale's chrome is complete and matches current English chrome.

    English is always fresh. For any other locale: a chrome entry must exist,
    cover every key English has, and carry an ``en_hash`` equal to the current
    English hash.
    """
    if locale == "en":
        return True
    entry = CHROME_LOCALES.get(locale)
    if not entry:
        return False
    strings = entry.get("strings")
    if not isinstance(strings, dict):
        return False
    if any(key not in strings for key in CHROME_EN):
        return False
    return entry.get("en_hash") == english_chrome_hash(CHROME_EN)


def complete_locales(topics: dict[str, Topic]) -> list[str]:
    """Non-English locales that are fully translated and current.

    A locale is complete iff every chapter, every topic, AND the chrome has a
    translation whose hash matches the current English. Anything short of that
    holds the whole locale back from publication — no half-English hybrid pages.
    """
    complete: list[str] = []
    for locale in LOCALES:
        if (
            chrome_is_fresh(locale)
            and all(chapter.is_fresh_in(locale) for chapter in CHAPTERS)
            and all(topic.is_fresh_in(locale) for topic in topics.values())
        ):
            complete.append(locale)
    return complete


def staleness_report(topics: dict[str, Topic]) -> list[str]:
    """One line per missing or stale piece of translation, across every known locale.

    Advisory: used by ``main()`` to print per-locale translation progress. These
    are not blocking build problems — a partial or stale locale simply does not
    publish, which is the all-or-nothing fallback rule in action. Empty list
    means every known locale is complete.
    """
    problems: list[str] = []
    for locale in LOCALES:
        if not chrome_is_fresh(locale):
            if locale not in CHROME_LOCALES:
                problems.append(f"{locale}: chrome is not translated")
            else:
                problems.append(f"{locale}: chrome is stale or incomplete (English has changed)")
        for chapter in CHAPTERS:
            if locale not in chapter.translations:
                problems.append(f"{locale}: chapter {chapter.id!r} is not translated")
            elif not chapter.is_fresh_in(locale):
                problems.append(f"{locale}: chapter {chapter.id!r} is stale (English has changed)")
        for topic in topics.values():
            if locale not in topic.translations:
                problems.append(f"{locale}: topic {topic.id!r} is not translated")
            elif not topic.is_fresh_in(locale):
                problems.append(f"{locale}: topic {topic.id!r} is stale (English has changed)")
    return problems


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


def plain(text: str, topics: dict[str, Topic], locale: str = "en") -> str:
    """Inline markup removed: what a reader of the pop-up sees, minus the bold."""
    text = LINK.sub(lambda m: f"“{topics[m.group(1)].title_for(locale)}”", text)
    return text


def inline_html(text: str, topics: dict[str, Topic], locale: str = "en") -> str:
    text = html.escape(text, quote=False)
    text = BOLD.sub(r"<strong>\1</strong>", text)
    text = LINK.sub(
        lambda m: f'<a class="xref" href="#{m.group(1)}">“{html.escape(topics[m.group(1)].title_for(locale), quote=False)}”</a>',
        text,
    )
    return text


def blocks_html(body: str, topics: dict[str, Topic], heading_tag: str, locale: str = "en") -> str:
    out: list[str] = []
    for kind, value in parse(body):
        if kind == "h":
            out.append(f"<{heading_tag}>{html.escape(value, quote=False)}</{heading_tag}>")
        elif kind == "p":
            out.append(f"<p>{inline_html(value, topics, locale)}</p>")
        else:
            tag = "ol" if kind == "ol" else "ul"
            items = "".join(f"<li>{inline_html(item, topics, locale)}</li>" for item in value)
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


def page(browser_title: str, sub: str, heading: str, body: str, footer: str, lang: str = "en") -> str:
    return f"""<!DOCTYPE html>
<!-- GENERATED by tools/guide/build_guide.py from tools/guide/content/. Do not edit this file: change the source and rebuild. -->
<html lang="{html.escape(lang, quote=True)}">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="color-scheme" content="light dark">
<title>{html.escape(browser_title)}</title>
<style>{CSS}</style>
</head>
<body>
<main>

  <h1>{html.escape(heading)}</h1>
  <p class="sub">{html.escape(sub)}</p>

{body}

  <footer>
    {html.escape(footer)}
  </footer>

</main>
</body>
</html>
"""


def language_picker_html(current: str, published: list[str], base_name: str) -> str:
    """A plain-link row for switching language.

    Returns an empty string when no other locale is published — in a monolingual
    world, the picker is not emitted at all, which keeps English output
    byte-identical to its pre-translation form. The in-app guide viewer runs with
    JavaScript disabled, so this must be plain `<a>` links; a `<select>` cannot
    be used.

    `base_name` is the file stem ("how-to-guide", "how-to-guide-accordion" or
    "setup-guide"), used to point each link at the sibling page in that language.
    English always keeps the unsuffixed filename; every other locale lives at
    `<base_name>.<locale>.html`.
    """
    siblings = [loc for loc in published if loc != current]
    if not siblings:
        return ""
    parts: list[str] = []
    # Walk published in a stable order: English first, then alphabetical.
    ordered = (["en"] if "en" in published else []) + sorted(l for l in published if l != "en")
    for loc in ordered:
        label = html.escape(LOCALE_DISPLAY_NAMES.get(loc, loc), quote=False)
        if loc == current:
            parts.append(f'<strong>{label}</strong>')
        else:
            target = f"{base_name}.html" if loc == "en" else f"{base_name}.{loc}.html"
            parts.append(f'<a href="{html.escape(target, quote=True)}">{label}</a>')
    return '<p class="switch picker">' + " · ".join(parts) + "</p>"


def intro_note(switch_html: str, picker_html: str = "", locale: str = "en") -> str:
    picker_line = f"\n  {picker_html}" if picker_html else ""
    lede = chrome("intro_lede", locale)
    body = chrome("intro_body", locale)
    return f"""  <div class="note">
    <p><strong>{lede}</strong> {body}</p>
  </div>
  <p class="switch">{switch_html}</p>{picker_line}"""


def toc(topics: dict[str, Topic], with_topics: bool, locale: str = "en") -> str:
    rows: list[str] = []
    for chapter in CHAPTERS:
        row = f'<li><a href="#ch-{chapter.id}">{html.escape(chapter.title_for(locale))}</a>'
        if with_topics:
            items = "".join(
                f'<li><a href="#{t.id}">{html.escape(t.title_for(locale))}</a></li>' for t in chapter.topics
            )
            row += f"<ul>{items}</ul>"
        rows.append(row + "</li>")
    contents_heading = html.escape(chrome("contents_heading", locale))
    return f'  <h2 id="contents" style="border-top:0;margin-top:24px">{contents_heading}</h2>\n  <ul class="toc">' + "".join(rows) + "</ul>"


def _accordion_target(locale: str) -> str:
    return "how-to-guide-accordion.html" if locale == "en" else f"how-to-guide-accordion.{locale}.html"


def _flat_target(locale: str) -> str:
    return "how-to-guide.html" if locale == "en" else f"how-to-guide.{locale}.html"


def build_flat(topics: dict[str, Topic], locale: str = "en", published: list[str] | None = None) -> str:
    published = published or ["en"]
    switch = (
        f'{chrome("flat_switch_text", locale)} '
        f'<a href="{_accordion_target(locale)}">{chrome("flat_switch_link", locale)}</a>'
    )
    picker = language_picker_html(locale, published, "how-to-guide")
    parts = [
        intro_note(switch, picker, locale=locale),
        toc(topics, with_topics=True, locale=locale),
    ]
    for chapter in CHAPTERS:
        parts.append(f'  <h2 id="ch-{chapter.id}">{html.escape(chapter.title_for(locale))}</h2>')
        parts.append(f'  <p class="chapter-intro">{inline_html(chapter.intro_for(locale), topics, locale)}</p>')
        for t in chapter.topics:
            parts.append(f'  <h3 id="{t.id}">{html.escape(t.title_for(locale))}</h3>')
            parts.append(blocks_html(for_guide(t.body_for(locale)), topics, "h4", locale))
    return page(
        browser_title=chrome("guide_browser_title", locale),
        sub=chrome("page_sub", locale).format(date=last_updated(locale)),
        heading=chrome("guide_heading", locale),
        body="\n".join(parts),
        footer=chrome("footer_text", locale).format(name=chrome("guide_name", locale), date=last_updated(locale)),
        lang=locale,
    )


def build_accordion(topics: dict[str, Topic], locale: str = "en", published: list[str] | None = None) -> str:
    published = published or ["en"]
    switch = (
        f'{chrome("accordion_switch_text", locale)} '
        f'<a href="{_flat_target(locale)}">{chrome("accordion_switch_link", locale)}</a> '
        f'{chrome("accordion_switch_tap_tip", locale)}'
    )
    picker = language_picker_html(locale, published, "how-to-guide-accordion")
    parts = [
        intro_note(switch, picker, locale=locale),
        toc(topics, with_topics=False, locale=locale),
    ]
    for chapter in CHAPTERS:
        parts.append(f'  <h2 id="ch-{chapter.id}">{html.escape(chapter.title_for(locale))}</h2>')
        parts.append(f'  <p class="chapter-intro">{inline_html(chapter.intro_for(locale), topics, locale)}</p>')
        for t in chapter.topics:
            # The id sits on the body, not on <details>: a link to something *inside* a closed
            # <details> opens it, where a link to the <details> element itself does not.
            parts.append(
                f'  <details class="topic"><summary>{html.escape(t.title_for(locale))}</summary>\n'
                f'  <div class="topic-body" id="{t.id}">\n{blocks_html(for_guide(t.body_for(locale)), topics, "h4", locale)}\n  </div></details>'
            )
    return page(
        browser_title=chrome("guide_browser_title", locale),
        sub=chrome("page_sub", locale).format(date=last_updated(locale)),
        heading=chrome("guide_heading", locale),
        body="\n".join(parts),
        footer=chrome("footer_text", locale).format(name=chrome("guide_name", locale), date=last_updated(locale)),
        lang=locale,
    )


def build_setup(topics: dict[str, Topic], locale: str = "en", published: list[str] | None = None) -> str:
    """The first-time setup chapter as a page of its own.

    The wizard links to this and to nothing else. Someone part-way through setup has no use for the
    rest of the guide, and a page that led into it would let them wander off into chapters about tabs
    they cannot open yet. So every link on it must stay on it: a reference to a topic outside the
    chapter is a build error rather than a dead link, and `{{full: ...}}` is how the source marks the
    sentences that only make sense in the full guide.
    """
    published = published or ["en"]
    chapter = next(c for c in CHAPTERS if c.id == "setup")
    ids = {t.id for t in chapter.topics}

    def stray(text: str) -> list[str]:
        return [slug for slug in LINK.findall(text) if slug not in ids]

    if stray(chapter.intro):
        raise SystemExit(f"setup page: the intro links outside the chapter: {stray(chapter.intro)}")

    picker = language_picker_html(locale, published, "setup-guide")
    picker_block = f"\n  {picker}" if picker else ""
    setup_lede = chrome("setup_lede", locale)
    setup_body = chrome("setup_body", locale)
    parts = [
        f"""  <div class="note">
    <p><strong>{setup_lede}</strong> {setup_body}</p>
  </div>{picker_block}""",
        f'  <p class="chapter-intro">{inline_html(chapter.intro_for(locale), topics, locale)}</p>',
    ]
    for t in chapter.topics:
        body = for_setup(t.body_for(locale))
        if stray(body):
            raise SystemExit(
                f"setup page: {t.id} links outside the chapter: {stray(body)}. "
                "Wrap that sentence in {{full: ...}} so only the full guide has it."
            )
        parts.append(
            f'  <details class="topic"><summary>{html.escape(t.title_for(locale))}</summary>\n'
            f'  <div class="topic-body" id="{t.id}">\n{blocks_html(body, topics, "h4", locale)}\n  </div></details>'
        )
    return page(
        browser_title=chrome("setup_browser_title", locale),
        sub=chrome("page_sub", locale).format(date=last_updated(locale)),
        heading=chrome("setup_heading", locale),
        body="\n".join(parts),
        footer=chrome("footer_text", locale).format(name=chrome("setup_name", locale), date=last_updated(locale)),
        lang=locale,
    )


# ── Android resources and Kotlin ─────────────────────────────────────────────

def xml_escape(text: str) -> str:
    text = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    text = text.replace("\\", "\\\\").replace("'", "\\'").replace('"', '\\"')
    text = text.replace("\n", "\\n")
    if text[:1] in ("@", "?"):
        text = "\\" + text
    return text


def popup_body(t: Topic, topics: dict[str, Topic], locale: str = "en") -> str:
    """The body as a pop-up shows it: same blocks, links resolved to titles, still with ** for bold."""
    out: list[str] = []
    for kind, value in parse(for_guide(t.body_for(locale))):
        if kind == "h":
            out.append(f"## {value}")
        elif kind == "p":
            out.append(plain(value, topics, locale))
        else:
            out.append("\n".join("• " + plain(item, topics, locale) for item in value))
    return "\n\n".join(out)


def slug_to_name(slug: str) -> str:
    return slug.replace("-", "_")


def build_xml(topics: dict[str, Topic], locale: str = "en") -> str:
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
        lines.append(f'    <string name="help_{name}_title" formatted="false">{xml_escape(t.title_for(locale))}</string>')
        lines.append(f'    <string name="help_{name}_body" formatted="false">{xml_escape(popup_body(t, topics, locale))}</string>')
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

def _locale_html_paths(locale: str) -> tuple[Path, Path, Path]:
    """The three HTML output paths for a locale. English keeps the unsuffixed names.

    English stays at the current filenames because the app's "Read more" buttons
    deep-link into them (``https://.../how-to-guide.html#album-detail`` and so on)
    and ``HowToGuideConsistencyTest`` / ``SupportLinks`` tests assert those exact
    URLs. Translations live alongside at ``<base>.<locale>.html``.
    """
    if locale == "en":
        return OUT_FLAT, OUT_ACCORDION, OUT_SETUP
    suffix = f".{locale}.html"
    docs = ROOT / "docs"
    return (
        docs / f"how-to-guide{suffix}",
        docs / f"how-to-guide-accordion{suffix}",
        docs / f"setup-guide{suffix}",
    )


def _locale_xml_path(locale: str) -> Path:
    """Where a locale's help_topics.xml lands.

    English stays at ``values/help_topics.xml``; every other locale lands in its
    matching ``values-<androidloc>/help_topics.xml`` so Android's own resource
    system serves the (?) pop-up in the user's language. The folder is shared
    with the wizard's translated ``strings.xml`` — our writes touch only the
    one filename and the orphan cleanup never removes the folder.
    """
    if locale == "en":
        return OUT_XML
    return OUT_RES_VALUES / f"values-{android_folder(locale)}" / "help_topics.xml"


def _android_folder_to_bcp47(android_loc: str) -> str:
    """Reverse of :func:`android_folder`: ``pt-rBR`` → ``pt-BR``; ``es`` → ``es``."""
    if "-r" in android_loc:
        lang, region = android_loc.split("-r", 1)
        return f"{lang}-{region}"
    return android_loc


_LOCALE_HTML_PATTERNS = (
    re.compile(r"^how-to-guide\.([^.]+)\.html$"),
    re.compile(r"^how-to-guide-accordion\.([^.]+)\.html$"),
    re.compile(r"^setup-guide\.([^.]+)\.html$"),
)


def orphans(published: list[str]) -> list[Path]:
    """Per-locale output files that currently exist but should not.

    When a locale drops out of ``published`` — a once-complete translation that
    the latest English edit has made stale, say — the files it wrote last build
    sit on disk and keep being served. The build removes them, and ``--check``
    reports them so a CI run catches the state. Scans ``docs/`` for the three
    HTML variants, and ``app/src/main/res/`` for stray ``values-<loc>/help_topics.xml``
    files whose locale no longer publishes. The surrounding ``values-<loc>/``
    folder and anything else inside it (``strings.xml`` from the wizard
    translation, in particular) are left alone — only ``help_topics.xml`` is
    ours to remove.
    """
    found: list[Path] = []
    docs = ROOT / "docs"
    if docs.exists():
        for entry in docs.iterdir():
            if not entry.is_file():
                continue
            for pattern in _LOCALE_HTML_PATTERNS:
                m = pattern.match(entry.name)
                if m and m.group(1) not in published:
                    found.append(entry)
                    break
    if OUT_RES_VALUES.exists():
        for sub in OUT_RES_VALUES.iterdir():
            if not sub.is_dir() or not sub.name.startswith("values-") or sub.name == "values":
                continue
            bcp47 = _android_folder_to_bcp47(sub.name.removeprefix("values-"))
            if bcp47 in published:
                continue
            xml_file = sub / "help_topics.xml"
            if xml_file.exists():
                found.append(xml_file)
    return found


def outputs() -> dict[Path, str]:
    topics = all_topics()
    check_links(topics)
    published = ["en"] + complete_locales(topics)
    out: dict[Path, str] = {}
    for locale in published:
        flat, accordion, setup = _locale_html_paths(locale)
        out[flat] = build_flat(topics, locale=locale, published=published)
        out[accordion] = build_accordion(topics, locale=locale, published=published)
        out[setup] = build_setup(topics, locale=locale, published=published)
        out[_locale_xml_path(locale)] = build_xml(topics, locale=locale)
    # Kotlin is English-only: the HelpTopic enum names resources that only the
    # English app uses. Translated resources will be wired in by whichever later
    # app release decides the app itself goes multilingual.
    out[OUT_KOTLIN] = build_kotlin(topics)
    return out


def _remove_orphan(path: Path) -> None:
    """Delete an orphan file. The containing ``values-<loc>/`` folder is left in place:
    it belongs to the wizard's own translation (``strings.xml`` and friends live there).
    """
    path.unlink()


def main() -> int:
    check_only = "--check" in sys.argv
    expected = outputs()
    stale: list[Path] = []
    for path, text in expected.items():
        current = path.read_text(encoding="utf-8") if path.exists() else None
        if current != text:
            stale.append(path)
            if not check_only:
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(text, encoding="utf-8", newline="\n")

    topics = all_topics()
    published = ["en"] + complete_locales(topics)
    orphan_files = orphans(published)
    if not check_only:
        for path in orphan_files:
            _remove_orphan(path)

    ui = sum(1 for t in topics.values() if t.ui)
    translation_warnings = staleness_report(topics)
    locales_summary = ", ".join(published)

    if check_only:
        for path in stale:
            print(f"out of date: {path.relative_to(ROOT)}")
        for path in orphan_files:
            print(f"orphan (should be removed): {path.relative_to(ROOT)}")
        for warning in translation_warnings:
            print(f"translation (informational): {warning}")
        print(
            f"{len(topics)} topics ({ui} with a (?) button), "
            f"{len(stale)} output(s) out of date, "
            f"{len(orphan_files)} orphan(s), "
            f"{len(translation_warnings)} translation warning(s); "
            f"published locales: {locales_summary}"
        )
        # Translation warnings are informational — a partial or stale locale
        # simply does not publish, which is the all-or-nothing fallback rule in
        # action and must not block the English-only build from being green.
        return 1 if (stale or orphan_files) else 0

    print(
        f"{len(topics)} topics ({ui} with a (?) button); "
        f"wrote {len(stale)} changed file(s), removed {len(orphan_files)} orphan(s); "
        f"published locales: {locales_summary}"
    )
    if translation_warnings:
        print(f"note: {len(translation_warnings)} translation issue(s) — those locales were not emitted:")
        for warning in translation_warnings:
            print(f"  {warning}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
