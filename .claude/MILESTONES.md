# GallerySync Milestones

**Samsung turns off Gallery Sync on 30 September 2026.**

## Release gate — decided by Ian

**Nothing is published to Google Play until v0.3.0 and v0.4.0 are built and tested.**

Shipping v0.2.0 alone would deliver "back up your photos, and they disappear from your
gallery" — the space is freed but nothing keeps the photos visible, because that is what
v0.3 (photo proxies) and v0.4 (retrieval) are for. That is a broken product no matter how
well the backup works.

Two consequences:

- **The Sept 30 date is a personal deadline, not a release deadline.** Ian's own library
  still needs protecting when Samsung's sync stops. Backup alone covers that: run it, and
  simply do not use Move to backup until the gallery side exists. OneDrive's own camera
  backup is a reasonable second net in the meantime.
- **v0.3 can be built properly rather than rushed**, since no store listing depends on it.

## Naming — retired 18 Aug 2026, replaced by a per-screen test

The old rule banned the word "Sync" in the UI until v0.3 and v0.4 both landed. **v0.3's photo
proxies landed and were verified on hardware on 18 Aug 2026**, and that is precisely the Samsung
behaviour the ban was waiting for: the photo stays in the gallery, the space is freed. The word is
earned for photos now, so a blanket ban has outlived its reason. Ian retired it the same day.

What replaces it is a test applied per screen, not a list of allowed words:

> **Say "sync" where the file ends up in the cloud *and* stays in the gallery.
> Where the file leaves the gallery, say that plainly instead.**

That is the whole promise users are migrating from, and it is the only thing the word has to carry.

| Operation | Local outcome | Wording |
|---|---|---|
| Upload, local copy kept | unchanged, still visible | **sync** — true for photos and video alike |
| Optimise, photo proxied | ~10x smaller, still visible | **sync** — this is the flagship case |
| Remove local copy | **gone from the gallery** | never "sync" — "Remove from this phone" |

The third row is the one the old rule was really protecting, and it stays protected. "Move to
backup" was a soft name for a hard action, and softening it was the actual risk — not the word
"sync" as such. The copy now says the file will no longer appear in the gallery, which it had never
said before.

### Consequence for video
Video satisfies row one and can never satisfy row two, so a video is synced right up until its
local copy is removed — at which point it leaves the gallery with no stand-in. See the video
section below; that limit is unchanged by any of this, and no wording can paper over it.
## Design principle — GallerySync is invisible

**It is not a gallery app and must never become one.**

Its only job is making files *present*. Everything a person actually does with photos —
viewing, search, face grouping, editing, sharing, albums, stories — the phone's existing
gallery already does, with years of work behind it. Rebuilding any of that would produce
something worse than what the user already has.

Consequences that bind every future task:

- **Feed the existing gallery, do not replace it.** A file with local bytes appears in Samsung
  Gallery, CapCut and everything else automatically, because it is an ordinary file. No
  integration is needed or possible.
- **GallerySync's own UI stays minimal**: setup, album selection, a storage budget, and a plain
  list for retrieving something that is not on the phone. No photo grid, no thumbnails
  browser, no search, no editing. If a task starts to look like building a gallery, it is the
  wrong task.
- **Set up and mostly forget.** The user sets a budget once and the background worker maintains it —
  but Android will ask them to approve each batch of photos it rewrites. See the consent constraint
  below: unattended-forever is not available to a third-party app, so the UI must not promise it.

## Platform constraints — established by experiment, do not re-litigate

Verified on a Galaxy Z Fold 4 (Android 16) on 2026-08-17:

- **A file with no local bytes cannot appear in any gallery app.** MediaStore rows must point
  at a real file, and the system opens that file directly — there is no hydration hook. Android
  has no placeholder-that-downloads-on-open mechanism for third-party apps.

  *Hydration hook* is the term for what Windows calls Files On-Demand: the OS lets an app intercept
  the moment a file is opened, fetch the real bytes, and hand them over, so a zero-byte placeholder
  behaves like a real file to every program. Windows has it (the Cloud Files API, which is how
  OneDrive shows files it has not downloaded) and macOS has it (File Provider extensions).
  **Android has no equivalent for media files.** That single absence is why this project cannot
  simply show everything and download on demand, and it is the root of most constraints below.
