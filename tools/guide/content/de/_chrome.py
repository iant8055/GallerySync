"""Chrome-Strings (Deutsch) — der Rahmentext des Guides.

Übersetzung von ``content/_chrome.py``. ``EN_HASH`` ist der Hash des englischen
Chromes, aus dem übersetzt wurde (``model.english_chrome_hash``).
"""
from __future__ import annotations

STRINGS: dict[str, str] = {
    # Full-guide page shell
    "guide_browser_title": "Anleitung — GallerySync",
    "guide_heading": "Anleitung",
    "guide_name": "Anleitung",

    # Standalone setup-page shell
    "setup_browser_title": "Ersteinrichtung — GallerySync",
    "setup_heading": "Ersteinrichtung",
    "setup_name": "Ersteinrichtung",

    # Subtitle under every H1, and the footer line
    "page_sub": "GallerySync — zuletzt aktualisiert: {date}",
    "footer_text": "GallerySync — {name}. Zuletzt aktualisiert: {date}.",

    # Contents heading on the two long-form pages
    "contents_heading": "Inhalt",

    # Intro note on the full guide.
    "intro_lede": "Jeder Bildschirm, jede Zeile.",
    "intro_body": (
        "Diese Anleitung erklärt jeden Teil von GallerySync in einfacher\n"
        "    Sprache &mdash; was du siehst und woher die Informationen kommen. In der App öffnen die\n"
        "    <strong>(?)</strong>-Schaltflächen dieselben Erklärungen als Pop-up."
    ),

    # Switch between flat and accordion.
    "flat_switch_text": "Lieber einklappbare Abschnitte?",
    "flat_switch_link": "Zur aufklappbaren Version.",
    "accordion_switch_text": "Lieber eine lange Seite?",
    "accordion_switch_link": "Zur Version auf einer Seite.",
    "accordion_switch_tap_tip": "Tippe auf eine Überschrift, um sie zu öffnen.",

    # Intro note on the standalone setup page.
    "setup_lede": "Jede Karte, der Reihe nach.",
    "setup_body": (
        "Diese Seite erklärt jeden Schritt der Ersteinrichtung von GallerySync\n"
        "    in einfacher Sprache: wofür eine Karte da ist, was ihre Schaltflächen tun und welche Meldungen dir begegnen können.\n"
        "    Tippe auf eine Überschrift, um sie zu öffnen."
    ),
}
EN_HASH = "3896ea3a0ec08a29"
