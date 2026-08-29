# Settings audit — 29 August 2026

Every setting the user can change, everything with a default, and every wizard step — plus the ones
that were decided and never built, and the ones that quietly went away.

Compiled from `BackupSettings.kt`, `SetupWizardScreen.kt`, `SettingsScreen.kt`, the enum defaults,
`MILESTONES.md` and TASK-010 through TASK-019. Engine constants nobody sets — batch sizes, page
sizes, chunk alignment — are excluded.

| | |
|---|---|
| Persisted settings | **25** |
| Wizard steps | **19** |
| Decided, never built | **7** |
| Resolved — not settings after all | **2** |
| Removed or superseded | **6** |

---

## Resolved since this was written

**The storage floor is not a user setting.** Ian, 29 Aug: *"storage floor has become an internal
coding issue — nothing the user needs to set."* The app still needs to know free space so it does not
start work it cannot finish — a restore needs room for the file it is fetching — but there is no
floor to choose, no notification, and no worker managing to a target. TASK-011's entire notification
apparatus went with it, and `POST_NOTIFICATIONS` with that.

**The Archive age is superseded, not overlooked.** It was specified on 19 Aug for a design in which
Archive ran as a *nightly scheduled pass* — TASK-012 has the WorkManager plumbing, the overnight
hour, `setRequiresCharging(true)`, and a section worrying about whether the window is guaranteed. An
unattended sweep needs an age gate; a manual one does not. Archive shipped on 27 Aug as a tab with a
summons, a file list you read, your tap, and Android's own dialog — and it **cannot** run unattended,
because `createTrashRequest` only launches from an Activity.

Ian, 29 Aug: *"the user is manually Archiving — and they know what/when they want to do it."* Right,
and the protection is stronger than the age would have been: every removal is chosen, at a moment of
the user's choosing, against a list in front of them. The clause was removed from the consent dialog
on 25 Aug when it was found describing a gate that did not exist; the gate itself should stay
unbuilt unless Archive ever gains an unattended mode.

---

## Still worth acting on

### A debug probe is still in the Settings screen

`SafGrowProbeSection()` renders at `SettingsScreen.kt:363`. Added 27 Aug to answer whether a tree
grant lets a file grow; it does, and the answer is in MILESTONES. Both earlier research probes were
deliberately removed on 24 Aug — this one was not.

---

## Persisted settings

Three DataStores. `backup_settings` holds 23 keys; theme and granted folders live apart.
**No UI** means the value is read and honoured but nothing on screen sets it.

| Setting | Key | Default | Reachable from |
|---|---|---|---|
| Automatic sync | `automatic_backup_enabled` | `true` | Settings |
| Allow mobile data | `allow_metered_network` | `false` | Settings + wizard |
| Default mode for new albums | `default_album_mode` | `OFF` | Settings + wizard |
| Destination folder | `destination_root` | `Samsung Gallery/DCIM` | Settings |
| First backup start hour | `first_backup_start_hour` | `1` (1 am) | Settings + wizard |
| First backup needs charging | `first_backup_requires_charging` | `true` | Settings + wizard |
| First backup finished | `first_backup_completed` | `false` | *internal* |
| Cloud deletion policy | `cloud_deletion_policy` | `LEAVE` | Settings + wizard |
| Cloud deletion grace | `cloud_deletion_grace_days` | `7` days | **No UI** |
| Show empty cloud folders | `show_empty_cloud_folders` | `false` | Settings |
| Acknowledged topics | `acknowledged_topics` | empty | *internal* |
| Setup complete | `setup_complete` | `false` | Settings — "Run setup again" |
| Backup paused | `backup_paused` | `false` | Albums hero |
| Upload interrupted at | `upload_interrupted_at` | `0` | *internal* |
| Run baseline bytes | `run_baseline_bytes` | `0` | *internal* |
| Archive snooze until | `archive_delayed_until` | `0` | Archive prompt — Delay |
| **Optimise enabled** (master) | `optimise_enabled` | `false` | **No UI yet** |
| **Photo optimise mode** | `photo_optimise_mode` | `Auto` | **No UI yet** |
| **Photo optimise age** | `photo_optimise_age` | `OneDay` | **No UI yet** |
| **Video optimise mode** | `video_optimise_mode` | `Auto` | **No UI yet** |
| **Video optimise age** | `video_optimise_age` | `OneDay` | **No UI yet** |
| **Video quality** | `video_quality` | `High` (480p) | **No UI yet** |
| **Optimise cutoff** | `optimise_cutoff` | `0` (everything) | Gate 2, option 3 |
| Theme | `theme_mode` · *appearance_settings* | `SYSTEM` | Settings |
| Granted source folders | `granted_tree_uris` · *media_scope* | empty | Settings + Gate 1 |