- **Samsung Gallery's cloud albums came from Samsung's own private index**, not MediaStore.
  That is why third-party apps could never see them, and it is exactly what is being switched
  off. It cannot be replicated by a third-party app.
- **A trash request is not a guarantee of recoverability.** See the deletion rule in CLAUDE.md.
- **Rewriting a photo always needs the user, and cannot be granted once and for all.**
  `MediaStore.createWriteRequest` launches only from an Activity, so no background worker can
  obtain consent by itself. A grant can be carried into background work via `ClipData`, but a
  single request is capped at 2000 URIs, so a large library needs repeated approvals as it grows.
  Automatic space management therefore means **"approve a batch occasionally", never "never asked
  again"**. From the platform docs rather than experiment — the ClipData hand-off is still to be
  confirmed on a Galaxy device.
- Therefore: **storage can be reduced, never eliminated.** Any plan that plans on zero local
  storage while remaining visible in the gallery is impossible, not merely hard.

## v0.1.0 — Foundation ✅ TAGGED
Scaffold, Logger, Room, OneDrive Graph adapter, MSAL sign-in, browse UI.
Verified on hardware: sign-in completes and the real drive lists.

## v0.2.0 — Backup ✅ WORKING (not yet tagged)
- [x] `Files.ReadWrite` scope
- [x] Upload ledger keyed on content, not MediaStore ids
- [x] Media scanner, partial-access aware
- [x] Resumable Graph upload — verified byte-identical on hardware
- [x] Per-album include/exclude
- [x] Skip files already present in OneDrive
- [x] Backup UI with a manual run
- [x] Move redundant local copies out, once the cloud copy is verified
- [x] Schedule the periodic worker — content-triggered on new media plus a 6-hourly safety net.
      Off by default; the user turns it on. Both were still listed as outstanding here long after
      222ba17 shipped them.
- [x] Metered-network preference — a real setting in `BackupSettings`, exposed as a toggle in
      Settings, defaulting to unmetered-only.
- [ ] Retry failed items from the UI
- [ ] **Verify a large video actually uploads.** Coded but never watched working. The session URL is
      not persisted, so a file too big to finish inside one worker window restarts from zero every
      run; and DEFAULT_BATCH is 25 files regardless of size. See the video section below.
- [ ] **Start time for the first backup.** The initial whole-gallery upload is the heaviest thing
      the app ever does. Let the user pick when it starts, default overnight, and require charging
      for that first run. Asked for by Ian 18 Aug 2026; see TASK-011 for why it needs no consent
      dialogs and the optimise pass does.

Cutover rule: run alongside Samsung's sync for at least two weeks before trusting this alone.

## v0.3.0 — Space management
The milestone that delivers the actual product: the phone stops filling up, and the existing
gallery keeps working.

- [x] **Photo proxies.** Downscale photos (~2048px, EXIF preserved) and keep the proxy in
      MediaStore permanently. Roughly 10x smaller, and every photo stays visible, searchable and
      editable in the phone's own gallery. This is not a storage trick — it is what keeps the
      existing gallery whole.
- [x] **Never proxy video silently.** A degraded clip handed to an editor fails quietly, and the
      user only discovers it in the exported result. Videos are kept whole or kept in the cloud.
- [ ] **Storage budget.** User sets a free-space floor, default 20 GB, with an enforced minimum so
      it stays clear of Android's low-storage threshold — below that the backup worker stops
      running and nothing new becomes eligible to proxy; the worker keeps the phone
      above it by proxying the largest verified photos, largest first. Nothing is evicted and
      nothing is deleted — proxying is the only lever. If proxying alone cannot reach the floor it
      stops and says so. Notifies when free space drops below the floor — which is also how it asks
      for the next batch of write consent. Decided 18 Aug 2026; see TASK-011.
