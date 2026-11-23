# Guide Sibling handoff: "no longer in the Cloud" → "not backed up" / "not in your Cloud"

Ian on the Moto, 22 Nov 2026: *"can we reword 'no longer in the cloud' to something like 'not backed up'"* and *"on the Album list on the Albums Tab - there are NO pending file lines"*.

## What changed in the app (committed `a3a26ac`)

`app/src/main/res/values/strings.xml`:

- **`album_missing_from_cloud` (plural, Albums-tab row flag)**
  - Was: `%1$d file is no longer in the Cloud` / `%1$d files are no longer in the Cloud`
  - Now: `%1$d file not backed up` / `%1$d files not backed up`
  - Drawn in red. Albums tab does not show PENDING as its own line, so there is no blur with pending files on that surface.

- **`album_status_not_in_cloud` (drill-down header count)**
  - Was: `%1$d not in the Cloud`
  - Now: `%1$d not in your Cloud`
  - Kept the "Cloud" wording rather than "not backed up" because PENDING is listed separately on the drill-down page — calling the missing count "not backed up" would read as covering pending files too.

- **`album_file_not_in_cloud` (per-file label on the drill-down page)**
  - **Unchanged**: still `not in %1$s`.

## What the Guide needs

### Help topic title (`app/src/main/res/values/help_topics.xml`, line 23)

- `help_album_resend_title`
  - Was: `Files no longer in the Cloud`
  - Change to: `Files not backed up` — matches the red row text users will tap (?) next to.

### Guide topic (`tools/guide/content/c3_albums.py`, slug `album-resend`, line 112)

- **Title**: `Files no longer in the Cloud` → `Files not backed up` (keeping the slug `album-resend` so cross-references hold).
- **Body**: current text uses "no longer has" and "counts them in red". Suggested rewording:

  ```
  Files this album sent that are no longer in their Cloud. **Rescan** on the Albums tab finds them, and the album's card counts them in red as **N files not backed up**.

  • Each is greyed and marked **not in <Cloud>**.
  • Nothing is sent again on its own. They may have been deleted in the Cloud on purpose.
  • Swipe right to select, left to deselect. **Select all** and **Clear** do every one.
  • **Resend** sends the selected files again, to the Cloud their folder goes to now. It removes nothing.
  • Optimized files are not listed: the full-size original was only in the Cloud.
  • In an **Off** album, set a mode first.
  • For Clouds other than OneDrive, each Rescan looks up at most 25 files per album that are not in the album's folder. Rescan again for the rest.
  ```

  Only the title and the first sentence change; the bullet list is unchanged.

### All six translations (de / es / fr / hi / ja / pt-BR)

- Translate "Files not backed up" into each locale (slug `album-resend`).
- Update the first-sentence wording to match.
- Recompute `en_hash` for `album-resend` against the new English (`english_hash(title, body)`).
- Bump `LAST_UPDATED` and all seven `LAST_UPDATED_BY_LOCALE` entries, then rebuild with `tools/guide/build_guide.py` (seven `docs/how-to-guide*.html` outputs plus their accordion variants).

### Companion rename, already landed in the UI

On the second, related banner fix (commit `0c096fd`): the Albums-tab header no longer announces "Everything here is backed up" while any row is still red or grey. That is behavioural only — no Guide copy changed. The existing topic `albums-all-backed-up` still describes the banner correctly.

## Out of scope for this patch

- `CHANGELOG.md` line 15 ("25 files are no longer in the Cloud") is a historical entry — leave alone.
- `BackupEngine.kt` and `RequeueMissingFromCloud.kt` log strings still contain "no longer in …" — log prefix, not UI; leave alone.
