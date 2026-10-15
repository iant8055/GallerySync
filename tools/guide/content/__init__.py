"""The How To Guide's content, chapter by chapter, in reading order.

English (this folder's own ``c1_start.py`` … ``c8_help.py``) is canonical and
is never edited by a translation pass.

Each non-English locale lives in its own sub-folder under this one, holding a
file per chapter with the same filename as the English version (so
``content/es/c3_albums.py`` is the Spanish of ``content/c3_albums.py``). Each
such file exports a ``TRANSLATIONS`` dict:

    TRANSLATIONS = {
        "_chapter": {"title": "...", "intro": "...", "en_hash": "..."},
        "topic-id": {"title": "...", "body": "...", "en_hash": "..."},
        ...
    }

The ``en_hash`` of each entry is the SHA-256 (first 16 hex chars) of the
English title + body (or title + intro) at the time the translation was made,
produced by :func:`model.english_hash`. If the English later changes, the
hash no longer matches and the whole locale is held back from publication
until that one topic is re-translated. English never goes stale.

Loading is passive: adding ``content/<locale>/`` with any number of chapter
files is enough — no registration needed. The build then decides whether that
locale is complete.
"""
from __future__ import annotations

import importlib
from pathlib import Path

from . import (
    _chrome,
    c1_start,
    c2_setup,
    c3_albums,
    c4_restore,
    c5_archive,
    c6_settings,
    c7_concepts,
    c8_help,
)

LAST_UPDATED = "11 October 2026"

# Per-locale rendering of the same date. Used by the build when it formats the
# page_sub and footer_text chrome strings, which both carry a {date} placeholder.
# When LAST_UPDATED changes, every entry here needs updating too; a missing locale
# falls back to the English string, so a new locale is never silently wrong.
LAST_UPDATED_BY_LOCALE: dict[str, str] = {
    "en": "11 October 2026",
    "es": "11 de octubre de 2026",
    "pt-BR": "11 de outubro de 2026",
    "fr": "11 octobre 2026",
    "de": "11. Oktober 2026",
    "ja": "2026年10月11日",
    "hi": "11 अक्टूबर 2026",
}

CHAPTERS = [
    c1_start.CHAPTER,
    c2_setup.CHAPTER,
    c3_albums.CHAPTER,
    c4_restore.CHAPTER,
    c5_archive.CHAPTER,
    c6_settings.CHAPTER,
    c7_concepts.CHAPTER,
    c8_help.CHAPTER,
]

_CHAPTER_MODULE_NAMES = (
    "c1_start",
    "c2_setup",
    "c3_albums",
    "c4_restore",
    "c5_archive",
    "c6_settings",
    "c7_concepts",
    "c8_help",
)

# English chrome. See ``content/_chrome.py`` for what each key is for.
CHROME_EN: dict[str, str] = dict(_chrome.STRINGS)

# Translated chrome, locale -> {"strings": dict[str, str], "en_hash": str}.
# Populated by the loader. A missing entry means that locale has no chrome
# translation; an entry whose ``en_hash`` no longer matches the current English
# chrome is treated as stale, exactly like a stale topic.
CHROME_LOCALES: dict[str, dict[str, object]] = {}


def _load_translations() -> list[str]:
    """Attach any `content/<locale>/*.py` translations to the English objects.

    Returns the list of locale codes found, sorted. Missing files are simply
    skipped — the build's completeness check reports them per topic/chapter,
    not here.
    """
    here = Path(__file__).parent
    locales: list[str] = []
    for entry in sorted(here.iterdir()):
        if not entry.is_dir():
            continue
        if entry.name.startswith(("_", ".")):
            continue
        locale = entry.name
        locales.append(locale)
        for chapter, mod_name in zip(CHAPTERS, _CHAPTER_MODULE_NAMES):
            if not (entry / f"{mod_name}.py").exists():
                continue
            module = importlib.import_module(f".{locale}.{mod_name}", package=__name__)
            translations = getattr(module, "TRANSLATIONS", None)
            if not isinstance(translations, dict):
                continue
            chapter_entry = translations.get("_chapter")
            if isinstance(chapter_entry, dict):
                chapter.translations[locale] = chapter_entry
            for topic in chapter.topics:
                topic_entry = translations.get(topic.id)
                if isinstance(topic_entry, dict):
                    topic.translations[locale] = topic_entry
        # Optional chrome: content/<locale>/_chrome.py exporting STRINGS + EN_HASH.
        if (entry / "_chrome.py").exists():
            chrome_module = importlib.import_module(f".{locale}._chrome", package=__name__)
            strings = getattr(chrome_module, "STRINGS", None)
            en_hash = getattr(chrome_module, "EN_HASH", None)
            if isinstance(strings, dict) and isinstance(en_hash, str):
                CHROME_LOCALES[locale] = {"strings": dict(strings), "en_hash": en_hash}
    return locales


LOCALES: list[str] = _load_translations()
