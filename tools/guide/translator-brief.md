# Translator agent brief — `translator-<code>`

**One of six Translator agents** running in parallel for the GallerySync Guide
translation sweep. One agent per language; this brief is reused for all six
with the target language filled in at launch. For `translator-hi` the Hindi
script rule block at the end of this document also applies.

## Target language (filled in at launch)

- **Code:** `<code>` — one of `es`, `pt-BR`, `fr`, `de`, `ja`, `hi`
- **Android folder qualifier:** `<androidloc>` — `es`, `pt-rBR`, `fr`, `de`, `ja`, `hi`
- **Display name:** `<display_name>` — the language's own name for itself, as
  used by the Guide's language picker (Español, Português (Brasil), Français,
  Deutsch, 日本語, हिन्दी)

## Required reading, in order

1. **`tools/guide/glossary.md`** — the DO NOT TRANSLATE list. Preserve every
   term in §1–6, §8, §9 and §10 exactly as spelled in English, including
   capitalisation and British `-ise` spelling. These stay English inside
   sentences of your target language. Platform words in §7 (Trash, Recycle
   Bin, gallery as a general lower-case noun) ARE translated naturally.
2. **`tools/guide/content/_chrome.py`** — the English chrome (page framing
   text).
3. **`tools/guide/content/c1_start.py` through `c8_help.py`** — the English
   Guide content, 108 topics across 8 chapters.
4. **For the setup chapter (`c2_setup.py`) only:**
   `tools/wizard/<lang>.py` — the wizard's source translation in your target
   language, authored and owned by the wizard owner (the GallerySync main
   session). File names map by locale code: `es.py`, `pt_BR.py`, `fr.py`,
   `de.py`, `ja.py`, `hi.py` (BCP-47 hyphens become underscores). Each file
   defines `T = { "key": "value", ... }` with the wizard's strings in your
   language. **Read `tools/wizard/<lang>.py`, not the Android resource XML.**
   The XML under `app/src/main/res/values-*/strings.xml` is the same text
   escaped (`\'` for apostrophe, `&amp;` for ampersand, `\"` for quote), so a
   character-for-character match against it fails on every apostrophe and
   HTML entity. The `.py` files hold the unescaped text and are authoritative
   for cross-referencing.

   Any wizard button, screen title or message the Guide quotes in the setup
   chapter must match the value in `tools/wizard/<lang>.py` EXACTLY — pick the
   string from the wizard, do not re-translate it. If a label the English
   Guide quotes is not in the wizard, flag it in your output rather than
   guessing.

## Scope — nine sub-tasks per language

| # | Input | Output |
|---|---|---|
| 1 | `content/_chrome.py` | `content/<code>/_chrome.py` |
| 2 | `content/c1_start.py` | `content/<code>/c1_start.py` |
| 3 | `content/c2_setup.py` | `content/<code>/c2_setup.py` |
| 4 | `content/c3_albums.py` | `content/<code>/c3_albums.py` |
| 5 | `content/c4_restore.py` | `content/<code>/c4_restore.py` |
| 6 | `content/c5_archive.py` | `content/<code>/c5_archive.py` |
| 7 | `content/c6_settings.py` | `content/<code>/c6_settings.py` |
| 8 | `content/c7_concepts.py` | `content/<code>/c7_concepts.py` |
| 9 | `content/c8_help.py` | `content/<code>/c8_help.py` |

**You never edit the English files.** You write new files only, under
`content/<code>/`.

## Output file formats

### Chapter files (`c1_start.py` through `c8_help.py`)

```python
TRANSLATIONS = {
    "_chapter": {
        "title": "<translated chapter title>",
        "intro": "<translated chapter intro>",
        "en_hash": "<hash>",
    },
    "<topic-id-1>": {
        "title": "<translated topic title>",
        "body": "<translated topic body>",
        "en_hash": "<hash>",
    },
    # ... one entry per topic the English chapter contains
}
```

The `_chapter` entry carries the chapter's own title and intro. One topic
entry per topic id in the English chapter — do not add or remove ids. The
body preserves the exact structure of the English (same number of
paragraphs, same bullet layout).

**Computing `en_hash`:** import `english_hash` from `tools/guide/model.py` and
call `english_hash(english_title, english_body_or_intro)`. The hash is the
first 16 hex characters of SHA-256 over `title + "\n" + body` (or
`title + "\n" + intro`). Record it for the English content you translated
from — the build uses it to detect when a later English edit makes your
translation stale.

### Chrome file (`_chrome.py`)

```python
STRINGS = {
    "guide_browser_title": "<...>",
    "guide_heading": "<...>",
    "guide_name": "<...>",
    "setup_browser_title": "<...>",
    "setup_heading": "<...>",
    "setup_name": "<...>",
    "page_sub": "<...>",         # must contain {date}
    "footer_text": "<...>",      # must contain {name} and {date}
    "contents_heading": "<...>",
    "intro_lede": "<...>",
    "intro_body": "<...>",       # may contain <strong>...</strong> and &mdash;
    "flat_switch_text": "<...>",
    "flat_switch_link": "<...>",
    "accordion_switch_text": "<...>",
    "accordion_switch_link": "<...>",
    "accordion_switch_tap_tip": "<...>",
    "setup_lede": "<...>",
    "setup_body": "<...>",       # may contain markup like intro_body
}
EN_HASH = "<hash>"
```

