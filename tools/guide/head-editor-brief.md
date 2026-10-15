# Head Editor agent brief — `head-editor`

**One agent, runs after all six Translators.** You verify the output of the
six `translator-<code>` agents (es, pt-BR, fr, de, ja, hi) and produce a
per-language report of flags. You never translate yourself; your job is to
check and route corrections.

## Required reading

1. **`tools/guide/glossary.md`** — the DO NOT TRANSLATE list.
2. **`tools/guide/translation-plan.md`** §Phase 2 Head Editor and §Phase 1
   Hindi script rule, for the full set of checks and the Hindi-specific
   exemption.
3. **`tools/guide/content/_chrome.py`** and
   **`tools/guide/content/c*.py`** — the English content you compare against.
4. **`tools/guide/content/<locale>/*.py`** — the Translator output for each of
   the six languages.
5. **`tools/wizard/<lang>.py`** for each language (`es.py`, `pt_BR.py`,
   `fr.py`, `de.py`, `ja.py`, `hi.py`) — the wizard's own source translation,
   authored and owned by the wizard owner (the GallerySync main session).
   Authoritative for cross-referencing the setup chapter. **Do not read the
   Android resource XML (`app/src/main/res/values-*/strings.xml`) for this
   check** — it is the same text but escaped (`\'`, `&amp;`, `\"`), so a
   character-for-character diff against it fails on apostrophes and HTML
   entities. The `.py` files hold the unescaped text.
6. **`tools/guide/translator-brief.md`** — the brief the Translators worked
   from, so you know what rules they were held to.

## Scope — the eight checks

Run every check against every topic (and chapter and chrome entry) in every
language.

### 1. Glossary adherence (hard check)

Every term in `glossary.md` §1–6, §8, §9 and §10 that appears in the English
source must appear in the translation **exactly as spelled in English**, at
least as often as in the English minus a reasonable tolerance (the target
language may fold two sentences into one, so a `Cloud` that appears three
times in English may appear two or three times in the translation).

**Hindi exemption.** For `translator-hi` output, glossary terms that the
Phase 1 Hindi script rule allows in Devanagari — chiefly `Backup`, `Cloud`,
`Sync` and other common technical nouns when used as action verbs or prose
nouns — may appear as बैकअप, क्लाउड, सिंक and so on. The terms that MUST
stay Latin even in Hindi are listed in that rule: GallerySync, Pro, the brand
names, the four tab names, the four mode names (Off/Backup/Sync/Archive),
"How To Guide", and whatever labels in §10 the shipped wizard renders in
Latin.

Mechanical approach: `grep -c` each glossary term in the English topic and
in the translation; flag any mismatch. For Hindi, exempt the specific list
above.

### 2. Markup preservation (hard check)

Count the following in each English topic body and in its translation; the
counts must match exactly:

- `##` section headings
- `• ` bullet-list items
- `1. ` numbered-list items (`\d+\. `)
- `**bold**` runs
- `[[topic-id]]` cross-references (slugs themselves must be identical)
- `{{full: ...}}` markers

Any mismatch is a flag.

### 3. No half-translated locked phrases

Phrases like `Sync now`, `Keep at full size`, `Back up to Cloud` must appear
whole in each translation (per glossary §9). A translation that renders them
as "Sync ahora" or "verwendet den Back up to Cloud" is wrong.

Approach: for each phrase in glossary §9, grep the English topic for
occurrences; for each occurrence, confirm the exact phrase appears in the
translation. If not, flag.

### 4. Spelling discipline

`Optimise`, `Optimised`, `Optimisation` (British `-ise` spellings) must not
have been "corrected" to American `-ize` anywhere, including inside the
translated prose when the word is quoted as an English on-screen label.

### 5. Spot-read for sense

Pick 5 random topics per language. Read the translation and translate it
back to English mentally (or with your target-language skill). Flag any
topic where the meaning drifted — a sentence saying something the English
does not, or missing something the English says. This is the only
qualitative check and the only one that cannot be mechanised.

### 6. Section 7 compliance

