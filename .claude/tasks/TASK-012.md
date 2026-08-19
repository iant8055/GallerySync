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
6. Rename the tab — first to **Sync**, then to **Album Modes** on reflection. *Done, 93bf7d4 and this commit*
7. A **sleeker, more modern** look across the whole UI
8. A fourth mode that **moves** an album off the phone into OneDrive — added 18 Aug 2026

## 1. Album modes — the centrepiece

Four modes, forming a ladder: how much of the album stays on the phone.

| Mode | Uploaded? | Local state | Space freed | Still in the gallery? |
|---|---|---|---|---|
| **Off** | no | untouched, full size | none | yes |
| **Backup** | yes | untouched, full size | none | yes |
| **Sync** | yes | photos proxied | ~90% of photos | yes |
| **Archive** | yes | **removed** | all of it | **no** |

Off/Backup/Sync map onto the wording test adopted today. Archive is Ian's fourth, added 18 Aug 2026:
*"moves the album off the local Gallery into OneDrive — not making a copy but moving it to a secure
location."*

**It subsumes an existing milestone item.** v0.3's "per-album keep originals on device for albums
actively edited from" *is* Backup mode. Close that row rather than building it separately.

**Mixed albums resolve themselves under Sync.** Camera holds photos and video together; photos are
proxied and video is left alone, because video is never proxied. No special casing.

### Archive is the dangerous one, and it is the failure Ian lived through
Under Archive the files leave the gallery entirely. Photos get no proxy — unlike Sync there is no
stand-in. This is what Samsung did to him: *"I couldn't find the video I had just shot because
Gallery had moved it to OneDrive."*

What makes it defensible is that **it is a per-album choice, never a blanket behaviour.** Samsung's
"free up phone space" was all-or-nothing with no per-folder control. Designating one album as an
archive is a different act from a policy that quietly reaches everything.

Four guards, none optional:

1. **Never a default**, and not offered as the Settings default for new albums.
2. **A minimum age before anything is archived.** Nothing recent is swept up, so a clip shot this
   morning stays put even inside an archived album. This is the guard that makes the mode safe
   against the exact experience that started the project. Suggested 30 days, and user-visible.
3. **Verified in OneDrive first** — Graph confirmed, byte size matched — the same bar as every other
   removal, and `createTrashRequest` rather than a delete, per the deletion policy.
4. **The UI says the files leave the gallery**, in those words, before the mode is applied.

### Archive should not ship before v0.4
Once an album is archived the only route back is retrieval. Without it, Archive means "gone until you
go and use the OneDrive app", which is worse than not offering the mode. Build it; gate it behind
retrieval landing.

### Naming — "Backup" is the collision, not "Archive"
Ian reads backup as *moving to a secure location*. Common usage runs the other way: Time Machine,
OneDrive backup and Google Photos backup all copy and leave the original alone, and today's wording
test says the same. Two readings of one word, with opposite consequences for someone's files, is the
worst possible label for the most destructive mode.

So the destructive mode is **Archive**, which carries "moved to long-term storage, not here any more"
without ambiguity. If that still reads too softly, **Cloud only** states the end result outright and
is harder to misread than either.

The copy-and-leave-alone mode keeps **Backup**, which matches how the rest of the industry uses the
word — and Archive now occupies the meaning Ian wanted a name for.

### Room migration — escalation, per CLAUDE.md
`AlbumPreferenceEntity.isEnabled: Boolean` becomes a four-valued mode. Schema change, so it needs a
migration and Ian's sign-off.

**Map `true` to Backup**, and nothing to Archive ever. Today an enabled album uploads and touches
nothing local; optimising happens only on a tap. Mapping to Sync would switch on space management
nobody chose; mapping to Archive would empty their gallery. `false` maps to Off.

### Default for new albums
`DEFAULT_ENABLED = true` exists so the safe failure is "uploaded something you did not need" rather
than "lost something you did". The equivalent under four modes is **Backup**. Configurable in
Settings, offering Off, Backup and Sync — **Archive is not selectable as a default.**

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
- An album can be set to Off, Backup, Sync or Archive, and the choice persists
- Backup mode never optimises or removes a local file, verified by observation not by reading code
- Existing enabled albums land in Backup after migration — never Sync, never Archive
- Archive removes nothing until the item is verified in OneDrive and older than the minimum age
- Archive is unavailable as the default for new albums
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