- [ ] **Rolling window for video**, so recent clips stay usable in the gallery. Video has no proxy
      path and cannot have one, so the only lever is removing the local file — which is why this is
      a separate decision from the photo budget.
- [ ] **Move to backup should distinguish photo from video.** It currently trashes both. A photo has
      a better option now (optimise, and stay visible); a video has none, and vanishes from the
      gallery until v0.4 retrieval. Same button, very different consequence.
- [ ] Per-album "keep originals on device" for albums actively edited from.
- [x] Clear marker showing which items are optimised versus whole. Cloud badge burned into the
      proxy plus an EXIF marker, verified on a Fold 4 across square and 16:9 at orientation=90.

### Proxying verified on hardware — 18 Aug 2026, Galaxy Z Fold 4
No longer theoretical. 11 photos optimised, 40,283,338 bytes reclaimed; five correctly skipped as
already small rather than needlessly rewritten.

- EXIF orientation and dates carried across. A NULL `datetaken` on one file was pre-existing —
  2,069 of 6,289 images on the device have none, mostly WhatsApp, which strips EXIF.
- Videos untouched: still 8K at 103 / 163 / 178 MB.
- OneDrive originals intact at full size (4 MB) beside a 348 KB local proxy.
- **CapCut can see the backed-up folder and its files.** This is the problem the project exists to
  solve, confirmed against the app that motivated it.

**Consequence for v0.4:** what an editor imports for an optimised photo is the 2048px proxy, so
exports from it are capped at that resolution. Retrieval is therefore load-bearing, not a nicety —
it is the only route back to a full-quality edit.

### Open at the end of 18 Aug 2026
- **Next task is TASK-011, the storage budget.** Photos only in the first pass; video eviction
  belongs with the rolling window. Ceiling-versus-floor is decided: a user-set free-space floor,
  default 20 GB. **Still needs Ian's call on where the applying step runs** — WorkManager cannot
  attach the `ClipData` that carries the write grant, so it is a foreground service or raw
  JobScheduler. **And on `POST_NOTIFICATIONS`** — Ian asked for a notification when free space
  drops below the floor, which adds a permission to the Play listing. See TASK-011.
- **Proxy recovery is untested on hardware.** `LedgerRecovery` guards the upload path against
  re-uploading proxies once the ledger is lost, but reproducing that means destroying a real
  ledger. Wants an in-memory Room test seeding a pending proxy row.
- **Album selections are still device-only.** They are the one part of the ledger that cannot be
  rebuilt from OneDrive or from the files, so a phone move loses them. Deliberately not built yet:
  it needs a new remote write path.
- **Six photos in `AaSync` carry the pre-fix sideways badge.** Harmless, marked as proxies so
  nothing will touch them again. Delete locally and re-fetch from OneDrive to tidy.

### "Sync" and "backup" are different words here, and video sits differently under each

Ian, 18 Aug 2026, recalled video sync being a v0.4 item. That is right, and the apparent
contradiction with "video is already backed up" is the naming rule above doing its job.

- **Backup** = the bytes are safely in OneDrive. Video: coded since v0.2, not yet verified.
- **Sync** = the Samsung behaviour — the file stays visible in the gallery while its space is
  freed. Reserved deliberately until v0.3 and v0.4 both land.

So video *backup* is v0.2 and video *sync* needs v0.4. Both statements are true and they are about
different things.

### The part that does not resolve: video can never fully sync
Sync means visible while remote. A photo achieves that through its proxy — a real local file, ~10x
smaller, that every gallery and editor opens normally. **Video has no equivalent and is not allowed
one**, because a degraded clip fails silently in an editor.

So even after v0.4 retrieval lands, the best available state for a video is one of:

- whole on the device, occupying its full size, or
- **not in the gallery at all**, retrievable on demand from the retrieval list.

There is no middle state for video, ever. This is a consequence of two constraints already
established — a file with no local bytes cannot appear in a gallery, and video must not be
degraded — and not something a later milestone fixes.

