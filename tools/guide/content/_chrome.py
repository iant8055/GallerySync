"""Chrome strings — the Guide's own framing text, held outside any one chapter.

These are the strings that are not in the chapters (page headings, the intro
notes, the switch between flat and accordion, the setup page's note, page
titles and the footer) but still reach the reader's eyes. They live here,
alongside the English chapter files, so every user-visible word has one home
under ``tools/guide/content/`` and a translator sees all of them.

English is canonical. A non-English locale translates these in its own file at
``content/<locale>/_chrome.py``, exporting a matching ``STRINGS`` dict and an
``EN_HASH`` set to :func:`model.english_chrome_hash` of the English chrome at
translation time. If the English chrome changes afterwards the hash no longer
matches, chrome is treated as stale and the whole locale is held back from
publication — the same contract every chapter and topic lives under.

Each string is a raw HTML fragment: ``<strong>``, ``&mdash;`` and so on are
written literally. A translator rewrites the whole string in the target
language, keeping any markup they want to keep. Nothing is escaped at build
time, so these come from an author we trust (ourselves and the Translator
agents), never from end-user input.

Placeholders ``{date}`` and ``{name}`` are substituted at build time; the rest
of each string is emitted verbatim. ``{name}`` carries either ``guide_name``
(for the full guide) or ``setup_name`` (for the standalone setup chapter),
both of which are themselves chrome strings and may be rewritten freely in
each language.
"""
from __future__ import annotations

STRINGS: dict[str, str] = {
    # Full-guide page shell
    "guide_browser_title": "How To Guide — GallerySync",
    "guide_heading": "How To Guide",
    "guide_name": "How To Guide",

    # Standalone setup-page shell
    "setup_browser_title": "First-Time Setup — GallerySync",
    "setup_heading": "First-Time Setup",
    "setup_name": "First-Time Setup",

    # Subtitle under every H1, and the footer line
    "page_sub": "GallerySync — last updated {date}",
    "footer_text": "GallerySync — {name}. Last updated {date}.",

    # Contents heading on the two long-form pages
    "contents_heading": "Contents",

    # Intro note on the full guide. The lede sits inside <strong>...</strong>
    # that the template adds; the body is the raw HTML fragment that follows
    # it inside the same <p> element.
    "intro_lede": "Every screen, every line.",
    "intro_body": (
        "This guide explains each part of GallerySync in plain\n"
        "    language &mdash; what you are looking at, and where the information comes from. Inside the app, the\n"
        "    <strong>(?)</strong> buttons open the same explanations as a pop-up."
    ),

    # Switch between flat and accordion. The link href is set by the build
    # (it has to vary by locale and by base filename); the chrome owns the
    # surrounding text and the link's visible label.
    "flat_switch_text": "Prefer collapsible sections?",
    "flat_switch_link": "Open the expandable version.",
    "accordion_switch_text": "Prefer one long page?",
    "accordion_switch_link": "Open the single-page version.",
    "accordion_switch_tap_tip": "Tap a heading to open it.",

    # Intro note on the standalone setup page.
    "setup_lede": "Every card, in order.",
    "setup_body": (
        "This page explains each step of GallerySync's first-time\n"
        "    setup in plain language: what a card is for, what its buttons do, and the messages you might meet.\n"
        "    Tap a heading to open it."
    ),
}
