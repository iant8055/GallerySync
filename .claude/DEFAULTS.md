# Defaults — every one, and exactly what it does

Requested by Ian, 25 Aug 2026: *precisely define exactly what the ramifications are of every default
setting.*

Every value here is read from the code, with the file it comes from. When a default changes, this
file changes with it — a default whose stated effect no longer matches its behaviour is worse than
no document, because it is the thing someone will quote back.

---

## The two sentences that matter

**Out of the box, GallerySync does nothing to your files.** A fresh install with nothing touched has
no granted folders and every album set to Off, so the scan returns nothing, the upload queue is
empty, and no run has anything to do. This is deliberate and was paid for: on 24 Aug 2026 a
content-triggered run on a fresh install uploaded 23 files from five albums nobody had chosen,
because the album gate was opt-*out*. It is opt-in now.

**No default can remove a file from anywhere.** Not from the phone, not from OneDrive. Every path
that removes something requires a choice the user made — an album mode they set, or a confirmation
they tapped. The three defaults that could have broken this all point the safe way, and each is
listed below with the reason.

---

## User settings

### Automatic sync — **on**
`BackupPreferences.isAutomaticEnabled = true` · [BackupSettings.kt](../app/src/main/java/com/gallery/sync/data/local/settings/BackupSettings.kt)

**If untouched:** background runs are scheduled, and they upload files from albums whose mode is not
Off. On a fresh install that is no albums, so this default does nothing until the user chooses one.

**Why on:** nothing can upload before sign-in, so signing in is the consent moment rather than a
separate switch. Changed 19 Aug 2026 — an app whose purpose is keeping files safe should not sit
idle waiting to be told to start.

**Cost of leaving it on:** battery and Wi-Fi data while uploads run. It cannot cost storage or
photos.

---

### Mobile data — **off (Wi-Fi only)**
`allowMeteredNetwork = false`

**If untouched:** no upload ever runs on a metered connection. A backup queued away from Wi-Fi waits.

**Why off:** the first run moves the whole library — measured at 148 GB across 8,520 files on the
Fold 8. Over mobile data that is a bill, and an expensive surprise is not something to opt someone
into.

**Cost of leaving it off:** photos taken away from Wi-Fi are not backed up until the phone is back on
one. On a phone rarely connected to Wi-Fi, that is indefinite.

---

### Optimise photos automatically — **off**
`isAutoOptimiseEnabled = false`

**If untouched:** no photo is ever replaced with a smaller copy unless the user taps Optimise.

**Why off:** optimising rewrites the file on the phone, and that is not undoable from the phone — the
full-quality original only exists in OneDrive from then on. Recovering it is a download.

**Cost of leaving it off:** no space is reclaimed by optimising. The 4.8 GB of proxy candidates on a
real library stays occupied until asked for.

**Note:** turning it on does *not* make optimising unattended. Android requires a confirmation dialog
per batch and that dialog needs an Activity. On means "ask me when there is something to do" rather
than "do it silently".

---

### Default mode for new albums — **Off**
`defaultAlbumMode = AlbumMode.DEFAULT` = `OFF` · [AlbumMode.kt:77](../app/src/main/java/com/gallery/sync/data/local/entity/AlbumMode.kt)

**If untouched:** an album the scan discovers is not backed up, not synced and not archived. It sits
in the list waiting to be given a mode.

**Why Off:** this is the opt-in gate. `nextPending` selects albums whose mode is *not* Off, rather
than excluding those that are — the two differ only for an album with no row, which is exactly the
case that failed on 24 Aug 2026.

**Cost of leaving it Off:** new albums are not protected until noticed. The failure mode is "backs up
too little", which is visible and recoverable; the opposite is neither.

**Archive can never be this default.** `AlbumMode.canBeDefault` excludes it
([AlbumMode.kt:87](../app/src/main/java/com/gallery/sync/data/local/entity/AlbumMode.kt)), so no
setting can arm a mode that removes files for albums the user has not seen. A stored value outside
that list falls back to Off on read.

---

### Where new backups go — **`Samsung Gallery/DCIM`**
`destinationRoot = RemoteRoots.DEFAULT_DESTINATION` · [RemoteRoots.kt](../app/src/main/java/com/gallery/sync/domain/backup/RemoteRoots.kt)

**If untouched:** uploads land beside what Samsung's own sync already put there, which is why the
skip-existing check finds anything at all — 6,278 of 6,371 files already present on the Fold 4, so a
first run sends 2.8 GB rather than 120 GB.

**If changed:** only *new* uploads move. `Samsung Gallery/DCIM` stays permanently in the search set,
so nothing already uploaded is stranded and nothing is sent twice. That is what makes the setting
safe to change at all.

**Fallback:** a stored path that fails `isValidDestination` is ignored on read, not on write — a
value that somehow became unusable falls back to the default rather than sending every upload to a
path Graph rejects forever.

---

### First backup window — **1am, 6 hours, charging required**
`firstBackupStartHour = 1`, `WINDOW_HOURS = 6`, `firstBackupRequiresCharging = true` · [FirstBackupWindow.kt](../app/src/main/java/com/gallery/sync/domain/backup/FirstBackupWindow.kt)