**Consequence for the migration.** Samsung Gallery Sync *did* show cloud-only videos, through its
own private index, and that cannot be replicated. A user moving across who kept videos in the cloud
will find them absent from the gallery until fetched, where before they appeared.

That is a real loss of browsing convenience and the store listing must not imply otherwise — but it
is narrower than it sounds. Those cloud-only videos were visible in Samsung Gallery **and nowhere
else**: no editor could open one. See the comparison above.

### Video upload is coded but never verified — and two things suggest it will struggle

Ian, 18 Aug 2026: "the Video Sync hasn't been built as far as I am aware."

There is no separate video sync to build — video rides the same path as photos and has since v0.2.
But nothing has ever confirmed it *works*, and the hardware note above only evidences a 4 MB photo.
No commit records a video reaching OneDrive. So the instinct is right even though the code is there.

Two structural reasons it is likely to struggle on large files, both found by reading rather than
by running, and both wanting hardware confirmation:

- **The upload session is not persisted.** `ChunkedUploader` holds `uploadUrl` as a local variable,
  and `BackupEntryEntity` has no column for it. Resume works *within* one call — the
  `nextExpectedRanges` handling recovers a failed chunk — but if the worker is stopped, the session
  is lost and the next run calls `createUploadSession` again from byte zero. Any single file too
  large to finish inside one worker window can therefore **never complete**, however many times it
  is retried.
- **`DEFAULT_BATCH = 25` is a file count, not a byte budget.** Sized for photos: 25 × 4 MB is about
  100 MB. Twenty-five videos at Ian's sizes is roughly 3.75 GB queued into a worker WorkManager
  stops after about ten minutes. The run dies mid-batch and the in-flight file's progress is thrown
  away — the batch still advances each run, so it grinds forward, but it wastes a partial upload
  every time.

Ian's clips are 103–178 MB, which at ordinary home upstream fit inside a single window comfortably;
it is longer 8K footage and slow connections where the first point bites. Worth a deliberate test:
back up one large video, confirm it lands byte-identical, then confirm a run killed mid-upload
resumes rather than restarting.

Fixing the first probably means persisting the session URL and its expiry on the ledger row. Fixing
the second means bounding the batch by bytes as well as by count.

### Move to backup does not distinguish video — and that is where it matters most

Raised by Ian, 18 Aug 2026: is sync deleting video, and should it not be a move that leaves a
thumbnail placeholder?

**Nothing automatic removes video.** There is no worker that evicts anything. The only thing that
removes a local file is the manual **Move to backup** button, and `BackupEngine.redundantLocalCopies()`
matches every verified file — it has no `isVideo` filter, unlike `proxyCandidates()`. So that button
does trash video, alongside photos.

**It is a move.** Nothing is eligible until Graph has confirmed the file and reported a matching
byte size, and only then is the local copy trashed. What makes it a move is that verified cloud
copy — not the trash, which the Fold 4 showed can be an outright delete on Samsung.

**A thumbnail placeholder cannot work.** Three variants, all dead ends:

- *A JPEG left at the video's path.* The MediaStore row still declares `video/*`, and the system
  opens the file directly — there is no hydration hook. Gallery and CapCut both read the real bytes
  and fail.
- *Swap the video row for an image row.* Now the gallery holds a fake photo where a video was. It
  sorts oddly, it misrepresents what is on the device, and the video is gone from the gallery
  regardless.
- *A transcoded low-resolution video.* The only technically working version, and the one CLAUDE.md
  rules out: a degraded clip fails quietly inside an editor and is discovered in the export. That is
  precisely the CapCut case this project exists for.

### The finding: the two features have inverted
Optimise now gives photos a graceful path — keep the file, keep it visible, reclaim ~90%. Video has
no equivalent and cannot have one.

So **Move to backup is at its most drastic exactly where it is the only option.** For a photo it is
now the worse of two available choices; for a video it is the sole lever, and it removes the file
from the gallery entirely with no stand-in until retrieval lands in v0.4.

