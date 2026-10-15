"""The shapes the guide's content is written in. See build_guide.py for the body format.

Translations
------------
English is canonical. Each non-English locale lives in its own files under
`content/<locale>/`, and is attached to the English `Topic`/`Chapter` here as a
`translations` map. English access stays as `.title` / `.body` / `.intro`; a
locale's text is read with `.title_for(locale)` and so on.

Each translation records the SHA-256 (first 16 hex chars) of the English title +
body (or title + intro, for a chapter) at the moment it was made, in `en_hash`.
If the English changes afterwards, the hash no longer matches and the
translation is treated as **stale** — the whole locale is held back from
publication until that topic is re-translated. English never goes stale.
"""
from __future__ import annotations

import hashlib
import textwrap
from dataclasses import dataclass, field


def english_hash(title: str, body_or_intro: str) -> str:
    """The identity of a piece of English text, for staleness detection.

    Short SHA-256 is enough: 16 hex chars gives 64 bits of collision resistance,
    which is overkill for a guide that holds at most a few hundred topics.
    """
    joined = title + "\n" + body_or_intro
    return hashlib.sha256(joined.encode("utf-8")).hexdigest()[:16]


@dataclass
class Topic:
    id: str
    title: str                       # English title (canonical)
    body: str                        # English body (canonical)
    ui: bool = False                 # True when the app has a (?) button that opens it
    chapter: str = ""
    # locale -> {"title": str, "body": str, "en_hash": str}
    translations: dict[str, dict[str, str]] = field(default_factory=dict)

    def en_hash(self) -> str:
        return english_hash(self.title, self.body)

    def is_fresh_in(self, locale: str) -> bool:
        if locale == "en":
            return True
        t = self.translations.get(locale)
        if t is None:
            return False
        return t.get("en_hash") == self.en_hash()

    def title_for(self, locale: str) -> str:
        if locale == "en":
            return self.title
        return self.translations[locale]["title"]

    def body_for(self, locale: str) -> str:
        if locale == "en":
            return self.body
        return self.translations[locale]["body"]


@dataclass
class Chapter:
    id: str
    title: str                       # English title (canonical)
    intro: str                       # English intro (canonical)
    topics: list[Topic] = field(default_factory=list)
    # locale -> {"title": str, "intro": str, "en_hash": str}
    translations: dict[str, dict[str, str]] = field(default_factory=dict)

    def en_hash(self) -> str:
        return english_hash(self.title, self.intro)

    def is_fresh_in(self, locale: str) -> bool:
        if locale == "en":
            return True
        t = self.translations.get(locale)
        if t is None:
            return False
        return t.get("en_hash") == self.en_hash()

    def title_for(self, locale: str) -> str:
        if locale == "en":
            return self.title
        return self.translations[locale]["title"]

    def intro_for(self, locale: str) -> str:
        if locale == "en":
            return self.intro
        return self.translations[locale]["intro"]


def topic(id: str, title: str, body: str, ui: bool = False) -> Topic:
    """One entry. `ui=True` means a (?) in the app opens it as a pop-up; the guide always has it."""
    return Topic(id=id, title=title, body=textwrap.dedent(body).strip("\n"), ui=ui)