**If untouched:** the first whole-library upload starts only between 1am and 7am, and only while
plugged in. At the ~3 MB/s measured, 148 GB is around fourteen hours, so this avoids a hot phone and
a flat battery in the middle of a day.

**What it does not gate:** "Sync now". A user who asks is never held — the window stops the app
choosing a bad moment on its own, not the user choosing one the app disagrees with.

**It lifts permanently** once the backlog drains (`hasCompletedFirstBackup`, one-way). Every later
run is incremental, and leaving the gate on would make a photo taken at noon wait until 1am.

**Cost of leaving it on:** on a phone that is never charged overnight, the first backup never starts
automatically, and the screen says which of the two conditions is holding it rather than just
"waiting".

---

### When you delete a photo from this phone — **Leave the OneDrive copy**
`cloudDeletionPolicy = CloudDeletionPolicy.DEFAULT` = `LEAVE` · [CloudDeletionPolicy.kt:41](../app/src/main/java/com/gallery/sync/domain/backup/CloudDeletionPolicy.kt)

**If untouched:** deleting a photo in your gallery never affects its backup. Nothing is offered,
nothing is asked, the cloud copy stays.

**Why Leave:** a cloud copy left behind costs storage. A cloud copy removed in error costs the photo,
because the local one is already gone. Those are not comparable.

**Cost of leaving it on Leave:** OneDrive accumulates copies of files no longer on the phone, and
nothing will ever prompt about them.

**There is no automatic value to set.** The enum has two members, Leave and Ask; there is deliberately
no third that deletes without a person reading names. A stored value that will not parse falls back
to Leave, never to Ask — a corrupt preference must not be able to arm the one feature that removes a
last copy.

---

### Waiting period before a cloud copy may be offered — **7 days**
`cloudDeletionGraceDays = CloudDeletionGrace.DEFAULT_DAYS` = `7`, selectable 1 / 7 / 30 / 90

**If untouched, and only under Ask:** a file must have been missing from the phone for seven
continuous days before its cloud copy appears in the review list.

**Why a grace period at all:** absence observed once is not evidence of deletion. An unmounted card, a
revoked permission or a gallery app mid-reindex all look like "the file is gone". Absence that
persists for a week does not.

**Cost of a longer value:** storage is reclaimed later. **Cost of a shorter one:** a transient absence
is more likely to be offered as a deletion. Under Leave, this setting governs nothing.

---

### Appearance — **System**
`ThemeMode.DEFAULT = SYSTEM` · [AppearanceSettings.kt:26](../app/src/main/java/com/gallery/sync/data/local/settings/AppearanceSettings.kt)

**If untouched:** the app follows the phone's light/dark setting.

**Testing consequence, learned 25 Aug 2026:** because this is an in-app override, `adb shell "cmd
uimode night yes"` alone does **not** prove dark mode works — with the app set to Light it still
renders light and a check can pass falsely. Dark mode has to be set in the app's own Appearance
control. CLAUDE.md's stated procedure is incomplete on this point.

---

## Not a setting, but it decides everything: granted folders

`ScopedDirectories.currentScope()` · [ScopedDirectories.kt](../app/src/main/java/com/gallery/sync/data/local/media/ScopedDirectories.kt)

**Default: empty.** Until the user picks folders in Gate 1, `scanAll` returns nothing at all, so
there is no ledger, no queue and nothing any run can do.

This is the reason the out-of-box state is inert even before album modes are considered. It is also
why the engine has *two* scans: `scanAll` is scoped and drives what is offered, while
`scanEverything` ignores the scope and answers only "does this still physically exist" — driving a
prune from a scoped scan would forget every album the user merely narrowed away.

---

## Engine constants that behave as defaults

These are not user settings, but they shape what a default run does.

| Constant | Value | What it means |
|---|---|---|
| `DEFAULT_BATCH` | 25 files | Files per run. Small enough that a cancelled worker loses little. |
| `DEFAULT_BATCH_BYTES` | 512 MB | Roughly one run's transfer. ~9 min at 1 MB/s, inside WorkManager's limit. A single file larger than this is still attempted alone, or the biggest files would never upload. |
| `MAX_ATTEMPTS` | 5 | Failures before a file is given up on, rather than retried forever. |
| `MAX_REMOTE_PAGES` | 200 | Ceiling on the listing page walk — 20,000 files per album. |
| `TARGET_LONG_EDGE_PX` | 2048 | What an optimised photo is resized to, roughly a tenth of the size. |
| `SQL_BATCH` | 500 | Bound variables per statement, under SQLite's 999 limit on older Android. |

A consequence worth stating: at 25 files and 512 MB per run, a large folder is confirmed over
**several runs, not one**. A user watching a single run finish with files still outstanding is seeing
correct behaviour.

---

## Every fallback points the safe way

Four stored values are re-validated on read rather than trusted. In each case an unreadable or
out-of-range value falls back to the *cautious* option, not the permissive one:

| Stored value | Falls back to | Why that direction |
|---|---|---|
| `defaultAlbumMode` | `OFF` | An unknown mode must not arm backup, and Archive is excluded entirely. |
| `cloudDeletionPolicy` | `LEAVE` | A corrupt preference must not be able to arm the feature that removes a last copy. |
| `destinationRoot` | `Samsung Gallery/DCIM` | An unusable path would fail every upload forever. |
| `firstBackupStartHour` | `1` | An out-of-range hour would make the window unopenable. |

---

## Open

- **`hasCompletedFirstBackup` is one-way.** Nothing resets it. If the flag is set wrongly — by a
  reinstall onto a device with a full library, say — the overnight window is lifted for a backlog it
  was meant to gate. Not yet a known failure, but nothing prevents it either.
- **The grace period governs nothing under the default policy.** Showing a setting whose effect is
  currently zero is its own kind of confusion; it may belong behind the Ask option rather than beside it.

---

# What happens when you change one

Ian, 25 Aug 2026: *"also importantly what happens if/when I change a default."*

The question behind this is **retroactivity**. Some changes affect only what happens next; some
reach backwards and act on things that already happened. That difference is invisible in a settings
screen and it is where the surprises live.

## The one that reaches backwards

### Leave → Ask exposes the entire backlog at once

**`markLocalMissing` is not gated on the policy.** `refreshLedger` records a
`localMissingSinceEpochMillis` for every backed-up file that leaves the phone, on every scan,
whatever the deletion policy says
([BackupEngine.kt:239](../app/src/main/java/com/gallery/sync/domain/backup/BackupEngine.kt)). Under
Leave those timestamps are simply never read.

`cloudDeletionCandidates` then selects on `localMissingSinceEpochMillis <= :missingBefore`, which is
a historical test.

So switching Leave → Ask does not begin watching from that moment. It **reveals everything already
recorded** — every file that has been gone longer than the grace period, however long ago it went. On
an install that has been running for months, that is potentially thousands of files presented in one
list, on the first screen after flipping a switch.

Nothing is deleted by this: the review still requires reading names and confirming, and the
pre-delete re-scan still runs. But it is the opposite of what "start asking me" sounds like, and it
is the worst possible moment to offer a bulk action.

**Implications, not yet built:**
- The switch to Ask should say how many files it is about to surface, before it is flipped.
- The review list needs to be sane at that size — the confirmation names a count and a total, and
  "4,812 files · 61 GB" is not a decision anyone can take responsibly in one tap.
- A first-time-Ask state may need to offer "only files that go missing from now on" as the safer
  reading of what the user meant.

## Everything else affects only what happens next

| Change | Retroactive? | What it does |
|---|---|---|
| **Automatic sync → off** | No | Scheduled runs stop. Nothing already uploaded changes. Photos taken from now on are not backed up until "Sync now" is tapped, and nothing will remind you. |
| **Mobile data → on** | No | The next run may use cellular. If the first backup has not completed, that is the whole library — 148 GB on the measured device. This is the only setting whose change can cost real money. |
| **Optimise automatically → on** | No | The app offers to optimise when candidates exist, instead of waiting to be found. Still one Android confirmation per batch; it cannot become unattended. Already-optimised photos are unaffected. |
| **New albums start as → Backup or Sync** | **No** — existing albums keep their modes | Only albums discovered *after* the change get it. `insertIfNew` uses `IGNORE`, so a choice already made is never overwritten. The live risk is different: a new folder appearing later — a messaging app's media directory, a new camera mode — begins uploading without a decision. Archive can never be selected here. |
| **Backs up into → a new folder** | No | New uploads go to the new path. Nothing already uploaded moves, and the old root stays permanently in the search set, so nothing is stranded and nothing is sent twice. |
| **First backup hour / charging** | Only until the first backup completes | Moves or removes the overnight gate. With charging off, a fourteen-hour transfer can begin on battery. Once `hasCompletedFirstBackup` is set the whole setting is inert, and it is one-way. |
| **Waiting period → shorter** | **Yes, under Ask** | Same mechanism as Leave → Ask: it re-tests existing timestamps, so shortening it surfaces more files immediately. A transient absence — unmounted card, revoked permission, gallery reindex — is likelier to be offered as a deletion. |
| **Waiting period → longer** | Yes | Files currently in the review list may drop out of it until they qualify again. |
| **Appearance** | No | Cosmetic. Note it is an in-app override, so `cmd uimode night yes` does not exercise it — see above. |

## Two changes that cannot be undone by changing the setting back

- **Optimising a photo.** Turning auto-optimise off later does not restore anything already
  replaced; the full-quality original exists only in OneDrive from then on, and getting it back is a
  download.
- **`hasCompletedFirstBackup`.** One-way by design. Nothing in the app sets it false again.

Setting an album to Archive belongs on this list in spirit, but it is a mode rather than a default
and it carries its own confirmation.

## The rule this suggests

A settings screen that shows only the current value is under-informative for any setting whose
change is retroactive. The two that are — the deletion policy and its grace period — should say what
flipping them will *reveal*, with a count, before they are flipped. Everything else can be changed
and reasoned about locally.