The current copy is honest about permanence but says nothing about either asymmetry. Before v0.3 is
called done, that button should at minimum separate the two — or say plainly that photos could be
optimised instead, and that video will disappear from the gallery until it is fetched back.

Not a shipped bug: the release gate already holds everything until v0.3 and v0.4 are built and
tested, and this is an instance of the exact breakage that gate exists for.

## How Samsung actually did it — checked against vendor docs, 18 Aug 2026

Worth having on record, because the whole project is a replacement for it and the mechanism was
being described from memory.

1. **Bidirectional sync of photos *and* videos** to OneDrive's Samsung Gallery folder. Microsoft's
   own documentation: files modified or deleted in Samsung Gallery are reflected in the cloud, and
   deleting from either side deletes from the other.
2. **"Free up phone space"** removes the local originals of synced media. It is **all-or-nothing** —
   Samsung gives no way to pick which items become cloud-only.
3. **Samsung Gallery keeps a cached thumbnail and its own index entry**, so a cloud-only item still
   appears in the grid. Tapping it fetches the original from OneDrive on demand.
4. This applied to video exactly as to photos. A cloud-only 8K clip still showed in Samsung Gallery.

### Why none of steps 2–4 can be copied
They work because Samsung owns both the index and the viewer. The thumbnail lives inside Samsung
Gallery, not in MediaStore as a real file, so **no third-party app ever saw those cloud-only items**
— which is the constraint already recorded above, now confirmed from the vendor side rather than
inferred. It is also precisely why CapCut could not see them, which is the reason this project
exists.

### The trade, stated honestly
| | Samsung Gallery Sync | GallerySync |
|---|---|---|
| Cloud-only item visible in the phone's gallery | yes, photos and video | **no** — platform limit |
| Cloud-only item visible to CapCut and other apps | **no, ever** | n/a — our files are real |
| Choosing what to free | all-or-nothing | selective, largest-first, to a floor |
| Photo kept usable while space is freed | no — original removed | **yes** — 2048px proxy stays |
| Local delete removes the cloud copy | **yes, silently** | no — opt-in, batched, never inferred |

So "a step down for video" needs qualifying. Samsung showed cloud-only video **in Samsung Gallery
alone**, where it was useless to every editor. We cannot show it there at all, but everything we do
leave on the phone is a real file that every app can open. The browsing convenience is genuinely
lost; the usability is not.

Row five is the one to keep in view while designing v0.4 deletion sync: Samsung's bidirectional
delete is the behaviour a migrating user has been trained on, and it is the behaviour this project
deliberately refuses.

## Video middle-state — two proposals from Ian, 18 Aug 2026

### Proposal 1: write our own thumbnail and index for Samsung Gallery to read — not possible
Three independent reasons, any one fatal:

- Samsung Gallery's cloud index is a private database inside its own app sandbox. There is no
  public API to write to it, and no third-party app can reach another app's private storage.
- The cached thumbnail lives inside Samsung Gallery, not in MediaStore. Making it visible is not a
  matter of producing the right file — nothing we write anywhere is read by that code path.
- **Samsung is switching this off on 30 September 2026.** Even a working exploit would target a
  mechanism that is being removed, and would break every other phone. This project is explicitly
  built for LG and Moto as well.

Recorded so it is not revisited. The constraint above already said this; this is the same wall from
a different angle.

### Proposal 2: truncate the video to a short clip — viable, and it inverts our own rule
Replace the local video with a genuine short clip, marked, with the original in OneDrive. This is
the video analogue of the photo proxy and it deserves proper evaluation rather than a reflex no.

**What is right about it.** It produces a real, valid, playable video file. It appears in MediaStore,
in Samsung Gallery and in CapCut, because it is an ordinary file — which is the entire mechanism
this project rests on. It is also cheap: cutting at a keyframe boundary with `MediaExtractor` and
`MediaMuxer` is a container-level stream copy, no re-encode, so it is fast and lossless on the
retained portion. Embedding the OneDrive reference in an MP4 metadata atom is the direct analogue
of what `ProxyMarker` already does with EXIF, and keeps the file self-describing.