Platform words in glossary §7 — Trash, Recycle Bin, gallery (as a generic
lower-case noun), phone, folder, file, photo, video — ARE translated in the
output per each language's convention. If a Translator left any of these
English where they should have been translated, flag it. The §7 note on
"Settings" (lowercase, Android's own Settings app) is deliberately
ambiguous; prefer the GallerySync reading and keep it English if unclear.

### 7. Setup chapter cross-check

When a translation of a topic in `c2_setup.py` quotes a wizard label (per
the §10 setup-chapter amendment of 14 Oct 2026), diff the quoted label
against the value in `tools/wizard/<lang>.py` (NOT the escaped Android XML
— see Required reading item 5). The strings must match
**character-for-character**. If the Translator re-translated the label
instead of pulling from the wizard, flag.

A quick index: common wizard-label references in the English setup chapter
include button names (`Next`, `Back`, `Finish`, `Cancel`, `Try again`,
`Choose all`), screen titles (`Welcome to GallerySync`, `Cloud Storage`,
`Choose folders to back up`, `Choose your Backup Plan`, `Optimisation
Settings`, `Ready to Back Up`, `Backup Progress`), and messages like
`Please select your language to continue`.

### 8. Chrome completeness

`content/<locale>/_chrome.py` must exist for every published language. It
must:

- export a `STRINGS` dict with EVERY key the English `_chrome.py` has (no
  missing keys);
- export an `EN_HASH` equal to `model.english_chrome_hash(CHROME_EN)`;
- keep the `{date}` placeholder in `page_sub` and `footer_text`, and the
  `{name}` placeholder in `footer_text`.

If any of these fail, the whole locale cannot publish — flag as a blocking
chrome failure.

## Output format

For each language, produce a flags report at
`tools/guide/out/<code>-flags.md` with:

```markdown
# <code> flags — round <N>

## Blocking issues
(entries that must be fixed before this locale can publish)

- **<topic-id>** — <rule broken, e.g. "glossary §4: 'GallerySync' appears 3× in English, 2× in <code>">
  - English: `<the English line>`
  - <code>: `<the translation line>`
  - Suggested fix: `<concrete replacement text>`

## Advisory issues
(entries worth fixing but not blocking)

- (same shape)

## Summary
- Blocking: <n>
- Advisory: <n>
- By rule type: §1 glossary=<n>, §2 markup=<n>, §3 locked phrases=<n>, §4 spelling=<n>, §5 sense=<n>, §6 §7-compliance=<n>, §7 setup-chapter=<n>, §8 chrome=<n>.
```

If a language has no blocking issues, name the report "no blocking issues"
and that language proceeds to publication.

## Loop

- Send the per-language flag report back to the matching Translator agent
  via `SendMessage` with `to: "translator-<code>"`. Ask for a round-two pass
  on only the flagged topics (do not re-translate the whole language).
- Translator re-translates only those topics, overwriting their entries in
  `content/<code>/*.py`.
- Head Editor re-checks only the previously-flagged topics in round two.
- **Two rounds maximum.** After two rounds, residual flags are recorded as
  known defects in the language's flags report and left for Ian to decide.
  They do not block publication of the OTHER unflagged topics in that
  language.

## Build verification

After every Translator has passed Head Editor, run:

    python tools/guide/build_guide.py --check

Expected: `0 output(s) out of date, 0 orphan(s), 0 translation warning(s);
published locales: en, es, pt-BR, fr, de, ja, hi` (order per internal
sort). If anything other than that appears, do not proceed — fix the
underlying problem first.

## Boundaries — what you NEVER edit

- Any English file under `tools/guide/content/` (English prose is canonical).
- Any file under `tools/wizard/` (owned by the wizard owner — the GallerySync
  main session).
- Any file under `app/src/main/res/values-*/strings.xml` (same reason).
- Any Kotlin or Android build file.

If you find a wizard wording problem while cross-referencing check #7 (bad
grammar, inconsistent spelling, a label that doesn't quite fit), do not fix
it. Record it in the language's flags report as a wizard note: `key → suggested
new text`. The main session reviews and applies.

Your edits are limited to the per-language flag reports and, when sending
round-two instructions, `SendMessage` calls to the Translator agents. You do
not touch `content/<locale>/*.py` directly — the Translator does, when you
send back the flags for round two.

## When done

Report back to the main session with:

- The six per-language flag reports' summary lines.
- The list of complete locales that will publish.
- Any residual blocking defects per language (should be zero after two
  rounds; if not, these need Ian's decision before the commit).
- Any wizard notes you collected under check #7, grouped by language.
- A one-paragraph assessment of overall translation quality so Ian has
  context before the roll-out go.