**Computing `EN_HASH`:** import `english_chrome_hash` from `model.py` and call
`english_chrome_hash(STRINGS)` where `STRINGS` is the ENGLISH chrome dict (not
yours).

**Pay attention to `guide_name`.** The Guide refers to itself by this name
everywhere ("Read more in <guide_name>"). Pick a name that reads naturally in
your language as a proper-noun self-reference — for example *Guía de uso*,
*Guia de uso*, *Mode d'emploi*, *Anleitung*, 使い方ガイド, उपयोग गाइड. This
value is also fed back into the wizard's `help_open_guide` string, so make it
work there too.

**Preserve `{date}` and `{name}` placeholders** exactly. These are substituted
at build time; the rest of each string is rendered verbatim.

## Translation rules

1. **Preserve markup counts and shape.** `## Heading` runs, `• ` bullets,
   `1. ` numbered-list items, `**bold**` runs, `[[topic-id]]` cross-references,
   `{{full: ...}}` markers. The COUNT of each in your translation must match
   the English. The CONTENT inside `[[topic-id]]` must be identical (same slug).

2. **Preserve printf placeholders.** `%1$s`, `%2$d`, `%1$,d`, `%d` and so on
   must appear with the same identifiers in your translation. Their *order*
   may change to fit grammar (`%2$d から %1$d` is fine), but the identifiers
   themselves do not change.

3. **Glossary §1–6, §8, §9, §10: stay English.** Exactly as spelled, inside
   your target language's sentences. British `-ise`, not American `-ize`.
   The §10 list of on-screen labels is long; keep each one as the English
   reference says it (except in the setup chapter, see below).

4. **Setup chapter rule.** When the Guide's setup chapter quotes a wizard
   label, use the exact string from `tools/wizard/<lang>.py` (NOT from the
   escaped Android XML — see Required reading item 4). If a Spanish Guide
   quotes the Next button, it says "Siguiente" (the value in
   `tools/wizard/es.py`), not "Next" (the glossary's English). Everywhere
   else in the Guide (Albums, Restore, Archive, Settings and the rest),
   labels stay English per §10 because those screens stay English.

5. **Platform words translate (§7).** Trash, Recycle Bin, gallery (as a
   generic lower-case noun) are translated to match what the user sees in
   their OS language. These are not GallerySync's own labels.

6. **One-pass policy.** This is raw LLM translation. You will not be
   human-reviewed before the Head Editor sees your output. Prioritise
   meaning preservation over stylistic flair; the goal is accurate and
   readable, not literary. The Head Editor will flag issues and send
   specific topics back to you for a second pass if needed.

7. **No invention.** If an English sentence names a specific count, size or
   label, use the same (translated or English per the rules above). Do not
   add examples the English does not have. Do not drop information the
   English contains.

## Deliverables

Nine files under `content/<code>/`:

- `_chrome.py`
- `c1_start.py`, `c2_setup.py`, `c3_albums.py`, `c4_restore.py`,
  `c5_archive.py`, `c6_settings.py`, `c7_concepts.py`, `c8_help.py`

Nothing else.

**You NEVER edit any of the following:**
- Any English file under `tools/guide/content/` (the English prose stays
  as it is).
- Any file under `tools/wizard/` (those are the wizard owner's; the
  GallerySync main session owns the wizard translation).
- Any file under `app/src/main/res/values-*/strings.xml` (same reason —
  those are generated from `tools/wizard/`).
- Any Kotlin, Android build file, or anything outside
  `tools/guide/content/<code>/`.

If you find a wizard wording issue while cross-referencing the setup chapter
(wrong grammar, missing string, inconsistent spelling), do not fix it. Report
it as a note at the end of your run as `key → suggested new text`; the main
session will decide and apply.

When all nine files are written, run:

    python tools/guide/build_guide.py --check

Expected outcome: your language appears in the "translation warning" lines as
having zero outstanding items, OR appears in "published locales". No blocking
errors.

---

## Hindi script rule (applies to `translator-hi` only)

Hindi uses the Devanagari script, and the shipped wizard sets the convention
you follow. Keep the terms below in Latin script, exactly as spelled in
English, inside Devanagari sentences — the app itself renders them in Latin
and the Guide must match on screen:

- **GallerySync**, **Pro**, every brand name in §6 (OneDrive, Google Photos,
  Google Drive, Dropbox, pCloud, IDrive e2, Backblaze B2, Samsung Gallery,
  Google, Microsoft, Android, Play Store, Wi-Fi).
- **The four album modes:** Off, **Backup**, **Sync**, **Archive**.
- **The four tab names:** Albums, Restore, Archive, Settings.
- **"How To Guide"** (the Settings card title).
- Every on-screen label in glossary §10 that the shipped wizard renders in
  Latin. The wizard's own source at `tools/wizard/hi.py` is authoritative —
  follow it.

**Transliterate the common technical nouns into Devanagari** the way the
shipped wizard does: **बैकअप** (backup as a general verb/noun), **क्लाउड**
(Cloud as a general noun), **सिंक** (sync as an action verb; **never as the
mode name Sync**, which stays Latin per the list above). This matches
standard Indian technology writing and is what Hindi readers expect.

**Rule of thumb:** if the word is a thing *on an app button or tab*, keep it
in Latin. If it is prose describing the action, Devanagari is natural.
Example from the shipped wizard: `Archive या Sync नहीं` (mode names Latin)
vs. `बैकअप या सिंक` (same words as verbs, Devanagari). Both are correct.
