# TASK-012 — Per-album modes, settings, and the shape of the app

Milestone: v0.3.0 / v0.4.0 — information architecture
Requested by: Ian, 18 Aug 2026
Depends on: TASK-011 (storage budget) for the auto-optimise toggle to control something

## What Ian asked for
1. Per-album **Off / Sync / Backup** dropdown, replacing the on/off toggle
2. A **default mode** for new albums, set in Settings
3. **Auto Optimise Photos** as a Settings toggle
4. **Storage** becomes where backup options live, including a schedule
5. **Remove the OneDrive tab**, unless thumbnails can be shown per file
6. Rename the **Backup tab to Sync** — *already done, 93bf7d4*
7. A **sleeker, more modern** look across the whole UI

## 1. Album modes — the centrepiece

The three modes are not degrees of the same thing; they are different promises:

| Mode | Uploaded? | Local files |
|---|---|---|
| **Off** | no | untouched |
| **Backup** | yes | **never touched** — nothing optimised, nothing removed |
| **Sync** | yes | space managed — photos proxied so they stay visible while smaller |

This maps exactly onto the wording test adopted today: sync means the file reaches the cloud *and*
stays in the gallery; backup means the cloud copy exists and nothing else changed.

**It subsumes an existing milestone item.** v0.3's "per-album keep originals on device for albums
actively edited from" *is* Backup mode. That row can be closed by this task rather than built
separately.

**Mixed albums resolve themselves.** Camera holds photos and video together. Under Sync, photos are
proxied and video is left alone — because video is never proxied and never auto-removed. No special
casing needed, and it is consistent with the origin-story decision in MILESTONES.

### Room migration — escalation, per CLAUDE.md
`AlbumPreferenceEntity.isEnabled: Boolean` becomes a mode. Schema change, so it needs a migration
and Ian's sign-off.

**Map `true` to Backup, not Sync.** Today an enabled album is uploaded and nothing local is touched;
optimising only happens when someone taps the button. Mapping to Sync would silently switch on space
management for albums the user never chose that for, and the first they would know is photos being
rewritten. `false` maps to Off.

### Default for new albums
Currently `DEFAULT_ENABLED = true`, and its reasoning holds: the safe failure is "uploaded something
you did not need", never "lost something you did". Under three modes the equivalent safe default is
**Backup** — get it to the cloud, touch nothing. Ian wants this configurable in Settings; the
setting should offer all three, and default to Backup.

## 2. Auto Optimise Photos — toggle in Settings
This is the on/off for TASK-011's worker. It does not replace the free-space floor; the two answer
different questions:

- **The toggle** — may the app optimise photos without being asked each time?
- **The floor** — at what point is it worth doing?

Without the floor, an "on" toggle would proxy every eligible photo the moment it qualified, degrading
images on a phone with plenty of space for no benefit. That is what TASK-011's hard rule 4 forbids.
Keep both, and put them together so the relationship is visible.

## 3. Storage section — where scheduling lives
Absorbs the start-time item added to v0.2 today: when the first whole-library upload runs, defaulting
to overnight and requiring charging. Storage already holds the verified count, the floor, and the
optimise controls, so scheduling belongs beside them — all four answer "how much of this phone is in
use, and when does anything change".

## 4. The OneDrive tab — remove it, or repurpose it

**Do not add thumbnails.** The design principle in CLAUDE.md and MILESTONES rules out "no photo grid,
no thumbnail browser" in as many words, and that framing is Ian's own. Graph returns thumbnails
readily, which is exactly why the principle exists — to stop the easy addition that turns this into a
worse gallery than the one already on the phone.

**Better than removing it: make it the retrieval list.** v0.4 needs "a plain retrieval list — not a
photo browser", and `BrowseScreen` is already a plain list with breadcrumbs and sorting. Repurposing
it costs less than deleting it and then building its replacement, and it keeps a job the app
genuinely needs.

So: strip it of general browsing, point it at what is *not* on the phone, and let a row be fetched
back. If v0.4 slips, removing the tab in the meantime is reasonable — but the code should not be
deleted.

## 5. Visual refresh — do it after the structure, not with it
The IA changes above alter what is on each screen. Restyling screens that are about to change shape
means doing the work twice and verifying neither properly.

Direction, once the structure settles: Material 3 with dynamic colour from the wallpaper, a more
generous type scale, and fewer boxed rows in favour of grouped list sections.

**This is the highest-risk change in the app for the dark-mode rule.** A restyle is exactly when
someone reaches for a specific colour, and CLAUDE.md is absolute: nothing hardcoded outside
`ui/theme/`, colours from `MaterialTheme.colorScheme`, text inheriting `LocalContentColor`, and both
themes checked on a device before it is called done. The teleprompter shipped unreadable in dark mode
this way.

## Acceptance
- An album can be set to Off, Sync or Backup, and the choice persists
- Backup mode never optimises or removes a local file, verified by observation not by reading code
- Existing enabled albums land in Backup after migration, not Sync
- The default for new albums is configurable and starts at Backup
- Auto-optimise is a Settings toggle governing TASK-011's worker
- Scheduling lives in Storage with the other space controls
- The OneDrive tab either fetches things back or is gone; it does not grow thumbnails
- Verified on hardware in both themes, per CLAUDE.md

## Notes
- Tabs are wired in `MainActivity.kt` around lines 88-106; the browse screen is `ui/browse/`.
- The album row and its `Switch` are in `BackupScreen.kt` around line 215.
- String *values* were updated to sync wording in 93bf7d4; the `backup_` string *names* are internal
  and were deliberately left, so do not treat them as stale.