**The link only helps us.** CapCut will not read a custom atom and fetch from OneDrive. It is a
marker for our retrieval path, exactly like the EXIF marker — not a hydration mechanism. Worth
being clear about, because "embed a link in the video" can sound like it makes the file work
elsewhere. It does not.

### The rule this runs into is less settled than it looks
The note says: *never proxy video **silently** — a degraded clip fails quietly, and the user only
discovers it in the exported result.* The operative word is silently, and the two candidate
mechanisms fail in opposite directions:

| | Truncate to a short clip | Downscale, full length |
|---|---|---|
| Cost to produce | cheap — stream copy, no re-encode | expensive — full transcode, adds Media3 Transformer |
| Gallery viewing | **destroyed** — you cannot rewatch anything | **preserved** — 480p is fine on a phone |
| Failure in an editor | **loud** — 2 seconds on the timeline, seen instantly | **quiet** — looks fine, discovered in the export |
| Content preserved | no — most of it is gone | yes — all of it, at lower quality |

So truncation is arguably *more* compliant with the rule as written, because its failure is
impossible to miss. The safer-sounding option is the one the rule actually describes.

**And the project already accepted quiet editor degradation — for photos.** A 2048px proxy exports
at 2048px, which the milestone notes above call out as the reason retrieval is load-bearing. The
video rule was written before proxies existed and has not been reconciled with that decision. The
asymmetry is worth examining rather than assumed.

### The reverse direction — Ian, 18 Aug 2026: selecting it pulls the original back
Right, and that is already the v0.4 retrieval item rather than something new. But **"selecting the
file" cannot mean selecting it in Samsung Gallery.**

There is no hydration hook — the constraint above, again. When the user taps that clip in Samsung
Gallery, the system opens the file directly and our app is never told. We cannot intercept it,
delay it, or substitute anything. So the round trip is necessarily a two-app flow:

1. The user meets the stub in their gallery and sees it is a stub.
2. They open GallerySync and fetch it from the retrieval list.
3. We download the original and put it back where the stub was.

The stub is therefore a **signpost, not a button**, and its job is to make step 2 obvious. That is a
point in truncation's favour that downscaling does not have: a full-length 480p copy looks like a
normal video and gives the user no reason to go looking, whereas a clip that visibly stops tells
them immediately. Whatever is chosen has to answer "how does the user know to come to us", and the
stub is the only place that message can live.

### Samsung's own retrieval is a deliberate tap too
Ian, 18 Aug 2026, from using it: in Samsung Gallery you must deliberately click **download** to pull
a video back from OneDrive. It does not stream or fetch on its own.

That narrows the gap considerably. Samsung is not doing hydration-on-open either — it is showing an
item and offering a button. The difference between the two products is therefore not *automatic
versus manual*, which would be damning, but **where the button lives**:

| | Samsung Gallery | GallerySync |
|---|---|---|
| How a cloud-only item is retrieved | deliberate tap | deliberate tap |
| Where that tap happens | on the item, in the gallery | in GallerySync's retrieval list |
| Wait for the download | yes | yes |

So the honest description of the loss is one app switch, not a lost capability. Worth keeping in
proportion — earlier notes in this file framed cloud-only visibility as the thing users would miss
most, and the actual daily experience being replaced is "find the video, press download, wait".

### Writing the original back needs consent too
Step 3 overwrites a MediaStore file the camera created, not one we own, so it needs
`createWriteRequest` exactly as proxying does. Retrieval is not a quiet background restore; it is
another dialog.

There is a way around it worth considering for both paths: **a file this app creates through
MediaStore is owned by this app, and we can modify our own files without asking.** If the stub were
inserted as a new entry we own rather than an overwrite of the original, hydrating it later would
need no dialog at all. The cost is that the original then has to be removed, which is the
destructive path with the Samsung trash behaviour attached — and the new entry would carry a new
MediaStore id, losing anything keyed to the old one.

Not resolved here, but it applies to photo proxies as much as video stubs, and it is the only
route seen so far that reduces the consent burden rather than working around it.