Six of the seven optimise settings built on 28 Aug have no way to be changed. They are honoured, and
invisible.

---

## Decided, never built

Ordered by how much their absence changes what the app does.

| Setting | Decided | Intended default | Status |
|---|---|---|---|
| ~~**Archive minimum age**~~ | 19 Aug · TASK-012 | ~~1 month~~ | **Superseded** — Archive is manual |
| **Sync scope** — two toggles, photos and video · *gates optimising, not upload* | 19 Aug, revised 29 Aug | both on | Not built |
| **Space saved per album** — freed so far, and what the mode could free | 19 Aug · TASK-011 | — | Not built |
| **Retry failed items** from the UI | v0.2 backlog | — | Not built |
| ~~**Storage floor**~~ — see above | 18 Aug · TASK-011 | ~~20 GB~~ | **Not a user setting** — resolved |
| **Language** | 19 Aug | English | Deferred — only one option exists |
| **Sync frequency** | v0.5 | — | Not built |
| **Account management** | v0.5 | — | Not built |
| **Per-album "keep originals"** — TASK-011 hard rule 5 defers to it | TASK-011 | — | Never specified further |

---

## Removed or superseded

Kept because each was a real decision, and two left stale copy behind.

| What | Became | When |
|---|---|---|
| `auto_optimise_enabled` | Master switch + per-medium mode | 28 Aug |
| Settings → Storage section | Deleted; its count duplicated the Albums hero | 26 Aug |
| "Move to backup" button | Archive mode, per album | 26 Aug |
| `POST_NOTIFICATIONS` | Not needed — Android warns, exit dialog summons | 28 Aug |
| Age scale 1 week / 1 month / 1 year | Straight away · 1h · 12h · 1 day · 1 week | 28 Aug |
| OneDrive browser tab | Repurposed as Restore | 25–27 Aug |

---

## Wizard, in order

19 steps. Two are conditional. Eleven explain, six ask, two are gates the engine cannot start
without.

| # | Step | Kind | Sets |
|---|---|---|---|
| 1 | What this is | explains | — |
| 2 | Folders | explains | — |
| 3 | **Source folders** | **Gate 1** | `granted_tree_uris` |
| 4 | Scan report *(only if folders granted)* | reports | — |
| 5 | Modes | explains · acknowledged | — |
| 6 | Default mode | asks | `default_album_mode` |
| 7 | Archive | explains · acknowledged | — |
| 8 | Optimising | explains · acknowledged | — |
| 9 | Auto optimise | asks | `optimise_enabled` — **copy is stale** |
| 10 | What we can promise | explains | — |
| 11 | Check your backups yourself | explains · acknowledged | — |
| 12 | Getting files back | explains | — |
| 13 | Deleting | explains | — |
| 14 | Deletion policy | asks | `cloud_deletion_policy` |
| 15 | Emptying trash | explains | — |
| 16 | When things happen | explains | — |
| 17 | **Library choice** | **Gate 2** | album modes + `optimise_cutoff` |
| 18 | Mobile data | asks | `allow_metered_network` |
| 19 | First backup window *(only if uploading)* | asks | `first_backup_start_hour`, `…requires_charging` |

### What the wizard never asks

| Not asked | Consequence |
|---|---|
| **Video quality** | Every user gets 480p without being told, once optimising runs |
| **Photo / video optimise age** | Defaults to 1 day, never surfaced |
| **Archive age** | Does not exist to ask about |
| **Sync scope** | Does not exist to ask about |
| **Cloud deletion grace** | Fixed at 7 days, no UI anywhere |

---

## Enum defaults, in one place

| Type | Default | Where it bites |
|---|---|---|
| `AlbumMode` | `OFF` | A new album does nothing until chosen |
| `ThemeMode` | `SYSTEM` | Follows the phone |
| `CloudDeletionPolicy` | `LEAVE` | A cloud copy is never removed unprompted |
| `CloudDeletionGrace` | `7` days | How long a file must be gone first |
| `MediaAge` | `OneDay` | Chosen by me, not you — worth a look |
| `OptimiseMode` | `Auto` | Only reachable once the master switch is on |
| `VideoQuality` | `High` (480p) | On the evidence of the 28 Aug comparison |
| `FirstBackupWindow` | `1` am | Six-hour window |
| `RemoteRoots` | `Samsung Gallery/DCIM` | Always searched, even when not the destination |

`MediaAge.DEFAULT` is the one default in this table that nobody decided. You specified the five
values and not which one leads; I picked the longest wait. One constant to change.
