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


def _load_translations() -> list[str]:
    """Attach any `content/<locale>/*.py` translations to the English objects.

    Returns the list of locale codes found, sorted. Missing files are simply
    skipped — the build's completeness check reports it per topic, not here.
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
    return locales


LOCALES: list[str] = _load_translations()