### Provenance correction — this was never a rule of Ian's
Ian, 18 Aug 2026: *"this was a Claude added rule."* Checked, and he is right.

- **"Never proxy video silently" does not appear in CLAUDE.md.** The only mention of video there is
  the one-line project description. It has been cited repeatedly in this conversation as a hard
  rule from CLAUDE.md, and that was wrong.
- It entered **this file** in `fc603ab`, as an unchecked item in the v0.3 planning list, alongside
  work that had not been done yet. That commit's body attributes the design principle to Ian and
  the platform constraints to experiment. It claims no origin for the video line.
- So it is an agent's reasoning written down in a planning list and later ticked, not a decision
  Ian made and not a constraint anything was verified against.

The argument in it still has force — a degraded clip really can fail quietly in an editor. But it
carries the weight of an opinion, not of the deletion policy or the dark-mode rule, both of which
came from Ian and from shipped bugs. **Revising it is a normal design decision, not an amendment to
a hard rule**, and the earlier framing of it as one overstated what stands in the way.

### What this needs from Ian
Amending a hard rule in CLAUDE.md, which is his call, not an agent's. The options are not
truncate-versus-downscale so much as **what a video proxy is for**:

- If it is for *viewing* — keeping the gallery whole, which is the design principle — then
  full-length downscale is the only candidate, and editing goes through retrieval exactly as it
  does for photos.
- If it is for *marking a placeholder* — a visible stub saying "this exists, fetch it" — then
  truncation is cheaper, louder, and honest, but the gallery stops being a place you can watch
  anything.
- If neither is acceptable, the position stands: video is whole on the device or absent from the
  gallery, and the rolling window decides which.

Recommendation: full-length downscale, with the badge and metadata marker the photo path already
uses, and retrieval as the documented route to a full-quality edit. It keeps the gallery whole,
which is the stated purpose, and makes video consistent with photos rather than a special case.
The transcode cost is real and would need measuring on an 8K clip before committing.

## Where video stands — it spans three milestones, so it is easy to lose track

Video is **already backed up**, and has been since v0.2. `MediaScanner` queries the images and the
video collections both, `BackupEngine` records `isVideo` on every row, and nothing in the upload
path filters on it. The only place video is excluded is `BackupEntryDao.proxyCandidates()`, which
is about local proxying, not about syncing.

So the four things "video" can mean, and where each actually sits:

| | Status |
|---|---|
| **Backed up to OneDrive** | ⚠️ Coded since v0.2, never verified on hardware — see above |
| **Proxied / downscaled** | ❌ Never — deliberate, a degraded clip fails silently in an editor |
| **Local copy reclaimed to free space** | ⬜ v0.3, rolling window — the only lever is removing the file, which is a deletion decision |
| **Retrieved back on demand** | ⬜ v0.4, same path as photos — and the only route to a full-quality video |

The one that carries real risk is reclaiming space. A video cannot be shrunk, so freeing its space
means removing the local file, which makes it vanish from the gallery and runs straight into the
deletion rules in CLAUDE.md and the Samsung trash behaviour. That is why TASK-011 covers photos
only and the rolling window is a separate task with a separate decision.

## v0.4.0 — Retrieval and deletion sync
- [ ] Fetch a cloud-only item back on demand, registering it in MediaStore so every app sees it.
      Photos and video both — video is already backed up, so retrieval is the same path, and it is
      the only route back to a full-quality edit from a 2048px proxy.
- [ ] Plain retrieval list — **not** a photo browser. It is also the only place a fetch can be
      triggered: there is no hydration hook, so tapping an item in Samsung Gallery cannot reach us.
- [ ] Deletion sync, opt-in and batched. Highest-risk feature in the product; it only follows a
      backup engine that has been watched working. Never infers deletion from absence alone —
      a card unmounting or a permission being revoked must not be read as intent.

## v0.5.0 — Google Photos + Billing
- [ ] Google Play Billing (`pro_unlock`)
- [ ] Google Photos adapter (requires OAuth — Ian)
- [ ] Settings: sync frequency, account management
