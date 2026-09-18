"""The shapes the guide's content is written in. See build_guide.py for the body format."""
from __future__ import annotations

import textwrap
from dataclasses import dataclass, field


@dataclass
class Topic:
    id: str
    title: str
    body: str
    ui: bool = False  # True when the app has a (?) button that opens it
    chapter: str = ""


@dataclass
class Chapter:
    id: str
    title: str
    intro: str
    topics: list[Topic] = field(default_factory=list)


def topic(id: str, title: str, body: str, ui: bool = False) -> Topic:
    """One entry. `ui=True` means a (?) in the app opens it as a pop-up; the guide always has it."""
    return Topic(id=id, title=title, body=textwrap.dedent(body).strip("\n"), ui=ui)
