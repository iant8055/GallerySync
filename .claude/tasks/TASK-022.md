# TASK-022 — Remove the orphaned wizard code and the photo age setting

Milestone: housekeeping, but the first half prevents a recurring defect
Requested by: Ian, 6 Sept 2026 — *"ok - tomorrow then"*
Depends on: nothing. Both halves are removals.

## Part A — delete three unreachable files

`ApplyLibraryChoice.kt`, `SetupWizardScreen.kt`, `ReconcileScreen.kt` have been unreachable since
`e6a0794` (31 Aug 2026) swapped `MainActivity` from `SetupWizardScreen` to `SetupTour`.
`SetupWizardScreen.kt:385` says so itself: *"only in ReconcileScreen, which nothing renders."*

**Why this is not tidying.** `ApplyLibraryChoice` is a class named for the install choice whose body
sets every album's mode in bulk, with `LibraryChoice.mode` pointing straight at it. Any agent reading
the wizard flow finds it and concludes the install choice writes album modes — the error CLAUDE.md
calls wrong by construction, which recurred again on 6 Sept. Ian on why that rule is in the
always-loaded file: *"I had to repeat and repeat those instructions to you as you over and over tried
to make the wizard change album modes."* Deleting it removes the temptation permanently.

**It is not a pure deletion — `ReconcileViewModel` is live** (it is what `SetupTour` uses) and needs
three edits:

1. Drop the `private val applyChoice: ApplyLibraryChoice` constructor parameter.
2. Drop `fun applyLibraryChoice()`.
3. Drop the `libraryApplied` and `applyingLibraryChoice` state fields — read **only** by
   `ReconcileScreen:179-180`, which goes in the same change. Note `setLibraryChoice` sets
   `libraryApplied = null` and is also the function that writes the optimise cutoff, so edit it
   carefully; that cutoff write is what makes Gate 2 #3 work at all.

Then reword two stale prose mentions: `MediaAge.kt:52` and the KDoc in `LibraryChoiceTest` that names
the class. Neither is a KDoc link, so nothing fails to compile — they would simply point at nothing.

Hilt needs no cleanup (`@Singleton` + `@Inject constructor`, no module entry). `LibraryChoice.mode`
survives: `optimisesAtInstall` and `uploads` both read it. Some string resources become unused, which
is harmless.

**Verify with a real wizard run**, not just a build. The edit is in the live wizard's ViewModel and a
constructor change touches Hilt injection; a mistake breaks the most exercised path in the app.

## Part B — remove `photoOptimiseAge`

Ian ruled on 19 Aug 2026, TASK-011: *"only the Sync age is limited to video. Photos are proxied
whatever their age, because a 2048px proxy leaves the photo in the gallery and costs an edit nothing
until the export. **There is no photo age setting and none is wanted.**"*

It was added anyway: code 28 Aug (`54f6124`), Settings screen 30 Aug (`f650536`), MILESTONES tree
29 Aug (`c0c9b81`). It has never governed anything — `proxyCandidates` has no age clause and must not
gain one.

So today the Settings screen offers *straight away · 1hr · 12hr · 1 day · 1 week* for photos, the value
persists, and nothing reads it. Remove it from `SettingsScreen`, `BackupViewModel`, `BackupSettings`
(field, key and read), and the Area 2 tree in MILESTONES — which is annotated to say so.

**Do not "fix" it by wiring it up.** That was done on 6 Sept (`56d5044`) and reverted the same evening
(`0d0fc9f`). The order of the question is *should this control exist?* before *why doesn't it work?*

Leave `MediaAge.thresholdEpochSeconds()` alone — video uses it, and it replaced a private copy of the
same arithmetic.
